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
import io.github.d4viddf.hyperisland_kit.models.CircularProgressInfo
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoLeft
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoRight
import io.github.d4viddf.hyperisland_kit.models.PicInfo
import io.github.d4viddf.hyperisland_kit.models.ProgressTextInfo
import io.github.d4viddf.hyperisland_kit.models.TextInfo

/**
 * 通用进度条灵动岛通知构建器。
 * 适用于任意含进度条的通知，按钮直接取自原通知（最多 2 个），不硬编码暂停/取消。
 */
object GenericProgressIslandNotification : IslandTemplate {

    // const val 在调用处被编译器内联，供无 Xposed 依赖的 TemplateManifest 安全引用
    const val TEMPLATE_ID   = "generic_progress"
    const val TEMPLATE_NAME = "下载"   // 硬编码回退值（中文）

    override val id          = TEMPLATE_ID
    override val displayName = TEMPLATE_NAME

    override fun getDisplayName(context: Context): String = try {
        context.moduleContext().getString(R.string.template_download_name)
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

    /**
     * 判断文本是否属于"状态噪声"（速度、百分比、下载状态词等），
     * 这类文本不适合作为摘要右侧的内容标题。
     */
    private val NOISE_REGEX = Regex(
        // 文件大小进度：33MB/320MB  |  1.2 GB / 4 GB  |  500KB/1024KB
        """(?i)\d+(\.\d+)?\s*(b|kb|mb|gb|tb|kib|mib|gib)\s*/\s*\d+(\.\d+)?\s*(b|kb|mb|gb|tb|kib|mib|gib)""" +
        // 网速：12.3 MB/s  |  5 KB/s  |  100 Mbps  |  兆/秒 等
        """|(?i)\d+(\.\d+)?\s*(mb/s|kb/s|gb/s|mib/s|kib/s|mbps|kbps|gbps|m/s|兆/秒|兆字节/秒)""" +
        // 百分比：31%  |  100 %
        """|(?i)\d+\s*%""" +
        // 中文下载状态词
        """|下载中|正在下载|准备下载|开始下载|等待下载|排队中|等待中|连接中|获取中|暂停中|已暂停|下载完成|下载失败|下载错误""" +
        // 时间剩余
        """|剩余\s*\d+|还有\s*\d+|剩余时间""" +
        // 英文状态词
        """|(?i)\bdownloading\b|\bdownload\b|\bqueued\b|\bpending\b|\bwaiting\b|\bpaused\b|\bconnecting\b|\bpreparing\b|\bremaining\b"""
    )

    private fun isStatusNoise(text: String): Boolean = NOISE_REGEX.containsMatchIn(text)

    /**
     * 去除标题里常见的下载前缀（如"正在下载 "、"下载中："），
     * 以便在两者都有噪声时尽量提取出有意义的文件名部分。
     */
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

    /**
     * 从 title / subtitle 中挑选适合显示在摘要右侧的内容文本：
     * - 优先选无噪声的一方
     * - 两者都无噪声时优先副标题
     * - 两者都有噪声时对 title 去除下载前缀后返回
     */
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
                isComplete            -> 0xFF4CAF50.toInt()  // 绿
                isPaused || isWaiting -> 0xFFFF9800.toInt()  // 橙
                else                  -> 0xFF2196F3.toInt()  // 蓝
            }
            val fallbackIcon = Icon.createWithResource(context, iconRes).apply { setTint(tintColor) }
            // 超级岛区域图标（bigIslandArea / smallIslandArea）
            val displayIcon = when (iconMode) {
                "notif_small" -> notifIcon ?: fallbackIcon
                "notif_large" -> largeIcon ?: notifIcon ?: fallbackIcon
                "app_icon"    -> appIconRaw ?: fallbackIcon
                else          -> notifIcon ?: largeIcon ?: fallbackIcon  // auto
            }.toRounded(context)
            // 焦点图标（iconTextInfo）
            val focusDisplayIcon = when (focusIconMode) {
                "notif_small" -> notifIcon ?: appIconRaw ?: fallbackIcon
                "notif_large" -> largeIcon ?: appIconRaw ?: notifIcon ?: fallbackIcon
                "app_icon"    -> appIconRaw ?: fallbackIcon
                else          -> largeIcon ?: appIconRaw ?: notifIcon ?: fallbackIcon  // auto
            }.toRounded(context)

            val resolvedFirstFloat  = firstFloat      == "on"
            val resolvedEnableFloat = enableFloatMode == "on"
            val showNotification    = focusNotif != "off"

            val builder = HyperIslandNotification.Builder(context, "generic_progress", title)

            builder.addPicture(HyperPicture("key_generic_progress_icon", displayIcon))
            builder.addPicture(HyperPicture("key_generic_focus_icon", focusDisplayIcon))

            builder.setIconTextInfo(
                picKey  = "key_generic_focus_icon",
                title   = title,
                content = displayContent,
            )

            builder.setIslandFirstFloat(resolvedFirstFloat)
            builder.setEnableFloat(resolvedEnableFloat)
            builder.setShowNotification(showNotification)
            builder.setIslandConfig(timeout = timeoutSecs)

            // 小岛：下载中时带环形进度，其他状态仅图标
            if (!isComplete && progress > 0) {
                builder.setSmallIslandCircularProgress("key_generic_progress_icon", progress)
            } else {
                builder.setSmallIsland("key_generic_progress_icon")
            }

            // 大岛：下载中时左侧状态+右侧环形进度，其他状态左侧状态+右侧文本
            if (!isComplete && !isWaiting && !isPaused) {
                builder.setBigIslandInfo(
                    left = ImageTextInfoLeft(
                        type     = 1,
                        picInfo  = PicInfo(type = 1, pic = "key_generic_progress_icon"),
                        textInfo = TextInfo(title = stateLabel),
                    ),
                    progressText = ProgressTextInfo(
                        progressInfo = CircularProgressInfo(progress = progress),
                        textInfo     = TextInfo(title = rightContent, narrowFont = true),
                    ),
                )
            } else {
                builder.setBigIslandInfo(
                    left = ImageTextInfoLeft(
                        type     = 1,
                        picInfo  = PicInfo(type = 1, pic = "key_generic_progress_icon"),
                        textInfo = TextInfo(title = stateLabel),
                    ),
                    right = ImageTextInfoRight(
                        type     = 2,
                        textInfo = TextInfo(title = rightContent, narrowFont = true),
                    ),
                )
            }

            // 来自原通知的按钮（最多 2 个）
            val effectiveActions = actions.take(2)
            if (effectiveActions.isNotEmpty() && showNotification) {
                val hyperActions = effectiveActions.mapIndexed { index, action ->
                    // 文本模式（无图标），避免 TextButtonInfo.actionIcon 指向不存在的 pic 键
                    HyperAction(
                        key              = "action_generic_$index",
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
            // HyperOS 从 extras 顶层查找 action，将嵌套 bundle 展开
            flattenActionsToExtras(resourceBundle, extras)
            // 修正字段名 + 保持 updatable 与原始逻辑一致
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
            XposedBridge.log("HyperIsland[Generic]: Island injected — $title ($stateTag) buttons=${actions.size}")

        } catch (e: Exception) {
            XposedBridge.log("HyperIsland[Generic]: Island injection error: ${e.message}")
        }
    }

    /**
     * 将 textButton 数组里新库输出的 "actionIntent"+"actionIntentType"
     * 替换为 HyperOS V3 协议所需的 "action" 字段，否则按钮点击无响应。
     */
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

    /** 将 buildResourceBundle() 里嵌套的 "miui.focus.actions" 展开到 extras 顶层 */
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
