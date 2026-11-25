package com.rootdb.studio.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rootdb.studio.AppController
import com.rootdb.studio.R
import com.rootdb.studio.data.Algo
import com.rootdb.studio.data.CryptoEngine
import com.rootdb.studio.data.CryptoSpec
import com.rootdb.studio.data.DbKeyStore
import com.rootdb.studio.data.KeyFormat
import com.rootdb.studio.data.SavedCrypto
import com.rootdb.studio.data.SavedDbKey
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 「加密与密钥」独立页面。
 *
 * 设计：顶部两个分段 —— 字段解密（当前表的算法/密钥，含已保存表配置管理）和
 * 数据库密钥（整库 SQLCipher 密码的保存、删除、清空）。所有配置都在这里管理，
 * 不再用弹窗，避免长列表 + 键盘把对话框挤满。
 */
@Composable
fun CryptoScreen(c: AppController, tab: Int) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.crypto_screen_title),
                navigationIcon = { BackIcon { c.closeCrypto() } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Chip(stringResource(R.string.crypto_tab_fields), tab == 0) { c.openCrypto(0) }
                Chip(stringResource(R.string.crypto_tab_db), tab == 1) { c.openCrypto(1) }
            }
            if (tab == 1) DbKeysTab(c) else FieldCryptoTab(c)
        }
    }
}

// ── 字段解密 ──────────────────────────────────────────────────────────
@Composable
private fun FieldCryptoTab(c: AppController) {
    val table = c.currentTable
    if (table == null) {
        EmptyHint(stringResource(R.string.crypto_need_table))
        return
    }
    val initial = c.crypto
    var algo by remember(table) { mutableStateOf(initial.algo) }
    var key by remember(table) { mutableStateOf(initial.key) }
    var keyFormat by remember(table) { mutableStateOf(initial.keyFormat) }
    var iv by remember(table) { mutableStateOf(initial.iv) }
    var ivFormat by remember(table) { mutableStateOf(initial.ivFormat) }
    var payload by remember(table) {
        mutableStateOf(if (initial.payload == KeyFormat.TEXT) KeyFormat.BASE64 else initial.payload)
    }
    var test by remember(table) { mutableStateOf<String?>(null) }
    var confirmClear by remember(table) { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp).padding(bottom = 28.dp),
    ) {
        Text(
            stringResource(R.string.crypto_current_table, table),
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
        Spacer(Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            onClick = null,
        ) {
            Text(
                stringResource(R.string.crypto_algorithm),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
            Spacer(Modifier.height(6.dp))
            // 算法标签按屏幕宽度自动换行，不再一行一个
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Algo.entries.forEach { a ->
                    Chip(stringResource(a.labelRes), algo == a) { algo = a }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.crypto_key_label),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
            Spacer(Modifier.height(6.dp))
            TextField(
                value = key,
                onValueChange = { key = it },
                label = stringResource(R.string.crypto_key_hint),
                useLabelAsPlaceholder = true,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyFormat.entries.forEach { f ->
                    Chip(stringResource(f.labelRes), keyFormat == f) { keyFormat = f }
                }
            }

            if (algo.needsIv) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.crypto_iv_label),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
                Spacer(Modifier.height(6.dp))
                TextField(
                    value = iv,
                    onValueChange = { iv = it },
                    label = stringResource(R.string.crypto_iv_hint),
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KeyFormat.entries.forEach { f ->
                        Chip(stringResource(f.labelRes), ivFormat == f) { ivFormat = f }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.crypto_payload),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(stringResource(R.string.fmt_base64), payload == KeyFormat.BASE64) { payload = KeyFormat.BASE64 }
                Chip(stringResource(R.string.fmt_hex), payload == KeyFormat.HEX) { payload = KeyFormat.HEX }
            }

            Spacer(Modifier.height(14.dp))
            val selftestOk = stringResource(R.string.crypto_selftest_ok)
            val selftestFailed = stringResource(R.string.crypto_selftest_failed)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val spec = CryptoSpec(algo, key, keyFormat, iv, ivFormat, payload)
                    test = runCatching {
                        val enc = CryptoEngine.encrypt(spec, "database studio")
                        selftestOk.format(enc, CryptoEngine.decrypt(spec, enc))
                    }.getOrElse { selftestFailed.format(it.message.orEmpty()) }
                }) { Text(stringResource(R.string.crypto_selftest)) }
                Button(onClick = {
                    c.saveCrypto(CryptoSpec(algo, key, keyFormat, iv, ivFormat, payload))
                }) { Text(stringResource(R.string.crypto_save_settings)) }
            }
            if (test != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    test!!,
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        SavedCryptoSection(
            saved = c.savedCryptos,
            currentDb = c.session?.sourcePath,
            currentTable = table,
            confirming = confirmClear,
            onConfirmChange = { confirmClear = it },
            onEdit = { item ->
                algo = item.spec.algo
                key = item.spec.key
                keyFormat = item.spec.keyFormat
                iv = item.spec.iv
                ivFormat = item.spec.ivFormat
                payload = if (item.spec.payload == KeyFormat.TEXT) KeyFormat.BASE64 else item.spec.payload
                test = null
                val sameTarget = item.dbPath == c.session?.sourcePath && item.table == table
                c.toast(
                    if (sameTarget) c.ctx.getString(R.string.crypto_saved_loaded)
                    else c.ctx.getString(R.string.crypto_saved_loaded_other, item.table, table)
                )
            },
            onDelete = { c.deleteSavedCrypto(it) },
            onClearAll = { c.clearSavedCryptos() },
        )
    }
}

@Composable
private fun SavedCryptoSection(
    saved: List<SavedCrypto>,
    currentDb: String?,
    currentTable: String?,
    confirming: Boolean,
    onConfirmChange: (Boolean) -> Unit,
    onEdit: (SavedCrypto) -> Unit,
    onDelete: (SavedCrypto) -> Unit,
    onClearAll: () -> Unit,
) {
    if (saved.isEmpty()) return
    Spacer(Modifier.height(16.dp))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.crypto_saved_title, saved.size),
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
        if (!confirming) {
            ActionText(stringResource(R.string.crypto_saved_clear_all), MiuixTheme.colorScheme.error) {
                onConfirmChange(true)
            }
        }
    }
    if (confirming) {
        ConfirmClearRow(
            question = stringResource(R.string.crypto_saved_clear_confirm),
            onYes = {
                onConfirmChange(false)
                onClearAll()
            },
            onNo = { onConfirmChange(false) },
        )
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        onClick = null,
    ) {
        saved.forEach { item ->
            val isCurrent = item.dbPath == currentDb && item.table == currentTable
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                    Text(
                        stringResource(
                            if (isCurrent) R.string.crypto_saved_table_current else R.string.crypto_saved_table,
                            item.table,
                        ),
                        fontSize = 13.sp,
                    )
                    Text(
                        stringResource(
                            R.string.crypto_saved_meta,
                            item.dbName,
                            stringResource(item.spec.algo.labelRes),
                        ),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
                ActionText(stringResource(R.string.crypto_saved_edit), MiuixTheme.colorScheme.primary) {
                    onEdit(item)
                }
                ActionText(stringResource(R.string.crypto_saved_delete), MiuixTheme.colorScheme.error) {
                    onDelete(item)
                }
            }
        }
    }
}

// ── 数据库密钥 ────────────────────────────────────────────────────────
@Composable
private fun DbKeysTab(c: AppController) {
    var confirmClear by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SavedDbKey?>(null) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp).padding(bottom = 28.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            onClick = null,
        ) {
            Text(stringResource(R.string.crypto_db_remember_title), fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.crypto_db_remember_desc),
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }

        val keys = c.savedDbKeys
        if (keys.isEmpty()) {
            Spacer(Modifier.height(18.dp))
            EmptyHint(stringResource(R.string.crypto_db_empty))
            return
        }

        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.crypto_db_saved_title, keys.size),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
            if (!confirmClear) {
                ActionText(stringResource(R.string.crypto_saved_clear_all), MiuixTheme.colorScheme.error) {
                    confirmClear = true
                }
            }
        }
        if (confirmClear) {
            ConfirmClearRow(
                question = stringResource(R.string.crypto_db_clear_confirm),
                onYes = { confirmClear = false; c.clearSavedDbKeys() },
                onNo = { confirmClear = false },
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            onClick = null,
        ) {
            keys.forEach { item ->
                val isCurrent = item.dbPath == c.session?.sourcePath
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                        Text(
                            stringResource(
                                if (isCurrent) R.string.crypto_db_current_item else R.string.crypto_db_item,
                                item.dbName,
                            ),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            item.dbPath,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                        Text(
                            stringResource(R.string.crypto_db_key_len, item.keyLength),
                            fontSize = 10.sp,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                    }
                    ActionText(stringResource(R.string.crypto_saved_edit), MiuixTheme.colorScheme.primary) {
                        editing = item
                    }
                    ActionText(stringResource(R.string.crypto_saved_delete), MiuixTheme.colorScheme.error) {
                        c.deleteSavedDbKey(item)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.crypto_db_security_note),
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
    }

    editing?.let { item ->
        DbKeyEditDialog(
            item = item,
            isCurrent = item.dbPath == c.session?.sourcePath,
            initialKey = remember(item) { DbKeyStore.get(c.ctx, item.dbPath).orEmpty() },
            onSave = { pwd, onResult -> c.updateSavedDbKey(item, pwd, onResult) },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun DbKeyEditDialog(
    item: SavedDbKey,
    isCurrent: Boolean,
    initialKey: String,
    onSave: (String, (String?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var pwd by remember { mutableStateOf(initialKey) }
    var error by remember { mutableStateOf<String?>(null) }
    RdDialog(
        title = stringResource(R.string.crypto_db_edit_title),
        summary = item.dbPath,
        onDismiss = onDismiss,
    ) {
        Text(
            stringResource(
                if (isCurrent) R.string.crypto_db_edit_current_hint else R.string.crypto_db_edit_desc
            ),
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
        Spacer(Modifier.height(10.dp))
        TextField(
            value = pwd,
            onValueChange = { pwd = it; error = null },
            label = stringResource(R.string.crypto_db_edit_field, item.keyLength),
            useLabelAsPlaceholder = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error!!, fontSize = 12.sp, color = MiuixTheme.colorScheme.error)
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.action_cancel))
            }
            Button(
                onClick = {
                    onSave(pwd) { err ->
                        if (err == null) onDismiss() else error = err
                    }
                },
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.action_save)) }
        }
    }
}

@Composable
private fun ActionText(text: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 12.sp,
        color = color,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp),
        maxLines = 1,
    )
}

@Composable
private fun ConfirmClearRow(question: String, onYes: () -> Unit, onNo: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(
            question,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
            color = MiuixTheme.colorScheme.onBackground,
        )
        ActionText(stringResource(R.string.crypto_saved_clear_yes), MiuixTheme.colorScheme.error, onYes)
        ActionText(stringResource(R.string.crypto_saved_clear_no), MiuixTheme.colorScheme.primary, onNo)
    }
}
