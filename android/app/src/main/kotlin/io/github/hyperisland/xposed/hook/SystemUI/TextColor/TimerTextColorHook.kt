package io.github.hyperisland.xposed.hook

import android.graphics.Color
import android.view.View
import android.widget.TextView
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.Collections
import java.util.WeakHashMap

/**
 * 超级岛计时器文本颜色。颜色计算复用 [TextColorPolicy]（只反转近似灰度）。
 *
 * 焦点通知计时器由 [FocusNotificationTextColorHook] 负责；本 Hook 只处理超级岛计时器模块。
 */
object TimerTextColorHook : BaseHook() {

    private const val TAG = "HyperIsland[TimerTextColor]"

    private val hookedClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    )
    private val trackedTimers = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<TextView, Boolean>())
    )

    @Volatile private var islandMode = TextColorPolicy.MODE_DEFAULT
    @Volatile private var isRegionDark = true

    private val rawStatusBarTintListener: (Int) -> Unit = {
        if (islandMode == TextColorPolicy.MODE_FOLLOW_STATUS_BAR) reapplyTrackedTimers()
    }

    private val readableStatusBarTintListener: (Int) -> Unit = {
        if (islandMode == TextColorPolicy.MODE_INVERT_STATUS_BAR) reapplyTrackedTimers()
    }

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        refreshModes()
        StatusBarTextColorHook.addTintListener(rawStatusBarTintListener)
        StatusBarTextColorHook.addReadableTintListener(readableStatusBarTintListener)
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
        hookClass(module, classLoader, BASE_ISLAND_HOLDER_CLASS, ::hookIslandTimerText)
        hookClass(module, classLoader, SAME_WIDTH_DIGIT_HOLDER_CLASS, ::hookIslandChronometer)
        hookClass(module, classLoader, TIMER_TEXT_EFFECT_VIEW_CLASS, ::hookTextEffectUpdates)
        hookClass(module, classLoader, HYPER_CHRONOMETER_CLASS, ::hookTextEffectUpdates)
        hookClass(module, classLoader, DYNAMIC_ISLAND_CONTENT_VIEW_CLASS, ::hookRegionDarkMode)
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
                            trackTimer(timerView)
                            applyTrackedTimer(timerView)
                        }
                    }
                    result
                }
                log(module) { "hooked ${holderClass.name}#${method.name}" }
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
                                trackTimer(chronometer)
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
                log(module) { "hooked ${holderClass.name}#${method.name}" }
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
                log(module) { "hooked ${textEffectViewClass.name}#${method.name}" }
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
                            if (islandMode == TextColorPolicy.MODE_FOLLOW_BACKGROUND ||
                                islandMode == TextColorPolicy.MODE_INVERT_BACKGROUND
                            ) {
                                reapplyTrackedTimers()
                            }
                        }
                    }
                    result
                }
                log(module) { "hooked ${contentViewClass.name}#${method.name}" }
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

    private fun trackTimer(textView: TextView) {
        trackedTimers.add(textView)
    }

    private fun untrackTimer(textView: TextView) {
        trackedTimers.remove(textView)
    }

    private fun applyTrackedTimer(textView: TextView) {
        if (!trackedTimers.contains(textView)) return
        TextColorPolicy.applyForTextView(
            textView,
            islandMode,
            regionDark = isRegionDark,
            statusBarTintBlack = statusBarTintBlack(),
        )
    }

    private fun reapplyTrackedTimers() {
        val sbBlack = statusBarTintBlack()
        val timers = synchronized(trackedTimers) { trackedTimers.toList() }
        timers.forEach { textView ->
            val original = TextColorPolicy.originalOf(textView) ?: return@forEach
            runCatching {
                TextColorPolicy.retarget(textView, original, islandMode, isRegionDark, sbBlack)
            }
        }
    }

    private fun refreshModes() {
        islandMode = when (
            val mode = ConfigManager.getString(KEY_ISLAND_TEXT_COLOR_MODE, TextColorPolicy.MODE_DEFAULT)
        ) {
            TextColorPolicy.MODE_BLACK,
            TextColorPolicy.MODE_FOLLOW_BACKGROUND,
            TextColorPolicy.MODE_INVERT_BACKGROUND,
            TextColorPolicy.MODE_FOLLOW_STATUS_BAR,
            TextColorPolicy.MODE_INVERT_STATUS_BAR -> mode
            else -> TextColorPolicy.MODE_DEFAULT
        }
    }

    private fun statusBarTintBlack(): Boolean =
        StatusBarTextColorHook.getReadableTint() == Color.BLACK

    private const val KEY_ISLAND_TEXT_COLOR_MODE = "pref_island_text_color_mode"

    private const val BASE_ISLAND_HOLDER_CLASS =
        "miui.systemui.dynamicisland.module.BaseIslandModuleViewHolder"
    private const val SAME_WIDTH_DIGIT_HOLDER_CLASS =
        "miui.systemui.dynamicisland.module.IslandSameWidthDigitViewHolder"
    private const val DYNAMIC_ISLAND_CONTENT_VIEW_CLASS =
        "miui.systemui.dynamicisland.window.content.DynamicIslandBaseContentView"
    private const val TIMER_TEXT_EFFECT_VIEW_CLASS =
        "miuix.colorful.texteffect.TimerTextEffectView"
    private const val HYPER_CHRONOMETER_CLASS =
        "miuix.colorful.texteffect.HyperChronometer"
}