package io.github.hyperisland.xposed.templates

import android.content.Context
import android.graphics.drawable.Icon
import android.os.Bundle
import io.github.hyperisland.xposed.IslandDispatcher
import io.github.hyperisland.xposed.log
import io.github.hyperisland.xposed.logError
import io.github.hyperisland.xposed.IslandRequest
import io.github.hyperisland.xposed.IslandTemplate
import io.github.hyperisland.xposed.IslandViewModel
import io.github.hyperisland.xposed.NotifData
import io.github.hyperisland.xposed.hook.FocusNotifStatusBarIconHook
import io.github.hyperisland.xposed.renderer.ImageTextWithButtonsRenderer
import io.github.hyperisland.xposed.renderer.resolveRenderer
import io.github.hyperisland.xposed.toRounded

/**
 * 通知超级岛通知构建器。
 * 适用于任意通知，以 bigIslandArea 摘要态展示：
 *  - 左侧：通知图标 + 通知标题
 *  - 右侧：通知正文（无则使用标题）
 *
 * 消息处理（[process]）与渲染（[ImageTextWithButtonsRenderer]/[ImageTextWithButtonsWrapRenderer]）分离。
 */
object NotificationIslandNotification : IslandTemplate {

    private const val TAG = "HyperIsland[NotifIsland]"
    const val TEMPLATE_ID = "notification_island"

    override val id = TEMPLATE_ID

    override fun inject(context: Context, extras: Bundle, data: NotifData) {
        if (data.focusNotif == "off") {
            injectViaDispatcher(context, data)
            return
        }
        try {
            val vm = process(context, data)
            resolveRenderer(data.renderer).render(context, extras, vm)
            //ConfigManager.module()?.log("$TAG: injected — ${data.title} | left=${vm.leftTitle} | right=${vm.rightTitle} | buttons=${data.actions.size} | isOngoing=${data.isOngoing}")
        } catch (e: Exception) {
            logError("$TAG: injection error: ${e.message}")
        }
    }

    // ── Dispatcher 路径（focusNotif == "off"）────────────────────────────────

    private fun injectViaDispatcher(context: Context, data: NotifData) {
        try {
            val fallbackIcon = Icon.createWithResource(context, android.R.drawable.ic_dialog_info)
            val displayIcon = when (data.iconMode) {
                "notif_small" -> data.notifIcon ?: fallbackIcon
                "notif_large" -> data.largeIcon ?: data.notifIcon ?: fallbackIcon
                "app_icon"    -> data.appIconRaw ?: fallbackIcon
                else          -> data.largeIcon ?: data.notifIcon ?: fallbackIcon
            }.toRounded(context)

            IslandDispatcher.post(
                context,
                IslandRequest(
                    title            = data.title,
                    content          = data.subtitle.ifEmpty { data.title },
                    icon             = displayIcon,
                    timeoutSecs      = data.islandTimeout,
                    firstFloat       = data.firstFloat == "on",
                    enableFloat      = data.enableFloatMode == "on",
                    showNotification = false,
                    preserveStatusBarSmallIcon = data.preserveStatusBarSmallIcon != "off",
                    contentIntent    = data.contentIntent,
                    isOngoing        = data.isOngoing,
                    actions          = data.actions.take(2),
                ),
            )
            //ConfigManager.module()?.log("$TAG: dispatcher island — ${data.title} | iconMode=${data.iconMode} | timeout=${data.islandTimeout}")
        } catch (e: Exception) {
            logError("$TAG: dispatcher island error: ${e.message}")
        }
    }

    // ── 消息处理 ──────────────────────────────────────────────────────────────

    fun process(context: Context, data: NotifData): IslandViewModel {
        val fallbackIcon = Icon.createWithResource(context, android.R.drawable.ic_dialog_info)

        val islandIcon = when (data.iconMode) {
            "notif_small" -> data.notifIcon ?: fallbackIcon
            "notif_large" -> data.largeIcon ?: data.notifIcon ?: fallbackIcon
            "app_icon"    -> data.appIconRaw ?: fallbackIcon
            else          -> data.largeIcon ?: data.notifIcon ?: fallbackIcon
        }.toRounded(context)

        val focusIcon = when (data.focusIconMode) {
            "notif_small" -> data.notifIcon ?: data.appIconRaw ?: fallbackIcon
            "notif_large" -> data.largeIcon ?: data.appIconRaw ?: data.notifIcon ?: fallbackIcon
            "app_icon"    -> data.appIconRaw ?: fallbackIcon
            else          -> data.largeIcon ?: data.appIconRaw ?: data.notifIcon ?: fallbackIcon
        }.toRounded(context)

        val showNotification   = data.focusNotif != "off"
        val shouldPreserveIcon = showNotification && data.preserveStatusBarSmallIcon != "off"

        return IslandViewModel(
            templateId        = TEMPLATE_ID,
            leftTitle         = data.title,
            rightTitle        = data.subtitle.ifEmpty { data.title },
            focusTitle        = data.title,
            focusContent      = data.subtitle.ifEmpty { data.title },
            islandIcon        = islandIcon,
            focusIcon         = focusIcon,
            circularProgress  = null,
            showRightSide     = true,
            actions           = data.actions,
            updatable         = data.isOngoing,
            showNotification  = showNotification,
            setFocusProxy     = showNotification,
            preserveStatusBarSmallIcon = shouldPreserveIcon,
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
        )
    }
}
