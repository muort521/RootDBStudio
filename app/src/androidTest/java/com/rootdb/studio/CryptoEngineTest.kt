package com.rootdb.studio

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rootdb.studio.data.Algo
import com.rootdb.studio.data.CryptoEngine
import com.rootdb.studio.data.CryptoSpec
import com.rootdb.studio.data.KeyFormat
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 字段级加解密回归测试：固定密钥/IV 的已知密文向量由桌面 JDK 21（SunJCE，SM4 用 BouncyCastle 1.72）
 * 生成，验证 Android 端各 Provider 的互通性，并检查 encrypt 的可复现性。
 */
@RunWith(AndroidJUnit4::class)
class CryptoEngineTest {

    private val plain = "RootDB Studio 数据库工坊 123"

    private data class Vector(val algo: Algo, val key: String, val iv: String, val cipherB64: String)

    private val aesKey = "2b7e151628aed2a6abf7158809cf4f3c"
    private val aesIv = "000102030405060708090a0b0c0d0e0f"
    private val gcmIv = "000102030405060708090a0b"
    private val desKey = "0123456789abcdef"
    private val desIv = "0001020304050607"
    private val des3Key = "0123456789abcdef23456789abcdef01456789abcdef0123"
    private val bfKey = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    private val chachaKey = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"
    private val chachaNonce = "000000000000004a00000000"
    private val sm4Key = "0123456789abcdeffedcba9876543210"

    private val vectors = listOf(
        Vector(Algo.AES_ECB, aesKey, "", "AQ6Vu0H4uQs3Vc1hJUosVvW+KjNSkRBxtEr2/XVH3pBGXj2sEmzIfEs1V55lAeRA"),
        Vector(Algo.AES_CBC, aesKey, aesIv, "d6dVgzvRfp1Fp9U4l2sgUHpyMUol3tDq0u77vcYLXeRAzI6r9TZEOfd/p4ipjnBn"),
        Vector(Algo.AES_CTR, aesKey, aesIv, "ApEIuN0vEuWufFOA9I8K9R/HA+BiWQBPbZSuefHXkm2l"),
        Vector(Algo.AES_CFB, aesKey, aesIv, "ApEIuN0vEuWufFOA9I8K9baaC5kmJzXwe+KVlsCItOI2"),
        Vector(Algo.AES_OFB, aesKey, aesIv, "ApEIuN0vEuWufFOA9I8K9WlCV3TtKLB63C7Y6wrBZ0aU"),
        Vector(Algo.AES_GCM, aesKey, gcmIv, "CaBUIvwqrhGUrXDvzalSArvcjFioY8c3sq9wOrYWZAU1D1FMhavC/DJ6oxYcq+B4EA=="),
        Vector(Algo.DES_ECB, desKey, "", "3Dnbx30PM/Ldejkg2krtXMVTSxkJwF5tMjoHHZ5D1RLDr2OOguWN+w=="),
        Vector(Algo.DES_CBC, desKey, desIv, "2Ntk1NhmtxN/IQDwOLqfXJsmf0VIAPvM2Y24LlC2I+1lCvzgTTpCQw=="),
        Vector(Algo.DES3_ECB, des3Key, "", "t6j6PQnQpUUcbK6CaKb1nyZTaJWc9uL27ROFFXeOdvP5kt+jTYSAhw=="),
        Vector(Algo.DES3_CBC, des3Key, desIv, "5fFtPBRpHGxnmxBfbQt/PniyNp3U+vTw267NmUUVfRlIJ1zmZJYuzw=="),
        Vector(Algo.BLOWFISH_ECB, bfKey, "", "xxT1gzrEar6H5jIj0uUzSRKNRSgtmhRjcraL3EmspLDelv8+3otT/Q=="),
        Vector(Algo.BLOWFISH_CBC, bfKey, desIv, "PF1BTCqGJY3SImnEscZXqRmPrNxns7rvfQYgxb0VkEpAF4Nw7rz8kQ=="),
        Vector(Algo.CHACHA20, chachaKey, chachaNonce, "/WpxNP/iFRr1R/7pBTToOmK+L4SIcdhcQfN5LmXC752w"),
        Vector(Algo.CHACHA20_POLY, chachaKey, chachaNonce, "cCA+hwRZ+bJbq0MG10P7eDz1kizYlpUHyeovcRTT/kq5W5HK477EEhDyMVm1ViMgQA=="),
        Vector(Algo.SM4_ECB, sm4Key, "", "+B0tWhIpwP66Nqg4U+mH8fwt9t56IvpNu31aZXxUQSwBjMx5UZ3xlhsD47TyV0yH"),
        Vector(Algo.SM4_CBC, sm4Key, aesIv, "oXvIfZ5iOz7zVMwIJhNEhQglK9XZdd0bAMvBc1XX7603x8FqSFYZHMmqf+Ot5Hrr"),
    )

    @Test
    fun knownVectorsDecryptAndEncrypt() {
        for (v in vectors) {
            val spec = CryptoSpec(
                algo = v.algo,
                key = v.key,
                keyFormat = KeyFormat.HEX,
                iv = v.iv,
                ivFormat = KeyFormat.HEX,
                payload = KeyFormat.BASE64,
            )
            assertEquals("decrypt ${v.algo}", plain, CryptoEngine.decrypt(spec, v.cipherB64))
            assertEquals("encrypt ${v.algo}", v.cipherB64, CryptoEngine.encrypt(spec, plain))
        }
    }

    @Test
    fun base64AndXorRoundTrip() {
        val b64 = CryptoSpec(algo = Algo.BASE64, payload = KeyFormat.BASE64)
        assertEquals(plain, CryptoEngine.decrypt(b64, CryptoEngine.encrypt(b64, plain)))

        val xor = CryptoSpec(algo = Algo.XOR, key = "rootdb", payload = KeyFormat.HEX)
        val enc = CryptoEngine.encrypt(xor, plain)
        assertEquals(plain, CryptoEngine.decrypt(xor, enc))
        // XOR 输出必须是合法十六进制
        assertEquals(0, enc.length % 2)
        require(enc.all { it in "0123456789abcdef" })
    }

    private val rsaPriv =
        "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDE9unipeEuivTlErgoyfKolozgsTdxammkVfFghqRhm40vUEdZSVyK4MWrwoMH/OjYdckZwELcdW6k+E5T9oG0KvCazhOrMCAXRtIrstq7Oke66pXmKFkkELsqrhAJsvzIIHK0aU+EH7cBkj3CAk7jz+g6aDIzz8wTKGU2rcWMxqKLpt7i5BVoGhtQiWFAVUEsv0MiVr+XLxA39g8q78LtbDuhND5YDt/J9IEkDtpU/hcFTKD80CSzP24TnXk8/erKjTqY/T/XFj2vAs8tkONb8TqbUTkxqp7h3p3JI81ZF/rgNJd97GqDDKekMoU9nFvOsWvrVYfbwpNOikZYx+kHAgMBAAECggEAI4Njk1aWXeZ3FnCEHPKgmo4ek4rQdNYbeyJ11ZIhRGogEDZYzvv/wBib+4akSoXq09CU9FwqkgZobz1IHjJ1m28cl8TtXfHc72V5EqD6eESfV0/hvDBtNigAH0doI9+OXWUgWXAdgrSXMiTUls0EvWWouRM9bgMbCB9B9JRraKC9BYtBt8sLqqE9xEuSgoOstigT6TkvimrdbSkaMC5eW4krzUHSwp8iBZd6ehY/duDar4VPCbgu8fxCzkz48SvFJxgHFWAdYGBhGpJygv7xXtkOIXDn83J4B6O7LnwmMhzxXfxdFux6RlZ03PL+dt4YNCUM+yj85RBg65fpQRYrcQKBgQDdlUsnFozbf3Y4VOp33PMBfgWI5zGDuWTJLOqZXy4nFaH95GkWDoFnsZ/dMCBCCQz10no0bBfrI7G7O/rE/cXeVhfGX1SJoSWW6B0bfJZecoWobhh/RBpLL26trxxOj77hQJphuSwlKEKeXYy3b6SyStBHK1yT4Jgs9f7WcnbFUQKBgQDjjrgy1JxkMb0VuHxOgYavCtZSw95oD1maazwtIzGHAO41PFSbsimdIDdvXxSSZrDZb+qUMHV7wNuTnIdIQaxb5uuALOTvF2MrzHxSyWVGReh3z5YXoXJl0/UljfBMo1hle9F5j8/ly8apvK379AUi0i5EUk6MfPPTstt4MoCS1wKBgQCgltedn5k8Z1c9okU/05EGjtfiDXR0PAPQTU5FE5c2WlZJ1KVYh/C/PxtWSc/HLBoJep54qbJ4BymDQggPhsBFz+1xJpv1FyZQBS87QYMVNjiqBgiZC7lmrhyvoc6JHWLAi3qXwoU89L27Wp9mPnKFiPUD72gsAEUpqKslrlVn0QKBgQDQlPlyXkAb9xuC1V/b9g4mVKCvrKooaCmRycmW9jNntAqk8W/8ZwqN5AHsLBMHPunL5TH4M5L8pIKgg1HdgZUjiD9FdeFC5wUYGI+8sq4KA/paGAVUE8BwZiDHE5u4I+Ha4+vWqWO/F1I6M6Cm2b37dPzD0038qGw3GDLjsib4cQKBgE3+CBZqRNhF+Dk7Cxjgoocfm90IRBWgCxry0VD0mWzJAxXM27uDrVuGjlx8O25useCwFTgWKPLAm2LY9A+zbnP1laUwPfNoI6cz1jfUX5LCy7FfFoEXyOregRqAsxuUJa5gkzMxq5PTKqkrqDa/Cr16wZvj4zcfr8gJuLBSgcWr"

    private val rsaPub =
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAxPbp4qXhLor05RK4KMnyqJaM4LE3cWpppFXxYIakYZuNL1BHWUlciuDFq8KDB/zo2HXJGcBC3HVupPhOU/aBtCrwms4TqzAgF0bSK7LauzpHuuqV5ihZJBC7Kq4QCbL8yCBytGlPhB+3AZI9wgJO48/oOmgyM8/MEyhlNq3FjMaii6be4uQVaBobUIlhQFVBLL9DIla/ly8QN/YPKu/C7Ww7oTQ+WA7fyfSBJA7aVP4XBUyg/NAksz9uE515PP3qyo06mP0/1xY9rwLPLZDjW/E6m1E5Maqe4d6dySPNWRf64DSXfexqgwynpDKFPZxbzrFr61WH28KTTopGWMfpBwIDAQAB"

    private fun pem(body: String, label: String) =
        "-----BEGIN $label-----\n" + body.chunked(64).joinToString("\n") + "\n-----END $label-----"

    @Test
    fun rsaRoundTripBothPaddings() {
        for (algo in listOf(Algo.RSA_PKCS1, Algo.RSA_OAEP)) {
            // 私钥直接用于加密（内部推导公钥）与解密
            val privSpec = CryptoSpec(algo = algo, key = rsaPriv, keyFormat = KeyFormat.BASE64)
            val enc1 = CryptoEngine.encrypt(privSpec, "RootDB Studio")
            assertEquals("rsa priv roundtrip $algo", "RootDB Studio", CryptoEngine.decrypt(privSpec, enc1))

            // 公钥加密 → 私钥解密
            val pubSpec = CryptoSpec(algo = algo, key = rsaPub, keyFormat = KeyFormat.BASE64)
            val enc2 = CryptoEngine.encrypt(pubSpec, "数据库工坊")
            assertEquals("rsa pub->priv $algo", "数据库工坊", CryptoEngine.decrypt(privSpec, enc2))

            // PEM（TEXT 格式）解析
            val pemSpec = CryptoSpec(algo = algo, key = pem(rsaPriv, "PRIVATE KEY"), keyFormat = KeyFormat.TEXT)
            assertEquals("rsa pem $algo", "RootDB Studio", CryptoEngine.decrypt(pemSpec, enc1))
        }
    }
}
