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

/** 展开内容沿用原通知，收起的大岛只显示图标和活动通知数。 */
object NotificationCountIslandNotification : IslandTemplate {
    const val TEMPLATE_ID = "notification_count_island"
    override val id = TEMPLATE_ID
    override val defaultFocusTitleExpr = "${'$'}{title}"
    override val defaultFocusContentExpr = "${'$'}{subtitle_or_title}"
    override val defaultIslandLeftExpr = ""
    override val defaultIslandRightExpr = "${'$'}{notification_count}"

    override fun islandExpressionVars(data: NotifData, vm: IslandViewModel) =
        mapOf("notification_count" to data.notificationCount.coerceAtLeast(0).toString())

    override fun inject(context: Context, extras: Bundle, data: NotifData) {
        val fallback = Icon.createWithResource(context, android.R.drawable.ic_dialog_info)
        val icon = (data.largeIcon ?: data.notifIcon ?: data.appIconRaw ?: fallback).toRounded(context)
        val result = FocusCustomizationEngine.apply(context, data, IslandViewModel(
            templateId = TEMPLATE_ID,
            leftTitle = "",
            rightTitle = data.notificationCount.coerceAtLeast(0).toString(),
            focusTitle = data.title,
            focusContent = data.subtitle.ifEmpty { data.title },
            islandIcon = icon,
            focusIcon = icon,
            actions = data.actions,
            updatable = data.isOngoing,
            showNotification = data.showNotification != "off",
            setFocusProxy = data.showNotification != "off",
            preserveStatusBarSmallIcon = data.preserveStatusBarSmallIcon != "off",
            firstFloat = data.firstFloat == "on",
            enableFloat = data.enableFloatMode == "on",
            timeoutSecs = data.islandTimeout,
            isOngoing = data.isOngoing,
            showIslandIcon = true,
            highlightColor = data.highlightColor,
            showLeftHighlightColor = false,
            showRightHighlightColor = data.showRightHighlightColor,
            outerGlow = data.outerGlow,
            islandOuterGlow = data.islandOuterGlow,
            islandOuterGlowColor = data.islandOuterGlowColor,
            outEffectColor = data.outEffectColor,
            aodText = data.aodText,
            aodCustomizationJson = data.aodCustomizationJson,
            islandEnabled = data.islandEnabled,
        ))
        val vm = FocusCustomizationEngine.applyIsland(context, data.copy(islandCustomizationJson = null), result.vm)
        resolveRenderer(data.renderer).render(context, extras, RendererContext(vm, result.rendererPayload))
    }
}
