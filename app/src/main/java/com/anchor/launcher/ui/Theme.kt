package com.anchor.launcher.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val PremiumOledColorScheme = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    secondary = Color(0xFF888888),
    background = Color(0xFF000000),
    surface = Color(0xFF0A0A0A),
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFFFFFFF),
    error = Color(0xFFFF5555)
)

@Composable
fun AnchorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PremiumOledColorScheme,
        content = content
    )
}
