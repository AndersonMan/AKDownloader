package com.andersonman.akdownloader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AkPurple = Color(0xFF7C4DFF)
val AkPurpleLight = Color(0xFFCFBCFF)
val AkBackground = Color(0xFF101014)
val AkSurface = Color(0xFF1A1A21)

private val DarkColorScheme = darkColorScheme(
    primary = AkPurpleLight,
    secondary = Color(0xFFB69DF8),
    tertiary = Color(0xFFEFB8C8),
    background = AkBackground,
    surface = AkSurface,
)

private val LightColorScheme = lightColorScheme(primary = AkPurple)

@Composable
fun AKDownloaderTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content,
    )
}
