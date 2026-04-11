package io.github.hyperisland.data.prefs

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager

class SettingsRepository(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PrefKeys.PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): SettingsState {
        val iconVisible = isDesktopIconVisible()
        val hideDesktopIcon = !iconVisible
        if (prefs.getBoolean(PrefKeys.HIDE_DESKTOP_ICON, false) != hideDesktopIcon) {
            prefs.edit().putBoolean(PrefKeys.HIDE_DESKTOP_ICON, hideDesktopIcon).apply()
        }

        return SettingsState(
            showWelcome = prefs.getBoolean(PrefKeys.SHOW_WELCOME, true),
            resumeNotification = prefs.getBoolean(PrefKeys.RESUME_NOTIFICATION, true),
            useHookAppIcon = prefs.getBoolean(PrefKeys.USE_HOOK_APP_ICON, true),
            interactionHaptics = prefs.getBoolean(PrefKeys.INTERACTION_HAPTICS, true),
            checkUpdateOnLaunch = prefs.getBoolean(PrefKeys.CHECK_UPDATE_ON_LAUNCH, true),
            themeMode = prefs.getString(PrefKeys.THEME_MODE, "system") ?: "system",
            locale = prefs.getString(PrefKeys.LOCALE, null),
            aiEnabled = prefs.getBoolean(PrefKeys.AI_ENABLED, false),
            roundIcon = prefs.getBoolean(PrefKeys.ROUND_ICON, true),
            marqueeFeature = prefs.getBoolean(PrefKeys.MARQUEE_FEATURE, false),
            marqueeSpeed = prefs.getInt(PrefKeys.MARQUEE_SPEED, 100).coerceIn(20, 500),
            bigIslandMaxWidthEnabled = prefs.getBoolean(PrefKeys.BIG_ISLAND_MAX_WIDTH_ENABLED, false),
            bigIslandMaxWidth = prefs.getInt(PrefKeys.BIG_ISLAND_MAX_WIDTH, 200).coerceIn(50, 500),
            useFloatingNavigationBar = prefs.getBoolean(PrefKeys.USE_FLOATING_NAVIGATION_BAR, false),
            unlockAllFocus = prefs.getBoolean(PrefKeys.UNLOCK_ALL_FOCUS, false),
            unlockFocusAuth = prefs.getBoolean(PrefKeys.UNLOCK_FOCUS_AUTH, false),
            defaultFirstFloat = prefs.getBoolean(PrefKeys.DEFAULT_FIRST_FLOAT, false),
            defaultEnableFloat = prefs.getBoolean(PrefKeys.DEFAULT_ENABLE_FLOAT, false),
            defaultShowIslandIcon = prefs.getBoolean(PrefKeys.DEFAULT_SHOW_ISLAND_ICON, true),
            defaultMarquee = prefs.getBoolean(PrefKeys.DEFAULT_MARQUEE, false),
            defaultDynamicHighlightColor = prefs.getBoolean(PrefKeys.DEFAULT_DYNAMIC_HIGHLIGHT_COLOR, false),
            defaultOuterGlow = prefs.getBoolean(PrefKeys.DEFAULT_OUTER_GLOW, false),
            defaultFocusNotif = prefs.getBoolean(PrefKeys.DEFAULT_FOCUS_NOTIF, true),
            defaultPreserveSmallIcon = prefs.getBoolean(PrefKeys.DEFAULT_PRESERVE_SMALL_ICON, false),
            defaultRestoreLockscreen = prefs.getBoolean(PrefKeys.DEFAULT_RESTORE_LOCKSCREEN, false),
            hideDesktopIcon = hideDesktopIcon,
        )
    }

    fun setBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun setString(key: String, value: String?) {
        prefs.edit().putString(key, value).apply()
    }

    fun setMarqueeSpeed(value: Int) {
        prefs.edit().putInt(PrefKeys.MARQUEE_SPEED, value.coerceIn(20, 500)).apply()
    }

    fun setBigIslandMaxWidth(value: Int) {
        prefs.edit().putInt(PrefKeys.BIG_ISLAND_MAX_WIDTH, value.coerceIn(50, 500)).apply()
    }

    fun setDesktopIconHidden(hidden: Boolean) {
        val componentName = ComponentName(context.packageName, "${context.packageName}.MainActivityAlias")
        val newState = if (hidden) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }
        context.packageManager.setComponentEnabledSetting(
            componentName,
            newState,
            PackageManager.DONT_KILL_APP,
        )
        prefs.edit().putBoolean(PrefKeys.HIDE_DESKTOP_ICON, hidden).apply()
    }

    private fun isDesktopIconVisible(): Boolean {
        val componentName = ComponentName(context.packageName, "${context.packageName}.MainActivityAlias")
        val state = context.packageManager.getComponentEnabledSetting(componentName)
        return state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }
}
