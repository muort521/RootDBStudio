package com.rootdb.studio.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HistoryEntry(
    val id: Long,
    val time: Long,
    val db: String,
    val table: String,
    val op: String,
    val column: String,
    val oldValue: String?,
    val newValue: String?,
    val rowid: Long?,
) {
    val timeText: String
        get() = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(time))
}

object HistoryStore {

    private fun file(ctx: Context) = File(ctx.filesDir, "history.json")

    fun list(ctx: Context): List<HistoryEntry> {
        val f = file(ctx)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            val out = ArrayList<HistoryEntry>(arr.length())
            for (i in arr.length() - 1 downTo 0) {
                val o = arr.getJSONObject(i)
                out.add(
                    HistoryEntry(
                        id = o.optLong("id"),
                        time = o.optLong("time"),
                        db = o.optString("db"),
                        table = o.optString("table"),
                        op = o.optString("op"),
                        column = o.optString("column"),
                        oldValue = o.optString("old", ""),
                        newValue = o.optString("new", ""),
                        rowid = o.optLong("rowid", -1).takeIf { it >= 0 },
                    )
                )
            }
            out
        } catch (t: Throwable) {
            emptyList()
        }
    }

    fun add(
        ctx: Context,
        db: String,
        table: String,
        op: String,
        column: String = "",
        oldValue: String? = null,
        newValue: String? = null,
        rowid: Long? = null,
    ): Boolean {
        val entries = list(ctx).toMutableList()
        val id = (entries.maxOfOrNull { it.id } ?: 0L) + 1
        entries.add(0, HistoryEntry(id, System.currentTimeMillis(), db, table, op, column, oldValue, newValue, rowid))
        return write(ctx, entries.take(500))
    }

    fun remove(ctx: Context, id: Long) {
        write(ctx, list(ctx).filter { it.id != id })
    }

    fun clear(ctx: Context) {
        write(ctx, emptyList())
    }

    private fun write(ctx: Context, entries: List<HistoryEntry>): Boolean = try {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(
                JSONObject()
                    .put("id", e.id)
                    .put("time", e.time)
                    .put("db", e.db)
                    .put("table", e.table)
                    .put("op", e.op)
                    .put("column", e.column)
                    .put("old", e.oldValue ?: "")
                    .put("new", e.newValue ?: "")
                    .put("rowid", e.rowid ?: -1)
            )
        }
        file(ctx).writeText(arr.toString())
        true
    } catch (t: Throwable) {
        false
    }
}

object BackupStore {

    fun dir(ctx: Context): File = File(ctx.filesDir, "backups").apply { mkdirs() }

    fun stamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

    fun create(ctx: Context, session: DbSession, note: String = ""): File? {
        val name = session.sourcePath.substringAfterLast('/').ifBlank { "db" }
        val target = File(dir(ctx), "$name.${stamp()}${if (note.isNotBlank()) ".$note" else ""}.db")
        return try {
            session.workingFile.inputStream().use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
            target
        } catch (t: Throwable) {
            null
        }
    }

    fun list(ctx: Context): List<File> =
        dir(ctx).listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun delete(file: File): Boolean = file.delete()

    fun humanSize(size: Long): String = when {
        size >= 1024L * 1024 * 1024 -> String.format("%.1f GB", size / 1024.0 / 1024 / 1024)
        size >= 1024L * 1024 -> String.format("%.1f MB", size / 1024.0 / 1024)
        size >= 1024 -> String.format("%.0f KB", size / 1024.0)
        else -> "$size B"
    }

    /** 导出到系统“下载”目录；返回可分享的 Uri */
    fun exportToDownloads(ctx: Context, file: File, mime: String = "application/octet-stream"): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = ctx.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
                resolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                val target = File(dir, file.name)
                file.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
                Uri.fromFile(target)
            }
        } catch (t: Throwable) {
            null
        }
    }
}

object PathPrefs {
    private const val NAME = "rootdb_prefs"

    fun lastPath(ctx: Context): String =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("last_path", "/data/data") ?: "/data/data"

    fun saveLastPath(ctx: Context, path: String) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().putString("last_path", path).apply()
    }
}

/** 最近浏览过的目录，最多保留 [LIMIT] 条，新的在最前 */
object RecentStore {
    private const val NAME = "rootdb_prefs"
    private const val KEY = "recent_paths"
    private const val LIMIT = 10

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun list(ctx: Context): List<String> =
        prefs(ctx).getString(KEY, "")
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            ?.map { canonicalPath(it) }
            ?.distinct()
            ?.take(LIMIT)
            ?: emptyList()

    fun add(ctx: Context, path: String): List<String> {
        if (path.isBlank()) return list(ctx)
        val p = canonicalPath(path)
        val next = (listOf(p) + list(ctx).filter { it != p }).take(LIMIT)
        prefs(ctx).edit().putString(KEY, next.joinToString("\n")).apply()
        return next
    }

    fun clear(ctx: Context): List<String> {
        prefs(ctx).edit().remove(KEY).apply()
        return emptyList()
    }
}

/**
 * 统一内部存储路径：`/sdcard`、`/storage/self/primary` 都归一到 `/storage/emulated/0`。
 *
 * 没有 root 时 `/sdcard` 是指向应用自身挂载视图里 `/storage/self/primary` 的软链接，
 * 普通应用即使拿到「所有文件访问权限」也常常列不出内容；`/storage/emulated/0` 则可以正常读写。
 */
fun canonicalPath(path: String): String {
    val base = "/storage/emulated/0"
    val p = path.trim().trimEnd('/')
    return when {
        p.isEmpty() -> "/"
        p == "/sdcard" || p == "/storage/self/primary" -> base
        p.startsWith("/sdcard/") -> base + p.removePrefix("/sdcard")
        p.startsWith("/storage/self/primary/") -> base + p.removePrefix("/storage/self/primary")
        else -> p
    }
}
