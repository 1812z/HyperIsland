package io.github.hyperisland.xposed.templates

import android.content.Context
import android.graphics.drawable.Icon
import android.os.Bundle
import de.robv.android.xposed.XposedBridge
import io.github.hyperisland.xposed.IslandDispatcher
import io.github.hyperisland.xposed.IslandRequest
import io.github.hyperisland.xposed.IslandTemplate
import io.github.hyperisland.xposed.IslandViewModel
import io.github.hyperisland.xposed.NotifData
import io.github.hyperisland.xposed.renderer.ImageTextWithButtonsRenderer
import io.github.hyperisland.xposed.renderer.ImageTextWithButtonsWrapRenderer
import io.github.hyperisland.xposed.toRounded

/**
 * 通知超级岛|精简 通知构建器。
 * 在 [NotificationIslandNotification] 基础上额外对聊天类通知做文本清理：
 *
 * 标题规则：删除形如 [3条新消息]、[4条]、【5条】 的计数括号块，保留纯用户名。
 * 正文规则：删除开头的 [xx条] 前缀；若标题是单一发送人名称，进一步删除 "发送人:" 前缀。
 *
 * 消息处理（[process]）与渲染（[ImageTextWithButtonsRenderer]/[ImageTextWithButtonsWrapRenderer]）分离。
 */
object NotificationIslandLiteNotification : IslandTemplate {

    const val TEMPLATE_ID = "notification_island_lite"

    override val id = TEMPLATE_ID

    override fun inject(context: Context, extras: Bundle, data: NotifData) {
        val cleanedTitle    = cleanTitle(data.title)
        val cleanedSubtitle = cleanSubtitle(data.subtitle, cleanedTitle)

        if (data.focusNotif == "off") {
            injectViaDispatcher(context, data, cleanedTitle, cleanedSubtitle)
            return
        }
        try {
            val vm = process(context, data, cleanedTitle, cleanedSubtitle)
            val renderer = if (data.renderer == ImageTextWithButtonsWrapRenderer.RENDERER_ID) ImageTextWithButtonsWrapRenderer
                           else ImageTextWithButtonsRenderer
            renderer.render(context, extras, vm)
            XposedBridge.log(
                "HyperIsland[NotifIslandLite]: injected — raw=${data.title} | clean=$cleanedTitle | right=${cleanedSubtitle.ifEmpty { cleanedTitle }} | notifId=${data.notifId}"
            )
        } catch (e: Exception) {
            XposedBridge.log("HyperIsland[NotifIslandLite]: Island injection error: ${e.message}")
        }
    }

    // ── 文本清理 ──────────────────────────────────────────────────────────────

    private val TITLE_COUNT_BRACKET =
        Regex("""[\[【(（][^\]】)）]*\d+[^\]】)）]*[\]】)）]\s*|\s*[\[【(（][^\]】)）]*\d+[^\]】)）]*[\]】)）]""")

    private fun cleanTitle(title: String): String =
        title.replace(TITLE_COUNT_BRACKET, "").trim()

    private val SUBTITLE_COUNT_PREFIX =
        Regex("""^[\[【(（][^\]】)）]*\d+[^\]】)）]*[\]】)）]\s*""")

    private fun cleanSubtitle(subtitle: String, cleanedTitle: String): String {
        var s = subtitle.replace(SUBTITLE_COUNT_PREFIX, "")
        if (cleanedTitle.isNotEmpty() && cleanedTitle.length <= 30) {
            s = s.replace(Regex("""^${Regex.escape(cleanedTitle)}\s*[:：]\s*"""), "")
        }
        return s.trim()
    }

    // ── Dispatcher 路径（focusNotif == "off"）────────────────────────────────

    private fun injectViaDispatcher(
        context: Context,
        data: NotifData,
        cleanedTitle: String,
        cleanedSubtitle: String,
    ) {
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
                    title            = cleanedTitle,
                    content          = cleanedSubtitle.ifEmpty { cleanedTitle },
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
            XposedBridge.log("HyperIsland[NotifIslandLite]: Dispatcher island — $cleanedTitle | iconMode=${data.iconMode}")
        } catch (e: Exception) {
            XposedBridge.log("HyperIsland[NotifIslandLite]: Dispatcher island error: ${e.message}")
        }
    }

    // ── 消息处理 ──────────────────────────────────────────────────────────────

    fun process(
        context: Context,
        data: NotifData,
        cleanedTitle: String = cleanTitle(data.title),
        cleanedSubtitle: String = cleanSubtitle(data.subtitle, cleanedTitle),
    ): IslandViewModel {
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
            leftTitle         = cleanedTitle,
            rightTitle        = cleanedSubtitle.ifEmpty { cleanedTitle },
            focusTitle        = cleanedTitle,
            focusContent      = cleanedSubtitle.ifEmpty { cleanedTitle },
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
        )
    }
}
