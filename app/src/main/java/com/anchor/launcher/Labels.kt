package com.anchor.launcher

/**
 * Maps internal, persisted/logic-facing keys (DensityMode enum, friction-level strings
 * stored in the DB and used in `when` branches) to display string resources. Keeping this
 * mapping separate from the keys themselves means the keys stay stable/English for
 * storage and comparisons while the text shown to the user can be localized.
 */
fun densityModeLabelRes(mode: DensityMode): Int = when (mode) {
    DensityMode.QUIET -> R.string.density_quiet_title
    DensityMode.BALANCED -> R.string.density_balanced_title
    DensityMode.CONTROL -> R.string.density_control_title
}

fun frictionLevelLabelRes(level: String): Int = when (level) {
    "LIGHT" -> R.string.friction_light
    "INTENT" -> R.string.friction_intent
    "TIMER" -> R.string.friction_timer
    "BLOCK" -> R.string.friction_block
    "SCHEDULE" -> R.string.friction_schedule
    else -> R.string.friction_off
}

/**
 * One-line explanation shown under each friction level in the Settings dialog. Added
 * because the friction system's behavior -- especially BLOCK, which only gates an app
 * during an active Focus session and otherwise opens instantly -- was never explained
 * anywhere in the UI, a gap flagged since the first pass over this app.
 */
fun frictionLevelDescRes(level: String): Int = when (level) {
    "LIGHT" -> R.string.friction_light_desc
    "INTENT" -> R.string.friction_intent_desc
    "TIMER" -> R.string.friction_timer_desc
    "BLOCK" -> R.string.friction_block_desc
    "SCHEDULE" -> R.string.friction_schedule_desc
    else -> R.string.friction_off_desc
}

/**
 * Display labels for the gesture-action values stored in AnchorViewModel.swipeDownAction /
 * doubleTapAction and interpreted by MainScreen.handleGestureAction(). Only actions that are
 * actually implemented there are offered here -- see that function before adding a new one.
 */
fun gestureActionLabelRes(action: String): Int = when (action) {
    "DRAWER" -> R.string.gesture_drawer
    "NONE" -> R.string.gesture_none
    else -> R.string.gesture_notifications
}
