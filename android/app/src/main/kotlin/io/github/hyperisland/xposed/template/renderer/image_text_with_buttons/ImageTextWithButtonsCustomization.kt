package io.github.hyperisland.xposed.renderer.image_text_with_buttons

import io.github.hyperisland.xposed.renderer.RendererCustomizationContributor
import io.github.hyperisland.xposed.renderer.RendererPayload
import io.github.hyperisland.xposed.template.core.contracts.IslandTemplate
import io.github.hyperisland.xposed.template.core.customization.FocusCustomizationApplyEnv
import io.github.hyperisland.xposed.template.core.customization.FocusCustomizationFieldSpec
import org.json.JSONObject

data class ImageTextWithButtonsPayload(
    val action1BgColor: String? = null,
    val action1BgColorDark: String? = null,
    val action1TitleColor: String? = null,
    val action1TitleColorDark: String? = null,
    val action2BgColor: String? = null,
    val action2BgColorDark: String? = null,
    val action2TitleColor: String? = null,
    val action2TitleColorDark: String? = null,
) : RendererPayload

object ImageTextWithButtonsCustomization : RendererCustomizationContributor {
    const val SLOT_ACTION_1_BG_COLOR = "action_1_bg_color"
    const val SLOT_ACTION_1_BG_COLOR_DARK = "action_1_bg_color_dark"
    const val SLOT_ACTION_1_TITLE_COLOR = "action_1_title_color"
    const val SLOT_ACTION_1_TITLE_COLOR_DARK = "action_1_title_color_dark"
    const val SLOT_ACTION_2_BG_COLOR = "action_2_bg_color"
    const val SLOT_ACTION_2_BG_COLOR_DARK = "action_2_bg_color_dark"
    const val SLOT_ACTION_2_TITLE_COLOR = "action_2_title_color"
    const val SLOT_ACTION_2_TITLE_COLOR_DARK = "action_2_title_color_dark"

    private const val KEY_ACTION_1_BG_COLOR = "action_1_bg_color"
    private const val KEY_ACTION_1_BG_COLOR_DARK = "action_1_bg_color_dark"
    private const val KEY_ACTION_1_TITLE_COLOR = "action_1_title_color"
    private const val KEY_ACTION_1_TITLE_COLOR_DARK = "action_1_title_color_dark"
    private const val KEY_ACTION_2_BG_COLOR = "action_2_bg_color"
    private const val KEY_ACTION_2_BG_COLOR_DARK = "action_2_bg_color_dark"
    private const val KEY_ACTION_2_TITLE_COLOR = "action_2_title_color"
    private const val KEY_ACTION_2_TITLE_COLOR_DARK = "action_2_title_color_dark"

    override val fields: List<FocusCustomizationFieldSpec> = listOf(
        colorField(slot = SLOT_ACTION_1_BG_COLOR, key = KEY_ACTION_1_BG_COLOR),
        colorField(slot = SLOT_ACTION_1_BG_COLOR_DARK, key = KEY_ACTION_1_BG_COLOR_DARK),
        colorField(slot = SLOT_ACTION_1_TITLE_COLOR, key = KEY_ACTION_1_TITLE_COLOR),
        colorField(slot = SLOT_ACTION_1_TITLE_COLOR_DARK, key = KEY_ACTION_1_TITLE_COLOR_DARK),
        colorField(slot = SLOT_ACTION_2_BG_COLOR, key = KEY_ACTION_2_BG_COLOR),
        colorField(slot = SLOT_ACTION_2_BG_COLOR_DARK, key = KEY_ACTION_2_BG_COLOR_DARK),
        colorField(slot = SLOT_ACTION_2_TITLE_COLOR, key = KEY_ACTION_2_TITLE_COLOR),
        colorField(slot = SLOT_ACTION_2_TITLE_COLOR_DARK, key = KEY_ACTION_2_TITLE_COLOR_DARK),
    )

    override fun buildPayload(
        config: JSONObject,
        template: IslandTemplate?,
        env: FocusCustomizationApplyEnv,
    ): RendererPayload {
        val action1Bg = readColor(config, KEY_ACTION_1_BG_COLOR, env)
        val action1BgDark = readColor(config, KEY_ACTION_1_BG_COLOR_DARK, env)
        val action1Title = readColor(config, KEY_ACTION_1_TITLE_COLOR, env)
        val action1TitleDark = readColor(config, KEY_ACTION_1_TITLE_COLOR_DARK, env)
        val action2Bg = readColor(config, KEY_ACTION_2_BG_COLOR, env)
        val action2BgDark = readColor(config, KEY_ACTION_2_BG_COLOR_DARK, env)
        val action2Title = readColor(config, KEY_ACTION_2_TITLE_COLOR, env)
        val action2TitleDark = readColor(config, KEY_ACTION_2_TITLE_COLOR_DARK, env)

        return ImageTextWithButtonsPayload(
            action1BgColor = action1Bg,
            action1BgColorDark = action1BgDark,
            action1TitleColor = action1Title,
            action1TitleColorDark = action1TitleDark,
            action2BgColor = action2Bg,
            action2BgColorDark = action2BgDark,
            action2TitleColor = action2Title,
            action2TitleColorDark = action2TitleDark,
        )
    }

    private fun readColor(config: JSONObject, key: String, env: FocusCustomizationApplyEnv): String? {
        val raw = config.optString(key, "").trim()
        if (raw.isEmpty()) return null
        return env.normalizeColor(raw)
    }

    private fun colorField(slot: String, key: String): FocusCustomizationFieldSpec {
        return FocusCustomizationFieldSpec(
            slot = slot,
            key = key,
            label = key,
            type = "color",
            applier = { _, _, vm -> vm },
        )
    }
}
