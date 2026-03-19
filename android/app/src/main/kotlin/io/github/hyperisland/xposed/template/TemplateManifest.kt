package io.github.hyperisland.xposed

import android.content.Context
import io.github.hyperisland.R
import io.github.hyperisland.xposed.templates.GenericDownLoadIslandNotificationOld
import io.github.hyperisland.xposed.templates.GenericProgressIslandNotification
import io.github.hyperisland.xposed.templates.NotificationIslandNotification
import io.github.hyperisland.xposed.templates.NotificationIslandNotificationOld

/**
 * 可供 Flutter 读取的模板元数据列表（无 Xposed 依赖）。
 *
 * 根据 Settings.System 中的 notification_focus_protocol 自动过滤：
 *  - 协议版本 1~2（HyperOS 2）：仅返回 OS2 模板
 *  - 协议版本 3+（HyperOS 3）或未知：仅返回 OS3 模板
 */
fun getRegisteredTemplates(context: Context): List<Map<String, String>> {
    val protocol = try {
        android.provider.Settings.System.getInt(
            context.contentResolver, "notification_focus_protocol", 0
        )
    } catch (_: Exception) { 0 }
    val isOs2 = protocol in 1..2

    return if (isOs2) listOf(
        mapOf(
            "id"   to GenericDownLoadIslandNotificationOld.TEMPLATE_ID,
            "name" to context.getString(R.string.template_download_old_name),
        ),
        mapOf(
            "id"   to NotificationIslandNotificationOld.TEMPLATE_ID,
            "name" to context.getString(R.string.template_notification_island_old_name),
        ),
    ) else listOf(
        mapOf(
            "id"   to GenericProgressIslandNotification.TEMPLATE_ID,
            "name" to context.getString(R.string.template_download_name),
        ),
        mapOf(
            "id"   to NotificationIslandNotification.TEMPLATE_ID,
            "name" to context.getString(R.string.template_notification_island_name),
        ),
    )
}
