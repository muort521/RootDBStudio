package com.rootdb.studio

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
import com.rootdb.studio.data.DbKeyStore
import com.rootdb.studio.data.SavedDbKey
import com.rootdb.studio.data.HistoryEntry
import com.rootdb.studio.data.HistoryStore
import com.rootdb.studio.data.PathPrefs
import com.rootdb.studio.data.RecentStore
import com.rootdb.studio.data.RowData
import com.rootdb.studio.data.SavedCrypto
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

    /** 独立的「加密与密钥」页面；[tab] 0=字段解密，1=数据库密钥 */
    data class Crypto(val tab: Int) : Screen
}

class AppController(val ctx: Context, val scope: CoroutineScope) {

    companion object {
        const val PAGE_SIZE = 50
    }

    // 浏览器
    var rooted by mutableStateOf(false)
    var rootText by mutableStateOf(ctx.getString(R.string.root_detecting))
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

    /** 当前表的搜索关键字（对全部非 BLOB 列模糊匹配，分页也按匹配结果） */
    var tableSearchQuery by mutableStateOf("")
        private set
    val tableSearch: String get() = tableSearchQuery

    // 加密（按「库 + 表」独立配置，进入表时载入）
    var crypto by mutableStateOf(CryptoSpec())
    var revealPlain by mutableStateOf(false)

    /** 已保存的表级配置，供「加密设置」里的管理列表展示 */
    var savedCryptos by mutableStateOf<List<SavedCrypto>>(emptyList())
        private set

    /** 已保存的整库密钥（只含库路径与长度，密码本体在 Keystore 加密存储） */
    var savedDbKeys by mutableStateOf<List<SavedDbKey>>(emptyList())
        private set

    // 历史 / 备份 / 监控
    var history by mutableStateOf<List<HistoryEntry>>(emptyList())
    var backups by mutableStateOf<List<File>>(emptyList())
    var monitorText by mutableStateOf(ctx.getString(R.string.monitor_waiting))
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
            rootText = ctx.getString(if (ok) R.string.root_ok_desc else R.string.root_no_desc)
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
                toast(ctx.getString(R.string.err_grant_page))
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
        CryptoPrefs.save(ctx, session?.sourcePath, currentTable, spec)
        refreshSavedCryptos()
        toast(ctx.getString(R.string.toast_crypto_saved, ctx.getString(spec.summaryRes)))
    }

    fun refreshSavedCryptos() {
        savedCryptos = CryptoPrefs.list(ctx)
    }

    /** 删除某张表已保存的加密配置；删的就是当前表时立刻回退为「不加密」 */
    fun deleteSavedCrypto(item: SavedCrypto) {
        CryptoPrefs.remove(ctx, item.dbPath, item.table)
        if (item.dbPath == session?.sourcePath && item.table == currentTable) {
            crypto = CryptoSpec()
        }
        refreshSavedCryptos()
        toast(ctx.getString(R.string.toast_crypto_deleted, item.table))
    }

    fun clearSavedCryptos() {
        CryptoPrefs.clearAll(ctx)
        crypto = CryptoSpec()
        refreshSavedCryptos()
        toast(ctx.getString(R.string.toast_crypto_cleared))
    }

    // ── 独立的「加密与密钥」页面 ──────────────────────────────────────
    /** 打开加密页面：tab 0=字段解密（当前表），1=整库密钥 */
    fun openCrypto(tab: Int = 0) {
        refreshSavedCryptos()
        refreshSavedDbKeys()
        screen = Screen.Crypto(tab)
    }

    /** 从加密页面返回：优先回到刚才的表，否则回库首页 */
    fun closeCrypto() {
        val t = currentTable
        screen = if (t != null) Screen.Table(t) else Screen.DbHome
    }

    fun refreshSavedDbKeys() {
        savedDbKeys = DbKeyStore.list(ctx)
    }

    fun deleteSavedDbKey(item: SavedDbKey) {
        DbKeyStore.remove(ctx, item.dbPath)
        refreshSavedDbKeys()
        toast(ctx.getString(R.string.toast_dbkey_deleted, item.dbName))
    }

    /**
     * 修改某条已保存的整库密钥。
     * 能校验就直接用新密码打开一次（打不开就不保存）；当前正打开着的库不能重开同一工作副本，
     * 只更新记录，下次打开时生效（失效时会自动清理并提示重输）。
     */
    fun updateSavedDbKey(item: SavedDbKey, newPassword: String, onResult: (String?) -> Unit) {
        val pwd = newPassword.trim()
        if (pwd.isEmpty()) {
            onResult(ctx.getString(R.string.crypto_db_edit_empty))
            return
        }
        if (pwd == DbKeyStore.get(ctx, item.dbPath)) {
            onResult(null)
            return
        }
        scope.launch(Dispatchers.IO) {
            val error: String? = if (session?.sourcePath == item.dbPath) {
                null
            } else {
                runCatching {
                    val probe = DbSession.open(ctx, item.dbPath, pwd, withJournal = false)
                    probe.close()
                }.fold({ null }, { ctx.getString(R.string.crypto_db_edit_wrong) })
            }
            withContext(Dispatchers.Main) {
                if (error == null) {
                    DbKeyStore.save(ctx, item.dbPath, pwd)
                    refreshSavedDbKeys()
                    toast(ctx.getString(R.string.toast_dbkey_updated, item.dbName))
                }
                onResult(error)
            }
        }
    }

    fun clearSavedDbKeys() {
        DbKeyStore.clearAll(ctx)
        refreshSavedDbKeys()
        toast(ctx.getString(R.string.toast_dbkey_cleared))
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
                    toast(ctx.getString(R.string.browser_dir_inaccessible, rootFailureText(target)))
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
        rootText = ctx.getString(R.string.root_rechecking)
        scope.launch(Dispatchers.IO) {
            val ok = RootShell.hasRoot
            withContext(Dispatchers.Main) {
                rooted = ok
                rootText = ctx.getString(if (ok) R.string.root_ok_desc else R.string.root_no_desc)
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
            // 手输密码优先；没传密码时用该库已保存的密钥自动尝试
            val remembered = if (password == null) DbKeyStore.get(ctx, dbPath) else null
            val effective = password ?: remembered
            try {
                session?.close()
                val s = DbSession.open(ctx, dbPath, effective)
                val t = s.tables()
                if (password != null) DbKeyStore.save(ctx, dbPath, password)
                withContext(Dispatchers.Main) {
                    if (password != null) refreshSavedDbKeys()
                    session = s
                    tables = t
                    currentTable = null
                    rows = emptyList()
                    total = null
                    tableColumns = emptyList()
                    columnsInfo = emptyList()
                    crypto = CryptoSpec()
                    screen = Screen.DbHome
                    tab = 0
                    askPasswordFor = null
                    pendingPasswordPath = null
                    tableSearchQuery = ""
                    toast(
                        ctx.getString(
                            when {
                                remembered != null && password == null -> R.string.toast_opened_with_saved_key
                                s.encrypted -> R.string.toast_opened_decrypted
                                else -> R.string.toast_opened
                            },
                            dbPath.substringAfterLast('/'),
                        )
                    )
                }
            } catch (e: DbOpenException) {
                withContext(Dispatchers.Main) {
                    if (remembered != null && password == null) {
                        // 记住的密钥已失效（库被换过密钥/文件被替换）：清掉并让用户重新输入
                        DbKeyStore.remove(ctx, dbPath)
                        refreshSavedDbKeys()
                        askPasswordFor = dbPath
                        passwordError = ctx.getString(R.string.pwd_error_saved_key_stale)
                    } else if (e.needPassword && password == null) {
                        pendingPasswordPath = dbPath
                        passwordError = null
                    } else if (effective != null) {
                        // 密钥错误：错误留在对话框内部显示，对话框保持打开方便直接改密钥重试
                        if (askPasswordFor != null) askPasswordFor = dbPath else pendingPasswordPath = dbPath
                        val detail = e.detail.orEmpty()
                        passwordError = when {
                            e.resId == R.string.err_wrong_key ||
                                detail.contains("not a database") || detail.contains("encrypted", true) ->
                                ctx.getString(R.string.pwd_error_wrong_key)

                            e.resId in setOf(
                                R.string.err_cannot_read_source,
                                R.string.err_read_schema_encrypted,
                                R.string.err_sqlcipher_load,
                            ) -> ctx.getString(e.resId)

                            else -> detail.ifBlank { ctx.getString(R.string.pwd_error_default) }
                        }
                    } else {
                        toast(errorTextOf(e))
                    }
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    val text = ctx.getString(R.string.toast_open_failed_detail, t.message.orEmpty())
                    if (effective != null) passwordError = text else toast(text)
                }
            } finally {
                withContext(Dispatchers.Main) { busy = false }
            }
        }
    }

    /** shell 失败细节：超时/被拒绝时用本地化文案，否则用调用方给的兜底文案 */
    private fun rootFailureText(fallback: String): String =
        if (RootShell.lastError != null) ctx.getString(R.string.err_root_timeout) else fallback

    /** 把 [DbOpenException] 按当前语言格式化成可展示文本 */
    private fun errorTextOf(e: DbOpenException): String = when (e.resId) {
        R.string.err_cannot_open_db, R.string.err_wrong_key_or_format ->
            ctx.getString(e.resId, e.detail.orEmpty())

        else -> ctx.getString(e.resId)
    }

    /** 从其他应用打开时：本地副本路径 -> 来源 Uri（用于「写回原库」后再同步回来源应用） */
    private val importedUris = java.util.concurrent.ConcurrentHashMap<String, Uri>()

    /** 导入副本对应的原始文件名，用于界面显示「来自其他应用」而不是缓存路径 */
    private val importedLabels = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun sourceLabel(path: String): String = importedLabels[path] ?: path

    /**
     * 处理其他应用（MT 管理器、文件管理器、分享面板）送来的 Uri。
     * file:// 直接按路径打开；content:// 先复制到应用缓存副本，再走正常的打开流程，
     * 这样不依赖外部 provider 在编辑期间持续授权读，也能正常被 Root 权限的 SQLCipher 打开。
     */
    fun openFromUri(uri: Uri) {
        when (uri.scheme?.lowercase()) {
            "file" -> {
                val path = uri.path
                if (path.isNullOrBlank()) {
                    toast(ctx.getString(R.string.toast_import_failed))
                } else {
                    openDb(path, null)
                }
            }

            else -> importAndOpen(uri)
        }
    }

    private fun importAndOpen(uri: Uri) {
        scope.launch(Dispatchers.IO) {
            val rawName = importDisplayName(uri).orEmpty()
            val safeName = rawName.replace(Regex("[\\\\/:*?\"<>|\\s]"), "_")
                .ifBlank { "imported_${System.currentTimeMillis()}.db" }
            // 能拿到真实路径就直接开原文件（有 Root，读写都不受 provider 授权限制），
            // 这样「写回原库」就是写回用户真正选中的那个文件，而不是临时副本。
            val realPath = importRealPath(uri)
            if (realPath != null) {
                withContext(Dispatchers.Main) {
                    toast(
                        ctx.getString(
                            R.string.toast_open_from_app,
                            safeName
                        )
                    )
                    openDb(realPath, null)
                }
                return@launch
            }
            val dir = File(ctx.cacheDir, "imported").apply { mkdirs() }
            val target = File(dir, safeName)
            val ok = runCatching {
                ctx.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { input.copyTo(it) }
                } != null && target.length() > 0
            }.getOrDefault(false)
            withContext(Dispatchers.Main) {
                if (!ok) {
                    runCatching { target.delete() }
                    toast(ctx.getString(R.string.toast_import_failed))
                    return@withContext
                }
                importedUris[target.absolutePath] = uri
                importedLabels[target.absolutePath] = ctx.getString(R.string.db_source_imported, safeName)
                toast(ctx.getString(R.string.toast_import_opened, safeName))
                openDb(target.absolutePath, null)
            }
        }
    }

    private fun importDisplayName(uri: Uri): String? = runCatching {
        when (uri.scheme?.lowercase()) {
            "file" -> uri.lastPathSegment
            else -> ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null }
        }
    }.getOrNull()

    /** 部分 provider（MediaStore、部分文件管理器）能查到底层真实路径，能用就优先开原文件 */
    private fun importRealPath(uri: Uri): String? = runCatching {
        val cursor = ctx.contentResolver.query(
            uri,
            arrayOf(android.provider.MediaStore.MediaColumns.DATA),
            null,
            null,
            null,
        ) ?: return@runCatching null
        cursor.use { c ->
            if (!c.moveToFirst() || c.isNull(0)) return@use null
            val p = c.getString(0)
            if (p.startsWith("/") && RootShell.isRegularFile(p)) p else null
        }
    }.getOrNull()

    /** 把编辑后的文件写回来源应用；provider 不支持写入时返回 false，不影响本地副本已写回 */
    private fun pushBackToSource(uri: Uri, file: File): Boolean = runCatching {
        val opened = ctx.contentResolver.openOutputStream(uri, "wt")
            ?: ctx.contentResolver.openOutputStream(uri)
            ?: return@runCatching false
        opened.use { out -> file.inputStream().use { it.copyTo(out) } }
        true
    }.getOrDefault(false)

    fun closeDb() {
        scope.launch(Dispatchers.IO) { session?.close() }
        // 从其他应用导入的临时副本，关闭时清掉，避免在缓存里长期留着明文库
        session?.sourcePath?.let { p ->
            if (importedUris.remove(p) != null) runCatching { File(p).delete() }
            importedLabels.remove(p)
        }
        session = null
        tables = emptyList()
        rows = emptyList()
        tableColumns = emptyList()
        currentTable = null
        crypto = CryptoSpec()
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
        val filter = tableSearch
        scope.launch(Dispatchers.IO) {
            busy = true
            try {
                val p = s.page(name, page * PAGE_SIZE, PAGE_SIZE, filter)
                val info = s.columns(name)
                withContext(Dispatchers.Main) {
                    tableColumns = p.columns
                    rows = p.rows
                    total = p.total
                    columnsInfo = info
                    currentTable = name
                    // 每张表带出自己的解密设置；换表时重载，避免用上一张表的算法/密钥
                    crypto = CryptoPrefs.load(ctx, s.sourcePath, name)
                    pageIndex = page
                    screen = Screen.Table(name)
                    tab = 0
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { toast(ctx.getString(R.string.toast_read_table_failed, t.message.orEmpty())) }
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

    /** 打开某张表：清掉上张表的搜索词，再读第一页 */
    fun openTable(name: String) {
        tableSearchQuery = ""
        loadTable(name, 0)
    }

    /** 表格内搜索：关键字变化后从第一页重新查（空串即恢复全表） */
    fun setTableSearch(keyword: String) {
        val q = keyword.trim()
        if (q == tableSearchQuery) return
        tableSearchQuery = q
        currentTable?.let { loadTable(it, 0) }
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
            EditKind.INTEGER -> input.toLongOrNull() ?: throw IllegalArgumentException(ctx.getString(R.string.err_not_integer))
            EditKind.REAL -> input.toDoubleOrNull() ?: throw IllegalArgumentException(ctx.getString(R.string.err_not_real))
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
                        toast(ctx.getString(R.string.toast_updated, column))
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
                        toast(ctx.getString(R.string.toast_update_row_missing))
                    }
                }
            } catch (t2: Throwable) {
                withContext(Dispatchers.Main) { toast(ctx.getString(R.string.toast_update_failed, t2.message.orEmpty())) }
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
                        toast(ctx.getString(R.string.toast_deleted, rowid))
                        HistoryStore.add(ctx, s.sourcePath.substringAfterLast('/'), t, "DELETE", "", "rowid=$rowid", "", rowid)
                        refreshHistory()
                        loadTable(t, pageIndex)
                    } else {
                        toast(ctx.getString(R.string.toast_delete_failed))
                    }
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) { toast(ctx.getString(R.string.toast_delete_failed_detail, e.message.orEmpty())) }
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
                        toast(ctx.getString(R.string.toast_inserted, id))
                        HistoryStore.add(ctx, s.sourcePath.substringAfterLast('/'), t, "INSERT", "", null, values.toString(), id)
                        refreshHistory()
                        loadTable(t, pageIndex)
                    } else {
                        toast(ctx.getString(R.string.toast_insert_failed))
                    }
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) { toast(ctx.getString(R.string.toast_insert_failed_detail, e.message.orEmpty())) }
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
                    val sourceUri = importedUris[s.sourcePath]
                    val pushed = if (sourceUri != null) pushBackToSource(sourceUri, File(s.sourcePath)) else false
                    when {
                        sourceUri == null -> toast(ctx.getString(R.string.toast_write_back_ok))
                        pushed -> toast(ctx.getString(R.string.toast_write_back_to_source, s.sourcePath.substringAfterLast('/')))
                        else -> toast(ctx.getString(R.string.toast_write_back_source_failed))
                    }
                    HistoryStore.add(ctx, s.sourcePath.substringAfterLast('/'), "", "WRITE_BACK", "", null, null, null)
                    refreshHistory()
                    // 重新载入，继续编辑
                    openDb(s.sourcePath, s.password)
                } else {
                    toast(ctx.getString(R.string.toast_write_back_failed, rootFailureText(ctx.getString(R.string.err_permission_or_locked))))
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
                    toast(ctx.getString(R.string.toast_backup_done, f.name))
                    refreshBackups()
                } else {
                    toast(ctx.getString(R.string.toast_backup_failed))
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
                        toast(ctx.getString(R.string.toast_restored))
                        openDb(s.sourcePath, s.password)
                    } else {
                        toast(ctx.getString(R.string.toast_restore_failed, rootFailureText(ctx.getString(R.string.err_write_denied))))
                    }
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { toast(ctx.getString(R.string.toast_restore_failed, t.message.orEmpty())) }
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
                    toast(if (uri != null) ctx.getString(R.string.toast_exported, name) else ctx.getString(R.string.toast_export_failed))
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { toast(ctx.getString(R.string.toast_export_failed_detail, t.message.orEmpty())) }
            }
        }
    }

    fun exportFile(f: File) {
        scope.launch(Dispatchers.IO) {
            val uri = BackupStore.exportToDownloads(ctx, f)
            withContext(Dispatchers.Main) {
                toast(if (uri != null) ctx.getString(R.string.toast_exported, f.name) else ctx.getString(R.string.toast_export_failed))
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
                ctx.getString(R.string.err_sql_exec_failed, t.message.orEmpty())
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
            toast(ctx.getString(R.string.toast_undo_unsupported))
            return
        }
        scope.launch(Dispatchers.IO) {
            try {
                val ok = s.updateByKey(entry.table, "rowid = ?", arrayOf(rowid.toString()), entry.column, entry.oldValue)
                withContext(Dispatchers.Main) {
                    if (ok) {
                        toast(ctx.getString(R.string.toast_undo_ok, entry.table, entry.column, entry.oldValue.orEmpty()))
                        HistoryStore.remove(ctx, entry.id)
                        refreshHistory()
                        loadTable(entry.table, pageIndex)
                    } else {
                        toast(ctx.getString(R.string.toast_undo_failed))
                    }
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { toast(ctx.getString(R.string.toast_undo_failed_detail, t.message.orEmpty())) }
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
                sb.append(p.substringAfterLast('/' )).append(ctx.getString(R.string.monitor_unreadable)).append("\n")
            } else {
                sb.append(p.substringAfterLast('/'))
                    .append("  ").append(BackupStore.humanSize(st.size))
                    .append("  ").append(formatTime(st.mtime))
                    .append('\n')
                if (suffix.isEmpty()) latest = st.mtime
            }
        }
        sb.append(ctx.getString(R.string.monitor_working_copy, BackupStore.humanSize(s.workingFile.length())))
        if (lastMtime >= 0 && latest > 0 && latest != lastMtime) {
            monitorChanges += 1
        }
        if (latest > 0) lastMtime = latest
        sb.append("\n").append(ctx.getString(R.string.monitor_external_changes, monitorChanges))
        monitorText = sb.toString()
    }

    private fun formatTime(ms: Long): String =
        java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(ms))
}

/** 单元格编辑时的类型选择；界面文案在 Dialogs.kt 里按当前语言取资源 */
enum class EditKind {
    AUTO, TEXT, INTEGER, REAL, BLOB, NULL,
}

/** 字节与十六进制互转（单元格输入输出用） */
object HexBytes {
    fun encode(bytes: ByteArray): String = com.rootdb.studio.data.Hex.encode(bytes)
    fun decode(text: String): ByteArray = com.rootdb.studio.data.Hex.decode(text)
}
