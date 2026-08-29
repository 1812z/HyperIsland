package io.github.hyperisland.xposed.template.core.contracts

import android.content.Context
import android.os.Bundle
import io.github.hyperisland.xposed.template.core.models.IslandViewModel
import io.github.hyperisland.xposed.template.core.models.NotifData

data class TemplatePlaceholder(
    val key: String,
    val label: String = key,
)

/**
 * 灵动岛通知模板接口。
 *
 * 新增模板步骤：
 *  1. 创建 object 实现此接口，id 与 Compose 配置常量对应
 *  2. 在 TemplateRegistry.registry 中添加一行
 */
interface IslandTemplate {
    /** 唯一标识符，与 Compose 侧模板常量对应。 */
    val id: String

    /** 模板可向用户暴露的表达式占位符。 */
    val expressionPlaceholders: List<TemplatePlaceholder>
        get() = listOf(
            TemplatePlaceholder("title"),
            TemplatePlaceholder("subtitle"),
            TemplatePlaceholder("subtitle_or_title"),
            TemplatePlaceholder("raw_title"),
            TemplatePlaceholder("raw_subtitle"),
            TemplatePlaceholder("raw_subtitle_or_title"),
            TemplatePlaceholder("pkg"),
            TemplatePlaceholder("channel_id"),
        )

    /** 焦点高级自定义可用的扩展占位符。 */
    val focusExpressionPlaceholders: List<TemplatePlaceholder>
        get() = emptyList()

    /** 超级岛高级自定义可用的扩展占位符。 */
    val islandExpressionPlaceholders: List<TemplatePlaceholder>
        get() = emptyList()

    /** 焦点标题默认表达式。 */
    val defaultFocusTitleExpr: String
        get() = "${'$'}{focus_title}"

    /** 焦点正文默认表达式。 */
    val defaultFocusContentExpr: String
        get() = "${'$'}{focus_content}"

    /** 超级岛左侧默认表达式。 */
    val defaultIslandLeftExpr: String
        get() = "${'$'}{left_title}"

    /** 超级岛右侧默认表达式。 */
    val defaultIslandRightExpr: String
        get() = "${'$'}{right_title}"

    /** 模板可扩展的焦点表达式变量。 */
    fun focusExpressionVars(data: NotifData, vm: IslandViewModel): Map<String, String> = emptyMap()

    /** 模板可扩展的超级岛表达式变量。 */
    fun islandExpressionVars(data: NotifData, vm: IslandViewModel): Map<String, String> = emptyMap()

    /** 将通知数据注入 extras，使其触发灵动岛展示。 */
    fun inject(context: Context, extras: Bundle, data: NotifData)
}
