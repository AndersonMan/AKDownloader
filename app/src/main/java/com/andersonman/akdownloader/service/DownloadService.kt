package com.andersonman.akdownloader.service

import android.app.Notification
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.andersonman.akdownloader.AKDownloaderApp
import com.andersonman.akdownloader.data.db.DownloadEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap

class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<Long, Job>()
    private val semaphore = Semaphore(MAX_CONCURRENT)
    private val dao by lazy { (application as AKDownloaderApp).downloadDao }
    private val downloader by lazy { (application as AKDownloaderApp).downloader }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        when (intent?.action) {
            ACTION_CANCEL -> {
                val id = intent.getLongExtra(EXTRA_ID, -1L)
                jobs.remove(id)?.let { job ->
                    scope.launch {
                        job.cancelAndJoin()
                        dao.updateError(id, DownloadEntity.Status.CANCELLED.name, "Annulé")
                        refreshNotification()
                        if (jobs.isEmpty()) stopSelf()
                    }
                }
            }
            else -> intent?.getLongArrayExtra(EXTRA_IDS)?.forEach { id -> startDownload(id) }
        }
        return START_NOT_STICKY
    }

    private fun startDownload(id: Long) {
        if (jobs.containsKey(id)) return
        jobs[id] = scope.launch {
            try {
                semaphore.withPermit { runDownload(id) }
            } finally {
                jobs.remove(id)
                refreshNotification()
                if (jobs.isEmpty()) stopSelf()
            }
        }
    }

    private suspend fun runDownload(id: Long) {
        val entity = dao.getById(id) ?: return
        if (entity.status == DownloadEntity.Status.COMPLETED.name) return

        dao.updateStatus(id, DownloadEntity.Status.RUNNING.name)
        val context = applicationContext

        val probe = try {
            downloader.probe(entity.url)
        } catch (e: Exception) {
            dao.updateError(id, DownloadEntity.Status.FAILED.name, e.message ?: "Serveur injoignable")
            return
        }

        val displayName = resolveDisplayName(entity.displayName, probe.suggestedName, probe.contentType)
        val mime = probe.contentType?.substringBefore(';')?.trim()?.takeIf { it.isNotBlank() }
            ?: entity.mimeType.ifBlank { "application/octet-stream" }

        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/AKDownloader")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            dao.updateError(id, DownloadEntity.Status.FAILED.name, "Impossible de créer le fichier")
            return
        }

        var lastUpdate = 0L
        try {
            resolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                RandomAccessFile(pfd.fileDescriptor, "rw").use { raf ->
                    downloader.download(entity.url, raf) { bytesRead, totalBytes ->
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > PROGRESS_INTERVAL_MS) {
                            lastUpdate = now
                            updateProgress(id, bytesRead, totalBytes, displayName)
                        }
                    }
                }
            } ?: throw IllegalStateException("Flux de stockage indisponible")

            val total = if (probe.totalBytes > 0) probe.totalBytes else -1L
            dao.markCompleted(id, uri.toString(), total, System.currentTimeMillis())
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            notifyFinished(displayName, id, success = true)
        } catch (e: CancellationException) {
            runCatching { resolver.delete(uri, null, null) }
            dao.updateError(id, DownloadEntity.Status.CANCELLED.name, "Annulé")
            throw e
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            dao.updateError(id, DownloadEntity.Status.FAILED.name, e.message ?: "Erreur inconnue")
            notifyFinished(displayName, id, success = false, error = e.message)
        }
    }

    private suspend fun updateProgress(id: Long, bytesRead: Long, totalBytes: Long, displayName: String) {
        dao.updateProgress(id, bytesRead, totalBytes)
        val percent = if (totalBytes > 0) ((bytesRead * 100) / totalBytes).toInt() else 0
        val notification = NotificationCompat.Builder(this, AKDownloaderApp.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(displayName)
            .setContentText(if (totalBytes > 0) "$percent %" else "Téléchargement en cours…")
            .setProgress(100, percent, totalBytes <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        NotificationManagerCompat.from(this).notify((BASE_NOTIFICATION_ID + id).toInt(), notification)
    }

    private fun notifyFinished(displayName: String, id: Long, success: Boolean, error: String? = null) {
        val notification = NotificationCompat.Builder(this, AKDownloaderApp.CHANNEL_ID)
            .setSmallIcon(if (success) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentTitle("AKDownloader")
            .setContentText(if (success) "Terminé : $displayName" else "Échec : $displayName ($error)")
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        NotificationManagerCompat.from(this).notify((BASE_NOTIFICATION_ID + id).toInt(), notification)
    }

    private fun resolveDisplayName(current: String, suggested: String?, contentType: String?): String {
        val base = (suggested ?: current).replace(Regex("""[\\/:*?"<>|]"""), "_").take(100)
        if (base.contains('.')) return base
        val mime = contentType?.substringBefore(';')?.trim()
        val ext = mime?.let {
            android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(it)
        } ?: "bin"
        return "$base.$ext"
    }

    private fun goForeground() {
        val notification = NotificationCompat.Builder(this, AKDownloaderApp.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("AKDownloader")
            .setContentText("File de téléchargement active")
            .setOnlyAlertOnce(true)
            .build()
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun refreshNotification() {
        val count = jobs.size
        if (count > 0) {
            val notification = NotificationCompat.Builder(this, AKDownloaderApp.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("AKDownloader")
                .setContentText("$count téléchargement(s) en cours")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val MAX_CONCURRENT = 3
        private const val PROGRESS_INTERVAL_MS = 300L
        private const val NOTIFICATION_ID = 42
        private const val BASE_NOTIFICATION_ID = 1000

        const val ACTION_START = "com.andersonman.akdownloader.action.START"
        const val ACTION_CANCEL = "com.andersonman.akdownloader.action.CANCEL"
        const val EXTRA_IDS = "com.andersonman.akdownloader.extra.IDS"
        const val EXTRA_ID = "com.andersonman.akdownloader.extra.ID"

        fun start(context: Context, ids: LongArray) {
            val intent = Intent(context, DownloadService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_IDS, ids)
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context, id: Long) {
            val intent = Intent(context, DownloadService::class.java)
                .setAction(ACTION_CANCEL)
                .putExtra(EXTRA_ID, id)
            context.startService(intent)
        }
    }
}
