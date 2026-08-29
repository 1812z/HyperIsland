package io.github.hyperisland.compose.data

import org.json.JSONObject

internal enum class IslandMaterialType(val value: String) {
    Default("default"),
    Gaussian("gaussian"),
    HighlightGlass("highlight_glass"),
    LiquidGlass("liquid_glass"),
    SoftGlass("soft_glass");

    companion object {
        fun fromValue(value: String?): IslandMaterialType = entries.firstOrNull { it.value == value } ?: Default
    }
}

internal enum class IslandMaterialState { Big, Small, Expand }

internal data class IslandMaterialConfig(
    val type: IslandMaterialType = IslandMaterialType.Default,
    val blur: Int = 35,
    val softLight: Double = -1.0,
    val saturation: Double = 0.0,
    val brightness: Double = 0.0,
    val softDarker: Double = 0.0,
    val transparency: Double = -0.57,
    val burn: Double = 0.0,
    val softRefraction: Double = 0.0,
    val softEdgeThickness: Double = 0.8,
    val softReflection: Double = 0.0,
    val directionalLightIntensity: Double = 1.0,
    val backgroundSaturation: Double = 0.0,
    val backgroundBrightness: Double = 0.04,
    val refraction: Int = 16,
    val edgeThickness: Int = 16,
    val reflectionStrength: Int = 42,
    val darker: Int = 14,
    val lightDirection: Int = 243,
    val dispersion: Int = 18,
    val blendColor: String = "#FFFFFF",
    val blendOpacity: Int = 0,
    val highlight: Boolean = true,
) {
    val isCustom: Boolean get() = type != IslandMaterialType.Default

    fun toJson(): JSONObject = JSONObject().apply {
        put("type", type.value)
        put("blur", blur)
        put("softSchema", 3)
        put("softLight", softLight)
        put("saturation", saturation)
        put("brightness", brightness)
        put("softDarker", softDarker)
        put("transparency", transparency)
        put("burn", burn)
        put("softRefraction", softRefraction)
        put("softEdgeThickness", softEdgeThickness)
        put("softReflection", softReflection)
        put("directionalLightIntensity", directionalLightIntensity)
        put("backgroundSaturation", backgroundSaturation)
        put("backgroundBrightness", backgroundBrightness)
        put("refraction", refraction)
        put("edgeThickness", edgeThickness)
        put("reflectionStrength", reflectionStrength)
        put("darker", darker)
        put("lightDirection", lightDirection)
        put("dispersion", dispersion)
        put("blendColor", blendColor)
        put("blendOpacity", blendOpacity)
        put("highlight", highlight)
    }

    companion object {
        fun fromJson(json: JSONObject): IslandMaterialConfig {
            fun integer(key: String, fallback: Int, range: IntRange): Int =
                json.optInt(key, fallback).coerceIn(range)
            fun decimal(key: String, fallback: Double): Double =
                json.optDouble(key, fallback).coerceIn(-50.0, 50.0)
            val softSchema = json.optInt("softSchema", 0)
            val type = IslandMaterialType.fromValue(json.optString("type", "default"))
            fun soft(key: String, fallback: Double): Double =
                if (softSchema >= 2) decimal(key, fallback) else fallback
            return IslandMaterialConfig(
                type = type,
                blur = integer("blur", 35, 0..100),
                softLight = soft("softLight", -1.0),
                saturation = if (softSchema >= 3) soft("saturation", 0.0) else 0.0,
                brightness = if (softSchema >= 3) soft("brightness", 0.0) else 0.0,
                softDarker = if (softSchema >= 3) soft("softDarker", 0.0) else 0.0,
                transparency = soft("transparency", -0.57),
                burn = soft("burn", 0.0),
                softRefraction = soft("softRefraction", 0.0),
                softEdgeThickness = soft("softEdgeThickness", 0.8),
                softReflection = soft("softReflection", 0.0),
                directionalLightIntensity = soft("directionalLightIntensity", 1.0),
                backgroundSaturation = soft("backgroundSaturation", 0.0),
                backgroundBrightness = soft("backgroundBrightness", 0.04),
                refraction = integer("refraction", 16, 0..40),
                edgeThickness = integer("edgeThickness", 16, 4..40),
                reflectionStrength = integer("reflectionStrength", 42, 0..100),
                darker = integer("darker", 14, 0..100),
                lightDirection = integer("lightDirection", 243, 0..359),
                dispersion = integer("dispersion", 18, 0..100),
                blendColor = json.optString("blendColor", "#FFFFFF").trim(),
                blendOpacity = if (softSchema < 2 && type == IslandMaterialType.SoftGlass) 0
                    else integer("blendOpacity", 0, 0..100),
                highlight = json.optBoolean("highlight", true),
            )
        }

        fun decode(raw: String): IslandMaterialConfig = runCatching {
            fromJson(JSONObject(raw))
        }.getOrDefault(IslandMaterialConfig())
    }
}

internal data class IslandMaterialSettings(
    val big: IslandMaterialConfig,
    val small: IslandMaterialConfig,
    val expand: IslandMaterialConfig,
    val smallFollowBig: Boolean,
    val expandFollowBig: Boolean,
) {
    fun config(state: IslandMaterialState): IslandMaterialConfig = when (state) {
        IslandMaterialState.Big -> big
        IslandMaterialState.Small -> small
        IslandMaterialState.Expand -> expand
    }

    fun effective(state: IslandMaterialState): IslandMaterialConfig = when (state) {
        IslandMaterialState.Big -> big
        IslandMaterialState.Small -> if (smallFollowBig) big else small
        IslandMaterialState.Expand -> if (expandFollowBig) big else expand
    }

    fun followsBig(state: IslandMaterialState): Boolean = when (state) {
        IslandMaterialState.Big -> false
        IslandMaterialState.Small -> smallFollowBig
        IslandMaterialState.Expand -> expandFollowBig
    }
}
