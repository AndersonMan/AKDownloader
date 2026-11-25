package com.andersonman.akdownloader.data.repo

import com.andersonman.akdownloader.data.db.DownloadDao
import com.andersonman.akdownloader.data.db.DownloadEntity
import com.andersonman.akdownloader.data.model.MediaItem
import com.andersonman.akdownloader.util.extensionOf
import com.andersonman.akdownloader.util.fileNameFromUrl
import com.andersonman.akdownloader.util.isMediaUrl
import com.andersonman.akdownloader.util.mimeFromExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class DownloadRepository(private val dao: DownloadDao) {

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val sharedClient: OkHttpClient get() = httpClient

    fun observeAll(): kotlinx.coroutines.flow.Flow<List<DownloadEntity>> = dao.observeAll()

    suspend fun getById(id: Long): DownloadEntity? = dao.getById(id)

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun clearCompleted() = dao.clearCompleted()

    suspend fun enqueue(url: String, suggestedName: String? = null, mimeType: String? = null): Long {
        val displayName = suggestedName?.takeIf { it.isNotBlank() }
            ?: fileNameFromUrl(url)
            ?: "akdownloader_${System.currentTimeMillis()}"
        val mime = mimeType?.takeIf { it.isNotBlank() && it != "application/octet-stream" }
            ?: extensionOf(url)?.let { mimeFromExtension(it) }
            ?: "application/octet-stream"
        return dao.insert(DownloadEntity(url = url, displayName = displayName, mimeType = mime))
    }

    /**
     * Analyse une URL : si c'est un fichier multimédia direct, renvoie-le ;
     * si c'est une page web, extrait tous les médias exposés publiquement.
     */
    suspend fun analyze(url: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
            val contentType = response.header("Content-Type") ?: ""
            if (!contentType.contains("text/html", ignoreCase = true)) {
                val name = fileNameFromUrl(url) ?: "fichier_${System.currentTimeMillis()}"
                return@withContext listOf(
                    MediaItem(url = url, displayName = name, mimeType = contentType.substringBefore(';'), source = "direct"),
                )
            }
            val html = readLimited(response.body?.byteStream(), MAX_HTML_BYTES) ?: ""
            extractMediaUrls(html, url).map { mediaUrl ->
                MediaItem(
                    url = mediaUrl,
                    displayName = fileNameFromUrl(mediaUrl) ?: mediaUrl.substringAfterLast('/').take(100),
                    mimeType = extensionOf(mediaUrl)?.let { mimeFromExtension(it) } ?: "application/octet-stream",
                    source = "page",
                )
            }
        }
    }

    private fun extractMediaUrls(html: String, baseUrl: String): List<String> {
        val results = LinkedHashSet<String>()
        val attributeRegex = Regex("""(?:src|href|source|file|url|content)\s*[:=]\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val bareRegex = Regex("""https?://[^\s"'<>\\]+""", RegexOption.IGNORE_CASE)

        for (match in attributeRegex.findAll(html) + bareRegex.findAll(html)) {
            val candidate = (match.groupValues.getOrNull(1) ?: match.value)
            if (!isMediaUrl(candidate)) continue
            val absolute = runCatching { java.net.URI(candidate).resolve(java.net.URI(baseUrl)).toString() }
                .getOrDefault(candidate)
            if (absolute.startsWith("http")) results.add(absolute)
            if (results.size >= MAX_RESULTS) break
        }
        return results.toList()
    }

    private fun readLimited(stream: java.io.InputStream?, maxBytes: Int): String? {
        if (stream == null) return null
        stream.use { input ->
            val reader = input.reader(Charsets.UTF_8)
            val output = StringBuilder()
            val buffer = CharArray(8192)
            var total = 0
            while (total < maxBytes) {
                val read = reader.read(buffer)
                if (read == -1) break
                output.append(buffer, 0, read)
                total += read
            }
            return output.toString()
        }
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Safari/537.36"
        private const val MAX_HTML_BYTES = 5 * 1024 * 1024
        private const val MAX_RESULTS = 60
    }
}
