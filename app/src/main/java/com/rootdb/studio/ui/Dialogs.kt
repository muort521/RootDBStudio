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
import com.rootdb.studio.data.Cell
import com.rootdb.studio.data.ColumnInfo
import com.rootdb.studio.data.CryptoEngine
import com.rootdb.studio.data.CryptoSpec
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rootdb.studio.R
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
    confirmText: String? = null,
    dismissText: String? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(onClick = onDismiss, modifier = Modifier.weight(1f)) {
            Text(dismissText ?: stringResource(R.string.action_cancel))
        }
        Button(onClick = onConfirm, modifier = Modifier.weight(1f)) {
            Text(confirmText ?: stringResource(R.string.action_ok))
        }
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
        title = stringResource(R.string.dialog_open_db_title),
        summary = path,
        onDismiss = onDismiss,
    ) {
        Text(
            stringResource(R.string.dialog_open_db_desc),
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
            label = stringResource(R.string.dialog_sqlcipher_key),
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
            confirmText = stringResource(R.string.action_open),
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
        title = stringResource(R.string.dialog_browse_path_title),
        summary = stringResource(R.string.dialog_browse_path_summary),
        onDismiss = onDismiss,
    ) {
        TextField(
            value = path,
            onValueChange = { path = it },
            label = stringResource(R.string.dialog_absolute_path),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        DialogButtons(
            confirmText = stringResource(R.string.action_browse),
            onConfirm = { onOpen(path.trim()) },
            onDismiss = onDismiss,
        )
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    summary: String,
    confirmText: String? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    RdDialog(title = title, summary = summary, onDismiss = onDismiss) {
        DialogButtons(confirmText = confirmText ?: stringResource(R.string.action_confirm), onConfirm = onConfirm, onDismiss = onDismiss)
    }
}

@Composable
fun CellEditorDialog(
    columnName: String,
    columnType: String,
    cell: Cell,
    crypto: CryptoSpec,
    decryptOnOpen: Boolean = false,
    onSave: (String, EditKind, Boolean) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val cipherText = cell.text ?: ""
    // 表格上已经按明文显示时，编辑框也直接给明文，保存时再加密写回，避免「看到明文、改的却是密文」
    val openedPlain = remember(cell) {
        if (decryptOnOpen && crypto.enabled && cipherText.isNotBlank()) {
            CryptoEngine.decryptTextOrNull(crypto, cipherText)
        } else null
    }
    var input by remember(cell) {
        mutableStateOf(
            if (openedPlain != null) openedPlain else when (cell.type) {
                ValueType.BLOB -> cell.blob?.let { "BLOB " + com.rootdb.studio.data.Hex.encode(it) } ?: ""
                ValueType.NULL -> ""
                else -> cell.text ?: ""
            }
        )
    }
    var kind by remember(cell) {
        mutableStateOf(
            when (cell.type) {
                ValueType.INTEGER -> EditKind.INTEGER
                ValueType.REAL -> EditKind.REAL
                ValueType.BLOB -> EditKind.BLOB
                else -> EditKind.AUTO
            }
        )
    }
    var encrypt by remember(cell) { mutableStateOf(openedPlain != null) }
    var preview by remember(cell) { mutableStateOf<String?>(null) }

    RdDialog(
        title = stringResource(R.string.dialog_edit_column_title, columnName),
        summary = stringResource(R.string.dialog_edit_column_summary, columnType, (openedPlain ?: cell.display).take(80)),
        onDismiss = onDismiss,
    ) {
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            TextField(
                value = input,
                onValueChange = { input = it },
                label = stringResource(R.string.dialog_value),
                singleLine = false,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row {
                Chip(stringResource(R.string.edit_kind_auto), kind == EditKind.AUTO) { kind = EditKind.AUTO }
                Spacer(Modifier.width(6.dp))
                Chip(stringResource(R.string.edit_kind_text), kind == EditKind.TEXT) { kind = EditKind.TEXT }
                Spacer(Modifier.width(6.dp))
                Chip(stringResource(R.string.edit_kind_integer), kind == EditKind.INTEGER) { kind = EditKind.INTEGER }
            }
            Spacer(Modifier.height(6.dp))
            Row {
                Chip(stringResource(R.string.edit_kind_real), kind == EditKind.REAL) { kind = EditKind.REAL }
                Spacer(Modifier.width(6.dp))
                Chip(stringResource(R.string.edit_kind_blob), kind == EditKind.BLOB) { kind = EditKind.BLOB }
                Spacer(Modifier.width(6.dp))
                Chip(stringResource(R.string.edit_kind_null), kind == EditKind.NULL) { kind = EditKind.NULL }
            }
            Spacer(Modifier.height(10.dp))
            if (crypto.enabled) {
                Text(
                    stringResource(R.string.dialog_crypto_summary, stringResource(crypto.summaryRes)),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
                Spacer(Modifier.height(6.dp))
                val previewFail = stringResource(R.string.dialog_crypto_preview_failed)
                val previewLoaded = stringResource(R.string.dialog_crypto_preview_loaded)
                Row {
                    Chip(stringResource(R.string.dialog_crypto_keep_cipher), !encrypt) {
                        // 切回「保持密文」时把输入框还原成原始密文，避免把明文直接写进库里
                        encrypt = false
                        if (openedPlain != null || preview == previewLoaded) input = cipherText
                        preview = null
                    }
                    Spacer(Modifier.width(6.dp))
                    Chip(stringResource(R.string.dialog_crypto_encrypt_on_save), encrypt) { encrypt = true }
                    Spacer(Modifier.width(6.dp))
                    Chip(stringResource(R.string.dialog_crypto_preview), false) {
                        // 预览不只是显示一下：把明文直接填进输入框，并把保存方式切到「加密后保存」
                        val plain = CryptoEngine.decryptTextOrNull(crypto, cipherText)
                        if (plain != null) {
                            input = plain
                            encrypt = true
                            preview = previewLoaded
                        } else {
                            preview = previewFail
                        }
                    }
                }
                if (preview != null) {
                    Text(preview!!, fontSize = 12.sp, color = MiuixTheme.colorScheme.primary)
                }
            } else {
                Text(
                    stringResource(R.string.dialog_crypto_not_configured),
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (onDelete != null) {
                Button(onClick = onDelete, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.dialog_delete_row), color = MiuixTheme.colorScheme.error)
                }
            }
            Button(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.action_cancel))
            }
            Button(onClick = { onSave(input, kind, encrypt) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

@Composable
fun SqlDialog(onRun: (String, (String) -> Unit) -> Unit, onDismiss: () -> Unit) {
    var sql by remember { mutableStateOf("SELECT name, type FROM sqlite_master WHERE type='table';") }
    var result by remember { mutableStateOf("") }
    RdDialog(
        title = stringResource(R.string.sql_dialog_title),
        summary = stringResource(R.string.sql_dialog_summary),
        onDismiss = onDismiss,
    ) {
        Column {
            TextField(
                value = sql,
                onValueChange = { sql = it },
                label = stringResource(R.string.sql_dialog_label),
                singleLine = false,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { onRun(sql) { result = it } },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.action_run)) }
                Button(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_close))
                }
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
    RdDialog(
        title = stringResource(R.string.insert_dialog_title),
        summary = stringResource(R.string.insert_dialog_summary),
        onDismiss = onDismiss,
    ) {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            columns.forEach { col ->
                TextField(
                    value = values[col.name] ?: "",
                    onValueChange = { values[col.name] = it },
                    label = col.name + if (col.pk) stringResource(R.string.insert_pk_suffix) else "",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
            }
        }
        if (error != null) {
            Text(error!!, fontSize = 12.sp, color = MiuixTheme.colorScheme.error)
        }
        val needOneField = stringResource(R.string.insert_need_one_field)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.action_cancel))
            }
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
                        error = needOneField
                    } else {
                        onInsert(map)
                    }
                },
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.action_insert)) }
        }
    }
}
