package io.github.hyperisland.xposed.islanddispatch.invoke

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import io.github.d4viddf.hyperisland_kit.HyperAction
import io.github.d4viddf.hyperisland_kit.HyperIslandNotification
import io.github.d4viddf.hyperisland_kit.HyperPicture
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoLeft
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoRight
import io.github.d4viddf.hyperisland_kit.models.PicInfo
import io.github.d4viddf.hyperisland_kit.models.TextInfo
import io.github.hyperisland.R
import io.github.hyperisland.utils.getAppIcon
import io.github.hyperisland.xposed.hook.FocusNotifStatusBarIconHook
import io.github.hyperisland.xposed.hook.MarqueeHook
import io.github.hyperisland.xposed.islanddispatch.core.IslandDispatchState
import io.github.hyperisland.xposed.islanddispatch.definition.IslandDispatchContract
import io.github.hyperisland.xposed.islanddispatch.definition.IslandRequest
import io.github.hyperisland.xposed.log
import io.github.hyperisland.xposed.logError
import io.github.hyperisland.xposed.utils.SceneBehavior
import io.github.hyperisland.xposed.utils.toRounded

internal object IslandDispatcherNotifier {

    private const val EXTRA_OWNER = "hyperisland.owner"
    private const val OWNER_MARKER = "io.github.hyperisland"
    private const val EFFECT_SRC = "outer_glow"
    private const val KEEP_ISLAND_NOTIF_ID = 0x4B494B49
    private val channelLock = Any()
    @Volatile private var channelReady = false

    fun post(context: Context, request: IslandRequest) {
        try {
            val sceneDecision = SceneBehavior.resolve(
                context = context,
                surface = SceneBehavior.Surface.DISPATCHER,
                sourcePackage = request.sourcePackage.orEmpty(),
                channelId = request.sourceChannelId.orEmpty(),
            )
            if (!request.bypassSceneBehavior && sceneDecision.shouldSuppress) {
                IslandDispatchState.module?.log(
                    "${IslandDispatchContract.TAG}: skip dispatcher post by scene rule",
                )
                return
            }

            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            ensureChannels(context)
            val channelId = if (request.notificationSilent) {
                IslandDispatchContract.SILENT_CHANNEL_ID
            } else {
                IslandDispatchContract.CHANNEL_ID
            }

            request.notificationExtras?.let { extras ->
                postNotificationWithExtras(context, nm, request, extras, channelId)
                return
            }

            val appIcon = resolveIcon(request.icon, context)
            val focusTitle = request.focusTitle ?: request.title
            val focusContent = request.focusContent ?: request.content
            val islandBuilder = HyperIslandNotification.Builder(
                context,
                "hyper_island_dispatch",
                focusTitle,
            )

            islandBuilder.addPicture(HyperPicture("key_island_icon", appIcon))
            if (!request.islandOnly) {
                islandBuilder.addPicture(HyperPicture("key_focus_icon", appIcon))
                islandBuilder.setIconTextInfo(
                    picKey = "key_focus_icon",
                    title = focusTitle,
                    content = focusContent,
                )
            }
            val effectiveFirstFloat = sceneDecision.applyToBoolean(request.firstFloat)
            val effectiveEnableFloat = sceneDecision.applyToBoolean(request.enableFloat)

            islandBuilder.setIslandFirstFloat(effectiveFirstFloat)
            islandBuilder.setEnableFloat(effectiveEnableFloat)
            islandBuilder.setShowNotification(request.showNotification)
            islandBuilder.setIslandConfig(
                timeout = request.timeoutSecs,
                dismissible = request.dismissIsland,
                highlightColor = request.highlightColor,
            )

            // 小岛 + 大岛（islandEnabled=false 时不构建，param_island 自然不存在）
            if (request.islandEnabled) {
                islandBuilder.setSmallIsland("key_island_icon")
                val bigIslandLeft = if (request.showIslandIcon) {
                    ImageTextInfoLeft(
                        type = 1,
                        picInfo = PicInfo(type = 1, pic = "key_island_icon"),
                        textInfo = TextInfo(
                            title = request.title,
                            narrowFont = request.showLeftNarrowFont,
                            showHighlightColor = request.showLeftHighlightColor,
                        ),
                    )
                } else {
                    ImageTextInfoLeft(
                        type = 1,
                        textInfo = TextInfo(
                            title = request.title,
                            narrowFont = request.showLeftNarrowFont,
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
                            narrowFont = request.showRightNarrowFont,
                            showHighlightColor = request.showRightHighlightColor,
                        ),
                    ),
                )
            }

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

            val customMode = request.focusRemoteViews != null ||
                    request.focusNightRemoteViews != null ||
                    request.focusIslandExpandRemoteViews != null
            val notificationExtras = if (customMode) {
                val mainRemoteViews = request.focusRemoteViews
                    ?: createFocusTextRemoteViews(focusTitle, focusContent)
                islandBuilder
                    .setTickerIcon(appIcon)
                    .setCustomRemoteView(mainRemoteViews)
                if (request.islandEnabled) {
                    islandBuilder.setCustomTinyRemoteView(
                        createIslandTinyTextRemoteViews(request.title, request.content),
                    )
                    request.focusIslandExpandRemoteViews?.let {
                        islandBuilder.setCustomIslandExpandRemoteView(it)
                    }
                }
                islandBuilder.buildCustomExtras().apply {
                    request.focusNightRemoteViews?.let {
                        putParcelable("miui.focus.rvNight", it)
                    }
                    request.focusAodRemoteViews?.let {
                        putParcelable("miui.focus.rvAod", it)
                    }
                    request.focusFullAodRemoteViews?.let {
                        putParcelable("miui.focus.rv.fullAod", it)
                    }
                }
            } else {
                islandBuilder.buildResourceBundle()
            }
            val publicVersion = Notification.Builder(context, channelId)
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

            val notif = Notification.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(focusTitle)
                .setContentText(focusContent)
                .setVisibility(visibility)
                .setPublicVersion(publicVersion)
                .setAutoCancel(true)
                .apply {
                    if (request.isOngoing) setOngoing(true)
                    request.contentIntent?.let { setContentIntent(it) }
                }
                .build()

            notif.extras.putAll(notificationExtras)
            if (!customMode) flattenActionsToExtras(notificationExtras, notif.extras)
            val aodIconKey = "miui.focus.pic_aod"
            request.icon?.let { notif.extras.putParcelable(aodIconKey, it) }

            if (!customMode) {
                val jsonParam = islandBuilder.buildJsonParam()
                    .let { fixTextButtonJson(it) }
                    .let {
                        injectIslandAppearance(
                            jsonParam = it,
                            highlightColor = request.highlightColor,
                            outerGlow = request.outerGlow,
                            islandOuterGlow = request.islandOuterGlow,
                            islandOuterGlowColor = request.islandOuterGlowColor,
                            outEffectColor = request.outEffectColor,
                            dismissIsland = request.dismissIsland,
                            aodTitle = request.aodTitle ?: request.content.ifEmpty { request.title },
                            aodPicKey = aodIconKey,
                            islandEnabled = request.islandEnabled,
                            updatable = request.notifId == KEEP_ISLAND_NOTIF_ID && !request.clearBeforePost,
                        )
                    }
                notif.extras.putString("miui.focus.param", jsonParam)
            }
            request.sourcePackage?.let { notif.extras.putString("hyperisland_source_pkg", it) }
            request.sourceChannelId?.let {
                notif.extras.putString("hyperisland_source_channel", it)
            }
            request.outEffectColor?.let {
                notif.extras.putString("hyperisland_focus_out_effect_color", it)
            }
            request.islandOuterGlowColor?.let {
                notif.extras.putString("hyperisland_island_outer_glow_color", it)
            }
            notif.extras.putString(EXTRA_OWNER, OWNER_MARKER)
            if (request.islandEnabled && request.islandOuterGlow) {
                notif.extras.putString("miui.bigIsland.effect.src", EFFECT_SRC)
            } else {
                notif.extras.remove("miui.bigIsland.effect.src")
            }
            if (request.outerGlow) {
                notif.extras.putString("miui.effect.src", EFFECT_SRC)
            } else {
                notif.extras.remove("miui.effect.src")
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

            if (request.clearBeforePost) {
                nm.cancel(request.notifId)
            }
            nm.notify(request.notifId, notif)
            IslandDispatchState.postedIds.add(request.notifId)
            request.sourcePackage?.let { pkg ->
                MarqueeHook.markDirectProxyPosted(pkg, request.sourceChannelId ?: "toast")
            }
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

    private fun postNotificationWithExtras(
        context: Context,
        nm: NotificationManager,
        request: IslandRequest,
        extras: Bundle,
        channelId: String,
    ) {
        val notif = Notification.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle(request.title)
            .setContentText(request.content)
            .setVisibility(request.notificationVisibility)
            .setOnlyAlertOnce(request.notificationOnlyAlertOnce)
            .setOngoing(request.isOngoing)
            .apply {
                if (request.notificationSilent) {
                    setSound(null)
                    setVibrate(null)
                    setDefaults(0)
                }
            }
            .build()
        notif.extras.putAll(extras)
        notif.extras.putString(EXTRA_OWNER, OWNER_MARKER)
        if (request.clearBeforePost) nm.cancel(request.notifId)
        nm.notify(request.notifId, notif)
        IslandDispatchState.postedIds.add(request.notifId)
        IslandDispatchState.module?.log(
            "${IslandDispatchContract.TAG}: posted custom extras notifId=${request.notifId}",
        )
    }

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (channelReady) return
        synchronized(channelLock) {
            if (channelReady) return
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            val existing = nm.getNotificationChannel(IslandDispatchContract.CHANNEL_ID)
            if (existing != null) {
                existing.setShowBadge(false)
                existing.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                nm.createNotificationChannel(existing)
            } else {
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
            val silentChannel = nm.getNotificationChannel(IslandDispatchContract.SILENT_CHANNEL_ID)
            if (silentChannel == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        IslandDispatchContract.SILENT_CHANNEL_ID,
                        IslandDispatchContract.SILENT_CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_HIGH,
                    ).apply {
                        setShowBadge(false)
                        lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                        setSound(null, null)
                        enableVibration(false)
                        vibrationPattern = null
                    },
                )
            }
            channelReady = true
        }
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
        islandOuterGlow: Boolean,
        islandOuterGlowColor: String?,
        outEffectColor: String?,
        dismissIsland: Boolean,
        aodTitle: String?,
        aodPicKey: String?,
        islandEnabled: Boolean = true,
        updatable: Boolean = false,
    ): String {
        if (highlightColor == null && !outerGlow && !islandOuterGlow && islandOuterGlowColor.isNullOrBlank() && outEffectColor.isNullOrBlank() && !dismissIsland && aodTitle.isNullOrBlank() && aodPicKey.isNullOrBlank() && !updatable) {
            return jsonParam
        }
        return try {
            val json = org.json.JSONObject(jsonParam)
            val pv2 = json.optJSONObject("param_v2") ?: return jsonParam
            // islandEnabled=false 时不写 param_island 任何字段
            if (islandEnabled) {
                val paramIsland = pv2.optJSONObject("param_island") ?: org.json.JSONObject()
                highlightColor?.let { paramIsland.put("highlightColor", it) }
                if (dismissIsland) paramIsland.put("dismissIsland", true)
                if (!islandOuterGlowColor.isNullOrBlank()) paramIsland.put("outEffectColor", islandOuterGlowColor)
                if (islandOuterGlow) paramIsland.put("outEffectSrc", "outer_glow")
                pv2.put("param_island", paramIsland)
            }
            if (outerGlow) pv2.put("outEffectSrc", "outer_glow")
            if (updatable) pv2.put("updatable", true)
            if (!outEffectColor.isNullOrBlank()) pv2.put("outEffectColor", outEffectColor)
            if (!aodTitle.isNullOrBlank()) pv2.put("aodTitle", aodTitle)
            if (!aodPicKey.isNullOrBlank()) pv2.put("aodPic", aodPicKey)
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

    private fun createFocusTextRemoteViews(title: String, content: String): RemoteViews =
        RemoteViews(OWNER_MARKER, R.layout.focus_notification_text_fallback).apply {
            setTextViewText(R.id.focus_fallback_title, title)
            setTextViewText(R.id.focus_fallback_content, content)
            setViewVisibility(
                R.id.focus_fallback_content,
                if (content.isBlank()) android.view.View.GONE else android.view.View.VISIBLE,
            )
        }

    private fun createIslandTinyTextRemoteViews(title: String, content: String): RemoteViews =
        RemoteViews(OWNER_MARKER, R.layout.focus_island_tiny_text).apply {
            setTextViewText(R.id.focus_tiny_left, title)
            setTextViewText(R.id.focus_tiny_right, content)
            setViewVisibility(
                R.id.focus_tiny_right,
                if (content.isBlank()) android.view.View.GONE else android.view.View.VISIBLE,
            )
        }
}
