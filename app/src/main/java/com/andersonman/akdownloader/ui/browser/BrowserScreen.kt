package com.andersonman.akdownloader.ui.browser

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andersonman.akdownloader.data.model.MediaItem

private const val DETECT_SCRIPT = """
    (function() {
        var found = {};
        var nodes = document.querySelectorAll('video,audio,source,a');
        for (var i = 0; i < nodes.length; i++) {
            var el = nodes[i];
            var link = el.currentSrc || el.src || el.href || '';
            if (link && /\.(mp3|m4a|wav|ogg|oga|flac|aac|opus|mp4|m4v|mkv|webm|mov|avi|m3u8|mpd)(\?|#|$)/i.test(link)) {
                found[link] = true;
            }
        }
        var html = document.documentElement.innerHTML;
        var regex = /https?:\/\/[^\"'\\ <>\ ]+?\.(mp3|m4a|flac|ogg|wav|mp4|mkv|webm|m3u8|mpd)(\?[^\"'\\ <>\ ]*)?/gi;
        var match;
        while ((match = regex.exec(html)) !== null) { found[match[0]] = true; }
        return JSON.stringify(Object.keys(found));
    })();
"""

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(viewModel: BrowserViewModel = viewModel()) {
    val context = LocalContext.current
    val detected by viewModel.detected.collectAsState()
    val address by viewModel.currentAddress

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    viewModel.onResource(request.url.toString())
                    return null
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    viewModel.onAddressChanged(url ?: "")
                    view.evaluateJavascript(DETECT_SCRIPT) { result -> viewModel.onScriptResult(result) }
                }
            }
            webChromeClient = WebChromeClient()
            loadUrl("https://www.google.com")
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = { if (webView.canGoBack()) webView.goBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Précédent")
                }
                IconButton(onClick = { if (webView.canGoForward()) webView.goForward() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Suivant")
                }
                IconButton(onClick = { webView.reload() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Recharger")
                }
                OutlinedTextField(
                    value = address,
                    onValueChange = { addressValue -> viewModel.currentAddress.value = addressValue },
                    label = { Text("Adresse") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    var target = viewModel.currentAddress.value.trim()
                    if (!target.startsWith("http")) target = "https://$target"
                    webView.loadUrl(target)
                }) { Text("Aller") }
            }

            Box(modifier = Modifier.weight(1f)) {
                AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
            }
        }

        if (detected.isNotEmpty()) {
            DetectedMediaPanel(
                items = detected,
                onDownload = { viewModel.download(it) },
                onDismiss = { viewModel.clearDetected() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(8.dp),
            )
        }
    }
}

@Composable
private fun DetectedMediaPanel(
    items: List<MediaItem>,
    onDownload: (MediaItem) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Médias détectés (${items.size})",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = null)
                    Text("Fermer")
                }
            }
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(items, key = { it.url }) { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            Text(item.mimeType, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { onDownload(item) }) {
                            Icon(Icons.Filled.Download, contentDescription = "Télécharger")
                        }
                    }
                }
            }
        }
    }
}
