package io.github.hyperisland.ui.app

internal fun filterApps(
    apps: List<AppItem>,
    query: String,
    showSystemApps: Boolean,
    alwaysVisiblePackages: Set<String>,
    prioritizedPackages: Set<String> = alwaysVisiblePackages,
): List<AppItem> {
    val normalizedQuery = query.trim().lowercase()
    return apps
        .filter { app ->
            val matchSystem =
                showSystemApps || !app.isSystem || alwaysVisiblePackages.contains(app.packageName)
            val matchQuery =
                normalizedQuery.isBlank() ||
                    app.appName.lowercase().contains(normalizedQuery) ||
                    app.packageName.lowercase().contains(normalizedQuery)
            matchSystem && matchQuery
        }
        .sortedWith(
            compareByDescending<AppItem> { prioritizedPackages.contains(it.packageName) }
                .thenBy { it.appName.lowercase() },
        )
}
