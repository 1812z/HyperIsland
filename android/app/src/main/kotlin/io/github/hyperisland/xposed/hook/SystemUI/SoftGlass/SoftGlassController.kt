package io.github.hyperisland.xposed.hook.SystemUI.SoftGlass

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.view.ViewOutlineProvider
import io.github.hyperisland.xposed.logWarn
import io.github.hyperisland.xposed.log
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

/**
 * Owns the complete HyperOS 4 native soft-glass pipeline.
 *
 * Callers only provide a [SoftGlassConfig] and lifecycle signals. Xiaomi reflection,
 * Bionics parameter conversion and managed View state intentionally stay behind this boundary.
 * SystemUI still owns window/pass-window sampling; this controller only prevents its settled
 * SMALL/BIG shutdown while a visible module Bionics layer still consumes that sampler.
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
    private val appliedParams = Collections.synchronizedMap(
        WeakHashMap<View, FloatArray>()
    )
    private val windowBlurRadii = Collections.synchronizedMap(
        WeakHashMap<View, Int>()
    )
    private val hookedWindowClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    )
    private val hookedStyleClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    )
    private val passWindowRoots = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    )
    private val pendingPassWindowRoots = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    )
    private val pendingPassWindowCloseRoots = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    )
    private val refreshedPassWindowRoots = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<View, Boolean>())
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

    /** Installs the two window-boundary hooks used by native soft glass. */
    fun hookWindowLifecycle(module: XposedModule, windowViewClass: Class<*>) {
        if (!hookedWindowClasses.add(windowViewClass)) return
        hookMaterialOwnership(module, windowViewClass.classLoader)
        hookPassWindowLifecycle(module, windowViewClass)
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

    /**
     * Protects managed island Views at SystemUI's two common Bionics writer methods.
     * This substitutes a conflicting stock write before it reaches View/RenderNode; it never
     * schedules a second setMiGlass transaction and does not affect non-island SystemUI surfaces.
     */
    private fun hookMaterialOwnership(module: XposedModule, classLoader: ClassLoader) {
        val styleClass = runCatching {
            Class.forName("miui.systemui.util.MiBackgroundStyle", false, classLoader)
        }.getOrNull() ?: return
        if (!hookedStyleClasses.add(styleClass)) return

        findMethod(
            styleClass,
            "setMiViewMaterialType",
            View::class.java,
            Int::class.javaPrimitiveType!!,
        )?.let { method ->
            runCatching {
                module.hook(method).intercept { chain ->
                    val view = chain.args.getOrNull(0) as? View
                    val type = chain.args.getOrNull(1) as? Int
                    if (view != null && type != 1 && managedViews.contains(view)) {
                        return@intercept chain.proceed(arrayOf(view, 1))
                    }
                    chain.proceed()
                }
            }.onFailure {
                logWarn("$TAG material-type ownership hook unavailable: ${it.message}")
            }
        }
        findMethod(
            styleClass,
            "setMiGlassCompat",
            View::class.java,
            FloatArray::class.java,
        )?.let { method ->
            runCatching {
                module.hook(method).intercept { chain ->
                    val view = chain.args.getOrNull(0) as? View
                    val params = view?.let(appliedParams::get)
                    if (view != null && params != null && managedViews.contains(view)) {
                        return@intercept chain.proceed(arrayOf(view, params))
                    }
                    chain.proceed()
                }
            }.onFailure {
                logWarn("$TAG glass ownership hook unavailable: ${it.message}")
            }
        }
    }

    /**
     * Keeps one continuous native sampler session for visible SOFT layers.
     *
     * SystemUI normally turns pass-window off after settling in SMALL/BIG. A module Bionics layer
     * still consumes that sampler, so keep one continuous session until the last visible SOFT View
     * disappears. Crucially, the native flag and SystemUI's lastPassWindowBlurEnabled cache always
     * remain identical: there is no false -> true rebuild and therefore no white frame or flash.
     */
    private fun hookPassWindowLifecycle(module: XposedModule, windowViewClass: Class<*>) {
        findMethod(
            windowViewClass,
            "updatePassWindowBlur",
            Boolean::class.javaPrimitiveType!!,
        )?.let { method ->
            runCatching {
                module.hook(method).intercept { chain ->
                    val requested = chain.args.getOrNull(0) as? Boolean ?: false
                    val root = chain.thisObject as? View ?: return@intercept chain.proceed()
                    if (!requested && isSystemBionicsActive(root) &&
                        hasVisibleManagedView(root)
                    ) {
                        if (!passWindowRoots.contains(root)) {
                            // Startup/restoration may reach the stock final false without a prior
                            // scheduled animation. Turn that same source transaction into the one
                            // native open; do not post a corrective write after it.
                            val result = chain.proceed(arrayOf(true))
                            passWindowRoots.add(root)
                            pendingPassWindowRoots.remove(root)
                            return@intercept result
                        }
                        passWindowRoots.add(root)
                        return@intercept null
                    }
                    val result = chain.proceed()
                    if (requested && isSystemBionicsActive(root)) {
                        passWindowRoots.add(root)
                    } else if (!requested) {
                        passWindowRoots.remove(root)
                    }
                    result
                }
            }.onFailure {
                logWarn("$TAG pass-window hook unavailable: ${it.message}")
            }
        }
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
        clearSelfBlur(view)
        managedViews.add(view)
        appliedConfigs[view] = config
        appliedParams[view] = params
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

    /** Clears Xiaomi's independent RenderNode self-blur without touching the glass material. */
    fun clearSelfBlur(view: View) {
        runCatching { setMiSelfBlurMethod?.invoke(view, 0, null) }
    }

    fun isSystemBionicsActive(view: View): Boolean = runCatching {
        isBionicsActiveMethod?.invoke(null, view.context) as? Boolean
    }.getOrNull() == true

    /** The radius belongs to DynamicIslandWindowView, so only the settled real-state path calls it. */
    fun updateWindowBlurRadius(source: View, radius: Int) {
        val root = findWindowView(source) ?: return
        setWindowBlurRadius(root, radius, force = false)
    }

    private fun setWindowBlurRadius(root: View, radius: Int, force: Boolean) {
        val value = radius.coerceAtLeast(0)
        if (!force && windowBlurRadii[root] == value) return
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

    /**
     * Reconciles native window properties after SystemUI exposes one cached fake slot.
     *
     * View visibility/surface reuse can recreate RenderNode state without changing
     * lastPassWindowBlurEnabled. Re-enter the system method to keep its cache correct, then
     * idempotently reassert only the two window-owned properties. The per-View glass material is
     * deliberately untouched, so its edge/refraction effect cannot restart or flash.
     */
    fun refreshPassWindowBlur(source: View) {
        if (!source.isAttachedToWindow || !source.isShown || !isSystemBionicsActive(source)
        ) {
            return
        }
        val root = findWindowView(source) ?: return
        if (!refreshedPassWindowRoots.add(root)) return
        if (!root.post { refreshedPassWindowRoots.remove(root) }) {
            refreshedPassWindowRoots.remove(root)
        }
        val method = findMethod(
            root.javaClass,
            "updatePassWindowBlur",
            Boolean::class.javaPrimitiveType!!,
        ) ?: return
        runCatching {
            val retainedSession = passWindowRoots.contains(root)
            method.invoke(root, true)
            val passApplied = !retainedSession || reassertNativePassWindow(root)
            appliedConfigs[source]?.let { config ->
                setWindowBlurRadius(root, config.blurRadius, force = true)
            }
            passWindowRoots.add(root)
            log(
                "$TAG sampler refresh view=${source.javaClass.name} " +
                    "pass=$passApplied radius=${appliedConfigs[source]?.blurRadius}",
            )
        }.onFailure {
            refreshedPassWindowRoots.remove(root)
            logWarn("$TAG sampler refresh unavailable: ${it.message}")
        }
    }

    private fun reassertNativePassWindow(root: View): Boolean {
        val methods = blurMethods ?: return false
        val passApplied = runCatching {
            methods.setPassWindowBlur?.invoke(null, root, true) as? Boolean
        }.getOrNull() == true
        runCatching { methods.setPassFps?.invoke(null, root, 60) }
        return passApplied
    }

    /** Opens the native sampler once when a prepared SOFT state actually becomes visible. */
    fun ensurePassWindowBlur(source: View) {
        if (!source.isAttachedToWindow || !source.isShown || source.alpha <= 0.01f ||
            !isSystemBionicsActive(source)
        ) {
            return
        }
        val root = findWindowView(source) ?: return
        if (passWindowRoots.contains(root) || !pendingPassWindowRoots.add(root)) return
        val open = Runnable {
            pendingPassWindowRoots.remove(root)
            if (passWindowRoots.contains(root) || !hasVisibleManagedView(root)) return@Runnable
            val method = findMethod(
                root.javaClass,
                "updatePassWindowBlur",
                Boolean::class.javaPrimitiveType!!,
            ) ?: return@Runnable
            runCatching { method.invoke(root, true) }
                .onFailure {
                    passWindowRoots.remove(root)
                    logWarn("$TAG cannot open native sampler: ${it.message}")
                }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            open.run()
        } else if (!root.post(open)) {
            pendingPassWindowRoots.remove(root)
        }
    }

    private fun closePassWindowIfIdle(root: View) {
        if (!passWindowRoots.contains(root) || !pendingPassWindowCloseRoots.add(root)) return
        val close = Runnable {
            pendingPassWindowCloseRoots.remove(root)
            if (!passWindowRoots.contains(root) || hasVisibleManagedView(root)) return@Runnable
            findMethod(
                root.javaClass,
                "updatePassWindowBlur",
                Boolean::class.javaPrimitiveType!!,
            )?.let { method ->
                runCatching { method.invoke(root, false) }
            }
        }
        // Release and replacement can occur in one SystemUI transaction. Recheck after that
        // transaction so an A -> B handoff never creates a false -> true sampler cycle.
        if (!root.post(close)) pendingPassWindowCloseRoots.remove(root)
    }

    /** Releases a View owned by this renderer and restores its captured background when needed. */
    fun release(view: View, restoreBackground: Boolean = true) {
        if (!managedViews.remove(view)) return
        val root = findWindowView(view)
        clearMaterial(view)
        val stock = stockBackgrounds.remove(view)
        appliedConfigs.remove(view)
        appliedParams.remove(view)
        if (restoreBackground && view.background == null) view.background = stock
        if (root != null) closePassWindowIfIdle(root)
    }

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
