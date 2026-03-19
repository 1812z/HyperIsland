package io.github.hyperisland.xposed.templates

import android.app.Notification
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import io.github.hyperisland.R
import io.github.hyperisland.xposed.IslandDispatcher
import io.github.hyperisland.xposed.IslandRequest
import io.github.hyperisland.xposed.IslandTemplate
import io.github.hyperisland.xposed.NotifData
import io.github.hyperisland.xposed.moduleContext
import io.github.hyperisland.xposed.toRounded
import de.robv.android.xposed.XposedBridge
import io.github.d4viddf.hyperisland_kit.HyperAction
import io.github.d4viddf.hyperisland_kit.HyperIslandNotification
import io.github.d4viddf.hyperisland_kit.HyperPicture

/**
 * 通知超级岛通知构建器（OS2 版本）。
 * 使用 OS2 协议的 setBaseInfo 组件展示：
 *  - ticker  = OS2 焦点通知状态栏显示文案（必须）
 *  - tickerPic = OS2 焦点通知状态栏图标 picKey（不传默认为应用图标）
 */
object NotificationIslandNotificationOld : IslandTemplate {

    const val TEMPLATE_ID   = "notification_island_old"
    const val TEMPLATE_NAME = "通知超级岛(OS2)"

    override val id          = TEMPLATE_ID
    override val displayName = TEMPLATE_NAME

    override fun getDisplayName(context: Context): String = try {
        context.moduleContext().getString(R.string.template_notification_island_name) + "(OS2)"
    } catch (_: Exception) { TEMPLATE_NAME }

    override fun inject(context: Context, extras: Bundle, data: NotifData) {
        if (data.focusNotif == "off") {
            injectViaDispatcher(context, data)
            return
        }
        inject(
            context         = context,
            extras          = extras,
            title           = data.title,
            subtitle        = data.subtitle,
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
            isOngoing       = data.isOngoing,
        )
    }

    private fun injectViaDispatcher(context: Context, data: NotifData) {
        try {
            val fallbackIcon = Icon.createWithResource(context, android.R.drawable.ic_dialog_info)
            val displayIcon = when (data.iconMode) {
                "notif_small" -> data.notifIcon ?: fallbackIcon
                "notif_large" -> data.largeIcon ?: data.notifIcon ?: fallbackIcon
                "app_icon"    -> data.appIconRaw ?: fallbackIcon
                else          -> data.largeIcon ?: data.notifIcon ?: fallbackIcon
            }.toRounded(context)

            val resolvedFirstFloat  = data.firstFloat      == "on"
            val resolvedEnableFloat = data.enableFloatMode == "on"

            IslandDispatcher.post(
                context,
                IslandRequest(
                    title            = data.title,
                    content          = data.subtitle.ifEmpty { data.title },
                    icon             = displayIcon,
                    timeoutSecs      = data.islandTimeout,
                    firstFloat       = resolvedFirstFloat,
                    enableFloat      = resolvedEnableFloat,
                    showNotification = false,
                    contentIntent    = data.contentIntent,
                    isOngoing        = data.isOngoing,
                    actions          = data.actions.take(2),
                ),
            )

            XposedBridge.log(
                "HyperIsland[NotifIslandOld]: Dispatcher island — ${data.title} | iconMode=${data.iconMode} | timeout=${data.islandTimeout}"
            )
        } catch (e: Exception) {
            XposedBridge.log("HyperIsland[NotifIslandOld]: Dispatcher island error: ${e.message}")
        }
    }

    private fun inject(
        context: Context,
        extras: Bundle,
        title: String,
        subtitle: String,
        actions: List<Notification.Action>,
        notifIcon: Icon?,
        largeIcon: Icon?,
        appIconRaw: Icon?,
        iconMode: String?,
        focusIconMode: String?,
        focusNotif: String,
        firstFloat: String,
        enableFloatMode: String,
        timeoutSecs: Int,
        isOngoing: Boolean,
    ) {
        try {
            val fallbackIcon = Icon.createWithResource(context, android.R.drawable.ic_dialog_info)
            val displayIcon = when (iconMode) {
                "notif_small" -> notifIcon ?: fallbackIcon
                "notif_large" -> largeIcon ?: notifIcon ?: fallbackIcon
                "app_icon"    -> appIconRaw ?: fallbackIcon
                else          -> largeIcon ?: notifIcon ?: fallbackIcon
            }.toRounded(context)
            val focusDisplayIcon = when (focusIconMode) {
                "notif_small" -> notifIcon ?: appIconRaw ?: fallbackIcon
                "notif_large" -> largeIcon ?: appIconRaw ?: notifIcon ?: fallbackIcon
                "app_icon"    -> appIconRaw ?: fallbackIcon
                else          -> largeIcon ?: appIconRaw ?: notifIcon ?: fallbackIcon
            }.toRounded(context)

            val leftText     = title
            val rightContent = subtitle.ifEmpty { title }

            val resolvedFirstFloat  = firstFloat      == "on"
            val resolvedEnableFloat = enableFloatMode == "on"
            val showNotification    = focusNotif != "off"

            // OS2 焦点通知状态栏文案（必须）
            val ticker: String = title
            // OS2 焦点通知状态栏图标 picKey，不传默认为应用图标
            val tickerPic: String = "key_notification_focus_icon_old"

            val builder = HyperIslandNotification.Builder(context, "notif_island_old", title)

            builder.addPicture(HyperPicture("key_notification_island_icon_old", displayIcon))
            builder.addPicture(HyperPicture("key_notification_focus_icon_old", focusDisplayIcon))

            builder.setIslandFirstFloat(resolvedFirstFloat)
            builder.setEnableFloat(resolvedEnableFloat)
            builder.setShowNotification(showNotification)
            builder.setIslandConfig(timeout = timeoutSecs)

            // OS2 组件：setBaseInfo 替代 OS3 的 setBigIslandInfo / setSmallIsland / setIconTextInfo
            builder.setBaseInfo(
                type       = 1,
                title      = leftText,
                content    = rightContent,
                subTitle   = "",
                pictureKey = "key_notification_island_icon_old",
            )

            // 来自原通知的按钮（最多 2 个）
            val effectiveActions = actions.take(2)
            if (effectiveActions.isNotEmpty()) {
                val hyperActions = effectiveActions.mapIndexed { index, action ->
                    HyperAction(
                        key              = "action_notif_island_old_$index",
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

            val jsonParam = fixTextButtonJson(builder.buildJsonParam())
                .let { if (!isOngoing) injectUpdatable(it, false) else it }
            extras.putString("miui.focus.param", jsonParam)

            XposedBridge.log(
                "HyperIsland[NotifIslandOld]: Island injected — $title | left=$leftText | right=$rightContent | buttons=${actions.size} | isOngoing=$isOngoing | ticker=$ticker"
            )

        } catch (e: Exception) {
            XposedBridge.log("HyperIsland[NotifIslandOld]: Island injection error: ${e.message}")
        }
    }

    private fun injectUpdatable(jsonParam: String, updatable: Boolean): String {
        return try {
            val json = org.json.JSONObject(jsonParam)
            val pv2  = json.optJSONObject("param_v2") ?: return jsonParam
            pv2.put("updatable", updatable)
            json.toString()
        } catch (_: Exception) { jsonParam }
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
