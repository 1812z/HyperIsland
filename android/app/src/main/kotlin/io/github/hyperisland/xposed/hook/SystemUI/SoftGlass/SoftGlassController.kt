package io.github.hyperisland.xposed.hook.SystemUI.SoftGlass

import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.View
import android.view.ViewOutlineProvider
import io.github.hyperisland.xposed.log
import io.github.hyperisland.xposed.logWarn
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

/**
 * Owns the complete HyperOS 4 native soft-glass pipeline.
 *
 * Callers only provide a [SoftGlassConfig] and lifecycle signals. Xiaomi reflection,
 * Bionics parameter conversion, managed View state, and pass-window blur retention
 * intentionally stay behind this boundary.
 */
internal object SoftGlassController {
    private const val TAG = "HyperIsland[SoftGlass]"
    private const val WINDOW_VIEW_CLASS =
        "miui.systemui.dynamicisland.window.DynamicIslandWindowView"

    private val managedViews = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    )
    private val stockBackgrounds = Collections.synchronizedMap(
        WeakHashMap<View, Drawable?>()
    )
    private val hookedWindowClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    )

    @Volatile
    private var systemParams: FloatArray? = null

    @Volatile
    private var blurMethods: BlurMethods? = null

    @Volatile
    private var sessionConfig: SoftGlassConfig? = null

    @Volatile
    private var sessionWindow: WeakReference<View>? = null

    /** Resolves the Xiaomi APIs for one SystemUI plugin class loader. */
    fun bindRuntime(contentClass: Class<*>, compatClass: Class<*>) {
        if (systemParams == null) {
            systemParams = runCatching {
                val tokenField = contentClass.getDeclaredField("EXPANDED_GLASS_TOKEN").apply {
                    isAccessible = true
                }
                val token = tokenField.get(null)
                val toParams = findMethod(token.javaClass, "getToBionicsParams")
                    ?: return@runCatching null
                (toParams.invoke(token) as? FloatArray)?.clone()
            }.getOrNull()
        }
        blurMethods = BlurMethods(
            setViewMode = findMethod(
                compatClass,
                "setMiViewBlurModeCompat",
                View::class.java,
                Int::class.javaPrimitiveType!!,
            ),
            clearBlend = findMethod(
                compatClass,
                "clearMiBackgroundBlendColorCompat",
                View::class.java,
            ),
            setBackgroundMode = findMethod(
                compatClass,
                "setMiBackgroundBlurModeCompat",
                View::class.java,
                Int::class.javaPrimitiveType!!,
            ),
            setBackgroundRadius = findMethod(
                compatClass,
                "setMiBackgroundBlurRadiusCompat",
                View::class.java,
                Int::class.javaPrimitiveType!!,
            ),
            setPassWindowBlur = findMethod(
                compatClass,
                "setPassWindowBlurEnabledCompat",
                View::class.java,
                Boolean::class.javaPrimitiveType!!,
            ),
            setPassFps = findMethod(
                compatClass,
                "setMiPassBlurFps",
                View::class.java,
                Int::class.javaPrimitiveType!!,
            ),
        )
    }

    /** Keeps Xiaomi from disabling backdrop sampling after EXPAND collapses. */
    fun hookWindowLifecycle(module: XposedModule, windowViewClass: Class<*>) {
        if (!hookedWindowClasses.add(windowViewClass)) return

        findMethod(
            windowViewClass,
            "updatePassWindowBlur",
            Boolean::class.javaPrimitiveType!!,
        )?.let { method ->
            module.hook(method).intercept { chain ->
                if (retainWindow(chain.thisObject as? View, "updatePassWindowBlur")) {
                    null
                } else {
                    chain.proceed()
                }
            }
        }

        findMethod(
            windowViewClass,
            "updateWindowBlur",
            Boolean::class.javaPrimitiveType!!,
        )?.let { method ->
            module.hook(method).intercept { chain ->
                val result = chain.proceed()
                retainWindow(chain.thisObject as? View, "updateWindowBlur")
                result
            }
        }
    }

    /** Applies native soft glass to a stable or fake island View. */
    fun apply(view: View, config: SoftGlassConfig): Boolean = runCatching {
        val setMaterial = findMethod(
            view.javaClass,
            "setMiViewMaterialType",
            Int::class.javaPrimitiveType!!,
        ) ?: return@runCatching false
        val setGlass = findMethod(view.javaClass, "setMiGlass", FloatArray::class.java)
            ?: return@runCatching false

        if (!managedViews.contains(view)) {
            stockBackgrounds[view] = view.background
        }
        val methods = blurMethods
        methods?.setViewMode?.invoke(null, view, 1)
        methods?.clearBlend?.invoke(null, view)
        installFallbackOutline(view)
        setMaterial.invoke(view, 1)
        setGlass.invoke(view, customizeParams(systemParams ?: defaultParams(), config))
        enableWindowBlur(view, config)
        view.background = null
        managedViews.add(view)
        view.invalidate()
        true
    }.getOrElse {
        logWarn("$TAG native soft glass unavailable: ${it.message}")
        false
    }

    fun activateWindowSession(config: SoftGlassConfig) {
        sessionConfig = config
    }

    fun clearWindowSession() {
        sessionConfig = null
        val root = sessionWindow?.get()
        if (root != null) disableWindowBlur(root)
        sessionWindow = null
    }

    fun isManaged(view: View): Boolean = managedViews.contains(view)

    /** Releases a View owned by this renderer and restores its captured background when needed. */
    fun release(view: View) {
        if (!managedViews.remove(view)) return
        clearMaterial(view)
        val stock = stockBackgrounds.remove(view)
        if (view.background == null) view.background = stock
    }

    /** Drops ownership after SystemUI has already installed the next material/background. */
    fun onSystemMaterialReplaced(view: View) {
        managedViews.remove(view)
        stockBackgrounds.remove(view)
    }

    private fun retainWindow(root: View?, source: String): Boolean {
        val config = sessionConfig ?: return false
        root ?: return false
        return runCatching {
            sessionWindow = WeakReference(root)
            enableWindowBlur(root, config)
            log("$TAG retained pass-window blur before $source")
            true
        }.getOrElse { error ->
            logWarn("$TAG $source retain failed safely: ${error.message}")
            false
        }
    }

    private fun enableWindowBlur(view: View, config: SoftGlassConfig) {
        val root = findWindowView(view) ?: return
        sessionWindow = WeakReference(root)
        blurMethods?.let { methods ->
            runCatching { methods.setPassWindowBlur?.invoke(null, root, true) }
            runCatching { methods.setPassFps?.invoke(null, root, 60) }
            runCatching { methods.setBackgroundMode?.invoke(null, root, 1) }
            runCatching { methods.setBackgroundRadius?.invoke(null, root, 0) }
        }
        runCatching {
            findMethod(
                root.javaClass,
                "setMiGlassBlurRadius",
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
            )?.invoke(root, config.blurRadius, config.blurRadius)
        }
    }

    private fun disableWindowBlur(root: View) {
        blurMethods?.let { methods ->
            runCatching { methods.setPassWindowBlur?.invoke(null, root, false) }
            runCatching { methods.setPassFps?.invoke(null, root, -1) }
            runCatching { methods.setBackgroundMode?.invoke(null, root, 0) }
            runCatching { methods.setBackgroundRadius?.invoke(null, root, 0) }
        }
    }

    private fun findWindowView(view: View): View? {
        var root = view
        while (root.parent is View) {
            root = root.parent as View
            if (root.javaClass.name == WINDOW_VIEW_CLASS) return root
        }
        return root.takeIf { it.javaClass.name == WINDOW_VIEW_CLASS }
    }

    private fun installFallbackOutline(view: View) {
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

    private fun customizeParams(source: FloatArray, config: SoftGlassConfig): FloatArray {
        val params = source.clone()
        fun apply(index: Int, configured: Double) {
            val original = params[index]
            params[index] = if (original == 0f) {
                configured.toFloat()
            } else {
                original * (1f + configured.toFloat() / 10f)
            }
        }
        apply(4, config.softLight)
        apply(5, config.saturation)
        apply(6, config.brightness)
        apply(7, config.darker)
        apply(14, config.transparency)
        apply(21, config.edgeThickness)
        apply(24, config.reflection)
        apply(28, config.directionalLightIntensity)
        apply(32, config.refraction)
        apply(33, config.backgroundSaturation)
        apply(34, config.backgroundBrightness)
        apply(35, config.burn)
        if (!config.highlight) params[24] = 0f

        if (Color.alpha(config.tintColor) > 0) {
            val tintWeight = Color.alpha(config.tintColor) / 255f
            fun mixInnerColor(index: Int, target: Float) {
                params[index] = params[index] * (1f - tintWeight) + target * tintWeight
            }
            mixInnerColor(11, Color.red(config.tintColor) / 255f)
            mixInnerColor(12, Color.green(config.tintColor) / 255f)
            mixInnerColor(13, Color.blue(config.tintColor) / 255f)
        }
        return params
    }

    private fun clearMaterial(view: View) {
        runCatching {
            findMethod(
                view.javaClass,
                "setMiViewMaterialType",
                Int::class.javaPrimitiveType!!,
            )?.invoke(view, -1)
        }
        view.invalidate()
    }

    private fun findMethod(clazz: Class<*>, name: String, vararg types: Class<*>): Method? {
        runCatching {
            return clazz.getMethod(name, *types).apply { isAccessible = true }
        }
        var current: Class<*>? = clazz
        while (current != null) {
            runCatching {
                return current.getDeclaredMethod(name, *types).apply { isAccessible = true }
            }
            current = current.superclass
        }
        return null
    }

    private fun defaultParams() = floatArrayOf(
        0f, 2f, .5f, .8f, .15f, 2.4f, .3f, .2f, 0f, 0f, 0f,
        .06f, .06f, .06f, .6f, .15f, .4f, 1.36f, 1f, 72f, 3.8f,
        80f, 1000f, 1.2f, .6f, -.4f, .6f, -.8f, 1.8f, 1.2f, 1f,
        1.1764706f, 3f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
    )

    private data class BlurMethods(
        val setViewMode: Method?,
        val clearBlend: Method?,
        val setBackgroundMode: Method?,
        val setBackgroundRadius: Method?,
        val setPassWindowBlur: Method?,
        val setPassFps: Method?,
    )
}
