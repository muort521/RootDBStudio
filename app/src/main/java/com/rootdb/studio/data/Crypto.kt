package com.rootdb.studio.data

import android.content.Context
import android.util.Base64
import androidx.annotation.StringRes
import com.rootdb.studio.R
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object Hex {
    fun encode(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    private val HEX = "0123456789abcdef".toCharArray()

    fun decode(text: String): ByteArray {
        val clean = text.replace(" ", "").replace("\n", "").replace("\r", "").removePrefix("0x")
        require(clean.length % 2 == 0) { "Hex input must have an even length" }
        val out = ByteArray(clean.length / 2)
        for (i in out.indices) {
            val hi = Character.digit(clean[i * 2], 16)
            val lo = Character.digit(clean[i * 2 + 1], 16)
            require(hi >= 0 && lo >= 0) { "Hex input contains non-hex characters" }
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    fun unb64(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP)
}

enum class Algo(@StringRes val labelRes: Int, val transformation: String, val needsIv: Boolean) {
    NONE(R.string.algo_none, "", false),
    AES_ECB(R.string.algo_aes_ecb, "AES/ECB/PKCS5Padding", false),
    AES_CBC(R.string.algo_aes_cbc, "AES/CBC/PKCS5Padding", true),
    AES_GCM(R.string.algo_aes_gcm, "AES/GCM/NoPadding", true),
    DES_ECB(R.string.algo_des_ecb, "DES/ECB/PKCS5Padding", false),
    DES3_ECB(R.string.algo_des3_ecb, "DESede/ECB/PKCS5Padding", false),
    XOR(R.string.algo_xor, "XOR", false),
    BASE64(R.string.algo_base64, "BASE64", false),
}

enum class KeyFormat(@StringRes val labelRes: Int) {
    TEXT(R.string.fmt_text),
    HEX(R.string.fmt_hex),
    BASE64(R.string.fmt_base64),
}

data class CryptoSpec(
    val algo: Algo = Algo.NONE,
    val key: String = "",
    val keyFormat: KeyFormat = KeyFormat.TEXT,
    val iv: String = "",
    val ivFormat: KeyFormat = KeyFormat.TEXT,
    val payload: KeyFormat = KeyFormat.BASE64,
) {
    val enabled: Boolean get() = algo != Algo.NONE

    /** 当前语言下的算法描述（供提示条展示） */
    @get:StringRes
    val summaryRes: Int get() = if (enabled) algo.labelRes else R.string.crypto_plain
}

object CryptoEngine {

    fun decrypt(spec: CryptoSpec, raw: String): String {
        if (!spec.enabled) return raw
        return when (spec.algo) {
            Algo.NONE -> raw
            Algo.BASE64 -> String(Hex.unb64(raw), Charsets.UTF_8)
            Algo.XOR -> String(xor(parseBytes(raw, spec.payload), keyBytes(spec)), Charsets.UTF_8)
            else -> String(doCipher(Cipher.DECRYPT_MODE, spec, decodePayload(raw, spec)), Charsets.UTF_8)
        }
    }

    /** 解密且结果看起来是可读明文时返回，否则返回 null（用于“解密显示”，避免误把普通字段解成乱码） */
    fun decryptTextOrNull(spec: CryptoSpec, raw: String): String? {
        if (!spec.enabled || raw.isBlank()) return null
        val out = runCatching { decrypt(spec, raw) }.getOrNull() ?: return null
        if (out.isBlank()) return null
        val bad = out.any { ch ->
            ch.code == 0xFFFD || ch.code == 0x7F ||
                (ch.code < 0x20 && ch != '\n' && ch != '\r' && ch != '\t')
        }
        return if (bad) null else out
    }

    fun encrypt(spec: CryptoSpec, plain: String): String {
        if (!spec.enabled) return plain
        return when (spec.algo) {
            Algo.NONE -> plain
            Algo.BASE64 -> Hex.b64(plain.toByteArray(Charsets.UTF_8))
            Algo.XOR -> Hex.encode(xor(plain.toByteArray(Charsets.UTF_8), keyBytes(spec)))
            else -> String(encodePayload(doCipher(Cipher.ENCRYPT_MODE, spec, plain.toByteArray(Charsets.UTF_8)), spec), Charsets.UTF_8)
        }
    }

    private fun parseBytes(text: String, format: KeyFormat): ByteArray = when (format) {
        KeyFormat.HEX -> Hex.decode(text)
        KeyFormat.BASE64 -> Hex.unb64(text)
        KeyFormat.TEXT -> text.toByteArray(Charsets.UTF_8)
    }

    private fun encodePayload(bytes: ByteArray, spec: CryptoSpec): ByteArray = when (spec.payload) {
        KeyFormat.HEX -> Hex.encode(bytes).toByteArray(Charsets.UTF_8)
        KeyFormat.BASE64 -> Hex.b64(bytes).toByteArray(Charsets.UTF_8)
        KeyFormat.TEXT -> bytes
    }
    // 注：payload 为 TEXT 时按原始字节处理（仅适用于可打印内容）

    private fun decodePayload(raw: String, spec: CryptoSpec): ByteArray = when (spec.payload) {
        KeyFormat.HEX -> Hex.decode(raw)
        KeyFormat.BASE64 -> Hex.unb64(raw)
        KeyFormat.TEXT -> raw.toByteArray(Charsets.UTF_8)
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun keyBytes(spec: CryptoSpec): ByteArray {
        var key = parseBytes(spec.key, spec.keyFormat)
        return when (spec.algo) {
            Algo.DES_ECB -> if (key.size == 8) key else sha256(key).copyOf(8)
            Algo.DES3_ECB -> if (key.size == 24) key else sha256(key).copyOf(24)
            Algo.AES_ECB, Algo.AES_CBC, Algo.AES_GCM ->
                if (key.size == 16 || key.size == 24 || key.size == 32) key else sha256(key)
            else -> key
        }
    }

    private fun ivBytes(spec: CryptoSpec, len: Int): ByteArray {
        val raw = if (spec.iv.isBlank()) sha256(spec.key.toByteArray()) else parseBytes(spec.iv, spec.ivFormat)
        return when {
            raw.size == len -> raw
            raw.size > len -> raw.copyOf(len)
            else -> sha256(raw).copyOf(len)
        }
    }

    private fun doCipher(mode: Int, spec: CryptoSpec, input: ByteArray): ByteArray {
        val transformation = spec.algo.transformation
        val cipher = Cipher.getInstance(transformation)
        val keyAlgorithm = when (spec.algo) {
            Algo.DES_ECB -> "DES"
            Algo.DES3_ECB -> "DESede"
            else -> "AES"
        }
        val keySpec = SecretKeySpec(keyBytes(spec), keyAlgorithm)
        when {
            spec.algo == Algo.AES_GCM -> {
                val iv = ivBytes(spec, 12)
                cipher.init(mode, keySpec, GCMParameterSpec(128, iv))
            }

            spec.algo.needsIv -> {
                val ivLen = if (spec.algo == Algo.DES_ECB) 8 else 16
                cipher.init(mode, keySpec, IvParameterSpec(ivBytes(spec, ivLen)))
            }

            else -> cipher.init(mode, keySpec)
        }
        return cipher.doFinal(input)
    }

    private fun xor(data: ByteArray, key: ByteArray): ByteArray {
        require(key.isNotEmpty()) { "XOR requires a key" }
        val out = ByteArray(data.size)
        for (i in data.indices) out[i] = (data[i].toInt() xor key[i % key.size].toInt()).toByte()
        return out
    }
}

/** 加解密配置持久化 */
object CryptoPrefs {
    private const val NAME = "rootdb_prefs"

    fun load(ctx: Context): CryptoSpec {
        val sp = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return CryptoSpec(
            algo = runCatching { Algo.valueOf(sp.getString("c_algo", Algo.NONE.name)!!) }.getOrDefault(Algo.NONE),
            key = sp.getString("c_key", "") ?: "",
            keyFormat = runCatching { KeyFormat.valueOf(sp.getString("c_keyfmt", KeyFormat.TEXT.name)!!) }
                .getOrDefault(KeyFormat.TEXT),
            iv = sp.getString("c_iv", "") ?: "",
            ivFormat = runCatching { KeyFormat.valueOf(sp.getString("c_ivfmt", KeyFormat.TEXT.name)!!) }
                .getOrDefault(KeyFormat.TEXT),
            payload = runCatching { KeyFormat.valueOf(sp.getString("c_payload", KeyFormat.BASE64.name)!!) }
                .getOrDefault(KeyFormat.BASE64),
        )
    }

    fun save(ctx: Context, spec: CryptoSpec) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("c_algo", spec.algo.name)
            .putString("c_key", spec.key)
            .putString("c_keyfmt", spec.keyFormat.name)
            .putString("c_iv", spec.iv)
            .putString("c_ivfmt", spec.ivFormat.name)
            .putString("c_payload", spec.payload.name)
            .apply()
    }
}
