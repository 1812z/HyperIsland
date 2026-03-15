package com.example.hyperisland.xposed

import android.app.Notification
import android.service.notification.StatusBarNotification
import com.example.hyperisland.xposed.templates.GenericProgressIslandNotification
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 通用进度条通知 Hook — 在 SystemUI 进程内 Hook MiuiBaseNotifUtil.generateInnerNotifBean()。
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
        // 白名单缓存：首次调用时从 SettingsProvider 加载，SystemUI 重启后自动刷新。
        // 用户修改白名单后需重启 SystemUI 生效（与其他设置项行为一致）。
        @Volatile private var cachedWhitelist: Map<String, Set<String>>? = null

        // 渠道模板缓存：key 为 "packageName/channelId"，首次遇到时懒加载。
        private val cachedTemplates = mutableMapOf<String, String>()

        // 渠道级额外设置缓存，key 带前缀以区分不同设置项。
        private val cachedChannelSettings = mutableMapOf<String, String>()

        // 进度缓存：key 为 "packageName#notifId"，记录每条通知最后一次已知进度（0-100）。
        // 用于通知进度条消失后（暂停/等待）回显上次进度。
        private val lastProgressCache = mutableMapOf<String, Int>()

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
                    "content://com.example.hyperisland.settings/$prefKey"
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
                    "content://com.example.hyperisland.settings/$key"
                )
                val template = context.contentResolver
                    .query(uri, null, null, null, null)
                    ?.use { if (it.moveToFirst()) it.getString(0).takeIf { s -> s.isNotBlank() } else null }
                    ?: GenericProgressIslandNotification.id
                cachedTemplates[cacheKey] = template
                template
            } catch (e: Exception) {
                XposedBridge.log("HyperIsland[Generic]: loadChannelTemplate failed: ${e.message}")
                GenericProgressIslandNotification.id
            }
        }

        private fun loadWhitelist(context: android.content.Context): Map<String, Set<String>> {
            cachedWhitelist?.let { return it }
            return try {
                val uri = android.net.Uri.parse(
                    "content://com.example.hyperisland.settings/pref_generic_whitelist"
                )
                val csv = context.contentResolver.query(uri, null, null, null, null)
                    ?.use { if (it.moveToFirst()) it.getString(0) else "" }
                    ?: ""
                val map = csv.split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .associate { pkg ->
                        val channelUri = android.net.Uri.parse(
                            "content://com.example.hyperisland.settings/pref_channels_$pkg"
                        )
                        val channelCsv = context.contentResolver
                            .query(channelUri, null, null, null, null)
                            ?.use { if (it.moveToFirst()) it.getString(0) else "" }
                            ?: ""
                        val channels = if (channelCsv.isBlank()) emptySet()
                        else channelCsv.split(",").filter { it.isNotBlank() }.toSet()
                        pkg to channels
                    }
                cachedWhitelist = map
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
    }

    private fun handleSbn(sbn: StatusBarNotification, lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val pkg = sbn.packageName ?: return

            // 先取 context，用于加载白名单
            val context = getContext(lpparam) ?: return

            // 白名单检查（动态从 SettingsProvider 读取）
            val allowedChannels = loadWhitelist(context)[pkg] ?: return
            val notif = sbn.notification ?: return
            val channelId = notif.channelId ?: ""
            if (allowedChannels.isNotEmpty() && channelId !in allowedChannels) return

            val extras = notif.extras ?: return

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

            val appIconRaw = InProcessController.getAppIcon(context, pkg)
            val largeIcon  = extractLargeIcon(extras)

            val iconMode = loadChannelStringSetting(
                context, "icon:$pkg/$channelId",
                "pref_channel_icon_${pkg}_$channelId", "auto"
            )
            val focusNotif = loadChannelStringSetting(
                context, "focus:$pkg/$channelId",
                "pref_channel_focus_${pkg}_$channelId", "default"
            )
            val firstFloat = loadChannelStringSetting(
                context, "first_float:$pkg/$channelId",
                "pref_channel_first_float_${pkg}_$channelId", "default"
            )
            val enableFloatMode = loadChannelStringSetting(
                context, "efloat:$pkg/$channelId",
                "pref_channel_enable_float_${pkg}_$channelId", "default"
            )

            XposedBridge.log(
                "HyperIsland[Generic]: $pkg/$channelId | $title | $progressPercent% | template=$template | buttons=${actions.size} | largeIcon=${largeIcon != null}"
            )

            TemplateRegistry.dispatch(
                templateId = template,
                context    = context,
                extras     = extras,
                data       = NotifData(
                    pkg             = pkg,
                    channelId       = channelId,
                    title           = title,
                    subtitle        = subtitle,
                    progress        = progressPercent,
                    actions         = actions,
                    notifIcon       = notif.smallIcon,
                    largeIcon       = largeIcon,
                    appIconRaw      = appIconRaw,
                    iconMode        = iconMode,
                    focusNotif      = focusNotif,
                    firstFloat      = firstFloat,
                    enableFloatMode = enableFloatMode,
                ),
            )

            extras.putBoolean("hyperisland_generic_processed", true)

        } catch (e: Throwable) {
            XposedBridge.log("HyperIsland[Generic]: handleSbn error: ${e.message}")
        }
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
