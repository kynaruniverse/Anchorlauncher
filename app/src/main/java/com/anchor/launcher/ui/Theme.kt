package com.anchor.launcher.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * Base and accent are both user-chosen from AnchorPalette (see ColorPalette.kt) -- a fixed
 * set of muted, calm colors rather than an arbitrary color wheel. Text/surface colors are
 * derived from the base's luminance rather than hardcoded, so any of the 15 palette colors
 * works as a background without hand-tuning contrast per color.
 *
 * `accent` (tertiary) keeps its original job regardless of which color it is: reserved for
 * exactly three "active state" moments -- an in-progress Focus session, the
 * currently-selected Space in Settings, and a completed task's checkbox. Not used
 * decoratively elsewhere, so when it appears it means something.
 */
fun anchorColorScheme(base: Color, accent: Color): ColorScheme {
    val baseIsDark = base.luminance() < 0.5f
    val onBase = if (baseIsDark) Color(0xFFFFFFFF) else Color(0xFF000000)

    // Muted/secondary text: onBase blended mostly toward itself, a little toward the base,
    // rather than a fixed gray -- keeps it legible against whichever base is active while
    // still reading as visually "quieter" than primary text.
    val onBaseSecondary = lerp(onBase, base, 0.35f)

    // Surface/surfaceVariant: base nudged toward onBase, giving cards/dialogs a distinct
    // layer without a Material shadow -- the same idea as the original fixed scheme's
    // #111111 surface on #000000 background, just relative to whichever base is chosen.
    val surface = lerp(base, onBase, if (baseIsDark) 0.08f else 0.06f)
    val surfaceVariant = lerp(base, onBase, if (baseIsDark) 0.14f else 0.10f)

    // Two separate named-argument calls rather than one call through a shared function
    // reference: Kotlin doesn't allow named arguments when invoking a value of function
    // type, only when calling the function directly.
    return if (baseIsDark) {
        darkColorScheme(
            primary = onBase,
            secondary = onBaseSecondary,
            tertiary = accent,
            background = base,
            surface = surface,
            surfaceVariant = surfaceVariant,
            onBackground = onBase,
            onSurface = onBase,
            error = Color(0xFFFF6B6B)
        )
    } else {
        lightColorScheme(
            primary = onBase,
            secondary = onBaseSecondary,
            tertiary = accent,
            background = base,
            surface = surface,
            surfaceVariant = surfaceVariant,
            onBackground = onBase,
            onSurface = onBase,
            error = Color(0xFFFF6B6B)
        )
    }
}

@Composable
fun AnchorTheme(
    baseColor: Color = Color(0xFF49453E), // Black Olive -- close in spirit to the app's
                                            // original OLED-black default
    accentColor: Color = Color(0xFFC4BC84), // Sage
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = anchorColorScheme(baseColor, accentColor),
        content = content
    )
}
