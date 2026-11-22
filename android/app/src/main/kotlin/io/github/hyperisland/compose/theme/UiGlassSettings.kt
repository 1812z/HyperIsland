package io.github.hyperisland.compose.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.rememberBooleanPreference
import io.github.hyperisland.compose.data.rememberStringPreference
import top.yukonga.miuix.kmp.glass.GlassPopupDefaults
import top.yukonga.miuix.kmp.glass.GlassPopupVisuals
import top.yukonga.miuix.kmp.material.GlassColorLayer
import top.yukonga.miuix.kmp.material.GlassMaterial

internal const val PREF_UI_GLASS_ENABLED = "pref_ui_glass_enabled"
internal const val PREF_UI_GLASS_TRANSPARENCY = "pref_ui_glass_transparency"
internal const val PREF_UI_GLASS_BLUR_RADIUS = "pref_ui_glass_blur_radius"
internal const val PREF_NAVIGATION_BAR_STYLE = "pref_navigation_bar_style"
internal const val DEFAULT_UI_GLASS_ENABLED = true
internal const val DEFAULT_UI_GLASS_TRANSPARENCY = 5L
internal const val DEFAULT_UI_GLASS_BLUR_RADIUS = 30L
internal const val MAX_UI_GLASS_TRANSPARENCY = 100L
internal const val MAX_UI_GLASS_BLUR_RADIUS = 80L

internal enum class NavigationBarStyle(val storedValue: String) {
    Default("default"),
    Os3("os3"),
    Os4("os4"),
    LiquidGlass("liquid_glass"),
}

@Composable
internal fun rememberNavigationBarStyle(prefs: FlutterPrefsRepository): NavigationBarStyle {
    val stored by rememberStringPreference(prefs, PREF_NAVIGATION_BAR_STYLE, "")
    val floating by rememberBooleanPreference(prefs, PREF_FLOATING_NAVIGATION_BAR, false)
    val liquidGlass by rememberBooleanPreference(prefs, PREF_LIQUID_GLASS_NAVIGATION_BAR, false)
    return NavigationBarStyle.entries.firstOrNull { it.storedValue == stored } ?: when {
        floating && liquidGlass -> NavigationBarStyle.LiquidGlass
        floating -> NavigationBarStyle.Os4
        else -> NavigationBarStyle.Default
    }
}

@Stable
internal class UiGlassSettings(
    enabled: Boolean = DEFAULT_UI_GLASS_ENABLED,
    transparency: Long = DEFAULT_UI_GLASS_TRANSPARENCY,
    blurRadius: Long = DEFAULT_UI_GLASS_BLUR_RADIUS,
) {
    var enabled by mutableStateOf(enabled)
    var transparency by mutableLongStateOf(transparency.coerceIn(0, MAX_UI_GLASS_TRANSPARENCY))
    var blurRadius by mutableLongStateOf(blurRadius.coerceIn(0, MAX_UI_GLASS_BLUR_RADIUS))

    val tintStrength: Float get() = 1f - transparency.toFloat() / MAX_UI_GLASS_TRANSPARENCY
    val blurRadiusDp get() = blurRadius.toInt().dp
}

internal val LocalUiGlassSettings = staticCompositionLocalOf { UiGlassSettings() }

@Composable
internal fun rememberUiGlassSettings(prefs: FlutterPrefsRepository): UiGlassSettings {
    val settings = remember(prefs) {
        UiGlassSettings(
            enabled = prefs.getBoolean(PREF_UI_GLASS_ENABLED, DEFAULT_UI_GLASS_ENABLED),
            transparency = prefs.getLong(PREF_UI_GLASS_TRANSPARENCY, DEFAULT_UI_GLASS_TRANSPARENCY),
            blurRadius = prefs.getLong(PREF_UI_GLASS_BLUR_RADIUS, DEFAULT_UI_GLASS_BLUR_RADIUS),
        )
    }
    DisposableEffect(prefs, settings) {
        val unregister = prefs.addChangeListener { key ->
            when (key) {
                PREF_UI_GLASS_ENABLED -> settings.enabled = prefs.getBoolean(key, DEFAULT_UI_GLASS_ENABLED)
                PREF_UI_GLASS_TRANSPARENCY -> settings.transparency =
                    prefs.getLong(key, DEFAULT_UI_GLASS_TRANSPARENCY).coerceIn(0, MAX_UI_GLASS_TRANSPARENCY)
                PREF_UI_GLASS_BLUR_RADIUS -> settings.blurRadius =
                    prefs.getLong(key, DEFAULT_UI_GLASS_BLUR_RADIUS).coerceIn(0, MAX_UI_GLASS_BLUR_RADIUS)
            }
        }
        onDispose(unregister)
    }
    return settings
}

@Composable
internal fun uiGlassMaterial(
    base: GlassMaterial,
    settings: UiGlassSettings = LocalUiGlassSettings.current,
): GlassMaterial {
    val tintStrength = settings.tintStrength
    val blurRadius = settings.blurRadiusDp
    return remember(base, tintStrength, blurRadius) {
        base.copy(
            blurRadius = blurRadius,
            first = base.first.scaleAlpha(tintStrength),
            second = base.second?.scaleAlpha(tintStrength),
            third = base.third?.scaleAlpha(tintStrength),
        )
    }
}

@Composable
internal fun uiGlassPopupVisuals(
    settings: UiGlassSettings = LocalUiGlassSettings.current,
): GlassPopupVisuals = GlassPopupDefaults.visuals(
    material = uiGlassMaterial(GlassPopupDefaults.material(), settings),
    stroke = if (settings.enabled) GlassPopupDefaults.stroke() else null,
)

private fun GlassColorLayer.scaleAlpha(strength: Float): GlassColorLayer =
    copy(color = color.copy(alpha = color.alpha * strength))
