package io.github.hyperisland.xposed.template.core.models

import android.app.Notification
import android.graphics.drawable.Icon

/**
 * 模板处理后的标准化视图模型，由渲染器消费以构建超级岛 UI。
 *
 * 模板只负责将 [NotifData] 处理为此对象（消息处理层）；
 * 渲染器只负责将此对象转为 JSON 注入 extras（渲染层）。
 */
data class IslandViewModel(
    // ── 模板标识（渲染器用于生成唯一 key）────────────────────────────────────
    val templateId: String = "island",

    // ── 大岛文字内容 ────────────────────────────────────────────────────────
    /** 大岛左侧文字（状态标签 / 通知标题 / 空字符串 = 不显示文字）。 */
    val leftTitle: String = "",
    /** 大岛右侧文字（文件名 / 内容 / 空字符串）。 */
    val rightTitle: String = "",

    // ── 焦点通知内容 ────────────────────────────────────────────────────────
    val focusTitle: String,
    val focusContent: String,

    // ── 已解析图标（渲染器负责注册 key）─────────────────────────────────────
    /** 超级岛区域图标（小岛 + 大岛左侧图标）。 */
    val islandIcon: Icon,
    /** 焦点通知图标（iconTextInfo 区域）。 */
    val focusIcon: Icon,

    // ── 可选环形进度 ────────────────────────────────────────────────────────
    /** 进度值 0–100；非 null 时大岛右侧显示环形进度，同时小岛显示进度环。 */
    val circularProgress: Int? = null,
    /** 进度条颜色（#RRGGBB / #AARRGGBB），用于 progressInfo。 */
    val progressColor: String? = null,

    // ── 动作按钮 ────────────────────────────────────────────────────────────
    val actions: List<Notification.Action> = emptyList(),

    // ── 渲染行为控制 ────────────────────────────────────────────────────────
    /** param_v2.updatable 值；下载模板 = !isComplete，通知模板 = isOngoing。 */
    val updatable: Boolean = false,
    val showNotification: Boolean = true,
    /** true 时写入 hyperisland_focus_proxy = true（通知类模板使用）。 */
    val setFocusProxy: Boolean = false,
    val preserveStatusBarSmallIcon: Boolean = false,
    val firstFloat: Boolean = false,
    val enableFloat: Boolean = false,
    val timeoutSecs: Int = 5,
    val isOngoing: Boolean = false,
    val showIslandIcon: Boolean = true,
    /** 岛边框高亮颜色，十六进制字符串如 "#E040FB"，null 表示不设置。 */
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
    /** 是否开启超级岛大岛态外圈光效。 */
    val islandOuterGlow: Boolean = false,
    /** 超级岛外圈光效颜色，支持 #RRGGBB / #AARRGGBB。 */
    val islandOuterGlowColor: String? = null,
    /** 大岛外圈光效颜色，十六进制字符串如 "#E040FB"，null 表示不设置。 */
    val outEffectColor: String? = null,
)
