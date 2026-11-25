package com.rootdb.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rootdb.studio.AppController
import com.rootdb.studio.data.BackupStore
import com.rootdb.studio.root.RootShell
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.basic.SearchCleanup
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 首页：只放入口与最近访问，真正的文件浏览在独立的 [BrowserScreen]。
 */
@Composable
fun HomeScreen(c: AppController) {
    Scaffold(
        topBar = { SmallTopAppBar(title = "数据库工坊") },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 28.dp),
        ) {
            item { RootCard(c) }
            item {
                CardGroup {
                    RowItem(
                        "所有文件访问权限",
                        if (c.allFilesGranted) "已授予，可浏览 /storage/emulated/0 下任意文件" else "未授予，内部存储目录可能无法列出",
                        value = if (c.allFilesGranted) "已授予" else "去授权",
                        onClick = { c.requestAllFilesAccess() },
                    )
                    RowItem(
                        "打开指定路径",
                        "手动输入 /data/data/... 等路径",
                        onClick = { c.showPathInput = true },
                    )
                }
            }
            item { SectionTitle("快捷入口") }
            item {
                CardGroup {
                    QuickRow(c, "/", "根目录（系统全部文件）", AppIcons.public)
                    QuickRow(c, "/data/data", "应用私有数据（含各 App 数据库）", AppIcons.storage)
                    QuickRow(c, "/data/system", "系统设置与账号数据库", Icons.Filled.Settings)
                    QuickRow(c, "/storage/emulated/0", "内部存储（无需 root）", AppIcons.sd)
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 26.dp, end = 18.dp, top = 16.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "最近访问（最多 10 条）",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (c.recent.isNotEmpty()) {
                        Text(
                            "清空",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { c.clearRecent() }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            if (c.recent.isEmpty()) {
                item { EmptyHint("还没有浏览记录，可从上方快捷入口或「打开指定路径」开始") }
            }
            itemsIndexed(c.recent, key = { index, p -> "$p#$index" }) { _, recentPath ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 11.dp),
                    onClick = { c.openBrowser(recentPath) },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = AppIcons.folder,
                            contentDescription = "目录",
                            modifier = Modifier.size(24.dp),
                            tint = MiuixTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                recentPath.substringAfterLast('/').ifBlank { "/" },
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                recentPath,
                                fontSize = 11.sp,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Chevron()
                    }
                }
            }
        }
    }

    PathDialogHost(c)
}

/**
 * 文件浏览页：搜索框与「上一级」固定在列表上方，滚动、软键盘都不会把它们挡住。
 */
@Composable
fun BrowserScreen(c: AppController) {
    val listState = rememberLazyListState()

    // 持续记录滚动位置，供返回上级时还原
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                c.visibleIndex = index
                c.visibleOffset = offset
            }
    }

    // 目录加载完成后：回到该目录上次的滚动位置；首次进入的目录回到顶部
    var restoredFor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(c.path, c.listing) {
        if (!c.listing && restoredFor != c.path) {
            restoredFor = c.path
            val memo = c.scrollOf(c.path)
            runCatching {
                if (memo == null || (memo.first == 0 && memo.second == 0)) {
                    listState.scrollToItem(0)
                } else {
                    listState.scrollToItem(memo.first, memo.second)
                }
            }
        }
    }

    // 过滤条件变化时回到列表顶部，避免结果被滚动位置挡住
    LaunchedEffect(c.searchQuery) {
        if (c.searchQuery.isNotEmpty()) runCatching { listState.scrollToItem(0) }
    }

    val dirName = when (c.path) {
        "/storage/emulated/0" -> "内部存储"
        else -> c.path.substringAfterLast('/').ifBlank { "/" }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = dirName,
                navigationIcon = { BackIcon { c.backFromBrowser() } },
                actions = {
                    Text(
                        "刷新",
                        fontSize = 15.sp,
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { c.refreshPath() }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // ── 固定在顶部的操作区 ──────────────────────────────
            Text(
                c.path,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 2.dp),
            )
            DirSearchBar(c)
            // 内部存储根目录已经是用户视角的顶层，不再提供「上一级」
            if (c.path != "/" && c.path.isNotEmpty() && c.path != "/storage/emulated/0") {
                CardGroup {
                    RowItem(
                        title = "上一级",
                        subtitle = c.path.substringBeforeLast('/').ifBlank { "/" },
                        showChevron = false,
                        subtitleMaxLines = 1,
                        icon = Icons.Filled.ArrowUpward,
                        iconTint = MiuixTheme.colorScheme.onBackgroundVariant,
                        onClick = { c.goUp() },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))

            // ── 可滚动的目录内容 ──────────────────────────────
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                if (c.listing) {
                    item { EmptyHint("正在读取目录…") }
                }
                val query = c.searchQuery.trim()
                val shown = c.visibleEntries
                if (!c.listing && c.entries.isEmpty()) {
                    item { EmptyHint("目录为空或权限不足。若为 /data/data，请确认已授予 root。") }
                }
                if (!c.listing && c.entries.isNotEmpty() && query.isNotEmpty()) {
                    item { EmptyHint("匹配「$query」：${shown.size} / ${c.entries.size} 项") }
                    if (shown.isEmpty()) item { EmptyHint("没有名称包含「$query」的条目") }
                }
                itemsIndexed(shown, key = { index, entry -> entry.path + "#" + index }) { _, entry ->
                    val kind = fileKindOf(entry.name, entry.isDir, entry.isLink)
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
                        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 11.dp),
                        onClick = { c.enter(entry) },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = iconOfKind(kind),
                                contentDescription = labelOfKind(kind),
                                modifier = Modifier.size(24.dp),
                                tint = colorOfKind(kind),
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(entry.name, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    buildString {
                                        if (entry.isDir) append("目录") else append(BackupStore.humanSize(entry.size))
                                        if (entry.isLink) append(" · 链接")
                                        append(" · ").append(entry.perm)
                                    },
                                    fontSize = 11.sp,
                                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                                    maxLines = 1,
                                )
                            }
                            Chevron()
                        }
                    }
                }
            }
        }
    }

    PathDialogHost(c)
}

/** 搜索框与路径输入/数据库密码等对话框，两个界面共用 */
@Composable
private fun PathDialogHost(c: AppController) {
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
    if (c.showPathInput) {
        PathInputDialog(
            initial = c.path,
            onOpen = { target ->
                c.showPathInput = false
                c.openBrowser(target)
            },
            onDismiss = { c.showPathInput = false },
        )
    }
}

/** 当前目录内的搜索框：卡片式外形 + 紧凑输入行，带一键清空 */
@Composable
private fun DirSearchBar(c: AppController) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        onClick = null,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = MiuixIcons.Basic.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MiuixTheme.colorScheme.onBackgroundVariant,
            )
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (c.searchQuery.isEmpty()) {
                    Text(
                        "搜索当前目录",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
                BasicTextField(
                    value = c.searchQuery,
                    onValueChange = { c.searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MiuixTheme.textStyles.main.copy(
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onBackground,
                    ),
                    cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                )
            }
            if (c.searchQuery.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                IconButton(onClick = { c.searchQuery = "" }, minWidth = 24.dp, minHeight = 24.dp) {
                    Icon(
                        imageVector = MiuixIcons.Basic.SearchCleanup,
                        contentDescription = "清空搜索",
                        modifier = Modifier.size(18.dp),
                        tint = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RootCard(c: AppController) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        insideMargin = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        onClick = null,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (c.rooted) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.error),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (c.rooted) "Root 已就绪" else "Root 未授权", fontSize = 16.sp)
                Text(
                    c.rootText,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    maxLines = 2,
                )
            }
            if (!c.rooted) {
                Spacer(Modifier.width(8.dp))
                Button(onClick = { c.retryRoot() }) { Text("检查") }
            }
        }
    }
}

@Composable
private fun QuickRow(
    c: AppController,
    path: String,
    desc: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    RowItem(
        title = path,
        subtitle = desc,
        icon = icon,
        iconTint = MiuixTheme.colorScheme.primary,
        onClick = { c.openBrowser(path) },
    )
}
