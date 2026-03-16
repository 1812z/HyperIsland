package com.example.hyperisland.xposed.templates

import android.app.Notification
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Bundle
import com.example.hyperisland.xposed.IslandTemplate
import com.example.hyperisland.xposed.NotifData
import com.example.hyperisland.xposed.toRounded
import com.xzakota.hyper.notification.focus.FocusNotification
import de.robv.android.xposed.XposedBridge

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

    override fun inject(context: Context, extras: Bundle, data: NotifData) = inject(
        context         = context,
        extras          = extras,
        title           = data.title,
        subtitle        = data.subtitle,
        actions         = data.actions,
        notifIcon       = data.notifIcon,
        largeIcon       = data.largeIcon,
        appIconRaw      = data.appIconRaw,
        iconMode        = data.iconMode,
        focusNotif      = data.focusNotif,
        firstFloat      = data.firstFloat,
        enableFloatMode = data.enableFloatMode,
        timeoutSecs     = data.islandTimeout,
        isOngoing       = data.isOngoing,
    )

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
        focusNotif: String,
        firstFloat: String,
        enableFloatMode: String,
        timeoutSecs: Int,
        isOngoing: Boolean,
    ) {
        try {
            val fallbackIcon = Icon.createWithResource(context, android.R.drawable.ic_dialog_info)
            val displayIcon  = when (iconMode) {
                "notif_small" -> notifIcon ?: fallbackIcon
                "notif_large" -> largeIcon ?: notifIcon ?: fallbackIcon
                "app_icon"    -> appIconRaw ?: fallbackIcon
                else          -> notifIcon ?: largeIcon ?: fallbackIcon  // auto
            }.toRounded(context)

            val leftText       = title
            val rightContent   = subtitle.ifEmpty { title }
            val displayContent = subtitle.ifEmpty { title }

            val resolvedFirstFloat  = firstFloat      == "on"
            val resolvedEnableFloat = enableFloatMode == "on"
            val focusNotificaiton = focusNotif != "off"

            val islandExtras = FocusNotification.buildV3 {
                val iconKey = createPicture("key_notification_island_icon", displayIcon)

                if (focusNotificaiton) {
                    islandFirstFloat = (firstFloat == "on")
                    enableFloat = (enableFloatMode == "on")
                }
                updatable        = isOngoing
                isShowNotification = focusNotificaiton
                ticker = title
                island {
                    islandProperty = 1
                    islandTimeout  = timeoutSecs
                    bigIslandArea {
                        imageTextInfoLeft {
                            type = 1
                            picInfo {
                                type = 1
                                pic  = iconKey
                            }
                            textInfo {
                                this.title = leftText
                            }
                        }
                        imageTextInfoRight {
                            type = 2
                            textInfo {
                                this.title = rightContent
                                narrowFont = true
                            }
                        }
                    }
                    smallIslandArea {
                        picInfo {
                            type = 1
                            pic  = iconKey
                        }
                    }
                }

                iconTextInfo {
                    this.title = title
                    content    = displayContent
                    animIconInfo {
                        type = 0
                        src  = iconKey
                    }
                }

                val effectiveActions = actions.take(2)
                if (effectiveActions.isNotEmpty()) {
                    textButton {
                        effectiveActions.forEachIndexed { index, action ->
                            addActionInfo {
                                val btnIcon = action.getIcon()
                                    ?: Icon.createWithResource(context, android.R.drawable.ic_menu_send)
                                val wrappedAction = Notification.Action.Builder(
                                    btnIcon,
                                    action.title ?: "",
                                    action.actionIntent,
                                ).build()
                                this.action = createAction("action_notif_island_$index", wrappedAction)
                                actionTitle = action.title?.toString() ?: ""
                            }
                        }
                    }
                }
            }

            extras.putAll(islandExtras)

            XposedBridge.log(
                "HyperIsland[NotifIsland]: Island injected — $title | left=$leftText | right=$rightContent | buttons=${actions.size} | isOngoing=${isOngoing}"
            )

        } catch (e: Exception) {
            XposedBridge.log("HyperIsland[NotifIsland]: Island injection error: ${e.message}")
        }
    }
}
