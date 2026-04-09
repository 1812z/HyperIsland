package io.github.hyperisland.ui.app

data class AppItem(
    val packageName: String,
    val appName: String,
    val isSystem: Boolean,
    val icon: ByteArray = byteArrayOf(),
)

data class AppsUiState(
    val loading: Boolean = true,
    val applying: Boolean = false,
    val query: String = "",
    val showSystemApps: Boolean = false,
    val apps: List<AppItem> = emptyList(),
    val filteredApps: List<AppItem> = emptyList(),
    val enabledPackages: Set<String> = emptySet(),
    val selectedPackages: Set<String> = emptySet(),
    val error: String? = null,
)
