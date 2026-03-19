package io.github.hyperisland.xposed.liveisland

import android.content.Context
import android.graphics.drawable.Icon
import de.robv.android.xposed.XposedBridge
import io.github.hyperisland.xposed.IslandDispatcher
import io.github.hyperisland.xposed.IslandRequest
import io.github.hyperisland.xposed.NotifData
import io.github.hyperisland.xposed.toRounded

/**
 * 活跃岛第三方发送器：滚动文本 + 切换动画。
 *
 * ## 与现有两种发送方式的区别
 * - **方式一**（GenericProgressHook）：修改原始通知的 extras，让系统渲染岛视图
 * - **方式二**（IslandDispatcher / injectViaDispatcher）：以 SystemUI 身份代发通知，
 *   展示基础静态文本岛
 * - **方式三（本类）**：在方式二的基础上，通过 [LiveIslandViewHook] 在视图层注入
 *   [ScrollingTextEngine] 与 [ContentSwitchAnimator]，实现滚动文本与切换动画
 *
 * ## 调用方式
 * ```kotlin
 * LiveIslandSender.send(context, notifData)
 * ```
 *
 * ## 设计约束
 * - [LiveIslandState.store] 必须在 [IslandDispatcher.post] 前调用，
 *   因为通知发出后 SystemUI 会异步创建视图，此时 Hook 才去读取状态
 * - 本类只在 SystemUI 进程内运行（Xposed 注入环境）
 */
object LiveIslandSender {

    private const val TAG = "HyperIsland[LiveIslandSender]"

    // ── 公开 API ──────────────────────────────────────────────────────────────

    /**
     * 发送活跃岛通知（方式三）。
     *
     * 步骤：
     * 1. 解析图标和展示文本
     * 2. 将内容存入 [LiveIslandState]，供 [LiveIslandViewHook] 在视图创建时取用
     * 3. 通过 [IslandDispatcher.post] 触发超级岛显示
     *
     * @param context 运行在 SystemUI 进程内的 Context
     * @param data    来自 GenericProgressHook 的通知结构化数据
     */
    fun send(context: Context, data: NotifData) {
        try {
            val icon = resolveIcon(context, data)

            // 优先用副标题，副标题为空时用主标题（与 injectViaDispatcher 保持一致）
            val displaySubtitle = data.subtitle.ifEmpty { data.title }

            // ── 步骤 1：先存状态，再发通知（顺序不可颠倒）──────────────────────
            LiveIslandState.store(
                LiveIslandState.DisplayContent(
                    title    = data.title,
                    subtitle = displaySubtitle,
                    icon     = icon,
                )
            )

            // ── 步骤 2：触发超级岛展示 ───────────────────────────────────────────
            val resolvedFirstFloat  = data.firstFloat      == "on"
            val resolvedEnableFloat = data.enableFloatMode == "on"

            IslandDispatcher.post(
                context,
                IslandRequest(
                    title            = data.title,
                    content          = displaySubtitle,
                    icon             = icon,
                    timeoutSecs      = data.islandTimeout,
                    firstFloat       = resolvedFirstFloat,
                    enableFloat      = resolvedEnableFloat,
                    showNotification = false,   // focusNotif == "off"：不显示焦点通知
                    contentIntent    = data.contentIntent,
                    isOngoing        = data.isOngoing,
                    actions          = data.actions.take(2),
                ),
            )

            XposedBridge.log(
                "$TAG send — '${data.title}' | iconMode=${data.iconMode}" +
                " | timeout=${data.islandTimeout} | scroll=${ScrollingTextEngine::isEnabled.name}" +
                " | anim=${ContentSwitchAnimator.isEnabled}"
            )
        } catch (e: Exception) {
            XposedBridge.log("$TAG send error: ${e.message}")
            // 发送失败时清理状态，避免污染下次展示
            LiveIslandState.clear()
        }
    }

    // ── 内部工具 ──────────────────────────────────────────────────────────────

    /**
     * 根据 [NotifData.iconMode] 解析最终使用的岛图标，并做圆角处理。
     * 与 [io.github.hyperisland.xposed.templates.NotificationIslandNotification] 的图标
     * 逻辑保持一致。
     */
    private fun resolveIcon(context: Context, data: NotifData): Icon {
        val fallback = Icon.createWithResource(context, android.R.drawable.ic_dialog_info)
        return when (data.iconMode) {
            "notif_small" -> data.notifIcon ?: fallback
            "notif_large" -> data.largeIcon ?: data.notifIcon ?: fallback
            "app_icon"    -> data.appIconRaw ?: fallback
            else          -> data.largeIcon ?: data.notifIcon ?: fallback  // auto
        }.toRounded(context)
    }
}
