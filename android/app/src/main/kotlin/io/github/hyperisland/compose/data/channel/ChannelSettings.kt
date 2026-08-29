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
