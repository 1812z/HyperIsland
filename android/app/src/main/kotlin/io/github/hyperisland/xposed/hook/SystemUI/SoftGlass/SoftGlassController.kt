package io.github.hyperisland.xposed.hook.SystemUI.SoftGlass

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.View
import android.view.ViewOutlineProvider
import io.github.hyperisland.xposed.log
import io.github.hyperisland.xposed.logWarn
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

/**
 * Per-View renderer for Xiaomi's native Bionics soft-glass material.
 *
 * Lifecycle is identical to the Gaussian renderer: apply on the currently rendered real/fake
 * View and fully release when that View stops rendering. SystemUI exclusively owns window blur,
 * pass-window blur, FPS and crop state.
 */
internal object SoftGlassController {
    private const val TAG = "HyperIsland[SoftGlass]"
    private const val WINDOW_VIEW_CLASS =
        "miui.systemui.dynamicisland.window.DynamicIslandWindowView"
    private const val SYSTEM_SMALL_BLUR_RADIUS = 50
    private const val SYSTEM_BIG_BLUR_RADIUS = 500
    private val managedViews = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<View, Boolean>()),
    )
    private val stockBackgrounds = Collections.synchronizedMap(WeakHashMap<View, Drawable?>())
    private val appliedConfigs = Collections.synchronizedMap(WeakHashMap<View, SoftGlassConfig>())
    private val appliedWindowRadii = Collections.synchronizedMap(WeakHashMap<View, Int>())
    private val retainedWindowBlurRoots = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<View, Boolean>()),
    )
    private val retainedPassBlurRoots = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<View, Boolean>()),
    )
    private val hookedStyleClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>()),
    )
    private val hookedWindowClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>()),
    )

    @Volatile private var systemParams: FloatArray? = null
    @Volatile private var setViewMode: Method? = null
    @Volatile private var clearBlend: Method? = null
    @Volatile private var setPassBlurFps: Method? = null
    @Volatile private var isBionicsActiveMethod: Method? = null

    fun bindRuntime(module: XposedModule, contentClass: Class<*>, compatClass: Class<*>) {
        if (systemParams == null) {
            systemParams = runCatching {
                val field = contentClass.getDeclaredField("EXPANDED_GLASS_TOKEN").apply {
                    isAccessible = true
                }
                val token = field.get(null)
                val method = findMethod(token.javaClass, "getToBionicsParams")
                    ?: return@runCatching null
                (method.invoke(token) as? FloatArray)?.clone()
            }.getOrNull()
        }
        val styleClass = runCatching {
            Class.forName("miui.systemui.util.MiBackgroundStyle", false, contentClass.classLoader)
        }.getOrNull()
        isBionicsActiveMethod = styleClass?.let {
            findMethod(it, "isBionicsActive", Context::class.java)
        }
        setViewMode = findMethod(
            compatClass,
            "setMiViewBlurModeCompat",
            View::class.java,
            Int::class.javaPrimitiveType!!,
        )
        clearBlend = findMethod(
            compatClass,
            "clearMiBackgroundBlendColorCompat",
            View::class.java,
        )
        setPassBlurFps = findMethod(
            compatClass,
            "setMiPassBlurFps",
            View::class.java,
            Int::class.javaPrimitiveType!!,
        )
        if (styleClass != null) hookSystemWriters(module, styleClass)
        log("$TAG runtime bound params=${systemParams?.size ?: -1}")
    }

    /**
     * A settled SMALL/BIG still needs the root background sampler for native Bionics. SystemUI
     * normally closes it after EXPAND; keep only updateWindowBlur enabled while a rendered module
     * SOFT View exists. This does not touch pass-window blur, crop or FPS.
     */
    fun observeWindowLifecycle(module: XposedModule, windowClass: Class<*>) {
        if (!hookedWindowClasses.add(windowClass)) return
        findMethod(
            windowClass,
            "updateWindowBlur",
            Boolean::class.javaPrimitiveType!!,
        )?.let { method ->
            module.hook(method).intercept { chain ->
                val root = chain.thisObject as? View ?: return@intercept chain.proceed()
                val requested = chain.args.getOrNull(0) as? Boolean ?: false
                val keepForSoft = !requested && hasVisibleManagedView(root)
                val result = if (keepForSoft) chain.proceed(arrayOf(true)) else chain.proceed()
                appliedWindowRadii.remove(root)
                if (requested || keepForSoft) {
                    retainedWindowBlurRoots.add(root)
                    currentVisibleConfig(root)?.let { updateWindowRadius(root, it.blurRadius) }
                } else {
                    retainedWindowBlurRoots.remove(root)
                }
                result
            }
        }
        findMethod(
            windowClass,
            "updatePassWindowBlur",
            Boolean::class.javaPrimitiveType!!,
        )?.let { method ->
            module.hook(method).intercept { chain ->
                val root = chain.thisObject as? View ?: return@intercept chain.proceed()
                val requested = chain.args.getOrNull(0) as? Boolean ?: false
                if (!requested && hasManagedView(root)) {
                    // Keep the same SurfaceTexture attached. Hidden states are released, so its
                    // crop follows only the currently rendered SOFT View instead of stale BIG.
                    retainedPassBlurRoots.add(root)
                    runCatching { setPassBlurFps?.invoke(null, root, -1) }
                    return@intercept null
                }
                val result = chain.proceed()
                if (requested && hasManagedView(root)) {
                    retainedPassBlurRoots.add(root)
                    runCatching { setPassBlurFps?.invoke(null, root, 60) }
                } else if (!requested) {
                    retainedPassBlurRoots.remove(root)
                }
                result
            }
        }
    }

    /** Observe stock cleanup and parameter rewrites without changing its window lifecycle. */
    private fun hookSystemWriters(module: XposedModule, styleClass: Class<*>) {
        if (!hookedStyleClasses.add(styleClass)) return
        findMethod(
            styleClass,
            "setMiViewMaterialType",
            View::class.java,
            Int::class.javaPrimitiveType!!,
        )?.let { method ->
            module.hook(method).intercept { chain ->
                val result = chain.proceed()
                val view = chain.args.getOrNull(0) as? View
                val type = chain.args.getOrNull(1) as? Int
                if (view != null && type != 1) forget(view)
                result
            }
        }
        findMethod(
            styleClass,
            "setMiGlassCompat",
            View::class.java,
            FloatArray::class.java,
        )?.let { method ->
            module.hook(method).intercept { chain ->
                val view = chain.args.getOrNull(0) as? View
                val config = view?.let(appliedConfigs::get)
                if (view != null && config != null && managedViews.contains(view)) {
                    val source = chain.args.getOrNull(1) as? FloatArray
                    return@intercept chain.proceed(
                        arrayOf(view, customizeParams(source ?: baseParams(), config)),
                    )
                }
                chain.proceed()
            }
        }
    }

    fun apply(view: View, config: SoftGlassConfig): Boolean = runCatching {
        if (!isSystemBionicsActive(view)) {
            release(view)
            return@runCatching false
        }
        val setMaterial = findMethod(
            view.javaClass,
            "setMiViewMaterialType",
            Int::class.javaPrimitiveType!!,
        ) ?: return@runCatching false
        val setGlass = findMethod(view.javaClass, "setMiGlass", FloatArray::class.java)
            ?: return@runCatching false

        if (managedViews.contains(view) && appliedConfigs[view] == config) {
            if (view.background != null) view.background = null
            updateWindowRadius(view, config.blurRadius)
            return@runCatching true
        }
        if (!managedViews.contains(view)) {
            stockBackgrounds[view] = view.background
            installOutline(view)
        }
        setViewMode?.invoke(null, view, 1)
        clearBlend?.invoke(null, view)
        view.background = null
        setMaterial.invoke(view, 1)
        setGlass.invoke(view, customizeParams(baseParams(), config))
        managedViews.add(view)
        appliedConfigs[view] = config
        ensureWindowBlur(view)
        ensurePassWindowBlur(view)
        updateWindowRadius(view, config.blurRadius)
        view.invalidate()
        true
    }.getOrElse { error ->
        logWarn("$TAG apply failed: ${error.message}")
        false
    }

    fun isManaged(view: View): Boolean = managedViews.contains(view)
    fun isActive(view: View): Boolean = isManaged(view)
    fun isSystemBionicsActive(view: View): Boolean = runCatching {
        isBionicsActiveMethod?.invoke(null, view.context) as? Boolean
    }.getOrNull() == true

    /** Gaussian-equivalent hidden-state lifecycle: no retained or preheated renderer. */
    fun suspend(view: View) = release(view, restoreBackground = false)

    fun release(
        view: View,
        restoreBackground: Boolean = true,
        releaseSampling: Boolean = true,
    ) {
        if (!managedViews.remove(view)) return
        val root = findWindowView(view)
        val stock = stockBackgrounds.remove(view)
        appliedConfigs.remove(view)
        clearMaterial(view)
        if (restoreBackground && view.background == null) view.background = stock
        if (root != null && !hasManagedView(root)) {
            restoreWindowRadius(root)
            if (releaseSampling) {
                closeRetainedPassBlur(root)
                closeRetainedWindowBlur(root)
            } else {
                // A DEFAULT/Gaussian/Liquid destination now owns the same SystemUI window.
                // Relinquish bookkeeping without sending false over its freshly opened sampler.
                retainedPassBlurRoots.remove(root)
                retainedWindowBlurRoots.remove(root)
            }
        }
    }

    private fun forget(view: View) {
        managedViews.remove(view)
        stockBackgrounds.remove(view)
        appliedConfigs.remove(view)
    }

    private fun clearMaterial(view: View) {
        runCatching {
            findMethod(
                view.javaClass,
                "setMiViewMaterialType",
                Int::class.javaPrimitiveType!!,
            )?.invoke(view, 0)
        }
        runCatching { clearBlend?.invoke(null, view) }
        runCatching { setViewMode?.invoke(null, view, 0) }
        view.invalidate()
    }

    /** Bionics blur strength is a window property, not one of the 42 shader parameters. */
    private fun updateWindowRadius(source: View, radius: Int) {
        val root = findWindowView(source) ?: return
        val small = radius.coerceIn(0, 100)
        if (appliedWindowRadii[root] == small) return
        val big = (small * 10).coerceIn(0, 1000)
        runCatching {
            findMethod(
                root.javaClass,
                "setMiGlassBlurRadius",
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
            )?.invoke(root, small, big)
            appliedWindowRadii[root] = small
        }.onFailure { error ->
            logWarn("$TAG blur radius unavailable: ${error.message}")
        }
    }

    private fun restoreWindowRadius(root: View) {
        if (appliedWindowRadii.remove(root) == null) return
        runCatching {
            findMethod(
                root.javaClass,
                "setMiGlassBlurRadius",
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
            )?.invoke(root, SYSTEM_SMALL_BLUR_RADIUS, SYSTEM_BIG_BLUR_RADIUS)
        }
    }

    private fun hasManagedView(root: View): Boolean {
        val views = synchronized(managedViews) { managedViews.toList() }
        return views.any { findWindowView(it) === root }
    }

    private fun hasVisibleManagedView(root: View): Boolean {
        val views = synchronized(managedViews) { managedViews.toList() }
        return views.any { view ->
            view.isAttachedToWindow && view.isShown && view.alpha > 0.01f &&
                findWindowView(view) === root
        }
    }

    private fun currentVisibleConfig(root: View): SoftGlassConfig? {
        val views = synchronized(managedViews) { managedViews.toList() }
        return views.asSequence()
            .filter { view ->
                view.isAttachedToWindow && view.isShown && view.alpha > 0.01f &&
                    findWindowView(view) === root
            }
            .maxByOrNull(View::getAlpha)
            ?.let(appliedConfigs::get)
    }

    private fun ensureWindowBlur(source: View) {
        val root = findWindowView(source) ?: return
        if (retainedWindowBlurRoots.contains(root)) return
        val method = findMethod(
            root.javaClass,
            "updateWindowBlur",
            Boolean::class.javaPrimitiveType!!,
        ) ?: return
        runCatching { method.invoke(root, true) }
        retainedWindowBlurRoots.add(root)
    }

    private fun closeRetainedWindowBlur(root: View) {
        if (!retainedWindowBlurRoots.remove(root)) return
        val method = findMethod(
            root.javaClass,
            "updateWindowBlur",
            Boolean::class.javaPrimitiveType!!,
        ) ?: return
        runCatching { method.invoke(root, false) }
    }

    private fun ensurePassWindowBlur(source: View) {
        val root = findWindowView(source) ?: return
        if (retainedPassBlurRoots.contains(root)) return
        val method = findMethod(
            root.javaClass,
            "updatePassWindowBlur",
            Boolean::class.javaPrimitiveType!!,
        ) ?: return
        runCatching { method.invoke(root, true) }
        retainedPassBlurRoots.add(root)
    }

    private fun closeRetainedPassBlur(root: View) {
        if (!retainedPassBlurRoots.remove(root)) return
        val method = findMethod(
            root.javaClass,
            "updatePassWindowBlur",
            Boolean::class.javaPrimitiveType!!,
        ) ?: return
        runCatching { method.invoke(root, false) }
    }

    private fun findWindowView(view: View): View? {
        var current = view
        while (true) {
            if (current.javaClass.name == WINDOW_VIEW_CLASS) return current
            current = current.parent as? View ?: return null
        }
    }

    private fun installOutline(view: View) {
        val maxRadius = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            32f,
            view.resources.displayMetrics,
        )
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(target: View, outline: Outline) {
                outline.setRoundRect(
                    0,
                    0,
                    target.width,
                    target.height,
                    minOf(target.height / 2f, maxRadius),
                )
            }
        }
        view.clipToOutline = true
    }

    private fun baseParams(): FloatArray = systemParams?.clone() ?: floatArrayOf(
        0f, 2f, .5f, .8f, .15f, 2.4f, .3f, .2f, 0f, 0f, 0f,
        .06f, .06f, .06f, .6f, .15f, .4f, 1.36f, 1f, 72f, 3.8f,
        80f, 1000f, 1.2f, .6f, -.4f, .6f, -.8f, 1.8f, 1.2f, 1f,
        1.1764706f, 3f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
    )

    private fun customizeParams(source: FloatArray, config: SoftGlassConfig): FloatArray {
        val params = source.clone()
        fun scale(index: Int, configured: Double) {
            val original = params[index]
            params[index] = if (original == 0f) configured.toFloat()
            else original * (1f + configured.toFloat() / 100f)
        }
        scale(4, config.softLight)
        params[5] = (1f + config.saturation.toFloat() / 100f).coerceIn(.5f, 1.5f)
        scale(6, config.brightness)
        scale(7, config.darker)
        scale(21, config.edgeThickness)
        scale(24, config.reflection)
        scale(28, config.directionalLightIntensity)
        scale(32, config.refraction)
        scale(33, config.backgroundSaturation)
        scale(34, config.backgroundBrightness)
        scale(35, config.burn)
        if (!config.highlight) {
            params[24] = 0f
            params[28] = 0f
        }
        // Xiaomi's expanded token also mixes a fixed white inner layer through channels 15/16.
        // Keeping it after clearing the RGB tint is what makes the island look opaque gray.
        params[15] = 0f
        params[16] = 0f
        val tintAlpha = Color.alpha(config.tintColor) / 255f
        if (tintAlpha > 0f) {
            params[11] = Color.red(config.tintColor) / 255f
            params[12] = Color.green(config.tintColor) / 255f
            params[13] = Color.blue(config.tintColor) / 255f
            params[14] = (tintAlpha * (1f + config.transparency.toFloat() / 100f))
                .coerceIn(0f, 1f)
        } else {
            params[11] = 0f
            params[12] = 0f
            params[13] = 0f
            params[14] = 0f
        }
        return params
    }

    private fun findMethod(clazz: Class<*>, name: String, vararg types: Class<*>): Method? {
        runCatching { return clazz.getMethod(name, *types).apply { isAccessible = true } }
        var current: Class<*>? = clazz
        while (current != null) {
            runCatching {
                return current.getDeclaredMethod(name, *types).apply { isAccessible = true }
            }
            current = current.superclass
        }
        return null
    }
}
