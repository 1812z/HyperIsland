package io.github.hyperisland.xposed

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Build
import io.github.hyperisland.getAppIcon
import android.os.Bundle
import android.util.Log
import io.github.hyperisland.xposed.hook.FocusNotifStatusBarIconHook
import io.github.d4viddf.hyperisland_kit.HyperAction
import io.github.d4viddf.hyperisland_kit.HyperIslandNotification
import io.github.d4viddf.hyperisland_kit.HyperPicture
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoLeft
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoRight
import io.github.d4viddf.hyperisland_kit.models.PicInfo
import io.github.d4viddf.hyperisland_kit.models.TextInfo

/**
 * SystemUI 进程内超级岛发送调度器。
 *
 * ## 原理
 * HyperOS 会抑制前台应用自身发出的岛通知。将通知改由 SystemUI（system UID）发出，
 * 可绕过该限制。[IslandDispatcherHook] 在 SystemUI 进程启动时调用 [register]，
 * 注册一个受权限保护的 BroadcastReceiver；HyperIsland 应用通过 [sendBroadcast]
 * 触发它，由此以 SystemUI 身份发出岛通知。
 *
 * ## 其他 Xposed 模块的使用方式（同在 SystemUI 进程内）
 * ```kotlin
 * IslandDispatcher.post(
 *     context,
 *     IslandRequest(title = "标题", content = "内容", icon = myIcon)
 * )
 * ```
 *
 * ## 跨进程使用方式（从任意应用）
 * ```kotlin
 * IslandDispatcher.sendBroadcast(
 *     context,
 *     IslandRequest(title = "标题", content = "内容", icon = myIcon)
 * )
 * ```
 */
object IslandDispatcher {

    /** 广播 Action，由 HyperIsland 应用发出，由 SystemUI 进程内 Receiver 接收。*/
    const val ACTION        = "io.github.hyperisland.ACTION_SHOW_ISLAND"
    /** 广播 Action，用于跨进程请求取消代理通知。*/
    const val ACTION_CANCEL = "io.github.hyperisland.ACTION_CANCEL_ISLAND"
    /** 取消广播携带的通知 ID extra 键。*/
    const val EXTRA_NOTIF_ID = "notif_id"

    /**
     * 广播发送方所需权限（signature 级）。
     * 只有与 HyperIsland 使用相同签名的应用才能获得此权限并触发 Receiver。
     */
    const val PERM     = "io.github.hyperisland.SEND_ISLAND"

    /** 默认通知 ID。固定 ID 保证同一时刻只有一条岛通知存在。*/
    const val NOTIF_ID = 0x48594944  // "HYID"

    const val CHANNEL_ID            = "hyperisland_dispatcher"
    private const val CHANNEL_NAME = "HyperIsland 超级岛"
    private const val TAG          = "HyperIsland[Dispatcher]"

    @Volatile private var registered = false

    /** 记录已发出的代理通知 ID，用于判断首次发送（触发岛动画）还是后续更新。*/
    private val postedIds = androidx.collection.ArraySet<Int>()

    // ── 广播接收器（运行在 SystemUI 进程）────────────────────────────────────

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val appCtx = context.applicationContext ?: context
            when (intent.action) {
                ACTION -> {
                    try {
                        val request = IslandRequest.fromIntent(intent)
                        Log.d("HyperIsland", "$TAG onReceive: title=${request.title}")
                        post(appCtx, request)
                    } catch (e: Exception) {
                        Log.d("HyperIsland", "$TAG onReceive error: ${e.message}")
                    }
                }
                ACTION_CANCEL -> {
                    try {
                        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, NOTIF_ID)
                        cancel(appCtx, notifId)
                    } catch (e: Exception) {
                        Log.d("HyperIsland", "$TAG onReceive cancel error: ${e.message}")
                    }
                }
            }
        }
    }

    // ── 初始化 ───────────────────────────────────────────────────────────────

    /**
     * 在 SystemUI 进程中注册广播接收器。由 [IslandDispatcherHook] 在 Application.onCreate
     * 后调用。重复调用安全（幂等）。
     */
    fun register(context: Context) {
        if (registered) return
        val appCtx = context.applicationContext ?: context
        createChannel(appCtx)

        val filter = IntentFilter(ACTION).apply { addAction(ACTION_CANCEL) }
        if (Build.VERSION.SDK_INT >= 33) {
            appCtx.registerReceiver(receiver, filter, PERM, null, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            appCtx.registerReceiver(receiver, filter, PERM, null)
        }
        registered = true
        Log.d("HyperIsland", "$TAG registered in pid=${android.os.Process.myPid()}")
    }

    // ── 公开 API ──────────────────────────────────────────────────────────────

    /**
     * [进程内直接调用]
     * 在 SystemUI 进程内立即发出岛通知。其他运行在 SystemUI 进程内的 Xposed 模块
     * 可直接调用，无需广播，效率更高。
     */
    fun post(context: Context, request: IslandRequest) {
        try {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            createChannel(context)

            val appIcon = resolveIcon(request.icon, context)

            val islandBuilder = HyperIslandNotification.Builder(
                context, "hyper_island_dispatch", request.title
            )

            islandBuilder.addPicture(HyperPicture("key_island_icon", appIcon))
            islandBuilder.addPicture(HyperPicture("key_focus_icon",  appIcon))

            islandBuilder.setIconTextInfo(
                picKey  = "key_focus_icon",
                title   = request.title,
                content = request.content,
            )
            islandBuilder.setIslandFirstFloat(request.firstFloat)
            islandBuilder.setEnableFloat(request.enableFloat)
            islandBuilder.setShowNotification(request.showNotification)
            islandBuilder.setIslandConfig(timeout = request.timeoutSecs)

            // 小岛：仅图标
            islandBuilder.setSmallIsland("key_island_icon")

            // 大岛：左侧图标+标题，右侧内容
            islandBuilder.setBigIslandInfo(
                left = ImageTextInfoLeft(
                    type     = 1,
                    picInfo  = PicInfo(type = 1, pic = "key_island_icon"),
                    textInfo = TextInfo(title = request.title),
                ),
                right = ImageTextInfoRight(
                    type     = 2,
                    textInfo = TextInfo(title = request.content, narrowFont = true),
                ),
            )

            // 文字按钮（最多 2 个），与 NotificationIslandNotification.inject() 保持一致
            val effectiveActions = request.actions.take(2)
            if (effectiveActions.isNotEmpty()) {
                val hyperActions = effectiveActions.mapIndexed { index, action ->
                    HyperAction(
                        key              = "action_dispatcher_$index",
                        title            = action.title?.toString() ?: "",
                        pendingIntent    = action.actionIntent,
                        actionIntentType = 2,
                    )
                }
                hyperActions.forEach { islandBuilder.addHiddenAction(it) }
                islandBuilder.setTextButtons(*hyperActions.toTypedArray())
            }

            val resourceBundle = islandBuilder.buildResourceBundle()

            val notif = Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(request.title)
                .setContentText(request.content)
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
                .let { injectIslandAppearance(it, request.highlightColor, request.dismissIsland) }
            notif.extras.putString("miui.focus.param", jsonParam)
            if (request.showNotification) {
                notif.extras.putBoolean("hyperisland_focus_proxy", true)
            }
            val shouldPreserveStatusBarSmallIcon =
                request.showNotification && request.preserveStatusBarSmallIcon
            if (shouldPreserveStatusBarSmallIcon) {
                notif.extras.putBoolean("hyperisland_preserve_status_bar_small_icon", true)
                FocusNotifStatusBarIconHook.markDirectProxyPosted(request.timeoutSecs)
            }
            XposedBridge.log(
                "$TAG preserve marker written=$shouldPreserveStatusBarSmallIcon: title=${request.title} | notifId=${request.notifId} | showNotification=${request.showNotification}"
            )

            val isFirstPost = !postedIds.contains(request.notifId)
            if (isFirstPost) {
                // 首次：cancel + notify，触发岛动画
                nm.cancel(request.notifId)
                nm.notify(request.notifId, notif)
                postedIds.add(request.notifId)
            } else {
                // 后续更新：直接 notify，HyperOS 视为更新而非新通知
                nm.notify(request.notifId, notif)
            }

            XposedBridge.log(
                "$TAG posted(first=$isFirstPost): ${request.title} | ${request.content}" +
                " | highlight=${request.highlightColor} | dismiss=${request.dismissIsland}"
            )
        } catch (e: Exception) {
            Log.d("HyperIsland", "$TAG post error: ${e.message}")
        }
    }

    /**
     * [跨进程调用]
     * 从任意进程向 SystemUI 进程发送岛展示请求。
     * 安全性由 Receiver 注册时的 broadcastPermission 保证，调用方无需额外操作。
     */
    fun sendBroadcast(context: Context, request: IslandRequest) {
        val intent = Intent(ACTION).apply {
            putExtras(request.toBundle())
        }
        context.sendBroadcast(intent)
    }

    /**
     * [进程内直接调用]
     * 原始通知被取消时调用，同步取消代理通知并清除首次发送状态。
     * 下次再为同一 [notifId] 调用 [post] 时将重新触发岛动画。
     */
    fun cancel(context: Context, notifId: Int) {
        try {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            nm.cancel(notifId)
            postedIds.remove(notifId)
            Log.d("HyperIsland", "$TAG cancel: notifId=$notifId")
        } catch (e: Exception) {
            Log.d("HyperIsland", "$TAG cancel error: ${e.message}")
        }
    }

    // ── 内部工具 ──────────────────────────────────────────────────────────────

    /**
     * 优先使用 [IslandRequest.icon]；为 null 时从 HyperIsland 应用获取启动图标并做圆角处理；
     * 失败时降级为系统默认图标。
     */
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

    /**
     * 将 [highlightColor] / [dismissIsland] 注入到 param_v2.param_island 子对象。
     * 这两个字段属于岛自身的外观/行为配置，在协议里位于 param_island 层级
     */
    private fun injectIslandAppearance(
        jsonParam: String,
        highlightColor: String?,
        dismissIsland: Boolean,
    ): String {
        if (highlightColor == null && !dismissIsland) return jsonParam
        return try {
            val json       = org.json.JSONObject(jsonParam)
            val pv2        = json.optJSONObject("param_v2") ?: return jsonParam
            val paramIsland = pv2.optJSONObject("param_island") ?: org.json.JSONObject()
            highlightColor?.let { paramIsland.put("highlightColor", it) }
            if (dismissIsland) paramIsland.put("dismissIsland", true)
            pv2.put("param_island", paramIsland)
            json.toString()
        } catch (_: Exception) { jsonParam }
    }

    /** 修正新库输出的 textButton JSON，将 "actionIntent" 字段替换为协议所需的 "action"。*/
    private fun fixTextButtonJson(jsonParam: String): String {
        return try {
            val json = org.json.JSONObject(jsonParam)
            val pv2  = json.optJSONObject("param_v2") ?: return jsonParam
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
        } catch (_: Exception) { jsonParam }
    }

    /** 将 buildResourceBundle() 里嵌套的 "miui.focus.actions" 展开到 extras 顶层。*/
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

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                setShowBadge(false)
            }
        )
    }
}
