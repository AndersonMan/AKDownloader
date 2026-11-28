package com.andersonman.akdownloader.ui.crash

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashReporter {

    private const val RELATIVE_DIR = "Download/AKDownloader"
    private const val PREFIX = "ak_crash_"

    fun install(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { saveCrash(context, throwable) }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun saveCrash(context: Context, throwable: Throwable) {
        val stack = android.util.Log.getStackTraceString(throwable).take(12000)
        val text = buildString {
            append("AKDownloader — rapport de plantage\n")
            append("Date : ")
            append(SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.FRANCE).format(Date()))
            append("\nAppareil : ")
            append(android.os.Build.MANUFACTURER).append(' ').append(android.os.Build.MODEL)
            append(" (Android ").append(android.os.Build.VERSION.RELEASE).append(")\n\n")
            append(stack)
        }
        saveText(context, text)
    }

    fun saveText(context: Context, text: String) {
        val internal = File(context.applicationContext.filesDir, "last_crash.txt")
        runCatching {
            internal.parentFile?.mkdirs()
            internal.writeText(text)
        }
        val resolver = context.applicationContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, PREFIX + System.currentTimeMillis() + ".txt")
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_DIR)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
        runCatching {
            resolver.openOutputStream(uri)?.use { output ->
                output.write(text.toByteArray())
            }
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
        }
    }

    fun readLastCrash(context: Context): Pair<String, String>? {
        val internal = File(context.applicationContext.filesDir, "last_crash.txt")
        if (internal.exists()) {
            val text = runCatching { internal.readText() }.getOrNull()
            if (!text.isNullOrBlank()) return "last_crash.txt" to text.take(4000)
        }
        return runCatching {
            val resolver = context.applicationContext.contentResolver
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val selection = MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?"
            val args = arrayOf("%AKDownloader%")
            resolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
                selection,
                args,
                MediaStore.MediaColumns.DATE_ADDED + " DESC",
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(1) ?: continue
                    if (!name.startsWith(PREFIX)) continue
                    val id = cursor.getLong(0)
                    val uri = android.content.ContentUris.withAppendedId(collection, id)
                    val text = resolver.openInputStream(uri)?.use { stream ->
                        stream.readBytes().decodeToString()
                    }
                    if (!text.isNullOrBlank()) return name to text.take(3000)
                }
            }
            null
        }.getOrNull()
    }

    fun deleteAllCrashReports(context: Context) {
        runCatching {
            val resolver = context.applicationContext.contentResolver
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val selection = MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ? AND " +
                MediaStore.MediaColumns.DISPLAY_NAME + " LIKE ?"
            val args = arrayOf("%AKDownloader%", PREFIX + "%")
            resolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID),
                selection,
                args,
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val uri = android.content.ContentUris.withAppendedId(collection, cursor.getLong(0))
                    runCatching { resolver.delete(uri, null, null) }
                }
            }
            File(context.cacheDir, "crashes").deleteRecursively()
            File(context.cacheDir.parentFile, "files/last_crash.txt").delete()
        }
    }
}
