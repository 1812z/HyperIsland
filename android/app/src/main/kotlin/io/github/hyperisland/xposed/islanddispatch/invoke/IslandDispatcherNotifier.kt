package io.github.hyperisland.xposed.islanddispatch.invoke

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import io.github.d4viddf.hyperisland_kit.HyperAction
import io.github.d4viddf.hyperisland_kit.HyperIslandNotification
import io.github.d4viddf.hyperisland_kit.HyperPicture
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoLeft
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoRight
import io.github.d4viddf.hyperisland_kit.models.PicInfo
import io.github.d4viddf.hyperisland_kit.models.TextInfo
import io.github.hyperisland.utils.getAppIcon
import io.github.hyperisland.xposed.hook.FocusNotifStatusBarIconHook
import io.github.hyperisland.xposed.hook.MarqueeHook
import io.github.hyperisland.xposed.islanddispatch.core.IslandDispatchState
import io.github.hyperisland.xposed.islanddispatch.definition.IslandDispatchContract
import io.github.hyperisland.xposed.islanddispatch.definition.IslandRequest
import io.github.hyperisland.xposed.log
import io.github.hyperisland.xposed.logError
import io.github.hyperisland.xposed.utils.FullscreenBehavior
import io.github.hyperisland.xposed.utils.toRounded

internal object IslandDispatcherNotifier {

    fun post(context: Context, request: IslandRequest) {
        try {
            val fullscreenMode = FullscreenBehavior.mode()
            val fullscreenDetected = FullscreenBehavior.isFullscreenLike(context)
            if (fullscreenDetected && fullscreenMode == FullscreenBehavior.MODE_FALLBACK) {
                IslandDispatchState.module?.log(
                    "${IslandDispatchContract.TAG}: skip dispatcher post in fullscreen fallback",
                )
                return
            }

            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            ensureChannel(context)

            val appIcon = resolveIcon(request.icon, context)
            val islandBuilder = HyperIslandNotification.Builder(
                context,
                "hyper_island_dispatch",
                request.title,
            )

            islandBuilder.addPicture(HyperPicture("key_island_icon", appIcon))
            islandBuilder.addPicture(HyperPicture("key_focus_icon", appIcon))

            islandBuilder.setIconTextInfo(
                picKey = "key_focus_icon",
                title = request.title,
                content = request.content,
            )
            val effectiveFirstFloat = if (
                fullscreenDetected && fullscreenMode == FullscreenBehavior.MODE_EXPAND
            ) {
                true
            } else {
                request.firstFloat
            }
            val effectiveEnableFloat = if (
                fullscreenDetected && fullscreenMode == FullscreenBehavior.MODE_EXPAND
            ) {
                true
            } else {
                request.enableFloat
            }

            islandBuilder.setIslandFirstFloat(effectiveFirstFloat)
            islandBuilder.setEnableFloat(effectiveEnableFloat)
            islandBuilder.setShowNotification(request.showNotification)
            islandBuilder.setIslandConfig(timeout = request.timeoutSecs)
            islandBuilder.setSmallIsland("key_island_icon")
            val bigIslandLeft = if (request.showIslandIcon) {
                ImageTextInfoLeft(
                    type = 1,
                    picInfo = PicInfo(type = 1, pic = "key_island_icon"),
                    textInfo = TextInfo(
                        title = request.title,
                        showHighlightColor = request.showLeftHighlightColor,
                    ),
                )
            } else {
                ImageTextInfoLeft(
                    type = 1,
                    textInfo = TextInfo(
                        title = request.title,
                        showHighlightColor = request.showLeftHighlightColor,
                    ),
                )
            }
            islandBuilder.setBigIslandInfo(
                left = bigIslandLeft,
                right = ImageTextInfoRight(
                    type = 2,
                    textInfo = TextInfo(
                        title = request.content,
                        narrowFont = true,
                        showHighlightColor = request.showRightHighlightColor,
                    ),
                ),
            )

            val effectiveActions = request.actions.take(2)
            if (effectiveActions.isNotEmpty()) {
                val hyperActions = effectiveActions.mapIndexed { index, action ->
                    HyperAction(
                        key = "action_dispatcher_$index",
                        title = action.title?.toString() ?: "",
                        pendingIntent = action.actionIntent,
                        actionIntentType = 2,
                    )
                }
                hyperActions.forEach { islandBuilder.addHiddenAction(it) }
                islandBuilder.setTextButtons(*hyperActions.toTypedArray())
            }

            val resourceBundle = islandBuilder.buildResourceBundle()
            val publicVersion = Notification.Builder(context, IslandDispatchContract.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(IslandDispatchContract.CHANNEL_NAME)
                .setContentText("")
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .build()

            val visibility = if (request.showNotification) {
                Notification.VISIBILITY_PRIVATE
            } else {
                Notification.VISIBILITY_SECRET
            }

            val notif = Notification.Builder(context, IslandDispatchContract.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(request.title)
                .setContentText(request.content)
                .setVisibility(visibility)
                .setPublicVersion(publicVersion)
                .setAutoCancel(true)
                .apply {
                    if (request.isOngoing) setOngoing(true)
                    request.contentIntent?.let { setContentIntent(it) }
                }
                .build()

            notif.extras.putAll(resourceBundle)
            flattenActionsToExtras(resourceBundle, notif.extras)

            val jsonParam = islandBuilder.buildJsonParam()
                .let { fixTextButtonJson(it) }
                .let {
                    injectIslandAppearance(
                        jsonParam = it,
                        highlightColor = request.highlightColor,
                        outerGlow = request.outerGlow,
                        outEffectColor = request.outEffectColor,
                        dismissIsland = request.dismissIsland,
                    )
                }
            notif.extras.putString("miui.focus.param", jsonParam)
            request.sourcePackage?.let { notif.extras.putString("hyperisland_source_pkg", it) }
            request.sourceChannelId?.let {
                notif.extras.putString("hyperisland_source_channel", it)
            }

            if (request.showNotification) {
                notif.extras.putBoolean("hyperisland_focus_proxy", true)
            }
            val shouldPreserveStatusBarSmallIcon =
                request.showNotification && request.preserveStatusBarSmallIcon
            if (shouldPreserveStatusBarSmallIcon) {
                notif.extras.putBoolean("hyperisland_preserve_status_bar_small_icon", true)
                FocusNotifStatusBarIconHook.markDirectProxyPosted(request.timeoutSecs)
            }
            IslandDispatchState.module?.log(
                "${IslandDispatchContract.TAG}: preserve marker=$shouldPreserveStatusBarSmallIcon title=${request.title} | notifId=${request.notifId} | showNotification=${request.showNotification}",
            )

            nm.cancel(request.notifId)
            nm.notify(request.notifId, notif)
            IslandDispatchState.postedIds.add(request.notifId)
            request.sourcePackage?.let { pkg ->
                MarqueeHook.markDirectProxyPosted(pkg, request.sourceChannelId ?: "toast")
            }

            IslandDispatchState.module?.log(
                "${IslandDispatchContract.TAG}: posted: ${request.title} | ${request.content} | highlight=${request.highlightColor} | dismiss=${request.dismissIsland}",
            )
        } catch (e: Exception) {
            IslandDispatchState.module?.logError("${IslandDispatchContract.TAG}: post error: ${e.message}")
        }
    }

    fun cancel(context: Context, notifId: Int) {
        try {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            nm.cancel(notifId)
            IslandDispatchState.postedIds.remove(notifId)
            IslandDispatchState.module?.log("${IslandDispatchContract.TAG}: cancel notifId=$notifId")
        } catch (e: Exception) {
            IslandDispatchState.module?.logError("${IslandDispatchContract.TAG}: cancel error: ${e.message}")
        }
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val existing = nm.getNotificationChannel(IslandDispatchContract.CHANNEL_ID)
        if (existing != null) {
            existing.setShowBadge(false)
            existing.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            nm.createNotificationChannel(existing)
            return
        }

        nm.createNotificationChannel(
            NotificationChannel(
                IslandDispatchContract.CHANNEL_ID,
                IslandDispatchContract.CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
        )
    }

    private fun resolveIcon(icon: Icon?, context: Context): Icon {
        if (icon != null) return icon
        return try {
            context.packageManager.getAppIcon("io.github.hyperisland")
                ?.toRounded(context)
                ?: fallbackIcon(context)
        } catch (_: Exception) {
            fallbackIcon(context)
        }
    }

    private fun fallbackIcon(context: Context): Icon =
        Icon.createWithResource(context, android.R.drawable.sym_def_app_icon)

    private fun injectIslandAppearance(
        jsonParam: String,
        highlightColor: String?,
        outerGlow: Boolean,
        outEffectColor: String?,
        dismissIsland: Boolean,
    ): String {
        if (highlightColor == null && !outerGlow && outEffectColor.isNullOrBlank() && !dismissIsland) {
            return jsonParam
        }
        return try {
            val json = org.json.JSONObject(jsonParam)
            val pv2 = json.optJSONObject("param_v2") ?: return jsonParam
            val paramIsland = pv2.optJSONObject("param_island") ?: org.json.JSONObject()
            highlightColor?.let { paramIsland.put("highlightColor", it) }
            if (dismissIsland) paramIsland.put("dismissIsland", true)
            pv2.put("param_island", paramIsland)
            if (outerGlow) pv2.put("outEffectSrc", "outer_glow")
            if (!outEffectColor.isNullOrBlank()) pv2.put("outEffectColor", outEffectColor)
            json.toString()
        } catch (_: Exception) {
            jsonParam
        }
    }

    private fun fixTextButtonJson(jsonParam: String): String {
        return try {
            val json = org.json.JSONObject(jsonParam)
            val pv2 = json.optJSONObject("param_v2") ?: return jsonParam
            val btns = pv2.optJSONArray("textButton")
            if (btns != null) {
                for (i in 0 until btns.length()) {
                    val btn = btns.getJSONObject(i)
                    val key = btn.optString("actionIntent").takeIf { it.isNotEmpty() } ?: continue
                    btn.put("action", key)
                    btn.remove("actionIntent")
                    btn.remove("actionIntentType")
                }
            }
            json.toString()
        } catch (_: Exception) {
            jsonParam
        }
    }

    private fun flattenActionsToExtras(resourceBundle: Bundle, extras: Bundle) {
        val nested = resourceBundle.getBundle("miui.focus.actions") ?: return
        for (key in nested.keySet()) {
            val action: Notification.Action? =
                if (Build.VERSION.SDK_INT >= 33) {
                    nested.getParcelable(key, Notification.Action::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    nested.getParcelable(key)
                }
            if (action != null) extras.putParcelable(key, action)
        }
    }
}
