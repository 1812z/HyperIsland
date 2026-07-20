package io.github.hyperisland.xposed.hook.SystemUI

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

/** Keeps the island outline opaque by overriding luminance inputs before rendering. */
object IslandOutlineHook : BaseHook() {

    private const val TAG = "HyperIsland[IslandOutline]"
    private const val KEY_ISLAND_OUTLINE = "pref_always_show_island_outline"
    private const val KEY_FOCUS_OUTLINE = "pref_always_show_focus_outline"
    private const val CONTENT_VIEW_CLASS =
        "miui.systemui.dynamicisland.window.content.DynamicIslandBaseContentView"
    private const val BACKGROUND_VIEW_CLASS =
        "miui.systemui.dynamicisland.DynamicIslandBackgroundView"

    private val hookedContentClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>()),
    )
    private val hookedBackgroundClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>()),
    )
    private val invokingOriginal = ThreadLocal<Boolean>()
    private val stockOutlines = Collections.synchronizedMap(
        WeakHashMap<Any, Drawable>(),
    )
    @Volatile private var alwaysShowIslandOutline = false
    @Volatile private var alwaysShowFocusOutline = false

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        if (param.packageName != "com.android.systemui") return
        loadConfig()
        hookClasses(module, param.defaultClassLoader)
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { classLoader ->
            hookClasses(module, classLoader)
        }
    }

    private fun hookClasses(module: XposedModule, classLoader: ClassLoader) {
        runCatching { classLoader.loadClass(CONTENT_VIEW_CLASS) }.onSuccess { contentViewClass ->
            if (hookedContentClasses.add(contentViewClass)) {
                runCatching {
                    hookMedianLuma(module, contentViewClass)
                    hookDarkLightMode(module, contentViewClass)
                }.onFailure { error ->
                    hookedContentClasses.remove(contentViewClass)
                    logError(module, "failed to hook island outline state: ${error.message}")
                }
            }
        }.onFailure { error ->
            if (error !is ClassNotFoundException) {
                logError(module, "failed to hook island outline state: ${error.message}")
            }
        }
        runCatching { classLoader.loadClass(BACKGROUND_VIEW_CLASS) }.onSuccess { backgroundClass ->
            if (hookedBackgroundClasses.add(backgroundClass)) {
                runCatching { hookStockDrawable(module, backgroundClass) }.onFailure { error ->
                    hookedBackgroundClasses.remove(backgroundClass)
                    logError(module, "failed to hook island outline drawable: ${error.message}")
                }
            }
        }.onFailure { error ->
            if (error !is ClassNotFoundException) {
                logError(module, "failed to hook island outline drawable: ${error.message}")
            }
        }
    }

    private fun hookMedianLuma(module: XposedModule, contentViewClass: Class<*>) {
        val isExpandedMethod = contentViewClass.getMethod("isExpanded")
        contentViewClass.declaredMethods
            .filter { method ->
                method.name == "updateMedianLuma" &&
                    method.parameterTypes.contentEquals(arrayOf(Float::class.javaPrimitiveType))
            }
            .forEach { method ->
                module.hook(method).intercept { chain ->
                    if (invokingOriginal.get() == true ||
                        !shouldAlwaysShow(chain.thisObject, isExpandedMethod)
                    ) {
                        return@intercept chain.proceed()
                    }
                    invokeWithArgs(method, chain.thisObject, arrayOf(0f))
                }
                log(module, "hooked updateMedianLuma")
            }
    }

    private fun hookDarkLightMode(module: XposedModule, contentViewClass: Class<*>) {
        val isExpandedMethod = contentViewClass.getMethod("isExpanded")
        contentViewClass.declaredMethods
            .filter { method ->
                method.name == "updateDarkLightMode" &&
                    method.parameterTypes.size == 4 &&
                    method.parameterTypes[2] == Boolean::class.javaPrimitiveType
            }
            .forEach { method ->
                module.hook(method).intercept { chain ->
                    if (invokingOriginal.get() == true ||
                        !shouldAlwaysShowForState(
                            chain.thisObject,
                            isExpandedMethod,
                            chain.args.getOrNull(0),
                        )
                    ) {
                        return@intercept chain.proceed()
                    }
                    val args = chain.args.toTypedArray()
                    // false selects the drawable branch that owns the island stroke.
                    args[2] = false
                    invokeWithArgs(method, chain.thisObject, args)
                }
                log(module, "hooked updateDarkLightMode")
            }
    }

    private fun hookStockDrawable(module: XposedModule, backgroundViewClass: Class<*>) {
        val method = backgroundViewClass.getDeclaredMethod("setDrawable", Drawable::class.java)
        module.hook(method).intercept { chain ->
            val result = chain.proceed()
            rememberStockOutline(chain.thisObject, chain.args.getOrNull(0) as? Drawable)
            result
        }
        log(module, "hooked background setDrawable")
    }

    internal fun rememberStockOutline(backgroundView: Any, drawable: Drawable?) {
        val stock = drawable as? GradientDrawable ?: return
        val outline = stock.constantState?.newDrawable()?.mutate() as? GradientDrawable ?: return
        // Keep SystemUI's stroke and shape, but let replacement backgrounds show through.
        outline.setColor(Color.TRANSPARENT)
        stockOutlines[backgroundView] = outline
    }

    /** Adds the stock SystemUI stroke to a drawable installed by another island hook. */
    internal fun withOutline(
        backgroundView: Any,
        replacement: Drawable,
        isExpanded: Boolean,
    ): Drawable {
        if (!isEnabledForState(isExpanded)) return replacement
        val cachedOutline = synchronized(stockOutlines) { stockOutlines[backgroundView] }
            ?: return replacement
        val outline = cachedOutline.constantState?.newDrawable()?.mutate() ?: return replacement
        return OutlineDrawable(replacement, outline)
    }

    internal fun isOutlineEnabled(isExpanded: Boolean): Boolean = isEnabledForState(isExpanded)

    internal fun hasOutline(drawable: Drawable?): Boolean = drawable is OutlineDrawable

    internal fun releaseOutline(drawable: Drawable?) {
        (drawable as? OutlineDrawable)?.release()
    }

    private fun shouldAlwaysShow(contentView: Any?, isExpandedMethod: Method): Boolean {
        if (contentView == null) return false
        val isExpanded = runCatching {
            isExpandedMethod.invoke(contentView) as Boolean
        }.getOrDefault(false)
        return isEnabledForState(isExpanded)
    }

    private fun shouldAlwaysShowForState(
        contentView: Any?,
        isExpandedMethod: Method,
        state: Any?,
    ): Boolean {
        val isExpanded = state?.javaClass?.simpleName?.contains("Expanded")
        return if (isExpanded != null) isEnabledForState(isExpanded)
        else shouldAlwaysShow(contentView, isExpandedMethod)
    }

    private fun isEnabledForState(isExpanded: Boolean): Boolean {
        return if (isExpanded) alwaysShowFocusOutline else alwaysShowIslandOutline
    }

    override fun onConfigChanged() {
        loadConfig()
    }

    private fun loadConfig() {
        alwaysShowIslandOutline = ConfigManager.getBoolean(KEY_ISLAND_OUTLINE, false)
        alwaysShowFocusOutline = ConfigManager.getBoolean(KEY_FOCUS_OUTLINE, false)
    }

    private fun invokeWithArgs(method: Method, receiver: Any?, args: Array<Any?>): Any? {
        invokingOriginal.set(true)
        return try {
            method.isAccessible = true
            method.invoke(receiver, *args)
        } catch (error: InvocationTargetException) {
            throw error.targetException
        } finally {
            invokingOriginal.remove()
        }
    }

    private class OutlineDrawable(
        private val fill: Drawable,
        private val outline: Drawable,
    ) : Drawable(), Drawable.Callback {

        init {
            fill.callback = this
            outline.callback = this
        }

        override fun draw(canvas: Canvas) {
            fill.bounds = bounds
            outline.bounds = bounds
            fill.draw(canvas)
            outline.draw(canvas)
        }

        override fun setAlpha(alpha: Int) {
            fill.alpha = alpha
            outline.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            fill.colorFilter = colorFilter
            outline.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

        override fun invalidateDrawable(who: Drawable) = invalidateSelf()

        override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
            scheduleSelf(what, `when`)
        }

        override fun unscheduleDrawable(who: Drawable, what: Runnable) {
            unscheduleSelf(what)
        }

        fun release() {
            fill.callback = null
            outline.callback = null
            callback = null
        }
    }
}
