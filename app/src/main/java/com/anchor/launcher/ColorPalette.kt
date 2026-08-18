package com.anchor.launcher

import androidx.compose.ui.graphics.Color

/**
 * The app's curated set of muted, calm colors -- used for both the Base (background) and
 * Accent pickers in Settings > Colors. Deliberately one shared palette for both roles,
 * rather than separate color spaces, so any base/accent combination the user picks stays
 * visually coherent with the rest of the set.
 *
 * Note on contrast: unlike the previous fixed OLED-black/white scheme (tuned to a specific
 * ~10:1 ratio), text/surface colors are now computed from whichever base is chosen (see
 * anchorColorScheme() in Theme.kt) rather than hand-verified per color. Muted-palette colors
 * inherently have less contrast headroom than pure black/white by design, so exact WCAG
 * ratios will vary somewhat by which base is picked -- an accepted tradeoff of letting the
 * background itself be one of these colors, not something silently glossed over.
 */
data class PaletteEntry(val id: String, val labelRes: Int, val color: Color)

val AnchorPalette = listOf(
    PaletteEntry("ebony", R.string.color_ebony, Color(0xFF51604B)),
    PaletteEntry("reseda_green", R.string.color_reseda_green, Color(0xFF6D8165)),
    PaletteEntry("cambridge_blue", R.string.color_cambridge_blue, Color(0xFF93AE88)),
    PaletteEntry("sage", R.string.color_sage, Color(0xFFC4BC84)),
    PaletteEntry("pearl", R.string.color_pearl, Color(0xFFD6D2B5)),
    PaletteEntry("ivory", R.string.color_ivory, Color(0xFFFAF8E8)),
    PaletteEntry("brown_sugar", R.string.color_brown_sugar, Color(0xFFC07560)),
    PaletteEntry("slate_gray", R.string.color_slate_gray, Color(0xFF767A8A)),
    PaletteEntry("powder_blue", R.string.color_powder_blue, Color(0xFFA1B9C5)),
    PaletteEntry("silver", R.string.color_silver, Color(0xFFC2C2C2)),
    PaletteEntry("dim_gray", R.string.color_dim_gray, Color(0xFF776F5F)),
    PaletteEntry("black_olive", R.string.color_black_olive, Color(0xFF49453E)),
    PaletteEntry("khaki", R.string.color_khaki, Color(0xFFC0A989)),
    PaletteEntry("tea_rose", R.string.color_tea_rose, Color(0xFFF3CFCF)),
    PaletteEntry("old_rose", R.string.color_old_rose, Color(0xFFD48C8C))
)

private val DEFAULT_ENTRY = AnchorPalette.first { it.id == "black_olive" }

fun colorForId(id: String): Color = AnchorPalette.find { it.id == id }?.color ?: DEFAULT_ENTRY.color

fun labelResForColorId(id: String): Int = AnchorPalette.find { it.id == id }?.labelRes ?: DEFAULT_ENTRY.labelRes
