package com.andersonman.akdownloader.ui.library

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun LibraryScreen(
    onPlay: (Uri) -> Unit,
    viewModel: LibraryViewModel = viewModel(),
) {
    val audio by viewModel.audio.collectAsState()
    val video by viewModel.video.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Bibliothèque", style = MaterialTheme.typography.headlineSmall)

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            item {
                Text("Vidéos (${video.size})", style = MaterialTheme.typography.titleMedium)
            }
            items(video, key = { "v_" + it.uri }) { entry ->
                LibraryRow(entry) { onPlay(Uri.parse(entry.uri)) }
            }
            item {
                Text("Audios (${audio.size})", style = MaterialTheme.typography.titleMedium)
            }
            items(audio, key = { "a_" + it.uri }) { entry ->
                LibraryRow(entry) { onPlay(Uri.parse(entry.uri)) }
            }
        }
    }
}

@Composable
private fun LibraryRow(entry: LibraryEntry, onPlay: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                if (entry.details.isNotBlank()) {
                    Text(entry.details, style = MaterialTheme.typography.bodySmall)
                }
            }
            IconButton(onClick = onPlay) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Lire")
            }
        }
    }
}
