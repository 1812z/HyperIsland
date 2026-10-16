package io.github.hyperisland.xposed.renderer.base_text_with_progress

import io.github.hyperisland.xposed.renderer.RendererPayload
import io.github.hyperisland.xposed.renderer.RendererCustomizationContributor
import io.github.hyperisland.xposed.template.core.contracts.IslandTemplate
import io.github.hyperisland.xposed.template.core.customization.FocusCustomizationApplyEnv
import io.github.hyperisland.xposed.template.core.customization.FocusCustomizationFieldSpec
import org.json.JSONObject

data class BaseTextWithProgressPayload(
    val progressBarColor: String = "#34C759",
    val progressBarColorEnd: String = "#30B0C7",
) : RendererPayload

object BaseTextWithProgressCustomization : RendererCustomizationContributor {
    const val SLOT_PROGRESS_BAR_COLOR = "base_text_progress_bar_color"
    const val SLOT_PROGRESS_BAR_COLOR_END = "base_text_progress_bar_color_end"

    private const val KEY_PROGRESS_BAR_COLOR = "progress_bar_color"
    private const val KEY_PROGRESS_BAR_COLOR_END = "progress_bar_color_end"

    override val fields: List<FocusCustomizationFieldSpec> = listOf(
        colorField(
            slot = SLOT_PROGRESS_BAR_COLOR,
            key = KEY_PROGRESS_BAR_COLOR,
            defaultValue = "#34C759",
        ),
        colorField(
            slot = SLOT_PROGRESS_BAR_COLOR_END,
            key = KEY_PROGRESS_BAR_COLOR_END,
            defaultValue = "#30B0C7",
        ),
    )

    override fun buildPayload(config: JSONObject, template: IslandTemplate?, env: FocusCustomizationApplyEnv): RendererPayload {
        val progressBarColor = readColor(config, KEY_PROGRESS_BAR_COLOR, "#34C759", env)
        val progressBarColorEnd = readColor(config, KEY_PROGRESS_BAR_COLOR_END, "#30B0C7", env)
        return BaseTextWithProgressPayload(
            progressBarColor = progressBarColor,
            progressBarColorEnd = progressBarColorEnd,
        )
    }

    private fun readColor(config: JSONObject, key: String, defaultValue: String, env: FocusCustomizationApplyEnv): String {
        val raw = config.optString(key, defaultValue).trim()
        val value = raw.ifEmpty { defaultValue }
        return env.normalizeColor(value) ?: defaultValue
    }

    private fun colorField(slot: String, key: String, defaultValue: String): FocusCustomizationFieldSpec {
        return FocusCustomizationFieldSpec(
            slot = slot,
            key = key,
            label = key,
            type = "color",
            defaultProvider = { defaultValue },
            applier = { _, _, vm -> vm },
        )
    }
}