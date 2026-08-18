package com.anchor.launcher

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Easing
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Spacing scale (8pt-based, with a couple of finer increments for tight rows). Previously
 * spacing was ad hoc dp values (4, 6, 8, 12, 16, 24, 32, 48...) chosen per call site with no
 * shared logic -- a big part of why the UI read as flat rather than deliberately simple.
 * Prefer these over new literal dp values.
 */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
    val xxxl = 64.dp
}

/**
 * Static type scale for the app's UI chrome (section labels, dialog headings, widget rows,
 * onboarding/gate copy). Anchor's whole identity is typographic -- OLED black, almost no
 * color, no icons on the home surface -- so weight and letter-spacing contrast are doing the
 * job color/elevation would do elsewhere. Previously nearly every Text() call hardcoded its
 * own fontSize/letterSpacing, so three near-identical "section label" styles existed (10sp/1sp,
 * 11sp/1sp, 10sp/2sp) purely by drift. This is the single source of truth going forward.
 *
 * Deliberately NOT used for: the home-screen clock, date, task list, or space name text in
 * TodaySurface/ClockDisplay. Those already scale with the user's Settings > Typography font
 * size slider and bold toggle -- a working, user-controlled system that's a different concern
 * from this static scale, and routing it through fixed tokens would fight that feature.
 */
object AnchorType {
    // Reserved for the largest "hero" moments (currently just the onboarding title) --
    // deliberately not reused elsewhere so it keeps its singular first-impression weight.
    val hero = TextStyle(
        fontWeight = FontWeight.ExtraLight,
        fontSize = 32.sp,
        letterSpacing = 12.sp
    )

    // Dialog/screen headings, onboarding "READY", the Intent Gate app-name heading.
    val title = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        letterSpacing = 4.sp
    )

    // The app's one repeating "eyebrow" style: section headers everywhere (Settings, the
    // App Drawer's FAVORITES/RECENT/ALL APPS, widget captions like "BATTERY"/"ONE THING").
    val label = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 2.sp
    )

    // Ordinary reading text: Intent Gate prompts, dialog body copy.
    val body = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        letterSpacing = 0.sp
    )
}

/**
 * Motion tokens. Previously durations/easings were chosen per-animation (200/300/4000ms,
 * mixed easings) with no shared logic. `standard` is for ordinary UI transitions (drawer
 * open/close, list entrance, step changes); `calm` is a slower, gentler pace reserved for
 * anything meant to be *felt* rather than just registered (the breathing widget).
 */
object Motion {
    const val fast = 150
    const val standard = 300
    const val breath = 4000

    val standardEasing: Easing = FastOutSlowInEasing
    val calmEasing: Easing = LinearOutSlowInEasing
}

/** Multiplies a TextStyle's font size by the user's Typography > Font Size setting. */
fun TextStyle.scaled(multiplier: Float): TextStyle = copy(fontSize = fontSize * multiplier)

/**
 * Hairline separator color for giving dialogs/cards/dividers quiet structure without a
 * Material shadow, which would look out of place against true OLED black. ~8% white.
 */
val AnchorSurfaceBorder = Color(0x14FFFFFF)
