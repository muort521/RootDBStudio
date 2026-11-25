package com.rootdb.studio.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Html
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Sd
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 文件类型分类，用于挑选图标与配色 */
enum class FileKind {
    DIR, DB, IMAGE, VIDEO, AUDIO, PLAYLIST, SUBTITLE,
    ARCHIVE, APK, BINARY, CODE, WEB, TEXT,
    PDF, DOC, SHEET, SLIDE, FONT, CERT, LOG, OTHER,
}

private val extMap: Map<String, FileKind> = buildMap {
    fun put(kind: FileKind, vararg exts: String) = exts.forEach { put(it, kind) }

    put(FileKind.DB, "db", "db3", "sqlite", "sqlite3", "sql", "db-wal", "db-shm", "db-journal", "sqlitedb")
    put(
        FileKind.IMAGE,
        "png", "jpg", "jpeg", "jpe", "webp", "gif", "bmp", "tif", "tiff", "heic", "heif",
        "avif", "ico", "svg", "svgz", "psd", "xcf", "dng", "raw", "cr2", "nef", "arw",
    )
    put(
        FileKind.VIDEO,
        "mp4", "m4v", "mkv", "webm", "avi", "mov", "wmv", "flv", "f4v", "3gp", "3gpp",
        "mpg", "mpeg", "ts", "m2ts", "rmvb", "rm", "vob", "ogv",
    )
    put(
        FileKind.AUDIO,
        "mp3", "flac", "wav", "ogg", "oga", "opus", "m4a", "aac", "amr", "awb", "ape",
        "wma", "mid", "midi", "aiff", "aif", "ac3", "dts", "mka", "3ga",
    )
    put(FileKind.PLAYLIST, "m3u", "m3u8", "pls", "cue", "wpl")
    put(FileKind.SUBTITLE, "srt", "ass", "ssa", "vtt", "lrc", "sub", "smi")
    put(
        FileKind.ARCHIVE,
        "zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "tbz", "xz", "txz", "lz", "lzma",
        "zst", "cab", "iso", "img", "bin", "obb", "img", "pak", "jar", "war", "aar", "lz4",
    )
    put(FileKind.APK, "apk", "apks", "xapk", "apkm", "aab", "dex", "oat", "vdex", "art", "odex")
    put(
        FileKind.BINARY,
        "so", "o", "a", "dylib", "dll", "elf", "exe", "ko", "lib", "class", "wasm", "axf", "hex",
    )
    put(
        FileKind.CODE,
        "kt", "kts", "java", "js", "mjs", "cjs", "ts", "tsx", "jsx", "py", "c", "cc", "cpp",
        "h", "hpp", "cs", "go", "rs", "rb", "php", "swift", "m", "mm", "scala", "lua", "pl",
        "r", "dart", "groovy", "gradle", "cmake", "mk", "make", "s", "asm", "proto",
    )
    put(FileKind.WEB, "html", "htm", "xhtml", "css", "scss", "sass", "less", "vue", "svelte")
    put(
        FileKind.TEXT,
        "txt", "text", "md", "markdown", "rst", "json", "json5", "xml", "yml", "yaml", "toml",
        "ini", "cfg", "conf", "config", "properties", "prop", "env", "plist", "csv", "tsv",
        "rtf", "text",
    )
    put(FileKind.PDF, "pdf", "xps", "epub", "mobi", "azw3", "djvu")
    put(
        FileKind.DOC,
        "doc", "docx", "odt", "ott", "wps", "pages", "dot", "dotx", "rtf", "tex",
    )
    put(FileKind.SHEET, "xls", "xlsx", "xlsm", "ods", "ots", "et", "numbers", "xlt", "xltx")
    put(FileKind.SLIDE, "ppt", "pptx", "pptm", "odp", "otp", "dps", "key", "pps", "ppsx")
    put(
        FileKind.FONT,
        "ttf", "ttc", "otf", "woff", "woff2", "eot", "pfb", "pfa", "fon", "fnt",
    )
    put(
        FileKind.CERT,
        "pem", "crt", "cer", "der", "p12", "pfx", "jks", "keystore", "bks", "csr", "p7b",
        "gpg", "asc", "sig", "key",
    )
    put(FileKind.LOG, "log", "logcat", "dump", "trace", "hprof", "dmp", "tombstone", "anr")
}

/** 依文件名推断类型（无扩展名、点开头的隐藏文件回落到 OTHER / DIR） */
fun fileKindOf(name: String, isDir: Boolean, isLink: Boolean): FileKind {
    if (isDir) return FileKind.DIR
    val dot = name.lastIndexOf('.')
    if (dot <= 0 || dot == name.length - 1) {
        val lower = name.lowercase()
        if (lower.endsWith("-wal") || lower.endsWith("-shm") || lower.endsWith("-journal")) return FileKind.DB
        return FileKind.OTHER
    }
    val ext = name.substring(dot + 1).lowercase()
    return extMap[ext] ?: FileKind.OTHER
}

fun iconOfKind(kind: FileKind): ImageVector = when (kind) {
    FileKind.DIR -> Icons.Filled.Folder
    FileKind.DB -> Icons.Filled.Storage
    FileKind.IMAGE -> Icons.Filled.Image
    FileKind.VIDEO -> Icons.Filled.Movie
    FileKind.AUDIO -> Icons.Filled.Audiotrack
    FileKind.PLAYLIST -> Icons.Filled.PlaylistPlay
    FileKind.SUBTITLE -> Icons.Filled.Subtitles
    FileKind.ARCHIVE -> Icons.Filled.FolderZip
    FileKind.APK -> Icons.Filled.Android
    FileKind.BINARY -> Icons.Filled.Memory
    FileKind.CODE -> Icons.Filled.Code
    FileKind.WEB -> Icons.Filled.Html
    FileKind.TEXT -> Icons.Filled.Article
    FileKind.PDF -> Icons.Filled.PictureAsPdf
    FileKind.DOC -> Icons.Filled.Description
    FileKind.SHEET -> Icons.Filled.TableChart
    FileKind.SLIDE -> Icons.Filled.Slideshow
    FileKind.FONT -> Icons.Filled.FontDownload
    FileKind.CERT -> Icons.Filled.Key
    FileKind.LOG -> Icons.Filled.BugReport
    FileKind.OTHER -> Icons.Filled.InsertDriveFile
}

/** 类型的中文名称，同时作为图标的无障碍描述 */
fun labelOfKind(kind: FileKind): String = when (kind) {
    FileKind.DIR -> "文件夹"
    FileKind.DB -> "数据库文件"
    FileKind.IMAGE -> "图片"
    FileKind.VIDEO -> "视频"
    FileKind.AUDIO -> "音频"
    FileKind.PLAYLIST -> "播放列表"
    FileKind.SUBTITLE -> "字幕"
    FileKind.ARCHIVE -> "压缩包"
    FileKind.APK -> "应用包"
    FileKind.BINARY -> "二进制文件"
    FileKind.CODE -> "代码文件"
    FileKind.WEB -> "网页文件"
    FileKind.TEXT -> "文本文件"
    FileKind.PDF -> "PDF 文档"
    FileKind.DOC -> "文档"
    FileKind.SHEET -> "表格"
    FileKind.SLIDE -> "演示文稿"
    FileKind.FONT -> "字体文件"
    FileKind.CERT -> "证书或密钥"
    FileKind.LOG -> "日志文件"
    FileKind.OTHER -> "文件"
}

/** 图标配色：目录/数据库用主色，文档与媒体用正文色，PDF/日志用错误色，保证明暗两种主题下都清晰 */
@Composable
fun colorOfKind(kind: FileKind): Color {
    val cs = MiuixTheme.colorScheme
    return when (kind) {
        FileKind.DIR, FileKind.DB, FileKind.CERT -> cs.primary
        FileKind.PDF, FileKind.LOG -> cs.error
        FileKind.OTHER -> cs.onBackgroundVariant
        else -> cs.onBackground
    }
}

/** 首页快捷入口用到的固定图标 */
object AppIcons {
    val folder = Icons.Filled.Folder
    val storage = Icons.Filled.Storage
    val sd = Icons.Filled.Sd
    val root = Icons.Filled.Android
    val public = Icons.Filled.Public
    val file = Icons.Filled.InsertDriveFile
    val gallery = Icons.Filled.PhotoLibrary
    val audioFile = Icons.Filled.AudioFile
    val videoFile = Icons.Filled.VideoFile
    val archive = Icons.Filled.Archive
    val extension = Icons.Filled.Extension
}
