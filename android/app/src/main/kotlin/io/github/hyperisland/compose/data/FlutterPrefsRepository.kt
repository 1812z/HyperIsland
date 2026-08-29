package io.github.hyperisland.compose.data

import android.content.Context
import android.content.SharedPreferences
import io.github.hyperisland.compose.data.channel.ChannelSettings
import io.github.hyperisland.compose.data.channel.ChannelSettingsPatch
import io.github.hyperisland.compose.data.channel.FILTER_BLACKLIST
import io.github.hyperisland.compose.data.channel.ICON_AUTO
import io.github.hyperisland.compose.data.channel.OPTION_DEFAULT
import io.github.hyperisland.compose.data.channel.OPTION_OFF
import io.github.hyperisland.compose.data.channel.OPTION_ON
import io.github.hyperisland.compose.data.channel.RENDERER_IMAGE_TEXT_BUTTONS
import io.github.hyperisland.compose.data.channel.TEMPLATE_NOTIFICATION
import org.json.JSONArray
import org.json.JSONObject

internal data class MediaNotificationSettings(
    val enabled: Boolean = true,
    val normalNotification: Boolean = false,
    val outerGlow: String = "default",
    val outEffectColor: String = "",
    val islandOuterGlow: String = "default",
    val islandOuterGlowColor: String = "",
)

internal data class DefaultConfigSettings(
    val firstFloat: Boolean = false,
    val aodText: Boolean = false,
    val enableFloat: Boolean = false,
    val marquee: Boolean = false,
    val marqueeAutoHide: String = "off",
    val timeout: Int = 5,
    val dynamicHighlightColor: Boolean = false,
    val focusNotification: Boolean = true,
    val restoreLockscreen: Boolean = false,
    val showIslandIcon: Boolean = true,
    val preserveSmallIcon: Boolean = false,
    val outerGlow: String = "off",
    val forceOuterGlow: Boolean = false,
    val outEffectColor: String = "",
    val islandOuterGlow: String = "off",
    val forceIslandOuterGlow: Boolean = false,
    val islandOuterGlowColor: String = "",
)

internal data class ToastAppSettings(
    val forwardEnabled: Boolean = false,
    val blockOriginal: Boolean = false,
    val showNotification: Boolean = false,
    val showIslandIcon: Boolean = true,
    val firstFloat: String = "default",
    val enableFloat: String = "default",
    val preserveSmallIcon: String = "default",
    val marquee: String = "default",
    val marqueeAutoHide: String = "default",
    val timeout: String = "default",
    val highlightColor: String = "",
    val dynamicHighlightColor: String = "default",
    val showLeftHighlight: String = "off",
    val showRightHighlight: String = "off",
    val outerGlow: String = "default",
    val outEffectColor: String = "",
    val islandOuterGlow: String = "default",
    val islandOuterGlowColor: String = "",
    val filterMode: String = "blacklist",
    val whitelistKeywords: List<String> = emptyList(),
    val blacklistKeywords: List<String> = emptyList(),
)

internal data class AiConfigSettings(
    val enabled: Boolean = false,
    val url: String = "",
    val apiKey: String = "",
    val model: String = "",
    val prompt: String = "",
    val promptInUser: Boolean = false,
    val customFields: String = "{\"enable_thinking\":false}",
    val timeout: Int = 3,
    val temperature: Double = 0.1,
    val maxTokens: Int = 50,
    val triggerCharCount: Int = 10,
)

internal data class KeepIslandSettings(
    val enabled: Boolean = false,
    val displayTiming: String = "always",
    val showNotification: Boolean = false,
    val autoHide: Boolean = true,
    val hideLandscape: Boolean = false,
    val highlightColor: String = "",
    val leftHighlight: Boolean = false,
    val rightHighlight: Boolean = false,
    val leftContents: List<String> = listOf("{time.HH:mm}"),
    val rightContents: List<String> = listOf("{battery.level}"),
    val carouselInterval: Int = 5,
    val focusNotification: Boolean = false,
    val focusContentType: String = "notification",
    val expandTextColorMode: String = "white",
    val notificationTitle: String = "",
    val notificationContent: String = "",
    val showIslandIcon: Boolean = false,
    val customIconPath: String = "",
)

/** 与 Flutter shared_preferences、XposedPrefsSyncApp 共用同一份配置。 */
class FlutterPrefsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun getBoolean(key: String, default: Boolean): Boolean =
        runCatching { prefs.getBoolean(storageKey(key), default) }.getOrDefault(default)

    fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(storageKey(key), value).apply()
    }

    fun getString(key: String, default: String = ""): String =
        runCatching { prefs.getString(storageKey(key), default) ?: default }.getOrDefault(default)

    fun putString(key: String, value: String) {
        prefs.edit().putString(storageKey(key), value).apply()
    }

    fun getLong(key: String, default: Long): Long =
        runCatching { prefs.getLong(storageKey(key), default) }.getOrDefault(default)

    fun putLong(key: String, value: Long) {
        prefs.edit().putLong(storageKey(key), value).apply()
    }

    fun getDouble(key: String, default: Double): Double = runCatching {
        val raw = prefs.getString(storageKey(key), null) ?: return@runCatching default
        raw.removePrefix(FLUTTER_DOUBLE_PREFIX).toDoubleOrNull() ?: default
    }.getOrElse {
        runCatching { prefs.getFloat(storageKey(key), default.toFloat()).toDouble() }.getOrDefault(default)
    }

    fun putDouble(key: String, value: Double) {
        prefs.edit().putString(storageKey(key), FLUTTER_DOUBLE_PREFIX + value).apply()
    }

    fun remove(key: String) {
        prefs.edit().remove(storageKey(key)).apply()
    }

    fun addChangeListener(listener: (String) -> Unit): () -> Unit {
        val delegate = SharedPreferences.OnSharedPreferenceChangeListener { _, storageKey ->
            if (storageKey?.startsWith(FLUTTER_PREFIX) == true) {
                listener(storageKey.removePrefix(FLUTTER_PREFIX))
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(delegate)
        return { prefs.unregisterOnSharedPreferenceChangeListener(delegate) }
    }

    fun enabledAppCount(): Int = getString("pref_generic_whitelist")
        .split(',')
        .count { it.trim().isNotEmpty() }

    fun enabledPackages(): Set<String> = getString("pref_generic_whitelist")
        .split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toSet()

    fun setAppEnabled(packageName: String, enabled: Boolean) {
        val packages = enabledPackages().toMutableSet()
        if (enabled) packages += packageName else packages -= packageName
        putString("pref_generic_whitelist", packages.joinToString(","))
    }

    fun setAppsEnabled(packageNames: Collection<String>, enabled: Boolean) {
        val packages = enabledPackages().toMutableSet()
        if (enabled) packages += packageNames else packages -= packageNames.toSet()
        putString("pref_generic_whitelist", packages.joinToString(","))
    }

    fun foregroundExcludedPackages(): Set<String> = getString(KEY_FOREGROUND_EXCLUDED)
        .split(',')
        .filter(String::isNotEmpty)
        .toSet()

    fun foregroundAction(packageName: String): String {
        val explicit = getString("$KEY_FOREGROUND_PREFIX$packageName")
        if (explicit in FOREGROUND_ACTIONS) return explicit
        return if (packageName in getString(KEY_LEGACY_BLACKLIST).split(',')) {
            "small_only"
        } else {
            "default"
        }
    }

    fun configuredForegroundPackages(): Set<String> = buildSet {
        addAll(getString(KEY_LEGACY_BLACKLIST).split(',').filter(String::isNotEmpty))
        val index = getString(KEY_FOREGROUND_INDEX).split(',').filter(String::isNotEmpty)
        addAll(index)
    }

    fun setForegroundAction(packageName: String, action: String) {
        val normalized = action.takeIf { it in FOREGROUND_ACTIONS } ?: "default"
        val blacklist = getString(KEY_LEGACY_BLACKLIST).split(',')
            .filter(String::isNotEmpty).toMutableSet()
        val configured = configuredForegroundPackages().toMutableSet()
        when (normalized) {
            "default" -> {
                remove("$KEY_FOREGROUND_PREFIX$packageName")
                blacklist -= packageName
                configured -= packageName
            }
            "small_only" -> {
                remove("$KEY_FOREGROUND_PREFIX$packageName")
                blacklist += packageName
                configured += packageName
            }
            else -> {
                putString("$KEY_FOREGROUND_PREFIX$packageName", normalized)
                blacklist -= packageName
                configured += packageName
            }
        }
        if (blacklist.isEmpty()) remove(KEY_LEGACY_BLACKLIST)
        else putString(KEY_LEGACY_BLACKLIST, blacklist.joinToString(","))
        if (configured.isEmpty()) remove(KEY_FOREGROUND_INDEX)
        else putString(KEY_FOREGROUND_INDEX, configured.joinToString(","))
    }

    fun setForegroundExcluded(packageName: String, excluded: Boolean) {
        val packages = foregroundExcludedPackages().toMutableSet()
        if (excluded) packages += packageName else packages -= packageName
        if (packages.isEmpty()) remove(KEY_FOREGROUND_EXCLUDED)
        else putString(KEY_FOREGROUND_EXCLUDED, packages.joinToString(","))
    }

    fun resetForegroundRules(): Int {
        val affected = configuredForegroundPackages() + foregroundExcludedPackages()
        configuredForegroundPackages().forEach { remove("$KEY_FOREGROUND_PREFIX$it") }
        remove(KEY_LEGACY_BLACKLIST)
        remove(KEY_FOREGROUND_INDEX)
        remove(KEY_FOREGROUND_EXCLUDED)
        return affected.size
    }

    fun isToastEnabled(packageName: String): Boolean = runCatching {
        JSONObject(getString("pref_app_config_$packageName"))
            .optJSONObject("toast")
            ?.optBoolean("forward", false) == true
    }.getOrDefault(false)

    fun setToastEnabled(packageName: String, enabled: Boolean) {
        val key = "pref_app_config_$packageName"
        val root = runCatching { JSONObject(getString(key)) }.getOrElse { JSONObject() }
        val toast = root.optJSONObject("toast") ?: JSONObject().also { root.put("toast", it) }
        toast.put("forward", enabled)
        toast.put("block", enabled)
        putString(key, root.toString())
    }

    fun setToastEnabled(packageNames: Collection<String>, enabled: Boolean) {
        packageNames.forEach { setToastEnabled(it, enabled) }
    }

    fun enabledChannelIds(packageName: String): Set<String> = runCatching {
        val enabled = JSONObject(getString("pref_app_config_$packageName"))
            .optJSONObject("channels")
            ?.optJSONArray("enabled") ?: return@runCatching emptySet()
        buildSet {
            for (index in 0 until enabled.length()) {
                enabled.optString(index).takeIf(String::isNotEmpty)?.let(::add)
            }
        }
    }.getOrDefault(emptySet())

    fun setEnabledChannelIds(packageName: String, channelIds: Set<String>) {
        updateAppConfig(packageName) { root ->
            val channels = root.optJSONObject("channels") ?: JSONObject().also { root.put("channels", it) }
            if (channelIds.isEmpty()) {
                channels.remove("enabled")
            } else {
                channels.put("enabled", JSONArray(channelIds.toList()))
            }
            if (channels.length() == 0) root.remove("channels")
        }
    }

    internal fun mediaNotificationSettings(packageName: String): MediaNotificationSettings = runCatching {
        val notification = JSONObject(getString("pref_app_config_$packageName"))
            .optJSONObject("notification") ?: return@runCatching MediaNotificationSettings()
        MediaNotificationSettings(
            enabled = notification.optBoolean("enabled", true),
            normalNotification = notification.optBoolean("normal_notification", false),
            outerGlow = notification.optString("outer_glow", TRI_STATE_DEFAULT),
            outEffectColor = notification.optString("out_effect_color", ""),
            islandOuterGlow = notification.optString("island_outer_glow", TRI_STATE_DEFAULT),
            islandOuterGlowColor = notification.optString("island_outer_glow_color", ""),
        )
    }.getOrDefault(MediaNotificationSettings())

    internal fun setMediaNotificationSettings(packageName: String, value: MediaNotificationSettings) {
        updateAppConfig(packageName) { root ->
            val notification = root.optJSONObject("notification")
                ?: JSONObject().also { root.put("notification", it) }
            putIfNonDefault(notification, "enabled", value.enabled, true)
            putIfNonDefault(notification, "normal_notification", value.normalNotification, false)
            putIfNonDefault(notification, "outer_glow", value.outerGlow, TRI_STATE_DEFAULT)
            putIfNonDefault(notification, "out_effect_color", value.outEffectColor.trim(), "")
            putIfNonDefault(notification, "island_outer_glow", value.islandOuterGlow, TRI_STATE_DEFAULT)
            putIfNonDefault(
                notification,
                "island_outer_glow_color",
                value.islandOuterGlowColor.trim(),
                "",
            )
            if (notification.length() == 0) root.remove("notification")
        }
    }

    internal fun channelSettings(packageName: String, channelId: String): ChannelSettings = runCatching {
        val channel = JSONObject(getString("pref_app_config_$packageName"))
            .optJSONObject("channels")
            ?.optJSONObject("settings")
            ?.optJSONObject(channelId) ?: return@runCatching ChannelSettings()
        ChannelSettings(
            template = channel.optString("template", TEMPLATE_NOTIFICATION).let {
                if (it == "download_lite") "generic_progress" else it
            },
            renderer = channel.optString("renderer", RENDERER_IMAGE_TEXT_BUTTONS),
            iconMode = channel.optString("icon", ICON_AUTO),
            focus = channel.optString("focus", OPTION_DEFAULT),
            showNotification = channel.optString("show_notification", OPTION_ON),
            preserveSmallIcon = channel.optString("preserve_small_icon", OPTION_DEFAULT),
            showIslandIcon = channel.optString("show_island_icon", OPTION_DEFAULT),
            firstFloat = channel.optString("first_float", OPTION_DEFAULT),
            enableFloat = channel.optString("enable_float", OPTION_DEFAULT),
            timeout = channel.optString("timeout", OPTION_DEFAULT),
            marquee = channel.optString("marquee", OPTION_DEFAULT),
            marqueeAutoHide = channel.optString("marquee_auto_hide", OPTION_DEFAULT),
            restoreLockscreen = channel.optString("restore_lockscreen", OPTION_DEFAULT),
            highlightColor = channel.optString("highlight_color", ""),
            dynamicHighlightColor = channel.optString("dynamic_highlight_color", OPTION_DEFAULT),
            showLeftHighlight = channel.optString("show_left_highlight", OPTION_OFF),
            showRightHighlight = channel.optString("show_right_highlight", OPTION_OFF),
            showLeftNarrowFont = channel.optString("show_left_narrow_font", OPTION_OFF),
            showRightNarrowFont = channel.optString("show_right_narrow_font", OPTION_OFF),
            outerGlow = channel.optString("outer_glow", OPTION_DEFAULT),
            islandOuterGlow = channel.optString("island_outer_glow", OPTION_DEFAULT),
            islandOuterGlowColor = channel.optString("island_outer_glow_color", ""),
            outEffectColor = channel.optString("out_effect_color", ""),
            focusCustom = channel.optString("focus_custom", ""),
            islandCustom = channel.optString("island_custom", ""),
            aodText = channel.optString("aod_text", OPTION_DEFAULT),
            aodCustom = channel.optString("aod_custom", ""),
            filterMode = channel.optString("filter_mode", FILTER_BLACKLIST),
            whitelistKeywords = decodeChannelKeywords(channel.optString("whitelist_keywords", "")),
            blacklistKeywords = decodeChannelKeywords(channel.optString("blacklist_keywords", "")),
            islandEnabled = channel.optString("island_enabled", "true").toBooleanStrictOrNull() ?: true,
        )
    }.getOrDefault(ChannelSettings())

    internal fun setChannelSettings(packageName: String, channelId: String, value: ChannelSettings) {
        updateAppConfig(packageName) { root ->
            val channels = root.optJSONObject("channels") ?: JSONObject().also { root.put("channels", it) }
            val settings = channels.optJSONObject("settings") ?: JSONObject().also { channels.put("settings", it) }
            val channel = settings.optJSONObject(channelId) ?: JSONObject().also { settings.put(channelId, it) }
            putIfNonDefault(channel, "template", value.template, TEMPLATE_NOTIFICATION)
            putIfNonDefault(channel, "renderer", value.renderer, RENDERER_IMAGE_TEXT_BUTTONS)
            putIfNonDefault(channel, "icon", value.iconMode, ICON_AUTO)
            putIfNonDefault(channel, "focus", value.focus, OPTION_DEFAULT)
            putIfNonDefault(channel, "show_notification", value.showNotification, OPTION_ON)
            putIfNonDefault(channel, "preserve_small_icon", value.preserveSmallIcon, OPTION_DEFAULT)
            putIfNonDefault(channel, "show_island_icon", value.showIslandIcon, OPTION_DEFAULT)
            putIfNonDefault(channel, "first_float", value.firstFloat, OPTION_DEFAULT)
            putIfNonDefault(channel, "enable_float", value.enableFloat, OPTION_DEFAULT)
            putIfNonDefault(channel, "timeout", value.timeout, OPTION_DEFAULT)
            putIfNonDefault(channel, "marquee", value.marquee, OPTION_DEFAULT)
            putIfNonDefault(channel, "marquee_auto_hide", value.marqueeAutoHide, OPTION_DEFAULT)
            putIfNonDefault(channel, "restore_lockscreen", value.restoreLockscreen, OPTION_DEFAULT)
            putIfNonDefault(channel, "highlight_color", value.highlightColor.trim(), "")
            putIfNonDefault(channel, "dynamic_highlight_color", value.dynamicHighlightColor, OPTION_DEFAULT)
            putIfNonDefault(channel, "show_left_highlight", value.showLeftHighlight, OPTION_OFF)
            putIfNonDefault(channel, "show_right_highlight", value.showRightHighlight, OPTION_OFF)
            putIfNonDefault(channel, "show_left_narrow_font", value.showLeftNarrowFont, OPTION_OFF)
            putIfNonDefault(channel, "show_right_narrow_font", value.showRightNarrowFont, OPTION_OFF)
            putIfNonDefault(channel, "outer_glow", value.outerGlow, OPTION_DEFAULT)
            putIfNonDefault(channel, "island_outer_glow", value.islandOuterGlow, OPTION_DEFAULT)
            putIfNonDefault(channel, "island_outer_glow_color", value.islandOuterGlowColor.trim(), "")
            putIfNonDefault(channel, "out_effect_color", value.outEffectColor.trim(), "")
            putIfNonDefault(channel, "focus_custom", value.focusCustom, "")
            putIfNonDefault(channel, "island_custom", value.islandCustom, "")
            putIfNonDefault(channel, "aod_text", value.aodText, OPTION_DEFAULT)
            putIfNonDefault(channel, "aod_custom", value.aodCustom, "")
            putIfNonDefault(channel, "filter_mode", value.filterMode, FILTER_BLACKLIST)
            putIfNonDefault(channel, "whitelist_keywords", encodeChannelKeywords(value.whitelistKeywords), "")
            putIfNonDefault(channel, "blacklist_keywords", encodeChannelKeywords(value.blacklistKeywords), "")
            putIfNonDefault(channel, "island_enabled", value.islandEnabled.toString(), "true")
            if (channel.length() == 0) settings.remove(channelId)
            if (settings.length() == 0) channels.remove("settings")
            if (channels.length() == 0) root.remove("channels")
        }
    }

    internal fun applyChannelSettingsPatch(
        packageName: String,
        channelIds: Collection<String>,
        patch: ChannelSettingsPatch,
    ) {
        if (!patch.hasChanges) return
        channelIds.forEach { channelId ->
            val current = channelSettings(packageName, channelId)
            setChannelSettings(
                packageName,
                channelId,
                current.copy(
                    template = patch.template ?: current.template,
                    renderer = patch.renderer ?: current.renderer,
                    iconMode = patch.iconMode ?: current.iconMode,
                    focus = patch.focus ?: current.focus,
                    showNotification = patch.showNotification ?: current.showNotification,
                    preserveSmallIcon = patch.preserveSmallIcon ?: current.preserveSmallIcon,
                    showIslandIcon = patch.showIslandIcon ?: current.showIslandIcon,
                    firstFloat = patch.firstFloat ?: current.firstFloat,
                    enableFloat = patch.enableFloat ?: current.enableFloat,
                    timeout = patch.timeout ?: current.timeout,
                    marquee = patch.marquee ?: current.marquee,
                    marqueeAutoHide = patch.marqueeAutoHide ?: current.marqueeAutoHide,
                    restoreLockscreen = patch.restoreLockscreen ?: current.restoreLockscreen,
                    highlightColor = patch.highlightColor ?: current.highlightColor,
                    dynamicHighlightColor = patch.dynamicHighlightColor ?: current.dynamicHighlightColor,
                    showLeftHighlight = patch.showLeftHighlight ?: current.showLeftHighlight,
                    showRightHighlight = patch.showRightHighlight ?: current.showRightHighlight,
                    showLeftNarrowFont = patch.showLeftNarrowFont ?: current.showLeftNarrowFont,
                    showRightNarrowFont = patch.showRightNarrowFont ?: current.showRightNarrowFont,
                    outerGlow = patch.outerGlow ?: current.outerGlow,
                    islandOuterGlow = patch.islandOuterGlow ?: current.islandOuterGlow,
                    islandOuterGlowColor = patch.islandOuterGlowColor ?: current.islandOuterGlowColor,
                    outEffectColor = patch.outEffectColor ?: current.outEffectColor,
                    aodText = patch.aodText ?: current.aodText,
                    filterMode = patch.filterMode ?: current.filterMode,
                    whitelistKeywords = patch.whitelistKeywords ?: current.whitelistKeywords,
                    blacklistKeywords = patch.blacklistKeywords ?: current.blacklistKeywords,
                    islandEnabled = patch.islandEnabled ?: current.islandEnabled,
                ),
            )
        }
    }

    internal fun defaultConfigSettings(): DefaultConfigSettings = DefaultConfigSettings(
        firstFloat = getBoolean("pref_default_first_float", false),
        aodText = getBoolean("pref_default_aod_text", false),
        enableFloat = getBoolean("pref_default_enable_float", false),
        marquee = getBoolean("pref_default_marquee", false),
        marqueeAutoHide = getString("pref_default_marquee_auto_hide", "off"),
        timeout = getLong("pref_default_timeout", 5L).toInt().coerceAtLeast(1),
        dynamicHighlightColor = getBoolean("pref_default_dynamic_highlight_color", false),
        focusNotification = getBoolean("pref_default_focus_notif", true),
        restoreLockscreen = getBoolean("pref_default_restore_lockscreen", false),
        showIslandIcon = getBoolean("pref_default_show_island_icon", true),
        preserveSmallIcon = getBoolean("pref_default_preserve_small_icon", false),
        outerGlow = getOuterGlowMode("pref_default_outer_glow"),
        forceOuterGlow = getBoolean("pref_default_force_outer_glow", false),
        outEffectColor = getString("pref_default_out_effect_color"),
        islandOuterGlow = getOuterGlowMode("pref_default_island_outer_glow"),
        forceIslandOuterGlow = getBoolean("pref_default_force_island_outer_glow", false),
        islandOuterGlowColor = getString("pref_default_island_outer_glow_color"),
    )

    internal fun setDefaultConfigSettings(value: DefaultConfigSettings) {
        putBoolean("pref_default_first_float", value.firstFloat)
        putBoolean("pref_default_aod_text", value.aodText)
        putBoolean("pref_default_enable_float", value.enableFloat)
        putBoolean("pref_default_marquee", value.marquee)
        putString("pref_default_marquee_auto_hide", value.marqueeAutoHide)
        putLong("pref_default_timeout", value.timeout.coerceAtLeast(1).toLong())
        putBoolean("pref_default_dynamic_highlight_color", value.dynamicHighlightColor)
        putBoolean("pref_default_focus_notif", value.focusNotification)
        putBoolean("pref_default_restore_lockscreen", value.restoreLockscreen)
        putBoolean("pref_default_show_island_icon", value.showIslandIcon)
        putBoolean("pref_default_preserve_small_icon", value.preserveSmallIcon)
        putString("pref_default_outer_glow", value.outerGlow)
        putBoolean("pref_default_force_outer_glow", value.forceOuterGlow)
        if (value.outEffectColor.isBlank()) remove("pref_default_out_effect_color")
        else putString("pref_default_out_effect_color", value.outEffectColor.trim())
        putString("pref_default_island_outer_glow", value.islandOuterGlow)
        putBoolean("pref_default_force_island_outer_glow", value.forceIslandOuterGlow)
        if (value.islandOuterGlowColor.isBlank()) remove("pref_default_island_outer_glow_color")
        else putString("pref_default_island_outer_glow_color", value.islandOuterGlowColor.trim())
    }

    internal fun toastAppSettings(packageName: String): ToastAppSettings = runCatching {
        val toast = JSONObject(getString("pref_app_config_$packageName"))
            .optJSONObject("toast") ?: return@runCatching ToastAppSettings()
        val defaultTimeout = defaultConfigSettings().timeout.toString()
        val storedTimeout = toast.optString("timeout", TRI_STATE_DEFAULT)
        ToastAppSettings(
            forwardEnabled = toast.optBoolean("forward", false),
            blockOriginal = toast.optBoolean("block", false),
            showNotification = toast.optBoolean("show_notification", false),
            showIslandIcon = toast.optBoolean("show_island_icon", true),
            firstFloat = toast.optString("first_float", TRI_STATE_DEFAULT),
            enableFloat = toast.optString("enable_float", TRI_STATE_DEFAULT),
            preserveSmallIcon = toast.optString("preserve_small_icon", TRI_STATE_DEFAULT),
            marquee = toast.optString("marquee", TRI_STATE_DEFAULT),
            marqueeAutoHide = toast.optString("marquee_auto_hide", TRI_STATE_DEFAULT),
            timeout = storedTimeout.takeUnless {
                it.isBlank() || it == "5" || it == defaultTimeout
            } ?: TRI_STATE_DEFAULT,
            highlightColor = toast.optString("highlight_color", ""),
            dynamicHighlightColor = toast.optString("dynamic_highlight_color", TRI_STATE_DEFAULT),
            showLeftHighlight = toast.optString("show_left_highlight", TRI_STATE_OFF),
            showRightHighlight = toast.optString("show_right_highlight", TRI_STATE_OFF),
            outerGlow = toast.optString("outer_glow", TRI_STATE_DEFAULT),
            outEffectColor = toast.optString("out_effect_color", ""),
            islandOuterGlow = toast.optString("island_outer_glow", TRI_STATE_DEFAULT),
            islandOuterGlowColor = toast.optString("island_outer_glow_color", ""),
            filterMode = toast.optString("filter_mode", "blacklist"),
            whitelistKeywords = decodeKeywords(toast.optString("whitelist_keywords", "")),
            blacklistKeywords = decodeKeywords(toast.optString("blacklist_keywords", "")),
        )
    }.getOrDefault(ToastAppSettings())

    internal fun setToastAppSettings(packageName: String, value: ToastAppSettings) {
        updateAppConfig(packageName) { root ->
            val toast = root.optJSONObject("toast") ?: JSONObject().also { root.put("toast", it) }
            putIfNonDefault(toast, "forward", value.forwardEnabled, false)
            putIfNonDefault(toast, "block", value.blockOriginal, false)
            putIfNonDefault(toast, "show_notification", value.showNotification, false)
            putIfNonDefault(toast, "show_island_icon", value.showIslandIcon, true)
            putIfNonDefault(toast, "first_float", value.firstFloat, TRI_STATE_DEFAULT)
            putIfNonDefault(toast, "enable_float", value.enableFloat, TRI_STATE_DEFAULT)
            putIfNonDefault(toast, "preserve_small_icon", value.preserveSmallIcon, TRI_STATE_DEFAULT)
            putIfNonDefault(toast, "marquee", value.marquee, TRI_STATE_DEFAULT)
            putIfNonDefault(toast, "marquee_auto_hide", value.marqueeAutoHide, TRI_STATE_DEFAULT)
            putIfNonDefault(toast, "timeout", value.timeout, TRI_STATE_DEFAULT)
            putIfNonDefault(toast, "highlight_color", value.highlightColor.trim(), "")
            putIfNonDefault(toast, "dynamic_highlight_color", value.dynamicHighlightColor, TRI_STATE_DEFAULT)
            putIfNonDefault(toast, "show_left_highlight", value.showLeftHighlight, TRI_STATE_OFF)
            putIfNonDefault(toast, "show_right_highlight", value.showRightHighlight, TRI_STATE_OFF)
            putIfNonDefault(toast, "outer_glow", value.outerGlow, TRI_STATE_DEFAULT)
            putIfNonDefault(toast, "out_effect_color", value.outEffectColor.trim(), "")
            putIfNonDefault(toast, "island_outer_glow", value.islandOuterGlow, TRI_STATE_DEFAULT)
            putIfNonDefault(toast, "island_outer_glow_color", value.islandOuterGlowColor.trim(), "")
            putIfNonDefault(toast, "filter_mode", value.filterMode, "blacklist")
            putIfNonDefault(toast, "whitelist_keywords", encodeKeywords(value.whitelistKeywords), "")
            putIfNonDefault(toast, "blacklist_keywords", encodeKeywords(value.blacklistKeywords), "")
            if (toast.length() == 0) root.remove("toast")
        }
    }

    internal fun aiConfigSettings(): AiConfigSettings = AiConfigSettings(
        enabled = getBoolean("pref_ai_enabled", false),
        url = getString("pref_ai_url"),
        apiKey = getString("pref_ai_api_key"),
        model = getString("pref_ai_model"),
        prompt = getString("pref_ai_prompt"),
        promptInUser = getBoolean("pref_ai_prompt_in_user", false),
        customFields = getString("pref_ai_custom_fields", DEFAULT_AI_CUSTOM_FIELDS),
        timeout = getLong("pref_ai_timeout", 3L).toInt().coerceIn(3, 15),
        temperature = getDouble("pref_ai_temperature", 0.1).coerceIn(0.0, 1.0),
        maxTokens = getLong("pref_ai_max_tokens", 50L).toInt().coerceIn(20, 100),
        triggerCharCount = getLong("pref_ai_trigger_char_count", 10L).toInt().coerceIn(0, 100),
    )

    internal fun setAiConfigSettings(value: AiConfigSettings) {
        putBoolean("pref_ai_enabled", value.enabled)
        putString("pref_ai_url", value.url.trim())
        putString("pref_ai_api_key", value.apiKey.trim())
        putString("pref_ai_model", value.model.trim())
        putString("pref_ai_prompt", value.prompt.trim())
        putBoolean("pref_ai_prompt_in_user", value.promptInUser)
        putString("pref_ai_custom_fields", value.customFields)
        putLong("pref_ai_timeout", value.timeout.coerceIn(3, 15).toLong())
        putDouble("pref_ai_temperature", value.temperature.coerceIn(0.0, 1.0))
        putLong("pref_ai_max_tokens", value.maxTokens.coerceIn(20, 100).toLong())
        putLong("pref_ai_trigger_char_count", value.triggerCharCount.coerceIn(0, 100).toLong())
    }

    internal fun keepIslandSettings(): KeepIslandSettings = KeepIslandSettings(
        enabled = getBoolean(KEY_KEEP_ISLAND, false),
        displayTiming = getString(KEY_KEEP_ISLAND_DISPLAY_TIMING, "always")
            .takeIf { it == "charging" } ?: "always",
        showNotification = getBoolean(KEY_KEEP_ISLAND_SHOW_NOTIFICATION, false),
        autoHide = getBoolean(KEY_KEEP_ISLAND_AUTO_HIDE, true),
        hideLandscape = getBoolean(KEY_KEEP_ISLAND_HIDE_LANDSCAPE, false),
        highlightColor = getString(KEY_KEEP_ISLAND_HIGHLIGHT_COLOR),
        leftHighlight = getBoolean(KEY_KEEP_ISLAND_LEFT_HIGHLIGHT, false),
        rightHighlight = getBoolean(KEY_KEEP_ISLAND_RIGHT_HIGHLIGHT, false),
        leftContents = decodeStringList(
            getString(KEY_KEEP_ISLAND_LEFT_CONTENT, ""),
            listOf("{time.HH:mm}"),
        ),
        rightContents = decodeStringList(
            getString(KEY_KEEP_ISLAND_RIGHT_CONTENT, ""),
            listOf("{battery.level}"),
        ),
        carouselInterval = getLong(KEY_KEEP_ISLAND_CAROUSEL_INTERVAL, 5L).toInt().coerceIn(1, 6000),
        focusNotification = getBoolean(KEY_KEEP_ISLAND_FOCUS_NOTIFICATION, false),
        focusContentType = getString(KEY_KEEP_ISLAND_FOCUS_CONTENT_TYPE, "notification")
            .takeIf { it in KEEP_ISLAND_CONTENT_TYPES } ?: "notification",
        expandTextColorMode = getString(KEY_KEEP_ISLAND_EXPAND_TEXT_COLOR, "white")
            .takeIf { it in KEEP_ISLAND_TEXT_COLORS } ?: "white",
        notificationTitle = getString(KEY_KEEP_ISLAND_NOTIFICATION_TITLE),
        notificationContent = getString(KEY_KEEP_ISLAND_NOTIFICATION_CONTENT),
        showIslandIcon = getBoolean(KEY_KEEP_ISLAND_SHOW_ICON, false),
        customIconPath = getString(KEY_KEEP_ISLAND_CUSTOM_ICON),
    )

    internal fun setKeepIslandSettings(value: KeepIslandSettings) {
        putBoolean(KEY_KEEP_ISLAND, value.enabled)
        putString(KEY_KEEP_ISLAND_DISPLAY_TIMING, value.displayTiming)
        putBoolean(KEY_KEEP_ISLAND_SHOW_NOTIFICATION, value.showNotification)
        putBoolean(KEY_KEEP_ISLAND_AUTO_HIDE, value.autoHide)
        putBoolean(KEY_KEEP_ISLAND_HIDE_LANDSCAPE, value.hideLandscape)
        if (value.highlightColor.isBlank()) remove(KEY_KEEP_ISLAND_HIGHLIGHT_COLOR)
        else putString(KEY_KEEP_ISLAND_HIGHLIGHT_COLOR, value.highlightColor.trim())
        putBoolean(KEY_KEEP_ISLAND_LEFT_HIGHLIGHT, value.leftHighlight)
        putBoolean(KEY_KEEP_ISLAND_RIGHT_HIGHLIGHT, value.rightHighlight)
        putString(KEY_KEEP_ISLAND_LEFT_CONTENT, JSONArray(value.leftContents).toString())
        putString(KEY_KEEP_ISLAND_RIGHT_CONTENT, JSONArray(value.rightContents).toString())
        putLong(KEY_KEEP_ISLAND_CAROUSEL_INTERVAL, value.carouselInterval.coerceIn(1, 6000).toLong())
        putBoolean(KEY_KEEP_ISLAND_FOCUS_NOTIFICATION, value.focusNotification)
        putString(KEY_KEEP_ISLAND_FOCUS_CONTENT_TYPE, value.focusContentType)
        putString(KEY_KEEP_ISLAND_EXPAND_TEXT_COLOR, value.expandTextColorMode)
        putString(KEY_KEEP_ISLAND_NOTIFICATION_TITLE, value.notificationTitle.trim())
        putString(KEY_KEEP_ISLAND_NOTIFICATION_CONTENT, value.notificationContent.trim())
        putBoolean(KEY_KEEP_ISLAND_SHOW_ICON, value.showIslandIcon)
        putString(KEY_KEEP_ISLAND_CUSTOM_ICON, value.customIconPath.trim())
    }

    internal fun exportChannelSettings(
        packageName: String,
        appName: String,
        channels: List<NotificationChannelInfo>,
    ): String {
        val root = runCatching { JSONObject(getString("pref_app_config_$packageName")) }.getOrElse { JSONObject() }
        val channelSection = root.optJSONObject("channels")
        val enabled = enabledChannelIds(packageName)
        val settings = channelSection?.optJSONObject("settings")
        val exportedChannels = JSONArray()
        channels.forEach { channel ->
            val channelSettings = settings?.optJSONObject(channel.id) ?: JSONObject()
            exportedChannels.put(
                JSONObject()
                    .put("id", channel.id)
                    .put("name", channel.name)
                    .put("enabled", enabled.isEmpty() || channel.id in enabled)
                    .put("template", channelSettings.optString("template", DEFAULT_CHANNEL_TEMPLATE))
                    .put("settings", JSONObject(channelSettings.toString()).apply { remove("template") }),
            )
        }
        return JSONObject()
            .put("version", 1)
            .put("app", appName)
            .put("package", packageName)
            .put("channels", exportedChannels)
            .toString(2)
    }

    fun importChannelSettings(
        packageName: String,
        availableChannelIds: Set<String>,
        rawJson: String,
    ): Pair<Int, Int> {
        val imported = JSONObject(rawJson).optJSONArray("channels")
            ?: throw IllegalArgumentException("missing_channels")
        var total = 0
        var matched = 0
        val enabled = mutableSetOf<String>()
        updateAppConfig(packageName) { root ->
            val channels = root.optJSONObject("channels") ?: JSONObject().also { root.put("channels", it) }
            val settings = channels.optJSONObject("settings") ?: JSONObject().also { channels.put("settings", it) }
            for (index in 0 until imported.length()) {
                val entry = imported.optJSONObject(index) ?: continue
                total++
                val id = entry.optString("id")
                if (id !in availableChannelIds) continue
                matched++
                if (entry.optBoolean("enabled", true)) enabled += id
                val importedSettings = entry.optJSONObject("settings")?.let { JSONObject(it.toString()) } ?: JSONObject()
                val template = entry.optString("template", DEFAULT_CHANNEL_TEMPLATE)
                if (template != DEFAULT_CHANNEL_TEMPLATE) importedSettings.put("template", template)
                if (importedSettings.length() == 0) settings.remove(id) else settings.put(id, importedSettings)
            }
            if (settings.length() == 0) channels.remove("settings")
            if (enabled.size == availableChannelIds.size) channels.remove("enabled")
            else channels.put("enabled", JSONArray(enabled.toList()))
        }
        if (matched > 0) setAppEnabled(packageName, true)
        return matched to total
    }

    fun toastEnabledAppCount(): Int = prefs.all.count { (key, value) ->
        if (!key.startsWith("${FLUTTER_PREFIX}pref_app_config_") || value !is String) {
            return@count false
        }
        runCatching {
            JSONObject(value).optJSONObject("toast")?.optBoolean("forward", false) == true
        }.getOrDefault(false)
    }

    private fun storageKey(key: String): String =
        if (key.startsWith(FLUTTER_PREFIX)) key else "$FLUTTER_PREFIX$key"

    private fun updateAppConfig(packageName: String, update: (JSONObject) -> Unit) {
        val key = "pref_app_config_$packageName"
        val root = runCatching { JSONObject(getString(key)) }.getOrElse { JSONObject() }
        update(root)
        putString(key, root.toString())
    }

    private fun putIfNonDefault(target: JSONObject, key: String, value: Any, defaultValue: Any) {
        if (value == defaultValue) target.remove(key) else target.put(key, value)
    }

    private fun decodeKeywords(raw: String): List<String> = raw.lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .toList()

    private fun encodeKeywords(keywords: List<String>): String = keywords.asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .joinToString("\n")

    private fun decodeChannelKeywords(raw: String): List<String> = raw
        .split(',', '\n')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()

    private fun encodeChannelKeywords(keywords: List<String>): String = keywords.asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .joinToString(",")

    private fun decodeStringList(raw: String, defaults: List<String>): List<String> {
        if (raw.isBlank()) return defaults
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index -> array.optString(index) }
        }.getOrElse { listOf(raw) }
    }

    private fun getOuterGlowMode(key: String): String = runCatching {
        prefs.getString(storageKey(key), null)
    }.getOrNull()?.takeIf { it == "on" || it == "off" || it == "follow_dynamic" }
        ?: runCatching { if (prefs.getBoolean(storageKey(key), false)) "on" else "off" }.getOrDefault("off")

    private companion object {
        const val PREFS_NAME = "FlutterSharedPreferences"
        const val FLUTTER_PREFIX = "flutter."
        const val DEFAULT_CHANNEL_TEMPLATE = "notification_island"
        const val TRI_STATE_DEFAULT = "default"
        const val TRI_STATE_OFF = "off"
        const val FLUTTER_DOUBLE_PREFIX = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBEb3VibGUu"
        const val DEFAULT_AI_CUSTOM_FIELDS = "{\"enable_thinking\":false}"
        const val KEY_LEGACY_BLACKLIST = "pref_app_blacklist"
        const val KEY_FOREGROUND_PREFIX = "pref_scene_foreground_"
        const val KEY_FOREGROUND_INDEX = "pref_scene_foreground_packages"
        const val KEY_FOREGROUND_EXCLUDED = "pref_scene_excluded_foreground_packages"
        const val KEY_KEEP_ISLAND = "pref_keep_island"
        const val KEY_KEEP_ISLAND_DISPLAY_TIMING = "pref_keep_island_display_timing"
        const val KEY_KEEP_ISLAND_SHOW_NOTIFICATION = "pref_keep_island_show_notification"
        const val KEY_KEEP_ISLAND_AUTO_HIDE = "pref_keep_island_auto_hide"
        const val KEY_KEEP_ISLAND_HIDE_LANDSCAPE = "pref_keep_island_hide_landscape"
        const val KEY_KEEP_ISLAND_HIGHLIGHT_COLOR = "pref_keep_island_highlight_color"
        const val KEY_KEEP_ISLAND_LEFT_HIGHLIGHT = "pref_keep_island_left_highlight"
        const val KEY_KEEP_ISLAND_RIGHT_HIGHLIGHT = "pref_keep_island_right_highlight"
        const val KEY_KEEP_ISLAND_LEFT_CONTENT = "pref_keep_island_left_content"
        const val KEY_KEEP_ISLAND_RIGHT_CONTENT = "pref_keep_island_right_content"
        const val KEY_KEEP_ISLAND_CAROUSEL_INTERVAL = "pref_keep_island_carousel_interval_seconds"
        const val KEY_KEEP_ISLAND_FOCUS_NOTIFICATION = "pref_keep_island_focus_notification"
        const val KEY_KEEP_ISLAND_FOCUS_CONTENT_TYPE = "pref_keep_island_focus_content_type"
        const val KEY_KEEP_ISLAND_EXPAND_TEXT_COLOR = "pref_keep_island_expand_text_color_mode"
        const val KEY_KEEP_ISLAND_NOTIFICATION_TITLE = "pref_keep_island_notification_title"
        const val KEY_KEEP_ISLAND_NOTIFICATION_CONTENT = "pref_keep_island_notification_content"
        const val KEY_KEEP_ISLAND_SHOW_ICON = "pref_keep_island_show_island_icon"
        const val KEY_KEEP_ISLAND_CUSTOM_ICON = "pref_keep_island_custom_icon_path"
        val KEEP_ISLAND_CONTENT_TYPES = setOf("notification", "performance", "device", "charging")
        val KEEP_ISLAND_TEXT_COLORS = setOf("white", "follow_status_bar", "invert_status_bar", "black")
        val FOREGROUND_ACTIONS = setOf("small_only", "expand", "suppress")
    }
}
