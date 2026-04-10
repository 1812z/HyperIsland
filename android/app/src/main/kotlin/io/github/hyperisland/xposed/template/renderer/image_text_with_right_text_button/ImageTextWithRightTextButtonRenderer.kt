package io.github.hyperisland.xposed.renderer.image_text_with_right_text_button

import android.content.Context
import android.os.Bundle
import io.github.hyperisland.xposed.renderer.IslandRenderer
import io.github.hyperisland.xposed.renderer.RendererCustomizationContributor
import io.github.hyperisland.xposed.renderer.RendererContext
import io.github.hyperisland.xposed.renderer.RendererPayload
import io.github.hyperisland.xposed.renderer.image_text_with_buttons.ImageTextWithButtonsCustomization
import io.github.hyperisland.xposed.renderer.image_text_with_buttons.ImageTextWithButtonsPayload
import io.github.hyperisland.xposed.renderer.image_text_with_buttons.ImageTextWithButtonsRenderer
import io.github.hyperisland.xposed.template.core.contracts.IslandTemplate
import io.github.hyperisland.xposed.template.core.customization.FocusCustomizationApplyEnv
import io.github.hyperisland.xposed.template.core.customization.FocusCustomizationFieldSpec
import org.json.JSONObject

/**
 * 新图文组件+右侧文本按钮 渲染器。
 *
 * 使用按钮组件1 (actions) type=2（文字按钮），取第一个 action，按钮显示在焦点通知右侧。
 * 与 [ImageTextWithButtonsRenderer] 使用的按钮组件4 (textButton) 不同：
 * type=2 文字按钮通过 addAction() 写入 actions 数组，无需图标，仅支持 1 个。
 */
object ImageTextWithRightTextButtonRenderer : IslandRenderer {

    const val RENDERER_ID = "image_text_with_right_text_button"

    override val id = RENDERER_ID
    override val focusCustomizationFields: List<FocusCustomizationFieldSpec> =
        ImageTextWithButtonsRenderer.focusCustomizationFields
    override val customizationContributor = ImageTextWithRightTextButtonCustomization

    override fun render(context: Context, extras: Bundle, ctx: RendererContext) {
        ImageTextWithButtonsRenderer.renderWith(context, extras, ctx, applyWrap = false, maxButtons = 1, useActionsButton = true)
    }
}

object ImageTextWithRightTextButtonCustomization : RendererCustomizationContributor {
    private const val KEY_ACTION_1_BG_COLOR = "action_1_bg_color"
    private const val KEY_ACTION_1_BG_COLOR_DARK = "action_1_bg_color_dark"
    private const val KEY_ACTION_1_TITLE_COLOR = "action_1_title_color"
    private const val KEY_ACTION_1_TITLE_COLOR_DARK = "action_1_title_color_dark"

    override val fields: List<FocusCustomizationFieldSpec> = listOf(
        colorField(slot = ImageTextWithButtonsCustomization.SLOT_ACTION_1_BG_COLOR, key = KEY_ACTION_1_BG_COLOR),
        colorField(slot = ImageTextWithButtonsCustomization.SLOT_ACTION_1_BG_COLOR_DARK, key = KEY_ACTION_1_BG_COLOR_DARK),
        colorField(slot = ImageTextWithButtonsCustomization.SLOT_ACTION_1_TITLE_COLOR, key = KEY_ACTION_1_TITLE_COLOR),
        colorField(slot = ImageTextWithButtonsCustomization.SLOT_ACTION_1_TITLE_COLOR_DARK, key = KEY_ACTION_1_TITLE_COLOR_DARK),
    )

    override fun buildPayload(
        config: JSONObject,
        template: IslandTemplate?,
        env: FocusCustomizationApplyEnv,
    ): RendererPayload {
        return ImageTextWithButtonsPayload(
            action1BgColor = readColor(config, KEY_ACTION_1_BG_COLOR, env),
            action1BgColorDark = readColor(config, KEY_ACTION_1_BG_COLOR_DARK, env),
            action1TitleColor = readColor(config, KEY_ACTION_1_TITLE_COLOR, env),
            action1TitleColorDark = readColor(config, KEY_ACTION_1_TITLE_COLOR_DARK, env),
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
