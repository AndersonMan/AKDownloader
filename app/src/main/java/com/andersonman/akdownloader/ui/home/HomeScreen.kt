package com.andersonman.akdownloader.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andersonman.akdownloader.data.model.MediaItem

@Composable
fun HomeScreen(
    onDownloadQueued: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val url by viewModel.url
    val analyzing by viewModel.analyzing
    val message by viewModel.message
    val results = viewModel.results

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("AKDownloader", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Collez le lien d'un fichier direct (.mp3, .mp4…) ou d'une page web : les médias exposés publiquement seront détectés automatiquement.",
            style = MaterialTheme.typography.bodySmall,
        )

        OutlinedTextField(
            value = url,
            onValueChange = { viewModel.url.value = it },
            label = { Text("Coller une URL (page ou fichier)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.downloadDirect() }, enabled = url.isNotBlank()) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Spacer(Modifier.size(4.dp))
                Text("Télécharger")
            }
            OutlinedButton(onClick = { viewModel.analyze() }, enabled = url.isNotBlank() && !analyzing) {
                Icon(Icons.Filled.Search, contentDescription = null)
                Spacer(Modifier.size(4.dp))
                Text("Analyser la page")
            }
        }

        if (analyzing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (results.isNotEmpty()) {
            Text("Médias détectés (${results.size})", style = MaterialTheme.typography.titleMedium)
            LazyColumn(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                items(results, key = { it.url }) { item ->
                    MediaResultRow(item) {
                        viewModel.startDownload(item)
                        onDownloadQueued()
                    }
                }
            }
        }
    }
}

(item: MediaItem, onDownload: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.mimeType, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDownload) {
                Icon(Icons.Filled.Download, contentDescription = "Télécharger")
            }
        }
    }
}
