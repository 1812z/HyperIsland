package io.github.hyperisland.utils

/** Scene overrides are transient; the per-state material settings remain untouched. */
internal data class SoftGlassSceneBrightnessConfig(
    val enabled: Boolean = false,
    val whiteSceneBrightness: Long = DEFAULT_BRIGHTNESS,
) {
    fun reflectionLightenOverride(blackStatusBarText: Boolean): Double? =
        if (enabled && blackStatusBarText) brightness(whiteSceneBrightness).toDouble() else null

    companion object {
        const val ENABLED_KEY = "pref_soft_glass_scene_dim_enabled"
        const val BRIGHTNESS_KEY = "pref_soft_glass_scene_white_brightness"
        const val DEFAULT_BRIGHTNESS = -28L
        const val MIN_BRIGHTNESS = -50L
        const val MAX_BRIGHTNESS = 50L

        fun brightness(value: Long): Long = value.coerceIn(MIN_BRIGHTNESS, MAX_BRIGHTNESS)
    }
}
