package io.github.hyperisland.compose.theme

import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
internal fun HyperIslandTheme(
    repository: FlutterPrefsRepository,
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
    MiuixTheme(controller = controller, content = content)
}

internal const val PREF_THEME_MODE = "pref_theme_mode"
internal const val PREF_MONET_ENABLED = "pref_monet_color_enabled"
internal const val PREF_THEME_SEED_COLOR = "pref_theme_seed_color"
internal const val PREF_FLOATING_NAVIGATION_BAR = "pref_floating_navigation_bar"
internal const val PREF_BLUR_BARS = "pref_blur_bars"
private const val DEFAULT_THEME_SEED_COLOR = 0xFF6750A4L
