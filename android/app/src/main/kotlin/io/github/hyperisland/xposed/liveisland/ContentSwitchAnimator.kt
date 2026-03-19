package io.github.hyperisland.xposed.liveisland

import android.view.animation.DecelerateInterpolator
import android.widget.TextView

/**
 * 新消息切换动画工具。
 *
 * 当岛内容更新（新通知到来）时，以动画方式替换文本，让切换更自然流畅。
 * 支持三种动画模式，可通过 [defaultMode] 配置，默认使用 [Mode.SLIDE_UP]。
 *
 * 通过 [isEnabled] 变量控制整体开关，默认开启。
 *
 * ## 使用
 * ```kotlin
 * ContentSwitchAnimator.animateChange(
 *     view        = myTextView,
 *     newText     = "新内容",
 *     scrollEngine = myScrollEngine,  // 可选，动画期间暂停/恢复滚动
 * )
 * ```
 */
object ContentSwitchAnimator {

    // ── 动画模式定义 ───────────────────────────────────────────────────────────

    enum class Mode {
        /** 向上滑出 + 从下方滑入（仿通知栏消息切换风格） */
        SLIDE_UP,
        /** 向左滑出 + 从右方滑入 */
        SLIDE_HORIZONTAL,
        /** 淡出 + 淡入（最轻量） */
        FADE,
    }

    // ── 开关控制 ──────────────────────────────────────────────────────────────

    /** 是否启用切换动画，默认 true。关闭时文本直接替换，无过渡效果。*/
    var isEnabled: Boolean = true

    /** 默认动画模式 */
    var defaultMode: Mode = Mode.SLIDE_UP

    // ── 动画参数 ──────────────────────────────────────────────────────────────

    private const val EXIT_DURATION_MS  = 200L
    private const val ENTER_DURATION_MS = 240L

    // ── 公开 API ──────────────────────────────────────────────────────────────

    /**
     * 以动画方式将 [view] 的文本切换为 [newText]。
     *
     * @param view        要切换内容的 TextView
     * @param newText     新文本内容
     * @param mode        动画模式，默认取 [defaultMode]
     * @param scrollEngine 若不为 null，动画期间会停止滚动，动画完成后重新启动
     * @param onComplete  动画（含入场）完成后的回调
     */
    fun animateChange(
        view: TextView,
        newText: String,
        mode: Mode = defaultMode,
        scrollEngine: ScrollingTextEngine? = null,
        onComplete: () -> Unit = {},
    ) {
        // 内容未变化，不触发动画
        if (view.text.toString() == newText) {
            onComplete()
            return
        }

        if (!isEnabled) {
            // 动画关闭：直接切换文本，重启滚动
            scrollEngine?.stop()
            view.text = newText
            scrollEngine?.start()
            onComplete()
            return
        }

        // 停止正在进行的滚动，避免动画期间出现位置跳跃
        scrollEngine?.stop()

        // ── 退出动画 ──────────────────────────────────────────────────────────
        val exitAnim = view.animate()
            .setDuration(EXIT_DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .alpha(0f)
            .apply {
                when (mode) {
                    Mode.SLIDE_UP         -> translationY(-view.height * 0.35f)
                    Mode.SLIDE_HORIZONTAL -> translationX(-view.width * 0.25f)
                    Mode.FADE             -> { /* 仅 alpha */ }
                }
            }

        exitAnim.withEndAction {
            // ── 切换内容 ──────────────────────────────────────────────────────
            view.text = newText
            view.scrollTo(0, 0)  // 归位，避免旧滚动偏移残留

            // 设置入场起始位置
            when (mode) {
                Mode.SLIDE_UP         -> view.translationY = view.height * 0.35f
                Mode.SLIDE_HORIZONTAL -> view.translationX = view.width * 0.25f
                Mode.FADE             -> { /* 直接在原位淡入 */ }
            }

            // ── 入场动画 ──────────────────────────────────────────────────────
            view.animate()
                .setDuration(ENTER_DURATION_MS)
                .setInterpolator(DecelerateInterpolator())
                .alpha(1f)
                .translationX(0f)
                .translationY(0f)
                .withEndAction {
                    scrollEngine?.start()
                    onComplete()
                }
                .start()
        }.start()
    }
}
