package com.andersonman.akdownloader.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.andersonman.akdownloader.ui.browser.BrowserScreen
import com.andersonman.akdownloader.ui.downloads.DownloadsScreen
import com.andersonman.akdownloader.ui.home.HomeScreen
import com.andersonman.akdownloader.ui.library.LibraryScreen
import com.andersonman.akdownloader.ui.player.PlayerScreen

enum class Tab(val label: String) {
    HOME("Accueil"),
    BROWSER("Navigateur"),
    DOWNLOADS("Téléchargements"),
    LIBRARY("Bibliothèque"),
}

@Composable
fun AKDownloaderApp() {
    var currentTab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var playerMedia by remember { mutableStateOf<String?>(null) }

    val openInPlayer: (Uri) -> Unit = { uri -> playerMedia = uri.toString() }

    playerMedia?.let { mediaUri ->
        PlayerScreen(uri = Uri.parse(mediaUri), onClose = { playerMedia = null })
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = {
                            when (tab) {
                                Tab.HOME -> Icon(Icons.Filled.Download, contentDescription = tab.label)
                                Tab.BROWSER -> Icon(Icons.Filled.Language, contentDescription = tab.label)
                                Tab.DOWNLOADS -> Icon(Icons.Filled.Subscriptions, contentDescription = tab.label)
                                Tab.LIBRARY -> Icon(Icons.Filled.LibraryMusic, contentDescription = tab.label)
                            }
                        },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (currentTab) {
                Tab.HOME -> HomeScreen(onDownloadQueued = { currentTab = Tab.DOWNLOADS })
                Tab.BROWSER -> BrowserScreen()
                Tab.DOWNLOADS -> DownloadsScreen(onPlay = openInPlayer)
                Tab.LIBRARY -> LibraryScreen(onPlay = openInPlayer)
            }
        }
    }
}
