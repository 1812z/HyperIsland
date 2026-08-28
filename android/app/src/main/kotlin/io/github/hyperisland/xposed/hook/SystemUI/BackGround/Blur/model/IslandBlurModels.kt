package io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.model

import android.graphics.Color
import io.github.hyperisland.xposed.hook.SystemUI.SoftGlass.SoftGlassConfig

internal enum class IslandType { SMALL, BIG, EXPAND }

internal enum class MaterialType {
    DEFAULT,
    GAUSSIAN,
    HIGHLIGHT,
    LIQUID,
    SOFT;

    companion object {
        fun fromValue(value: String): MaterialType = when (value) {
            "gaussian" -> GAUSSIAN
            "highlight_glass" -> HIGHLIGHT
            "liquid_glass" -> LIQUID
            "soft_glass" -> SOFT
            else -> DEFAULT
        }
    }
}

internal class BlurConfig(
    enabled: Boolean,
    val radius: Int,
    val blendColor: Int,
) {
    val isActive = enabled
}

internal data class MaterialConfig(
    val type: MaterialType,
    val blur: Int,
    val softGlass: SoftGlassConfig,
    val darker: Int,
    val refraction: Int,
    val edgeThickness: Int,
    val reflectionStrength: Int,
    val lightDirection: Int,
    val dispersion: Int,
    val blendColor: Int,
    val highlight: Boolean,
) {
    val isCustom get() = type != MaterialType.DEFAULT

    fun toBlurConfig() = BlurConfig(
        type == MaterialType.GAUSSIAN ||
            type == MaterialType.HIGHLIGHT ||
            type == MaterialType.LIQUID,
        blur,
        blendColor,
    )

    fun softFallback() = BlurConfig(true, blur, blendColor)

    companion object {
        fun default() = MaterialConfig(
            MaterialType.DEFAULT,
            35,
            SoftGlassConfig.default(),
            14,
            16,
            16,
            42,
            243,
            18,
            Color.TRANSPARENT,
            true,
        )
    }
}

internal data class MaterialConfigs(
    val small: MaterialConfig,
    val big: MaterialConfig,
    val expand: MaterialConfig,
) {
    val anyCustom get() = small.isCustom || big.isCustom || expand.isCustom

    fun forType(type: IslandType): MaterialConfig = when (type) {
        IslandType.SMALL -> small
        IslandType.BIG -> big
        IslandType.EXPAND -> expand
    }

    companion object {
        fun defaults(): MaterialConfigs {
            val value = MaterialConfig.default()
            return MaterialConfigs(value, value, value)
        }

        fun fromLegacy(configs: BlurConfigs, states: GlassStates): MaterialConfigs {
            fun convert(blur: BlurConfig, glass: GlassState): MaterialConfig {
                if (!blur.isActive) return MaterialConfig.default()
                val type = when {
                    glass.refractionEnabled -> MaterialType.LIQUID
                    glass.enabled -> MaterialType.HIGHLIGHT
                    else -> MaterialType.GAUSSIAN
                }
                return MaterialConfig.default().copy(
                    type = type,
                    blur = blur.radius,
                    blendColor = blur.blendColor,
                )
            }
            return MaterialConfigs(
                convert(configs.small, states.small),
                convert(configs.big, states.big),
                convert(configs.expand, states.expand),
            )
        }
    }
}

internal data class BlurConfigs(
    val small: BlurConfig,
    val big: BlurConfig,
    val expand: BlurConfig,
) {
    fun forType(type: IslandType): BlurConfig = when (type) {
        IslandType.SMALL -> small
        IslandType.BIG -> big
        IslandType.EXPAND -> expand
    }

    companion object {
        fun disabled(): BlurConfigs {
            val disabled = BlurConfig(false, 80, 0x20FFFFFF)
            return BlurConfigs(disabled, disabled, disabled)
        }
    }
}

internal data class GlassState(
    val enabled: Boolean,
    val refractionEnabled: Boolean,
)

internal data class GlassStates(
    val small: GlassState,
    val big: GlassState,
    val expand: GlassState,
) {
    val anyEnabled = small.enabled || big.enabled || expand.enabled
    val anyRefractionEnabled = small.refractionEnabled ||
        big.refractionEnabled || expand.refractionEnabled

    fun forType(type: IslandType): GlassState = when (type) {
        IslandType.SMALL -> small
        IslandType.BIG -> big
        IslandType.EXPAND -> expand
    }

    companion object {
        fun disabled(): GlassStates {
            val disabled = GlassState(false, false)
            return GlassStates(disabled, disabled, disabled)
        }
    }
}
