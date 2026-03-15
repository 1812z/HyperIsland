package com.example.hyperisland.xposed

import android.app.Notification
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Bundle

/**
 * 灵动岛通知模板接口。
 *
 * 新增模板步骤：
 *  1. 创建 object 实现此接口，id 与 Flutter 侧常量对应
 *  2. 在 TemplateRegistry.registry 中添加一行
 */
interface IslandTemplate {
    /** 唯一标识符，与 Flutter 侧 kTemplate* 常量对应。 */
    val id: String

    /** 在 Flutter UI 中显示的模板名称。 */
    val displayName: String

    /** 将通知数据注入 extras，使其触发灵动岛展示。 */
    fun inject(context: Context, extras: Bundle, data: NotifData)
}

/**
 * GenericProgressHook 从通知提取的结构化数据，供各模板统一接收。
 */
data class NotifData(
    val pkg: String,
    val channelId: String,
    val title: String,
    val subtitle: String,
    val progress: Int,
    val actions: List<Notification.Action>,
    /** 通知小图标（来自 smallIcon 或应用图标）。 */
    val notifIcon: Icon?,
    /** 通知大图标（头像、封面、应用图标等），通常比 notifIcon 更具辨识度。 */
    val largeIcon: Icon?,
)
