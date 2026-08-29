package io.github.hyperisland.xposed.hook

import android.graphics.Color
import android.text.Spanned
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

/** Keeps island and expanded-focus timer text in sync with their configured text color. */
object TimerTextColorHook : BaseHook() {

    private const val TAG = "HyperIsland[TimerTextColor]"

    private val hookedClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    )
    private val trackedTimers = Collections.synchronizedMap(
        WeakHashMap<TextView, TimerScope>()
    )
    private val resolvedTextEffectViews = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<TextView, Boolean>())
    )
    private val textEffectAccessors = Collections.synchronizedMap(
        WeakHashMap<TextView, TextEffectAccessors>()
    )

    @Volatile private var islandMode = MODE_DEFAULT
    @Volatile private var focusMode = MODE_DEFAULT
    @Volatile private var isRegionDark = true

    private val statusBarTintListener: (Int) -> Unit = {
        if (isStatusBarMode(islandMode) || isStatusBarMode(focusMode)) {
            reapplyTrackedTimers()
        }
    }

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        refreshModes()
        IslandTextColorHook.addStatusBarTintListener(statusBarTintListener)
        hookClasses(module, param.defaultClassLoader)
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { classLoader ->
            hookClasses(module, classLoader)
        }
    }

    override fun onConfigChanged() {
        refreshModes()
        reapplyTrackedTimers()
    }

    private fun hookClasses(module: XposedModule, classLoader: ClassLoader) {
        hookClass(
            module,
            classLoader,
            BASE_ISLAND_HOLDER_CLASS,
            ::hookIslandTimerText,
        )
        hookClass(
            module,
            classLoader,
            SAME_WIDTH_DIGIT_HOLDER_CLASS,
            ::hookIslandChronometer,
        )
        hookClass(
            module,
            classLoader,
            FOCUS_HOLDER_CLASS,
            ::hookFocusTimers,
        )
        hookClass(
            module,
            classLoader,
            TIMER_TEXT_EFFECT_VIEW_CLASS,
            ::hookTextEffectUpdates,
        )
        hookClass(
            module,
            classLoader,
            HYPER_CHRONOMETER_CLASS,
            ::hookTextEffectUpdates,
        )
        hookClass(
            module,
            classLoader,
            DYNAMIC_ISLAND_CONTENT_VIEW_CLASS,
            ::hookRegionDarkMode,
        )
    }

    private fun hookClass(
        module: XposedModule,
        classLoader: ClassLoader,
        className: String,
        hook: (XposedModule, Class<*>) -> Unit,
    ) {
        runCatching {
            val clazz = classLoader.loadClass(className)
            if (hookedClasses.add(clazz)) hook(module, clazz)
        }.onFailure { error ->
            if (error !is ClassNotFoundException) {
                logError(module, "failed to hook $className: ${error.message}")
            }
        }
    }

    /** Registers TimerTextEffectView instances while preserving explicit template highlights. */
    private fun hookIslandTimerText(module: XposedModule, holderClass: Class<*>) {
        holderClass.declaredMethods
            .filter { method ->
                (method.name == "setTitleHighlightColor" ||
                    method.name == "setContentHighlightColor") &&
                    method.parameterTypes.size == 4 &&
                    TextView::class.java.isAssignableFrom(method.parameterTypes[2])
            }
            .forEach { method ->
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    val timerView = chain.args.getOrNull(2) as? TextView
                    if (timerView != null) {
                        if (usesHighlightColor(chain.args)) {
                            untrackTimer(timerView)
                        } else {
                            trackTimer(timerView, TimerScope.ISLAND)
                            applyTrackedTimer(timerView)
                        }
                    }
                    result
                }
                log(module, "hooked ${holderClass.name}#${method.name}")
            }
    }

    /** Registers the actual same-width timer, which is a HyperChronometer. */
    private fun hookIslandChronometer(module: XposedModule, holderClass: Class<*>) {
        val chronometerField = holderClass.getDeclaredField("sameWidthDigit").apply {
            isAccessible = true
        }
        holderClass.declaredMethods
            .filter { method -> method.name == "bind" && method.parameterTypes.size == 2 }
            .forEach { method ->
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    runCatching {
                        val chronometer = chronometerField.get(chain.thisObject) as? TextView
                        if (chronometer != null) {
                            if (chronometer.visibility == View.VISIBLE) {
                                trackTimer(chronometer, TimerScope.ISLAND)
                                applyTrackedTimer(chronometer)
                            } else {
                                untrackTimer(chronometer)
                            }
                        }
                    }.onFailure { error ->
                        logError(module, "failed to update island chronometer: ${error.message}")
                    }
                    result
                }
                log(module, "hooked ${holderClass.name}#${method.name}")
            }
    }

    /**
     * Focus holders expose both real and fake dynamic-island instances through marker
     * methods. Register their TimerTextEffectViews before subclasses bind their text,
     * and their HyperChronometer through the common setTimerData entry point.
     */
    private fun hookFocusTimers(module: XposedModule, holderClass: Class<*>) {
        holderClass.declaredMethods
            .filter { method ->
                method.name == "initTextAndColor" && method.parameterTypes.size == 1
            }
            .forEach { method ->
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    val holder = chain.thisObject
                    if (holder != null && isDynamicIslandHolder(holder)) {
                        getHolderView(holder)?.let(::registerFocusTimerTextViews)
                    }
                    result
                }
                log(module, "hooked ${holderClass.name}#${method.name}")
            }

        holderClass.declaredMethods
            .filter { method -> method.name == "setTimerData" && method.parameterTypes.size == 2 }
            .forEach { method ->
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    val holder = chain.thisObject
                    if (holder != null && isDynamicIslandHolder(holder)) {
                        val root = getHolderView(holder)
                        val viewId = chain.args.firstOrNull() as? Int ?: View.NO_ID
                        val chronometer = if (root != null && viewId != View.NO_ID) {
                            root.findViewById<View>(viewId) as? TextView
                        } else {
                            null
                        } ?: root?.let { findTextViewByClassName(it, HYPER_CHRONOMETER_CLASS) }
                        if (chronometer != null) {
                            trackTimer(chronometer, TimerScope.FOCUS)
                            applyTrackedTimer(chronometer)
                        }
                    }
                    result
                }
                log(module, "hooked ${holderClass.name}#${method.name}")
            }
    }

    /** Reapplies color after each component-generated span replacement, not during draw. */
    private fun hookTextEffectUpdates(module: XposedModule, textEffectViewClass: Class<*>) {
        textEffectViewClass.declaredMethods
            .filter { method ->
                method.name == "setText" &&
                    method.parameterTypes.size == 2 &&
                    CharSequence::class.java.isAssignableFrom(method.parameterTypes[0])
            }
            .forEach { method ->
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    (chain.thisObject as? TextView)?.let(::applyTrackedTimer)
                    result
                }
                log(module, "hooked ${textEffectViewClass.name}#${method.name}")
            }
    }

    private fun hookRegionDarkMode(module: XposedModule, contentViewClass: Class<*>) {
        contentViewClass.declaredMethods
            .filter { method ->
                method.name == "updateDarkLightMode" && method.parameterTypes.size >= 3
            }
            .forEach { method ->
                module.hook(method).intercept { chain ->
                    val useDarkText = chain.args.getOrNull(2) as? Boolean
                    val result = chain.proceed()
                    if (useDarkText != null) {
                        val nextRegionDark = !useDarkText
                        if (isRegionDark != nextRegionDark) {
                            isRegionDark = nextRegionDark
                            if (islandMode == MODE_FOLLOW_BACKGROUND ||
                                islandMode == MODE_INVERT_BACKGROUND
                            ) {
                                reapplyTrackedTimers(TimerScope.ISLAND)
                            }
                        }
                    }
                    result
                }
                log(module, "hooked ${contentViewClass.name}#${method.name}")
            }
    }

    private fun registerFocusTimerTextViews(view: View) {
        if (view.javaClass.name == TIMER_TEXT_EFFECT_VIEW_CLASS &&
            view is TextView && resourceEntryName(view) in FOCUS_TEXT_IDS
        ) {
            trackTimer(view, TimerScope.FOCUS)
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                registerFocusTimerTextViews(view.getChildAt(index))
            }
        }
    }

    private fun findTextViewByClassName(view: View, className: String): TextView? {
        if (view.javaClass.name == className && view is TextView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findTextViewByClassName(view.getChildAt(index), className)?.let { return it }
            }
        }
        return null
    }

    private fun resourceEntryName(view: View): String? {
        if (view.id == View.NO_ID) return null
        return runCatching { view.resources.getResourceEntryName(view.id) }.getOrNull()
    }

    private fun getHolderView(holder: Any): View? {
        return runCatching {
            holder.javaClass.methods.firstOrNull { method ->
                method.name == "getView" && method.parameterTypes.isEmpty()
            }?.invoke(holder) as? View
        }.getOrNull()
    }

    private fun isDynamicIslandHolder(holder: Any): Boolean {
        return ISLAND_MARKER_METHODS.any { methodName ->
            runCatching {
                holder.javaClass.methods.firstOrNull { method ->
                    method.name == methodName && method.parameterTypes.isEmpty()
                }?.invoke(holder) as? Boolean
            }.getOrNull() == true
        }
    }

    private fun usesHighlightColor(args: List<*>): Boolean {
        val template = args.getOrNull(0) ?: return false
        val showHighlight = args.getOrNull(1) as? Boolean ?: false
        if (!showHighlight) return false
        val highlightColor = runCatching {
            template.javaClass.methods.firstOrNull { method ->
                method.name == "getHighlightColor" && method.parameterTypes.isEmpty()
            }?.invoke(template) as? String
        }.getOrNull()
        return !highlightColor.isNullOrBlank()
    }

    private fun trackTimer(textView: TextView, scope: TimerScope) {
        trackedTimers[textView] = scope
    }

    private fun untrackTimer(textView: TextView) {
        trackedTimers.remove(textView)
        resolvedTextEffectViews.remove(textView)
        textEffectAccessors.remove(textView)
    }

    private fun applyTrackedTimer(textView: TextView) {
        val scope = trackedTimers[textView] ?: return
        val color = resolveTextColor(scope) ?: return
        applyTextColor(textView, color)
    }

    private fun reapplyTrackedTimers(scope: TimerScope? = null) {
        val timers = synchronized(trackedTimers) {
            trackedTimers.entries
                .filter { entry -> scope == null || entry.value == scope }
                .map { entry -> entry.key }
        }
        timers.forEach(::applyTrackedTimer)
    }

    private fun applyTextColor(textView: TextView, color: Int) {
        val accessors = resolveTextEffectAccessors(textView)
        if (accessors == null) {
            if (textView.currentTextColor != color) textView.setTextColor(color)
            return
        }

        val text = textView.text as? Spanned
        val spans = if (text != null) {
            text.getSpans(0, text.length, accessors.spanClass)
        } else {
            emptyArray()
        }
        if (text == null || spans.isEmpty()) {
            if (textView.currentTextColor != color) textView.setTextColor(color)
            return
        }

        var updated = true
        spans.forEach { span ->
            if (runCatching {
                if (textView.visibility == View.VISIBLE && textView.isAttachedToWindow) {
                    accessors.setNewAppearance.invoke(span, color)
                } else {
                    val start = text.getSpanStart(span)
                    val end = text.getSpanEnd(span)
                    val spanText = if (start >= 0 && end >= start) {
                        text.subSequence(start, end)
                    } else {
                        text
                    }
                    accessors.setOldAppearance.invoke(span, spanText, color)
                }
            }.isFailure) {
                updated = false
            }
        }
        if (textView.currentTextColor != color) textView.setTextColor(color)
        if (updated) textView.invalidate()
    }

    private fun resolveTextEffectAccessors(textView: TextView): TextEffectAccessors? {
        if (resolvedTextEffectViews.contains(textView)) return textEffectAccessors[textView]
        resolvedTextEffectViews.add(textView)
        val spanClassName = when (textView.javaClass.name) {
            TIMER_TEXT_EFFECT_VIEW_CLASS -> TIMER_TEXT_EFFECT_SPAN_CLASS
            HYPER_CHRONOMETER_CLASS -> HYPER_CHRONOMETER_SPAN_CLASS
            else -> return null
        }
        val classLoader = textView.javaClass.classLoader ?: ClassLoader.getSystemClassLoader()
        val spanClass = runCatching { classLoader.loadClass(spanClassName) }.getOrNull()
            ?: return null
        val setOldAppearance = spanClass.methods.firstOrNull { method ->
            method.name == "setOldTextAppearance" && method.parameterTypes.size == 2
        } ?: return null
        val setNewAppearance = spanClass.methods.firstOrNull { method ->
            method.name == "setNewTextAppearance" && method.parameterTypes.size == 1
        } ?: return null
        return TextEffectAccessors(
            spanClass,
            setOldAppearance,
            setNewAppearance,
        ).also { accessors ->
            textEffectAccessors[textView] = accessors
        }
    }

    private fun resolveTextColor(scope: TimerScope): Int? {
        val mode = if (scope == TimerScope.ISLAND) islandMode else focusMode
        return when (mode) {
            MODE_BLACK -> Color.BLACK
            MODE_FOLLOW_BACKGROUND -> if (isRegionDark) Color.WHITE else Color.BLACK
            MODE_INVERT_BACKGROUND -> if (isRegionDark) Color.BLACK else Color.WHITE
            MODE_FOLLOW_STATUS_BAR -> IslandTextColorHook.getStatusBarTint()
            MODE_INVERT_STATUS_BAR -> {
                val tint = IslandTextColorHook.getStatusBarTint()
                if (isLightColor(tint)) Color.BLACK else Color.WHITE
            }
            else -> null
        }
    }

    private fun refreshModes() {
        islandMode = when (
            val mode = ConfigManager.getString(KEY_ISLAND_TEXT_COLOR_MODE, MODE_DEFAULT)
        ) {
            MODE_BLACK,
            MODE_FOLLOW_BACKGROUND,
            MODE_INVERT_BACKGROUND,
            MODE_FOLLOW_STATUS_BAR,
            MODE_INVERT_STATUS_BAR -> mode
            else -> MODE_DEFAULT
        }
        focusMode = when (
            val mode = ConfigManager.getString(KEY_FOCUS_TEXT_COLOR_MODE, MODE_DEFAULT)
        ) {
            MODE_BLACK,
            MODE_FOLLOW_STATUS_BAR,
            MODE_INVERT_STATUS_BAR -> mode
            else -> MODE_DEFAULT
        }
    }

    private fun isStatusBarMode(mode: String): Boolean {
        return mode == MODE_FOLLOW_STATUS_BAR || mode == MODE_INVERT_STATUS_BAR
    }

    private fun isLightColor(color: Int): Boolean {
        return Color.red(color) * 299 + Color.green(color) * 587 +
            Color.blue(color) * 114 >= 128000
    }

    private enum class TimerScope {
        ISLAND,
        FOCUS,
    }

    private data class TextEffectAccessors(
        val spanClass: Class<*>,
        val setOldAppearance: Method,
        val setNewAppearance: Method,
    )

    private val ISLAND_MARKER_METHODS = listOf(
        "getIsland",
        "getDynamicIsland",
        "getFakeDynamicIsland",
        "isDynamicIsland",
    )

    private val FOCUS_TEXT_IDS = setOf(
        "focus_title",
        "focus_subtitle",
        "focus_subtitle_divider",
        "focus_extra_title",
        "focus_extra_title_divider",
        "focus_special_title",
        "focus_content",
        "focus_sub_content",
        "focus_sub_content_divider",
        "focus_function_icon_divider",
    )

    private const val KEY_ISLAND_TEXT_COLOR_MODE = "pref_island_text_color_mode"
    private const val KEY_FOCUS_TEXT_COLOR_MODE = "pref_focus_notification_text_color_mode"

    private const val MODE_DEFAULT = "default"
    private const val MODE_BLACK = "black"
    private const val MODE_FOLLOW_BACKGROUND = "follow_background"
    private const val MODE_INVERT_BACKGROUND = "invert_background"
    private const val MODE_FOLLOW_STATUS_BAR = "follow_status_bar"
    private const val MODE_INVERT_STATUS_BAR = "invert_status_bar"

    private const val BASE_ISLAND_HOLDER_CLASS =
        "miui.systemui.dynamicisland.module.BaseIslandModuleViewHolder"
    private const val SAME_WIDTH_DIGIT_HOLDER_CLASS =
        "miui.systemui.dynamicisland.module.IslandSameWidthDigitViewHolder"
    private const val FOCUS_HOLDER_CLASS =
        "miui.systemui.notification.focus.moduleV3.ModuleViewHolder"
    private const val DYNAMIC_ISLAND_CONTENT_VIEW_CLASS =
        "miui.systemui.dynamicisland.window.content.DynamicIslandBaseContentView"
    private const val TIMER_TEXT_EFFECT_VIEW_CLASS =
        "miuix.colorful.texteffect.TimerTextEffectView"
    private const val HYPER_CHRONOMETER_CLASS =
        "miuix.colorful.texteffect.HyperChronometer"
    private const val TIMER_TEXT_EFFECT_SPAN_CLASS =
        "miuix.colorful.texteffect.TimerTextEffectSpan"
    private const val HYPER_CHRONOMETER_SPAN_CLASS =
        "miuix.colorful.texteffect.HyperChronometerEffectSpan"
}
