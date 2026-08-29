package io.github.hyperisland.compose.service

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

internal object ConfigBackupService {
    private const val prefsName = "FlutterSharedPreferences"
    private const val flutterPrefix = "flutter."
    private const val doublePrefix = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBEb3VibGUu"
    private const val schemaVersion = 2
    private const val appConfigPrefix = "pref_app_config_"
    private const val whitelistKey = "pref_generic_whitelist"

    fun exportJson(context: Context): String {
        val prefs = prefs(context)
        migrateLegacyPrefs(prefs)
        val settings = JSONObject()
        logicalEntries(prefs).forEach { (key, value) ->
            if (key.startsWith("pref_") && key != "pref_ai_api_key") {
                settings.put(key, exportValue(value))
            }
        }
        val appVersion = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        }.getOrDefault("")
        settings.put("pref_config_app_version", appVersion)
        settings.put("pref_config_schema_version", schemaVersion)
        return JSONObject()
            .put("version", schemaVersion)
            .put("appVersion", appVersion)
            .put("settings", settings)
            .toString(2)
    }

    fun importJson(context: Context, raw: String): Int {
        val root = runCatching { JSONObject(raw) }.getOrElse { throw InvalidConfigException() }
        val settings = root.optJSONObject("settings") ?: throw InvalidConfigException()
        val values = linkedMapOf<String, Any>()
        settings.keys().forEach { key ->
            if (!key.startsWith("pref_") || !validPackageConfig(key, settings.opt(key))) {
                throw InvalidConfigException()
            }
            when (val value = settings.opt(key)) {
                is Boolean, is String, is Number -> values[key] = value
                JSONObject.NULL -> Unit
                else -> throw InvalidConfigException()
            }
        }
        root.optString("appVersion").trim().takeIf(String::isNotEmpty)?.let { appVersion ->
            values.putIfAbsent("pref_config_app_version", appVersion)
        }

        val prefs = prefs(context)
        val editor = prefs.edit()
        logicalEntries(prefs).keys.filter(::isImportedAppConfigKey).forEach {
            editor.remove(storageKey(it))
        }
        var count = 0
        values.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(storageKey(key), value)
                is Byte, is Short, is Int, is Long -> editor.putLong(storageKey(key), (value as Number).toLong())
                is Float, is Double -> editor.putString(storageKey(key), doublePrefix + (value as Number).toDouble())
                is String -> editor.putString(storageKey(key), value)
            }
            count++
        }
        if (!editor.commit()) throw IllegalStateException("SharedPreferences commit failed")

        val importedVersion = root.optInt("version", 1)
        if (importedVersion < schemaVersion) count += migrateLegacyPrefs(prefs, force = true)
        else prefs.edit().putLong(storageKey("pref_config_schema_version"), schemaVersion.toLong()).apply()
        return count
    }

    fun cleanUninstalled(context: Context): Int {
        val prefs = prefs(context)
        migrateLegacyPrefs(prefs)
        val installed = context.packageManager.getInstalledApplications(0).mapTo(mutableSetOf()) { it.packageName }
        val entries = logicalEntries(prefs)
        val editor = prefs.edit()
        var count = 0
        entries.keys.filter { it.startsWith(appConfigPrefix) }.forEach { key ->
            if (key.removePrefix(appConfigPrefix) !in installed) {
                editor.remove(storageKey(key))
                count++
            }
        }
        val enabled = csv(entries[whitelistKey] as? String)
        val remaining = enabled.intersect(installed)
        if (remaining.size != enabled.size) {
            editor.putString(storageKey(whitelistKey), remaining.joinToString(","))
            count += enabled.size - remaining.size
        }
        if (!editor.commit()) throw IllegalStateException("SharedPreferences commit failed")
        return count
    }

    fun cleanDisabled(context: Context): Int {
        val prefs = prefs(context)
        migrateLegacyPrefs(prefs)
        val enabledPackages = csv(logicalEntries(prefs)[whitelistKey] as? String)
        val editor = prefs.edit()
        var count = 0
        logicalEntries(prefs).forEach { (key, value) ->
            if (!key.startsWith(appConfigPrefix) || value !is String) return@forEach
            val packageName = key.removePrefix(appConfigPrefix)
            val config = runCatching { JSONObject(value) }.getOrElse { JSONObject() }
            val toast = config.optJSONObject("toast")
            if (toast != null && !toast.optBoolean("forward", false)) {
                config.remove("toast")
                count++
            }
            if (packageName !in enabledPackages) {
                if (config.remove("notification") != null) count++
                if (config.remove("channels") != null) count++
            } else {
                val channels = config.optJSONObject("channels")
                val enabledChannels = channels?.optJSONArray("enabled")?.stringSet()
                val channelSettings = channels?.optJSONObject("settings")
                if (enabledChannels != null && channelSettings != null) {
                    channelSettings.keys().asSequence().toList().forEach { channelId ->
                        if (channelId !in enabledChannels) {
                            channelSettings.remove(channelId)
                            count++
                        }
                    }
                    if (channelSettings.length() == 0) channels.remove("settings")
                }
            }
            if (config.length() == 0) editor.remove(storageKey(key))
            else editor.putString(storageKey(key), config.toString())
        }
        if (!editor.commit()) throw IllegalStateException("SharedPreferences commit failed")
        return count
    }

    private fun migrateLegacyPrefs(prefs: SharedPreferences, force: Boolean = false): Int {
        val entries = logicalEntries(prefs)
        val current = (entries["pref_config_schema_version"] as? Number)?.toInt() ?: 1
        if (!force && current >= schemaVersion) return 0

        val packages = csv(entries[whitelistKey] as? String).toMutableSet()
        entries.keys.forEach { key ->
            when {
                key.startsWith(appConfigPrefix) -> packages += key.removePrefix(appConfigPrefix)
                else -> legacyPackageName(key)?.takeIf(String::isNotEmpty)?.let(packages::add)
            }
        }
        var sortedPackages = packages.sortedByDescending(String::length)
        entries.keys.forEach { key ->
            channelFields.values.firstOrNull { key.startsWith(it.prefix) }?.let { field ->
                val rest = key.removePrefix(field.prefix)
                sortedPackages.firstOrNull { rest.startsWith("${it}_") }?.let(packages::add)
            }
        }
        sortedPackages = packages.sortedByDescending(String::length)

        val editor = prefs.edit()
        var count = 0
        packages.forEach { packageName ->
            val configKey = "$appConfigPrefix$packageName"
            val config = runCatching { JSONObject(entries[configKey] as? String ?: "{}") }.getOrElse { JSONObject() }
            count += migrateSection(config, "toast", packageName, toastFields, entries)
            count += migrateSection(config, "notification", packageName, notificationFields, entries)

            val channels = config.optJSONObject("channels") ?: JSONObject()
            (entries["pref_channels_$packageName"] as? String)?.takeIf(String::isNotEmpty)?.let { raw ->
                channels.put("enabled", JSONArray(csv(raw).toList()))
                count++
            }
            val channelSettings = channels.optJSONObject("settings") ?: JSONObject()
            entries.forEach { (key, value) ->
                channelFields.forEach { (name, field) ->
                    val prefix = "${field.prefix}${packageName}_"
                    if (key.startsWith(prefix)) {
                        val channelId = key.removePrefix(prefix)
                        val channel = channelSettings.optJSONObject(channelId) ?: JSONObject()
                        if (value != field.defaultValue) {
                            channel.put(name, exportValue(value))
                            channelSettings.put(channelId, channel)
                            count++
                        }
                    }
                }
            }
            if (channelSettings.length() > 0) channels.put("settings", channelSettings)
            if (channels.length() > 0) config.put("channels", channels)
            if (config.length() > 0) editor.putString(storageKey(configKey), config.toString())
        }
        entries.keys.filter(::isLegacyAppConfigKey).forEach { editor.remove(storageKey(it)) }
        editor.putLong(storageKey("pref_config_schema_version"), schemaVersion.toLong())
        if (!editor.commit()) throw IllegalStateException("SharedPreferences commit failed")
        return count
    }

    private fun migrateSection(
        config: JSONObject,
        sectionName: String,
        packageName: String,
        fields: Map<String, LegacyField>,
        entries: Map<String, Any?>,
    ): Int {
        val section = config.optJSONObject(sectionName) ?: JSONObject()
        var count = 0
        fields.forEach { (name, field) ->
            val value = entries["${field.prefix}$packageName"] ?: return@forEach
            if (value != field.defaultValue) {
                section.put(name, exportValue(value))
                count++
            } else {
                section.remove(name)
            }
        }
        if (section.length() == 0) config.remove(sectionName) else config.put(sectionName, section)
        return count
    }

    private fun logicalEntries(prefs: SharedPreferences): Map<String, Any?> = prefs.all
        .filterKeys { it.startsWith(flutterPrefix) }
        .mapKeys { it.key.removePrefix(flutterPrefix) }

    private fun exportValue(value: Any?): Any = if (value is String && value.startsWith(doublePrefix)) {
        value.removePrefix(doublePrefix).toDoubleOrNull() ?: value
    } else {
        value ?: JSONObject.NULL
    }

    private fun validPackageConfig(key: String, value: Any?): Boolean {
        if (!key.startsWith(appConfigPrefix)) return true
        val packageName = key.removePrefix(appConfigPrefix)
        return packageName.matches(Regex("^[A-Za-z0-9._]+$")) && value is String &&
            runCatching { JSONObject(value) }.isSuccess
    }

    private fun legacyPackageName(key: String): String? {
        (toastFields.values + notificationFields.values).firstOrNull { key.startsWith(it.prefix) }?.let {
            return key.removePrefix(it.prefix)
        }
        return key.takeIf { it.startsWith("pref_channels_") }?.removePrefix("pref_channels_")
    }

    private fun isImportedAppConfigKey(key: String): Boolean =
        key.startsWith(appConfigPrefix) || isLegacyAppConfigKey(key)

    private fun isLegacyAppConfigKey(key: String): Boolean =
        legacyPackageName(key) != null || channelFields.values.any { key.startsWith(it.prefix) }

    private fun storageKey(key: String) = flutterPrefix + key
    private fun prefs(context: Context) = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private fun csv(value: String?): Set<String> = value.orEmpty().split(',').filter(String::isNotEmpty).toSet()
    private fun JSONArray.stringSet(): Set<String> = buildSet {
        for (index in 0 until length()) optString(index).takeIf(String::isNotEmpty)?.let(::add)
    }

    private data class LegacyField(val prefix: String, val defaultValue: Any)

    private val toastFields = mapOf(
        "forward" to LegacyField("pref_toast_forward_", false),
        "block" to LegacyField("pref_toast_block_", false),
        "show_notification" to LegacyField("pref_toast_show_notification_", false),
        "show_island_icon" to LegacyField("pref_toast_show_island_icon_", true),
        "first_float" to LegacyField("pref_toast_first_float_", "default"),
        "enable_float" to LegacyField("pref_toast_enable_float_", "default"),
        "preserve_small_icon" to LegacyField("pref_toast_preserve_small_icon_", "default"),
        "marquee" to LegacyField("pref_toast_marquee_", "default"),
        "marquee_auto_hide" to LegacyField("pref_toast_marquee_auto_hide_", "default"),
        "timeout" to LegacyField("pref_toast_timeout_", "5"),
        "highlight_color" to LegacyField("pref_toast_highlight_color_", ""),
        "dynamic_highlight_color" to LegacyField("pref_toast_dynamic_highlight_color_", "default"),
        "show_left_highlight" to LegacyField("pref_toast_show_left_highlight_", "off"),
        "show_right_highlight" to LegacyField("pref_toast_show_right_highlight_", "off"),
        "outer_glow" to LegacyField("pref_toast_outer_glow_", "default"),
        "out_effect_color" to LegacyField("pref_toast_out_effect_color_", ""),
        "island_outer_glow" to LegacyField("pref_toast_island_outer_glow_", "default"),
        "island_outer_glow_color" to LegacyField("pref_toast_island_outer_glow_color_", ""),
        "filter_mode" to LegacyField("pref_toast_filter_mode_", "blacklist"),
        "whitelist_keywords" to LegacyField("pref_toast_filter_whitelist_keywords_", ""),
        "blacklist_keywords" to LegacyField("pref_toast_filter_blacklist_keywords_", ""),
    )

    private val notificationFields = mapOf(
        "enabled" to LegacyField("pref_media_island_enabled_", true),
        "normal_notification" to LegacyField("pref_media_island_normal_notification_", false),
        "outer_glow" to LegacyField("pref_media_outer_glow_", "default"),
        "out_effect_color" to LegacyField("pref_media_out_effect_color_", ""),
        "island_outer_glow" to LegacyField("pref_media_island_outer_glow_", "default"),
        "island_outer_glow_color" to LegacyField("pref_media_island_outer_glow_color_", ""),
    )

    private val channelFields = mapOf(
        "template" to LegacyField("pref_channel_template_", "notification_island"),
        "renderer" to LegacyField("pref_channel_renderer_", "image_text_with_buttons_4"),
        "icon" to LegacyField("pref_channel_icon_", "auto"),
        "focus" to LegacyField("pref_channel_focus_", "default"),
        "show_notification" to LegacyField("pref_channel_show_notification_", "on"),
        "preserve_small_icon" to LegacyField("pref_channel_preserve_small_icon_", "default"),
        "show_island_icon" to LegacyField("pref_channel_show_island_icon_", "default"),
        "first_float" to LegacyField("pref_channel_first_float_", "default"),
        "enable_float" to LegacyField("pref_channel_enable_float_", "default"),
        "timeout" to LegacyField("pref_channel_timeout_", "5"),
        "marquee" to LegacyField("pref_channel_marquee_", "default"),
        "marquee_auto_hide" to LegacyField("pref_channel_marquee_auto_hide_", "default"),
        "restore_lockscreen" to LegacyField("pref_channel_restore_lockscreen_", "default"),
        "highlight_color" to LegacyField("pref_channel_highlight_color_", ""),
        "dynamic_highlight_color" to LegacyField("pref_channel_dynamic_highlight_color_", "default"),
        "show_left_highlight" to LegacyField("pref_channel_show_left_highlight_", "off"),
        "show_right_highlight" to LegacyField("pref_channel_show_right_highlight_", "off"),
        "show_left_narrow_font" to LegacyField("pref_channel_show_left_narrow_font_", "off"),
        "show_right_narrow_font" to LegacyField("pref_channel_show_right_narrow_font_", "off"),
        "outer_glow" to LegacyField("pref_channel_outer_glow_", "default"),
        "island_outer_glow" to LegacyField("pref_channel_island_outer_glow_", "default"),
        "island_outer_glow_color" to LegacyField("pref_channel_island_outer_glow_color_", ""),
        "out_effect_color" to LegacyField("pref_channel_out_effect_color_", ""),
        "focus_custom" to LegacyField("pref_channel_focus_custom_", ""),
        "island_custom" to LegacyField("pref_channel_island_custom_", ""),
        "aod_text" to LegacyField("pref_channel_aod_text_", "default"),
        "aod_custom" to LegacyField("pref_channel_aod_custom_", ""),
        "filter_mode" to LegacyField("pref_channel_filter_mode_", "blacklist"),
        "whitelist_keywords" to LegacyField("pref_channel_filter_whitelist_keywords_", ""),
        "blacklist_keywords" to LegacyField("pref_channel_filter_blacklist_keywords_", ""),
        "island_enabled" to LegacyField("pref_channel_island_enabled_", "true"),
    )
}

internal class InvalidConfigException : IllegalArgumentException()
