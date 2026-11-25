package com.rootdb.studio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rootdb.studio.data.DbKeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 整库密钥记忆：Keystore 加密存储、读取、列表、删除、清空。 */
@RunWith(AndroidJUnit4::class)
class DbKeyStoreTest {

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val a = "/sdcard/Download/keytest-a.db"
    private val b = "/sdcard/Download/keytest-b.db"

    @After
    fun cleanup() {
        DbKeyStore.remove(ctx, a)
        DbKeyStore.remove(ctx, b)
    }

    @Test
    fun saveAndReadBack() {
        DbKeyStore.save(ctx, a, "RootDB@Test")
        assertEquals("RootDB@Test", DbKeyStore.get(ctx, a))
        assertNull("未保存的库应返回 null", DbKeyStore.get(ctx, b))
    }

    @Test
    fun listAndRemove() {
        DbKeyStore.save(ctx, a, "one")
        DbKeyStore.save(ctx, b, "two")
        val keys = DbKeyStore.list(ctx)
        assertTrue(keys.any { it.dbPath == a && it.keyLength == 3 })
        assertTrue(keys.any { it.dbPath == b })

        DbKeyStore.remove(ctx, a)
        assertNull(DbKeyStore.get(ctx, a))
        assertTrue("删除后列表不应再有该库", DbKeyStore.list(ctx).none { it.dbPath == a })
    }

    @Test
    fun clearAllRemovesEverything() {
        DbKeyStore.save(ctx, a, "one")
        DbKeyStore.save(ctx, b, "two")
        DbKeyStore.clearAll(ctx)
        assertTrue(DbKeyStore.list(ctx).isEmpty())
    }
}
