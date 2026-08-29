package io.github.hyperisland.compose.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    DisposableEffect(repository) {
        val unregister = repository.addChangeListener { key ->
            if (key == PREF_THEME_MODE) {
                themeMode = repository.getString(PREF_THEME_MODE, "system")
            }
        }
        onDispose(unregister)
    }
    val controller = remember(themeMode) {
        ThemeController(
            colorSchemeMode = when (themeMode) {
                "light" -> ColorSchemeMode.Light
                "dark" -> ColorSchemeMode.Dark
                else -> ColorSchemeMode.System
            },
        )
    }
    MiuixTheme(controller = controller, content = content)
}

internal const val PREF_THEME_MODE = "pref_theme_mode"
