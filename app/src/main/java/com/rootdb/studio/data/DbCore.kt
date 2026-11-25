package com.rootdb.studio.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.annotation.StringRes
import com.rootdb.studio.R
import com.rootdb.studio.root.RootShell
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.Closeable

enum class ValueType { NULL, INTEGER, REAL, TEXT, BLOB }

data class Cell(val type: ValueType, val text: String?, val blob: ByteArray?) {
    val display: String
        get() = when (type) {
            ValueType.NULL -> "NULL"
            ValueType.BLOB -> blob?.let { "BLOB " + Hex.encode(it).take(64) } ?: "BLOB"
            else -> text ?: ""
        }
}

data class ColumnInfo(val name: String, val type: String, val notNull: Boolean, val pk: Boolean)

data class TableInfo(val name: String, val type: String)

data class RowData(val rowid: Long?, val cells: List<Cell>)

data class RowsPage(val columns: List<String>, val rows: List<RowData>, val total: Long?)

/** 数据库打开失败：带上要展示的字符串资源与可选技术细节，由 UI 层格式化到当前语言 */
class DbOpenException(
    @StringRes val resId: Int,
    val detail: String? = null,
    val needPassword: Boolean = false,
) : Exception(detail ?: "")

interface DbHandle {
    fun rawQuery(sql: String, args: Array<String>?): Cursor
    fun execSQL(sql: String)
    fun update(table: String, values: ContentValues, where: String?, args: Array<String>?): Int
    fun delete(table: String, where: String?, args: Array<String>?): Int
    fun insert(table: String, values: ContentValues): Long
    fun close()
}

private class PlainHandle(private val db: SQLiteDatabase) : DbHandle {
    override fun rawQuery(sql: String, args: Array<String>?): Cursor = db.rawQuery(sql, args)
    override fun execSQL(sql: String) = db.execSQL(sql)
    override fun update(table: String, values: ContentValues, where: String?, args: Array<String>?): Int =
        db.update(table, values, where, args)

    override fun delete(table: String, where: String?, args: Array<String>?): Int = db.delete(table, where, args)
    override fun insert(table: String, values: ContentValues): Long = db.insert(table, null, values)
    override fun close() = db.close()
}

private class CipherHandle(private val db: net.zetetic.database.sqlcipher.SQLiteDatabase) : DbHandle {
    override fun rawQuery(sql: String, args: Array<String>?): Cursor = db.rawQuery(sql, args)
    override fun execSQL(sql: String) = db.execSQL(sql)
    override fun update(table: String, values: ContentValues, where: String?, args: Array<String>?): Int =
        db.update(table, values, where, args)

    override fun delete(table: String, where: String?, args: Array<String>?): Int = db.delete(table, where, args)
    override fun insert(table: String, values: ContentValues): Long = db.insert(table, null, values)
    override fun close() = db.close()
}

class DbSession private constructor(
    private val ctx: Context,
    val sourcePath: String,
    val workingFile: File,
    private val handle: DbHandle,
    val encrypted: Boolean,
    val password: String?,
) : Closeable {

    var dirty: Boolean = false
        private set

    private fun q(identifier: String): String = "\"" + identifier.replace("\"", "\"\"") + "\""

    fun tables(): List<TableInfo> {
        val list = ArrayList<TableInfo>()
        handle.rawQuery(
            "SELECT name, type FROM sqlite_master WHERE type IN ('table','view') AND name NOT LIKE 'sqlite_%' ORDER BY name",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                list.add(TableInfo(c.getString(0) ?: continue, c.getString(1) ?: "table"))
            }
        }
        return list
    }

    fun columns(table: String): List<ColumnInfo> {
        val list = ArrayList<ColumnInfo>()
        handle.rawQuery("PRAGMA table_info(${q(table)})", null).use { c ->
            val nameIdx = c.getColumnIndex("name")
            val typeIdx = c.getColumnIndex("type")
            val nnIdx = c.getColumnIndex("notnull")
            val pkIdx = c.getColumnIndex("pk")
            while (c.moveToNext()) {
                list.add(
                    ColumnInfo(
                        name = c.getString(nameIdx) ?: "",
                        type = c.getString(typeIdx) ?: "",
                        notNull = c.getInt(nnIdx) != 0,
                        pk = c.getInt(pkIdx) != 0,
                    )
                )
            }
        }
        return list
    }

    fun count(table: String): Long? = try {
        handle.rawQuery("SELECT COUNT(*) FROM ${q(table)}", null).use { c ->
            if (c.moveToFirst()) c.getLong(0) else null
        }
    } catch (t: Throwable) {
        null
    }

    /** 读取一页数据；rowid 表会额外带上 rowid 便于精确修改；filter 非空时按所有非 BLOB 列模糊匹配 */
    fun page(table: String, offset: Int, limit: Int, filter: String? = null): RowsPage {
        val keyword = filter?.trim().orEmpty()
        val cols = if (keyword.isEmpty()) emptyList() else columns(table)
            .filterNot { it.type.contains("BLOB", ignoreCase = true) }
        val where = if (cols.isEmpty()) "" else
            "WHERE (" + cols.joinToString(" OR ") { "lower(CAST(${q(it.name)} AS TEXT)) LIKE ? ESCAPE '\\'" } + ")"
        val args = if (cols.isEmpty()) null else {
            val esc = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
            Array(cols.size) { "%${esc.lowercase()}%" }
        }
        var useRowid = true
        var cursor: Cursor? = null
        var sql = "SELECT rowid AS __rid__, * FROM ${q(table)} $where LIMIT $limit OFFSET $offset"
        try {
            cursor = handle.rawQuery(sql, args)
        } catch (t: Throwable) {
            useRowid = false
            sql = "SELECT * FROM ${q(table)} $where LIMIT $limit OFFSET $offset"
            cursor = handle.rawQuery(sql, args)
        }
        val columns = ArrayList<String>()
        val rows = ArrayList<RowData>()
        cursor.use { c ->
            val startIdx = if (useRowid) 1 else 0
            for (i in startIdx until c.columnCount) columns.add(c.getColumnName(i))
            while (c.moveToNext()) {
                val rowid = if (useRowid) c.getLong(0) else null
                val cells = ArrayList<Cell>(c.columnCount - startIdx)
                for (i in startIdx until c.columnCount) {
                    cells.add(readCell(c, i))
                }
                rows.add(RowData(rowid, cells))
            }
        }
        return RowsPage(columns, rows, countFiltered(table, where, args))
    }

    private fun countFiltered(table: String, where: String, args: Array<String>?): Long? = try {
        handle.rawQuery("SELECT COUNT(*) FROM ${q(table)} $where", args).use { c ->
            if (c.moveToFirst()) c.getLong(0) else null
        }
    } catch (t: Throwable) {
        null
    }

    private fun readCell(c: Cursor, index: Int): Cell {
        return when (c.getType(index)) {
            Cursor.FIELD_TYPE_NULL -> Cell(ValueType.NULL, null, null)
            Cursor.FIELD_TYPE_INTEGER -> Cell(ValueType.INTEGER, c.getLong(index).toString(), null)
            Cursor.FIELD_TYPE_FLOAT -> Cell(ValueType.REAL, c.getDouble(index).toString(), null)
            Cursor.FIELD_TYPE_BLOB -> Cell(ValueType.BLOB, null, c.getBlob(index))
            else -> Cell(ValueType.TEXT, c.getString(index), null)
        }
    }

    fun updateCell(table: String, rowid: Long, column: String, value: Any?): Boolean {
        val cv = ContentValues()
        putTyped(cv, column, value)
        val where = "rowid = ?"
        val n = handle.update(table, cv, where, arrayOf(rowid.toString()))
        if (n > 0) dirty = true
        return n > 0
    }

    fun updateByKey(table: String, whereClause: String, whereArgs: Array<String>, column: String, value: Any?): Boolean {
        val cv = ContentValues()
        putTyped(cv, column, value)
        val n = handle.update(table, cv, whereClause, whereArgs)
        if (n > 0) dirty = true
        return n > 0
    }

    private fun putTyped(cv: ContentValues, column: String, value: Any?) {
        when (value) {
            null -> cv.putNull(column)
            is Long -> cv.put(column, value)
            is Int -> cv.put(column, value)
            is Double -> cv.put(column, value)
            is Float -> cv.put(column, value)
            is ByteArray -> cv.put(column, value)
            is Boolean -> cv.put(column, value)
            else -> cv.put(column, value.toString())
        }
    }

    fun insertRow(table: String, values: Map<String, Any?>): Long {
        val cv = ContentValues()
        values.forEach { (k, v) -> putTyped(cv, k, v) }
        val id = handle.insert(table, cv)
        if (id >= 0) dirty = true
        return id
    }

    fun deleteRow(table: String, rowid: Long): Boolean {
        val n = handle.delete(table, "rowid = ?", arrayOf(rowid.toString()))
        if (n > 0) dirty = true
        return n > 0
    }

    /** 执行任意 SQL。查询语句返回结果文本，写语句返回执行结果 */
    fun exec(sql: String): String {
        val trimmed = sql.trim().trimEnd(';')
        val first = trimmed.trimStart().lowercase()
        val isQuery = first.startsWith("select") || first.startsWith("pragma") || first.startsWith("explain") ||
            first.startsWith("with")
        if (!isQuery) {
            handle.execSQL(trimmed)
            dirty = true
            return ctx.getString(R.string.sql_result_ok)
        }
        val sb = StringBuilder()
        handle.rawQuery(trimmed, null).use { c ->
            sb.append(c.columnNames.joinToString(" | ")).append('\n')
            var n = 0
            while (c.moveToNext() && n < 500) {
                val line = (0 until c.columnCount).joinToString(" | ") { i ->
                    when (c.getType(i)) {
                        Cursor.FIELD_TYPE_NULL -> "NULL"
                        Cursor.FIELD_TYPE_BLOB -> "BLOB(" + (c.getBlob(i)?.size ?: 0) + "B)"
                        else -> c.getString(i) ?: ""
                    }
                }
                sb.append(line).append('\n')
                n++
            }
            sb.append(ctx.getString(R.string.sql_result_rows, n))
        }
        return sb.toString()
    }

    fun writeBack(): Boolean {
        if (!dirty) return true
        handle.close()
        val ok = RootShell.writeBack(workingFile, sourcePath)
        if (ok) dirty = false
        return ok
    }

    override fun close() {
        try {
            handle.close()
        } catch (_: Throwable) {
        }
    }

    companion object {
        private val nativeLoaded: Boolean by lazy {
            try {
                System.loadLibrary("sqlcipher")
                true
            } catch (t: Throwable) {
                false
            }
        }

        fun cacheDir(ctx: Context): File = File(ctx.cacheDir, "dbwork").apply { mkdirs() }

        /** 清空工作副本缓存（`cache/dbwork` 下的全部内容），不影响已打开的会话 */
        fun clearCache(ctx: Context) {
            val dir = cacheDir(ctx)
            dir.listFiles()?.forEach { runCatching { it.deleteRecursively() } }
        }

        fun safeName(path: String): String {
            val base = path.substringAfterLast('/')
            val hash = Integer.toHexString(path.hashCode())
            return "${base.ifBlank { "db" }}.$hash.db"
        }

        /** 把源文件（含 WAL/SHM）复制到应用私有缓存，root 失败时尝试直接复制 */
        private fun stage(ctx: Context, sourcePath: String, wanted: File, withJournal: Boolean): Boolean {
            for (suffix in listOf("", "-wal", "-shm", "-journal")) {
                val target = if (suffix.isEmpty()) wanted else File(wanted.absolutePath + suffix)
                if (!withJournal && suffix != "") continue
                if (target.exists()) target.delete()
            }
            var ok = RootShell.copyToLocal(sourcePath, wanted)
            if (!ok) {
                // 无 root 时，尝试应用自身可读
                val src = File(sourcePath)
                if (src.exists() && src.canRead()) {
                    try {
                        copyFile(src, wanted)
                        ok = wanted.exists()
                    } catch (_: Throwable) {
                        ok = false
                    }
                }
            }
            if (!ok) return false
            if (withJournal) {
                for (suffix in listOf("-wal", "-shm")) {
                    val src = File(sourcePath + suffix)
                    if (src.exists() || RootShell.exists(sourcePath + suffix)) {
                        val target = File(wanted.absolutePath + suffix)
                        if (!RootShell.copyToLocal(sourcePath + suffix, target)) {
                            try {
                                if (src.canRead()) copyFile(src, target)
                            } catch (_: Throwable) {
                            }
                        }
                    }
                }
            }
            return true
        }

        private fun copyFile(src: File, dst: File) {
            dst.parentFile?.mkdirs()
            FileInputStream(src).use { input ->
                FileOutputStream(dst).use { output ->
                    input.copyTo(output)
                }
            }
        }

        fun open(ctx: Context, sourcePath: String, password: String?, withJournal: Boolean = true): DbSession {
            val wanted = File(cacheDir(ctx), safeName(sourcePath))
            wanted.parentFile?.mkdirs()
            if (!stage(ctx, sourcePath, wanted, withJournal)) {
                throw DbOpenException(R.string.err_cannot_read_source)
            }
            if (password.isNullOrBlank()) {
                val db = try {
                    SQLiteDatabase.openDatabase(wanted.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
                } catch (t: Throwable) {
                    throw DbOpenException(
                        R.string.err_cannot_open_db,
                        t.message ?: ctx.getString(R.string.err_file_maybe_encrypted),
                        needPassword = true,
                    )
                }
                val handle = PlainHandle(db)
                try {
                    handle.rawQuery("SELECT count(*) FROM sqlite_master", null).use { it.moveToFirst() }
                } catch (t: Throwable) {
                    handle.close()
                    throw DbOpenException(R.string.err_read_schema_encrypted, needPassword = true)
                }
                return DbSession(ctx, sourcePath, wanted, handle, false, null)
            }

            if (!nativeLoaded) throw DbOpenException(R.string.err_sqlcipher_load)
            val db = try {
                net.zetetic.database.sqlcipher.SQLiteDatabase.openDatabase(
                    wanted.absolutePath,
                    password,
                    null,
                    net.zetetic.database.sqlcipher.SQLiteDatabase.OPEN_READWRITE,
                    null,
                )
            } catch (t: Throwable) {
                throw DbOpenException(R.string.err_wrong_key_or_format, t.message ?: "")
            }
            val handle = CipherHandle(db)
            try {
                handle.rawQuery("SELECT count(*) FROM sqlite_master", null).use { it.moveToFirst() }
            } catch (t: Throwable) {
                handle.close()
                throw DbOpenException(R.string.err_wrong_key)
            }
            return DbSession(ctx, sourcePath, wanted, handle, true, password)
        }
    }
}
