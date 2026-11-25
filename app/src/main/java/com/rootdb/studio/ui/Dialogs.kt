package com.rootdb.studio.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.rootdb.studio.EditKind
import com.rootdb.studio.data.Algo
import com.rootdb.studio.data.Cell
import com.rootdb.studio.data.ColumnInfo
import com.rootdb.studio.data.CryptoEngine
import com.rootdb.studio.data.CryptoSpec
import com.rootdb.studio.data.KeyFormat
import com.rootdb.studio.data.ValueType
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 应用内对话框：独立全屏窗口 + 半透明遮罩 + 居中卡片。
 * 不使用 Miuix SuperDialog 的 popup host，避免个别机型上弹窗不可见却拦截触摸。
 */
@Composable
fun RdDialog(
    title: String? = null,
    summary: String? = null,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrim = remember { MutableInteractionSource() }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f))
                .clickable(interactionSource = scrim, indication = null) { onDismiss() }
                .imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            val inner = remember { MutableInteractionSource() }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clickable(interactionSource = inner, indication = null) { },
                cornerRadius = 18.dp,
                insideMargin = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
            ) {
                if (!title.isNullOrBlank()) {
                    Text(title, fontSize = 17.sp)
                    Spacer(Modifier.height(4.dp))
                }
                if (!summary.isNullOrBlank()) {
                    Text(summary, fontSize = 12.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
                    Spacer(Modifier.height(10.dp))
                }
                content()
            }
        }
    }
}

@Composable
private fun DialogButtons(
    confirmText: String = "确定",
    dismissText: String = "取消",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(dismissText) }
        Button(onClick = onConfirm, modifier = Modifier.weight(1f)) { Text(confirmText) }
    }
}

@Composable
fun PasswordDialog(
    path: String,
    onOpen: (String?) -> Unit,
    onDismiss: () -> Unit,
    error: String? = null,
    onEdit: () -> Unit = {},
) {
    var password by remember { mutableStateOf("") }
    RdDialog(
        title = "打开数据库",
        summary = path,
        onDismiss = onDismiss,
    ) {
        Text(
            "普通 SQLite 直接打开；SQLCipher 加密库请输入密钥（如微信 EnMicroMsg.db 的 32 位十六进制密钥）。",
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
        Spacer(Modifier.height(10.dp))
        TextField(
            value = password,
            onValueChange = {
                password = it
                onEdit()
            },
            label = "SQLCipher 密钥（留空则按明文打开）",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!error.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                error,
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.error,
            )
        }
        DialogButtons(
            confirmText = "打开",
            onConfirm = { onOpen(password.ifBlank { null }) },
            onDismiss = onDismiss,
        )
    }
}

@Composable
fun PathInputDialog(
    initial: String,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var path by remember { mutableStateOf(initial) }
    RdDialog(
        title = "浏览指定路径",
        summary = "例如 /data/data/com.tencent.mm/databases 或 /sdcard/Download",
        onDismiss = onDismiss,
    ) {
        TextField(
            value = path,
            onValueChange = { path = it },
            label = "绝对路径",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        DialogButtons(
            confirmText = "浏览",
            onConfirm = { onOpen(path.trim()) },
            onDismiss = onDismiss,
        )
    }
}

@Composable
fun ConfirmDialog(    title: String,
    summary: String,
    confirmText: String = "确认",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    RdDialog(title = title, summary = summary, onDismiss = onDismiss) {
        DialogButtons(confirmText = confirmText, onConfirm = onConfirm, onDismiss = onDismiss)
    }
}

@Composable
fun CellEditorDialog(
    columnName: String,
    columnType: String,
    cell: Cell,
    crypto: CryptoSpec,
    onSave: (String, EditKind, Boolean) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var input by remember {
        mutableStateOf(
            when (cell.type) {
                ValueType.BLOB -> cell.blob?.let { "BLOB " + com.rootdb.studio.data.Hex.encode(it) } ?: ""
                ValueType.NULL -> ""
                else -> cell.text ?: ""
            }
        )
    }
    var kind by remember {
        mutableStateOf(
            when (cell.type) {
                ValueType.INTEGER -> EditKind.INTEGER
                ValueType.REAL -> EditKind.REAL
                ValueType.BLOB -> EditKind.BLOB
                else -> EditKind.AUTO
            }
        )
    }
    var encrypt by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<String?>(null) }

    RdDialog(
        title = "编辑 $columnName",
        summary = "$columnType · 原值：${cell.display.take(80)}",
        onDismiss = onDismiss,
    ) {
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            TextField(
                value = input,
                onValueChange = { input = it },
                label = "值",
                singleLine = false,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row {
                Chip("自动", kind == EditKind.AUTO) { kind = EditKind.AUTO }
                Spacer(Modifier.width(6.dp))
                Chip("文本", kind == EditKind.TEXT) { kind = EditKind.TEXT }
                Spacer(Modifier.width(6.dp))
                Chip("整数", kind == EditKind.INTEGER) { kind = EditKind.INTEGER }
            }
            Spacer(Modifier.height(6.dp))
            Row {
                Chip("小数", kind == EditKind.REAL) { kind = EditKind.REAL }
                Spacer(Modifier.width(6.dp))
                Chip("BLOB", kind == EditKind.BLOB) { kind = EditKind.BLOB }
                Spacer(Modifier.width(6.dp))
                Chip("NULL", kind == EditKind.NULL) { kind = EditKind.NULL }
            }
            Spacer(Modifier.height(10.dp))
            if (crypto.enabled) {
                Text("加密：${crypto.summary}", fontSize = 12.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
                Spacer(Modifier.height(6.dp))
                Row {
                    Chip("按密文原样保存", !encrypt) { encrypt = false }
                    Spacer(Modifier.width(6.dp))
                    Chip("保存时加密", encrypt) { encrypt = true }
                    Spacer(Modifier.width(6.dp))
                    Chip("解密预览", false) {
                        preview = CryptoEngine.decryptTextOrNull(crypto, cell.display)
                            ?: "未能解出可读明文（该值可能未按当前算法加密）"
                    }
                }
                if (preview != null) {
                    Text(preview!!, fontSize = 12.sp, color = MiuixTheme.colorScheme.primary)
                }
            } else {
                Text(
                    "未配置解密算法。可在表页“加密设置”里选择 AES / 3DES / XOR / Base64 等，支持直接编辑加密字段。",
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (onDelete != null) {
                Button(onClick = onDelete, modifier = Modifier.weight(1f)) {
                    Text("删除该行", color = MiuixTheme.colorScheme.error)
                }
            }
            Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
            Button(onClick = { onSave(input, kind, encrypt) }, modifier = Modifier.weight(1f)) { Text("保存") }
        }
    }
}

@Composable
fun CryptoDialog(
    initial: CryptoSpec,
    onSave: (CryptoSpec) -> Unit,
    onDismiss: () -> Unit,
) {
    var algo by remember { mutableStateOf(initial.algo) }
    var key by remember { mutableStateOf(initial.key) }
    var keyFormat by remember { mutableStateOf(initial.keyFormat) }
    var iv by remember { mutableStateOf(initial.iv) }
    var ivFormat by remember { mutableStateOf(initial.ivFormat) }
    var payload by remember { mutableStateOf(if (initial.payload == KeyFormat.TEXT) KeyFormat.BASE64 else initial.payload) }
    var test by remember { mutableStateOf<String?>(null) }

    RdDialog(
        title = "字段解密 / 加密设置",
        summary = "用于直接查看与编辑加密存储的记录",
        onDismiss = onDismiss,
    ) {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            Text("算法", fontSize = 12.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Algo.entries.forEach { a ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Chip(a.label, algo == a) { algo = a }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            TextField(
                value = key,
                onValueChange = { key = it },
                label = "密钥（AES 支持 16/24/32 字节，其它长度自动 SHA-256 派生）",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyFormat.entries.forEach { f -> Chip(f.label, keyFormat == f) { keyFormat = f } }
            }
            Spacer(Modifier.height(10.dp))
            TextField(
                value = iv,
                onValueChange = { iv = it },
                label = "IV / Nonce（CBC、GCM 需要；留空自动派生）",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyFormat.entries.forEach { f -> Chip(f.label, ivFormat == f) { ivFormat = f } }
            }
            Spacer(Modifier.height(10.dp))
            Text("密文编码", fontSize = 12.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("Base64", payload == KeyFormat.BASE64) { payload = KeyFormat.BASE64 }
                Chip("十六进制", payload == KeyFormat.HEX) { payload = KeyFormat.HEX }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val spec = CryptoSpec(algo, key, keyFormat, iv, ivFormat, payload)
                    test = runCatching {
                        val enc = CryptoEngine.encrypt(spec, "database studio")
                        "自检：database studio → $enc → ${CryptoEngine.decrypt(spec, enc)}"
                    }.getOrElse { "自检失败：${it.message}" }
                }) { Text("自检") }
                Button(onClick = {
                    onSave(CryptoSpec(algo, key, keyFormat, iv, ivFormat, payload))
                }) { Text("保存设置") }
            }
            if (test != null) {
                Spacer(Modifier.height(6.dp))
                Text(test!!, fontSize = 11.sp, color = MiuixTheme.colorScheme.onBackgroundVariant)
            }
        }
    }
}

@Composable
fun SqlDialog(onRun: (String, (String) -> Unit) -> Unit, onDismiss: () -> Unit) {
    var sql by remember { mutableStateOf("SELECT name, type FROM sqlite_master WHERE type='table';") }
    var result by remember { mutableStateOf("") }
    RdDialog(title = "执行 SQL", summary = "支持查询与读写语句", onDismiss = onDismiss) {
        Column {
            TextField(
                value = sql,
                onValueChange = { sql = it },
                label = "SQL",
                singleLine = false,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { onRun(sql) { result = it } },
                    modifier = Modifier.weight(1f),
                ) { Text("执行") }
                Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("关闭") }
            }
            if (result.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth(), onClick = null) {
                    Text(result.take(4000), fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun InsertRowDialog(
    columns: List<ColumnInfo>,
    onInsert: (Map<String, Any?>) -> Unit,
    onDismiss: () -> Unit,
) {
    val values = remember { mutableMapOf<String, String>() }
    var error by remember { mutableStateOf<String?>(null) }
    RdDialog(title = "新增记录", summary = "留空的列将写入 NULL", onDismiss = onDismiss) {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            columns.forEach { col ->
                TextField(
                    value = values[col.name] ?: "",
                    onValueChange = { values[col.name] = it },
                    label = col.name + if (col.pk) " (主键)" else "",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
            }
        }
        if (error != null) {
            Text(error!!, fontSize = 12.sp, color = MiuixTheme.colorScheme.error)
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
            Button(
                onClick = {
                    val map = LinkedHashMap<String, Any?>()
                    columns.forEach { col ->
                        val raw = values[col.name]
                        if (!raw.isNullOrBlank()) {
                            val type = col.type.uppercase()
                            map[col.name] = when {
                                type.contains("INT") -> raw.toLongOrNull() ?: raw
                                type.contains("REAL") || type.contains("FLOA") || type.contains("DOUB") ->
                                    raw.toDoubleOrNull() ?: raw
                                type.contains("BLOB") -> runCatching { com.rootdb.studio.data.Hex.decode(raw) }.getOrNull() ?: raw
                                else -> raw
                            }
                        }
                    }
                    if (map.isEmpty()) {
                        error = "至少填写一个字段"
                    } else {
                        onInsert(map)
                    }
                },
                modifier = Modifier.weight(1f),
            ) { Text("插入") }
        }
    }
}
