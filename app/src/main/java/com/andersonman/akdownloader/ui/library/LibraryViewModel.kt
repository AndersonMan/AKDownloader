package com.andersonman.akdownloader.ui.library

import android.app.Application
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andersonman.akdownloader.util.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LibraryEntry(
    val uri: String,
    val title: String,
    val details: String,
)

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val _audio = MutableStateFlow<List<LibraryEntry>>(emptyList())
    val audio: StateFlow<List<LibraryEntry>> = _audio.asStateFlow()

    private val _video = MutableStateFlow<List<LibraryEntry>>(emptyList())
    val video: StateFlow<List<LibraryEntry>> = _video.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _audio.value = query(
                    collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection = arrayOf(
                        MediaStore.MediaColumns._ID,
                        MediaStore.MediaColumns.DISPLAY_NAME,
                        MediaStore.MediaColumns.DURATION,
                        MediaStore.MediaColumns.SIZE,
                    ),
                )
                _video.value = query(
                    collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    projection = arrayOf(
                        MediaStore.MediaColumns._ID,
                        MediaStore.MediaColumns.DISPLAY_NAME,
                        MediaStore.MediaColumns.DURATION,
                        MediaStore.MediaColumns.SIZE,
                    ),
                )
            }
        }
    }

    private fun query(collection: Uri, projection: Array<String>): List<LibraryEntry> {
        val resolver = getApplication<Application>().contentResolver
        val results = mutableListOf<LibraryEntry>()
        resolver.query(collection, projection, null, null, "${MediaStore.MediaColumns.DISPLAY_NAME} ASC")?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val duration = formatDuration(cursor.getLong(durationColumn))
                val size = android.text.format.Formatter.formatShortFileSize(
                    getApplication(),
                    cursor.getLong(sizeColumn),
                )
                results += LibraryEntry(
                    uri = ContentUris.withAppendedId(collection, id).toString(),
                    title = cursor.getString(nameColumn) ?: "Sans titre",
                    details = listOf(duration, size).filter { it.isNotBlank() }.joinToString(" · "),
                )
            }
        }
        return results
    }
}
