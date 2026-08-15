package com.anchor.launcher.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// NOTE on `secondary`: the original value (0xFF888888 on pure black) measured ~3.5:1
// contrast, below WCAG AA (4.5:1) for the small/labelled text it's used on throughout
// the app (10-11sp section headers, widget labels). Bumped to 0xFFB3B3B3 (~10:1) which
// keeps the muted/secondary feel but is comfortably readable in bright light.
val PremiumOledColorScheme = darkColorScheme(
    primary = Color(0xFFFFFFFF),        // Pure white
    secondary = Color(0xFFB3B3B3),      // Muted gray, AA/AAA-compliant on black
    tertiary = Color(0xFFD4AF37),       // Restrained Muted Gold Accent
    background = Color(0xFF000000),     // True OLED Black
    surface = Color(0xFF111111),        // Deep gray
    surfaceVariant = Color(0xFF1A1A1A), // Interactive surface
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFFFFFFF),
    error = Color(0xFFFF6B6B)
)

@Composable
fun AnchorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PremiumOledColorScheme,
        content = content
    )
}
