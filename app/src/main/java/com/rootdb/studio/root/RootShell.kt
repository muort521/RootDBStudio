package com.rootdb.studio.root

import android.os.Process as AndroidProcess
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.locks.ReentrantLock

/**
 * 一条 root 交互式 shell（su -c sh），所有命令共用，避免反复弹授权框。
 * 无 root 时降级为普通 sh，仅能访问本应用可读的路径（如 /sdcard）。
 */
object RootShell {

    data class Entry(
        val name: String,
        val path: String,
        val isDir: Boolean,
        val isLink: Boolean,
        val size: Long,
        val date: String,
        val perm: String,
    )

    data class Stat(val size: Long, val mtime: Long, val mode: String)

    private val lock = ReentrantLock()
    private var proc: java.lang.Process? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null
    private var seq = 0
    private var rootChecked = false
    private var rootResult = false

    var lastError: String? = null
        private set

    val hasRoot: Boolean
        get() {
            if (!rootChecked) {
                rootChecked = true
                rootResult = run("id -u")?.trim()?.lineSequence()?.lastOrNull { it.isNotBlank() }?.trim() == "0"
            }
            return rootResult
        }

    fun resetRootCache() {
        rootChecked = false
        rootResult = false
    }

    fun quote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    private fun alive(): Boolean = proc?.isAlive == true && writer != null && reader != null

    private fun closeQuietly() {
        try {
            writer?.close()
        } catch (_: Throwable) {
        }
        try {
            reader?.close()
        } catch (_: Throwable) {
        }
        try {
            proc?.destroy()
        } catch (_: Throwable) {
        }
        writer = null
        reader = null
        proc = null
    }

    private fun start(): Boolean {
        closeQuietly()
        val candidates = listOf(
            // 进入 init 的挂载命名空间，才能看到完整的 /data/data（应用命名空间会被隔离）
            arrayOf("su", "-c", "nsenter -t 1 -m -- /system/bin/sh"),
            arrayOf("su", "-mm", "-c", "sh"),
            arrayOf("su", "-c", "sh"),
            arrayOf("sh", "-c", "sh"),
        )
        var bestUid0 = false
        for (cmd in candidates) {
            try {
                val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
                val w = BufferedWriter(OutputStreamWriter(p.outputStream, Charsets.UTF_8))
                val r = BufferedReader(InputStreamReader(p.inputStream, Charsets.UTF_8))
                proc = p
                writer = w
                reader = r
                val probe = "__RD_PROBE__"
                w.write("echo $probe\n")
                w.flush()
                var alive = false
                val deadline = System.currentTimeMillis() + 12_000
                while (System.currentTimeMillis() < deadline) {
                    if (r.ready()) {
                        val line = r.readLine()
                        if (line != null && line.trim() == probe) {
                            alive = true
                            break
                        }
                    } else {
                        if (!p.isAlive) break
                        Thread.sleep(8)
                    }
                }
                if (!alive) {
                    closeQuietly()
                    continue
                }
                val uid = send("id -u", 5000)?.trim()?.lineSequence()
                    ?.lastOrNull { it.isNotBlank() }?.trim()
                val dirCount = send("ls /data/data 2>/dev/null | wc -l", 8000)
                    ?.trim()?.toIntOrNull() ?: 0
                if (uid == "0" && dirCount >= 10) return true
                if (uid == "0") {
                    // 是 root，但命名空间受限；留着当备选
                    bestUid0 = true
                    closeQuietly()
                    continue
                }
                closeQuietly()
            } catch (t: Throwable) {
                lastError = t.message ?: t.javaClass.simpleName
                closeQuietly()
            }
        }
        if (bestUid0) {
            // 退一步：至少拿到 root 权限
            for (cmd in candidates) {
                if (cmd[0] != "su") continue
                try {
                    val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
                    proc = p
                    writer = BufferedWriter(OutputStreamWriter(p.outputStream, Charsets.UTF_8))
                    reader = BufferedReader(InputStreamReader(p.inputStream, Charsets.UTF_8))
                    return true
                } catch (_: Throwable) {
                    closeQuietly()
                }
            }
        }
        return false
    }

    private fun send(command: String, timeoutMs: Long): String? {
        val id = "__RD_END_" + (++seq) + "__"
        val w = writer ?: return null
        val r = reader ?: return null
        w.write(command + "\n")
        w.write("echo $id\n")
        w.flush()
        val sb = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (r.ready()) {
                val line = r.readLine() ?: break
                if (line.trim() == id) return sb.toString()
                sb.append(line).append('\n')
            } else {
                if (proc?.isAlive != true) break
                Thread.sleep(6)
            }
        }
        lastError = "命令执行超时或被拒绝"
        closeQuietly()
        return null
    }

    /** 执行命令，返回合并后的 stdout；失败或超时返回 null */
    fun run(command: String, timeoutMs: Long = 25_000): String? {
        lock.lock()
        try {
            if (!alive() && !start()) return null
            return send(command, timeoutMs)
        } finally {
            lock.unlock()
        }
    }

    private val lsRegex = Regex(
        "^([bcdlps-])([rwxsStTlL.-]{9})[.+@]?\\s+(\\d+)\\s+(\\S+)\\s+(\\S+)\\s+(\\d+)\\s+" +
            "((?:\\d{4}-\\d{2}-\\d{2})|(?:[A-Za-z]{3}\\s+\\d{1,2}))\\s+(\\d{2}:\\d{2})\\s+(.*)$"
    )

    fun listDir(path: String): List<Entry> {
        // 末尾补斜杠，避免 ls 把软链接参数本身当成结果返回
        val target = if (path.endsWith("/")) path else "$path/"
        val out = run("ls -la --color=never " + quote(target)) ?: return emptyList()
        val list = ArrayList<Entry>()
        for (line in out.lineSequence()) {
            val m = lsRegex.find(line) ?: continue
            val type = m.groupValues[1]
            val perm = m.groupValues[2]
            val size = m.groupValues[6].toLongOrNull() ?: 0L
            val date = m.groupValues[7] + " " + m.groupValues[8]
            var name = m.groupValues[9]
            var isLink = false
            if (type == "l" && name.contains(" -> ")) {
                isLink = true
                name = name.substringBefore(" -> ")
            }
            if (name == "." || name == ".." || name.isBlank()) continue
            val full = if (path.endsWith("/")) path + name else "$path/$name"
            list.add(
                Entry(
                    name = name,
                    path = full,
                    isDir = type == "d",
                    isLink = isLink,
                    size = size,
                    date = date,
                    perm = type + perm,
                )
            )
        }
        return list.sortedWith(compareByDescending<Entry> { it.isDir }.thenBy { it.name.lowercase() })
    }

    fun stat(path: String): Stat? {
        val out = run("stat -c '%s;%Y;%a' " + quote(path))?.trim() ?: return null
        val parts = out.split(";")
        if (parts.size < 3) return null
        return Stat(
            size = parts[0].toLongOrNull() ?: 0L,
            mtime = (parts[1].toLongOrNull() ?: 0L) * 1000L,
            mode = parts[2],
        )
    }

    fun exists(path: String): Boolean =
        run("test -e " + quote(path) + " && echo YES")?.contains("YES") == true

    /** 目标是否为普通文件（用于「打开指定路径」直接跳转打开数据库） */
    fun isRegularFile(path: String): Boolean =
        run("test -f " + quote(path) + " && echo RD_FILE")?.contains("RD_FILE") == true

    fun mkdir(path: String): Boolean =
        run("mkdir -p " + quote(path) + " && echo OK")?.contains("OK") == true

    /** 把受保护文件（含 -wal/-shm）复制到应用可读位置 */
    fun copyToLocal(src: String, dest: File): Boolean {
        val uid = AndroidProcess.myUid()
        dest.parentFile?.mkdirs()
        val cmd = buildString {
            append("cp -f ").append(quote(src)).append(' ').append(quote(dest.absolutePath))
            append(" && chmod 660 ").append(quote(dest.absolutePath))
            append(" && chown ").append(uid).append(':').append(uid).append(' ').append(quote(dest.absolutePath))
            append(" && echo OK")
        }
        val out = run(cmd) ?: return false
        return out.contains("OK") && dest.exists()
    }

    /** 将本地修改后的数据库写回原路径（先备份原文件） */
    fun writeBack(local: File, dest: String, keepBackup: Boolean = true): Boolean {
        val stat = run("stat -c '%a;%u;%g' " + quote(dest))?.trim()
        val mode = stat?.split(";")?.getOrNull(0) ?: "660"
        val owner = stat?.split(";")?.getOrNull(1) ?: "0"
        val group = stat?.split(";")?.getOrNull(2) ?: "0"
        val bak = "$dest.dbstudio.bak"
        val cmd = buildString {
            if (keepBackup) {
                append("cp -f ").append(quote(dest)).append(' ').append(quote(bak)).append(" ; ")
            }
            append("cp -f ").append(quote(local.absolutePath)).append(' ').append(quote(dest))
            append(" && chmod ").append(mode).append(' ').append(quote(dest))
            append(" && chown ").append(owner).append(':').append(group).append(' ').append(quote(dest))
            // 清理旧的 WAL/SHM，避免新写入被旧日志覆盖
            append(" ; rm -f ").append(quote("$dest-wal")).append(' ').append(quote("$dest-shm"))
            append(" && echo OK")
        }
        return run(cmd)?.contains("OK") == true
    }

    fun delete(path: String): Boolean =
        run("rm -f " + quote(path) + " && echo OK")?.contains("OK") == true

    fun chmodLocal(file: File) {
        try {
            file.setReadable(true, true)
            file.setWritable(true, true)
        } catch (_: Throwable) {
        }
    }
}
