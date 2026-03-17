package com.example.hyperisland.xposed.templates

import android.app.Notification
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import com.example.hyperisland.xposed.IslandDispatcher
import com.example.hyperisland.xposed.IslandRequest
import com.example.hyperisland.xposed.IslandTemplate
import com.example.hyperisland.xposed.NotifData
import com.example.hyperisland.xposed.toRounded
import de.robv.android.xposed.XposedBridge
import io.github.d4viddf.hyperisland_kit.HyperAction
import io.github.d4viddf.hyperisland_kit.HyperIslandNotification
import io.github.d4viddf.hyperisland_kit.HyperPicture
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoLeft
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoRight
import io.github.d4viddf.hyperisland_kit.models.PicInfo
import io.github.d4viddf.hyperisland_kit.models.TextInfo

/**
 * 通知超级岛通知构建器。
 * 适用于任意通知，以 bigIslandArea 摘要态展示：
 *  - 左侧：通知图标（无则应用图标）+ 通知标题（超 5 字符则改用应用名称）
 *  - 右侧：主标题在左已显示则展示通知内容，否则展示主标题
 * 按钮直接取自原通知（最多 2 个）。
 */
object NotificationIslandNotification : IslandTemplate {

    const val TEMPLATE_ID   = "notification_island"
    const val TEMPLATE_NAME = "通知超级岛"

    override val id          = TEMPLATE_ID
    override val displayName = TEMPLATE_NAME

    override fun inject(context: Context, extras: Bundle, data: NotifData) {
        if (data.focusNotif == "off") {
            // 不处理通知 extras，原始通知直接通过；
            // 但仍通过 IslandDispatcher 直接触发超级岛展示。
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

    /**
     * focusNotif == "off" 时使用：不修改原始通知，
     * 直接通过 IslandDispatcher 以 SystemUI 身份发出超级岛。
     * iconMode 与 timeoutSecs 依然对此岛生效。
     */
    private fun injectViaDispatcher(context: Context, data: NotifData) {
        try {
            val fallbackIcon = Icon.createWithResource(context, android.R.drawable.ic_dialog_info)
            val displayIcon = when (data.iconMode) {
                "notif_small" -> data.notifIcon ?: fallbackIcon
                "notif_large" -> data.largeIcon ?: data.notifIcon ?: fallbackIcon
                "app_icon"    -> data.appIconRaw ?: fallbackIcon
                else          -> data.largeIcon ?: data.notifIcon ?: fallbackIcon  // auto
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
                ),
            )

            XposedBridge.log(
                "HyperIsland[NotifIsland]: Dispatcher island — ${data.title} | iconMode=${data.iconMode} | timeout=${data.islandTimeout}"
            )
        } catch (e: Exception) {
            XposedBridge.log("HyperIsland[NotifIsland]: Dispatcher island error: ${e.message}")
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
            // 超级岛区域图标（bigIslandArea / smallIslandArea）
            val displayIcon = when (iconMode) {
                "notif_small" -> notifIcon ?: fallbackIcon
                "notif_large" -> largeIcon ?: notifIcon ?: fallbackIcon
                "app_icon"    -> appIconRaw ?: fallbackIcon
                else          -> largeIcon ?: notifIcon ?: fallbackIcon  // auto
            }.toRounded(context)
            // 焦点图标（iconTextInfo）
            val focusDisplayIcon = when (focusIconMode) {
                "notif_small" -> notifIcon ?: appIconRaw ?: fallbackIcon
                "notif_large" -> largeIcon ?: appIconRaw ?: notifIcon ?: fallbackIcon
                "app_icon"    -> appIconRaw ?: fallbackIcon
                else          -> largeIcon ?: appIconRaw ?: notifIcon ?: fallbackIcon  // auto
            }.toRounded(context)

            val leftText       = title
            val rightContent   = subtitle.ifEmpty { title }
            val displayContent = subtitle.ifEmpty { title }

            val resolvedFirstFloat  = firstFloat      == "on"
            val resolvedEnableFloat = enableFloatMode == "on"
            val showNotification    = focusNotif != "off"

            val builder = HyperIslandNotification.Builder(context, "notif_island", title)

            builder.addPicture(HyperPicture("key_notification_island_icon", displayIcon))
            builder.addPicture(HyperPicture("key_notification_focus_icon", focusDisplayIcon))

            builder.setIconTextInfo(
                picKey  = "key_notification_focus_icon",
                title   = title,
                content = displayContent,
            )

            builder.setIslandFirstFloat(resolvedFirstFloat)
            builder.setEnableFloat(resolvedEnableFloat)
            builder.setShowNotification(showNotification)
            builder.setIslandConfig(timeout = timeoutSecs)

            // 小岛：仅图标
            builder.setSmallIsland("key_notification_island_icon")

            // 大岛：左侧图标+标题，右侧内容
            builder.setBigIslandInfo(
                left = ImageTextInfoLeft(
                    type     = 1,
                    picInfo  = PicInfo(type = 1, pic = "key_notification_island_icon"),
                    textInfo = TextInfo(title = leftText),
                ),
                right = ImageTextInfoRight(
                    type     = 2,
                    textInfo = TextInfo(title = rightContent, narrowFont = true),
                ),
            )

            // 来自原通知的按钮（最多 2 个）
            val effectiveActions = actions.take(2)
            if (effectiveActions.isNotEmpty()) {
                val hyperActions = effectiveActions.mapIndexed { index, action ->
                    // 文本模式（无图标），避免 TextButtonInfo.actionIcon 指向不存在的 pic 键
                    HyperAction(
                        key              = "action_notif_island_$index",
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
            // 修正 textButton 字段名：新库输出 "actionIntent"，HyperOS V3 协议只认 "action"
            val jsonParam = fixTextButtonJson(builder.buildJsonParam())
                .let { if (!isOngoing) injectUpdatable(it, false) else it }
            extras.putString("miui.focus.param", jsonParam)

            XposedBridge.log(
                "HyperIsland[NotifIsland]: Island injected — $title | left=$leftText | right=$rightContent | buttons=${actions.size} | isOngoing=${isOngoing}"
            )

        } catch (e: Exception) {
            XposedBridge.log("HyperIsland[NotifIsland]: Island injection error: ${e.message}")
        }
    }

    /** 将 param_v2.updatable 注入为指定值（库默认 true，不可一键清除）。*/
    private fun injectUpdatable(jsonParam: String, updatable: Boolean): String {
        return try {
            val json = org.json.JSONObject(jsonParam)
            val pv2  = json.optJSONObject("param_v2") ?: return jsonParam
            pv2.put("updatable", updatable)
            json.toString()
        } catch (_: Exception) { jsonParam }
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
