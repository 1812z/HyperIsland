package io.github.hyperisland.ui.app

data class ChannelItem(
    val id: String,
    val name: String,
    val description: String,
    val importance: Int,
)

data class AppChannelsUiState(
    val packageName: String = "",
    val appName: String = "",
    val appIcon: ByteArray = byteArrayOf(),
    val appEnabled: Boolean = false,
    val loading: Boolean = true,
    val channels: List<ChannelItem> = emptyList(),
    val enabledChannels: Set<String> = emptySet(),
    val channelTemplates: Map<String, String> = emptyMap(),
    val channelTimeout: Map<String, String> = emptyMap(),
    val channelExtras: Map<String, ChannelExtraSettings> = emptyMap(),
    val error: String? = null,
)

data class ChannelExtraSettings(
    val icon: String = "auto",
    val focusIcon: String = "auto",
    val focus: String = "default",
    val preserveSmallIcon: String = "default",
    val showIslandIcon: String = "default",
    val firstFloat: String = "default",
    val enableFloat: String = "default",
    val marquee: String = "default",
    val renderer: String = "image_text_with_buttons_4",
    val restoreLockscreen: String = "default",
    val highlightColor: String = "",
    val showLeftHighlight: String = "off",
    val showRightHighlight: String = "off",
)
