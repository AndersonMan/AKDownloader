package com.andersonman.akdownloader.ui.downloads

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andersonman.akdownloader.data.db.DownloadEntity
import com.andersonman.akdownloader.util.formatBytes

@Composable
fun DownloadsScreen(
    onPlay: (Uri) -> Unit,
    viewModel: DownloadsViewModel = viewModel(),
) {
    val downloads by viewModel.downloads.collectAsState()
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Téléchargements",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { viewModel.clearCompleted() }) { Text("Nettoyer") }
        }

        if (downloads.isEmpty()) {
            Text("Aucun téléchargement. Utilisez l'accueil ou le navigateur pour en lancer.", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(downloads, key = { it.id }) { download ->
                DownloadRow(
                    download = download,
                    onCancel = { viewModel.cancel(context, download.id) },
                    onRetry = { viewModel.retry(context, download.id) },
                    onDelete = { viewModel.delete(download.id) },
                    onPlay = { download.contentUri?.let { onPlay(Uri.parse(it)) } },
                )
            }
        }
    }
}

@Composable
private fun DownloadRow(
    download: DownloadEntity,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onPlay: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(download.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${download.mimeType} · ${formatBytes(download.downloadedBytes)} / ${formatBytes(download.totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                when (download.status) {
                    DownloadEntity.Status.RUNNING.name ->
                        IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "Annuler") }
                    DownloadEntity.Status.COMPLETED.name ->
                        IconButton(onClick = onPlay) { Icon(Icons.Filled.PlayArrow, contentDescription = "Lire") }
                    else -> {}
                }
            }

            if (download.status == DownloadEntity.Status.RUNNING.name && download.totalBytes > 0) {
                val progress = (download.downloadedBytes.toFloat() / download.totalBytes.toFloat())
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }

            when (download.status) {
                DownloadEntity.Status.FAILED.name -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Échec : ${download.error ?: "erreur inconnue"}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(onClick = onRetry) { Text("Réessayer") }
                }
                DownloadEntity.Status.CANCELLED.name ->
                    Text("Annulé", style = MaterialTheme.typography.bodySmall)
                else -> {}
            }

            if (download.status != DownloadEntity.Status.RUNNING.name) {
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Text("Supprimer")
                    }
                }
            }
        }
    }
}
