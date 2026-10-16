package io.github.hyperisland.xposed.hook

import android.graphics.Color
import android.widget.TextView
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * 超级岛默认文本颜色。颜色计算复用 [TextColorPolicy]：只反转近似灰度的文本，
 * 彩色（含模板高亮色）原样保留。
 */
object IslandTextColorHook : BaseHook() {

    private const val TAG = "HyperIsland[IslandTextColor]"
    private const val KEY_TEXT_COLOR_MODE = "pref_island_text_color_mode"

    private val hookedClassLoaders = ConcurrentHashMap.newKeySet<Int>()
    private val defaultTextViews = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<TextView, Boolean>())
    )

    @Volatile private var isRegionDark = true

    private val rawStatusBarTintListener: (Int) -> Unit = {
        if (currentMode() == TextColorPolicy.MODE_FOLLOW_STATUS_BAR) applyTextColorToTrackedViews()
    }

    private val readableStatusBarTintListener: (Int) -> Unit = {
        if (currentMode() == TextColorPolicy.MODE_INVERT_STATUS_BAR) applyTextColorToTrackedViews()
    }

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        StatusBarTextColorHook.addTintListener(rawStatusBarTintListener)
        StatusBarTextColorHook.addReadableTintListener(readableStatusBarTintListener)
        hookClasses(module, param.defaultClassLoader)
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { classLoader ->
            hookClasses(module, classLoader)
        }
    }

    override fun onConfigChanged() {
        applyTextColorToTrackedViews()
    }

    private fun hookClasses(module: XposedModule, classLoader: ClassLoader) {
        val clId = System.identityHashCode(classLoader)
        if (!hookedClassLoaders.add(clId)) return

        var hookedAny = false

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
                    val textView = chain.args.getOrNull(2) as? TextView
                    if (usesHighlightColor(chain.args)) {
                        if (textView != null) untrack(textView)
                    } else if (currentMode() != TextColorPolicy.MODE_DEFAULT && textView != null) {
                        defaultTextViews.add(textView)
                        applyToView(textView)
                    }
                    result
                }
                log(module) { "hooked ${method.name}" }
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
                log(module) { "hooked updateDarkLightMode" }
            }
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

    private fun applyToView(textView: TextView) {
        TextColorPolicy.applyForTextView(
            textView,
            currentMode(),
            regionDark = isRegionDark,
            statusBarTintBlack = statusBarTintBlack(),
        )
    }

    private fun untrack(textView: TextView) {
        defaultTextViews.remove(textView)
    }

    private fun applyTextColorToTrackedViews() {
        if (currentMode() == TextColorPolicy.MODE_DEFAULT) return
        val mode = currentMode()
        val sbBlack = statusBarTintBlack()
        val views = synchronized(defaultTextViews) { defaultTextViews.toList() }
        views.forEach { textView ->
            val original = TextColorPolicy.originalOf(textView) ?: return@forEach
            runCatching {
                TextColorPolicy.retarget(textView, original, mode, isRegionDark, sbBlack)
            }
        }
    }

    private fun currentMode(): String =
        when (val mode = ConfigManager.getString(KEY_TEXT_COLOR_MODE, TextColorPolicy.MODE_DEFAULT)) {
            TextColorPolicy.MODE_BLACK,
            TextColorPolicy.MODE_FOLLOW_BACKGROUND,
            TextColorPolicy.MODE_INVERT_BACKGROUND,
            TextColorPolicy.MODE_FOLLOW_STATUS_BAR,
            TextColorPolicy.MODE_INVERT_STATUS_BAR -> mode
            else -> TextColorPolicy.MODE_DEFAULT
        }

    private fun statusBarTintBlack(): Boolean =
        StatusBarTextColorHook.getReadableTint() == Color.BLACK
}