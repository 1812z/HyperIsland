package io.github.hyperisland.xposed

import android.content.Context
import android.os.Bundle
import io.github.hyperisland.xposed.templates.AINotificationIslandNotification
import io.github.hyperisland.xposed.templates.DownloadLiteIslandNotification
import io.github.hyperisland.xposed.templates.GenericProgressIslandNotification
import io.github.hyperisland.xposed.templates.NotificationIslandLiteNotification
import io.github.hyperisland.xposed.templates.NotificationIslandNotification
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
            XposedBridge.log(
                "HyperIsland[Registry]: unknown template '$templateId', skipped"
            )
            return
        }
        // 通知进入黑名单处理
        val filteredData = BlacklistFilter.applyTo(context, data) ?: return
        template.inject(context, extras, filteredData)
    }
}
