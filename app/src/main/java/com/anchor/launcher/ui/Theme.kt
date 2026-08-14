package com.anchor.launcher.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val PremiumOledColorScheme = darkColorScheme(
    primary = Color(0xFFFFFFFF),        // Pure white
    secondary = Color(0xFF888888),      // Muted gray
    tertiary = Color(0xFFD4AF37),       // Restrained Muted Gold Accent
    background = Color(0xFF000000),     // True OLED Black
    surface = Color(0xFF111111),        // Deep gray
    surfaceVariant = Color(0xFF1A1A1A), // Interactive surface
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
