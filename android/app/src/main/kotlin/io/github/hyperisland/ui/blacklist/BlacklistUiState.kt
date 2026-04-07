package io.github.hyperisland.ui.blacklist

import io.github.hyperisland.ui.app.AppItem

data class BlacklistUiState(
    val loading: Boolean = true,
    val query: String = "",
    val showSystemApps: Boolean = false,
    val apps: List<AppItem> = emptyList(),
    val blacklistedPackages: Set<String> = emptySet(),
    val error: String? = null,
)
