package com.andersonman.akdownloader.ui.browser

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andersonman.akdownloader.AKDownloaderApp
import com.andersonman.akdownloader.data.model.MediaItem
import com.andersonman.akdownloader.service.DownloadService
import com.andersonman.akdownloader.util.extensionOf
import com.andersonman.akdownloader.util.fileNameFromUrl
import com.andersonman.akdownloader.util.isMediaUrl
import com.andersonman.akdownloader.util.mimeFromExtension
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class BrowserViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as AKDownloaderApp).downloadRepository
    private val seen = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    private val _detected = MutableStateFlow<List<MediaItem>>(emptyList())
    val detected: StateFlow<List<MediaItem>> = _detected.asStateFlow()

    val currentAddress = mutableStateOf("")

    fun onResource(url: String) {
        if (!isMediaUrl(url)) return
        add(url)
    }

    fun onAddressChanged(url: String) {
        if (url.startsWith("http")) currentAddress.value = url
    }

    fun onScriptResult(raw: String?) {
        if (raw.isNullOrEmpty() || raw == "null") return
        val decoded = raw.trim()
            .removeSurrounding("\"")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
        val array = runCatching { JSONArray(decoded) }.getOrNull() ?: return
        for (i in 0 until array.length()) {
            val candidate = array.optString(i)
            if (isMediaUrl(candidate)) add(candidate)
        }
    }

    fun clearDetected() {
        seen.clear()
        _detected.value = emptyList()
    }

    fun download(item: MediaItem) {
        viewModelScope.launch {
            val id = repository.enqueue(item.url, item.displayName, item.mimeType)
            DownloadService.start(getApplication(), longArrayOf(id))
        }
    }

    private fun add(url: String) {
        if (!seen.add(url)) return
        if (seen.size > MAX_SEEN) seen.clear()
        val item = MediaItem(
            url = url,
            displayName = fileNameFromUrl(url) ?: url.substringAfterLast('/').take(100),
            mimeType = extensionOf(url)?.let { mimeFromExtension(it) } ?: "application/octet-stream",
            source = "navigation",
        )
        _detected.value = listOf(item) + _detected.value.take(MAX_LIST - 1)
    }

    companion object {
        private const val MAX_SEEN = 300
        private const val MAX_LIST = 80
    }
}
