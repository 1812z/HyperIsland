package io.github.hyperisland.xposed.utils

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import io.github.hyperisland.xposed.ConfigManager

object FullscreenBehavior {

    const val MODE_OFF = "off"
    const val MODE_FALLBACK = "fallback"
    const val MODE_EXPAND = "expand"

    private const val PREF_KEY = "pref_fullscreen_behavior"

    fun mode(): String {
        return when (ConfigManager.getString(PREF_KEY, MODE_OFF).trim().lowercase()) {
            MODE_FALLBACK -> MODE_FALLBACK
            MODE_EXPAND -> MODE_EXPAND
            else -> MODE_OFF
        }
    }

    fun isFullscreenLike(context: Context): Boolean {
        val isLandscape =
            context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (isLandscape) return true

        val immersivePolicy = runCatching {
            Settings.Global.getString(context.contentResolver, "policy_control")
                ?.lowercase()
                .orEmpty()
        }.getOrDefault("")
        return immersivePolicy.contains("immersive.full") ||
            immersivePolicy.contains("immersive.status")
    }
}
