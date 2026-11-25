package com.andersonman.akdownloader.data.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.atomic.AtomicLong

/**
 * Téléchargeur multi-connexions : découpe le fichier en segments
 * (requêtes HTTP Range) téléchargés en parallèle, avec reprise
 * automatique de chaque segment en cas d'échec réseau.
 */
class SegmentedDownloader(
    private val client: OkHttpClient,
    private val segmentCount: Int = DEFAULT_SEGMENTS,
) {

    data class Probe(
        val totalBytes: Long,
        val acceptsRanges: Boolean,
        val contentType: String?,
        val suggestedName: String?,
    )

    data class DownloadResult(
        val contentType: String?,
        val suggestedName: String?,
        val totalBytes: Long,
    )

    suspend fun probe(url: String): Probe = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-0")
            .build()
        client.newCall(request).execute().use { response ->
            val contentRange = response.header("Content-Range")
            val total = contentRange?.substringAfterLast('/')?.toLongOrNull()
                ?: response.header("Content-Length")?.toLongOrNull()
            Probe(
                totalBytes = total ?: -1L,
                acceptsRanges = response.code == 206,
                contentType = response.header("Content-Type"),
                suggestedName = fileNameFromDisposition(response.header("Content-Disposition")),
            )
        }
    }

    suspend fun download(
        url: String,
        target: RandomAccessFile,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit,
    ): DownloadResult = withContext(Dispatchers.IO) {
        val info = probe(url)
        val total = info.totalBytes
        val channel = target.channel
        if (total > 0) {
            target.setLength(total)
        }
        val counter = AtomicLong()
        coroutineScope {
            if (info.acceptsRanges && total > MIN_SEGMENT_SIZE && segmentCount > 1) {
                val base = total / segmentCount
                val remainder = total % segmentCount
                var start = 0L
                (0 until segmentCount).map { index ->
                    val length = base + if (index < remainder) 1 else 0
                    val segmentStart = start
                    val segmentEnd = start + length - 1
                    start += length
                    async {
                        downloadSegment(url, segmentStart, segmentEnd, channel, counter, total, onProgress)
                    }
                }.awaitAll()
            } else {
                downloadSegment(url, 0L, if (total > 0) total - 1 else -1L, channel, counter, total, onProgress)
            }
        }
        DownloadResult(
            contentType = info.contentType,
            suggestedName = info.suggestedName,
            totalBytes = if (total > 0) total else counter.get(),
        )
    }

    private suspend fun downloadSegment(
        url: String,
        start: Long,
        endInclusive: Long,
        channel: FileChannel,
        counter: AtomicLong,
        total: Long,
        onProgress: (Long, Long) -> Unit,
    ) {
        val segmentLength = if (endInclusive >= 0) endInclusive - start + 1 else -1L
        var offset = 0L
        var lastError: Exception? = null
        repeat(MAX_ATTEMPTS) {
            try {
                currentCoroutineContext().ensureActive()
                val builder = Request.Builder().url(url)
                if (endInclusive >= 0) {
                    builder.header("Range", "bytes=${start + offset}-$endInclusive")
                }
                client.newCall(builder.build()).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val body = response.body ?: throw IOException("Réponse vide")
                    if (endInclusive >= 0 && response.code != 206) {
                        throw IOException("Le serveur ne supporte pas les plages (HTTP ${response.code})")
                    }
                    val stream = body.byteStream()
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = stream.read(buffer)
                        if (read == -1) break
                        channel.write(ByteBuffer.wrap(buffer, 0, read), start + offset)
                        offset += read
                        onProgress(counter.addAndGet(read.toLong()), total)
                    }
                }
                if (segmentLength >= 0 && offset < segmentLength) {
                    throw IOException("Segment incomplet : $offset / $segmentLength")
                }
                return
            } catch (e: IOException) {
                counter.addAndGet(-offset)
                offset = 0L
                lastError = e
            }
        }
        throw lastError ?: IOException("Échec du segment")
    }

    private fun fileNameFromDisposition(disposition: String?): String? {
        if (disposition.isNullOrBlank()) return null
        val match = FILENAME_REGEX.find(disposition) ?: return null
        return match.groupValues[1].take(100)
    }

    companion object {
        const val DEFAULT_SEGMENTS = 4
        private const val MIN_SEGMENT_SIZE = 1024 * 1024L
        private const val MAX_ATTEMPTS = 3
        private const val BUFFER_SIZE = 64 * 1024
        private val FILENAME_REGEX = Regex("""filename\s*=\s*"?([^";]+)"?""", RegexOption.IGNORE_CASE)
    }
}
