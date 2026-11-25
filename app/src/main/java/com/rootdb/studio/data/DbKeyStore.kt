package com.rootdb.studio.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 一条已保存的整库密钥（只记录长度，不保存明文） */
data class SavedDbKey(
    val dbPath: String,
    val keyLength: Int,
    val savedAt: Long = 0L,
) {
    val dbName: String get() = dbPath.substringAfterLast('/').ifBlank { dbPath }
}

/**
 * 整库（SQLCipher）密钥记忆。
 *
 * 密码用 Android Keystore 里的 AES-256-GCM 密钥加密后再落盘，SharedPreferences 里只有密文；
 * Keystore 密钥不可导出，换机/清数据后旧密码自动失效（读取返回 null）。
 */
object DbKeyStore {
    private const val PREFS = "rootdb_dbkeys"
    private const val ALIAS = "rootdb.dbkey.v1"
    private const val ROOT = "dbkey|"
    private const val F_BLOB = "blob"
    private const val F_LEN = "len"
    private const val F_TS = "ts"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(password: String): String? = runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val body = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(cipher.iv + body, Base64.NO_WRAP)
    }.getOrNull()

    private fun decrypt(blob: String): String? = runCatching {
        val raw = Base64.decode(blob, Base64.NO_WRAP)
        if (raw.size <= 12) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, raw.copyOf(12)))
        String(cipher.doFinal(raw.copyOfRange(12, raw.size)), Charsets.UTF_8)
    }.getOrNull()

    fun save(ctx: Context, dbPath: String?, password: String?) {
        if (dbPath.isNullOrBlank()) return
        if (password.isNullOrBlank()) {
            remove(ctx, dbPath)
            return
        }
        val blob = encrypt(password) ?: return
        prefs(ctx).edit()
            .putString("$ROOT$dbPath|$F_BLOB", blob)
            .putInt("$ROOT$dbPath|$F_LEN", password.length)
            .putLong("$ROOT$dbPath|$F_TS", System.currentTimeMillis())
            .apply()
    }

    fun get(ctx: Context, dbPath: String?): String? {
        if (dbPath.isNullOrBlank()) return null
        val blob = prefs(ctx).getString("$ROOT$dbPath|$F_BLOB", null) ?: return null
        return decrypt(blob)
    }

    fun has(ctx: Context, dbPath: String?): Boolean {
        if (dbPath.isNullOrBlank()) return false
        return prefs(ctx).contains("$ROOT$dbPath|$F_BLOB")
    }

    fun list(ctx: Context): List<SavedDbKey> {
        val sp = prefs(ctx)
        val out = ArrayList<SavedDbKey>()
        for (key in sp.all.keys) {
            if (!key.startsWith(ROOT) || !key.endsWith("|$F_BLOB")) continue
            val db = key.removePrefix(ROOT).removeSuffix("|$F_BLOB")
            if (db.isBlank()) continue
            out += SavedDbKey(
                dbPath = db,
                keyLength = sp.getInt("$ROOT$db|$F_LEN", 0),
                savedAt = sp.getLong("$ROOT$db|$F_TS", 0L),
            )
        }
        return out.sortedByDescending { it.savedAt }
    }

    fun remove(ctx: Context, dbPath: String?) {
        if (dbPath.isNullOrBlank()) return
        prefs(ctx).edit()
            .remove("$ROOT$dbPath|$F_BLOB")
            .remove("$ROOT$dbPath|$F_LEN")
            .remove("$ROOT$dbPath|$F_TS")
            .apply()
    }

    fun clearAll(ctx: Context) {
        val sp = prefs(ctx)
        val edit = sp.edit()
        for (key in sp.all.keys) if (key.startsWith(ROOT)) edit.remove(key)
        edit.apply()
    }
}
