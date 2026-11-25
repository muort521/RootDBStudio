package com.rootdb.studio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rootdb.studio.AppController
import com.rootdb.studio.Screen
import com.rootdb.studio.data.BackupStore
import com.rootdb.studio.data.CryptoEngine
import com.rootdb.studio.data.CryptoSpec
import com.rootdb.studio.data.RowData
import com.rootdb.studio.data.ValueType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.basic.SearchCleanup
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DbScreen(c: AppController) {
    val s = c.session
    val name = s?.sourcePath?.substringAfterLast('/') ?: "数据库"
    val inTable = c.screen is Screen.Table
    Scaffold(
        topBar = {
            TopAppBar(
                title = if (inTable) (c.screen as Screen.Table).name else name,
                navigationIcon = {
                    BackIcon {
                        if (inTable) c.backToDbHome() else c.closeDb()
                    }
                },
                actions = {
                    Text(
                        if (c.session?.dirty == true) "未写回" else "已同步",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                },
            )
        },
    ) { padding ->
        if (inTable) {
            TableBody(c, (c.screen as Screen.Table).name, padding)
        } else {
            DbHomeBody(c, padding)
        }
        c.askPasswordFor?.let { dbPath ->
            PasswordDialog(
                path = dbPath,
                onOpen = { pw -> c.openDb(dbPath, pw) },
                onDismiss = { c.askPasswordFor = null; c.passwordError = null },
                error = c.passwordError,
                onEdit = { c.passwordError = null },
            )
        }
        c.pendingPasswordPath?.let { dbPath ->
            PasswordDialog(
                path = dbPath,
                onOpen = { pw -> c.openDb(dbPath, pw) },
                onDismiss = { c.pendingPasswordPath = null; c.passwordError = null },
                error = c.passwordError,
                onEdit = { c.passwordError = null },
            )
        }
    }
}

@Composable
private fun DbHomeBody(c: AppController, padding: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(padding)) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Chip("数据表", c.tab == 0) { c.tab = 0 }
            Chip("实时监控", c.tab == 1) { c.tab = 1 }
            Chip("备份恢复", c.tab == 2) { c.tab = 2 }
            Chip("历史记录", c.tab == 3) { c.tab = 3 }
        }
        when (c.tab) {
            1 -> MonitorTab(c)
            2 -> BackupTab(c)
            3 -> HistoryTab(c)
            else -> TablesTab(c)
        }
    }
}

@Composable
private fun TablesTab(c: AppController) {
    val s = c.session ?: return
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item {
            CardGroup {
                RowItem("源文件", s.sourcePath, showChevron = false)
                RowItem("加密方式", if (s.encrypted) "SQLCipher（已解密）" else "SQLite 明文", showChevron = false)
                RowItem("表 / 视图", "${c.tables.size} 个", showChevron = false)
            }
        }
        item { SectionTitle("数据表") }
        items(c.tables, key = { it.name }) { t ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                onClick = { c.openTable(t.name) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.name, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (t.type == "view") "视图" else "表",
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                    }
                    Chevron()
                }
            }
        }
        if (c.tables.isEmpty()) item { EmptyHint("没有可访问的表") }
    }
}

@Composable
private fun MonitorTab(c: AppController) {
    LaunchedEffect(c.tab) {
        if (c.tab == 1) {
            while (true) {
                withContext(Dispatchers.IO) { c.sampleMonitor() }
                delay(1500)
            }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            insideMargin = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            onClick = null,
        ) {
            Text("实时监控", fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                c.monitorText,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
        CardGroup {
            RowItem(
                "采样间隔",
                "1.5 秒（轮询主库、WAL、SHM 的大小与修改时间）",
                showChevron = false,
                subtitleMaxLines = 2,
            )
            RowItem(
                "说明",
                "检测到外部修改时，可通过“备份恢复”回滚，或重新打开数据库载入最新数据",
                showChevron = false,
                subtitleMaxLines = 4,
            )
        }
    }
}

@Composable
private fun BackupTab(c: AppController) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { c.createBackup() }) { Text("新建备份") }
            Button(onClick = { c.exportDb() }) { Text("导出到下载") }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
            items(c.backups, key = { it.absolutePath }) { f ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    insideMargin = PaddingValues(16.dp),
                    onClick = null,
                ) {
                    Text(f.name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${BackupStore.humanSize(f.length())} · ${
                            java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(f.lastModified()))
                        }",
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { c.restoreBackup(f) }) { Text("恢复") }
                        Button(onClick = { c.exportFile(f) }) { Text("导出") }
                        Button(onClick = {
                            BackupStore.delete(f)
                            c.refreshBackups()
                        }) { Text("删除") }
                    }
                }
            }
            if (c.backups.isEmpty()) item { EmptyHint("暂无备份") }
        }
    }
}

@Composable
private fun HistoryTab(c: AppController) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            CardGroup {
                RowItem(
                    "清空历史", "仅清空记录，不影响数据库内容",
                    onClick = {
                        com.rootdb.studio.data.HistoryStore.clear(c.ctx)
                        c.refreshHistory()
                    },
                )
            }
        }
        items(c.history, key = { it.id }) { e ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
                insideMargin = PaddingValues(16.dp),
                onClick = null,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${e.op} · ${e.table}${if (e.column.isNotBlank()) "." + e.column else ""}",
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${e.timeText} · ${e.db}${e.rowid?.let { " · rowid=$it" } ?: ""}",
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                        if (e.oldValue != null || e.newValue != null) {
                            Text(
                                "${e.oldValue ?: "-"} → ${e.newValue ?: "-"}",
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                            )
                        }
                    }
                    if (e.op == "UPDATE" && e.rowid != null) {
                        Button(onClick = { c.undo(e) }) { Text("撤销") }
                    }
                }
            }
        }
        if (c.history.isEmpty()) item { EmptyHint("暂无操作记录") }
    }
}

@Composable
private fun TableBody(c: AppController, table: String, padding: PaddingValues) {
    var editing by remember { mutableStateOf<Pair<RowData, String>?>(null) }
    var showInsert by remember { mutableStateOf(false) }
    var showSql by remember { mutableStateOf(false) }
    var showCrypto by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<RowData?>(null) }
    var sqlResult by remember { mutableStateOf("") }

    val showPlain = c.revealPlain && c.crypto.enabled

    Column(Modifier.fillMaxSize().padding(padding)) {
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Chip("新增行", false) { showInsert = true }
            Chip("SQL", false) { showSql = true }
            Chip("加密设置", false) { showCrypto = true }
            Chip(if (c.revealPlain) "解密显示：开" else "解密显示：关", c.revealPlain) {
                c.revealPlain = !c.revealPlain
            }
            Chip("写回原库", false) { c.writeBack() }
            Chip("备份", false) { c.createBackup() }
        }

        TableSearchBar(
            query = c.tableSearch,
            matched = c.total,
            onSearch = { c.setTableSearch(it) },
        )

        // 表格横向滚动：标准滚动容器（带惯性），底部是可拖动的位置条
        val hs = rememberScrollState()
        val scope = rememberCoroutineScope()
        var viewportPx by remember { mutableFloatStateOf(0f) }
        val trackColor = MiuixTheme.colorScheme.dividerLine
        val gridColor = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.32f)
        val thumbColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.55f)
        // 表格总宽：横向滚动容器里 fillMaxWidth 会被无限约束压成 0，网格线必须显式给宽度
        val contentWidth = 78.dp + 156.dp * c.tableColumns.size

        Box(
            Modifier.weight(1f).fillMaxWidth()
                .onSizeChanged { viewportPx = it.width.toFloat() },
        ) {
            Column(Modifier.fillMaxSize().horizontalScroll(hs)) {
                Row(
                    Modifier.background(MiuixTheme.colorScheme.secondaryContainer),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HeaderCell("rowid", 78.dp)
                    c.tableColumns.forEach { col -> HeaderCell(col, 156.dp) }
                }
                Box(Modifier.width(contentWidth).height(1.dp).background(gridColor))
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    items(c.rows) { row ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CellBox(
                                text = row.rowid?.toString() ?: "-",
                                width = 78.dp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                                onClick = null,
                            )
                            row.cells.forEachIndexed { idx, cell ->
                                val colName = c.tableColumns.getOrNull(idx) ?: "col$idx"
                                val text = if (showPlain && cell.type == ValueType.TEXT) {
                                    CryptoEngine.decryptTextOrNull(c.crypto, cell.text ?: "") ?: cell.display
                                } else {
                                    cell.display
                                }
                                CellBox(
                                    text = text,
                                    width = 156.dp,
                                    color = if (cell.type == ValueType.NULL) MiuixTheme.colorScheme.onBackgroundVariant
                                    else MiuixTheme.colorScheme.onBackground,
                                    onClick = {
                                        if (row.rowid == null) {
                                            c.toast("该表无 rowid（WITHOUT ROWID），请用 SQL 编辑")
                                        } else {
                                            editing = row to colName
                                        }
                                    },
                                )
                            }
                        }
                        Box(
                            Modifier.width(contentWidth).height(0.8.dp)
                                .background(gridColor),
                        )
                    }
                    if (c.rows.isEmpty()) item { EmptyHint("该表没有数据或当前页为空") }
                }
            }
            val maxScroll = hs.maxValue
            if (maxScroll > 0 && viewportPx > 0f) {
                Box(
                    Modifier.align(Alignment.BottomCenter)
                        .fillMaxWidth().height(16.dp)
                        .pointerInput(maxScroll, viewportPx) {
                            if (maxScroll <= 0 || viewportPx <= 0f) return@pointerInput
                            val w = size.width.toFloat()
                            val thumbW = (w * viewportPx / (viewportPx + maxScroll)).coerceAtLeast(28f)
                            val usable = (w - thumbW).coerceAtLeast(1f)
                            fun target(x: Float): Float =
                                ((x - thumbW / 2f) / usable).coerceIn(0f, 1f) * maxScroll
                            detectDragGestures(
                                onDragStart = { pos -> scope.launch { hs.scrollTo(target(pos.x).toInt()) } },
                                onDrag = { change, _ ->
                                    change.consume()
                                    scope.launch { hs.scrollTo(target(change.position.x).toInt()) }
                                },
                            )
                        },
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val cy = size.height / 2f
                        val barH = 3.dp.toPx()
                        drawRoundRect(
                            color = trackColor,
                            topLeft = Offset(0f, cy - barH / 2f),
                            size = Size(size.width, barH),
                            cornerRadius = CornerRadius(barH / 2, barH / 2),
                        )
                        val frac = (viewportPx / (viewportPx + maxScroll)).coerceIn(0.05f, 1f)
                        val thumbW = (size.width * frac).coerceAtLeast(28f)
                        val usable = (size.width - thumbW).coerceAtLeast(1f)
                        val x = usable * (hs.value.toFloat() / maxScroll.toFloat())
                        drawRoundRect(
                            color = thumbColor,
                            topLeft = Offset(x, cy - barH / 2f),
                            size = Size(thumbW, barH),
                            cornerRadius = CornerRadius(barH / 2, barH / 2),
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { c.prevPage() }, enabled = c.pageIndex > 0) { Text("上一页") }
            Text(
                "第 ${c.pageIndex + 1} 页 / ${c.total ?: "?"} 行",
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
            Button(onClick = { c.nextPage() }) { Text("下一页") }
        }
    }

    // 编辑单元格
    editing?.let { (row, colName) ->
        val colIdx = c.tableColumns.indexOf(colName)
        val cell = row.cells.getOrNull(colIdx) ?: return@let
        val colInfo = c.columnsInfo.firstOrNull { it.name == colName }
        CellEditorDialog(
            columnName = colName,
            columnType = colInfo?.type?.ifBlank { "TEXT" } ?: "TEXT",
            cell = cell,
            crypto = c.crypto,
            onSave = { input, kind, encrypt ->
                row.rowid?.let { rid -> c.saveCell(colName, rid, cell, input, kind, encrypt) }
                editing = null
            },
            onDelete = { deleteTarget = row; editing = null },
            onDismiss = { editing = null },
        )
    }

    deleteTarget?.let { row ->
        ConfirmDialog(
            title = "删除记录",
            summary = "将删除 rowid=${row.rowid}，可先在“备份”中留档。",
            confirmText = "删除",
            onConfirm = {
                row.rowid?.let { c.deleteRow(it) }
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }

    if (showInsert) {
        InsertRowDialog(
            columns = c.columnsInfo,
            onInsert = { values ->
                c.insertRow(values)
                showInsert = false
            },
            onDismiss = { showInsert = false },
        )
    }

    if (showSql) {
        SqlDialog(
            onRun = { sql, cb -> c.executeSql(sql, cb) },
            onDismiss = { showSql = false },
        )
    }

    if (showCrypto) {
        CryptoDialog(
            initial = c.crypto,
            onSave = { spec: CryptoSpec ->
                c.saveCrypto(spec)
                showCrypto = false
            },
            onDismiss = { showCrypto = false },
        )
    }
}

@Composable
private fun TableSearchBar(query: String, matched: Long?, onSearch: (String) -> Unit) {
    var text by remember { mutableStateOf(query) }
    // 外部（如撤销/刷新）改了关键字时同步回输入框
    LaunchedEffect(query) { if (text.trim() != query.trim()) text = query }
    // 输入停顿后再查，几千上万行也不至于每个按键都扫一遍
    LaunchedEffect(text) {
        delay(450)
        if (text.trim() != query.trim()) onSearch(text)
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        onClick = null,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = MiuixIcons.Basic.Search,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MiuixTheme.colorScheme.onBackgroundVariant,
            )
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (text.isEmpty()) {
                    Text(
                        "在整个表中搜索（所有列模糊匹配）",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MiuixTheme.textStyles.main.copy(
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onBackground,
                    ),
                    cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch(text) }),
                )
            }
            if (text.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                IconButton(
                    onClick = { text = ""; onSearch("") },
                    minWidth = 24.dp,
                    minHeight = 24.dp,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Basic.SearchCleanup,
                        contentDescription = "清空搜索",
                        modifier = Modifier.size(16.dp),
                        tint = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
            } else if (query.isNotEmpty() && matched != null) {
                Text(
                    "$matched 行",
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, width: androidx.compose.ui.unit.Dp) {
    val line = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.32f)
    Box(
        Modifier
            .width(width)
            .drawBehind {
                val x = size.width - 0.6.dp.toPx()
                drawLine(line, Offset(x, 0f), Offset(x, size.height), 0.6.dp.toPx())
            }
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Text(text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CellBox(text: String, width: androidx.compose.ui.unit.Dp, color: Color, onClick: (() -> Unit)?) {
    val line = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.32f)
    Box(
        Modifier
            .width(width)
            .drawBehind {
                val x = size.width - 0.6.dp.toPx()
                drawLine(line, Offset(x, 0f), Offset(x, size.height), 0.6.dp.toPx())
            }
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 10.dp, vertical = 11.dp),
    ) {
        Text(text, fontSize = 12.sp, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}