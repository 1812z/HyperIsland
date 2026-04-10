package io.github.hyperisland.xposed.renderer

import android.content.Context
import android.os.Bundle
import io.github.hyperisland.xposed.template.core.models.IslandViewModel

/**
 * 新图文组件+按钮组件4·换行 渲染器。
 *
 * 在 [ImageTextWithButtonsRenderer] 的基础上，额外对焦点通知正文应用
 * 长文本折行样式（[wrapLongTextJson]）：内容超过约 18 个汉字时，
 * 将 iconTextInfo 转为 coverInfo，拆为上下两行显示。
 *
 * 所有布局逻辑与 [ImageTextWithButtonsRenderer] 完全共享，
 * 仅 JSON 后处理步骤不同。
 */
object ImageTextWithButtonsWrapRenderer : IslandRenderer {

    const val RENDERER_ID = "image_text_with_buttons_4_wrap"

    override val id = RENDERER_ID

    override fun render(context: Context, extras: Bundle, vm: IslandViewModel) {
        ImageTextWithButtonsRenderer.renderWith(context, extras, vm, applyWrap = true)
    }
}
