package com.andersonman.akdownloader.service

import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.andersonman.akdownloader.AKDownloaderApp
import com.andersonman.akdownloader.data.db.DownloadEntity
import com.andersonman.akdownloader.data.net.SegmentedDownloader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLongArray

class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<Long, Job>()
    private val semaphore = Semaphore(MAX_CONCURRENT)
    private val pauseRequests: MutableSet<Long> = ConcurrentHashMap.newKeySet()
    private val dao by lazy { (application as AKDownloaderApp).downloadDao }
    private val downloader by lazy { (application as AKDownloaderApp).downloader }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        when (intent?.action) {
            ACTION_PAUSE -> {
                val id = intent.getLongExtra(EXTRA_ID, -1L)
                val job = jobs[id]
                if (job != null) {
                    pauseRequests.add(id)
                    job.cancel()
                }
            }
            ACTION_CANCEL -> {
                val id = intent.getLongExtra(EXTRA_ID, -1L)
                pauseRequests.remove(id)
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
        val resolver = applicationContext.contentResolver

        val probe = try {
            downloader.probe(entity.url)
        } catch (e: Exception) {
            dao.updateError(id, DownloadEntity.Status.FAILED.name, e.message ?: "Serveur injoignable")
            return
        }

        val displayName = resolveDisplayName(entity.displayName, probe.suggestedName, probe.contentType)
        val mime = probe.contentType?.substringBefore(';')?.trim()?.takeIf { it.isNotBlank() }
            ?: entity.mimeType.ifBlank { "application/octet-stream" }

        val cacheFile = File(cacheDir, "download_" + id + ".part")
        val metaFile = File(cacheDir, "download_" + id + ".meta")

        // Reprise : lire les offsets sauvegardés lors de la dernière pause, si cohérents.
        val offsets = AtomicLongArray(SegmentedDownloader.DEFAULT_SEGMENTS)
        var resuming = false
        if (cacheFile.exists() && metaFile.exists()) {
            runCatching {
                DataInputStream(BufferedInputStream(metaFile.inputStream())).use { input ->
                    val savedTotal = input.readLong()
                    if (savedTotal == probe.totalBytes && probe.acceptsRanges && probe.totalBytes > 0) {
                        for (i in 0 until offsets.length()) offsets.set(i, input.readLong())
                        resuming = true
                    }
                }
            }
            if (!resuming) runCatching { metaFile.delete() }
        }

        var mediaUri: Uri? = null
        var paused = false
        try {
            var latestBytes = 0L
            var latestTotal = probe.totalBytes
            val raf = RandomAccessFile(cacheFile, "rw")
            val progressJob = scope.launch {
                while (true) {
                    delay(PROGRESS_INTERVAL_MS)
                    dao.updateProgress(id, latestBytes, latestTotal)
                    notifyProgress(displayName, id, latestBytes, latestTotal)
                }
            }
            try {
                downloader.download(entity.url, raf, { bytesRead: Long, totalBytes: Long ->
                    latestBytes = bytesRead
                    latestTotal = totalBytes
                }, offsets)
            } finally {
                progressJob.cancel()
                runCatching { raf.close() }
            }

            val (collection, relativePath) = destinationFor(mime)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values)
                ?: throw IllegalStateException("Impossible de créer le fichier final")
            mediaUri = uri
            val output = resolver.openOutputStream(uri)
            if (output != null) {
                output.use { stream ->
                    cacheFile.inputStream().use { source ->
                        source.copyTo(stream)
                    }
                }
            } else {
                throw IllegalStateException("Flux de stockage indisponible")
            }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)

            val total = if (latestTotal > 0) latestTotal else cacheFile.length()
            dao.markCompleted(id, uri.toString(), total, System.currentTimeMillis())
            notifyFinished(displayName, id, success = true)
        } catch (e: CancellationException) {
            if (pauseRequests.remove(id)) {
                paused = true
                // Sauvegarder la position de chaque segment pour reprendre plus tard.
                runCatching {
                    DataOutputStream(BufferedOutputStream(metaFile.outputStream())).use { out ->
                        out.writeLong(probe.totalBytes)
                        for (i in 0 until offsets.length()) out.writeLong(offsets.get(i))
                    }
                }
                dao.updateProgress(id, sumOffsets(offsets), probe.totalBytes)
                dao.updateError(id, DownloadEntity.Status.PAUSED.name, null)
            } else {
                mediaUri?.let { runCatching { resolver.delete(it, null, null) } }
                dao.updateError(id, DownloadEntity.Status.CANCELLED.name, "Annulé")
                throw e
            }
        } catch (e: Exception) {
            mediaUri?.let { runCatching { resolver.delete(it, null, null) } }
            dao.updateError(id, DownloadEntity.Status.FAILED.name, e.message ?: "Erreur inconnue")
            notifyFinished(displayName, id, success = false, error = e.message)
        } finally {
            if (!paused) {
                runCatching { cacheFile.delete() }
                runCatching { metaFile.delete() }
            }
        }
    }

    /** Destination selon le type : Musique, Films, ou Téléchargements. */
    private fun destinationFor(mime: String): Pair<Uri, String> {
        return when (mime.substringBefore('/').lowercase()) {
            "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to
                (Environment.DIRECTORY_MUSIC + "/AKDownloader")
            "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI to
                (Environment.DIRECTORY_MOVIES + "/AKDownloader")
            else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI to
                (Environment.DIRECTORY_DOWNLOADS + "/AKDownloader")
        }
    }

    private fun sumOffsets(offsets: AtomicLongArray): Long {
        var sum = 0L
        for (i in 0 until offsets.length()) sum += offsets.get(i)
        return sum
    }

    private fun notifyProgress(displayName: String, id: Long, bytesRead: Long, totalBytes: Long) {
        val percent = if (totalBytes > 0) ((bytesRead * 100) / totalBytes).toInt() else 0
        val notification = NotificationCompat.Builder(this, AKDownloaderApp.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(displayName)
            .setContentText(if (totalBytes > 0) percent.toString() + " %" else "Téléchargement en cours…")
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
            .setContentText(if (success) "Terminé : " + displayName else "Échec : " + displayName + " (" + error + ")")
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
        return base + "." + ext
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
                .setContentText(count.toString() + " téléchargement(s) en cours")
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
        const val ACTION_PAUSE = "com.andersonman.akdownloader.action.PAUSE"
        const val ACTION_CANCEL = "com.andersonman.akdownloader.action.CANCEL"
        const val EXTRA_IDS = "com.andersonman.akdownloader.extra.IDS"
        const val EXTRA_ID = "com.andersonman.akdownloader.extra.ID"

        fun start(context: Context, ids: LongArray) {
            val intent = Intent(context, DownloadService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_IDS, ids)
            ContextCompat.startForegroundService(context, intent)
        }

        fun pause(context: Context, id: Long) {
            val intent = Intent(context, DownloadService::class.java)
                .setAction(ACTION_PAUSE)
                .putExtra(EXTRA_ID, id)
            context.startService(intent)
        }

        fun cancel(context: Context, id: Long) {
            val intent = Intent(context, DownloadService::class.java)
                .setAction(ACTION_CANCEL)
                .putExtra(EXTRA_ID, id)
            context.startService(intent)
        }
    }
}
