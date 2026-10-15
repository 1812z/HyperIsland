package io.github.hyperisland.xposed.hook

import android.graphics.Color
import android.text.Spanned
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

/**
 * 超级岛 / 焦点通知 / 媒体通知 共用的文本颜色策略。
 *
 * 只处理“近似灰度”的文本：三通道最大差值 ≤ [GRAY_TOLERANCE] 即视为灰度（如
 * rgb(196,202,209)），真正有饱和度的彩色原样保留。灰度按模式做平均亮度反转，
 * 输出纯灰（白↔黑、浅灰↔深灰）。
 */
internal object TextColorPolicy {

    const val MODE_DEFAULT = "default"
    const val MODE_BLACK = "black"
    const val MODE_FOLLOW_BACKGROUND = "follow_background"
    const val MODE_INVERT_BACKGROUND = "invert_background"
    const val MODE_FOLLOW_STATUS_BAR = "follow_status_bar"
    const val MODE_INVERT_STATUS_BAR = "invert_status_bar"

    private const val GRAY_TOLERANCE = 32

    private const val TIMER_TEXT_EFFECT_VIEW_CLASS = "miuix.colorful.texteffect.TimerTextEffectView"
    private const val HYPER_CHRONOMETER_CLASS = "miuix.colorful.texteffect.HyperChronometer"
    private const val TIMER_TEXT_EFFECT_SPAN_CLASS = "miuix.colorful.texteffect.TimerTextEffectSpan"
    private const val HYPER_CHRONOMETER_SPAN_CLASS =
        "miuix.colorful.texteffect.HyperChronometerEffectSpan"

    private val effectAccessors = Collections.synchronizedMap(
        WeakHashMap<Class<*>, EffectAccessors>()
    )

    /** 各视图的基准原始色，跨 Hook 共享，保证同一视图被多个 Hook 处理时结果一致。 */
    private val originals = Collections.synchronizedMap(WeakHashMap<TextView, Int>())

    fun originalOf(textView: TextView): Int? = synchronized(originals) { originals[textView] }

    fun isConfigured(mode: String): Boolean = mode != MODE_DEFAULT

    fun isNearGray(color: Int): Boolean {
        if (Color.alpha(color) == 0) return false
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return maxOf(red, green, blue) - minOf(red, green, blue) <= GRAY_TOLERANCE
    }

    /** 平均亮度反转，输出纯灰（保留 alpha）。 */
    fun invert(color: Int): Int {
        val level = (Color.red(color) + Color.green(color) + Color.blue(color)) / 3
        val inverted = 255 - level
        return Color.argb(Color.alpha(color), inverted, inverted, inverted)
    }

    /**
     * 依据模式计算目标色。
     *
     * @return 目标色；`default` 或非灰度返回 null（保持不动）。目标色可能与原色相同
     *         （表示该模式下维持原亮度）。
     */
    fun map(
        color: Int,
        mode: String,
        regionDark: Boolean,
        statusBarTintBlack: Boolean,
    ): Int? {
        if (mode == MODE_DEFAULT || !isNearGray(color)) return null
        return when (mode) {
            // 黑色：静态反转一次，不跟随状态栏，也不是直接涂黑。
            MODE_BLACK -> invert(color)
            // 跟随岛背景：深色背景保持亮字，浅色背景反转为暗字。
            MODE_FOLLOW_BACKGROUND -> if (regionDark) color else invert(color)
            // 反跟随岛背景：与跟随相反。
            MODE_INVERT_BACKGROUND -> if (regionDark) invert(color) else color
            // 跟随状态栏：状态栏黑字（浅底）时反转，白字（深底）时保持。
            MODE_FOLLOW_STATUS_BAR -> if (statusBarTintBlack) invert(color) else color
            // 反跟随状态栏：与跟随相反。
            MODE_INVERT_STATUS_BAR -> if (statusBarTintBlack) color else invert(color)
            else -> null
        }
    }

    /** 读取应作为变换基准的原始色：文字效果视图以 span 颜色为准，否则取当前文字色。 */
    fun readSource(textView: TextView): Int {
        if (isEffectView(textView)) {
            readSpanColor(textView)?.let { return it }
        }
        return textView.currentTextColor
    }

    /** 应用颜色：文字效果视图同时刷新其 span 颜色。 */
    fun apply(textView: TextView, color: Int) {
        if (isEffectView(textView)) {
            applyToEffect(textView, color)
        } else if (textView.currentTextColor != color) {
            textView.setTextColor(color)
        }
    }

    /**
     * 解析基准原始色并映射应用；彩色返回时不做任何处理。
     *
     * 幂等：若当前色恰好等于 `map(已记录原始色)`，说明是上次写回的结果，沿用原始色，
     * 因此同一视图被多个 Hook 依次处理也不会来回反转。
     */
    fun applyForTextView(
        textView: TextView,
        mode: String,
        regionDark: Boolean,
        statusBarTintBlack: Boolean,
    ) {
        val original = resolveSource(textView, mode, regionDark, statusBarTintBlack) ?: return
        retarget(textView, original, mode, regionDark, statusBarTintBlack)
    }

    /** 以已知原始色重新映射并应用（用于配置变更 / 状态栏明暗变化重刷）。 */
    fun retarget(
        textView: TextView,
        original: Int,
        mode: String,
        regionDark: Boolean,
        statusBarTintBlack: Boolean,
    ) {
        val target = map(original, mode, regionDark, statusBarTintBlack) ?: return
        apply(textView, target)
    }

    private fun resolveSource(
        textView: TextView,
        mode: String,
        regionDark: Boolean,
        statusBarTintBlack: Boolean,
    ): Int? {
        val current = readSource(textView)
        val stored = synchronized(originals) { originals[textView] }
        if (!isNearGray(current)) {
            synchronized(originals) { originals[textView] = current }
            return null
        }
        if (stored != null && isNearGray(stored) &&
            map(stored, mode, regionDark, statusBarTintBlack) == current
        ) {
            return stored
        }
        synchronized(originals) { originals[textView] = current }
        return current
    }

    fun findHyperChronometer(view: View): TextView? =
        findTextViewByClassFamily(view, HYPER_CHRONOMETER_CLASS)

    private fun isEffectView(view: TextView): Boolean =
        matchesClassFamily(view, TIMER_TEXT_EFFECT_VIEW_CLASS) ||
            matchesClassFamily(view, HYPER_CHRONOMETER_CLASS)

    private fun findTextViewByClassFamily(view: View, className: String): TextView? {
        if (view is TextView && matchesClassFamily(view, className)) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findTextViewByClassFamily(view.getChildAt(index), className)?.let { return it }
            }
        }
        return null
    }

    private fun matchesClassFamily(view: View, className: String): Boolean {
        var current: Class<*>? = view.javaClass
        while (current != null) {
            if (current.name == className) return true
            current = current.superclass
        }
        return false
    }

    private fun applyToEffect(textView: TextView, color: Int) {
        if (textView.currentTextColor != color) textView.setTextColor(color)
        val accessors = resolveEffectAccessors(textView) ?: return
        val text = textView.text as? Spanned ?: return
        val spans: Array<*> = runCatching {
            text.getSpans(0, text.length, accessors.spanClass) as Array<*>
        }.getOrElse { emptyArray<Any>() }
        if (spans.isEmpty()) return
        spans.forEach { span ->
            runCatching {
                val start = text.getSpanStart(span)
                val end = text.getSpanEnd(span)
                val spanText = if (start in 0..end && end <= text.length) {
                    text.subSequence(start, end)
                } else {
                    text
                }
                accessors.setOldAppearance.invoke(span, spanText, color)
                // 同步“新文本”外观与前景色：倒计时数字更新时，进入动画用 span 的
                // mNewTextColor/mForegroundColor 绘制新数字；只改旧外观会让更新的数字恢复原色。
                accessors.setNewAppearance?.invoke(span, color)
                accessors.foregroundField?.setInt(span, color)
            }
        }
        textView.invalidate()
    }

    private fun readSpanColor(textView: TextView): Int? {
        val accessors = resolveEffectAccessors(textView) ?: return null
        val text = textView.text as? Spanned ?: return null
        val spans: Array<*> = runCatching {
            text.getSpans(0, text.length, accessors.spanClass) as Array<*>
        }.getOrElse { emptyArray<Any>() }
        val span = spans.firstOrNull() ?: return null
        return runCatching {
            (accessors.newColorField?.get(span) as? Int)
                ?: (accessors.oldColorField?.get(span) as? Int)
                ?: accessors.foregroundField?.getInt(span)
        }.getOrNull()
    }

    private fun resolveEffectAccessors(textView: TextView): EffectAccessors? {
        val spanClassName = when {
            matchesClassFamily(textView, TIMER_TEXT_EFFECT_VIEW_CLASS) -> TIMER_TEXT_EFFECT_SPAN_CLASS
            matchesClassFamily(textView, HYPER_CHRONOMETER_CLASS) -> HYPER_CHRONOMETER_SPAN_CLASS
            else -> return null
        }
        val classLoader = textView.javaClass.classLoader ?: ClassLoader.getSystemClassLoader()
        val spanClass = runCatching { classLoader.loadClass(spanClassName) }.getOrNull() ?: return null
        synchronized(effectAccessors) { effectAccessors[spanClass] }?.let { return it }
        val setOldAppearance = spanClass.methods.firstOrNull { method ->
            method.name == "setOldTextAppearance" && method.parameterTypes.size == 2
        } ?: return null
        val setNewAppearance = spanClass.methods.firstOrNull { method ->
            method.name == "setNewTextAppearance" && method.parameterTypes.size == 1
        }
        val newColorField = runCatching {
            spanClass.getDeclaredField("mNewTextColor").apply { isAccessible = true }
        }.getOrNull()
        val oldColorField = runCatching {
            spanClass.getDeclaredField("mOldTextColor").apply { isAccessible = true }
        }.getOrNull()
        val foregroundField = runCatching {
            spanClass.getDeclaredField("mForegroundColor").apply { isAccessible = true }
        }.getOrNull()
        return EffectAccessors(
            spanClass,
            setOldAppearance,
            setNewAppearance,
            newColorField,
            oldColorField,
            foregroundField,
        ).also {
            synchronized(effectAccessors) { effectAccessors[spanClass] = it }
        }
    }

    private data class EffectAccessors(
        val spanClass: Class<*>,
        val setOldAppearance: Method,
        val setNewAppearance: Method?,
        val newColorField: Field?,
        val oldColorField: Field?,
        val foregroundField: Field?,
    )
}