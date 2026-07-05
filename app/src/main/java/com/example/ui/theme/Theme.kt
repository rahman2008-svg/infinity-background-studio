package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VibrantColorScheme = lightColorScheme(
    primary = VibrantPrimary,
    onPrimary = Color.White,
    secondary = VibrantSecondary,
    onSecondary = Color.White,
    background = VibrantBackground,
    onBackground = VibrantTextPrimary,
    surface = Color.White,
    onSurface = VibrantTextPrimary,
    surfaceVariant = Color(0xFFF3EDF7),
    onSurfaceVariant = Color(0xFF1D192B)
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = VibrantColorScheme,
        typography = Typography,
        content = content
    )
}

