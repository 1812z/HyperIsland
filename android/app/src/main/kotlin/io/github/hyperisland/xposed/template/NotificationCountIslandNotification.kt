package io.github.hyperisland.xposed.templates

import android.content.Context
import android.os.Bundle
import android.graphics.drawable.Icon
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.Typeface
import io.github.hyperisland.xposed.template.core.contracts.IslandTemplate
import io.github.hyperisland.xposed.template.core.models.IslandViewModel
import io.github.hyperisland.xposed.template.core.models.NotifData
import io.github.hyperisland.xposed.template.core.customization.FocusCustomizationEngine
import io.github.hyperisland.xposed.renderer.RendererContext
import io.github.hyperisland.xposed.renderer.resolveRenderer
import io.github.hyperisland.xposed.utils.toRounded
import io.github.hyperisland.xposed.log
import io.github.hyperisland.xposed.logError
import io.github.hyperisland.xposed.islanddispatch.IslandDispatcher
import io.github.hyperisland.xposed.islanddispatch.definition.IslandRequest
import io.github.hyperisland.xposed.islanddispatch.definition.IslandDispatchContract
import io.github.hyperisland.xposed.hook.SystemUI.NotificationCountTracker
import java.util.concurrent.ConcurrentHashMap

/**
 * 通知计数岛模板。
 *
 * 收起的大岛只显示图标和活动通知数（右侧计数徽标），展开内容沿用最近一条原始通知。
 *
 * 岛始终由 [IslandDispatcher] 独立代发，原因有两个：
 *  - 原始通知不能带代理 owner 标记，否则会被 [NotificationCountTracker] 误过滤；
 *  - 原始通知不能自己带岛，否则每条通知都会抢占计数岛。
 *
 * 焦点通知开启时（[NotifData.focusNotif] != "off"）额外把原始通知改造成焦点通知，
 * 但不给它建岛（param_island.dismissIsland = true）—— 岛只属于计数岛代理，
 * 焦点通知只改通知样式，其他行为不变。
 */
object NotificationCountIslandNotification : IslandTemplate {

    private const val TAG = "HyperIsland[CountIsland]"
    const val TEMPLATE_ID = "notification_count_island"
    override val id = TEMPLATE_ID
    override val defaultFocusTitleExpr = "${'$'}{title}"
    override val defaultFocusContentExpr = "${'$'}{subtitle_or_title}"
    /** 左侧无文本、右侧是计数徽标图标，计数岛不消费超级岛文本表达式。 */
    override val defaultIslandLeftExpr = ""
    override val defaultIslandRightExpr = ""
    private val lastPostedSignature = ConcurrentHashMap<String, String>()

    fun reset(scope: NotificationCountTracker.Scope) { lastPostedSignature.remove(scopeKey(scope)) }
    fun resetAll() { lastPostedSignature.clear() }

    private fun scopeKey(scope: NotificationCountTracker.Scope) = "${scope.pkg}\u0000${scope.channelId}"

    override fun islandExpressionVars(data: NotifData, vm: IslandViewModel) =
        mapOf("notification_count" to data.notificationCount.coerceAtLeast(0).toString())

    override fun inject(context: Context, extras: Bundle, data: NotifData) {
        extras.putBoolean(IslandDispatchContract.EXTRA_SUPPRESS_SOURCE_HEADS_UP, true)
        // 先把原始通知改成焦点通知（内容更新也要重新套用），再刷新计数岛。
        if (data.focusNotif != "off") injectFocusNotification(context, extras, data)
        postCountIsland(context, data)
    }

    // ── 原始通知 → 焦点通知（无岛）────────────────────────────────────────────

    /**
     * 只替换原始通知的焦点样式：复用与通知模板相同的渲染器管线（焦点通知自定义、息屏显示、
     * 外圈光效都走这里），但 vm.islandEnabled = false（不写小岛/大岛 areas）、
     * vm.dismissIsland = true（param_island.dismissIsland = true，系统不给这条通知显示岛），
     * 保证焦点通知不会创建自己的岛去抢计数岛。
     */
    private fun injectFocusNotification(context: Context, extras: Bundle, data: NotifData) {
        try {
            val rendererContext = buildFocusContext(context, data)
            resolveRenderer(data.renderer).render(context, extras, rendererContext)
            log { "count-trace focus restyle pkg=${data.pkg} channel=${data.channelId} renderer=${data.renderer} showNotification=${rendererContext.vm.showNotification} dismissIsland=${rendererContext.vm.dismissIsland}" }
        } catch (e: Exception) {
            logError("$TAG: focus restyle error: ${e.message}")
        }
    }

    private fun buildFocusContext(context: Context, data: NotifData): RendererContext {
        val fallbackIcon = Icon.createWithResource(context, android.R.drawable.ic_dialog_info)
        val islandIcon = resolveIslandIcon(data, fallbackIcon).toRounded(context)
        val focusIcon = (data.largeIcon ?: data.appIconRaw ?: data.notifIcon ?: fallbackIcon).toRounded(context)
        val showNotification = data.showNotification != "off"
        val baseVm = IslandViewModel(
            templateId        = TEMPLATE_ID,
            leftTitle         = data.title,
            rightTitle        = data.subtitle.ifEmpty { data.title },
            focusTitle        = data.title,
            focusContent      = data.subtitle.ifEmpty { data.title },
            islandIcon        = islandIcon,
            focusIcon         = focusIcon,
            // 计数岛不代发按钮，保持原始通知行为一致。
            actions           = emptyList(),
            updatable         = false,
            showNotification  = showNotification,
            setFocusProxy     = showNotification,
            preserveStatusBarSmallIcon = showNotification && data.preserveStatusBarSmallIcon != "off",
            firstFloat        = false,
            enableFloat       = false,
            timeoutSecs       = data.islandTimeout,
            isOngoing         = data.isOngoing,
            highlightColor    = data.highlightColor,
            outerGlow         = data.outerGlow,
            outEffectColor    = data.outEffectColor,
            aodText           = data.aodText,
            aodCustomizationJson = data.aodCustomizationJson,
            // 焦点通知本身不建岛：不写 areas，并用 dismissIsland 关掉系统给它的岛。
            islandEnabled     = false,
            dismissIsland     = true,
        )
        val applyResult = FocusCustomizationEngine.apply(context, data, baseVm)
        return RendererContext(
            vm = FocusCustomizationEngine.applyIsland(context, data, applyResult.vm),
            payload = applyResult.rendererPayload,
        )
    }

    // ── 计数岛代理 ────────────────────────────────────────────────────────────

    private fun postCountIsland(context: Context, data: NotifData) {
        val scope = NotificationCountTracker.Scope(data.pkg, data.channelId)
        val notificationId = NotificationCountTracker.notificationId(scope)
        val count = data.notificationCount.coerceAtLeast(0)
        val signature = "$count|${data.notificationKey.orEmpty()}|${data.title}|${data.subtitle}"
        if (lastPostedSignature.put(scopeKey(scope), signature) == signature) {
            log { "count-trace template skip pkg=${data.pkg} channel=${data.channelId} count=$count (unchanged)" }
            return
        }
        val fallback = Icon.createWithResource(context, android.R.drawable.ic_dialog_info)
        val icon = resolveIslandIcon(data, fallback).toRounded(context)
        val countIcon = createCountIcon(context, count)
        // 数量岛必须独立代发；不修改原始通知 extras，展开时原通知内容保持不变。
        val posted = IslandDispatcher.post(context, IslandRequest(
            title = "",
            content = count.toString(),
            icon = icon,
            rightIcon = countIcon,
            notifId = notificationId,
            timeoutSecs = data.islandTimeout,
            firstFloat = data.firstFloat == "on",
            enableFloat = data.enableFloatMode == "on",
            showNotification = false,
            contentIntent = data.contentIntent,
            isOngoing = data.isOngoing,
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
            updatable = false,
            // 计数岛本身就是岛，固定开启：UI 不为计数岛模板提供「启用超级岛」开关。
            islandEnabled = true,
            bypassSceneBehavior = false,
        ))
        log { "count-trace template post pkg=${data.pkg} channel=${data.channelId} count=$count timeout=${data.islandTimeout} ongoing=${data.isOngoing} posted=$posted notifId=$notificationId updatable=false" }
    }

    /** 岛图标来源与通知模板保持一致：由渠道「图标样式」决定来消息瞬间岛上的图标。 */
    private fun resolveIslandIcon(data: NotifData, fallback: Icon): Icon = when (data.iconMode) {
        "notif_small" -> data.notifIcon ?: fallback
        "notif_large" -> data.largeIcon ?: data.notifIcon ?: fallback
        "app_icon"    -> data.appIconRaw ?: fallback
        else          -> data.largeIcon ?: data.notifIcon ?: fallback
    }

    /** Gray circular badge used by the right island area; the text remains the expanded content. */
    private fun createCountIcon(context: Context, count: Int): Icon {
        val density = context.resources.displayMetrics.density
        val size = (40f * density).toInt().coerceAtLeast(40)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f
        val radius = size * 0.49f
        val circle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 110, 110, 110)
        }
        canvas.drawCircle(center, center, radius, circle)
        val label = count.coerceAtLeast(0).let { if (it > 99) "99+" else it.toString() }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = when {
                label.length >= 3 -> size * 0.27f
                label.length == 2 -> size * 0.34f
                else -> size * 0.44f
            }
        }
        val baseline = center - (text.ascent() + text.descent()) / 2f
        canvas.drawText(label, center, baseline, text)
        return Icon.createWithBitmap(bitmap)
    }
}
