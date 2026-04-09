package io.github.hyperisland.ui.app

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import io.github.hyperisland.NotificationChannelReader
import io.github.hyperisland.data.prefs.PrefKeys
import io.github.hyperisland.utils.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap

class AppAdaptationRepository(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PrefKeys.PREFS_NAME, Context.MODE_PRIVATE)
    private val iconCache = ConcurrentHashMap<String, ByteArray>()

    suspend fun loadInstalledApps(includeIcons: Boolean = true): List<AppItem> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        pm.getInstalledApplications(0)
            .asSequence()
            .filter { it.packageName != context.packageName }
            .mapNotNull { app ->
                runCatching {
                    AppItem(
                        packageName = app.packageName,
                        appName = pm.getApplicationLabel(app).toString(),
                        isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                        icon = if (includeIcons) {
                            readCachedAppIconBytes(pm, app.packageName)
                        } else {
                            byteArrayOf()
                        },
                    )
                }.getOrNull()
            }
            .sortedBy { it.appName.lowercase() }
            .toList()
    }

    suspend fun loadAppItem(packageName: String): AppItem? = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        runCatching {
            val app = pm.getApplicationInfo(packageName, 0)
            AppItem(
                packageName = packageName,
                appName = pm.getApplicationLabel(app).toString(),
                isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                icon = readCachedAppIconBytes(pm, packageName),
            )
        }.getOrNull()
    }

    suspend fun loadAppIcon(packageName: String): ByteArray = withContext(Dispatchers.IO) {
        readCachedAppIconBytes(context.packageManager, packageName)
    }

    suspend fun loadAppIcons(
        packageNames: List<String>,
        parallelism: Int = 6,
    ): Map<String, ByteArray> = coroutineScope {
        val pm = context.packageManager
        val iconDispatcher = Dispatchers.IO.limitedParallelism(parallelism)
        packageNames
            .distinct()
            .map { packageName ->
                async(iconDispatcher) {
                    packageName to readCachedAppIconBytes(pm, packageName)
                }
            }
            .awaitAll()
            .filter { (_, icon) -> icon.isNotEmpty() }
            .toMap()
    }

    private fun readAppIconBytes(pm: PackageManager, packageName: String): ByteArray {
        return runCatching {
            ByteArrayOutputStream().use { stream ->
                pm.getApplicationIcon(packageName)
                    .toBitmap(96)
                    .compress(Bitmap.CompressFormat.PNG, 90, stream)
                stream.toByteArray()
            }
        }.getOrDefault(byteArrayOf())
    }

    private fun readCachedAppIconBytes(pm: PackageManager, packageName: String): ByteArray {
        val cached = iconCache[packageName]
        if (cached != null) return cached
        val icon = readAppIconBytes(pm, packageName)
        if (icon.isNotEmpty()) {
            iconCache[packageName] = icon
        }
        return icon
    }

    fun loadEnabledPackages(): Set<String> {
        val csv = prefs.getString("pref_generic_whitelist", "") ?: ""
        return if (csv.isBlank()) emptySet() else csv.split(",").filter { it.isNotBlank() }.toSet()
    }

    fun setEnabledPackages(value: Set<String>) {
        prefs.edit().putString("pref_generic_whitelist", value.joinToString(",")).apply()
    }

    fun isAppEnabled(packageName: String): Boolean {
        return loadEnabledPackages().contains(packageName)
    }

    fun setAppEnabled(packageName: String, enabled: Boolean) {
        val next = loadEnabledPackages().toMutableSet().apply {
            if (enabled) add(packageName) else remove(packageName)
        }.toSet()
        setEnabledPackages(next)
    }

    suspend fun loadChannels(packageName: String): List<ChannelItem>? = withContext(Dispatchers.IO) {
        NotificationChannelReader.readChannels(packageName)?.map {
            ChannelItem(
                id = it.id,
                name = it.name,
                description = it.description,
                importance = it.importance,
            )
        }
    }

    fun getEnabledChannels(packageName: String): Set<String> {
        val csv = prefs.getString("pref_channels_$packageName", "") ?: ""
        return if (csv.isBlank()) emptySet() else csv.split(",").filter { it.isNotBlank() }.toSet()
    }

    fun setEnabledChannels(packageName: String, channels: Set<String>) {
        prefs.edit().putString("pref_channels_$packageName", channels.joinToString(",")).apply()
    }

    fun getChannelTemplate(packageName: String, channelId: String): String {
        return prefs.getString(
            "pref_channel_template_${packageName}_$channelId",
            "notification_island",
        ) ?: "notification_island"
    }

    fun setChannelTemplate(packageName: String, channelId: String, template: String) {
        prefs.edit().putString("pref_channel_template_${packageName}_$channelId", template).apply()
    }

    fun getChannelTimeout(packageName: String, channelId: String): String {
        return prefs.getString("pref_channel_timeout_${packageName}_$channelId", "5") ?: "5"
    }

    fun setChannelTimeout(packageName: String, channelId: String, value: String) {
        prefs.edit().putString("pref_channel_timeout_${packageName}_$channelId", value).apply()
    }

    fun getChannelExtras(packageName: String, channelId: String): ChannelExtraSettings {
        return ChannelExtraSettings(
            icon = prefs.getString("pref_channel_icon_${packageName}_$channelId", "auto") ?: "auto",
            focusIcon = prefs.getString("pref_channel_focus_icon_${packageName}_$channelId", "auto") ?: "auto",
            focus = prefs.getString("pref_channel_focus_${packageName}_$channelId", "default") ?: "default",
            preserveSmallIcon = prefs.getString(
                "pref_channel_preserve_small_icon_${packageName}_$channelId",
                "default",
            ) ?: "default",
            showIslandIcon = prefs.getString(
                "pref_channel_show_island_icon_${packageName}_$channelId",
                "default",
            ) ?: "default",
            firstFloat = prefs.getString("pref_channel_first_float_${packageName}_$channelId", "default")
                ?: "default",
            enableFloat = prefs.getString("pref_channel_enable_float_${packageName}_$channelId", "default")
                ?: "default",
            marquee = prefs.getString("pref_channel_marquee_${packageName}_$channelId", "default") ?: "default",
            renderer = prefs.getString(
                "pref_channel_renderer_${packageName}_$channelId",
                "image_text_with_buttons_4",
            ) ?: "image_text_with_buttons_4",
            restoreLockscreen = prefs.getString(
                "pref_channel_restore_lockscreen_${packageName}_$channelId",
                "default",
            ) ?: "default",
            highlightColor = prefs.getString("pref_channel_highlight_color_${packageName}_$channelId", "") ?: "",
            showLeftHighlight = prefs.getString(
                "pref_channel_show_left_highlight_${packageName}_$channelId",
                "off",
            ) ?: "off",
            showRightHighlight = prefs.getString(
                "pref_channel_show_right_highlight_${packageName}_$channelId",
                "off",
            ) ?: "off",
        )
    }

    fun setChannelSetting(packageName: String, channelId: String, setting: String, value: String) {
        val key = when (setting) {
            "icon" -> "pref_channel_icon_${packageName}_$channelId"
            "focus_icon" -> "pref_channel_focus_icon_${packageName}_$channelId"
            "focus" -> "pref_channel_focus_${packageName}_$channelId"
            "preserve_small_icon" -> "pref_channel_preserve_small_icon_${packageName}_$channelId"
            "show_island_icon" -> "pref_channel_show_island_icon_${packageName}_$channelId"
            "first_float" -> "pref_channel_first_float_${packageName}_$channelId"
            "enable_float" -> "pref_channel_enable_float_${packageName}_$channelId"
            "marquee" -> "pref_channel_marquee_${packageName}_$channelId"
            "renderer" -> "pref_channel_renderer_${packageName}_$channelId"
            "restore_lockscreen" -> "pref_channel_restore_lockscreen_${packageName}_$channelId"
            "highlight_color" -> "pref_channel_highlight_color_${packageName}_$channelId"
            "show_left_highlight" -> "pref_channel_show_left_highlight_${packageName}_$channelId"
            "show_right_highlight" -> "pref_channel_show_right_highlight_${packageName}_$channelId"
            else -> return
        }
        if (setting == "highlight_color" && value.isBlank()) {
            prefs.edit().remove(key).apply()
        } else {
            prefs.edit().putString(key, value).apply()
        }
    }

    fun batchApplyChannelSettings(
        packageName: String,
        channelIds: List<String>,
        settings: Map<String, String>,
    ) {
        if (channelIds.isEmpty() || settings.isEmpty()) return
        val editor = prefs.edit()
        for (channelId in channelIds) {
            settings.forEach { (setting, value) ->
                val key = when (setting) {
                    "template" -> "pref_channel_template_${packageName}_$channelId"
                    "timeout" -> "pref_channel_timeout_${packageName}_$channelId"
                    "icon" -> "pref_channel_icon_${packageName}_$channelId"
                    "focus_icon" -> "pref_channel_focus_icon_${packageName}_$channelId"
                    "focus" -> "pref_channel_focus_${packageName}_$channelId"
                    "preserve_small_icon" -> "pref_channel_preserve_small_icon_${packageName}_$channelId"
                    "show_island_icon" -> "pref_channel_show_island_icon_${packageName}_$channelId"
                    "first_float" -> "pref_channel_first_float_${packageName}_$channelId"
                    "enable_float" -> "pref_channel_enable_float_${packageName}_$channelId"
                    "marquee" -> "pref_channel_marquee_${packageName}_$channelId"
                    "renderer" -> "pref_channel_renderer_${packageName}_$channelId"
                    "restore_lockscreen" -> "pref_channel_restore_lockscreen_${packageName}_$channelId"
                    "highlight_color" -> "pref_channel_highlight_color_${packageName}_$channelId"
                    "show_left_highlight" -> "pref_channel_show_left_highlight_${packageName}_$channelId"
                    "show_right_highlight" -> "pref_channel_show_right_highlight_${packageName}_$channelId"
                    else -> null
                } ?: return@forEach
                if (setting == "highlight_color" && value.isBlank()) {
                    editor.remove(key)
                } else {
                    editor.putString(key, value)
                }
            }
        }
        editor.apply()
    }

    suspend fun batchApplyToAllEnabledApps(
        settings: Map<String, String>,
        onProgress: (done: Int, total: Int) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val enabledPackages = loadEnabledPackages().toList()
        val total = enabledPackages.size
        enabledPackages.forEachIndexed { index, pkg ->
            runCatching {
                val channels = loadChannels(pkg).orEmpty()
                val ids = channels.map { it.id }
                if (ids.isNotEmpty()) {
                    batchApplyChannelSettings(pkg, ids, settings)
                }
            }
            onProgress(index + 1, total)
        }
    }
}
