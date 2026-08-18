package com.anchor.launcher

import android.graphics.Bitmap

data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: Bitmap? = null
)

data class Space(
    val id: String,
    val name: String,
    val allowedApps: List<String> = emptyList()
)

/**
 * A named shortcut bundling "switch to this Space" + "start a Focus session for N minutes"
 * into one tap (e.g. "Deep Work" -> Work space + 25 min). Deliberately scoped this way
 * rather than snapshotting/restoring entire per-app friction-level maps -- that would be a
 * much larger, riskier feature, and this still delivers the core idea (one tap, a named
 * routine) using primitives that already exist and are already tested (startFocus,
 * currentSpaceIndex).
 */
data class Preset(
    val id: String,
    val name: String,
    val focusMinutes: Int,
    // Empty means "don't switch Space, just start the focus session in whichever Space
    // is currently active."
    val spaceId: String = ""
)

enum class DensityMode {
    QUIET,
    BALANCED,
    CONTROL
}

// Central place for small magic numbers that were previously scattered/hardcoded.
object AnchorDefaults {
    const val MAX_RECENT_APPS = 5
}
