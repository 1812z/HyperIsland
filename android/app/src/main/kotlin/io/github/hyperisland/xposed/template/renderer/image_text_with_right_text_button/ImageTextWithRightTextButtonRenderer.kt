package io.github.hyperisland.xposed.renderer.image_text_with_right_text_button

import android.content.Context
import android.os.Bundle
import io.github.hyperisland.xposed.renderer.IslandRenderer
import io.github.hyperisland.xposed.renderer.RendererContext
import io.github.hyperisland.xposed.renderer.image_text_with_buttons.ImageTextWithButtonsCustomization
import io.github.hyperisland.xposed.renderer.image_text_with_buttons.ImageTextWithButtonsRenderer
import io.github.hyperisland.xposed.template.core.customization.FocusCustomizationFieldSpec

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
    override val customizationContributor = ImageTextWithButtonsCustomization

    override fun render(context: Context, extras: Bundle, ctx: RendererContext) {
        ImageTextWithButtonsRenderer.renderWith(context, extras, ctx, applyWrap = false, maxButtons = 1, useActionsButton = true)
    }
}
