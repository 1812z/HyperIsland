package io.github.hyperisland.compose.data

import android.content.Context
import android.content.SharedPreferences
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

    private companion object {
        const val PREFS_NAME = "FlutterSharedPreferences"
        const val FLUTTER_PREFIX = "flutter."
        const val DEFAULT_CHANNEL_TEMPLATE = "notification_island"
        const val TRI_STATE_DEFAULT = "default"
    }
}
