package io.github.hyperisland.xposed.hook.SystemUI

import android.R
import android.app.KeyguardManager
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import io.github.hyperisland.utils.getAppIcon
import io.github.hyperisland.utils.resolveDynamicHighlightColor
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.hyperisland.xposed.hook.IslandOuterGlowHook
import io.github.hyperisland.xposed.islanddispatch.IslandDispatcher
import io.github.hyperisland.xposed.template.core.TemplateRegistry
import io.github.hyperisland.xposed.template.core.models.NotifData
import io.github.hyperisland.xposed.utils.SceneBehavior
import io.github.hyperisland.xposed.utils.toRounded
import io.github.hyperisland.xposed.templates.NotificationIslandNotification
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method

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
object GenericProgressHook : BaseHook() {

    private const val TAG = "HyperIsland[Generic]"
    private const val EXTRA_OWNER = "hyperisland.owner"
    private const val OWNER_MARKER = "io.github.hyperisland"
    private const val EFFECT_SRC = "outer_glow"

    override fun getTag() = TAG

    override fun onConfigChanged() {
        clearAllCaches()
    }

    @Volatile private var cachedWhitelist: Map<String, Set<String>>? = null

    /**
     * LRU 缓存：accessOrder=true 时，get/put 都会将被访问的条目移到尾部，
     * 超过容量时自动淘汰头部（最久未访问）的条目，无需整清空。
     */
    private val cachedTemplates = object : LinkedHashMap<String, String>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > MAX_TEMPLATES_SIZE
    }
    private val cachedChannelSettings = object : LinkedHashMap<String, String>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > MAX_CHANNEL_SETTINGS_SIZE
    }

    private val lastProgressCache = mutableMapOf<String, Int>()
    private val trackedForCancel = mutableMapOf<String, Int>()

    /** LRU 缓存上限（超限自动淘汰最久未访问的条目） */
    private const val MAX_TEMPLATES_SIZE = 5000
    private const val MAX_CHANNEL_SETTINGS_SIZE = 5000
    /** 非 LRU 缓存上限（超限时整体清空再写入） */
    private const val MAX_PROGRESS_CACHE_SIZE = 500
    private const val MAX_TRACKED_CANCEL_SIZE = 500

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

    private fun resolveGlowMode(channelValue: String, globalDefault: String): String =
        when (channelValue) {
            "on", "off", "follow_dynamic" -> channelValue
            else -> globalDefault
        }

    private fun clearAllCaches() {
        cachedWhitelist = null
        cachedTemplates.clear()
        cachedChannelSettings.clear()
        lastProgressCache.clear()
        trackedForCancel.clear()
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
        log(module, "whitelist loaded (${map.size} apps): ${map.keys}")
        return map
    }

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
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
            log(module, "hooked MiuiBaseNotifUtil.generateInnerNotifBean")
        } catch (e: Throwable) {
            logError(module, "hook failed: ${e.message}")
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
                log(module, "hooked onNotificationRemoved(sbn, rankingMap, reason)")
            } catch (e: Throwable) {
                logError(module, "onNotificationRemoved 3-param hook failed: ${e.message}")
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
                log(module, "hooked onNotificationRemoved(sbn)")
            } catch (e: Throwable) {
                logError(module, "onNotificationRemoved 1-param hook failed: ${e.message}")
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
        val context = HookUtils.getContext(classLoader) ?: return
        IslandDispatcher.cancel(context, proxyId)
    }

    private fun handleSbn(sbn: StatusBarNotification, module: XposedModule, classLoader: ClassLoader) {
        try {
            val pkg = sbn.packageName ?: return
            val notif = sbn.notification ?: return
            val extras = notif.extras ?: return
            val isHyperIslandProxy = extras.getString(EXTRA_OWNER) == OWNER_MARKER

            if (pkg == "com.android.systemui" &&
                notif.channelId == IslandDispatcher.CHANNEL_ID &&
                !isHyperIslandProxy) return

        val context = HookUtils.getContext(classLoader) ?: return

            val channelId = notif.channelId ?: ""
            val sourcePkg = extras.getString("hyperisland_source_pkg") ?: pkg
            val sourceChannelId = extras.getString("hyperisland_source_channel") ?: channelId
            extras.putString("hyperisland_source_pkg", sourcePkg)
            extras.putString("hyperisland_channel_id", sourceChannelId)
            extras.putString(EXTRA_OWNER, OWNER_MARKER)
            if (isHyperIslandProxy && extras.containsKey("miui.focus.param")) return

            if (!isHyperIslandProxy) {
                val allowedChannels = loadWhitelist(module)[pkg] ?: return
                if (allowedChannels.isNotEmpty() && channelId !in allowedChannels) return
            }

            val sceneDecision = SceneBehavior.resolve(
                context = context,
                surface = SceneBehavior.Surface.GENERIC_NOTIFICATION,
                sourcePackage = sourcePkg,
                channelId = sourceChannelId,
            )
            if (sceneDecision.shouldSuppress) return

            if (isMediaNotification(notif, extras)) {
                handleMediaNotification(pkg, channelId, sbn.id, sbn.key, notif, extras, context, module, classLoader)
                return
            }

            val defaultRestoreLockscreen = loadBooleanSetting("global:default_restore_lockscreen", "pref_default_restore_lockscreen", false)
            val restoreLockscreenRaw = loadChannelStringSetting("restore_lockscreen:$pkg/$channelId", "pref_channel_restore_lockscreen_${pkg}_$channelId", "default")
            val restoreLockscreen = resolveTriOpt(restoreLockscreenRaw, defaultRestoreLockscreen)

            if (restoreLockscreen == "on" && shouldRedactPrivateContentOnLockscreen(context, notif, module)) {
                log(module, "restoreLockscreen raw=$restoreLockscreenRaw, resolved=$restoreLockscreen, default=$defaultRestoreLockscreen")
                log(module, "skipping due to lockscreen restore")
                extras.remove("miui.focus.param")
                extras.remove("hyperisland_processed")
                return
            }

            if (extras.containsKey("miui.focus.param")) return

            val progressMax   = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
            val indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
            val hasProgressBar = progressMax > 0 && !indeterminate

            if (hasProgressBar) {
                if (extras.getBoolean("hyperisland_processed", false)) return
            }

            val cacheKey = "$pkg#${sbn.id}"
            val progressPercent: Int
            if (hasProgressBar) {
                val progressRaw = extras.getInt(Notification.EXTRA_PROGRESS, -1)
                if (progressRaw < 0) return
                progressPercent = (progressRaw * 100 / progressMax).coerceIn(0, 100)
                if (progressPercent in 0..99) {
                    if (lastProgressCache.size >= MAX_PROGRESS_CACHE_SIZE) lastProgressCache.clear()
                    lastProgressCache[cacheKey] = progressPercent
                }
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

            val actions: List<Notification.Action> = resolveNotificationActions(context, pkg, notif)

            val template = loadChannelTemplate(pkg, channelId)

            val appIconRaw = context.packageManager.getAppIcon(pkg)
            val largeIcon  = extractLargeIcon(extras)

            val iconMode = loadChannelStringSetting("icon:$pkg/$channelId", "pref_channel_icon_${pkg}_$channelId", "auto")
            val defaultFirstFloat        = loadBooleanSetting("global:default_first_float",        "pref_default_first_float",        false)
            val defaultEnableFloat       = loadBooleanSetting("global:default_enable_float",       "pref_default_enable_float",       false)
            val defaultMarquee           = loadBooleanSetting("global:default_marquee",            "pref_default_marquee",            false)
            val defaultFocusNotif        = loadBooleanSetting("global:default_focus_notif",        "pref_default_focus_notif",        true)
            val defaultAodText           = loadBooleanSetting("global:default_aod_text",           "pref_default_aod_text",           false)
            val defaultDynamicHighlightColor = loadBooleanSetting("global:default_dynamic_highlight_color", "pref_default_dynamic_highlight_color", false)
            val defaultOuterGlow = ConfigManager.getString("pref_default_outer_glow", "off")
            val defaultIslandOuterGlow = ConfigManager.getString(
                "pref_default_island_outer_glow",
                "off",
            )
            val defaultPreserveSmallIcon = loadBooleanSetting("global:default_preserve_small_icon","pref_default_preserve_small_icon", false)
            val defaultShowIslandIcon    = loadBooleanSetting("global:default_show_island_icon",   "pref_default_show_island_icon",   true)

            val focusNotif = resolveTriOpt(
                loadChannelStringSetting("focus:$pkg/$channelId", "pref_channel_focus_${pkg}_$channelId", "default"),
                defaultFocusNotif
            )
            val showNotification = loadChannelStringSetting(
                "show_notification:$pkg/$channelId",
                "pref_channel_show_notification_${pkg}_$channelId",
                "on",
            )
            val preserveStatusBarSmallIcon = resolveTriOpt(
                loadChannelStringSetting("preserve_small_icon:$pkg/$channelId", "pref_channel_preserve_small_icon_${pkg}_$channelId", "default"),
                defaultPreserveSmallIcon
            )
            val showIslandIcon = resolveTriOpt(
                loadChannelStringSetting("show_island_icon:$pkg/$channelId", "pref_channel_show_island_icon_${pkg}_$channelId", "default"),
                defaultShowIslandIcon
            )
            val firstFloat = resolveTriOpt(
                loadChannelStringSetting("first_float:$pkg/$channelId", "pref_channel_first_float_${pkg}_$channelId", "default"),
                defaultFirstFloat
            )
            val enableFloatMode = resolveTriOpt(
                loadChannelStringSetting("efloat:$pkg/$channelId", "pref_channel_enable_float_${pkg}_$channelId", "default"),
                defaultEnableFloat
            )
            val effectiveFirstFloat = sceneDecision.applyToTriOpt(firstFloat)
            val effectiveEnableFloat = sceneDecision.applyToTriOpt(enableFloatMode)
            val islandTimeoutStr = loadChannelStringSetting(
                "timeout:$pkg/$channelId", "pref_channel_timeout_${pkg}_$channelId", "5"
            )
            val isOngoing = (notif.flags and Notification.FLAG_ONGOING_EVENT) != 0
            val marqueeEnabled = resolveTriOpt(
                loadChannelStringSetting(
                    "marquee:$pkg/$channelId",
                    "pref_channel_marquee_${pkg}_$channelId",
                    "default",
                ),
                defaultMarquee,
            ) == "on"
            val marqueeAutoHide = loadChannelStringSetting(
                "marquee_auto_hide:$pkg/$channelId",
                "pref_channel_marquee_auto_hide_${pkg}_$channelId",
                "default",
            ).let { value ->
                if (value == "default") {
                    ConfigManager.getString("pref_default_marquee_auto_hide", "off")
                } else {
                    value
                }
            }
            val overrideMarqueeTimeout = marqueeEnabled && !isOngoing &&
                marqueeAutoHide in setOf("1_override", "2_override")
            val islandTimeout = if (overrideMarqueeTimeout) {
                Int.MAX_VALUE
            } else {
                islandTimeoutStr.toIntOrNull() ?: 5
            }
            val renderer = loadChannelStringSetting(
                "renderer:$pkg/$channelId", "pref_channel_renderer_${pkg}_$channelId", "image_text_with_buttons_4"
            )
            val focusCustomizationJson = loadChannelStringSetting(
                "focus_custom:$pkg/$channelId",
                "pref_channel_focus_custom_${pkg}_$channelId",
                ""
            ).takeIf { it.isNotBlank() }
            val islandCustomizationJson = loadChannelStringSetting(
                "island_custom:$pkg/$channelId",
                "pref_channel_island_custom_${pkg}_$channelId",
                ""
            ).takeIf { it.isNotBlank() }
            val aodText = loadChannelStringSetting(
                "aod_text:$pkg/$channelId",
                "pref_channel_aod_text_${pkg}_$channelId",
                "default"
            ).let { resolveTriOpt(it, defaultAodText) }
            val aodCustomizationJson = loadChannelStringSetting(
                "aod_custom:$pkg/$channelId",
                "pref_channel_aod_custom_${pkg}_$channelId",
                ""
            ).takeIf { it.isNotBlank() }

            val islandEnabled = loadChannelStringSetting(
                "island_enabled:$pkg/$channelId",
                "pref_channel_island_enabled_${pkg}_$channelId",
                "true"
            ) != "false"
            val highlightColor = loadChannelStringSetting(
                "highlight_color:$pkg/$channelId", "pref_channel_highlight_color_${pkg}_$channelId", ""
            ).takeIf { it.isNotBlank() }
            val dynamicHighlightColorRaw = loadChannelStringSetting(
                "dynamic_highlight_color:$pkg/$channelId",
                "pref_channel_dynamic_highlight_color_${pkg}_$channelId",
                "default"
            )
            val dynamicHighlightColorMode = when (dynamicHighlightColorRaw) {
                "on", "off", "dark", "darker" -> dynamicHighlightColorRaw
                else -> if (defaultDynamicHighlightColor) "on" else "off"
            }
            val resolvedHighlightColor = resolveHighlightColor(
                context = context,
                iconMode = iconMode,
                notifIcon = notif.smallIcon,
                largeIcon = largeIcon,
                appIconRaw = appIconRaw,
                manualHighlightColor = highlightColor,
                dynamicMode = dynamicHighlightColorMode,
            )
            val showLeftHighlight = loadChannelStringSetting(
                "show_left_highlight:$pkg/$channelId", "pref_channel_show_left_highlight_${pkg}_$channelId", "off"
            ) == "on"
            val showRightHighlight = loadChannelStringSetting(
                "show_right_highlight:$pkg/$channelId", "pref_channel_show_right_highlight_${pkg}_$channelId", "off"
            ) == "on"
            val showLeftNarrowFont = loadChannelStringSetting(
                "show_left_narrow_font:$pkg/$channelId", "pref_channel_show_left_narrow_font_${pkg}_$channelId", "off"
            ) == "on"
            val showRightNarrowFont = loadChannelStringSetting(
                "show_right_narrow_font:$pkg/$channelId", "pref_channel_show_right_narrow_font_${pkg}_$channelId", "off"
            ) == "on"
            val outerGlowRaw = loadChannelStringSetting(
                "outer_glow:$pkg/$channelId", "pref_channel_outer_glow_${pkg}_$channelId", "default"
            )
            val resolvedOuterGlowMode = resolveGlowMode(outerGlowRaw, defaultOuterGlow)
            val islandOuterGlowRaw = loadChannelStringSetting(
                "island_outer_glow:$pkg/$channelId",
                "pref_channel_island_outer_glow_${pkg}_$channelId",
                "default"
            )
            val resolvedIslandOuterGlowMode = resolveGlowMode(islandOuterGlowRaw, defaultIslandOuterGlow)
            val islandOuterGlowColor = loadChannelStringSetting(
                "island_outer_glow_color:$pkg/$channelId",
                "pref_channel_island_outer_glow_color_${pkg}_$channelId",
                ConfigManager.getString("pref_default_island_outer_glow_color", ""),
            ).takeIf { it.isNotBlank() }
            val outEffectColor = loadChannelStringSetting(
                "out_effect_color:$pkg/$channelId",
                "pref_channel_out_effect_color_${pkg}_$channelId",
                ConfigManager.getString("pref_default_out_effect_color", ""),
            ).takeIf { it.isNotBlank() }
            val resolvedOutEffectColor = when (resolvedOuterGlowMode) {
                "follow_dynamic" -> resolvedHighlightColor
                else -> outEffectColor
            }
            val resolvedIslandOuterGlowColor = when (resolvedIslandOuterGlowMode) {
                "follow_dynamic" -> resolvedHighlightColor
                else -> islandOuterGlowColor
            }
            resolvedOutEffectColor?.let { extras.putString("hyperisland_focus_out_effect_color", it) }
            resolvedIslandOuterGlowColor?.let { extras.putString("hyperisland_island_outer_glow_color", it) }

            if (resolvedIslandOuterGlowMode != "off") {
                extras.putString("miui.bigIsland.effect.src", EFFECT_SRC)
                extras.putString("miui.effect.src", EFFECT_SRC)
            } else {
                extras.remove("miui.bigIsland.effect.src")
                extras.remove("miui.effect.src")
            }

            log(module, "$pkg/$channelId | $title |  template=$template")
//            log(module, "$pkg/$channelId | $title | $progressPercent% | template=$template | buttons=${actions.size} | largeIcon=${largeIcon != null} | preserveSmallIcon=$preserveStatusBarSmallIcon")

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
                    focusNotif      = focusNotif,
                    showNotification = showNotification,
                    preserveStatusBarSmallIcon = preserveStatusBarSmallIcon,
                    showIslandIcon  = showIslandIcon,
                    firstFloat      = effectiveFirstFloat,
                    enableFloatMode = effectiveEnableFloat,
                    islandTimeout   = islandTimeout,
                    isOngoing       = isOngoing,
                    contentIntent   = notif.contentIntent,
                    renderer        = renderer,
                    highlightColor  = resolvedHighlightColor,
                    showLeftHighlightColor = showLeftHighlight,
                    showRightHighlightColor = showRightHighlight,
                    showLeftNarrowFont = showLeftNarrowFont,
                    showRightNarrowFont = showRightNarrowFont,
                    outerGlow = resolvedOuterGlowMode != "off",
                    islandOuterGlow = resolvedIslandOuterGlowMode != "off",
                    islandOuterGlowColor = resolvedIslandOuterGlowColor,
                    outEffectColor = resolvedOutEffectColor,
                    focusCustomizationJson = focusCustomizationJson,
                    islandCustomizationJson = islandCustomizationJson,
                    aodText = aodText,
                    aodCustomizationJson = aodCustomizationJson,
                    islandEnabled = islandEnabled,
                ),
            )

            if (trackedForCancel.size >= MAX_TRACKED_CANCEL_SIZE) trackedForCancel.clear()
            trackedForCancel["$pkg#${sbn.id}"] = IslandDispatcher.NOTIF_ID

        } catch (e: Throwable) {
            logError(module, "handleSbn error: ${e.message}")
        }
    }

    private fun resolveHighlightColor(
        context: Context,
        iconMode: String,
        notifIcon: Icon?,
        largeIcon: Icon?,
        appIconRaw: Icon?,
        manualHighlightColor: String?,
        dynamicMode: String,
    ): String? {
        val mode = dynamicMode.trim().lowercase()
        if (mode != "on" && mode != "dark" && mode != "darker") {
            return manualHighlightColor
        }
        val fallback = Icon.createWithResource(context, R.drawable.ic_dialog_info)
        val iconForColor = when (iconMode) {
            "notif_small" -> notifIcon ?: fallback
            "notif_large" -> largeIcon ?: notifIcon ?: fallback
            "app_icon" -> appIconRaw ?: fallback
            else -> largeIcon ?: notifIcon ?: fallback
        }.toRounded(context)

        return iconForColor.resolveDynamicHighlightColor(context, mode) ?: manualHighlightColor
    }

    private fun shouldRedactPrivateContentOnLockscreen(
        context: Context,
        notif: Notification,
        module: XposedModule,
    ): Boolean {
        if (!isKeyguardLocked(context, module)) return false
        val vis = notif.visibility
        log(module, "notification visibility = $vis (PUBLIC=${Notification.VISIBILITY_PUBLIC}, PRIVATE=${Notification.VISIBILITY_PRIVATE}, SECRET=${Notification.VISIBILITY_SECRET})")
        if (vis == Notification.VISIBILITY_PUBLIC) return false
        // VISIBILITY_PRIVATE 或 VISIBILITY_SECRET 都应该跳过处理
        return true
    }

    private fun isKeyguardLocked(context: Context, module: XposedModule): Boolean {
        val keyguardManager = context.getSystemService(KeyguardManager::class.java) ?: return false
        val locked = keyguardManager.isKeyguardLocked
        log(module, "isKeyguardLocked = $locked")
        return locked
    }

    private fun isMediaNotification(notif: Notification, extras: Bundle): Boolean {
        if (extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return true
        val template = extras.getString(Notification.EXTRA_TEMPLATE) ?: return false
        return template.contains("MediaStyle", ignoreCase = true)
    }

    private fun resolveNotificationActions(
        context: Context,
        pkg: String,
        notif: Notification,
    ): List<Notification.Action> {
        val fallbackIntent = notif.contentIntent ?: createLaunchAppIntent(context, pkg)
        return notif.actions?.take(2)?.map { action ->
            if (action.hasRemoteInput() && fallbackIntent != null) {
                Notification.Action.Builder(action.icon, action.title, fallbackIntent).build()
            } else {
                action
            }
        } ?: emptyList()
    }

    private fun createLaunchAppIntent(context: Context, pkg: String): PendingIntent? {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        } ?: return null
        return PendingIntent.getActivity(
            context,
            pkg.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun Notification.Action.hasRemoteInput(): Boolean {
        return remoteInputs?.isNotEmpty() == true
    }

    private fun handleMediaNotification(
        pkg: String,
        channelId: String,
        notifId: Int,
        sbnKey: String?,
        notif: Notification,
        extras: Bundle,
        context: Context,
        module: XposedModule,
        classLoader: ClassLoader,
    ) {
//        val hasMediaSession = extras.containsKey(Notification.EXTRA_MEDIA_SESSION)
//        val template = extras.getString(Notification.EXTRA_TEMPLATE).orEmpty()
//        val mediaFocus = extras.getString("miui.focus.param.media")
//        log(
//            module, "media notification detected: pkg=$pkg channel=$channelId id=$notifId hasSession=$hasMediaSession template=$template mediaFocus=${!mediaFocus.isNullOrBlank()}",
//        )

        if (!ConfigManager.getBoolean("pref_media_island_enabled_$pkg", true)) {
            cancelPostedNotification(sbnKey, module, classLoader)
            log(module, "media notification canceled: pkg=$pkg channel=$channelId id=$notifId key=$sbnKey")
            return
        }

        if (ConfigManager.getBoolean("pref_media_island_normal_notification_$pkg", false)) {
            extras.remove(Notification.EXTRA_MEDIA_SESSION)
            extras.remove(Notification.EXTRA_TEMPLATE)
            extras.remove("miui.focus.param.media")
            extras.remove("miui.bigIsland.effect.src")
            extras.remove("miui.effect.src")
            extras.putBoolean("hyperisland_processed", true)
            //log(module, "media notification downgraded to normal: pkg=$pkg channel=$channelId id=$notifId")
            return
        }

        val defaultIslandOuterGlow = ConfigManager.getString(
            "pref_default_island_outer_glow",
            "off",
        )
        val islandOuterGlowRaw = ConfigManager.getString(
            "pref_media_island_outer_glow_$pkg",
            "default",
        )
        val resolvedIslandOuterGlowMode = resolveGlowMode(
            islandOuterGlowRaw,
            defaultIslandOuterGlow,
        )
        if (resolvedIslandOuterGlowMode != "off") {
            extras.putString("miui.bigIsland.effect.src", EFFECT_SRC)
            extras.putString("miui.effect.src", EFFECT_SRC)
        } else {
            extras.remove("miui.bigIsland.effect.src")
            extras.remove("miui.effect.src")
        }
        extras.putString("hyperisland_channel_id", "media")
        val manualIslandOuterGlowColor = ConfigManager.getString(
            "pref_media_island_outer_glow_color_$pkg",
            "",
        ).takeIf { it.isNotBlank() }
        val resolvedIslandOuterGlowColor = when (resolvedIslandOuterGlowMode) {
            "follow_dynamic" -> resolveHighlightColor(
                context = context,
                iconMode = "auto",
                notifIcon = notif.smallIcon,
                largeIcon = extractLargeIcon(extras),
                appIconRaw = context.packageManager.getAppIcon(pkg),
                manualHighlightColor = manualIslandOuterGlowColor,
                dynamicMode = "on",
            )
            else -> manualIslandOuterGlowColor
        }
        resolvedIslandOuterGlowColor?.let { extras.putString("hyperisland_island_outer_glow_color", it) }
        IslandOuterGlowHook.recordMediaGlowRequest(
            pkg = pkg,
            enabled = resolvedIslandOuterGlowMode != "off",
            color = extras.getString("hyperisland_island_outer_glow_color"),
            module = module,
        )
        log(
            module,
            "media glow request: pkg=$pkg channel=media id=$notifId raw=$islandOuterGlowRaw resolved=$resolvedIslandOuterGlowMode big=${extras.getString("miui.bigIsland.effect.src")} effect=${extras.getString("miui.effect.src")} color=${extras.getString("hyperisland_island_outer_glow_color")}",
        )
    }

    private fun cancelPostedNotification(
        sbnKey: String?,
        module: XposedModule,
        classLoader: ClassLoader,
    ) {
        if (sbnKey.isNullOrBlank()) return
        runCatching {
            val listenerClass = classLoader.loadClass("android.service.notification.NotificationListenerService")
            val method = findMethod(listenerClass, "cancelNotification", String::class.java)
            val context = HookUtils.getContext(classLoader) ?: return@runCatching
            if (listenerClass.isInstance(context)) {
                method.invoke(context, sbnKey)
            }
        }.onFailure {
            logError(module, "cancel media notification failed: ${it.message}")
        }
    }

    private fun extractLargeIcon(extras: Bundle): Icon? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                @Suppress("DEPRECATION")
                val icon = extras.getParcelable<Icon>(
                    Notification.EXTRA_LARGE_ICON
                )
                if (icon != null) return icon
            }
            @Suppress("DEPRECATION")
            val bitmap = extras.getParcelable<Bitmap>(
                Notification.EXTRA_LARGE_ICON
            )
            if (bitmap != null) Icon.createWithBitmap(bitmap) else null
        } catch (_: Exception) { null }
    }

    private fun getContext(classLoader: ClassLoader): Context? {
        return try {
            val at = classLoader.loadClass("android.app.ActivityThread")
            at.getMethod("currentApplication").invoke(null) as? Context
        } catch (_: Exception) {
            try {
                val at = classLoader.loadClass("android.app.ActivityThread")
                (at.getMethod("getSystemContext").invoke(null) as? Context)?.applicationContext
            } catch (_: Exception) { null }
        }
    }

    private fun findMethod(clazz: Class<*>, name: String, vararg paramTypes: Class<*>): Method {
        var c: Class<*>? = clazz
        while (c != null) {
            try { return c.getDeclaredMethod(name, *paramTypes) } catch (_: NoSuchMethodException) {}
            c = c.superclass
        }
        throw NoSuchMethodException("$name not found in ${clazz.name} hierarchy")
    }
}
