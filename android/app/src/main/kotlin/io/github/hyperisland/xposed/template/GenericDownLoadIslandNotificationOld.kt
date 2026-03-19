package io.github.hyperisland.xposed.templates

import android.app.Notification
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import io.github.hyperisland.R
import io.github.hyperisland.xposed.IslandTemplate
import io.github.hyperisland.xposed.NotifData
import io.github.hyperisland.xposed.moduleContext
import io.github.hyperisland.xposed.toRounded
import de.robv.android.xposed.XposedBridge
import io.github.d4viddf.hyperisland_kit.HyperAction
import io.github.d4viddf.hyperisland_kit.HyperIslandNotification
import io.github.d4viddf.hyperisland_kit.HyperPicture

/**
 * 通用进度条灵动岛通知构建器（OS2 版本）。
 * 适用于任意含进度条的通知，使用 OS2 协议的 setBaseInfo 组件展示，
 * 需要 ticker（状态栏显示文案）与 tickerPic（状态栏图标 picKey）。
 */
object GenericDownLoadIslandNotificationOld : IslandTemplate {

    const val TEMPLATE_ID   = "generic_progress_old"
    const val TEMPLATE_NAME = "下载(OS2)"

    override val id          = TEMPLATE_ID
    override val displayName = TEMPLATE_NAME

    override fun getDisplayName(context: Context): String = try {
        context.moduleContext().getString(R.string.template_download_old_name)
    } catch (_: Exception) { TEMPLATE_NAME }

    override fun inject(context: Context, extras: Bundle, data: NotifData) = inject(
        context         = context,
        extras          = extras,
        title           = data.title,
        subtitle        = data.subtitle,
        progress        = data.progress,
        actions         = data.actions,
        notifIcon       = data.notifIcon,
        largeIcon       = data.largeIcon,
        appIconRaw      = data.appIconRaw,
        iconMode        = data.iconMode,
        focusIconMode   = data.focusIconMode,
        focusNotif      = data.focusNotif,
        firstFloat      = data.firstFloat,
        enableFloatMode = data.enableFloatMode,
        timeoutSecs     = data.islandTimeout,
    )

    private val NOISE_REGEX = Regex(
        """(?i)\d+(\.\d+)?\s*(b|kb|mb|gb|tb|kib|mib|gib)\s*/\s*\d+(\.\d+)?\s*(b|kb|mb|gb|tb|kib|mib|gib)""" +
        """|(?i)\d+(\.\d+)?\s*(mb/s|kb/s|gb/s|mib/s|kib/s|mbps|kbps|gbps|m/s|兆/秒|兆字节/秒)""" +
        """|(?i)\d+\s*%""" +
        """|下载中|正在下载|准备下载|开始下载|等待下载|排队中|等待中|连接中|获取中|暂停中|已暂停|下载完成|下载失败|下载错误""" +
        """|剩余\s*\d+|还有\s*\d+|剩余时间""" +
        """|(?i)\bdownloading\b|\bdownload\b|\bqueued\b|\bpending\b|\bwaiting\b|\bpaused\b|\bconnecting\b|\bpreparing\b|\bremaining\b"""
    )

    private fun isStatusNoise(text: String): Boolean = NOISE_REGEX.containsMatchIn(text)

    private fun stripDownloadPrefix(text: String): String {
        var s = text.trim()
        for (prefix in listOf("正在下载", "下载中", "下载", "Downloading", "Download")) {
            if (s.startsWith(prefix, ignoreCase = true)) {
                s = s.removePrefix(prefix).trimStart(':', '：', ' ', '-')
                break
            }
        }
        return s.trim()
    }

    private fun pickContent(title: String, subtitle: String): String {
        val subClean   = subtitle.isNotEmpty() && !isStatusNoise(subtitle)
        val titleClean = title.isNotEmpty()    && !isStatusNoise(title)
        return when {
            subClean              -> subtitle
            titleClean            -> title
            subtitle.isNotEmpty() -> subtitle
            else                  -> stripDownloadPrefix(title)
        }
    }

    private fun inject(
        context: Context,
        extras: Bundle,
        title: String,
        subtitle: String,
        progress: Int,
        actions: List<Notification.Action>,
        notifIcon: Icon?,
        largeIcon: Icon?,
        appIconRaw: Icon?,
        iconMode: String,
        focusIconMode: String,
        focusNotif: String,
        firstFloat: String,
        enableFloatMode: String,
        timeoutSecs: Int,
    ) {
        try {
            val combined   = "$title $subtitle "
            val isComplete = progress >= 100 ||
                combined.contains("完成") || combined.contains("成功") ||
                combined.contains("complete", ignoreCase = true) ||
                combined.contains("finished", ignoreCase = true) ||
                combined.contains("done",     ignoreCase = true)
            val isPaused   = !isComplete && (
                combined.contains("暂停") || combined.contains("已暂停") || combined.contains("暂停中") ||
                combined.contains("paused", ignoreCase = true)
            )
            val isWaiting  = !isComplete && !isPaused && (
                combined.contains("等待") || combined.contains("准备中") ||
                combined.contains("队列") || combined.contains("排队") ||
                combined.contains("pending",  ignoreCase = true) ||
                combined.contains("queued",   ignoreCase = true) ||
                combined.contains("waiting",  ignoreCase = true)
            )

            val mc = context.moduleContext()
            val stateLabel = when {
                isComplete -> mc.getString(R.string.island_state_complete)
                isPaused   -> mc.getString(R.string.island_state_paused)
                isWaiting  -> mc.getString(R.string.island_state_waiting)
                else       -> mc.getString(R.string.island_state_downloading)
            }
            val rightContent   = pickContent(title, subtitle)
            val displayContent = subtitle.ifEmpty { title }

            val iconRes   = if (isComplete) android.R.drawable.stat_sys_download_done
                            else            android.R.drawable.stat_sys_download
            val tintColor = when {
                isComplete            -> 0xFF4CAF50.toInt()
                isPaused || isWaiting -> 0xFFFF9800.toInt()
                else                  -> 0xFF2196F3.toInt()
            }
            val fallbackIcon = Icon.createWithResource(context, iconRes).apply { setTint(tintColor) }
            val displayIcon = when (iconMode) {
                "notif_small" -> notifIcon ?: fallbackIcon
                "notif_large" -> largeIcon ?: notifIcon ?: fallbackIcon
                "app_icon"    -> appIconRaw ?: fallbackIcon
                else          -> notifIcon ?: largeIcon ?: fallbackIcon
            }.toRounded(context)
            val focusDisplayIcon = when (focusIconMode) {
                "notif_small" -> notifIcon ?: appIconRaw ?: fallbackIcon
                "notif_large" -> largeIcon ?: appIconRaw ?: notifIcon ?: fallbackIcon
                "app_icon"    -> appIconRaw ?: fallbackIcon
                else          -> largeIcon ?: appIconRaw ?: notifIcon ?: fallbackIcon
            }.toRounded(context)

            val resolvedFirstFloat  = firstFloat      == "on"
            val resolvedEnableFloat = enableFloatMode == "on"
            val showNotification    = focusNotif != "off"

            // OS2 焦点通知状态栏文案
            val ticker: String = "$stateLabel $rightContent".trim()
            // OS2 焦点通知状态栏图标 picKey，不传默认为应用图标
            val tickerPic: String = "key_generic_progress_focus_icon_old"

            val builder = HyperIslandNotification.Builder(context, "generic_progress_old", title)

            builder.addPicture(HyperPicture("key_generic_progress_icon_old", displayIcon))
            builder.addPicture(HyperPicture("key_generic_progress_focus_icon_old", focusDisplayIcon))

            builder.setIslandFirstFloat(resolvedFirstFloat)
            builder.setEnableFloat(resolvedEnableFloat)
            builder.setShowNotification(showNotification)
            builder.setIslandConfig(timeout = timeoutSecs)

            // OS2 组件：setBaseInfo 替代 OS3 的 setBigIslandInfo / setSmallIsland / setIconTextInfo
            val progressText = if (!isComplete && progress > 0) "$progress%" else ""
            builder.setBaseInfo(
                type       = 1,
                title      = stateLabel,
                content    = displayContent,
                subTitle   = progressText,
                pictureKey = "key_generic_progress_icon_old",
            )

            // 来自原通知的按钮（最多 2 个）
            val effectiveActions = actions.take(2)
            if (effectiveActions.isNotEmpty() && showNotification) {
                val hyperActions = effectiveActions.mapIndexed { index, action ->
                    HyperAction(
                        key              = "action_generic_old_$index",
                        title            = action.title ?: "",
                        pendingIntent    = action.actionIntent,
                        actionIntentType = 2,
                    )
                }
                hyperActions.forEach { builder.addHiddenAction(it) }
                builder.setTextButtons(*hyperActions.toTypedArray())
            }

            val resourceBundle = builder.buildResourceBundle()
            extras.putAll(resourceBundle)
            flattenActionsToExtras(resourceBundle, extras)

            // OS2 状态栏 ticker 与 tickerPic
            extras.putString("ticker", ticker)
            if (tickerPic.isNotEmpty()) {
                extras.putString("tickerPic", tickerPic)
            }

            val jsonParam = injectUpdatable(
                fixTextButtonJson(builder.buildJsonParam()), !isComplete && !isPaused
            )
            extras.putString("miui.focus.param", jsonParam)

            val stateTag = when {
                isComplete -> "done"
                isPaused   -> "paused"
                isWaiting  -> "waiting"
                else       -> "${progress}%"
            }
            XposedBridge.log("HyperIsland[GenericOld]: Island injected — $title ($stateTag) buttons=${actions.size} ticker=$ticker")

        } catch (e: Exception) {
            XposedBridge.log("HyperIsland[GenericOld]: Island injection error: ${e.message}")
        }
    }

    private fun fixTextButtonJson(jsonParam: String): String {
        return try {
            val json = org.json.JSONObject(jsonParam)
            val pv2  = json.optJSONObject("param_v2") ?: return jsonParam
            val btns = pv2.optJSONArray("textButton") ?: return jsonParam
            for (i in 0 until btns.length()) {
                val btn = btns.getJSONObject(i)
                val key = btn.optString("actionIntent").takeIf { it.isNotEmpty() } ?: continue
                btn.put("action", key)
                btn.remove("actionIntent")
                btn.remove("actionIntentType")
            }
            json.toString()
        } catch (_: Exception) { jsonParam }
    }

    private fun injectUpdatable(jsonParam: String, updatable: Boolean): String {
        return try {
            val json = org.json.JSONObject(jsonParam)
            val pv2  = json.optJSONObject("param_v2") ?: org.json.JSONObject()
            pv2.put("updatable", updatable)
            json.put("param_v2", pv2)
            json.toString()
        } catch (_: Exception) { jsonParam }
    }

    private fun flattenActionsToExtras(resourceBundle: Bundle, extras: Bundle) {
        val nested = resourceBundle.getBundle("miui.focus.actions") ?: return
        for (key in nested.keySet()) {
            val action: Notification.Action? = if (Build.VERSION.SDK_INT >= 33)
                nested.getParcelable(key, Notification.Action::class.java)
            else
                @Suppress("DEPRECATION") nested.getParcelable(key)
            if (action != null) extras.putParcelable(key, action)
        }
    }
}
