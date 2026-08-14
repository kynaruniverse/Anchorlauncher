package com.anchor.launcher.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val CalmDarkColorScheme = darkColorScheme(
    primary = Color(0xFFE2E2E2),
    secondary = Color(0xFF8E8E93),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onBackground = Color(0xFFE2E2E2),
    onSurface = Color(0xFFE2E2E2)
)

@Composable
fun AnchorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CalmDarkColorScheme,
        content = content
    )
}
