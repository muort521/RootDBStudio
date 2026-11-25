package com.rootdb.studio.data

import android.content.Context
import android.util.Base64
import androidx.annotation.StringRes
import com.rootdb.studio.R
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.RSAPrivateCrtKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.security.spec.X509EncodedKeySpec
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

/**
 * 字段级加解密算法。
 *
 * - [transformation] 直接传给 [Cipher.getInstance]
 * - [keyAlgorithm] 用于 [SecretKeySpec]；RSA 忽略
 * - [ivLength] 需要的 IV/Nonce 长度，0 表示不需要
 * - [provider] 非空时从指定 Provider（BouncyCastle）取实现（SM4）
 * - [rsa] 非对称算法，key 字段解析为 PEM/Base64 DER 私钥或公钥
 */
enum class Algo(
    @param:StringRes val labelRes: Int,
    val transformation: String,
    val needsIv: Boolean,
    val keyAlgorithm: String = "",
    val ivLength: Int = 0,
    val provider: String? = null,
    val rsa: Boolean = false,
) {
    NONE(R.string.algo_none, "", false),
    BASE64(R.string.algo_base64, "BASE64", false),
    XOR(R.string.algo_xor, "XOR", false),

    AES_ECB(R.string.algo_aes_ecb, "AES/ECB/PKCS5Padding", false, "AES"),
    AES_CBC(R.string.algo_aes_cbc, "AES/CBC/PKCS5Padding", true, "AES", 16),
    AES_CTR(R.string.algo_aes_ctr, "AES/CTR/NoPadding", true, "AES", 16),
    AES_CFB(R.string.algo_aes_cfb, "AES/CFB/NoPadding", true, "AES", 16),
    AES_OFB(R.string.algo_aes_ofb, "AES/OFB/NoPadding", true, "AES", 16),
    AES_GCM(R.string.algo_aes_gcm, "AES/GCM/NoPadding", true, "AES", 12),

    DES_ECB(R.string.algo_des_ecb, "DES/ECB/PKCS5Padding", false, "DES"),
    DES_CBC(R.string.algo_des_cbc, "DES/CBC/PKCS5Padding", true, "DES", 8),
    DES3_ECB(R.string.algo_des3_ecb, "DESede/ECB/PKCS5Padding", false, "DESede"),
    DES3_CBC(R.string.algo_des3_cbc, "DESede/CBC/PKCS5Padding", true, "DESede", 8),

    BLOWFISH_ECB(R.string.algo_blowfish_ecb, "Blowfish/ECB/PKCS5Padding", false, "Blowfish"),
    BLOWFISH_CBC(R.string.algo_blowfish_cbc, "Blowfish/CBC/PKCS5Padding", true, "Blowfish", 8),

    CHACHA20(R.string.algo_chacha20, "ChaCha20", true, "ChaCha20", 12),
    CHACHA20_POLY(R.string.algo_chacha20_poly, "ChaCha20-Poly1305", true, "ChaCha20", 12),

    SM4_ECB(R.string.algo_sm4_ecb, "SM4/ECB/PKCS5Padding", false, "SM4", 0, "BC"),
    SM4_CBC(R.string.algo_sm4_cbc, "SM4/CBC/PKCS5Padding", true, "SM4", 16, "BC"),

    RSA_PKCS1(R.string.algo_rsa_pkcs1, "RSA/ECB/PKCS1Padding", false, "RSA", 0, null, true),
    RSA_OAEP(R.string.algo_rsa_oaep, "RSA/ECB/OAEPWithSHA-256AndMGF1Padding", false, "RSA", 0, null, true),
}

enum class KeyFormat(@param:StringRes val labelRes: Int) {
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

    private val bcProvider: BouncyCastleProvider by lazy { BouncyCastleProvider() }

    fun decrypt(spec: CryptoSpec, raw: String): String {
        if (!spec.enabled) return raw
        return when {
            spec.algo == Algo.NONE -> raw
            spec.algo == Algo.BASE64 -> String(Hex.unb64(raw), Charsets.UTF_8)
            spec.algo == Algo.XOR -> String(xor(parseBytes(raw, spec.payload), keyBytes(spec)), Charsets.UTF_8)
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
        return when {
            spec.algo == Algo.NONE -> plain
            spec.algo == Algo.BASE64 -> Hex.b64(plain.toByteArray(Charsets.UTF_8))
            spec.algo == Algo.XOR -> Hex.encode(xor(plain.toByteArray(Charsets.UTF_8), keyBytes(spec)))
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
        val key = if (spec.algo.rsa) {
            // RSA 由 doCipher 单独解析，这里不会用到
            ByteArray(0)
        } else {
            parseBytes(spec.key, spec.keyFormat)
        }
        return when (spec.algo) {
            Algo.DES_ECB, Algo.DES_CBC -> if (key.size == 8) key else sha256(key).copyOf(8)
            Algo.DES3_ECB, Algo.DES3_CBC -> if (key.size == 24) key else sha256(key).copyOf(24)
            Algo.BLOWFISH_ECB, Algo.BLOWFISH_CBC ->
                if (key.size in 4..56) key else sha256(key).copyOf(32)
            Algo.CHACHA20, Algo.CHACHA20_POLY -> if (key.size == 32) key else sha256(key)
            Algo.SM4_ECB, Algo.SM4_CBC -> if (key.size == 16) key else sha256(key).copyOf(16)
            Algo.AES_ECB, Algo.AES_CBC, Algo.AES_CTR, Algo.AES_CFB, Algo.AES_OFB, Algo.AES_GCM ->
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
        val algo = spec.algo
        val transformation = algo.transformation
        val cipher = if (algo.provider != null && algo.provider == "BC") {
            Cipher.getInstance(transformation, bcProvider)
        } else {
            Cipher.getInstance(transformation)
        }
        if (algo.rsa) {
            val keys = rsaKeys(spec)
            val key = if (mode == Cipher.ENCRYPT_MODE) {
                keys.public ?: throw IllegalStateException("RSA encryption requires a public key (X.509/PEM or derived from a private key)")
            } else {
                keys.private ?: throw IllegalStateException("RSA decryption requires a private key (PKCS#8/PEM)")
            }
            cipher.init(mode, key)
        } else {
            val keySpec = SecretKeySpec(keyBytes(spec), algo.keyAlgorithm)
            when {
                algo == Algo.AES_GCM -> cipher.init(mode, keySpec, GCMParameterSpec(128, ivBytes(spec, 12)))
                algo.needsIv -> cipher.init(mode, keySpec, IvParameterSpec(ivBytes(spec, algo.ivLength)))
                else -> cipher.init(mode, keySpec)
            }
        }
        return cipher.doFinal(input)
    }

    private class RsaKeys(val public: PublicKey?, val private: PrivateKey?)

    private fun rsaKeyBytes(spec: CryptoSpec): ByteArray {
        val text = spec.key.trim()
        return when (spec.keyFormat) {
            KeyFormat.HEX -> Hex.decode(text)
            KeyFormat.BASE64 -> Hex.unb64(text.replace("\n", "").replace("\r", "").replace(" ", ""))
            KeyFormat.TEXT -> {
                if (text.contains("BEGIN")) {
                    val body = text.lineSequence()
                        .filter { !it.contains("-----") }
                        .joinToString("")
                        .replace(" ", "")
                    Hex.unb64(body)
                } else {
                    val compact = text.replace("\n", "").replace("\r", "").replace(" ", "")
                    runCatching { Hex.unb64(compact) }.getOrElse { Hex.decode(text) }
                }
            }
        }
    }

    private fun rsaKeys(spec: CryptoSpec): RsaKeys {
        val bytes = rsaKeyBytes(spec)
        require(bytes.isNotEmpty()) { "RSA key is empty" }
        // 必须显式用 BouncyCastle 解析：Android 默认的 KeyFactory("RSA") 在 API 37 上会落到
        // AndroidKeyStore 实现，只接受 Keystore 内的密钥，普通 PKCS#8/X.509 会被拒绝。
        val factory = KeyFactory.getInstance("RSA", bcProvider)
        val privTry = runCatching { factory.generatePrivate(PKCS8EncodedKeySpec(bytes)) }
        val priv = privTry.getOrNull()
        val privErr = privTry.exceptionOrNull()?.message
        val pubTry = if (priv is RSAPrivateCrtKey) {
            runCatching { factory.generatePublic(RSAPublicKeySpec(priv.modulus, priv.publicExponent)) }
        } else {
            runCatching { factory.generatePublic(X509EncodedKeySpec(bytes)) }
        }
        val pub = pubTry.getOrNull()
        val pubErr = pubTry.exceptionOrNull()?.message
        if (priv == null && pub == null) {
            throw IllegalArgumentException(
                "RSA key format not recognized: expect a PKCS#8 private key or X.509 public key " +
                    "(PEM text or Base64 DER). priv=$privErr; pub=$pubErr"
            )
        }
        return RsaKeys(pub, priv)
    }

    private fun xor(data: ByteArray, key: ByteArray): ByteArray {
        require(key.isNotEmpty()) { "XOR requires a key" }
        val out = ByteArray(data.size)
        for (i in data.indices) out[i] = (data[i].toInt() xor key[i % key.size].toInt()).toByte()
        return out
    }
}

/** 一条已保存的表级加密配置，供设置弹窗里的「已保存配置」列表使用 */
data class SavedCrypto(
    val dbPath: String,
    val table: String,
    val spec: CryptoSpec,
    val savedAt: Long = 0L,
) {
    val dbName: String get() = dbPath.substringAfterLast('/').ifBlank { dbPath }
}

/**
 * 加解密配置持久化。
 *
 * 配置按「库 + 表」分别保存：同一张表下次打开自动带出上次的算法/密钥/格式，
 * 不同库的同名表、同一库的不同表互不影响。没有配置过的表返回 [CryptoSpec] 默认值（不加密）。
 */
object CryptoPrefs {
    private const val NAME = "rootdb_prefs"
    private const val ROOT = "spec2|"

    private const val F_ALGO = "algo"
    private const val F_KEY = "key"
    private const val F_KEYFMT = "keyfmt"
    private const val F_IV = "iv"
    private const val F_IVFMT = "ivfmt"
    private const val F_PAYLOAD = "payload"
    private const val F_TS = "ts"

    /** 一张表一个命名空间；库路径参与 key，避免同名的临时库/备份库互相串配置 */
    private fun prefix(dbPath: String?, table: String?): String? {
        if (table.isNullOrBlank()) return null
        val db = dbPath.orEmpty()
        return "$ROOT$db|$table|"
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun load(ctx: Context, dbPath: String?, table: String?): CryptoSpec {
        val p = prefix(dbPath, table) ?: return CryptoSpec()
        val sp = prefs(ctx)
        fun str(k: String, def: String): String = sp.getString(p + k, def) ?: def
        return CryptoSpec(
            algo = runCatching { Algo.valueOf(str(F_ALGO, Algo.NONE.name)) }.getOrDefault(Algo.NONE),
            key = str(F_KEY, ""),
            keyFormat = runCatching { KeyFormat.valueOf(str(F_KEYFMT, KeyFormat.TEXT.name)) }
                .getOrDefault(KeyFormat.TEXT),
            iv = str(F_IV, ""),
            ivFormat = runCatching { KeyFormat.valueOf(str(F_IVFMT, KeyFormat.TEXT.name)) }
                .getOrDefault(KeyFormat.TEXT),
            payload = runCatching { KeyFormat.valueOf(str(F_PAYLOAD, KeyFormat.BASE64.name)) }
                .getOrDefault(KeyFormat.BASE64),
        )
    }

    fun save(ctx: Context, dbPath: String?, table: String?, spec: CryptoSpec) {
        val p = prefix(dbPath, table) ?: return
        prefs(ctx).edit()
            .putString(p + F_ALGO, spec.algo.name)
            .putString(p + F_KEY, spec.key)
            .putString(p + F_KEYFMT, spec.keyFormat.name)
            .putString(p + F_IV, spec.iv)
            .putString(p + F_IVFMT, spec.ivFormat.name)
            .putString(p + F_PAYLOAD, spec.payload.name)
            .putLong(p + F_TS, System.currentTimeMillis())
            .apply()
    }

    /** 列出所有已保存的表级配置，最近保存的排在最前 */
    fun list(ctx: Context): List<SavedCrypto> {
        val sp = prefs(ctx)
        val out = ArrayList<SavedCrypto>()
        for (key in sp.all.keys) {
            if (!key.startsWith(ROOT) || !key.endsWith("|$F_ALGO")) continue
            val body = key.removePrefix(ROOT).removeSuffix("|$F_ALGO")
            val sep = body.lastIndexOf('|')
            if (sep <= 0) continue
            val db = body.substring(0, sep)
            val table = body.substring(sep + 1)
            if (table.isBlank()) continue
            val spec = load(ctx, db, table)
            out += SavedCrypto(db, table, spec, sp.getLong("$ROOT$db|$table|$F_TS", 0L))
        }
        return out.sortedByDescending { it.savedAt }
    }

    fun remove(ctx: Context, dbPath: String?, table: String?) {
        val p = prefix(dbPath, table) ?: return
        prefs(ctx).edit()
            .remove(p + F_ALGO)
            .remove(p + F_KEY)
            .remove(p + F_KEYFMT)
            .remove(p + F_IV)
            .remove(p + F_IVFMT)
            .remove(p + F_PAYLOAD)
            .remove(p + F_TS)
            .apply()
    }

    fun clearAll(ctx: Context) {
        val sp = prefs(ctx)
        val edit = sp.edit()
        for (key in sp.all.keys) if (key.startsWith(ROOT)) edit.remove(key)
        edit.apply()
    }
}
