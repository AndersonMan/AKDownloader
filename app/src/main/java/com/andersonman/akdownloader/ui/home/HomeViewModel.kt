package com.andersonman.akdownloader.ui.home

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andersonman.akdownloader.AKDownloaderApp
import com.andersonman.akdownloader.data.model.MediaItem
import com.andersonman.akdownloader.service.DownloadService
import kotlinx.coroutines.launch

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as AKDownloaderApp).downloadRepository

    val url = mutableStateOf("")
    val analyzing = mutableStateOf(false)
    val results = mutableStateListOf<MediaItem>()
    val message = mutableStateOf<String?>(null)

    fun downloadDirect() {
        val target = url.value.trim()
        if (!target.startsWith("http")) {
            message.value = "Entrez une URL valide (http/https)."
            return
        }
        viewModelScope.launch {
            runCatching {
                val id = repository.enqueue(target)
                DownloadService.start(getApplication(), longArrayOf(id))
            }.onFailure { message.value = "Erreur : ${it.message}" }
        }
    }

    fun startDownload(item: MediaItem) {
        viewModelScope.launch {
            runCatching {
                val id = repository.enqueue(item.url, item.displayName, item.mimeType)
                DownloadService.start(getApplication(), longArrayOf(id))
            }.onFailure { message.value = "Erreur : ${it.message}" }
        }
    }

    fun analyze() {
        val target = url.value.trim()
        if (!target.startsWith("http")) {
            message.value = "Entrez une URL valide (http/https)."
            return
        }
        analyzing.value = true
        message.value = null
        viewModelScope.launch {
            runCatching { repository.analyze(target) }
                .onSuccess { items ->
                    results.clear()
                    results.addAll(items)
                    message.value = if (items.isEmpty()) "Aucun média détecté sur cette page." else null
                }
                .onFailure { message.value = "Erreur : ${it.message}" }
            analyzing.value = false
        }
    }
}
