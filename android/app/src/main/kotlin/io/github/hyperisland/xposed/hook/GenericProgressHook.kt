package io.github.hyperisland.xposed

import android.app.Notification
import android.service.notification.StatusBarNotification
import io.github.hyperisland.getAppIcon
import io.github.hyperisland.xposed.hook.MarqueeHook
import io.github.hyperisland.xposed.templates.GenericProgressIslandNotification
import io.github.hyperisland.xposed.templates.NotificationIslandNotification
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

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
 * 必须在 generateInnerNotifBean 之前（beforeHookedMethod）写入 island extras，
 * 否则 bean 已经用原始 extras 创建完毕，后续修改不影响岛的触发判断。
 *
 * 通过白名单（包名 → 渠道集合）精确控制处理范围，空渠道集合表示该包全部渠道。
 */
class GenericProgressHook : IXposedHookLoadPackage {

    companion object {
        @Volatile private var cachedWhitelist: Map<String, Set<String>>? = null
        private val cachedTemplates = mutableMapOf<String, String>()
        private val cachedChannelSettings = mutableMapOf<String, String>()

        @Volatile private var observerRegistered = false

        /** 在 SystemUI 进程首次处理通知时注册，监听设置变化并实时清空缓存。 */
        fun ensureObserver(context: android.content.Context) {
            if (observerRegistered) return
            val settingsUri = android.net.Uri.parse("content://io.github.hyperisland.settings/")
            context.contentResolver.registerContentObserver(
                settingsUri, true,
                object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        clearAllCaches("root")
                    }

                    override fun onChange(selfChange: Boolean, uri: android.net.Uri?) {
                        val segment = uri?.lastPathSegment
                        when {
                            segment == null || segment.isBlank() -> clearAllCaches("root")
                            segment == "pref_generic_whitelist" || segment.startsWith("pref_channels_") -> {
                                cachedWhitelist = null
                                XposedBridge.log("HyperIsland[Generic]: whitelist cache cleared for $segment")
                            }
                            segment.startsWith("pref_channel_template_") -> {
                                val suffix = segment.removePrefix("pref_channel_template_")
                                cachedTemplates.removeSuffixMatch(suffix)
                                XposedBridge.log("HyperIsland[Generic]: template cache cleared for $segment")
                            }
                            segment.startsWith("pref_channel_") ||
                            segment == "pref_preserve_status_bar_small_icon" ||
                            segment == "pref_marquee_speed" ||
                            segment == "pref_round_icon"  -> {
                                cachedChannelSettings.clear()
                                XposedBridge.log("HyperIsland[Generic]: channel settings cache cleared for $segment")
                            }
                            else -> clearAllCaches(segment)
                        }
                    }
                }
            )
            observerRegistered = true
            XposedBridge.log("HyperIsland[Generic]: ContentObserver registered in SystemUI")
        }

        // 进度缓存：key 为 "packageName#notifId"，记录每条通知最后一次已知进度（0-100）。
        // 用于通知进度条消失后（暂停/等待）回显上次进度。
        private val lastProgressCache = mutableMapOf<String, Int>()

        // 取消追踪：key 为 "packageName#sbnId" → 代理通知 ID
        // 原始通知被移除时，据此调用 IslandDispatcher.cancel() 清除首次发送状态。
        private val trackedForCancel = mutableMapOf<String, Int>()

        /** 通用字符串设置懒加载，带缓存。 */
        private fun loadChannelStringSetting(
            context: android.content.Context,
            cacheKey: String,
            prefKey: String,
            default: String
        ): String {
            cachedChannelSettings[cacheKey]?.let { return it }
            return try {
                val uri = android.net.Uri.parse(
                    "content://io.github.hyperisland.settings/$prefKey"
                )
                val value = context.contentResolver
                    .query(uri, null, null, null, null)
                    ?.use { if (it.moveToFirst()) it.getString(0).takeIf { s -> s.isNotBlank() } else null }
                    ?: default
                cachedChannelSettings[cacheKey] = value
                value
            } catch (e: Exception) {
                XposedBridge.log("HyperIsland[Generic]: loadChannelStringSetting($prefKey) failed: ${e.message}")
                default
            }
        }

        private fun loadBooleanSetting(
            context: android.content.Context,
            cacheKey: String,
            prefKey: String,
            default: Boolean
        ): Boolean {
            cachedChannelSettings[cacheKey]?.let { return it == "1" }
            return try {
                val uri = android.net.Uri.parse(
                    "content://io.github.hyperisland.settings/$prefKey"
                )
                val value = context.contentResolver
                    .query(uri, null, null, null, null)
                    ?.use { if (it.moveToFirst()) it.getInt(0) != 0 else default }
                    ?: default
                cachedChannelSettings[cacheKey] = if (value) "1" else "0"
                value
            } catch (e: Exception) {
                XposedBridge.log("HyperIsland[Generic]: loadBooleanSetting($prefKey) failed: ${e.message}")
                default
            }
        }

        private fun resolveTriStateBoolean(global: Boolean, channelValue: String): Boolean {
            return when (channelValue) {
                "on" -> true
                "off" -> false
                else -> global
            }
        }

        /** 将渠道 tri-state 字符串结合全局默认解析为 "on"/"off"。 */
        private fun resolveTriOpt(channelValue: String, globalDefault: Boolean): String =
            when (channelValue) {
                "on"  -> "on"
                "off" -> "off"
                else  -> if (globalDefault) "on" else "off"
            }

        private fun clearAllCaches(reason: String) {
            cachedWhitelist = null
            cachedTemplates.clear()
            cachedChannelSettings.clear()
            XposedBridge.log("HyperIsland[Generic]: settings changed, cache cleared ($reason)")
        }

        private fun MutableMap<String, String>.removeSuffixMatch(suffix: String) {
            if (suffix.isBlank()) {
                clear()
                return
            }
            val matchedKeys = keys.filter { it.endsWith(suffix) }
            matchedKeys.forEach { remove(it) }
        }

        /** 读取指定渠道的模板设置，结果会懒缓存，SystemUI 重启后刷新。 */
        fun loadChannelTemplate(
            context: android.content.Context,
            pkg: String,
            channelId: String
        ): String {
            val cacheKey = "$pkg/$channelId"
            cachedTemplates[cacheKey]?.let { return it }
            return try {
                val key = "pref_channel_template_${pkg}_$channelId"
                val uri = android.net.Uri.parse(
                    "content://io.github.hyperisland.settings/$key"
                )
                val template = context.contentResolver
                    .query(uri, null, null, null, null)
                    ?.use { if (it.moveToFirst()) it.getString(0).takeIf { s -> s.isNotBlank() } else null }
                    ?: NotificationIslandNotification.TEMPLATE_ID
                cachedTemplates[cacheKey] = template
                template
            } catch (e: Exception) {
                XposedBridge.log("HyperIsland[Generic]: loadChannelTemplate failed: ${e.message}")
                NotificationIslandNotification.TEMPLATE_ID
            }
        }

        private fun loadWhitelist(context: android.content.Context): Map<String, Set<String>> {
            cachedWhitelist?.let { return it }
            return try {
                val uri = android.net.Uri.parse(
                    "content://io.github.hyperisland.settings/pref_generic_whitelist"
                )
                val csv = context.contentResolver.query(uri, null, null, null, null)
                    ?.use { if (it.moveToFirst()) it.getString(0) else "" }
                    ?: ""
                val map = csv.split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .associate { pkg ->
                        val channelUri = android.net.Uri.parse(
                            "content://io.github.hyperisland.settings/pref_channels_$pkg"
                        )
                        val channelCsv = context.contentResolver
                            .query(channelUri, null, null, null, null)
                            ?.use { if (it.moveToFirst()) it.getString(0) else "" }
                            ?: ""
                        val channels = if (channelCsv.isBlank()) emptySet()
                        else channelCsv.split(",").filter { it.isNotBlank() }.toSet()
                        pkg to channels
                    }
                if (map.isNotEmpty()) cachedWhitelist = map
                XposedBridge.log("HyperIsland[Generic]: whitelist loaded (${map.size} apps): ${map.keys}")
                map
            } catch (e: Exception) {
                XposedBridge.log("HyperIsland[Generic]: loadWhitelist failed: ${e.message}")
                emptyMap()
            }
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.android.systemui") return

        try {
            XposedHelpers.findAndHookMethod(
                "com.miui.systemui.notification.MiuiBaseNotifUtil",
                lpparam.classLoader,
                "generateInnerNotifBean",
                StatusBarNotification::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val sbn = param.args[0] as? StatusBarNotification ?: return
                        handleSbn(sbn, lpparam)
                    }
                }
            )
            XposedBridge.log("HyperIsland[Generic]: hooked MiuiBaseNotifUtil.generateInnerNotifBean")
        } catch (e: Throwable) {
            XposedBridge.log("HyperIsland[Generic]: hook failed: ${e.message}")
        }

        val cancelCallback = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val sbn = param.args[0] as? StatusBarNotification ?: return
                val key = "${sbn.packageName}#${sbn.id}"
                val proxyId = trackedForCancel.remove(key) ?: return
                val context = getContext(lpparam) ?: return
                IslandDispatcher.cancel(context, proxyId)
            }
        }

        // 优先 hook 3 参数版（Android 8+ 实际调用路径）
        var cancelHooked = false
        try {
            val rankingMapClass = lpparam.classLoader.loadClass(
                "android.service.notification.NotificationListenerService\$RankingMap"
            )
            XposedHelpers.findAndHookMethod(
                "android.service.notification.NotificationListenerService",
                lpparam.classLoader,
                "onNotificationRemoved",
                StatusBarNotification::class.java,
                rankingMapClass,
                Int::class.javaPrimitiveType!!,
                cancelCallback
            )
            cancelHooked = true
            XposedBridge.log("HyperIsland[Generic]: hooked onNotificationRemoved(sbn, rankingMap, reason)")
        } catch (e: Throwable) {
            XposedBridge.log("HyperIsland[Generic]: onNotificationRemoved 3-param hook failed: ${e.message}")
        }

        // 降级到单参数版本
        if (!cancelHooked) {
            try {
                XposedHelpers.findAndHookMethod(
                    "android.service.notification.NotificationListenerService",
                    lpparam.classLoader,
                    "onNotificationRemoved",
                    StatusBarNotification::class.java,
                    cancelCallback
                )
                XposedBridge.log("HyperIsland[Generic]: hooked onNotificationRemoved(sbn)")
            } catch (e: Throwable) {
                XposedBridge.log("HyperIsland[Generic]: onNotificationRemoved 1-param hook failed: ${e.message}")
            }
        }
    }

    private fun handleSbn(sbn: StatusBarNotification, lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val pkg = sbn.packageName ?: return

            // IslandDispatcher 代发的代理通知（com.android.systemui / hyperisland_dispatcher）
            // 会再次触发此回调，但不应重置 pendingMarqueeEnabled——那是原始通知处理时设置的，
            // MarqueeHook 需要在代理通知的大岛视图创建时读取该值。
            if (pkg == "com.android.systemui" &&
                sbn.notification?.channelId == IslandDispatcher.CHANNEL_ID) return

            // 提前重置，防止上一条通知的 true 值在本次提前返回时污染后续岛视图
            MarqueeHook.pendingMarqueeEnabled = false

            // 先取 context，用于加载白名单
            val context = getContext(lpparam) ?: return
            ensureObserver(context)

            // 白名单检查（动态从 SettingsProvider 读取）
            val allowedChannels = loadWhitelist(context)[pkg] ?: return
            val notif = sbn.notification ?: return
            val channelId = notif.channelId ?: ""
            if (allowedChannels.isNotEmpty() && channelId !in allowedChannels) return

            val extras = notif.extras ?: return

            // 跳过媒体通知（MediaStyle），避免对音乐/播放器等通知二次处理
            if (isMediaNotification(notif, extras)) return

            // 跳过已自带超级岛参数的通知，避免重复处理导致 SystemUI 崩溃
            if (extras.containsKey("miui.focus.param")) return

            // ── 进度条检测（需先于 flag 检查，以便状态变化通知绕过缓存标记）────────
            val progressMax    = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
            val indeterminate  = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
            val hasProgressBar = progressMax > 0 && !indeterminate

            // 跳过已处理的通知；无进度条（暂停/完成/等待）属于状态变化，需强制重新处理
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
                // 缓存本次进度，供后续无进度条的状态变化通知回显
                if (progressPercent in 0..99) lastProgressCache[cacheKey] = progressPercent
            } else {
                // 无进度条：尝试回填上次已知进度（如暂停时保留进度显示）；无缓存则为 -1
                progressPercent = lastProgressCache[cacheKey] ?: -1
            }

            // ── 提取标题 / 副标题 ─────────────────────────────────────────────────
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

            val template = loadChannelTemplate(context, pkg, channelId)

            val appIconRaw = context.packageManager.getAppIcon(pkg)
            val largeIcon  = extractLargeIcon(extras)

            val iconMode = loadChannelStringSetting(
                context, "icon:$pkg/$channelId",
                "pref_channel_icon_${pkg}_$channelId", "auto"
            )
            val defaultFirstFloat       = loadBooleanSetting(context, "global:default_first_float",        "pref_default_first_float",        false)
            val defaultEnableFloat      = loadBooleanSetting(context, "global:default_enable_float",       "pref_default_enable_float",       false)
            val defaultMarquee          = loadBooleanSetting(context, "global:default_marquee",            "pref_default_marquee",            false)
            val defaultFocusNotif       = loadBooleanSetting(context, "global:default_focus_notif",        "pref_default_focus_notif",        true)
            val defaultPreserveSmallIcon = loadBooleanSetting(context, "global:default_preserve_small_icon", "pref_default_preserve_small_icon", false)

            val focusNotif = resolveTriOpt(
                loadChannelStringSetting(context, "focus:$pkg/$channelId", "pref_channel_focus_${pkg}_$channelId", "default"),
                defaultFocusNotif
            )
            val preserveStatusBarSmallIcon = resolveTriOpt(
                loadChannelStringSetting(context, "preserve_small_icon:$pkg/$channelId", "pref_channel_preserve_small_icon_${pkg}_$channelId", "default"),
                defaultPreserveSmallIcon
            )
            val firstFloat = resolveTriOpt(
                loadChannelStringSetting(context, "first_float:$pkg/$channelId", "pref_channel_first_float_${pkg}_$channelId", "default"),
                defaultFirstFloat
            )
            val enableFloatMode = resolveTriOpt(
                loadChannelStringSetting(context, "efloat:$pkg/$channelId", "pref_channel_enable_float_${pkg}_$channelId", "default"),
                defaultEnableFloat
            )
            val islandTimeoutStr = loadChannelStringSetting(
                context, "timeout:$pkg/$channelId",
                "pref_channel_timeout_${pkg}_$channelId", "5"
            )
            val islandTimeout = islandTimeoutStr.toIntOrNull() ?: 5
            val focusIconMode = loadChannelStringSetting(
                context, "focus_icon:$pkg/$channelId",
                "pref_channel_focus_icon_${pkg}_$channelId", "auto"
            )
            val isOngoing = (notif.flags and Notification.FLAG_ONGOING_EVENT) != 0
            val marqueeEnabled = !isOngoing && resolveTriStateBoolean(
                defaultMarquee,
                loadChannelStringSetting(context, "marquee:$pkg/$channelId", "pref_channel_marquee_${pkg}_$channelId", "default")
            )
            MarqueeHook.pendingMarqueeEnabled = marqueeEnabled
            val renderer = loadChannelStringSetting(
                context, "renderer:$pkg/$channelId",
                "pref_channel_renderer_${pkg}_$channelId", "image_text_with_buttons_4"
            )

            XposedBridge.log(
                "HyperIsland[Generic]: $pkg/$channelId | $title | $progressPercent% | template=$template | buttons=${actions.size} | largeIcon=${largeIcon != null} | preserveSmallIcon=$preserveStatusBarSmallIcon"
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
            // 记录本条通知对应的代理通知 ID，供 onNotificationRemoved 同步取消
            trackedForCancel["$pkg#${sbn.id}"] = IslandDispatcher.NOTIF_ID

        } catch (e: Throwable) {
            XposedBridge.log("HyperIsland[Generic]: handleSbn error: ${e.message}")
        }
    }

    /**
     * 判断是否为媒体通知（MediaStyle）。
     * 满足以下任一条件即视为媒体通知，直接跳过处理：
     *   1. extras 含 EXTRA_MEDIA_SESSION —— 调用了 setMediaSession()
     *   2. EXTRA_TEMPLATE 包含 "MediaStyle" —— 使用了 Notification.MediaStyle
     */
    private fun isMediaNotification(notif: Notification, extras: android.os.Bundle): Boolean {
        if (extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return true
        val template = extras.getString(Notification.EXTRA_TEMPLATE) ?: return false
        return template.contains("MediaStyle", ignoreCase = true)
    }

    /**
     * 从通知 extras 提取 largeIcon（头像、封面、应用大图标等）。
     * Android 7+ 的 EXTRA_LARGE_ICON 可能是 Icon 或 Bitmap，兼容两种形式。
     */
    private fun extractLargeIcon(extras: android.os.Bundle): android.graphics.drawable.Icon? {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                // Android 6+：尝试直接取 Icon 类型
                @Suppress("DEPRECATION")
                val icon = extras.getParcelable<android.graphics.drawable.Icon>(
                    android.app.Notification.EXTRA_LARGE_ICON
                )
                if (icon != null) return icon
            }
            // 兜底：Bitmap 类型（旧版通知）
            @Suppress("DEPRECATION")
            val bitmap = extras.getParcelable<android.graphics.Bitmap>(
                android.app.Notification.EXTRA_LARGE_ICON
            )
            if (bitmap != null) android.graphics.drawable.Icon.createWithBitmap(bitmap) else null
        } catch (_: Exception) { null }
    }

    private fun getContext(lpparam: XC_LoadPackage.LoadPackageParam): android.content.Context? {
        return try {
            val at = lpparam.classLoader.loadClass("android.app.ActivityThread")
            at.getMethod("currentApplication").invoke(null) as? android.content.Context
        } catch (_: Exception) {
            try {
                val at = lpparam.classLoader.loadClass("android.app.ActivityThread")
                (at.getMethod("getSystemContext").invoke(null) as? android.content.Context)?.applicationContext
            } catch (_: Exception) { null }
        }
    }
}
