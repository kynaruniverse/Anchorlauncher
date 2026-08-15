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
    else -> R.string.friction_off
}
