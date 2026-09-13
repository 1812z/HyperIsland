package io.github.hyperisland.xposed.templates

import android.content.Context
import android.os.Bundle
import android.graphics.drawable.Icon
import io.github.hyperisland.xposed.template.core.contracts.IslandTemplate
import io.github.hyperisland.xposed.template.core.models.IslandViewModel
import io.github.hyperisland.xposed.template.core.models.NotifData
import io.github.hyperisland.xposed.template.core.customization.FocusCustomizationEngine
import io.github.hyperisland.xposed.renderer.RendererContext
import io.github.hyperisland.xposed.renderer.resolveRenderer
import io.github.hyperisland.xposed.utils.toRounded
import io.github.hyperisland.xposed.log
import io.github.hyperisland.xposed.islanddispatch.IslandDispatcher
import io.github.hyperisland.xposed.islanddispatch.definition.IslandRequest
import io.github.hyperisland.xposed.islanddispatch.definition.IslandDispatchContract
import java.util.concurrent.ConcurrentHashMap

/** 展开内容沿用原通知，收起的大岛只显示图标和活动通知数。 */
object NotificationCountIslandNotification : IslandTemplate {
    const val TEMPLATE_ID = "notification_count_island"
    override val id = TEMPLATE_ID
    override val defaultFocusTitleExpr = "${'$'}{title}"
    override val defaultFocusContentExpr = "${'$'}{subtitle_or_title}"
    override val defaultIslandLeftExpr = ""
    override val defaultIslandRightExpr = "${'$'}{notification_count}"
    private val lastPostedSignature = ConcurrentHashMap<String, String>()

    fun reset(pkg: String) { lastPostedSignature.remove(pkg) }
    fun resetAll() { lastPostedSignature.clear() }

    override fun islandExpressionVars(data: NotifData, vm: IslandViewModel) =
        mapOf("notification_count" to data.notificationCount.coerceAtLeast(0).toString())

    override fun inject(context: Context, extras: Bundle, data: NotifData) {
        extras.putBoolean(IslandDispatchContract.EXTRA_SUPPRESS_SOURCE_HEADS_UP, true)
        val count = data.notificationCount.coerceAtLeast(0)
        val signature = "$count|${data.notificationKey.orEmpty()}|${data.title}|${data.subtitle}"
        if (lastPostedSignature.put(data.pkg, signature) == signature) {
            log("count-trace template skip pkg=${data.pkg} count=$count (unchanged)")
            return
        }
        val fallback = Icon.createWithResource(context, android.R.drawable.ic_dialog_info)
        val icon = (data.largeIcon ?: data.notifIcon ?: data.appIconRaw ?: fallback).toRounded(context)
        // 数量岛必须独立代发；不修改原始通知 extras，展开时原通知内容保持不变。
        val posted = IslandDispatcher.post(context, IslandRequest(
            title = "",
            content = count.toString(),
            icon = icon,
            timeoutSecs = data.islandTimeout,
            firstFloat = data.firstFloat == "on",
            enableFloat = data.enableFloatMode == "on",
            showNotification = false,
            preserveStatusBarSmallIcon = false,
            highlightColor = data.highlightColor,
            showRightHighlightColor = data.showRightHighlightColor,
            islandOuterGlow = data.islandOuterGlow,
            islandOuterGlowColor = data.islandOuterGlowColor,
            sourcePackage = data.pkg,
            sourceChannelId = data.channelId,
            // 保留焦点通知区域，展开时显示最近一条原始通知的内容。
            islandOnly = false,
            focusTitle = data.title,
            focusContent = data.subtitle.ifEmpty { data.title },
            updatable = true,
            islandEnabled = data.islandEnabled,
            bypassSceneBehavior = false,
        ))
        log("count-trace template post pkg=${data.pkg} count=$count posted=$posted notifId=${IslandDispatcher.NOTIF_ID} updatable=true")
    }
}
