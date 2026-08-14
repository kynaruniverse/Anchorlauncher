package com.anchor.launcher

data class AppInfo(
    val label: String,
    val packageName: String
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
