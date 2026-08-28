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
import java.lang.reflect.Field
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
    private val appliedConfigs = Collections.synchronizedMap(
        WeakHashMap<View, SoftGlassConfig>()
    )
    private val windowBlurRadii = Collections.synchronizedMap(
        WeakHashMap<View, Int>()
    )
    private val windowPassStates = Collections.synchronizedMap(
        WeakHashMap<View, Boolean>()
    )
    private val hookedWindowClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    )
    private val setMiSelfBlurMethod: Method? by lazy {
        runCatching {
            View::class.java.getMethod(
                "setMiSelfBlur",
                Int::class.javaPrimitiveType!!,
                ArrayList::class.java,
            ).apply { isAccessible = true }
        }.getOrNull()
    }

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
            clearBlend = findMethod(
                compatClass,
                "clearMiBackgroundBlendColorCompat",
                View::class.java,
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
     * Keeps SystemUI's pass-window switch stable while a module Bionics layer is visible.
     *
     * finalizeAnimFinished() requests false whenever EXPAND ends, even when a module-owned BIG
     * remains on screen. Letting that false write run and turning it back on afterwards resets
     * the shared sampler and flashes the otherwise stationary BIG. Suppress that source write;
     * once the last managed layer is hidden, SystemUI's next false request proceeds normally.
     */
    fun hookWindowLifecycle(module: XposedModule, windowViewClass: Class<*>) {
        if (!hookedWindowClasses.add(windowViewClass)) return
        val lastPassWindowBlurField = findField(
            windowViewClass,
            "lastPassWindowBlurEnabled",
        )
        findMethod(
            windowViewClass,
            "updatePassWindowBlur",
            Boolean::class.javaPrimitiveType!!,
        )?.let { method ->
            module.hook(method).intercept { chain ->
                val requested = chain.args.getOrNull(0) as? Boolean ?: false
                val root = chain.thisObject as? View ?: return@intercept chain.proceed()
                sessionWindow = WeakReference(root)
                lastSystemPassWindowBlur = requested
                if (!requested &&
                    isSystemBionicsActive(root) &&
                    (retainPassWindowBlur || hasVisibleManagedView(root))
                ) {
                    // Keep the native sampler alive for module-owned SMALL/BIG, but retain the
                    // logical false transition. If the field stays true, the next stock true
                    // request is skipped and EXPAND renders as an opaque self-blur layer.
                    val logicalStateReset = runCatching {
                        lastPassWindowBlurField?.set(root, false)
                        lastPassWindowBlurField != null
                    }.getOrDefault(false)
                    if (!logicalStateReset) {
                        // Compatibility fallback for a renamed OS build: let SystemUI update its
                        // field, then restore the sampler in the same UI transaction.
                        chain.proceed()
                    }
                    windowPassStates.remove(root)
                    enforcePassWindowBlur(root)
                    log(
                        "$TAG preserve pass-window false request " +
                            "retention=$retainPassWindowBlur visible=${hasVisibleManagedView(root)} " +
                            "logicalReset=$logicalStateReset",
                    )
                    return@intercept null
                }
                val result = chain.proceed()
                windowPassStates[root] = requested && isSystemBionicsActive(root)
                result
            }
        }
        findMethod(
            windowViewClass,
            "updateWindowBlur",
            Boolean::class.javaPrimitiveType!!,
        )?.let { method ->
            module.hook(method).intercept { chain ->
                val result = chain.proceed()
                val root = chain.thisObject as? View ?: return@intercept result
                // SystemUI has just restored 50/500. Invalidate our cache and reassert the
                // currently visible state's single window-owned radius, if one exists.
                windowBlurRadii.remove(root)
                val current = synchronized(managedViews) { managedViews.toList() }
                    .asSequence()
                    .filter { view ->
                        view.isAttachedToWindow && view.isShown && view.alpha > 0.01f &&
                            findWindowView(view) === root
                    }
                    .maxByOrNull { it.alpha }
                val config = current?.let(appliedConfigs::get)
                if (current != null && config != null) {
                    updateWindowBlurRadius(current, config.blurRadius)
                }
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
     * Installs the transparent Bionics material directly on the state View.
     *
     * IslandBlurHook replaces SystemUI's updateBackgroundBg() at its source for SOFT, so this
     * method must be complete and idempotent: notification updates with unchanged parameters do
     * not resubmit setMiGlass and therefore cannot restart the edge-highlight RenderNode effect.
     */
    fun apply(
        view: View,
        config: SoftGlassConfig,
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

        val newlyManaged = !managedViews.contains(view)
        if (!newlyManaged && appliedConfigs[view] == config) {
            if (view.background != null) view.background = null
            ensurePassWindowBlur(view)
            return@runCatching true
        }
        if (newlyManaged) {
            stockBackgrounds[view] = view.background
            installFallbackOutline(view)
        }
        blurMethods?.setViewMode?.invoke(null, view, 1)
        blurMethods?.clearBlend?.invoke(null, view)
        view.background = null
        val params = customizeParams(systemParams ?: defaultParams(), config)
        setMaterial.invoke(view, 1)
        setGlass.invoke(view, params)
        // Clear a self-blur left by the stock transition once when material ownership changes.
        // OS4 frame writers are intercepted at their source, so this must not run every frame.
        runCatching { setMiSelfBlurMethod?.invoke(view, 0, null) }
        managedViews.add(view)
        appliedConfigs[view] = config
        ensurePassWindowBlur(view)
        view.invalidate()
        log(
            "$TAG apply success view=${view.javaClass.name} " +
                "material=${setMaterial.declaringClass.name} glass=${setGlass.declaringClass.name} " +
                "radius=window-owned " + summarize(params),
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

    /** The radius belongs to DynamicIslandWindowView, so only the settled real-state path calls it. */
    fun updateWindowBlurRadius(source: View, radius: Int) {
        val root = findWindowView(source) ?: return
        val value = radius.coerceAtLeast(0)
        if (windowBlurRadii[root] == value) return
        val method = findMethod(
            root.javaClass,
            "setMiGlassBlurRadius",
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
        ) ?: return
        runCatching {
            method.invoke(root, value, value)
            windowBlurRadii[root] = value
        }.onFailure {
            logWarn("$TAG window radius unavailable: ${it.message}")
        }
    }

    /** Enables the sampler as soon as a managed View is actually visible, independent of state. */
    fun ensurePassWindowBlur(source: View) {
        if (!isSystemBionicsActive(source)) return
        val root = findWindowView(source) ?: return
        sessionWindow = WeakReference(root)
        enforcePassWindowBlur(root)
    }

    /** Releases a View owned by this renderer and restores its captured background when needed. */
    fun release(view: View, restoreBackground: Boolean = true) {
        if (!managedViews.remove(view)) return
        val window = findWindowView(view)
        clearMaterial(view)
        val stock = stockBackgrounds.remove(view)
        appliedConfigs.remove(view)
        if (restoreBackground && view.background == null) view.background = stock
        if (window != null) enforcePassWindowBlur(window)
    }

    private fun enforcePassWindowBlur(root: View) {
        val enabled = lastSystemPassWindowBlur || retainPassWindowBlur ||
            hasVisibleManagedView(root)
        if (windowPassStates[root] == enabled) return
        blurMethods?.let { methods ->
            runCatching { methods.setPassWindowBlur?.invoke(null, root, enabled) }
            runCatching { methods.setPassFps?.invoke(null, root, if (enabled) 60 else -1) }
        }
        windowPassStates[root] = enabled
        log("$TAG direct pass-window enabled=$enabled visible=${hasVisibleManagedView(root)}")
    }

    /** One island leaving must not disable the window sampler used by a surviving island. */
    private fun hasVisibleManagedView(root: View): Boolean {
        val snapshot = synchronized(managedViews) { managedViews.toList() }
        return snapshot.any { view ->
            view.isAttachedToWindow && view.isShown && view.alpha > 0.01f &&
                findWindowView(view) === root
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
        // Unlike the other controls, saturation is exposed around the shader's neutral
        // multiplier instead of Xiaomi's heavily saturated island token (2.4):
        // UI 0 = 1.0, -50 = 0.5, +50 = 1.5.
        params[5] = (1f + config.saturation.toFloat() / 100f).coerceIn(.5f, 1.5f)
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
            )?.invoke(view, 0)
        }
        runCatching { blurMethods?.clearBlend?.invoke(null, view) }
        runCatching { blurMethods?.setViewMode?.invoke(null, view, 0) }
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

    private fun findField(clazz: Class<*>, name: String): Field? {
        var current: Class<*>? = clazz
        while (current != null) {
            runCatching {
                return current.getDeclaredField(name).apply { isAccessible = true }
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
        val setPassWindowBlur: Method?,
        val setPassFps: Method?,
    )
}
