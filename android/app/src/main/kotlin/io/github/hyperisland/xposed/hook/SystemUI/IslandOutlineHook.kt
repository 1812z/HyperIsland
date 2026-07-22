package io.github.hyperisland.xposed.hook.SystemUI

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.hyperisland.xposed.hook.IslandBackgroundHook
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
    private val capturingStockDrawable = ThreadLocal<Boolean>()
    private val capturedStockDrawable = ThreadLocal<Drawable>()
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
        val backgroundViewField = contentViewClass.getDeclaredField("backgroundView").apply {
            isAccessible = true
        }
        val stateField = contentViewClass.getDeclaredField("state").apply { isAccessible = true }
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
                    invokeAndRefreshOutline(
                        method,
                        chain.thisObject,
                        backgroundViewField,
                        arrayOf(0f),
                        outlineType(runCatching { stateField.get(chain.thisObject) }.getOrNull()),
                    )
                }
                log(module, "hooked updateMedianLuma")
            }
    }

    private fun hookDarkLightMode(module: XposedModule, contentViewClass: Class<*>) {
        val isExpandedMethod = contentViewClass.getMethod("isExpanded")
        val backgroundViewField = contentViewClass.getDeclaredField("backgroundView").apply {
            isAccessible = true
        }
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
                    // Select the SystemUI drawable branch that owns the stroke. The
                    // captured drawable remains scoped to this exact state update.
                    args[2] = false
                    invokeAndRefreshOutline(
                        method,
                        chain.thisObject,
                        backgroundViewField,
                        args,
                        outlineType(chain.args.getOrNull(0)),
                    )
                }
                log(module, "hooked updateDarkLightMode")
            }
    }

    private fun hookStockDrawable(module: XposedModule, backgroundViewClass: Class<*>) {
        val method = backgroundViewClass.getDeclaredMethod("setDrawable", Drawable::class.java)
        module.hook(method).intercept { chain ->
            if (capturingStockDrawable.get() == true) {
                (chain.args.getOrNull(0) as? Drawable)?.let(capturedStockDrawable::set)
            }
            chain.proceed()
        }
        log(module, "hooked background setDrawable")
    }

    /** Adds the current stock SystemUI stroke to a replacement drawable. */
    internal fun withOutline(
        replacement: Drawable,
        stockDrawable: Drawable?,
        isExpanded: Boolean,
        typeName: String? = null,
    ): Drawable {
        if (!isEnabledForState(isExpanded)) return replacement
        // Only consume a fresh SystemUI drawable, never an outline from a previous wrapper.
        val stock = stockDrawable as? GradientDrawable ?: return replacement
        val outline = stock.constantState?.newDrawable()?.mutate() as? GradientDrawable
            ?: return replacement
        outline.setColor(Color.TRANSPARENT)
        return OutlineDrawable(replacement, outline, typeName)
    }

    internal fun refreshOutline(
        drawable: Drawable?,
        stockDrawable: Drawable,
        isExpanded: Boolean,
        typeName: String?,
    ): Drawable? {
        val current = drawable as? OutlineDrawable ?: return null
        if (typeName == null || current.typeName != typeName) return null
        val fill = current.fill
        val alpha = current.alpha
        current.release()
        return withOutline(fill, stockDrawable, isExpanded, typeName).apply { this.alpha = alpha }
    }

    internal fun stockOutlineFor(drawable: Drawable?, typeName: String): Drawable? {
        val current = drawable as? OutlineDrawable ?: return null
        return current.outline.takeIf { current.typeName == typeName }
    }

    private fun invokeAndRefreshOutline(
        method: Method,
        contentView: Any?,
        backgroundViewField: java.lang.reflect.Field,
        args: Array<Any?>,
        type: OutlineType?,
    ) {
        capturedStockDrawable.remove()
        capturingStockDrawable.set(true)
        try {
            invokeWithArgs(method, contentView, args)
            val backgroundView = runCatching { backgroundViewField.get(contentView) }.getOrNull()
            val handledByBlur = IslandBlurHook.updateStockOutline(
                backgroundView,
                capturedStockDrawable.get(),
                type?.name,
            )
            if (!handledByBlur) {
                IslandBackgroundHook.updateStockOutline(
                    backgroundView,
                    capturedStockDrawable.get(),
                    type?.name,
                )
            }
        } finally {
            capturingStockDrawable.remove()
            capturedStockDrawable.remove()
        }
    }

    internal fun isOutlineEnabled(isExpanded: Boolean): Boolean = isEnabledForState(isExpanded)

    internal fun hasOutline(drawable: Drawable?): Boolean = drawable is OutlineDrawable

    internal fun releaseOutline(drawable: Drawable?) {
        (drawable as? OutlineDrawable)?.release()
    }

    private fun shouldAlwaysShow(contentView: Any?, isExpandedMethod: Method): Boolean {
        return isEnabledForState(isExpanded(contentView, isExpandedMethod))
    }

    private fun shouldAlwaysShowForState(
        contentView: Any?,
        isExpandedMethod: Method,
        state: Any?,
    ): Boolean {
        return isEnabledForState(isExpandedForState(contentView, isExpandedMethod, state))
    }

    private fun isExpanded(contentView: Any?, isExpandedMethod: Method): Boolean {
        if (contentView == null) return false
        return runCatching { isExpandedMethod.invoke(contentView) as Boolean }.getOrDefault(false)
    }

    private fun isExpandedForState(
        contentView: Any?,
        isExpandedMethod: Method,
        state: Any?,
    ): Boolean {
        return state?.javaClass?.simpleName?.contains("Expanded")
            ?: isExpanded(contentView, isExpandedMethod)
    }

    private fun outlineType(state: Any?): OutlineType? {
        val name = state?.javaClass?.simpleName.orEmpty()
        return when {
            name.contains("SmallIsland") -> OutlineType.SMALL
            name.contains("BigIsland") -> OutlineType.BIG
            name.contains("Expanded") -> OutlineType.EXPAND
            else -> null
        }
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
        val fill: Drawable,
        val outline: Drawable,
        val typeName: String?,
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

    private enum class OutlineType { SMALL, BIG, EXPAND }
}
