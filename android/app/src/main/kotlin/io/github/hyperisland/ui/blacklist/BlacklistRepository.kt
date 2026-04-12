package io.github.hyperisland.ui.blacklist

import android.content.Context
import io.github.hyperisland.data.prefs.PrefKeys
import io.github.hyperisland.ui.app.AppAdaptationRepository
import io.github.hyperisland.ui.app.AppItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BlacklistRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PrefKeys.PREFS_NAME, Context.MODE_PRIVATE)
    private val appRepo = AppAdaptationRepository(context)

    suspend fun loadApps(): List<AppItem> = withContext(Dispatchers.IO) { appRepo.loadInstalledApps() }

    fun loadBlacklistedPackages(): Set<String> {
        val csv = prefs.getString(PrefKeys.APP_BLACKLIST, "") ?: ""
        return if (csv.isBlank()) emptySet() else csv.split(",").filter { it.isNotBlank() }.toSet()
    }

    fun saveBlacklistedPackages(packages: Set<String>) {
        prefs.edit().putString(PrefKeys.APP_BLACKLIST, packages.joinToString(",")).apply()
    }

    fun applyGamePreset(allApps: List<AppItem>, existing: Set<String>): Pair<Set<String>, Int> {
        val next = existing.toMutableSet()
        var added = 0
        allApps.forEach { app ->
            if (app.packageName in GamePresets.PACKAGES && next.add(app.packageName)) {
                added += 1
            }
        }
        return next.toSet() to added
    }
}
