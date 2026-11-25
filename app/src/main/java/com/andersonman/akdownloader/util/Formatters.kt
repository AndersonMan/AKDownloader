package com.andersonman.akdownloader.util

import java.net.URLDecoder
import java.util.Locale

private val MEDIA_EXTENSIONS = setOf(
    "mp3", "m4a", "wav", "ogg", "oga", "flac", "aac", "opus",
    "mp4", "m4v", "mkv", "webm", "mov", "avi", "m3u8", "mpd",
)

fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "taille inconnue"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1 -> String.format(Locale.FRANCE, "%.2f Go", gb)
        mb >= 1 -> String.format(Locale.FRANCE, "%.1f Mo", mb)
        kb >= 1 -> String.format(Locale.FRANCE, "%.0f Ko", kb)
        else -> "$bytes o"
    }
}

fun formatDuration(millis: Long): String {
    if (millis <= 0) return ""
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

fun extensionOf(url: String): String? =
    url.substringBefore('#').substringBefore('?')
        .substringAfterLast('.', "")
        .lowercase(Locale.ROOT)
        .takeIf { it in MEDIA_EXTENSIONS }

fun isMediaUrl(url: String): Boolean = extensionOf(url) != null

fun mimeFromExtension(ext: String): String = when (ext) {
    "mp3" -> "audio/mpeg"
    "m4a" -> "audio/mp4"
    "wav" -> "audio/wav"
    "ogg", "oga" -> "audio/ogg"
    "flac" -> "audio/flac"
    "aac" -> "audio/aac"
    "opus" -> "audio/opus"
    "mp4", "m4v" -> "video/mp4"
    "mkv" -> "video/x-matroska"
    "webm" -> "video/webm"
    "mov" -> "video/quicktime"
    "avi" -> "video/x-msvideo"
    "m3u8" -> "application/vnd.apple.mpegurl"
    "mpd" -> "application/dash+xml"
    else -> "application/octet-stream"
}

fun mimeFromUrl(url: String): String = extensionOf(url)?.let { mimeFromExtension(it) } ?: "application/octet-stream"

fun fileNameFromUrl(url: String): String? {
    val cleaned = url.substringBefore('#').substringBefore('?')
    val name = cleaned.substringAfterLast('/')
    if (name.isBlank()) return null
    val decoded = runCatching { URLDecoder.decode(name, "UTF-8") }.getOrDefault(name)
    return decoded.take(100)
}
