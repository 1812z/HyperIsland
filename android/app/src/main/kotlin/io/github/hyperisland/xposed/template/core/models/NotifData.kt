package io.github.hyperisland.xposed.template.core.models

import android.app.Notification
import android.graphics.drawable.Icon

/**
 * GenericProgressHook 从通知提取的结构化数据，供各模板统一接收。
 */
data class NotifData(
    val pkg: String,
    val channelId: String,
    val notifId: Int,
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
    /** 超级岛区域图标来源（bigIslandArea / smallIslandArea）: "auto" / "notif_small" / "notif_large" / "app_icon" */
    val iconMode: String = "auto",
    /** 焦点通知（island 块）: "default" / "off" */
    val focusNotif: String = "default",
    /** 是否保留状态栏左上角小图标: "default" / "on" / "off" */
    val preserveStatusBarSmallIcon: String = "default",
    /** 初次自动展开 islandFirstFloat: "default" / "on" / "off" */
    val firstFloat: String = "default",
    /** 更新时自动展开 enableFloat: "default" / "on" / "off" */
    val enableFloatMode: String = "default",
    /** 超级岛自动消失时间，默认 5 */
    val islandTimeout: Int = 5,
    /** 是否显示大岛图标（小岛不受影响）: "default" / "on" / "off" */
    val showIslandIcon: String = "default",
    /** 是否为实时（持续）通知，对应 Notification.FLAG_ONGOING_EVENT */
    val isOngoing: Boolean = false,
    /** 原通知的点击动作，用于代发通知时还原点击行为。 */
    val contentIntent: android.app.PendingIntent? = null,
    /** 渲染器（样式）标识符，对应 ImageTextWithButtonsRenderer.RENDERER_ID 等。 */
    val renderer: String = "image_text_with_buttons_4",
    /** 岛边框高亮颜色，十六进制字符串如 "#E040FB"，null 表示不设置（使用默认颜色）。 */
    val highlightColor: String? = null,
    /** 大岛左侧文本是否显示高亮颜色。 */
    val showLeftHighlightColor: Boolean = false,
    /** 大岛右侧文本是否显示高亮颜色。 */
    val showRightHighlightColor: Boolean = false,
    /** 大岛左侧文本是否使用窄字体。 */
    val showLeftNarrowFont: Boolean = false,
    /** 大岛右侧文本是否使用窄字体。 */
    val showRightNarrowFont: Boolean = false,
    /** 是否开启大岛外圈光效（outEffectSrc=outer_glow）。 */
    val outerGlow: Boolean = false,
    /** 焦点通知自定义配置（JSON 字符串），由模板占位符 + 渲染器槽位共同决定。 */
    val focusCustomizationJson: String? = null,
    /** 超级岛文本自定义配置（JSON 字符串），仅控制左右文本。 */
    val islandCustomizationJson: String? = null,
)
