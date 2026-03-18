package io.github.hyperisland.xposed

import android.content.Context
import io.github.hyperisland.R
import io.github.hyperisland.xposed.templates.GenericProgressIslandNotification
import io.github.hyperisland.xposed.templates.NotificationIslandNotification

/**
 * 可供 Flutter 读取的模板元数据列表（无 Xposed 依赖）。
 *
 * 新增模板时，在此添加一行。模板 ID 来自各模板文件的 const 常量（编译器内联，
 * 不会在普通 App 进程中触发 Xposed 类加载）；显示名称从字符串资源加载以支持多语言。
 */
fun getRegisteredTemplates(context: Context): List<Map<String, String>> = listOf(
    mapOf(
        "id"   to GenericProgressIslandNotification.TEMPLATE_ID,
        "name" to context.getString(R.string.template_download_name),
    ),
    mapOf(
        "id"   to NotificationIslandNotification.TEMPLATE_ID,
        "name" to context.getString(R.string.template_notification_island_name),
    ),
)
