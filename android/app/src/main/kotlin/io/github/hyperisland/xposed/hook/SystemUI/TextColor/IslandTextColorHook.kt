package io.github.hyperisland.xposed.hook

import android.graphics.Color
import android.text.Spanned
import android.widget.TextView
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.Collections
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.WeakHashMap

/**
 * Overrides Dynamic Island default text colors while preserving explicit highlight colors.
 */
object IslandTextColorHook : BaseHook() {

    private const val TAG = "HyperIsland[IslandTextColor]"
    private const val KEY_TEXT_COLOR_MODE = "pref_island_text_color_mode"

    private const val MODE_DEFAULT = "default"
    private const val MODE_BLACK = "black"
    private const val MODE_FOLLOW_BACKGROUND = "follow_background"
    private const val MODE_INVERT_BACKGROUND = "invert_background"
    private const val MODE_FOLLOW_STATUS_BAR = "follow_status_bar"
    private const val MODE_INVERT_STATUS_BAR = "invert_status_bar"

    private val hookedClassLoaders = ConcurrentHashMap.newKeySet<Int>()
    private val hookedTextViewSetColor = AtomicBoolean(false)
    private val defaultTextViews = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<TextView, Boolean>())
    )
    private val resolvedTextEffectViews = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<TextView, Boolean>())
    )
    private val textEffectAccessors = Collections.synchronizedMap(
        WeakHashMap<TextView, TextEffectAccessors>()
    )
    private val statusBarTintListeners = CopyOnWriteArrayList<(Int) -> Unit>()

    @Volatile private var isRegionDark = true
    @Volatile private var statusBarTint = Color.WHITE
    @Volatile private var dispatchedStatusBarTint = Color.WHITE

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { classLoader ->
            hookClasses(module, classLoader)
        }
    }

    override fun onConfigChanged() {
        applyTextColorToTrackedViews()
    }

    fun getStatusBarTint(): Int = statusBarTint

    fun addStatusBarTintListener(listener: (Int) -> Unit) {
        statusBarTintListeners.addIfAbsent(listener)
    }

    private fun hookClasses(module: XposedModule, classLoader: ClassLoader) {
        val clId = System.identityHashCode(classLoader)
        if (!hookedClassLoaders.add(clId)) return

        var hookedAny = false
        if (hookStatusBarSetTextColor(module)) hookedAny = true

        runCatching {
            val baseHolderClass = classLoader.loadClass(
                "miui.systemui.dynamicisland.module.BaseIslandModuleViewHolder"
            )
            hookTextColorMethods(module, baseHolderClass)
            hookedAny = true
        }.onFailure { error ->
            if (error !is ClassNotFoundException) {
                logError(module, "failed to hook BaseIslandModuleViewHolder: ${error.message}")
            }
        }

        runCatching {
            val contentViewClass = classLoader.loadClass(
                "miui.systemui.dynamicisland.window.content.DynamicIslandBaseContentView"
            )
            hookUpdateDarkLightMode(module, contentViewClass)
            hookedAny = true
        }.onFailure { error ->
            if (error !is ClassNotFoundException) {
                logError(module, "failed to hook DynamicIslandBaseContentView: ${error.message}")
            }
        }

        if (hookStatusBarTextTint(module, classLoader)) hookedAny = true

        if (!hookedAny) hookedClassLoaders.remove(clId)
    }

    private fun hookTextColorMethods(module: XposedModule, baseHolderClass: Class<*>) {
        baseHolderClass.declaredMethods
            .filter { method ->
                method.name == "setTitleHighlightColor" || method.name == "setContentHighlightColor"
            }
            .filter { method ->
                method.parameterTypes.size == 4 && TextView::class.java.isAssignableFrom(method.parameterTypes[2])
            }
            .forEach { method ->
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    val mode = ConfigManager.getString(KEY_TEXT_COLOR_MODE, MODE_DEFAULT)
                    val textView = chain.args.getOrNull(2) as? TextView
                    if (usesHighlightColor(chain.args)) {
                        if (textView != null) defaultTextViews.remove(textView)
                    } else if (mode != MODE_DEFAULT) {
                        val text = chain.args.getOrNull(3) as? String
                        if (textView != null && text != null) {
                            defaultTextViews.add(textView)
                            applyTextColor(textView, resolveDefaultTextColor(mode))
                        }
                    }
                    result
                }
                log(module, "hooked ${method.name}")
            }
    }

    private fun hookUpdateDarkLightMode(module: XposedModule, contentViewClass: Class<*>) {
        contentViewClass.declaredMethods
            .filter { method -> method.name == "updateDarkLightMode" && method.parameterTypes.size >= 3 }
            .forEach { method ->
                module.hook(method).intercept { chain ->
                    (chain.args.getOrNull(2) as? Boolean)?.let { useDarkText ->
                        val nextIsRegionDark = !useDarkText
                        val changed = isRegionDark != nextIsRegionDark
                        isRegionDark = nextIsRegionDark
                        if (changed) applyTextColorToTrackedViews()
                    }
                    chain.proceed()
                }
                log(module, "hooked updateDarkLightMode")
            }
    }

    private fun hookStatusBarTextTint(module: XposedModule, classLoader: ClassLoader): Boolean {
        var hookedAny = false
        listOf(
            "com.android.systemui.statusbar.policy.Clock",
            "com.android.systemui.statusbar.views.MiuiClock",
        ).forEach { className ->
            runCatching {
                val statusTextClass = classLoader.loadClass(className)
                statusTextClass.declaredMethods
                    .filter { method ->
                        method.name == "onDarkChanged" || method.name == "onLightDarkTintChanged"
                    }
                    .forEach { method ->
                        module.hook(method).intercept { chain ->
                            val result = chain.proceed()
                            runCatching {
                                updateStatusBarTintFromTextView(chain.thisObject as? TextView)
                            }
                            result
                        }
                        log(module, "hooked $className#${method.name}")
                        hookedAny = true
                    }
            }.onFailure { error ->
                if (error !is ClassNotFoundException) {
                    logError(module, "failed to hook $className: ${error.message}")
                }
            }
        }
        return hookedAny
    }

    private fun hookStatusBarSetTextColor(module: XposedModule): Boolean {
        if (!hookedTextViewSetColor.compareAndSet(false, true)) return false
        var hookedAny = false
        TextView::class.java.declaredMethods
            .filter { method ->
                method.name == "setTextColor" &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == Integer.TYPE
            }
            .forEach { method ->
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    runCatching {
                        updateStatusBarTintFromTextView(chain.thisObject as? TextView)
                    }
                    result
                }
                log(module, "hooked TextView#setTextColor")
                hookedAny = true
            }
        if (!hookedAny) hookedTextViewSetColor.set(false)
        return hookedAny
    }

    private fun usesHighlightColor(args: List<*>): Boolean {
        val template = args.getOrNull(0) ?: return false
        val showHighlight = args.getOrNull(1) as? Boolean ?: false
        if (!showHighlight) return false
        val highlightColor = runCatching {
            template.javaClass.methods.firstOrNull { it.name == "getHighlightColor" }?.invoke(template) as? String
        }.getOrNull()
        return !highlightColor.isNullOrBlank()
    }

    private fun resolveDefaultTextColor(mode: String): Int {
        return when (mode) {
            MODE_BLACK -> Color.BLACK
            MODE_FOLLOW_BACKGROUND -> if (isRegionDark) Color.WHITE else Color.BLACK
            MODE_INVERT_BACKGROUND -> if (isRegionDark) Color.BLACK else Color.WHITE
            MODE_FOLLOW_STATUS_BAR -> statusBarTint
            MODE_INVERT_STATUS_BAR -> invertReadableColor(statusBarTint)
            else -> Color.WHITE
        }
    }

    private fun invertReadableColor(color: Int): Int {
        return if (isLightColor(color)) Color.BLACK else Color.WHITE
    }

    private fun isLightColor(color: Int): Boolean {
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return (red * 299 + green * 587 + blue * 114) >= 128000
    }

    private fun updateStatusBarTintFromTextView(textView: TextView?) {
        if (textView == null || !isStatusBarTextView(textView)) return
        val nextTint = textView.currentTextColor
        if (statusBarTint == nextTint) return
        statusBarTint = nextTint
        applyTextColorToTrackedViews()
        val readableTint = if (isLightColor(nextTint)) Color.WHITE else Color.BLACK
        if (dispatchedStatusBarTint != readableTint) {
            dispatchedStatusBarTint = readableTint
            statusBarTintListeners.forEach { listener ->
                runCatching { listener(readableTint) }
            }
        }
    }

    private fun isStatusBarTextView(textView: TextView): Boolean {
        val className = textView.javaClass.name
        if (className in STATUS_BAR_TEXT_CLASSES) return true

        var parent = textView.parent
        repeat(5) {
            val parentName = parent?.javaClass?.name ?: return@repeat
            if (parentName in STATUS_BAR_TEXT_PARENT_CLASSES) return true
            parent = parent.parent
        }
        return false
    }

    private fun applyTextColorToTrackedViews() {
        val mode = ConfigManager.getString(KEY_TEXT_COLOR_MODE, MODE_DEFAULT)
        if (mode == MODE_DEFAULT) return
        val color = resolveDefaultTextColor(mode)
        val views = synchronized(defaultTextViews) { defaultTextViews.toList() }
        views.forEach { textView ->
            applyTextColor(textView, color)
        }
    }

    private fun applyTextColor(textView: TextView, color: Int) {
        val accessors = resolveTextEffectAccessors(textView)
        if (accessors == null) {
            if (textView.currentTextColor == color) return
            textView.setTextColor(color)
            return
        }

        val spanned = textView.text as? Spanned
        val spans = if (spanned != null) {
            spanned.getSpans(0, spanned.length, accessors.spanClass)
        } else {
            emptyArray()
        }
        if (spanned == null || spans.isEmpty()) {
            textView.setTextColor(color)
            return
        }
        if (runCatching {
                accessors.setAppearance.invoke(spans[0], spanned, color)
            }.isFailure
        ) {
            textView.setTextColor(color)
            return
        }
        textView.setTextColor(color)
        textView.invalidate()
    }

    private fun resolveTextEffectAccessors(textView: TextView): TextEffectAccessors? {
        if (resolvedTextEffectViews.contains(textView)) return textEffectAccessors[textView]
        resolvedTextEffectViews.add(textView)
        textView.javaClass.methods.firstOrNull { method ->
            method.name == "updateTextWithNewAppearance" && method.parameterTypes.size == 2
        } ?: return null
        val spanClass = runCatching {
            textView.javaClass.classLoader.loadClass(
                "miuix.colorful.texteffect.TimerTextEffectSpan"
            )
        }.getOrNull() ?: return null
        val setAppearance = spanClass.methods.firstOrNull { method ->
            method.name == "setOldTextAppearance" && method.parameterTypes.size == 2
        } ?: return null
        return TextEffectAccessors(spanClass, setAppearance).also { accessors ->
            textEffectAccessors[textView] = accessors
        }
    }

    private data class TextEffectAccessors(
        val spanClass: Class<*>,
        val setAppearance: Method,
    )

    private val STATUS_BAR_TEXT_CLASSES = setOf(
        "com.android.systemui.statusbar.policy.Clock",
        "com.android.systemui.statusbar.views.MiuiClock",
        "com.android.systemui.statusbar.OperatorNameView",
    )

    private val STATUS_BAR_TEXT_PARENT_CLASSES = setOf(
        "com.android.systemui.battery.BatteryMeterView",
        "com.android.systemui.statusbar.views.MiuiBatteryMeterView",
        "com.android.systemui.statusbar.views.NetworkSpeedView",
    )

}
