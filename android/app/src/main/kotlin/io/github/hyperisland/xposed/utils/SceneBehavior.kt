package io.github.hyperisland.xposed.utils

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import android.util.Log
import io.github.hyperisland.xposed.ConfigManager

object SceneBehavior {

    const val ACTION_DEFAULT = "default"
    const val ACTION_SMALL_ONLY = "small_only"
    const val ACTION_EXPAND = "expand"
    const val ACTION_SUPPRESS = "suppress"

    private const val PREF_FOREGROUND_PACKAGES = "pref_scene_foreground_packages"
    private const val PREF_FOREGROUND_EXCLUDED_PACKAGES = "pref_scene_excluded_foreground_packages"
    private const val PREF_LEGACY_BLACKLIST = "pref_app_blacklist"
    private const val PREF_DND = "pref_scene_dnd"
    private const val PREF_FULLSCREEN = "pref_scene_fullscreen"
    private const val PREF_LANDSCAPE = "pref_scene_landscape"

    enum class Surface {
        GENERIC_NOTIFICATION,
        TOAST,
        DISPATCHER,
    }

    data class Environment(
        val foregroundPackage: String,
        val isLandscape: Boolean,
        val isImmersiveFullscreen: Boolean,
        val isDndEnabled: Boolean,
    ) {
        val isFullscreenLike: Boolean
            get() = isLandscape || isImmersiveFullscreen
    }

    data class Decision(
        val action: String,
        val environment: Environment,
    ) {
        val shouldSuppress: Boolean
            get() = action == ACTION_SUPPRESS

        fun applyToTriOpt(value: String): String {
            return when (action) {
                ACTION_SMALL_ONLY -> "off"
                ACTION_EXPAND -> "on"
                else -> value
            }
        }

        fun applyToBoolean(value: Boolean): Boolean {
            return when (action) {
                ACTION_SMALL_ONLY -> false
                ACTION_EXPAND -> true
                else -> value
            }
        }
    }

    fun resolve(
        context: Context,
        surface: Surface,
        sourcePackage: String = "",
        channelId: String = "",
    ): Decision {
        val environment = Environment(
            foregroundPackage = "",
            isLandscape = false,
            isImmersiveFullscreen = false,
            isDndEnabled = false,
        )
        val action = resolveAction(context, environment, surface, sourcePackage, channelId)
        return Decision(action, environment)
    }

    fun readEnvironment(context: Context): Environment {
        return Environment(
            foregroundPackage = foregroundPackage(context),
            isLandscape = isLandscape(context),
            isImmersiveFullscreen = isImmersiveFullscreen(context),
            isDndEnabled = isDndEnabled(context),
        )
    }

    private fun resolveAction(
        context: Context,
        environment: Environment,
        surface: Surface,
        sourcePackage: String,
        channelId: String,
    ): String {
        val scopedSourceAction = normalizedAction(
            ConfigManager.getString("pref_scene_source_${sourcePackage}_$channelId", ""),
        )
        if (scopedSourceAction != ACTION_DEFAULT) return scopedSourceAction

        val sourceAction = normalizedAction(
            ConfigManager.getString("pref_scene_source_$sourcePackage", ""),
        )
        if (sourceAction != ACTION_DEFAULT) return sourceAction

        val foregroundExcludedPackages = ConfigManager.getString(PREF_FOREGROUND_EXCLUDED_PACKAGES)
        if (!isListed(sourcePackage, foregroundExcludedPackages)) {
            val foregroundPackages = ConfigManager.getString(PREF_FOREGROUND_PACKAGES)
            val legacyBlacklist = ConfigManager.getString(PREF_LEGACY_BLACKLIST)
            val needsForeground = foregroundPackages.isNotBlank() || legacyBlacklist.isNotBlank()
            if (needsForeground) {
                val foregroundPackage = foregroundPackage(context)
                val foregroundAction = normalizedAction(
                    ConfigManager.getString("pref_scene_foreground_$foregroundPackage", ""),
                )
                if (foregroundAction != ACTION_DEFAULT) return foregroundAction
                if (isListed(foregroundPackage, legacyBlacklist)) return ACTION_SMALL_ONLY
            }
        }

        val dndAction = normalizedAction(ConfigManager.getString(PREF_DND, ACTION_DEFAULT))
        if (dndAction != ACTION_DEFAULT && isDndEnabled(context)) {
            return dndAction
        }

        val sceneFullscreenAction = normalizedAction(ConfigManager.getString(PREF_FULLSCREEN, ""))
        val legacyFullscreenAction = legacyFullscreenAction()
        if ((sceneFullscreenAction != ACTION_DEFAULT || legacyFullscreenAction != null) && isImmersiveFullscreen(context)) {
            if (sceneFullscreenAction != ACTION_DEFAULT) return sceneFullscreenAction
            legacyFullscreenAction?.let { return it }
        }

        val sceneLandscapeAction = normalizedAction(ConfigManager.getString(PREF_LANDSCAPE, ""))
        val legacyLandscapeAction = legacyLandscapeAction()
        if ((sceneLandscapeAction != ACTION_DEFAULT || legacyLandscapeAction != null) && isLandscape(context)) {
            if (sceneLandscapeAction != ACTION_DEFAULT) return sceneLandscapeAction
            legacyLandscapeAction?.let { return it }
        }

        val surfaceAction = normalizedAction(
            ConfigManager.getString("pref_scene_surface_${surface.name.lowercase()}", ""),
        )
        return surfaceAction
    }

    private fun normalizedAction(value: String): String {
        return when (value.trim().lowercase()) {
            ACTION_SMALL_ONLY -> ACTION_SMALL_ONLY
            ACTION_EXPAND -> ACTION_EXPAND
            ACTION_SUPPRESS -> ACTION_SUPPRESS
            else -> ACTION_DEFAULT
        }
    }

    private fun legacyFullscreenAction(): String? {
        return when (FullscreenBehavior.mode()) {
            FullscreenBehavior.MODE_FALLBACK -> ACTION_SUPPRESS
            FullscreenBehavior.MODE_EXPAND -> ACTION_EXPAND
            else -> null
        }
    }

    private fun legacyLandscapeAction(): String? {
        return when (FullscreenBehavior.landscapeMode()) {
            FullscreenBehavior.MODE_FALLBACK -> ACTION_SUPPRESS
            FullscreenBehavior.MODE_EXPAND -> ACTION_EXPAND
            else -> null
        }
    }

    private fun isListed(packageName: String, csv: String): Boolean {
        if (packageName.isBlank()) return false
        if (csv.isBlank()) return false
        return csv.split(',').any { it.trim() == packageName }
    }

    private fun isFullscreenLike(context: Context): Boolean {
        return isLandscape(context) || isImmersiveFullscreen(context)
    }

    private fun foregroundPackage(context: Context): String {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            @Suppress("DEPRECATION")
            am.getRunningTasks(1).firstOrNull()?.topActivity?.packageName.orEmpty()
        } catch (e: Throwable) {
            Log.d("HyperIsland", "HyperIsland[Scene]: get foreground failed: ${e.message}")
            ""
        }
    }

    fun isLandscape(context: Context): Boolean {
        return context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    private fun isImmersiveFullscreen(context: Context): Boolean {
        val immersivePolicy = runCatching {
            Settings.Global.getString(context.contentResolver, "policy_control")
                ?.lowercase()
                .orEmpty()
        }.getOrDefault("")
        return immersivePolicy.contains("immersive.full") ||
            immersivePolicy.contains("immersive.status")
    }

    private fun isDndEnabled(context: Context): Boolean {
        return try {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return false
            nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        } catch (e: Throwable) {
            Log.d("HyperIsland", "HyperIsland[Scene]: read DND failed: ${e.message}")
            false
        }
    }
}
