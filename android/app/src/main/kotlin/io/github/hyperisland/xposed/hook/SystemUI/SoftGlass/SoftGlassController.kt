package io.github.hyperisland.xposed.hook.SystemUI.SoftGlass

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.View
import android.view.ViewOutlineProvider
import io.github.hyperisland.xposed.logWarn
import io.github.hyperisland.xposed.log
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
    private val hookedTokenClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    )
    private val hookedWindowClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    )

    @Volatile
    private var systemParams: FloatArray? = null

    @Volatile
    private var blurMethods: BlurMethods? = null

    @Volatile
    private var isBionicsActiveMethod: Method? = null

    @Volatile
    private var retainPassWindowBlur = false

    @Volatile
    private var lastSystemPassWindowBlur = false

    @Volatile
    private var sessionWindow: WeakReference<View>? = null

    private val tokenConfigOverride = ThreadLocal<SoftGlassConfig>()

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
        isBionicsActiveMethod = runCatching {
            val styleClass = Class.forName(
                "miui.systemui.util.MiBackgroundStyle",
                false,
                contentClass.classLoader,
            )
            findMethod(styleClass, "isBionicsActive", Context::class.java)
        }.getOrNull()
        blurMethods = BlurMethods(
            setViewMode = findMethod(
                compatClass,
                "setMiViewBlurModeCompat",
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
        log(
            "$TAG runtime bound params=${systemParams?.size ?: -1} " +
                "bionicsCheck=${isBionicsActiveMethod != null} " +
                "viewMode=${blurMethods?.setViewMode != null} " +
                "passWindow=${blurMethods?.setPassWindowBlur != null}",
        )
    }

    /**
     * Lets SystemUI keep its own pass-window state while extending sampling only for
     * module-provided SMALL/BIG Bionics materials. The system's bookkeeping still runs first.
     */
    fun hookWindowLifecycle(module: XposedModule, windowViewClass: Class<*>) {
        if (!hookedWindowClasses.add(windowViewClass)) return
        findMethod(
            windowViewClass,
            "updatePassWindowBlur",
            Boolean::class.javaPrimitiveType!!,
        )?.let { method ->
            module.hook(method).intercept { chain ->
                val requested = chain.args.getOrNull(0) as? Boolean ?: false
                val result = chain.proceed()
                val root = chain.thisObject as? View ?: return@intercept result
                sessionWindow = WeakReference(root)
                lastSystemPassWindowBlur = requested
                enforcePassWindowBlur(root)
                result
            }
        }
    }

    fun setPassWindowRetention(enabled: Boolean, source: View? = null) {
        retainPassWindowBlur = enabled
        val root = source?.let(::findWindowView) ?: sessionWindow?.get() ?: return
        sessionWindow = WeakReference(root)
        enforcePassWindowBlur(root)
    }

    /**
     * Customizes only SystemUI's own EXPANDED_GLASS_TOKEN result. The system remains
     * responsible for applying/reapplying the material during every notification update.
     */
    fun hookExpandedTokenParams(module: XposedModule, contentClass: Class<*>) {
        val token = runCatching {
            contentClass.getDeclaredField("EXPANDED_GLASS_TOKEN").apply {
                isAccessible = true
            }.get(null)
        }.getOrNull() ?: return
        val tokenClass = token.javaClass
        if (!hookedTokenClasses.add(tokenClass)) return
        val getter = findMethod(tokenClass, "getToBionicsParams") ?: return
        module.hook(getter).intercept { chain ->
            val result = chain.proceed()
            val params = result as? FloatArray ?: return@intercept result
            val config = tokenConfigOverride.get()
            if (chain.thisObject === token && config != null) {
                val customized = customizeParams(params, config)
                log("$TAG expanded token ${summarize(customized)}")
                customized
            } else {
                result
            }
        }
    }

    fun <T> withSystemTokenConfig(config: SoftGlassConfig, block: () -> T): T {
        tokenConfigOverride.set(config)
        return try {
            block()
        } finally {
            tokenConfigOverride.remove()
        }
    }

    /** Applies native soft glass to a stable or fake island View. */
    fun apply(
        view: View,
        config: SoftGlassConfig,
        preserveSystemOutline: Boolean = false,
    ): Boolean = runCatching {
        val bionicsActive = isSystemBionicsActive(view)
        if (!bionicsActive) {
            log("$TAG skip apply bionics=false view=${view.javaClass.name}")
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

        if (!managedViews.contains(view)) {
            stockBackgrounds[view] = view.background
        }
        blurMethods?.setViewMode?.invoke(null, view, 1)
        if (!preserveSystemOutline) installFallbackOutline(view)
        val params = customizeParams(systemParams ?: defaultParams(), config)
        setMaterial.invoke(view, 1)
        setGlass.invoke(view, params)
        val window = findWindowView(view)
        val radiusApplied = window != null && runCatching {
            findMethod(
                window.javaClass,
                "setMiGlassBlurRadius",
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
            )?.let { method ->
                method.invoke(window, config.blurRadius, config.blurRadius)
                true
            } ?: false
        }.getOrDefault(false)
        view.background = null
        managedViews.add(view)
        view.invalidate()
        log(
            "$TAG apply success view=${view.javaClass.name} outline=$preserveSystemOutline " +
                "material=${setMaterial.declaringClass.name} glass=${setGlass.declaringClass.name} " +
                "radius=${config.blurRadius}/$radiusApplied " + summarize(params),
        )
        true
    }.getOrElse {
        logWarn("$TAG native soft glass unavailable: ${it.message}")
        false
    }

    fun isManaged(view: View): Boolean = managedViews.contains(view)

    fun isSystemBionicsActive(view: View): Boolean = runCatching {
        isBionicsActiveMethod?.invoke(null, view.context) as? Boolean
    }.getOrNull() == true

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

    private fun enforcePassWindowBlur(root: View) {
        val enabled = lastSystemPassWindowBlur || retainPassWindowBlur
        blurMethods?.let { methods ->
            runCatching { methods.setPassWindowBlur?.invoke(null, root, enabled) }
            runCatching { methods.setPassFps?.invoke(null, root, if (enabled) 60 else -1) }
        }
    }

    private fun findWindowView(view: View): View? {
        var current = view
        while (true) {
            if (current.javaClass.name == WINDOW_VIEW_CLASS) return current
            current = current.parent as? View ?: return null
        }
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
                original * (1f + configured.toFloat() / 100f)
            }
        }
        apply(4, config.softLight)
        apply(5, config.saturation)
        apply(6, config.brightness)
        apply(7, config.darker)
        apply(21, config.edgeThickness)
        apply(24, config.reflection)
        apply(28, config.directionalLightIntensity)
        apply(32, config.refraction)
        apply(33, config.backgroundSaturation)
        apply(34, config.backgroundBrightness)
        apply(35, config.burn)
        if (!config.highlight) {
            // Bionics builds the edge highlight from both the reflection lobe and
            // directional light. Refraction is independently controlled by index 32.
            params[24] = 0f
            params[28] = 0f
        }

        // EXPANDED_GLASS_TOKEN contains Xiaomi's gray inner mix (.06/.06/.06/.6).
        // The module's default is transparent; the native gray must not survive as a
        // second dark layer. User blend color/opacity exclusively owns channels 11..14.
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

    private fun summarize(params: FloatArray): String {
        fun value(index: Int) = params.getOrNull(index)?.toString() ?: "missing"
        return "size=${params.size} p4=${value(4)} p5=${value(5)} p6=${value(6)} " +
            "p7=${value(7)} rgb=${value(11)},${value(12)},${value(13)} " +
            "p14=${value(14)} p21=${value(21)} p24=${value(24)} p28=${value(28)} " +
            "p32=${value(32)} p33=${value(33)} p34=${value(34)} p35=${value(35)}"
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
        val setPassWindowBlur: Method?,
        val setPassFps: Method?,
    )
}
