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

enum class DensityMode {
    QUIET,
    BALANCED,
    CONTROL
}

// Central place for small magic numbers that were previously scattered/hardcoded.
object AnchorDefaults {
    const val MAX_RECENT_APPS = 5
}
