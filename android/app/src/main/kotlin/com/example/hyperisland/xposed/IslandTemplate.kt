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
    /** 通知小图标（smallIcon）。 */
    val notifIcon: Icon?,
    /** 通知大图标（头像、封面等）。 */
    val largeIcon: Icon?,
    /** 应用图标（来自 PackageManager）。 */
    val appIconRaw: Icon? = null,
    // ── 渠道级覆盖设置 ────────────────────────────────────────────────────────
    /** 图标来源："auto" / "notif_small" / "notif_large" / "app_icon" */
    val iconMode: String = "auto",
    /** 焦点通知（island 块）："default" / "off" */
    val focusNotif: String = "default",
    /** 初次自动展开 islandFirstFloat："default" / "on" / "off" */
    val firstFloat: String = "default",
    /** 更新时自动展开 enableFloat："default" / "on" / "off" */
    val enableFloatMode: String = "default",
)
