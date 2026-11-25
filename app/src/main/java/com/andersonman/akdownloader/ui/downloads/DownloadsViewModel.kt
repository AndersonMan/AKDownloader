package com.andersonman.akdownloader.ui.downloads

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andersonman.akdownloader.AKDownloaderApp
import com.andersonman.akdownloader.data.db.DownloadEntity
import com.andersonman.akdownloader.service.DownloadService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DownloadsViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as AKDownloaderApp).downloadRepository

    val downloads: StateFlow<List<DownloadEntity>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun cancel(context: Context, id: Long) = DownloadService.cancel(context, id)

    fun retry(context: Context, id: Long) = DownloadService.start(context, longArrayOf(id))

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun clearCompleted() {
        viewModelScope.launch { repository.clearCompleted() }
    }
}
