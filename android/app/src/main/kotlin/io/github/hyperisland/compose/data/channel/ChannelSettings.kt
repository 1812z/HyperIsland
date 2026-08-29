package io.github.hyperisland.compose.data.channel

internal data class ChannelSettings(
    val template: String = TEMPLATE_NOTIFICATION,
    val renderer: String = RENDERER_IMAGE_TEXT_BUTTONS,
    val iconMode: String = ICON_AUTO,
    val focus: String = OPTION_DEFAULT,
    val showNotification: String = OPTION_ON,
    val preserveSmallIcon: String = OPTION_DEFAULT,
    val showIslandIcon: String = OPTION_DEFAULT,
    val firstFloat: String = OPTION_DEFAULT,
    val enableFloat: String = OPTION_DEFAULT,
    val timeout: String = OPTION_DEFAULT,
    val marquee: String = OPTION_DEFAULT,
    val marqueeAutoHide: String = OPTION_DEFAULT,
    val restoreLockscreen: String = OPTION_DEFAULT,
    val highlightColor: String = "",
    val dynamicHighlightColor: String = OPTION_DEFAULT,
    val showLeftHighlight: String = OPTION_OFF,
    val showRightHighlight: String = OPTION_OFF,
    val showLeftNarrowFont: String = OPTION_OFF,
    val showRightNarrowFont: String = OPTION_OFF,
    val outerGlow: String = OPTION_DEFAULT,
    val islandOuterGlow: String = OPTION_DEFAULT,
    val islandOuterGlowColor: String = "",
    val outEffectColor: String = "",
    val focusCustom: String = "",
    val islandCustom: String = "",
    val aodText: String = OPTION_DEFAULT,
    val aodCustom: String = "",
    val filterMode: String = FILTER_BLACKLIST,
    val whitelistKeywords: List<String> = emptyList(),
    val blacklistKeywords: List<String> = emptyList(),
    val islandEnabled: Boolean = true,
)

internal data class ChannelSettingsPatch(
    val template: String? = null,
    val renderer: String? = null,
    val iconMode: String? = null,
    val focus: String? = null,
    val showNotification: String? = null,
    val preserveSmallIcon: String? = null,
    val showIslandIcon: String? = null,
    val firstFloat: String? = null,
    val enableFloat: String? = null,
    val timeout: String? = null,
    val marquee: String? = null,
    val marqueeAutoHide: String? = null,
    val restoreLockscreen: String? = null,
    val highlightColor: String? = null,
    val dynamicHighlightColor: String? = null,
    val showLeftHighlight: String? = null,
    val showRightHighlight: String? = null,
    val showLeftNarrowFont: String? = null,
    val showRightNarrowFont: String? = null,
    val outerGlow: String? = null,
    val islandOuterGlow: String? = null,
    val islandOuterGlowColor: String? = null,
    val outEffectColor: String? = null,
    val aodText: String? = null,
    val filterMode: String? = null,
    val whitelistKeywords: List<String>? = null,
    val blacklistKeywords: List<String>? = null,
    val islandEnabled: Boolean? = null,
) {
    val hasChanges: Boolean
        get() = this != ChannelSettingsPatch()
}

/** 将单渠道的完整配置转换为共享表单状态。 */
internal fun ChannelSettings.toFullPatch(): ChannelSettingsPatch = ChannelSettingsPatch(
    template = template,
    renderer = renderer,
    iconMode = iconMode,
    focus = focus,
    showNotification = showNotification,
    preserveSmallIcon = preserveSmallIcon,
    showIslandIcon = showIslandIcon,
    firstFloat = firstFloat,
    enableFloat = enableFloat,
    timeout = timeout,
    marquee = marquee,
    marqueeAutoHide = marqueeAutoHide,
    restoreLockscreen = restoreLockscreen,
    highlightColor = highlightColor,
    dynamicHighlightColor = dynamicHighlightColor,
    showLeftHighlight = showLeftHighlight,
    showRightHighlight = showRightHighlight,
    showLeftNarrowFont = showLeftNarrowFont,
    showRightNarrowFont = showRightNarrowFont,
    outerGlow = outerGlow,
    islandOuterGlow = islandOuterGlow,
    islandOuterGlowColor = islandOuterGlowColor,
    outEffectColor = outEffectColor,
    aodText = aodText,
    filterMode = filterMode,
    whitelistKeywords = whitelistKeywords,
    blacklistKeywords = blacklistKeywords,
    islandEnabled = islandEnabled,
)

/** 合并单渠道或批量渠道补丁；null 始终保留现有值，兼容原批量设置语义。 */
internal fun ChannelSettings.withPatch(patch: ChannelSettingsPatch): ChannelSettings = copy(
    template = patch.template ?: template,
    renderer = patch.renderer ?: renderer,
    iconMode = patch.iconMode ?: iconMode,
    focus = patch.focus ?: focus,
    showNotification = patch.showNotification ?: showNotification,
    preserveSmallIcon = patch.preserveSmallIcon ?: preserveSmallIcon,
    showIslandIcon = patch.showIslandIcon ?: showIslandIcon,
    firstFloat = patch.firstFloat ?: firstFloat,
    enableFloat = patch.enableFloat ?: enableFloat,
    timeout = patch.timeout ?: timeout,
    marquee = patch.marquee ?: marquee,
    marqueeAutoHide = patch.marqueeAutoHide ?: marqueeAutoHide,
    restoreLockscreen = patch.restoreLockscreen ?: restoreLockscreen,
    highlightColor = patch.highlightColor ?: highlightColor,
    dynamicHighlightColor = patch.dynamicHighlightColor ?: dynamicHighlightColor,
    showLeftHighlight = patch.showLeftHighlight ?: showLeftHighlight,
    showRightHighlight = patch.showRightHighlight ?: showRightHighlight,
    showLeftNarrowFont = patch.showLeftNarrowFont ?: showLeftNarrowFont,
    showRightNarrowFont = patch.showRightNarrowFont ?: showRightNarrowFont,
    outerGlow = patch.outerGlow ?: outerGlow,
    islandOuterGlow = patch.islandOuterGlow ?: islandOuterGlow,
    islandOuterGlowColor = patch.islandOuterGlowColor ?: islandOuterGlowColor,
    outEffectColor = patch.outEffectColor ?: outEffectColor,
    aodText = patch.aodText ?: aodText,
    filterMode = patch.filterMode ?: filterMode,
    whitelistKeywords = patch.whitelistKeywords ?: whitelistKeywords,
    blacklistKeywords = patch.blacklistKeywords ?: blacklistKeywords,
    islandEnabled = patch.islandEnabled ?: islandEnabled,
)

internal sealed interface BatchChannelTarget {
    data class Channels(val packageName: String, val channelIds: Set<String>) : BatchChannelTarget
    data class Apps(val packageNames: Set<String>) : BatchChannelTarget
}

internal enum class ChannelCustomizationTarget {
    Island,
    Focus,
    Aod,
}

internal const val TEMPLATE_PROGRESS = "generic_progress"
internal const val TEMPLATE_NOTIFICATION = "notification_island"
internal const val TEMPLATE_AI_NOTIFICATION = "ai_notification_island"

internal const val RENDERER_IMAGE_TEXT_BUTTONS = "image_text_with_buttons_4"
internal const val RENDERER_IMAGE_TEXT_WRAP = "image_text_with_buttons_4_wrap"
internal const val RENDERER_IMAGE_TEXT_RIGHT_BUTTON = "image_text_with_right_text_button"
internal const val RENDERER_IMAGE_TEXT_PROGRESS = "image_text_with_progress"

internal const val ICON_AUTO = "auto"
internal const val ICON_NOTIFICATION_SMALL = "notif_small"
internal const val ICON_NOTIFICATION_LARGE = "notif_large"
internal const val ICON_APP = "app_icon"

internal const val OPTION_DEFAULT = "default"
internal const val OPTION_ON = "on"
internal const val OPTION_OFF = "off"
internal const val OPTION_FOLLOW_DYNAMIC = "follow_dynamic"

internal const val FILTER_BLACKLIST = "blacklist"
internal const val FILTER_WHITELIST = "whitelist"
