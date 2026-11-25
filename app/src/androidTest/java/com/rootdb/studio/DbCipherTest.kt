package com.rootdb.studio

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rootdb.studio.data.DbSession
import com.rootdb.studio.data.DbOpenException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** SQLCipher 整库打开回归：默认 SQLCipher 4 参数、错误密码、明文库。 */
@RunWith(AndroidJUnit4::class)
class DbCipherTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun loadNative() {
            System.loadLibrary("sqlcipher")
        }
    }

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun newFile(name: String) = File(ctx.cacheDir, "$name-${System.nanoTime()}.db").also {
        it.parentFile?.mkdirs()
        if (it.exists()) it.delete()
    }

    @Test
    fun openSqlCipher4DatabaseWithPassword() {
        val file = newFile("cipher4")
        net.zetetic.database.sqlcipher.SQLiteDatabase.openOrCreateDatabase(file, "RootDB@Test", null, null).use { db ->
            db.execSQL("CREATE TABLE users(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, note TEXT)")
            db.execSQL("INSERT INTO users(name, note) VALUES('alice', 'first'), ('bob', 'second')")
        }

        DbSession.open(ctx, file.absolutePath, "RootDB@Test").use { session ->
            assertTrue("应识别为加密库", session.encrypted)
            assertTrue("应读到 users 表", session.tables().any { it.name == "users" })
            assertEquals(2L, session.count("users"))
        }
    }

    @Test
    fun openSqlCipher3LegacyDatabase() {
        val file = newFile("legacy3")
        val hook = object : net.zetetic.database.sqlcipher.SQLiteDatabaseHook {
            override fun preKey(connection: net.zetetic.database.sqlcipher.SQLiteConnection) {
                connection.execute("PRAGMA cipher_compatibility = 3", emptyArray<Any>(), null)
            }

            override fun postKey(connection: net.zetetic.database.sqlcipher.SQLiteConnection) = Unit
        }
        net.zetetic.database.sqlcipher.SQLiteDatabase
            .openOrCreateDatabase(file, "Legacy@Test", null, null, hook).use { db ->
                db.execSQL("CREATE TABLE legacy(id INTEGER PRIMARY KEY, v TEXT)")
                db.execSQL("INSERT INTO legacy(v) VALUES('old')")
            }

        DbSession.open(ctx, file.absolutePath, "Legacy@Test").use { session ->
            assertTrue("应兼容 SQLCipher 3.x 参数", session.encrypted)
            assertEquals(1L, session.count("legacy"))
        }
    }

    @Test
    fun wrongPasswordIsRejected() {
        val file = newFile("wrongpass")
        net.zetetic.database.sqlcipher.SQLiteDatabase.openOrCreateDatabase(file, "RootDB@Test", null, null).use { db ->
            db.execSQL("CREATE TABLE secrets(id INTEGER PRIMARY KEY, v TEXT)")
            db.execSQL("INSERT INTO secrets(v) VALUES('x')")
        }

        try {
            DbSession.open(ctx, file.absolutePath, "not-the-password").close()
            fail("错误密码不应打开成功")
        } catch (e: DbOpenException) {
            assertTrue("错误密码应提示需要密码/密钥错误", e.needPassword || e.resId != 0)
        }
    }

    /** 生成一个加密演示库到 Download，供真机 UI 验证「整库密钥记忆」用 */
    @Test
    fun exportDemoEncryptedDatabaseForUi() {
        val demo = File("/sdcard/Download/sqlcipher_2.1.0_demo.db")
        if (demo.exists()) demo.delete()
        net.zetetic.database.sqlcipher.SQLiteDatabase.openOrCreateDatabase(demo, "Demo@2026", null, null).use { db ->
            db.execSQL("CREATE TABLE notes(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, body TEXT)")
            db.execSQL("INSERT INTO notes(title, body) VALUES('demo', 'SQLCipher whole-database key demo')")
        }
        assertTrue("演示库应写入成功", demo.length() > 0)
        DbSession.open(ctx, demo.absolutePath, "Demo@2026").use { session ->
            assertTrue(session.encrypted)
            assertEquals(1L, session.count("notes"))
        }
    }

    @Test
    fun plainDatabaseOpensWithoutPassword() {        val file = newFile("plain")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE notes(id INTEGER PRIMARY KEY, body TEXT)")
            db.execSQL("INSERT INTO notes(body) VALUES('hello')")
        }

        DbSession.open(ctx, file.absolutePath, null).use { session ->
            assertTrue("明文库不应标记为加密", !session.encrypted)
            assertEquals(1L, session.count("notes"))
        }
    }
}

