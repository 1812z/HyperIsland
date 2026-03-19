package io.github.hyperisland.xposed.liveisland

import android.graphics.drawable.Icon

/**
 * 活跃岛状态协调器。
 *
 * 在 [LiveIslandSender] 发送通知前存储展示内容，由 [LiveIslandViewHook] 在 SystemUI
 * 创建岛视图时读取，从而将滚动/动画逻辑注入到正确的那次岛展示中。
 *
 * ## 使用流程
 * 1. [LiveIslandSender.send] → [store] 存储内容
 * 2. IslandDispatcher.post → 通知触发 SystemUI 创建岛视图
 * 3. [LiveIslandViewHook] → [consume] 取出内容并注入自定义视图
 *
 * 状态有效期由 [EXPIRY_MS] 控制，超时后 [consume] 自动返回 null，避免旧状态污染
 * 后续不相关的岛展示。
 */
object LiveIslandState {

    /** 待展示的岛内容 */
    data class DisplayContent(
        val title: String,
        val subtitle: String,
        val icon: Icon?,
        val storedAtMs: Long = System.currentTimeMillis(),
    )

    /** 状态有效期（毫秒）：超过此时间未被消费则视为过期 */
    private const val EXPIRY_MS = 3_000L

    @Volatile private var pending: DisplayContent? = null

    /** 存储待展示内容。由 [LiveIslandSender] 在 IslandDispatcher.post() 前调用。*/
    fun store(content: DisplayContent) {
        pending = content
    }

    /**
     * 原子性地读取并清除待展示内容。
     * 内容已过期（超过 [EXPIRY_MS]）时返回 null。
     * 由 [LiveIslandViewHook] 在岛视图创建后调用。
     */
    fun consume(): DisplayContent? {
        val c = pending ?: return null
        pending = null
        val age = System.currentTimeMillis() - c.storedAtMs
        return if (age <= EXPIRY_MS) c else null
    }

    /** 查看当前状态而不消费。*/
    fun peek(): DisplayContent? = pending

    /** 清除状态（例如通知取消时）。*/
    fun clear() {
        pending = null
    }
}
