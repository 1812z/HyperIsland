package io.github.hyperisland.xposed

import android.app.Notification
import android.service.notification.StatusBarNotification
import io.github.hyperisland.getAppIcon
import io.github.hyperisland.xposed.hook.MarqueeHook
import io.github.hyperisland.xposed.templates.NotificationIslandNotification
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModule

/**
 * 通用通知 Hook — 在 SystemUI 进程内 Hook MiuiBaseNotifUtil.generateInnerNotifBean()。
 *
 * 调用链：
 *   onNotificationPosted(sbn)
 *     → mBgHandler.post（后台线程）
 *         → generateInnerNotifBean(sbn)   ← ★ 此处最先读取 extras，快照进 InnerNotifBean
 *         → mMainExecutor.execute
 *             → extras.putParcelable("inner_notif_bean", innerNotifBean)
 *             → NotificationHandler.onNotificationPosted（最终分发）
 *
 * 必须在 generateInnerNotifBean 之前（intercept 中先处理再 proceed）写入 island extras，
 * 否则 bean 已经用原始 extras 创建完毕，后续修改不影响岛的触发判断。
 */
object GenericProgressHook {

    private const val TAG = "HyperIsland[Generic]"

    @Volatile private var cachedWhitelist: Map<String, Set<String>>? = null
    private val cachedTemplates = mutableMapOf<String, String>()
    private val cachedChannelSettings = mutableMapOf<String, String>()

    @Volatile private var observerRegistered = false

    private val lastProgressCache = mutableMapOf<String, Int>()
    private val trackedForCancel = mutableMapOf<String, Int>()

    fun ensureObserver(context: android.content.Context, module: XposedModule) {
        if (observerRegistered) return
        ConfigManager.init(module)
        ConfigManager.addChangeListener {
            clearAllCaches("prefs_changed", module)
        }
        observerRegistered = true
        module.log("$TAG: ConfigManager Observer registered in SystemUI")
    }

    private fun loadChannelStringSetting(cacheKey: String, prefKey: String, default: String): String {
        cachedChannelSettings[cacheKey]?.let { return it }
        val value = ConfigManager.getString(prefKey, default).takeIf { it.isNotBlank() } ?: default
        cachedChannelSettings[cacheKey] = value
        return value
    }

    private fun loadBooleanSetting(cacheKey: String, prefKey: String, default: Boolean): Boolean {
        cachedChannelSettings[cacheKey]?.let { return it == "1" }
        val value = ConfigManager.getBoolean(prefKey, default)
        cachedChannelSettings[cacheKey] = if (value) "1" else "0"
        return value
    }

    private fun resolveTriStateBoolean(global: Boolean, channelValue: String): Boolean {
        return when (channelValue) {
            "on" -> true
            "off" -> false
            else -> global
        }
    }

    private fun resolveTriOpt(channelValue: String, globalDefault: Boolean): String =
        when (channelValue) {
            "on"  -> "on"
            "off" -> "off"
            else  -> if (globalDefault) "on" else "off"
        }

    private fun clearAllCaches(reason: String, module: XposedModule) {
        cachedWhitelist = null
        cachedTemplates.clear()
        cachedChannelSettings.clear()
        module.log("$TAG: settings changed, cache cleared ($reason)")
    }

    fun loadChannelTemplate(pkg: String, channelId: String): String {
        val cacheKey = "$pkg/$channelId"
        cachedTemplates[cacheKey]?.let { return it }
        val key = "pref_channel_template_${pkg}_$channelId"
        val template = ConfigManager.getString(key)
            .takeIf { it.isNotBlank() } ?: NotificationIslandNotification.TEMPLATE_ID
        cachedTemplates[cacheKey] = template
        return template
    }

    private fun loadWhitelist(module: XposedModule): Map<String, Set<String>> {
        cachedWhitelist?.let { return it }
        val csv = ConfigManager.getString("pref_generic_whitelist")
        val map = csv.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .associate { pkg ->
                val channelCsv = ConfigManager.getString("pref_channels_$pkg")
                val channels = if (channelCsv.isBlank()) emptySet()
                else channelCsv.split(",").filter { it.isNotBlank() }.toSet()
                pkg to channels
            }
        if (map.isNotEmpty()) cachedWhitelist = map
        module.log("$TAG: whitelist loaded (${map.size} apps): ${map.keys}")
        return map
    }

    fun init(module: XposedModule, param: PackageLoadedParam) {
        val classLoader = param.defaultClassLoader

        // Hook generateInnerNotifBean (before)
        try {
            val clazz = classLoader.loadClass("com.miui.systemui.notification.MiuiBaseNotifUtil")
            val method = clazz.getDeclaredMethod("generateInnerNotifBean", StatusBarNotification::class.java)
            module.hook(method).intercept { chain ->
                val sbn = chain.args[0] as? StatusBarNotification
                if (sbn != null) handleSbn(sbn, module, classLoader)
                chain.proceed()
            }
            module.log("$TAG: hooked MiuiBaseNotifUtil.generateInnerNotifBean")
        } catch (e: Throwable) {
            module.log("$TAG: hook failed: ${e.message}")
        }

        // Hook onNotificationRemoved (after) — 优先 3 参数版本
        var cancelHooked = false
        try {
            val rankingMapClass = classLoader.loadClass(
                "android.service.notification.NotificationListenerService\$RankingMap"
            )
            val removeMethod = findMethod(
                classLoader.loadClass("android.service.notification.NotificationListenerService"),
                "onNotificationRemoved",
                StatusBarNotification::class.java,
                rankingMapClass,
                Int::class.javaPrimitiveType!!
            )
            module.hook(removeMethod).intercept { chain ->
                val result = chain.proceed()
                handleNotificationRemoved(chain.args[0] as? StatusBarNotification, module, classLoader)
                result
            }
            cancelHooked = true
            module.log("$TAG: hooked onNotificationRemoved(sbn, rankingMap, reason)")
        } catch (e: Throwable) {
            module.log("$TAG: onNotificationRemoved 3-param hook failed: ${e.message}")
        }

        if (!cancelHooked) {
            try {
                val removeMethod = findMethod(
                    classLoader.loadClass("android.service.notification.NotificationListenerService"),
                    "onNotificationRemoved",
                    StatusBarNotification::class.java
                )
                module.hook(removeMethod).intercept { chain ->
                    val result = chain.proceed()
                    handleNotificationRemoved(chain.args[0] as? StatusBarNotification, module, classLoader)
                    result
                }
                module.log("$TAG: hooked onNotificationRemoved(sbn)")
            } catch (e: Throwable) {
                module.log("$TAG: onNotificationRemoved 1-param hook failed: ${e.message}")
            }
        }
    }

    private fun handleNotificationRemoved(
        sbn: StatusBarNotification?,
        module: XposedModule,
        classLoader: ClassLoader
    ) {
        sbn ?: return
        val key = "${sbn.packageName}#${sbn.id}"
        val proxyId = trackedForCancel.remove(key) ?: return
        val context = getContext(classLoader) ?: return
        IslandDispatcher.cancel(context, proxyId)
    }

    private fun handleSbn(sbn: StatusBarNotification, module: XposedModule, classLoader: ClassLoader) {
        try {
            val pkg = sbn.packageName ?: return

            if (pkg == "com.android.systemui" &&
                sbn.notification?.channelId == IslandDispatcher.CHANNEL_ID) return

            MarqueeHook.pendingMarqueeEnabled = false

            val context = getContext(classLoader) ?: return
            ensureObserver(context, module)

            val allowedChannels = loadWhitelist(module)[pkg] ?: return
            val notif = sbn.notification ?: return
            val channelId = notif.channelId ?: ""
            if (allowedChannels.isNotEmpty() && channelId !in allowedChannels) return

            val extras = notif.extras ?: return

            if (isMediaNotification(notif, extras)) return
            if (extras.containsKey("miui.focus.param")) return

            val progressMax   = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
            val indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
            val hasProgressBar = progressMax > 0 && !indeterminate

            if (hasProgressBar) {
                if (extras.getBoolean("hyperisland_processed", false)) return
                if (extras.getBoolean("hyperisland_generic_processed", false)) return
            }

            val cacheKey = "$pkg#${sbn.id}"
            val progressPercent: Int
            if (hasProgressBar) {
                val progressRaw = extras.getInt(Notification.EXTRA_PROGRESS, -1)
                if (progressRaw < 0) return
                progressPercent = (progressRaw * 100 / progressMax).coerceIn(0, 100)
                if (progressPercent in 0..99) lastProgressCache[cacheKey] = progressPercent
            } else {
                progressPercent = lastProgressCache[cacheKey] ?: -1
            }

            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()
                ?: return

            val subtitle = listOf(
                extras.getCharSequence(Notification.EXTRA_SUB_TEXT),
                extras.getCharSequence(Notification.EXTRA_TEXT),
                extras.getCharSequence(Notification.EXTRA_INFO_TEXT),
                extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ).firstNotNullOfOrNull { it?.toString()?.takeIf { s -> s.isNotEmpty() } } ?: ""

            val actions: List<Notification.Action> = notif.actions?.take(2) ?: emptyList()

            val template = loadChannelTemplate(pkg, channelId)

            val appIconRaw = context.packageManager.getAppIcon(pkg)
            val largeIcon  = extractLargeIcon(extras)

            val iconMode = loadChannelStringSetting("icon:$pkg/$channelId", "pref_channel_icon_${pkg}_$channelId", "auto")
            val defaultFirstFloat        = loadBooleanSetting("global:default_first_float",        "pref_default_first_float",        false)
            val defaultEnableFloat       = loadBooleanSetting("global:default_enable_float",       "pref_default_enable_float",       false)
            val defaultMarquee           = loadBooleanSetting("global:default_marquee",            "pref_default_marquee",            false)
            val defaultFocusNotif        = loadBooleanSetting("global:default_focus_notif",        "pref_default_focus_notif",        true)
            val defaultPreserveSmallIcon = loadBooleanSetting("global:default_preserve_small_icon","pref_default_preserve_small_icon", false)

            val focusNotif = resolveTriOpt(
                loadChannelStringSetting("focus:$pkg/$channelId", "pref_channel_focus_${pkg}_$channelId", "default"),
                defaultFocusNotif
            )
            val preserveStatusBarSmallIcon = resolveTriOpt(
                loadChannelStringSetting("preserve_small_icon:$pkg/$channelId", "pref_channel_preserve_small_icon_${pkg}_$channelId", "default"),
                defaultPreserveSmallIcon
            )
            val firstFloat = resolveTriOpt(
                loadChannelStringSetting("first_float:$pkg/$channelId", "pref_channel_first_float_${pkg}_$channelId", "default"),
                defaultFirstFloat
            )
            val enableFloatMode = resolveTriOpt(
                loadChannelStringSetting("efloat:$pkg/$channelId", "pref_channel_enable_float_${pkg}_$channelId", "default"),
                defaultEnableFloat
            )
            val islandTimeoutStr = loadChannelStringSetting(
                "timeout:$pkg/$channelId", "pref_channel_timeout_${pkg}_$channelId", "5"
            )
            val islandTimeout = islandTimeoutStr.toIntOrNull() ?: 5
            val focusIconMode = loadChannelStringSetting(
                "focus_icon:$pkg/$channelId", "pref_channel_focus_icon_${pkg}_$channelId", "auto"
            )
            val isOngoing = (notif.flags and Notification.FLAG_ONGOING_EVENT) != 0
            val marqueeEnabled = !isOngoing && resolveTriStateBoolean(
                defaultMarquee,
                loadChannelStringSetting("marquee:$pkg/$channelId", "pref_channel_marquee_${pkg}_$channelId", "default")
            )
            MarqueeHook.pendingMarqueeEnabled = marqueeEnabled
            val renderer = loadChannelStringSetting(
                "renderer:$pkg/$channelId", "pref_channel_renderer_${pkg}_$channelId", "image_text_with_buttons_4"
            )

            module.log(
                "$TAG: $pkg/$channelId | $title | $progressPercent% | template=$template | buttons=${actions.size} | largeIcon=${largeIcon != null} | preserveSmallIcon=$preserveStatusBarSmallIcon"
            )

            TemplateRegistry.dispatch(
                templateId = template,
                context    = context,
                extras     = extras,
                data       = NotifData(
                    pkg             = pkg,
                    channelId       = channelId,
                    notifId         = sbn.id,
                    title           = title,
                    subtitle        = subtitle,
                    progress        = progressPercent,
                    actions         = actions,
                    notifIcon       = notif.smallIcon,
                    largeIcon       = largeIcon,
                    appIconRaw      = appIconRaw,
                    iconMode        = iconMode,
                    focusIconMode   = focusIconMode,
                    focusNotif      = focusNotif,
                    preserveStatusBarSmallIcon = preserveStatusBarSmallIcon,
                    firstFloat      = firstFloat,
                    enableFloatMode = enableFloatMode,
                    islandTimeout   = islandTimeout,
                    isOngoing       = isOngoing,
                    contentIntent   = notif.contentIntent,
                    renderer        = renderer,
                ),
            )

            extras.putBoolean("hyperisland_generic_processed", true)
            trackedForCancel["$pkg#${sbn.id}"] = IslandDispatcher.NOTIF_ID

        } catch (e: Throwable) {
            module.log("$TAG: handleSbn error: ${e.message}")
        }
    }

    private fun isMediaNotification(notif: Notification, extras: android.os.Bundle): Boolean {
        if (extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return true
        val template = extras.getString(Notification.EXTRA_TEMPLATE) ?: return false
        return template.contains("MediaStyle", ignoreCase = true)
    }

    private fun extractLargeIcon(extras: android.os.Bundle): android.graphics.drawable.Icon? {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                @Suppress("DEPRECATION")
                val icon = extras.getParcelable<android.graphics.drawable.Icon>(
                    android.app.Notification.EXTRA_LARGE_ICON
                )
                if (icon != null) return icon
            }
            @Suppress("DEPRECATION")
            val bitmap = extras.getParcelable<android.graphics.Bitmap>(
                android.app.Notification.EXTRA_LARGE_ICON
            )
            if (bitmap != null) android.graphics.drawable.Icon.createWithBitmap(bitmap) else null
        } catch (_: Exception) { null }
    }

    private fun getContext(classLoader: ClassLoader): android.content.Context? {
        return try {
            val at = classLoader.loadClass("android.app.ActivityThread")
            at.getMethod("currentApplication").invoke(null) as? android.content.Context
        } catch (_: Exception) {
            try {
                val at = classLoader.loadClass("android.app.ActivityThread")
                (at.getMethod("getSystemContext").invoke(null) as? android.content.Context)?.applicationContext
            } catch (_: Exception) { null }
        }
    }

    private fun findMethod(clazz: Class<*>, name: String, vararg paramTypes: Class<*>): java.lang.reflect.Method {
        var c: Class<*>? = clazz
        while (c != null) {
            try { return c.getDeclaredMethod(name, *paramTypes) } catch (_: NoSuchMethodException) {}
            c = c.superclass
        }
        throw NoSuchMethodException("$name not found in ${clazz.name} hierarchy")
    }
}