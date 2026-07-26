package com.vicherarr.camespdroid.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = AccentCyan,
    secondary = AccentTeal,
    background = DarkBg,
    surface = SurfaceDark,
    surfaceVariant = SurfaceCard,
    onPrimary = DarkBg,
    onSecondary = DarkBg,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary
)

@Composable
fun CamEspDroidTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}