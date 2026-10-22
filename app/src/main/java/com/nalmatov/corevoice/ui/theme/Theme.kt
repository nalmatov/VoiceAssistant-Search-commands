package com.nalmatov.corevoice.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CoreVoiceColors = darkColorScheme(
    primary = Color(0xFF8B5CF6),
    secondary = Color(0xFF6366F1),
    background = Color(0xFF09090B),
    surface = Color(0xFF18181B),
    onPrimary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun CoreVoiceTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CoreVoiceColors,
        content = content
    )
}