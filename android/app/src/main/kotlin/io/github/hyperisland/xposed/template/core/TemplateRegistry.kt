package io.github.hyperisland.xposed.template.core

import android.content.Context
import android.os.Bundle
import io.github.hyperisland.xposed.logWarn
import io.github.hyperisland.xposed.template.core.contracts.IslandTemplate
import io.github.hyperisland.xposed.template.core.filters.BlacklistFilter
import io.github.hyperisland.xposed.template.core.models.NotifData
import io.github.hyperisland.xposed.templates.AINotificationIslandNotification
import io.github.hyperisland.xposed.templates.DownloadLiteIslandNotification
import io.github.hyperisland.xposed.templates.GenericDownloadIslandNotification
import io.github.hyperisland.xposed.templates.NotificationIslandLiteNotification
import io.github.hyperisland.xposed.templates.NotificationIslandNotification
/**
 * 模板注册表。
 *
 * 将模板 ID 映射到对应的 [IslandTemplate] 实现；
 * 未知 ID 时自动降级到 [GenericDownloadIslandNotification]。
 *
 * 新增模板只需在 [registry] 中添加一行，不改动 Hook 代码。
 */
object TemplateRegistry {

    private const val TAG = "HyperIsland[TemplateRegistry]"

    private val registry: Map<String, IslandTemplate> = listOf<IslandTemplate>(
        GenericDownloadIslandNotification,
        NotificationIslandNotification,
        NotificationIslandLiteNotification,
        DownloadLiteIslandNotification,
        AINotificationIslandNotification,
    ).associateBy { it.id }

    fun dispatch(
        templateId: String,
        context: Context,
        extras: Bundle,
        data: NotifData,
    ) {
        val template = registry[templateId]
        if (template == null) {
            logWarn("$TAG: unknown template '$templateId', skipped")
            return
        }
        // 通知进入黑名单处理
        val filteredData = BlacklistFilter.applyTo(context, data) ?: return
        template.inject(context, extras, filteredData)
    }
}
