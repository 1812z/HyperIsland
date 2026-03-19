package io.github.hyperisland.xposed.liveisland

import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/**
 * 基于 [Choreographer.FrameCallback] 的文本平滑滚动引擎。
 *
 * 通过逐帧推进 [TextView.scrollTo] 实现像素级精准滚动，避免系统 marquee 的抖动问题。
 * 滚动流程：等待 [INITIAL_DELAY_MS] → 匀速滚动 → 到达末尾暂停 [END_PAUSE_MS] → 归零重复。
 *
 * ## 使用
 * ```kotlin
 * val engine = ScrollingTextEngine(myTextView)
 * engine.start()         // 启动滚动（内部已用 post 保证布局完成）
 * engine.stop()          // 停止并归位
 * engine.updateText("新内容")  // 更新文本并重新开始滚动
 * ```
 *
 * 通过 [isEnabled] 变量控制整体开关，默认开启。
 */
class ScrollingTextEngine(private val targetView: TextView) {

    // ── 开关控制 ─────────────────────────────────────────────────────────────────

    /** 是否启用滚动，默认 true。可在运行时切换，关闭时文本静止显示。*/
    var isEnabled: Boolean = true

    // ── 滚动参数 ─────────────────────────────────────────────────────────────────

    companion object {
        /** 滚动速度，单位 px/s */
        private const val SCROLL_SPEED_PX_PER_SEC = 80f

        /** 首次滚动前的等待时间（ms），让用户有时间读取文本开头 */
        private const val INITIAL_DELAY_MS = 1_200L

        /** 滚动到末尾后暂停时间（ms），再重置回头 */
        private const val END_PAUSE_MS = 900L
    }

    // ── 内部状态 ─────────────────────────────────────────────────────────────────

    private val mainHandler = Handler(Looper.getMainLooper())

    /** 当前已滚动的像素偏移量 */
    private var currentOffsetPx = 0f

    /** 是否处于滚动中 */
    @Volatile private var isScrolling = false

    /** 上一帧的纳秒时间戳，用于计算帧间时长 */
    private var lastFrameNanos = 0L

    /** 等待延迟的截止纳秒，0 表示已过延迟 */
    private var waitUntilNanos = 0L

    /** 末尾暂停中（已提交 postDelayed 重置逻辑） */
    private var pausePending = false

    // ── 帧回调 ────────────────────────────────────────────────────────────────────

    private val frameCallback = Choreographer.FrameCallback { frameNanos ->
        if (!isScrolling || !isEnabled) return@FrameCallback

        // 初始等待
        if (frameNanos < waitUntilNanos) {
            Choreographer.getInstance().postFrameCallback(frameCallback)
            return@FrameCallback
        }

        // 末尾暂停中，暂停逻辑由 postDelayed 处理
        if (pausePending) return@FrameCallback

        val textWidth  = targetView.paint.measureText(targetView.text.toString())
        val viewWidth  = targetView.width.toFloat()
        val maxOffset  = textWidth - viewWidth

        if (maxOffset <= 0f) {
            stopInternal()
            return@FrameCallback
        }

        val elapsedSec = if (lastFrameNanos == 0L) 0f
                         else (frameNanos - lastFrameNanos) / 1_000_000_000f
        lastFrameNanos = frameNanos

        currentOffsetPx += SCROLL_SPEED_PX_PER_SEC * elapsedSec

        if (currentOffsetPx >= maxOffset) {
            // 到达末尾
            currentOffsetPx = maxOffset
            targetView.scrollTo(currentOffsetPx.toInt(), 0)
            pausePending = true
            // 暂停后归零重新开始
            mainHandler.postDelayed({
                currentOffsetPx = 0f
                pausePending     = false
                lastFrameNanos   = 0L
                waitUntilNanos   = System.nanoTime() + INITIAL_DELAY_MS * 1_000_000L
                targetView.scrollTo(0, 0)
                if (isScrolling && isEnabled) {
                    Choreographer.getInstance().postFrameCallback(frameCallback)
                }
            }, END_PAUSE_MS)
            return@FrameCallback
        }

        targetView.scrollTo(currentOffsetPx.toInt(), 0)
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    // ── 公开 API ──────────────────────────────────────────────────────────────────

    /**
     * 启动滚动。内部使用 [View.post] 保证在布局完成后测量宽度，
     * 若文本宽度未超出视图则不滚动。
     */
    fun start() {
        if (!isEnabled) return
        targetView.post {
            prepareTextView()
            val textWidth = targetView.paint.measureText(targetView.text.toString())
            val viewWidth = targetView.width.toFloat()
            if (textWidth <= viewWidth) return@post   // 无需滚动

            currentOffsetPx = 0f
            lastFrameNanos  = 0L
            pausePending    = false
            waitUntilNanos  = System.nanoTime() + INITIAL_DELAY_MS * 1_000_000L
            isScrolling     = true
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
    }

    /**
     * 停止滚动并将文本归位。
     * 可从任意线程调用，内部会自动切换到主线程。
     */
    fun stop() {
        mainHandler.post { stopInternal() }
    }

    /**
     * 更新文本内容并重新开始滚动（配合 [ContentSwitchAnimator] 使用）。
     * 若 [isEnabled] 为 false，仅更新文本不滚动。
     */
    fun updateText(newText: String) {
        stopInternal()
        targetView.text = newText
        if (isEnabled) {
            targetView.post { start() }
        }
    }

    // ── 内部工具 ──────────────────────────────────────────────────────────────────

    private fun stopInternal() {
        isScrolling = false
        pausePending = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        targetView.scrollTo(0, 0)
        currentOffsetPx = 0f
    }

    /**
     * 为 [targetView] 设置滚动所需的 TextView 属性，并禁用父链裁剪以允许文本溢出。
     */
    private fun prepareTextView() {
        targetView.isSingleLine = true
        targetView.ellipsize   = null   // 移除截断省略，由我们控制溢出

        // 禁用父容器的裁剪，让 scrollTo 产生的内容超出自身边界可见
        var ancestor: Any? = targetView.parent
        while (ancestor is ViewGroup) {
            ancestor.clipChildren  = false
            ancestor.clipToPadding = false
            ancestor = ancestor.parent
        }
    }
}
