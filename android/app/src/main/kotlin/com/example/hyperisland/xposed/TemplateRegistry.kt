package com.example.hyperisland.xposed

import android.content.Context
import android.os.Bundle
import com.example.hyperisland.xposed.templates.GenericProgressIslandNotification
import de.robv.android.xposed.XposedBridge

/**
 * 模板注册表。
 *
 * 将模板 ID 映射到对应的 [IslandTemplate] 实现；
 * 未知 ID 时自动降级到 [GenericProgressIslandNotification]。
 *
 * 新增模板只需在 [registry] 中添加一行，不改动 Hook 代码。
 */
object TemplateRegistry {

    private val registry: Map<String, IslandTemplate> = listOf<IslandTemplate>(
        GenericProgressIslandNotification,
    ).associateBy { it.id }

    /** 返回所有已注册模板的元数据，与 [registeredTemplates] 来源相同。 */
    fun getAll(): List<Map<String, String>> =
        registry.values.map { mapOf("id" to it.id, "name" to it.displayName) }

    fun dispatch(
        templateId: String,
        context: Context,
        extras: Bundle,
        data: NotifData,
    ) {
        val template = registry[templateId]
        if (template == null) {
            XposedBridge.log(
                "HyperIsland[Registry]: unknown template '$templateId', skipped"
            )
            return
        }
        template.inject(context, extras, data)
    }
}
