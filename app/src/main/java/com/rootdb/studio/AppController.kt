package com.rootdb.studio

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rootdb.studio.data.BackupStore
import com.rootdb.studio.data.Cell
import com.rootdb.studio.data.ColumnInfo
import com.rootdb.studio.data.CryptoPrefs
import com.rootdb.studio.data.CryptoSpec
import com.rootdb.studio.data.DbOpenException
import com.rootdb.studio.data.DbSession
import com.rootdb.studio.data.HistoryEntry
import com.rootdb.studio.data.HistoryStore
import com.rootdb.studio.data.PathPrefs
import com.rootdb.studio.data.RecentStore
import com.rootdb.studio.data.RowData
import com.rootdb.studio.data.TableInfo
import com.rootdb.studio.data.ValueType
import com.rootdb.studio.data.canonicalPath
import com.rootdb.studio.root.RootShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface Screen {
    data object Home : Screen
    data object Browser : Screen
    data object DbHome : Screen
    data class Table(val name: String) : Screen
}

class AppController(val ctx: Context, val scope: CoroutineScope) {

    companion object {
        const val PAGE_SIZE = 50
    }

    // 浏览器
    var rooted by mutableStateOf(false)
    var rootText by mutableStateOf("正在检测 root 权限…")
    var allFilesGranted by mutableStateOf(false)
    var showPathInput by mutableStateOf(false)
    var path by mutableStateOf(PathPrefs.lastPath(ctx))
    var entries by mutableStateOf<List<RootShell.Entry>>(emptyList())
    var listing by mutableStateOf(false)
    /** 当前目录内的过滤关键字 */
    var searchQuery by mutableStateOf("")

    /** 按搜索关键字过滤后的列表（不区分大小写） */
    val visibleEntries: List<RootShell.Entry>
        get() {
            val q = searchQuery.trim()
            if (q.isEmpty()) return entries
            return entries.filter { it.name.contains(q, ignoreCase = true) }
        }

    // 导航
    var screen by mutableStateOf<Screen>(Screen.Home)
    var busy by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)

    /** 最近访问的目录（最多 10 条，最新的在最前） */
    var recent by mutableStateOf(RecentStore.list(ctx))

    // 会话
    var session by mutableStateOf<DbSession?>(null)
    var tables by mutableStateOf<List<TableInfo>>(emptyList())
    var askPasswordFor by mutableStateOf<String?>(null)
    var pendingPasswordPath by mutableStateOf<String?>(null)

    /** 密钥错误等打开失败原因，显示在密码对话框内部（避免被对话框/输入法挡住） */
    var passwordError by mutableStateOf<String?>(null)

    // 表
    var currentTable by mutableStateOf<String?>(null)
    var columnsInfo by mutableStateOf<List<ColumnInfo>>(emptyList())
    var tableColumns by mutableStateOf<List<String>>(emptyList())
    var rows by mutableStateOf<List<RowData>>(emptyList())
    var total by mutableStateOf<Long?>(null)
    var pageIndex by mutableStateOf(0)

    // 加密
    var crypto by mutableStateOf(CryptoPrefs.load(ctx))
    var revealPlain by mutableStateOf(false)

    // 历史 / 备份 / 监控
    var history by mutableStateOf<List<HistoryEntry>>(emptyList())
    var backups by mutableStateOf<List<File>>(emptyList())
    var monitorText by mutableStateOf("等待采样…")
    var monitorChanges by mutableStateOf(0)
    private var lastMtime = -1L

    // 表页签
    var tab by mutableStateOf(0)

    init {
        refreshBackups()
        refreshHistory()
        allFilesGranted = runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                android.os.Environment.isExternalStorageManager()
            } else {
                true
            }
        }.getOrDefault(false)
        scope.launch(Dispatchers.IO) {
            val ok = RootShell.hasRoot
            rooted = ok
            rootText = if (ok) {
                "可读写 /data/data 等受保护目录"
            } else {
                "仅可浏览内部存储；受保护目录需 root"
            }
        }
    }

    /** 从系统设置等外部页面回到应用时调用，重新读取权限状态 */
    fun onAppResume() {
        refreshAllFilesState()
    }

    fun refreshAllFilesState() {
        allFilesGranted = runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                android.os.Environment.isExternalStorageManager()
            } else {
                true
            }
        }.getOrDefault(false)
    }

    fun requestAllFilesAccess() {
        try {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                android.net.Uri.parse("package:" + ctx.packageName),
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
        } catch (t: Throwable) {
            try {
                ctx.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Throwable) {
                toast("无法打开授权页面，请在系统设置中手动授予“所有文件访问权限”")
            }
        }
    }

    fun toast(text: String) {
        message = text
    }

    fun backToDbHome() {
        screen = Screen.DbHome
        currentTable = null
        tab = 0
    }

    fun saveCrypto(spec: CryptoSpec) {
        crypto = spec
        CryptoPrefs.save(ctx, spec)
        toast("加密设置已保存：${spec.summary}")
    }

    fun loadPath(raw: String) {
        val target = canonicalPath(raw)
        memoScroll()            // 离开/刷新前记住当前目录的滚动位置
        searchQuery = ""        // 换目录时清空过滤
        listing = true
        scope.launch(Dispatchers.IO) {
            if (RootShell.isRegularFile(target)) {
                withContext(Dispatchers.Main) {
                    listing = false
                    askPasswordFor = target
                }
                return@launch
            }
            var list = RootShell.listDir(target)
            if (list.isEmpty()) {
                // su 刚启动时首次列表偶尔为空，稍等再试一次
                runCatching { Thread.sleep(500) }
                list = RootShell.listDir(target)
            }
            withContext(Dispatchers.Main) {
                if (list.isEmpty()) {
                    toast("目录为空或无权访问：${RootShell.lastError ?: target}")
                } else {
                    path = target
                    PathPrefs.saveLastPath(ctx, target)
                }
                entries = list
                listing = false
                if (target == path) {
                    recent = RecentStore.add(ctx, target)
                }
            }
        }
    }

    fun refreshPath() = loadPath(path)

    /** 从首页进入文件浏览（并记录到最近访问） */
    fun openBrowser(target: String) {
        screen = Screen.Browser
        loadPath(target)
    }

    /** 文件浏览页返回到首页 */
    fun backFromBrowser() {
        screen = Screen.Home
    }

    /** 重新检测 root（首页「重试」按钮） */
    fun retryRoot() {
        RootShell.resetRootCache()
        rootText = "正在重新检测 root 权限…"
        scope.launch(Dispatchers.IO) {
            val ok = RootShell.hasRoot
            withContext(Dispatchers.Main) {
                rooted = ok
                rootText = if (ok) {
                    "可读写 /data/data 等受保护目录"
                } else {
                    "仅可浏览内部存储；受保护目录需 root"
                }
            }
        }
    }

    // ── 目录滚动位置记忆 ──────────────────────────────────────────────
    /** 列表当前可见位置，由 UI 持续写入 */
    var visibleIndex by mutableStateOf(0)
    var visibleOffset by mutableStateOf(0)

    private val scrollMemo = HashMap<String, Pair<Int, Int>>()

    private fun memoScroll() {
        scrollMemo[path] = visibleIndex to visibleOffset
    }

    /** 取回某目录上次离开时的滚动位置（无记录返回 null） */
    fun scrollOf(target: String): Pair<Int, Int>? = scrollMemo[target]

    fun goUp() {
        if (screen != Screen.Browser) return
        if (path == "/" || path.isEmpty()) return
        val parent = path.substringBeforeLast('/', "")
        loadPath(if (parent.isEmpty()) "/" else parent)
    }

    fun enter(entry: RootShell.Entry) {
        if (entry.isDir) {
            loadPath(entry.path)
        } else {
            askPasswordFor = entry.path
        }
    }

    fun openDb(rawPath: String, password: String?) {
        val dbPath = canonicalPath(rawPath)
        passwordError = null
        scope.launch(Dispatchers.IO) {
            busy = true
            try {
                session?.close()
                val s = DbSession.open(ctx, dbPath, password)
                val t = s.tables()
                withContext(Dispatchers.Main) {
                    session = s
                    tables = t
                    currentTable = null
                    rows = emptyList()
                    total = null
                    tableColumns = emptyList()
                    columnsInfo = emptyList()
                    screen = Screen.DbHome
                    tab = 0
                    askPasswordFor = null
                    pendingPasswordPath = null
                    toast(if (s.encrypted) "已解密打开：${dbPath.substringAfterLast('/')}" else "已打开：${dbPath.substringAfterLast('/')}")
                }
            } catch (e: DbOpenException) {
                withContext(Dispatchers.Main) {
                    if (e.needPassword && password == null) {
                        pendingPasswordPath = dbPath
                        passwordError = null
                    } else if (password != null) {
                        // 密钥错误：错误留在对话框内部显示，对话框保持打开方便直接改密钥重试
                        if (askPasswordFor != null) askPasswordFor = dbPath else pendingPasswordPath = dbPath
                        val raw = e.message.orEmpty()
                        passwordError = if (raw.contains("not a database") || raw.contains("encrypted", true)) {
                            "密钥不正确，无法解密该数据库；也可能这个文件本身不是数据库。"
                        } else {
                            raw.ifBlank { "打开失败，请检查密钥或文件格式" }
                        }
                    } else {
                        toast(e.message ?: "打开失败")
                    }
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    if (password != null) passwordError = "打开失败：${t.message}"
                    else toast("打开失败：${t.message}")
                }
            } finally {
                withContext(Dispatchers.Main) { busy = false }
            }
        }
    }

    fun closeDb() {
        scope.launch(Dispatchers.IO) { session?.close() }
        session = null
        tables = emptyList()
        rows = emptyList()
        tableColumns = emptyList()
        currentTable = null
        screen = Screen.Browser
        tab = 0
        // 从数据库返回浏览页时，若目录内容还没载入则补一次
        if (entries.isEmpty()) loadPath(path)
    }

    fun clearRecent() {
        recent = RecentStore.clear(ctx)
        // 顺手清掉数据库工作副本缓存（cache/dbwork），避免留下打开过的库的明文副本
        scope.launch(Dispatchers.IO) { runCatching { DbSession.clearCache(ctx) } }
    }

    fun loadTable(name: String, page: Int = 0) {
        val s = session ?: return
        scope.launch(Dispatchers.IO) {
            busy = true
            try {
                val p = s.page(name, page * PAGE_SIZE, PAGE_SIZE)
                val info = s.columns(name)
                withContext(Dispatchers.Main) {
                    tableColumns = p.columns
                    rows = p.rows
                    total = p.total
                    columnsInfo = info
                    currentTable = name
                    pageIndex = page
                    screen = Screen.Table(name)
                    tab = 0
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { toast("读取表失败：${t.message}") }
            } finally {
                withContext(Dispatchers.Main) { busy = false }
            }
        }
    }

    fun nextPage() {
        val t = currentTable ?: return
        val totalRows = total
        if (totalRows != null && (pageIndex + 1) * PAGE_SIZE >= totalRows) return
        loadTable(t, pageIndex + 1)
    }

    fun prevPage() {
        val t = currentTable ?: return
        if (pageIndex <= 0) return
        loadTable(t, pageIndex - 1)
    }

    fun typedValueFor(input: String, kind: EditKind, original: Cell): Any? {
        return when (kind) {
            EditKind.AUTO -> when (original.type) {
                ValueType.INTEGER -> input.toLongOrNull() ?: input
                ValueType.REAL -> input.toDoubleOrNull() ?: input
                ValueType.BLOB -> try {
                    HexBytes.decode(input.removePrefix("BLOB ").trim())
                } catch (t: Throwable) {
                    input.toByteArray()
                }

                ValueType.NULL -> if (input.isBlank()) null else input
                ValueType.TEXT -> input
            }

            EditKind.NULL -> null
            EditKind.TEXT -> input
            EditKind.INTEGER -> input.toLongOrNull() ?: throw IllegalArgumentException("不是合法整数")
            EditKind.REAL -> input.toDoubleOrNull() ?: throw IllegalArgumentException("不是合法小数")
            EditKind.BLOB -> HexBytes.decode(input.removePrefix("BLOB ").trim())
        }
    }

    fun saveCell(column: String, rowid: Long, original: Cell, input: String, kind: EditKind, encrypt: Boolean) {
        val s = session ?: return
        val t = currentTable ?: return
        scope.launch(Dispatchers.IO) {
            try {
                var text = input
                if (encrypt && crypto.enabled) text = com.rootdb.studio.data.CryptoEngine.encrypt(crypto, input)
                val value = typedValueFor(text, kind, original)
                val ok = s.updateCell(t, rowid, column, value)
                withContext(Dispatchers.Main) {
                    if (ok) {
                        toast("已更新 $column")
                        HistoryStore.add(
                            ctx,
                            db = s.sourcePath.substringAfterLast('/'),
                            table = t,
                            op = "UPDATE",
                            column = column,
                            oldValue = original.display,
                            newValue = if (value is ByteArray) "BLOB " + HexBytes.encode(value) else value?.toString(),
                            rowid = rowid,
                        )
                        refreshHistory()
                        loadTable(t, pageIndex)
                    } else {
                        toast("更新失败：未找到目标行")
                    }
                }
            } catch (t2: Throwable) {
                withContext(Dispatchers.Main) { toast("更新失败：${t2.message}") }
            }
        }
    }

    fun deleteRow(rowid: Long) {
        val s = session ?: return
        val t = currentTable ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val ok = s.deleteRow(t, rowid)
                withContext(Dispatchers.Main) {
                    if (ok) {
                        toast("已删除 rowid=$rowid")
                        HistoryStore.add(ctx, s.sourcePath.substringAfterLast('/'), t, "DELETE", "", "rowid=$rowid", "", rowid)
                        refreshHistory()
                        loadTable(t, pageIndex)
                    } else {
                        toast("删除失败")
                    }
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) { toast("删除失败：${e.message}") }
            }
        }
    }

    fun insertRow(values: Map<String, Any?>) {
        val s = session ?: return
        val t = currentTable ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val id = s.insertRow(t, values)
                withContext(Dispatchers.Main) {
                    if (id >= 0) {
                        toast("已插入 rowid=$id")
                        HistoryStore.add(ctx, s.sourcePath.substringAfterLast('/'), t, "INSERT", "", null, values.toString(), id)
                        refreshHistory()
                        loadTable(t, pageIndex)
                    } else {
                        toast("插入失败（可能主键冲突或类型不匹配）")
                    }
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) { toast("插入失败：${e.message}") }
            }
        }
    }

    fun writeBack() {
        val s = session ?: return
        scope.launch(Dispatchers.IO) {
            busy = true
            val ok = try {
                s.writeBack()
            } catch (t: Throwable) {
                false
            }
            withContext(Dispatchers.Main) {
                if (ok) {
                    toast("已写回原文件，原库已备份为 *.dbstudio.bak")
                    HistoryStore.add(ctx, s.sourcePath.substringAfterLast('/'), "", "WRITE_BACK", "", null, null, null)
                    refreshHistory()
                    // 重新载入，继续编辑
                    openDb(s.sourcePath, s.password)
                } else {
                    toast("写回失败：${RootShell.lastError ?: "权限或文件被占用"}")
                }
                busy = false
            }
        }
    }

    fun createBackup() {
        val s = session ?: return
        scope.launch(Dispatchers.IO) {
            val f = BackupStore.create(ctx, s)
            withContext(Dispatchers.Main) {
                if (f != null) {
                    toast("备份完成：${f.name}")
                    refreshBackups()
                } else {
                    toast("备份失败")
                }
            }
        }
    }

    fun restoreBackup(file: File) {
        val s = session ?: return
        scope.launch(Dispatchers.IO) {
            busy = true
            try {
                s.close()
                file.inputStream().use { input ->
                    s.workingFile.outputStream().use { input.copyTo(it) }
                }
                val ok = RootShell.writeBack(s.workingFile, s.sourcePath)
                withContext(Dispatchers.Main) {
                    if (ok) {
                        toast("已从备份恢复并写回原库")
                        openDb(s.sourcePath, s.password)
                    } else {
                        toast("恢复失败：${RootShell.lastError ?: "写回被拒绝"}")
                    }
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { toast("恢复失败：${t.message}") }
            } finally {
                withContext(Dispatchers.Main) { busy = false }
            }
        }
    }

    fun exportDb() {
        val s = session ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val name = s.sourcePath.substringAfterLast('/').ifBlank { "database.db" }
                val tmp = File(ctx.cacheDir, name)
                s.workingFile.inputStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
                val uri = BackupStore.exportToDownloads(ctx, tmp)
                withContext(Dispatchers.Main) {
                    toast(if (uri != null) "已导出到系统下载目录：$name" else "导出失败")
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { toast("导出失败：${t.message}") }
            }
        }
    }

    fun exportFile(f: File) {
        scope.launch(Dispatchers.IO) {
            val uri = BackupStore.exportToDownloads(ctx, f)
            withContext(Dispatchers.Main) {
                toast(if (uri != null) "已导出到系统下载目录：${f.name}" else "导出失败")
            }
        }
    }

    fun executeSql(sql: String, onResult: (String) -> Unit) {
        val s = session ?: return
        scope.launch(Dispatchers.IO) {
            busy = true
            val result = try {
                s.exec(sql)
            } catch (t: Throwable) {
                "执行失败：${t.message}"
            }
            withContext(Dispatchers.Main) {
                busy = false
                onResult(result)
                currentTable?.let { loadTable(it, pageIndex) }
            }
        }
    }

    fun refreshHistory() {
        history = HistoryStore.list(ctx)
    }

    fun refreshBackups() {
        backups = BackupStore.list(ctx)
    }

    fun undo(entry: HistoryEntry) {
        val s = session ?: return
        val rowid = entry.rowid ?: return
        if (entry.op != "UPDATE" || entry.column.isBlank()) {
            toast("该记录不支持自动撤销，可用备份恢复")
            return
        }
        scope.launch(Dispatchers.IO) {
            try {
                val ok = s.updateByKey(entry.table, "rowid = ?", arrayOf(rowid.toString()), entry.column, entry.oldValue)
                withContext(Dispatchers.Main) {
                    if (ok) {
                        toast("已撤销：${entry.table}.${entry.column} 恢复为 ${entry.oldValue}")
                        HistoryStore.remove(ctx, entry.id)
                        refreshHistory()
                        loadTable(entry.table, pageIndex)
                    } else {
                        toast("撤销失败")
                    }
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { toast("撤销失败：${t.message}") }
            }
        }
    }

    suspend fun sampleMonitor() {
        val s = session ?: return
        val targets = listOf("", "-wal", "-shm")
        val sb = StringBuilder()
        var latest = -1L
        for (suffix in targets) {
            val p = s.sourcePath + suffix
            val st = RootShell.stat(p)
            if (st == null) {
                sb.append(p.substringAfterLast('/')).append("  不可读\n")
            } else {
                sb.append(p.substringAfterLast('/'))
                    .append("  ").append(BackupStore.humanSize(st.size))
                    .append("  ").append(formatTime(st.mtime))
                    .append('\n')
                if (suffix.isEmpty()) latest = st.mtime
            }
        }
        sb.append("工作副本：").append(BackupStore.humanSize(s.workingFile.length()))
        if (lastMtime >= 0 && latest > 0 && latest != lastMtime) {
            monitorChanges += 1
        }
        if (latest > 0) lastMtime = latest
        sb.append("\n检测到外部变更：").append(monitorChanges).append(" 次")
        monitorText = sb.toString()
    }

    private fun formatTime(ms: Long): String =
        java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(ms))
}

enum class EditKind(val label: String) {
    AUTO("自动（沿用原类型）"),
    TEXT("文本 TEXT"),
    INTEGER("整数 INTEGER"),
    REAL("小数 REAL"),
    BLOB("BLOB（十六进制）"),
    NULL("NULL"),
}

/** 字节与十六进制互转（单元格输入输出用） */
object HexBytes {
    fun encode(bytes: ByteArray): String = com.rootdb.studio.data.Hex.encode(bytes)
    fun decode(text: String): ByteArray = com.rootdb.studio.data.Hex.decode(text)
}
