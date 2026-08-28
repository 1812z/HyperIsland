package io.github.hyperisland.xposed.hook.SystemUI.SoftGlass

import org.json.JSONObject

/** Parameters understood by HyperOS 4's native Bionics soft-glass renderer. */
internal data class SoftGlassConfig(
    val blurRadius: Int,
    val softLight: Double,
    val saturation: Double,
    val brightness: Double,
    val darker: Double,
    val transparency: Double,
    val burn: Double,
    val refraction: Double,
    val edgeThickness: Double,
    val reflection: Double,
    val directionalLightIntensity: Double,
    val backgroundSaturation: Double,
    val backgroundBrightness: Double,
    val tintColor: Int,
    val highlight: Boolean,
) {
    companion object {
        fun fromJson(
            json: JSONObject,
            blurRadius: Int,
            tintColor: Int,
            highlight: Boolean,
        ): SoftGlassConfig {
            val softSchema = json.optInt("softSchema", 0)
            val hasRelativeSoftParams = softSchema >= 2
            fun value(name: String, fallback: Double): Double = if (hasRelativeSoftParams) {
                json.optDouble(name, fallback).coerceIn(-50.0, 50.0)
            } else {
                fallback
            }
            return SoftGlassConfig(
                blurRadius = blurRadius,
                softLight = value("softLight", -1.0),
                // Schema 2 was relative to Xiaomi's 2.4 island token. Reset it to the new
                // neutral-centered scale instead of carrying the old over-saturation forward.
                saturation = if (softSchema >= 3) value("saturation", 0.0) else 0.0,
                brightness = if (softSchema >= 3) value("brightness", 0.0) else 0.0,
                darker = if (softSchema >= 3) value("softDarker", 0.0) else 0.0,
                transparency = value("transparency", -0.57),
                burn = value("burn", 0.0),
                refraction = value("softRefraction", 0.0),
                edgeThickness = value("softEdgeThickness", 0.8),
                reflection = value("softReflection", 0.0),
                directionalLightIntensity = value("directionalLightIntensity", 1.0),
                backgroundSaturation = value("backgroundSaturation", 0.0),
                backgroundBrightness = value("backgroundBrightness", 0.04),
                tintColor = tintColor,
                highlight = highlight,
            )
        }

        fun default() = fromJson(
            json = JSONObject(),
            blurRadius = 35,
            tintColor = 0,
            highlight = true,
        )
    }
}
