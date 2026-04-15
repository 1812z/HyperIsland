package io.github.hyperisland.xposed.templates

import android.content.Context
import android.graphics.drawable.Icon
import android.os.Bundle
import io.github.hyperisland.R
import io.github.hyperisland.xposed.template.core.contracts.IslandTemplate
import io.github.hyperisland.xposed.template.core.contracts.TemplatePlaceholder
import io.github.hyperisland.xposed.template.core.customization.FocusCustomizationEngine
import io.github.hyperisland.xposed.template.core.models.NotifData
import io.github.hyperisland.xposed.template.core.models.IslandViewModel
import io.github.hyperisland.xposed.utils.toRounded
import io.github.hyperisland.xposed.utils.moduleContext
import io.github.hyperisland.xposed.logError
import io.github.hyperisland.xposed.renderer.RendererContext
import io.github.hyperisland.xposed.renderer.resolveRenderer

/**
 * 通用进度条灵动岛通知构建器。
 * 适用于任意含进度条的通知，按钮直接取自原通知（最多 2 个）。
 *
 * 消息处理（[process]）与渲染（[ImageTextWithButtonsRenderer]/[ImageTextWithButtonsWrapRenderer]）分离。
 */
object GenericDownloadIslandNotification : IslandTemplate {

    private const val TAG = "HyperIsland[DownloadIsland]"
    const val TEMPLATE_ID = "generic_progress"

    override val id = TEMPLATE_ID
    override val focusExpressionPlaceholders: List<TemplatePlaceholder> = listOf(
        TemplatePlaceholder("progress_text"),
    )
    override val islandExpressionPlaceholders: List<TemplatePlaceholder> = listOf(
        TemplatePlaceholder("progress_text"),
    )
    override val defaultFocusTitleExpr: String = "${'$'}{title}"
    override val defaultFocusContentExpr: String = "${'$'}{subtitle_or_title}"
    override val defaultIslandLeftExpr: String = "${'$'}{state_label}"
    override val defaultIslandRightExpr: String = "${'$'}{subtitle_or_title}"
    override fun inject(context: Context, extras: Bundle, data: NotifData) {
        try {
            val ctx = process(context, data)
            resolveRenderer(data.renderer).render(context, extras, ctx)
        } catch (e: Exception) {
            logError("$TAG: injection error: ${e.message}")
        }
    }

    // ── 消息处理 ──────────────────────────────────────────────────────────────

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

    fun process(context: Context, data: NotifData): RendererContext {
        val combined   = "${data.title} ${data.subtitle} "
        val isComplete = data.progress >= 100 ||
            combined.contains("完成") || combined.contains("成功") ||
            combined.contains("complete", ignoreCase = true) ||
            combined.contains("finished", ignoreCase = true) ||
            combined.contains("done",     ignoreCase = true)
        val isPaused = !isComplete && (
            combined.contains("暂停") || combined.contains("已暂停") || combined.contains("暂停中") ||
            combined.contains("paused", ignoreCase = true)
        )
        val isWaiting = !isComplete && !isPaused && (
            combined.contains("等待") || combined.contains("准备中") ||
            combined.contains("队列") || combined.contains("排队") ||
            combined.contains("pending",  ignoreCase = true) ||
            combined.contains("queued",   ignoreCase = true) ||
            combined.contains("waiting",  ignoreCase = true)
        )

        val hasValidProgress   = data.progress in 0..100
        val safeProgress       = data.progress.coerceIn(0, 100)
        val shouldShowProgress = !isComplete && !isWaiting && !isPaused && hasValidProgress

        val mc = context.moduleContext()
        val stateLabel = when {
            isComplete -> mc.getString(R.string.island_state_complete)
            isPaused   -> mc.getString(R.string.island_state_paused)
            isWaiting  -> mc.getString(R.string.island_state_waiting)
            else       -> mc.getString(R.string.island_state_downloading)
        }

        val tintColor = when {
            isComplete            -> 0xFF4CAF50.toInt()
            isPaused || isWaiting -> 0xFFFF9800.toInt()
            else                  -> 0xFF2196F3.toInt()
        }
        val iconRes  = if (isComplete) android.R.drawable.stat_sys_download_done
                       else            android.R.drawable.stat_sys_download
        val fallback = Icon.createWithResource(context, iconRes).apply { setTint(tintColor) }

        val islandIcon = when (data.iconMode) {
            "notif_small" -> data.notifIcon ?: fallback
            "notif_large" -> data.largeIcon ?: data.notifIcon ?: fallback
            "app_icon"    -> data.appIconRaw ?: fallback
            else          -> data.notifIcon ?: data.largeIcon ?: fallback
        }.toRounded(context)

        val focusIcon = (data.largeIcon ?: data.appIconRaw ?: data.notifIcon ?: fallback).toRounded(context)

        val baseVm = IslandViewModel(
            templateId        = TEMPLATE_ID,
            leftTitle         = stateLabel,
            rightTitle        = pickContent(data.title, data.subtitle),
            focusTitle        = data.title,
            focusContent      = data.subtitle.ifEmpty { data.title },
            islandIcon        = islandIcon,
            focusIcon         = focusIcon,
            circularProgress  = if (shouldShowProgress) safeProgress else null,
            actions           = data.actions,
            updatable         = !isComplete && !isPaused,
            showNotification  = data.focusNotif != "off",
            setFocusProxy     = false,
            preserveStatusBarSmallIcon = false,
            firstFloat        = data.firstFloat == "on",
            enableFloat       = data.enableFloatMode == "on",
            timeoutSecs       = data.islandTimeout,
            isOngoing         = data.isOngoing,
            showIslandIcon    = data.showIslandIcon == "on",
            highlightColor    = data.highlightColor,
            showLeftHighlightColor = data.showLeftHighlightColor,
            showRightHighlightColor = data.showRightHighlightColor,
            showLeftNarrowFont = data.showLeftNarrowFont,
            showRightNarrowFont = data.showRightNarrowFont,
            outerGlow = data.outerGlow,
            islandOuterGlow = data.islandOuterGlow,
            islandOuterGlowColor = data.islandOuterGlowColor,
            outEffectColor = data.outEffectColor,
        )
        val applyResult = FocusCustomizationEngine.apply(context, data, baseVm)
        val vm = FocusCustomizationEngine.applyIsland(data, applyResult.vm)
        return RendererContext(vm = vm, payload = applyResult.rendererPayload)
    }

    override fun focusExpressionVars(data: NotifData, vm: IslandViewModel): Map<String, String> {
        val progressText = data.progress.coerceIn(0, 100).toString()
        return mapOf("progress_text" to progressText)
    }

}
