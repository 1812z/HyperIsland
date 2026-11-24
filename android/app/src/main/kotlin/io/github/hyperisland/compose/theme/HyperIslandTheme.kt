package io.github.hyperisland.compose.theme

import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.rememberBooleanPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
internal fun HyperIslandTheme(
    repository: FlutterPrefsRepository,
    onShellReady: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    var themeMode by remember { mutableStateOf(repository.getString(PREF_THEME_MODE, "system")) }
    var monetEnabled by remember { mutableStateOf(repository.getBoolean(PREF_MONET_ENABLED, false)) }
    var themeSeedColor by remember {
        mutableStateOf(repository.getLong(PREF_THEME_SEED_COLOR, DEFAULT_THEME_SEED_COLOR))
    }
    val systemInDarkTheme = isSystemInDarkTheme()
    val useDarkSystemBars = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> systemInDarkTheme
    }
    DisposableEffect(repository) {
        val unregister = repository.addChangeListener { key ->
            when (key) {
                PREF_THEME_MODE -> themeMode = repository.getString(PREF_THEME_MODE, "system")
                PREF_MONET_ENABLED -> monetEnabled = repository.getBoolean(PREF_MONET_ENABLED, false)
                PREF_THEME_SEED_COLOR -> {
                    themeSeedColor = repository.getLong(PREF_THEME_SEED_COLOR, DEFAULT_THEME_SEED_COLOR)
                }
            }
        }
        onDispose(unregister)
    }
    val controller = remember(themeMode, monetEnabled, themeSeedColor) {
        ThemeController(
            colorSchemeMode = when (themeMode) {
                "light" -> if (monetEnabled) ColorSchemeMode.MonetLight else ColorSchemeMode.Light
                "dark" -> if (monetEnabled) ColorSchemeMode.MonetDark else ColorSchemeMode.Dark
                else -> if (monetEnabled) ColorSchemeMode.MonetSystem else ColorSchemeMode.System
            },
            keyColor = if (monetEnabled) Color(themeSeedColor.toInt()) else null,
        )
    }
    val activity = LocalContext.current as? ComponentActivity
    SideEffect {
        val systemBarStyle = if (useDarkSystemBars) {
            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        } else {
            SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        }
        activity?.enableEdgeToEdge(
            statusBarStyle = systemBarStyle,
            navigationBarStyle = systemBarStyle,
        )
    }
    val glassEffectsEnabled by rememberBooleanPreference(repository, PREF_GLASS_EFFECTS_ENABLED, true)
    val glassSettings = rememberGlassSettings(repository, GlassPreferences.Interface)
    val navigationBarGlassSettings = rememberGlassSettings(repository, GlassPreferences.NavigationBar)
    val cardGlassSettings = rememberGlassSettings(repository, GlassPreferences.Cards)
    val wallpaper = rememberAppWallpaper(repository)
    // 启动遮罩放行信号：壁纸（可选）解码完成后，再等一帧让壁纸真正画上屏幕。
    // 否则撤掉 Splash 的那一帧底下还是纯色底，用户会看到"先闪一下底色才出壁纸"。
    LaunchedEffect(wallpaper) {
        while (wallpaper.busy) withFrameNanos { }
        withFrameNanos { }
        onShellReady()
    }
    CompositionLocalProvider(
        LocalGlassEffectsEnabled provides glassEffectsEnabled,
        LocalUiGlassSettings provides glassSettings,
        LocalNavigationBarGlassSettings provides navigationBarGlassSettings,
        LocalCardGlassSettings provides cardGlassSettings,
        LocalAppWallpaper provides wallpaper,
    ) {
        MiuixTheme(controller = controller, content = content)
    }
}

internal const val PREF_THEME_MODE = "pref_theme_mode"
internal const val PREF_MONET_ENABLED = "pref_monet_color_enabled"
internal const val PREF_THEME_SEED_COLOR = "pref_theme_seed_color"
internal const val PREF_FLOATING_NAVIGATION_BAR = "pref_floating_navigation_bar"
internal const val PREF_LIQUID_GLASS_NAVIGATION_BAR = "pref_liquid_glass_navigation_bar"
internal const val PREF_PREDICTIVE_BACK_MAX_TRANSLATION = "pref_predictive_back_max_translation"
internal const val DEFAULT_PREDICTIVE_BACK_TRANSLATION_PERCENT = 50L
private const val DEFAULT_THEME_SEED_COLOR = 0xFF6750A4L
