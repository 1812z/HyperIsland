package io.github.hyperisland.xposed.templates

import android.content.Context
import android.graphics.drawable.Icon
import android.os.Bundle
import android.util.Log
import io.github.hyperisland.xposed.template.core.contracts.IslandTemplate
import io.github.hyperisland.xposed.template.core.models.NotifData
import io.github.hyperisland.xposed.template.core.models.IslandViewModel
import io.github.hyperisland.xposed.utils.toRounded
import io.github.hyperisland.xposed.logError
import io.github.hyperisland.xposed.renderer.ImageTextWithButtonsRenderer
import io.github.hyperisland.xposed.renderer.resolveRenderer
import io.github.hyperisland.xposed.renderer.ImageTextWithRightTextButtonRenderer

/**
 * 下载 Lite 灵动岛通知构建器。
 * 摘要态仅显示图标与环形进度，无任何文字。焦点通知与按钮与大版本保持一致。
 *
 * 消息处理（[process]）与渲染（[ImageTextWithButtonsRenderer]/[ImageTextWithButtonsWrapRenderer]）分离。
 */
object DownloadLiteIslandNotification : IslandTemplate {

    private const val TAG = "HyperIsland[DownloadIslandLite]"
    const val TEMPLATE_ID = "download_lite"

    override val id = TEMPLATE_ID

    override fun inject(context: Context, extras: Bundle, data: NotifData) {
        try {
            val vm = process(context, data)
            resolveRenderer(data.renderer).render(context, extras, vm)
            val stateTag = when {
                vm.circularProgress != null -> "${vm.circularProgress}%"
                !vm.updatable               -> "done"
                else                        -> "paused"
            }
            //module.log("$TAG: "HyperIsland[DownloadLite]: injected — ${data.title} ($stateTag) buttons=${data.actions.size}")
        } catch (e: Exception) {
            logError("$TAG: injection error: ${e.message}")
        }
    }

    // ── 消息处理 ──────────────────────────────────────────────────────────────

    fun process(context: Context, data: NotifData): IslandViewModel {
        val isComplete = data.progress >= 100
        val isPaused   = !isComplete && "${data.title} ${data.subtitle} ".let {
            it.contains("暂停") || it.contains("已暂停") || it.contains("暂停中") ||
            it.contains("paused", ignoreCase = true)
        }
        val hasValidProgress   = data.progress in 0..100
        val safeProgress       = data.progress.coerceIn(0, 100)
        val shouldShowProgress = !isComplete && !isPaused && hasValidProgress

        val tintColor = when {
            isComplete -> 0xFF4CAF50.toInt()
            isPaused   -> 0xFFFF9800.toInt()
            else       -> 0xFF2196F3.toInt()
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

        val focusIcon = when (data.focusIconMode) {
            "notif_small" -> data.notifIcon ?: data.appIconRaw ?: fallback
            "notif_large" -> data.largeIcon ?: data.appIconRaw ?: data.notifIcon ?: fallback
            "app_icon"    -> data.appIconRaw ?: fallback
            else          -> data.largeIcon ?: data.appIconRaw ?: data.notifIcon ?: fallback
        }.toRounded(context)

return IslandViewModel(
            templateId        = TEMPLATE_ID,
            leftTitle         = "",
            rightTitle        = "",
            focusTitle        = data.title,
            focusContent      = data.subtitle.ifEmpty { data.title },
            islandIcon        = islandIcon,
            focusIcon         = focusIcon,
            circularProgress  = if (shouldShowProgress) safeProgress else null,
            showRightSide     = shouldShowProgress,
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
        )
    }
}
