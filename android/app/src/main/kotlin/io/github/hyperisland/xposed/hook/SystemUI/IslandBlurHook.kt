package io.github.hyperisland.xposed.hook.SystemUI

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.hyperisland.xposed.hook.IslandBackgroundHook
import io.github.hyperisland.xposed.hook.SystemUI.LiqudGlass.LiquidGlassConfig
import io.github.hyperisland.xposed.hook.SystemUI.LiqudGlass.LiquidGlassDrawable
import io.github.hyperisland.xposed.log
import io.github.hyperisland.xposed.logError
import io.github.hyperisland.xposed.logWarn
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap

/** Applies HyperOS native live background blur independently to each island state. */
object IslandBlurHook : BaseHook() {

    private const val TAG = "HyperIsland[IslandBlur]"
    private const val CONTENT_VIEW_CLASS =
        "miui.systemui.dynamicisland.window.content.DynamicIslandBaseContentView"
    private const val BACKGROUND_VIEW_CLASS =
        "miui.systemui.dynamicisland.DynamicIslandBackgroundView"
    private const val ANIMATION_DELEGATE_CLASS =
        "miui.systemui.dynamicisland.anim.DynamicIslandAnimationDelegate"
    private const val FAKE_VIEW_CLASS =
        "miui.systemui.dynamicisland.window.content.DynamicIslandContentFakeView"
    private const val KEY_SMALL_ENABLED = "pref_island_blur_small_enabled"
    private const val KEY_SMALL_RADIUS = "pref_island_blur_small_radius"
    private const val KEY_SMALL_COLOR = "pref_island_blur_small_color"
    private const val KEY_BIG_ENABLED = "pref_island_blur_big_enabled"
    private const val KEY_BIG_RADIUS = "pref_island_blur_big_radius"
    private const val KEY_BIG_COLOR = "pref_island_blur_big_color"
    private const val KEY_EXPAND_ENABLED = "pref_island_blur_expand_enabled"
    private const val KEY_EXPAND_RADIUS = "pref_island_blur_expand_radius"
    private const val KEY_EXPAND_COLOR = "pref_island_blur_expand_color"
    private const val KEY_GLASS_ENABLED = "pref_island_glass_enabled"
    private const val KEY_GLASS_SMALL_ENABLED = "pref_island_glass_small_enabled"
    private const val KEY_GLASS_BIG_ENABLED = "pref_island_glass_big_enabled"
    private const val KEY_GLASS_EXPAND_ENABLED = "pref_island_glass_expand_enabled"
    private const val KEY_GLASS_EDGE_WIDTH = "pref_island_glass_edge_width"
    private const val KEY_GLASS_REFRACTION = "pref_island_glass_refraction"
    private const val KEY_GLASS_HIGHLIGHT = "pref_island_glass_highlight"
    private const val KEY_GLASS_SHADOW = "pref_island_glass_shadow"
    private const val KEY_GLASS_LIGHT_DIRECTION = "pref_island_glass_light_direction"
    private const val KEY_GLASS_DISPERSION = "pref_island_glass_dispersion"
    private const val KEY_GLASS_GYROSCOPE = "pref_island_glass_gyroscope"
    private const val KEY_GLASS_HDR_HIGHLIGHT = "pref_island_glass_hdr_highlight"
    private const val KEY_GLASS_TRUE_REFRACTION = "pref_island_glass_true_refraction"
    private const val KEY_REFRACTION_SMALL_ENABLED = "pref_island_refraction_small_enabled"
    private const val KEY_REFRACTION_BIG_ENABLED = "pref_island_refraction_big_enabled"
    private const val KEY_REFRACTION_EXPAND_ENABLED = "pref_island_refraction_expand_enabled"
    private const val KEY_GLASS_CAPTURE_FPS = "pref_island_glass_capture_fps"
    private const val KEY_GLASS_CAPTURE_QUALITY = "pref_island_glass_capture_quality"

    private const val DEFAULT_RADIUS = 80
    private const val DEFAULT_BLEND_COLOR = 0x20FFFFFF
    private val hookedContentClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    )
    private val refreshTargets = Collections.synchronizedMap(
        WeakHashMap<View, RefreshTarget>()
    )
    private val outerBlurs = Collections.synchronizedMap(
        WeakHashMap<View, OuterBlur>()
    )
    private val pendingOuterBlurs = Collections.synchronizedMap(
        WeakHashMap<View, PendingBlur>()
    )
    private val recoveringOuterBlurs = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    )
    private val transitionBlurs = Collections.synchronizedMap(
        WeakHashMap<View, TransitionBlur>()
    )
    private val detachListeners = Collections.synchronizedMap(
        WeakHashMap<View, View.OnAttachStateChangeListener>()
    )
    private val mainHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = Runnable { refreshTrackedViews() }
    private val islandTypeHolder = ThreadLocal<IslandType>()

    @Volatile
    private var lastIslandType: IslandType? = null

    @Volatile
    private var configs = BlurConfigs.disabled()

    @Volatile
    private var glassConfig = LiquidGlassConfig.disabled()

    @Volatile
    private var glassStates = GlassStates.disabled()

    @Volatile
    private var glassConfigRevision = 0

    @Volatile
    private var anyBlurEnabled = false

    @Volatile
    private var islandTempHidden = false

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        if (param.packageName != "com.android.systemui") return
        loadConfig()
        hookPlugin(module, param.defaultClassLoader)
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { classLoader ->
            hookPlugin(module, classLoader)
        }
    }

    override fun onConfigChanged() {
        loadConfig()
        mainHandler.post {
            val stale = synchronized(outerBlurs) {
                outerBlurs.entries.mapNotNull { (view, outer) ->
                    if (configForType(outer.owned.type).isActive) null else view to outer
                }
            }
            stale.forEach { (view, outer) ->
                deactivateOuterBlur(view, outer.drawableField, "config-disabled")
            }
            synchronized(pendingOuterBlurs) {
                pendingOuterBlurs.entries.removeAll { (view, pending) ->
                    val remove = !configForType(pending.type).isActive
                    if (remove) recoveringOuterBlurs.remove(view)
                    remove
                }
            }
        }
        mainHandler.removeCallbacks(refreshRunnable)
        if (anyBlurEnabled) {
            mainHandler.postDelayed(refreshRunnable, 80L)
        }
    }

    private fun loadConfig() {
        configs = BlurConfigs(
            small = readConfig(
                KEY_SMALL_ENABLED,
                KEY_SMALL_RADIUS,
                KEY_SMALL_COLOR,
            ),
            big = readConfig(
                KEY_BIG_ENABLED,
                KEY_BIG_RADIUS,
                KEY_BIG_COLOR,
            ),
            expand = readConfig(
                KEY_EXPAND_ENABLED,
                KEY_EXPAND_RADIUS,
                KEY_EXPAND_COLOR,
            ),
        )
        anyBlurEnabled = configs.small.isActive || configs.big.isActive || configs.expand.isActive
        val legacyGlassEnabled = ConfigManager.getBoolean(KEY_GLASS_ENABLED, false)
        val legacyRefractionEnabled = ConfigManager.getBoolean(KEY_GLASS_TRUE_REFRACTION, false)
        glassStates = GlassStates(
            small = readGlassState(
                KEY_GLASS_SMALL_ENABLED,
                KEY_REFRACTION_SMALL_ENABLED,
                legacyGlassEnabled,
                legacyRefractionEnabled,
            ),
            big = readGlassState(
                KEY_GLASS_BIG_ENABLED,
                KEY_REFRACTION_BIG_ENABLED,
                legacyGlassEnabled,
                legacyRefractionEnabled,
            ),
            expand = readGlassState(
                KEY_GLASS_EXPAND_ENABLED,
                KEY_REFRACTION_EXPAND_ENABLED,
                legacyGlassEnabled,
                legacyRefractionEnabled,
            ),
        )
        glassConfig = LiquidGlassConfig(
            enabled = glassStates.anyEnabled && anyBlurEnabled,
            edgeWidth = ConfigManager.getInt(KEY_GLASS_EDGE_WIDTH, 16).coerceIn(4, 40) / 100f,
            refraction = ConfigManager.getInt(KEY_GLASS_REFRACTION, 16).coerceIn(0, 40) / 100f,
            highlight = ConfigManager.getInt(KEY_GLASS_HIGHLIGHT, 42).coerceIn(0, 100) / 100f,
            shadow = ConfigManager.getInt(KEY_GLASS_SHADOW, 14).coerceIn(0, 100) / 100f,
            lightDirection = ConfigManager.getInt(KEY_GLASS_LIGHT_DIRECTION, 243)
                .coerceIn(0, 359),
            dispersion = ConfigManager.getInt(KEY_GLASS_DISPERSION, 18)
                .coerceIn(0, 100) / 100f,
            gyroscope = ConfigManager.getBoolean(KEY_GLASS_GYROSCOPE, true),
            hdrHighlight = ConfigManager.getBoolean(KEY_GLASS_HDR_HIGHLIGHT, false),
            trueRefraction = glassStates.anyRefractionEnabled,
            captureFps = ConfigManager.getInt(KEY_GLASS_CAPTURE_FPS, 20).coerceIn(1, 90),
            captureScale = ConfigManager.getInt(KEY_GLASS_CAPTURE_QUALITY, 30)
                .coerceIn(10, 100) / 100f,
        )
        glassConfigRevision++
    }

    private fun readGlassState(
        glassKey: String,
        refractionKey: String,
        legacyGlassEnabled: Boolean,
        legacyRefractionEnabled: Boolean,
    ): GlassState {
        val enabled = if (ConfigManager.contains(glassKey)) {
            ConfigManager.getBoolean(glassKey, false)
        } else {
            legacyGlassEnabled
        }
        val refractionEnabled = if (ConfigManager.contains(refractionKey)) {
            ConfigManager.getBoolean(refractionKey, false)
        } else {
            legacyRefractionEnabled
        }
        return GlassState(enabled, enabled && refractionEnabled)
    }

    private fun glassConfigForType(type: IslandType): LiquidGlassConfig {
        val state = glassStates.forType(type)
        return glassConfig.copy(
            enabled = state.enabled && configForType(type).isActive,
            trueRefraction = state.refractionEnabled && configForType(type).isActive,
        )
    }

    private fun readConfig(
        enabledKey: String,
        radiusKey: String,
        colorKey: String,
    ): BlurConfig {
        return BlurConfig(
            enabled = ConfigManager.getBoolean(enabledKey, false),
            radius = ConfigManager.getInt(radiusKey, DEFAULT_RADIUS).coerceIn(0, 100),
            blendColor = parseColor(ConfigManager.getString(colorKey)),
        )
    }

    private fun parseColor(value: String): Int {
        if (value.isBlank()) return DEFAULT_BLEND_COLOR
        return runCatching { Color.parseColor(value.trim()) }.getOrDefault(DEFAULT_BLEND_COLOR)
    }

    private fun hookPlugin(module: XposedModule, classLoader: ClassLoader) {
        try {
            val contentClass = Class.forName(CONTENT_VIEW_CLASS, false, classLoader)
            val backgroundClass = Class.forName(BACKGROUND_VIEW_CLASS, false, classLoader)
            val stateClass = Class.forName(
                "miui.systemui.dynamicisland.event.DynamicIslandState",
                false,
                classLoader,
            )
            val animationDelegateClass = Class.forName(
                ANIMATION_DELEGATE_CLASS,
                false,
                classLoader,
            )
            val fakeViewClass = Class.forName(FAKE_VIEW_CLASS, false, classLoader)
            val windowViewClass = Class.forName(
                "miui.systemui.dynamicisland.window.DynamicIslandWindowView",
                false,
                classLoader,
            )
            val compatClass = sequenceOf(
                "miui.systemui.util.MiBlurCompat",
                "miui.util.MiBlurCompat",
            ).mapNotNull { name ->
                runCatching { Class.forName(name, false, classLoader) }.getOrNull()
            }.firstOrNull() ?: throw ClassNotFoundException("MiBlurCompat")
            val methods = BlurMethods(
                setViewMode = compatClass.getDeclaredMethod(
                    "setMiViewBlurModeCompat",
                    View::class.java,
                    Int::class.javaPrimitiveType,
                ).apply { isAccessible = true },
                clearBlend = compatClass.getDeclaredMethod(
                    "clearMiBackgroundBlendColorCompat",
                    View::class.java,
                ).apply { isAccessible = true },
            )
            val updateMethod = contentClass.getDeclaredMethod(
                "updateBackgroundBg",
                View::class.java,
                Boolean::class.javaPrimitiveType,
            )
            val backgroundViewField = contentClass.getDeclaredField("backgroundView").apply {
                isAccessible = true
            }
            val stateField = contentClass.getDeclaredField("state").apply {
                isAccessible = true
            }
            val outerDrawableField = backgroundClass.getDeclaredField("drawable").apply {
                isAccessible = true
            }
            if (!hookedContentClasses.add(contentClass)) return
            hookIslandState(
                module,
                contentClass,
                stateClass,
                updateMethod,
                stateField,
                backgroundViewField,
                outerDrawableField,
            )
            hookBackgroundDrawing(module, backgroundClass, outerDrawableField)
            hookBackgroundAlphaUpdates(module, backgroundClass)
            hookTempHiddenLifecycle(module, windowViewClass)
            module.hook(updateMethod).intercept { chain ->
                val result = chain.proceed()
                val view = chain.args.getOrNull(0) as? View ?: return@intercept result
                val contentView = chain.thisObject ?: return@intercept result
                val type = typeForView(view)
                refreshTargets[view] = RefreshTarget(
                    contentView = WeakReference(contentView),
                    updateMethod = updateMethod,
                    promoted = chain.args.getOrNull(1) as? Boolean ?: false,
                    type = type,
                    stateField = stateField,
                )
                type ?: return@intercept result
                val backgroundView = runCatching {
                    backgroundViewField.get(contentView) as? View
                }.getOrNull()
                val config = configForType(type)
                val stateType = islandTypeHolder.get() ?: runCatching {
                    resolveIslandType(stateField.get(contentView))
                }.getOrNull() ?: lastIslandType
                // The shared state field can still contain BIG while expanded_view is
                // already laid out for a focus notification. The target view is authoritative.
                if (backgroundView == null) return@intercept result

                val staleUpdate = stateType != null && type != stateType
                val active = if (staleUpdate) {
                    false
                } else if (config.isActive) {
                    applyOuterBlur(
                        backgroundView,
                        view,
                        type,
                        config,
                        outerDrawableField,
                    )
                } else {
                    deactivateOuterBlur(backgroundView, outerDrawableField, "state-update")
                    // DynamicIslandBackgroundView owns one shared drawable slot. A
                    // previous state's blur restores its old stock drawable, not the
                    // current state's image, so re-install the current image here.
                    IslandBackgroundHook.restoreCustomBackground(backgroundView, type.name)
                    false
                }
                if (active) {
                    backgroundView.invalidate()
                }
                result
            }
            module.log("native island blur hook installed")
        } catch (_: ClassNotFoundException) {
        } catch (e: Throwable) {
            module.logError("hook installation failed: ${e.message}")
        }
    }

    private fun refreshTrackedViews() {
        val targets = synchronized(refreshTargets) {
            refreshTargets.entries.mapNotNull { (view, target) ->
                val contentView = target.contentView.get() ?: return@mapNotNull null
                Triple(view, contentView, target)
            }
        }
        targets.forEach { (view, contentView, target) ->
            if (!view.isAttachedToWindow) return@forEach
            val currentType = runCatching {
                resolveIslandType(target.stateField.get(contentView))
            }.getOrNull()
            if (target.type == null || currentType != target.type) return@forEach
            runCatching {
                target.updateMethod.invoke(contentView, view, target.promoted)
            }
        }
    }

    private fun hookIslandState(
        module: XposedModule,
        contentClass: Class<*>,
        stateClass: Class<*>,
        updateMethod: Method,
        stateField: java.lang.reflect.Field,
        backgroundViewField: java.lang.reflect.Field,
        outerDrawableField: java.lang.reflect.Field,
    ) {
        val method = contentClass.getDeclaredMethod(
            "updateDarkLightMode",
            stateClass,
            String::class.java,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
        )
        module.hook(method).intercept { chain ->
            val state = chain.args.getOrNull(0)
            val type = resolveIslandType(state)
            if (type != null) {
                islandTypeHolder.set(type)
                lastIslandType = type
            }
            try {
                val result = chain.proceed()
                if (type != null && configForType(type).isActive) {
                    mainHandler.removeCallbacks(refreshRunnable)
                    mainHandler.post(refreshRunnable)
                }
                if (type != null) {
                    mainHandler.post {
                        synchronizeOuterVisual(
                            chain.thisObject,
                            type,
                            stateField,
                            backgroundViewField,
                            outerDrawableField,
                        )
                        if (configForType(type).isActive) {
                            refreshConcreteIslandViews(chain.thisObject, updateMethod, type)
                        }
                    }
                } else if (isNoContentState(state)) {
                    val contentView = chain.thisObject
                    mainHandler.post {
                        val currentState = runCatching {
                            stateField.get(contentView)
                        }.getOrNull()
                        if (!isNoContentState(currentState)) return@post
                        val backgroundView = runCatching {
                            backgroundViewField.get(contentView) as? View
                        }.getOrNull() ?: return@post
                        deactivateOuterBlur(backgroundView, outerDrawableField, "no-content")
                        lastIslandType = null
                    }
                }
                result
            } finally {
                islandTypeHolder.remove()
            }
        }
    }

    /**
     * SystemUI does not always call updateBackgroundBg for small/big views.
     * Refresh the concrete views the same way the peer module does.
     */
    private fun refreshConcreteIslandViews(
        contentView: Any,
        updateMethod: Method,
        type: IslandType,
    ) {
        val getterName = when (type) {
            IslandType.SMALL -> "getSmallIslandView"
            IslandType.BIG -> "getBigIslandView"
            IslandType.EXPAND -> return
        }
        if (!configForType(type).isActive) return
        val getter = findMethod(contentView.javaClass, getterName)
        val view = runCatching { getter?.invoke(contentView) as? View }.getOrNull()
        if (view == null) return
        runCatching { updateMethod.invoke(contentView, view, false) }
    }

    private fun islandViewForType(contentView: Any, type: IslandType): View? {
        val getterName = when (type) {
            IslandType.SMALL -> "getSmallIslandView"
            IslandType.BIG -> "getBigIslandView"
            IslandType.EXPAND -> "getExpandedView"
        }
        return runCatching {
            findMethod(contentView.javaClass, getterName)?.invoke(contentView) as? View
        }.getOrNull()
    }

    private fun hookBackgroundDrawing(
        module: XposedModule,
        backgroundClass: Class<*>,
        drawableField: java.lang.reflect.Field,
    ) {
        val method = backgroundClass.getDeclaredMethod("onDraw", Canvas::class.java)
        module.hook(method).intercept { chain ->
            val backgroundView = chain.thisObject as? View ?: return@intercept chain.proceed()
            if (!anyBlurEnabled) return@intercept chain.proceed()

            realizePendingBlur(backgroundView, drawableField)
            val outer = outerBlurs[backgroundView]
            if (outer?.active == true) {
                runCatching {
                    if (drawableField.get(backgroundView) !== outer.renderDrawable) {
                        drawableField.set(backgroundView, outer.renderDrawable)
                    }
                }
                chain.proceed()
            } else {
                // The stock black/custom drawable belongs to SystemUI or the
                // background hook. Never suppress it for an inactive instance.
                chain.proceed()
            }
        }
    }

    /** Preserves the live blur across SystemUI's explicit temporary-hide lifecycle. */
    private fun hookTempHiddenLifecycle(
        module: XposedModule,
        windowViewClass: Class<*>,
    ) {
        val tempHideMethod = windowViewClass.declaredMethods.firstOrNull { method ->
            method.name == "onIslandTempHide" &&
                method.parameterCount == 2 &&
                method.parameterTypes[0] == Boolean::class.javaPrimitiveType
        } ?: return
        module.hook(tempHideMethod).intercept { chain ->
            val wasHidden = islandTempHidden
            val hidden = chain.args.getOrNull(0) as? Boolean
            when (hidden) {
                true -> if (!wasHidden) enterTempHidden()
                false, null -> Unit
            }
            val result = chain.proceed()
            if (hidden == false && wasHidden) {
                // The false callback precedes restoration of visible geometry. Keep
                // the reusable drawable protected until a real target can be drawn.
                islandTempHidden = false
                mainHandler.removeCallbacks(refreshRunnable)
                mainHandler.post(refreshRunnable)
                val recoveryViews = rebuildTempHiddenRecoveryQueue()
                recoveryViews.forEach(View::invalidate)
            }
            result
        }
    }

    /** Prevents an obsolete hide animation from making the current blur transparent. */
    private fun hookBackgroundAlphaUpdates(
        module: XposedModule,
        backgroundClass: Class<*>,
    ) {
        val backgroundAlphaField = backgroundClass.getDeclaredField("backgroundAlpha").apply {
            isAccessible = true
        }
        val method = backgroundClass.getDeclaredMethod("scheduleUpdate").apply {
            isAccessible = true
        }
        module.hook(method).intercept { chain ->
            val backgroundView = chain.thisObject as? View
            if (backgroundView != null && shouldKeepBackgroundOpaque(backgroundView)) {
                runCatching { backgroundAlphaField.setFloat(backgroundView, 1f) }
            }
            chain.proceed()
        }
    }

    private fun shouldKeepBackgroundOpaque(backgroundView: View): Boolean {
        val pending = pendingOuterBlurs[backgroundView]
        val outer = outerBlurs[backgroundView]
        val shapeView = pending?.shapeView?.get() ?: outer?.shapeView?.get() ?: return false
        val type = pending?.type ?: outer?.owned?.type ?: return false
        return configForType(type).isActive && hasVisibleGeometry(backgroundView, shapeView)
    }

    private fun enterTempHidden() {
        islandTempHidden = true
        synchronized(pendingOuterBlurs) {
            pendingOuterBlurs.entries.removeAll { (view, pending) ->
                !view.isAttachedToWindow || pending.shapeView.get() == null
            }
        }
        val active = synchronized(outerBlurs) {
            outerBlurs.entries.map { it.key to it.value }
        }
        active.forEach { (view, outer) ->
            outer.shapeView.get()?.let { shapeView ->
                pendingOuterBlurs[view] = PendingBlur(
                    shapeView = WeakReference(shapeView),
                    type = outer.owned.type,
                    requireVisibleGeometry = true,
                )
            }
        }
    }

    private fun currentBackgroundBounds(view: View): android.graphics.Rect? {
        val left = runCatching {
            findMethod(view.javaClass, "getActualLeft")?.invoke(view) as? Int
        }.getOrNull() ?: return null
        val top = runCatching {
            findMethod(view.javaClass, "getActualTop")?.invoke(view) as? Int
        }.getOrNull() ?: return null
        val right = runCatching {
            findMethod(view.javaClass, "getActualWidth")?.invoke(view) as? Int
        }.getOrNull() ?: return null
        val bottom = runCatching {
            findMethod(view.javaClass, "getActualHeight")?.invoke(view) as? Int
        }.getOrNull() ?: return null
        return android.graphics.Rect(left, top, right, bottom).takeIf {
            it.width() > 0 && it.height() > 0
        }
    }

    private fun setCurrentBackgroundBounds(view: View, drawable: Drawable): Boolean {
        val bounds = currentBackgroundBounds(view) ?: return false
        val stroke = resolveStrokeWidth(view)
        drawable.setBounds(
            bounds.left - stroke,
            bounds.top - stroke,
            bounds.right + stroke,
            bounds.bottom + stroke,
        )
        return true
    }


    private fun hookTransitionBlur(
        module: XposedModule,
        animationDelegateClass: Class<*>,
        fakeViewClass: Class<*>,
        methods: BlurMethods,
    ) {
        val access = TransitionAccess(
            fakeSmall = findMethod(fakeViewClass, "getFakeSmallIsland"),
            fakeBig = findMethod(fakeViewClass, "getFakeBigIsland"),
            fakeExpanded = findMethod(fakeViewClass, "getFakeExpandedView"),
        )
        val updateMethod = animationDelegateClass.getDeclaredMethod("updateFakeViewAnimState")
        val getFakeView = findMethod(animationDelegateClass, "getFakeView")
        module.hook(updateMethod).intercept { chain ->
            val result = chain.proceed()
            val fakeView = runCatching { getFakeView?.invoke(chain.thisObject) }.getOrNull()
            applyTransitionBlur(fakeView, methods, access)
            result
        }

        val containerUpdate = animationDelegateClass.getDeclaredMethod("containerScheduleUpdate")
        module.hook(containerUpdate).intercept { chain ->
            val result = chain.proceed()
            // container/fakeContainer are shared by all three states. Clearing either
            // for one enabled blur also removes the stock mask of disabled states.
            val fakeView = runCatching { getFakeView?.invoke(chain.thisObject) }.getOrNull()
            applyTransitionBlur(fakeView, methods, access)
            result
        }

        val finishInflate = fakeViewClass.getDeclaredMethod("onFinishInflate")
        module.hook(finishInflate).intercept { chain ->
            val result = chain.proceed()
            applyTransitionBlur(chain.thisObject, methods, access)
            result
        }

        val setVisibility = fakeViewClass.getDeclaredMethod(
            "setVisibility",
            Int::class.javaPrimitiveType,
        )
        module.hook(setVisibility).intercept { chain ->
            val result = chain.proceed()
            val fakeView = chain.thisObject
            if ((chain.args.getOrNull(0) as? Int) == View.VISIBLE) {
                applyTransitionBlur(fakeView, methods, access)
            }
            result
        }
    }

    private fun applyTransitionBlur(fakeView: Any?, methods: BlurMethods, access: TransitionAccess) {
        if (!anyBlurEnabled) return
        if (fakeView !is View) return
        access.forEachFakeView(fakeView) { type, child ->
            if (!configForType(type).isActive) return@forEachFakeView
            runCatching { methods.setViewMode.invoke(null, child, 0) }
            runCatching { methods.clearBlend.invoke(null, child) }
            child.background = null
        }
    }

    /** Keeps the shared outer drawable aligned with the settled logical state. */
    private fun synchronizeOuterVisual(
        contentView: Any?,
        type: IslandType,
        stateField: java.lang.reflect.Field,
        backgroundViewField: java.lang.reflect.Field,
        outerDrawableField: java.lang.reflect.Field,
    ) {
        if (contentView == null || runCatching {
                resolveIslandType(stateField.get(contentView))
            }.getOrNull() != type
        ) return
        val backgroundView = runCatching {
            backgroundViewField.get(contentView) as? View
        }.getOrNull() ?: return
        val config = configForType(type)
        val shapeView = islandViewForType(contentView, type)
        if (!config.isActive) {
            deactivateOuterBlur(backgroundView, outerDrawableField, "type-disabled")
            IslandBackgroundHook.restoreCustomBackground(backgroundView, type.name)
            return
        }
        // Expanded content can be inflated after the state/background callback.
        // Absence here is not a lifecycle end; keep the current blur until the
        // concrete target arrives through updateBackgroundBg/refresh.
        if (shapeView == null) return
        applyOuterBlur(backgroundView, shapeView, type, config, outerDrawableField)
    }

    private fun resolveIslandType(state: Any?): IslandType? {
        val name = state?.javaClass?.simpleName.orEmpty()
        return when {
            name.contains("SmallIsland") -> IslandType.SMALL
            name.contains("BigIsland") -> IslandType.BIG
            name.contains("Expanded") -> IslandType.EXPAND
            else -> null
        }
    }

    private fun isNoContentState(state: Any?): Boolean {
        val name = state?.javaClass?.simpleName.orEmpty()
        val text = state?.toString().orEmpty()
        return sequenceOf(name, text).any { value ->
            value.contains("Deleted", ignoreCase = true) ||
                value.contains("Empty", ignoreCase = true) ||
                value.contains("Invisible", ignoreCase = true) ||
                value.contains("Idle", ignoreCase = true) ||
                value.contains("None", ignoreCase = true)
        }
    }

    private fun configForType(type: IslandType): BlurConfig = when (type) {
        IslandType.SMALL -> configs.small
        IslandType.BIG -> configs.big
        IslandType.EXPAND -> configs.expand
    }

    internal fun isTransitionBlurEnabled(typeName: String): Boolean {
        val type = typeFromName(typeName) ?: return false
        return configForType(type).isActive
    }

    /** Installs the same native blur/liquid-glass pipeline on an animated fake island. */
    internal fun applyTransitionBlur(view: View, typeName: String): Boolean {
        val type = typeFromName(typeName) ?: return false
        val config = configForType(type)
        if (!config.isActive || !view.isAttachedToWindow) {
            releaseTransitionBlur(view)
            return false
        }

        return runCatching {
            var transition = transitionBlurs[view]
            if (transition?.owned?.type != type) {
                releaseTransitionBlur(view)
                val owned = createBackgroundBlurDrawable(view, type) ?: return@runCatching false
                transition = TransitionBlur(owned, view.background)
                transitionBlurs[view] = transition
            }
            val active = transition ?: return@runCatching false
            updateOwnedBlur(view, active.owned, config, view)
            if (view.background !== active.owned.drawable) {
                view.background = active.owned.drawable
            }
            active.owned.active = true
            ensureTransitionDetachCleanup(view)
            view.invalidate()
            true
        }.getOrDefault(false)
    }

    internal fun hasTransitionBlur(view: View, typeName: String): Boolean {
        val type = typeFromName(typeName) ?: return false
        val transition = transitionBlurs[view] ?: return false
        return transition.owned.type == type && view.background === transition.owned.drawable
    }

    internal fun releaseTransitionBlur(view: View) {
        val transition = transitionBlurs.remove(view) ?: return
        if (view.background === transition.owned.drawable) {
            view.background = transition.stockDrawable
        }
        runCatching {
            transition.owned.methods.setRadius.invoke(transition.owned.effectDrawable, 0)
        }
        transition.owned.release()
        transition.owned.active = false
        view.invalidate()
    }

    private fun typeFromName(typeName: String): IslandType? = when (typeName) {
        "SMALL" -> IslandType.SMALL
        "BIG" -> IslandType.BIG
        "EXPAND" -> IslandType.EXPAND
        else -> null
    }

    private fun ensureTransitionDetachCleanup(view: View) {
        if (view.getTag(TRANSITION_BLUR_LISTENER_TAG) != null) return
        val listener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit

            override fun onViewDetachedFromWindow(view: View) {
                releaseTransitionBlur(view)
                view.setTag(TRANSITION_BLUR_LISTENER_TAG, null)
                view.removeOnAttachStateChangeListener(this)
            }
        }
        view.setTag(TRANSITION_BLUR_LISTENER_TAG, listener)
        view.addOnAttachStateChangeListener(listener)
    }

    private fun typeForView(view: View): IslandType? {
        val resourceName = runCatching {
            if (view.id == View.NO_ID) "" else view.resources.getResourceEntryName(view.id)
        }.getOrDefault("")
        if (resourceName.contains("fake_expanded")) return null

        val className = view.javaClass.name
        if (className.contains("ExpandedView")) return IslandType.EXPAND
        if (className.contains("BigIslandView")) return IslandType.BIG

        return when {
            resourceName.contains("small_island") -> IslandType.SMALL
            resourceName.contains("big_island") -> IslandType.BIG
            resourceName.contains("expanded") -> IslandType.EXPAND
            else -> null
        }
    }

    private fun applyOuterBlur(
        backgroundView: View,
        shapeView: View,
        type: IslandType,
        config: BlurConfig,
        drawableField: java.lang.reflect.Field,
    ): Boolean {
        return runCatching {
            if (!backgroundView.isAttachedToWindow) return@runCatching false
            if (isTempHiddenLifecycleActive(backgroundView)) {
                pendingOuterBlurs[backgroundView] = PendingBlur(
                    shapeView = WeakReference(shapeView),
                    type = type,
                    requireVisibleGeometry = true,
                )
                return@runCatching false
            }
            val current = outerBlurs[backgroundView]
            if (current == null) {
                pendingOuterBlurs[backgroundView] = PendingBlur(
                    shapeView = WeakReference(shapeView),
                    type = type,
                )
                backgroundView.invalidate()
                return@runCatching true
            }
            pendingOuterBlurs.remove(backgroundView)
            val outer = current
            val typeChanged = outer.owned.type != type
            if (typeChanged) {
                val currentDrawable = drawableField.get(backgroundView) as? Drawable
                if (currentDrawable !== outer.renderDrawable) {
                    outer.stockDrawable = currentDrawable
                }
                log("$TAG reuse ${outer.owned.type} -> $type")
                outer.owned.type = type
            }
            outer.shapeView = WeakReference(shapeView)
            ensureDetachCleanup(backgroundView)
            updateOwnedBlur(backgroundView, outer.owned, config, shapeView)
            outer.owned.liquidDrawable.setVisible(true, false)
            outer.renderDrawable.setVisible(true, false)
            outer.renderDrawable.alpha = 255
            val outlineEnabled = IslandOutlineHook.isOutlineEnabled(type == IslandType.EXPAND)
            if (typeChanged || IslandOutlineHook.hasOutline(outer.renderDrawable) != outlineEnabled) {
                IslandOutlineHook.releaseOutline(outer.renderDrawable)
                outer.renderDrawable.callback = null
                outer.renderDrawable = IslandOutlineHook.withOutline(
                    outer.owned.drawable,
                    outer.stockDrawable,
                    type == IslandType.EXPAND,
                    type.name,
                )
            }
            drawableField.set(backgroundView, outer.renderDrawable)
            if (outer.renderDrawable.callback == null) {
                outer.renderDrawable.callback = WeakViewDrawableCallback(backgroundView)
            }
            outer.owned.active = true
            outer.active = true
            backgroundView.invalidate()
            true
        }.onFailure {
            recoveringOuterBlurs.remove(backgroundView)
            val failed = outerBlurs.remove(backgroundView)
            failed?.release()
            if (failed?.stockDrawable != null) {
                runCatching { drawableField.set(backgroundView, failed.stockDrawable) }
                backgroundView.invalidate()
            }
        }.getOrDefault(false)
    }

    /** Creates the native blur only once the background has entered a real draw pass. */
    private fun realizePendingBlur(
        backgroundView: View,
        drawableField: java.lang.reflect.Field,
    ) {
        val pending = pendingOuterBlurs[backgroundView] ?: return
        if (!backgroundView.isAttachedToWindow ||
            backgroundView.visibility != View.VISIBLE
        ) return
        val config = configForType(pending.type)
        if (!config.isActive) {
            pendingOuterBlurs.remove(backgroundView)
            recoveringOuterBlurs.remove(backgroundView)
            return
        }
        val shapeView = pending.shapeView.get() ?: run {
            pendingOuterBlurs.remove(backgroundView)
            recoveringOuterBlurs.remove(backgroundView)
            return
        }
        if (isTempHiddenLifecycleActive(backgroundView) || pending.requireVisibleGeometry) {
            if (!hasVisibleGeometry(backgroundView, shapeView)) return
        }
        if (currentBackgroundBounds(backgroundView) == null) return

        var candidate: OwnedBlur? = null
        runCatching {
            val current = outerBlurs[backgroundView]
            if (current != null) {
                val typeChanged = current.owned.type != pending.type
                if (typeChanged) {
                    val currentDrawable = drawableField.get(backgroundView) as? Drawable
                    if (currentDrawable !== current.renderDrawable) {
                        current.stockDrawable = currentDrawable
                    }
                    current.owned.type = pending.type
                }
                updateOwnedBlur(backgroundView, current.owned, config, shapeView)
                current.owned.liquidDrawable.setVisible(true, false)
                current.renderDrawable.setVisible(true, false)
                current.renderDrawable.alpha = 255
                val outlineEnabled = IslandOutlineHook.isOutlineEnabled(
                    pending.type == IslandType.EXPAND,
                )
                if (typeChanged ||
                    IslandOutlineHook.hasOutline(current.renderDrawable) != outlineEnabled
                ) {
                    IslandOutlineHook.releaseOutline(current.renderDrawable)
                    current.renderDrawable.callback = null
                    current.renderDrawable = IslandOutlineHook.withOutline(
                        current.owned.drawable,
                        current.stockDrawable,
                        pending.type == IslandType.EXPAND,
                        pending.type.name,
                    )
                }
                drawableField.set(backgroundView, current.renderDrawable)
                if (current.renderDrawable.callback == null) {
                    current.renderDrawable.callback = WeakViewDrawableCallback(backgroundView)
                }
                current.owned.active = true
                current.active = true
                pendingOuterBlurs.remove(backgroundView)
                recoveringOuterBlurs.remove(backgroundView)
                ensureDetachCleanup(backgroundView)
                return@runCatching
            }
            val stock = if (current == null) {
                drawableField.get(backgroundView) as? Drawable
            } else {
                current.stockDrawable
            }
            val owned = createBackgroundBlurDrawable(backgroundView, pending.type) ?: run {
                pendingOuterBlurs.remove(backgroundView)
                recoveringOuterBlurs.remove(backgroundView)
                log("$TAG native blur unavailable for ${pending.type}")
                return@runCatching
            }
            candidate = owned
            if (!setCurrentBackgroundBounds(backgroundView, owned.drawable)) {
                owned.release()
                candidate = null
                return@runCatching
            }
            val outer = OuterBlur(
                owned,
                stock,
                drawableField,
                WeakReference(shapeView),
            )
            updateOwnedBlur(backgroundView, owned, config, shapeView)
            outer.renderDrawable = IslandOutlineHook.withOutline(
                owned.drawable,
                stock,
                pending.type == IslandType.EXPAND,
                pending.type.name,
            )
            outer.renderDrawable.alpha = 255
            outer.renderDrawable.callback = WeakViewDrawableCallback(backgroundView)
            drawableField.set(backgroundView, outer.renderDrawable)
            current?.release()
            outerBlurs[backgroundView] = outer
            candidate = null
            owned.active = true
            outer.active = true
            pendingOuterBlurs.remove(backgroundView)
            recoveringOuterBlurs.remove(backgroundView)
            ensureDetachCleanup(backgroundView)
        }.onFailure { error ->
            logWarn("$TAG realization failed for ${pending.type}: ${error.message}")
            candidate?.let { owned ->
                runCatching { owned.methods.setRadius.invoke(owned.effectDrawable, 0) }
                owned.release()
            }
            pendingOuterBlurs.remove(backgroundView)
            recoveringOuterBlurs.remove(backgroundView)
            val failed = outerBlurs.remove(backgroundView)
            failed?.release()
            if (failed != null &&
                runCatching { drawableField.get(backgroundView) }.getOrNull() === failed.renderDrawable
            ) {
                runCatching { drawableField.set(backgroundView, failed.stockDrawable) }
            }
        }
    }

    internal fun updateStockOutline(
        backgroundView: Any?,
        stockDrawable: Drawable?,
        typeName: String?,
    ): Boolean {
        val view = backgroundView as? View ?: return false
        val stock = stockDrawable ?: return false
        val outer = outerBlurs[view] ?: return false
        if (typeName == null) return false
        outer.stockDrawable = stock
        // updateDarkLightMode installs the destination stock drawable before the
        // destination content View/config is synchronized. Preserve it without
        // rebuilding the wrapper against the old blur type.
        if (outer.owned.type.name != typeName) return true
        if (!outer.active) return true
        IslandOutlineHook.releaseOutline(outer.renderDrawable)
        outer.renderDrawable.callback = null
        outer.renderDrawable = IslandOutlineHook.withOutline(
            outer.owned.drawable,
            stock,
            outer.owned.type == IslandType.EXPAND,
            outer.owned.type.name,
        )
        runCatching { outer.drawableField.set(view, outer.renderDrawable) }
        outer.renderDrawable.callback = WeakViewDrawableCallback(view)
        view.invalidate()
        return true
    }

    private fun deactivateOuterBlur(
        backgroundView: View,
        drawableField: java.lang.reflect.Field,
        reason: String = "state-disabled",
    ) {
        pendingOuterBlurs.remove(backgroundView)
        recoveringOuterBlurs.remove(backgroundView)
        val outer = outerBlurs[backgroundView] ?: return
        if (runCatching { drawableField.get(backgroundView) }.getOrNull() === outer.renderDrawable) {
            runCatching { drawableField.set(backgroundView, outer.stockDrawable) }
        }
        outerBlurs.remove(backgroundView)
        log("$TAG release ${outer.owned.type}, reason=$reason")
        outer.release()
        backgroundView.invalidate()
    }

    private fun rebuildTempHiddenRecoveryQueue(): List<View> {
        val reusable = synchronized(outerBlurs) {
            outerBlurs.entries.mapNotNull { (view, outer) ->
                val shapeView = outer.shapeView.get() ?: return@mapNotNull null
                if (!view.isAttachedToWindow || !shapeView.isAttachedToWindow) {
                    return@mapNotNull null
                }
                Triple(view, shapeView, outer.owned.type)
            }
        }
        reusable.forEach { (view, shapeView, type) ->
            recoveringOuterBlurs.add(view)
            pendingOuterBlurs[view] = PendingBlur(
                shapeView = WeakReference(shapeView),
                type = type,
                requireVisibleGeometry = true,
            )
        }
        return reusable.map { it.first }
    }

    private fun isTempHiddenLifecycleActive(backgroundView: View): Boolean {
        return islandTempHidden || recoveringOuterBlurs.contains(backgroundView)
    }

    private fun ensureDetachCleanup(backgroundView: View) {
        synchronized(detachListeners) {
            if (detachListeners.containsKey(backgroundView)) return
            val listener = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) = Unit

                override fun onViewDetachedFromWindow(view: View) {
                    pendingOuterBlurs.remove(view)
                    recoveringOuterBlurs.remove(view)
                    outerBlurs.remove(view)?.release()
                    detachListeners.remove(view)
                    view.removeOnAttachStateChangeListener(this)
                }
            }
            detachListeners[backgroundView] = listener
            backgroundView.addOnAttachStateChangeListener(listener)
        }
    }

    private class WeakViewDrawableCallback(view: View) : Drawable.Callback {
        private val view = WeakReference(view)

        override fun invalidateDrawable(who: Drawable) {
            view.get()?.invalidateDrawable(who)
        }

        override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
            view.get()?.scheduleDrawable(who, what, `when`)
        }

        override fun unscheduleDrawable(who: Drawable, what: Runnable) {
            view.get()?.unscheduleDrawable(who, what)
        }
    }

    private fun createBackgroundBlurDrawable(view: View, type: IslandType): OwnedBlur? {
        val viewRoot = runCatching {
            findMethod(view.javaClass, "getViewRootImpl")?.invoke(view)
        }.getOrNull() ?: return null
        val drawable = runCatching {
            val method = findMethod(viewRoot.javaClass, "createBackgroundBlurDrawable")
                ?: return@runCatching null
            method.invoke(viewRoot) as? Drawable
        }.getOrNull() ?: return null

        return runCatching {
            val drawableClass = drawable.javaClass
            val methods = BlurDrawableMethods(
                setRadius = findMethod(
                    drawableClass,
                    "setBlurRadius",
                    Int::class.javaPrimitiveType!!,
                ) ?: return@runCatching null,
                setCornerRadius = findMethod(
                    drawableClass,
                    "setCornerRadius",
                    Float::class.javaPrimitiveType!!,
                    Float::class.javaPrimitiveType!!,
                    Float::class.javaPrimitiveType!!,
                    Float::class.javaPrimitiveType!!,
                ) ?: return@runCatching null,
                setColor = findMethod(
                    drawableClass,
                    "setColor",
                    Int::class.javaPrimitiveType!!,
                ) ?: return@runCatching null,
            )
            val strokeWidth = resolveStrokeWidth(view)
            val clippedDrawable = ClippedBlurDrawable(
                drawable,
                strokeWidth,
            )
            val liquidDrawable = LiquidGlassDrawable(
                view.context,
                view,
                clippedDrawable,
                strokeWidth,
                glassConfigForType(type),
            )
            OwnedBlur(
                drawable = liquidDrawable,
                effectDrawable = drawable,
                clippedDrawable = clippedDrawable,
                liquidDrawable = liquidDrawable,
                type = type,
                methods = methods,
            )
        }.getOrNull()
    }

    private fun updateOwnedBlur(
        view: View,
        owned: OwnedBlur,
        config: BlurConfig,
        shapeView: View,
    ) {
        if (owned.cornerRadius.isNaN()) {
            val radius = resolveCornerRadius(view)
            owned.cornerRadius = radius
            owned.clippedDrawable.setCornerRadius(radius)
            owned.liquidDrawable.setCornerRadius(radius)
            owned.methods.setCornerRadius.invoke(
                owned.effectDrawable,
                radius,
                radius,
                radius,
                radius,
            )
        }
        owned.liquidDrawable.setContentView(shapeView)
        owned.liquidDrawable.setBackgroundBlurRadius(config.radius.toFloat())
        owned.liquidDrawable.setBlendColor(config.blendColor)
        if (owned.glassConfigRevision != glassConfigRevision || owned.glassConfigType != owned.type) {
            owned.glassConfigRevision = glassConfigRevision
            owned.glassConfigType = owned.type
            owned.liquidDrawable.updateConfig(glassConfigForType(owned.type))
        }
        if (owned.blendColor != config.blendColor) {
            owned.blendColor = config.blendColor
            owned.methods.setColor.invoke(owned.effectDrawable, config.blendColor)
        }
        if (owned.blurRadius != config.radius) {
            owned.blurRadius = config.radius
            // Radius activates the RenderThread blur region, so geometry, corners,
            // and color must all be initialized before this final call.
            owned.methods.setRadius.invoke(owned.effectDrawable, config.radius)
        }
    }

    private fun resolveCornerRadius(view: View): Float {
        // Keep one base radius while actualWidth/actualHeight animate. Once the
        // height drops below twice this value, rounded-rect geometry naturally
        // becomes the target pill without a radius discontinuity at state change.
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            32f,
            view.resources.displayMetrics,
        )
    }

    private fun resolveStrokeWidth(view: View): Int {
        return runCatching {
            (findMethod(view.javaClass, "getStokeWidth")?.invoke(view) as? Int) ?: 0
        }.getOrDefault(0).coerceAtLeast(0)
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

    private class BlurConfig(
        enabled: Boolean,
        val radius: Int,
        blendColor: Int,
    ) {
        val blendColor = blendColor
        val isActive = enabled
    }

    private data class BlurConfigs(
        val small: BlurConfig,
        val big: BlurConfig,
        val expand: BlurConfig,
    ) {
        companion object {
            fun disabled(): BlurConfigs {
                val disabled = BlurConfig(
                    false,
                    DEFAULT_RADIUS,
                    DEFAULT_BLEND_COLOR,
                )
                return BlurConfigs(disabled, disabled, disabled)
            }
        }
    }

    private data class GlassState(
        val enabled: Boolean,
        val refractionEnabled: Boolean,
    )

    private data class GlassStates(
        val small: GlassState,
        val big: GlassState,
        val expand: GlassState,
    ) {
        val anyEnabled = small.enabled || big.enabled || expand.enabled
        val anyRefractionEnabled = small.refractionEnabled ||
            big.refractionEnabled || expand.refractionEnabled

        fun forType(type: IslandType): GlassState = when (type) {
            IslandType.SMALL -> small
            IslandType.BIG -> big
            IslandType.EXPAND -> expand
        }

        companion object {
            fun disabled(): GlassStates {
                val disabled = GlassState(false, false)
                return GlassStates(disabled, disabled, disabled)
            }
        }
    }

    private data class BlurMethods(
        val setViewMode: Method,
        val clearBlend: Method,
    )

    private data class BlurDrawableMethods(
        val setRadius: Method,
        val setCornerRadius: Method,
        val setColor: Method,
    )

    private class TransitionAccess(
        val fakeSmall: Method?,
        val fakeBig: Method?,
        val fakeExpanded: Method?,
    ) {
        fun forEachFakeView(owner: Any, action: (IslandType, View) -> Unit) {
            fun apply(type: IslandType, getter: Method?) {
                val view = runCatching { getter?.invoke(owner) as? View }.getOrNull()
                if (view != null) action(type, view)
            }
            apply(IslandType.SMALL, fakeSmall)
            apply(IslandType.BIG, fakeBig)
            apply(IslandType.EXPAND, fakeExpanded)
        }
    }

    private class OuterBlur(
        val owned: OwnedBlur,
        var stockDrawable: Drawable?,
        val drawableField: java.lang.reflect.Field,
        var shapeView: WeakReference<View>,
        var active: Boolean = false,
    ) {
        var renderDrawable: Drawable = owned.drawable

        fun release() {
            runCatching { owned.methods.setRadius.invoke(owned.effectDrawable, 0) }
            IslandOutlineHook.releaseOutline(renderDrawable)
            renderDrawable.callback = null
            owned.release()
            owned.active = false
            active = false
        }
    }

    /** A hidden status bar does not imply that a newly focused island is hidden. */
    private fun hasVisibleGeometry(backgroundView: View, shapeView: View): Boolean {
        if (!backgroundView.isShown || !shapeView.isShown ||
            backgroundView.windowVisibility != View.VISIBLE ||
            shapeView.windowVisibility != View.VISIBLE
        ) return false

        val visibleRect = android.graphics.Rect()
        if (!shapeView.getGlobalVisibleRect(visibleRect) ||
            visibleRect.width() <= 0 || visibleRect.height() <= 0
        ) return false

        var current: View? = shapeView
        while (current != null) {
            if (current.visibility != View.VISIBLE || current.alpha <= 0.01f) return false
            current = current.parent as? View
        }
        return true
    }

    private class TransitionBlur(
        val owned: OwnedBlur,
        val stockDrawable: Drawable?,
    )

    private class OwnedBlur(
        val drawable: Drawable,
        val effectDrawable: Drawable,
        val clippedDrawable: ClippedBlurDrawable,
        val liquidDrawable: LiquidGlassDrawable,
        var type: IslandType,
        val methods: BlurDrawableMethods,
        var active: Boolean = false,
    ) {
        var cornerRadius = Float.NaN
        var blurRadius = Int.MIN_VALUE
        var blendColor = Int.MIN_VALUE
        var glassConfigRevision = Int.MIN_VALUE
        var glassConfigType: IslandType? = null

        fun release() {
            liquidDrawable.release()
            clippedDrawable.release()
        }
    }

    private const val TRANSITION_BLUR_LISTENER_TAG = 0x4859424c

    /** Keeps the blur region inside the same stroked rounded bounds as image backgrounds. */
    private class ClippedBlurDrawable(
        private val child: Drawable,
        private val inset: Int,
    ) : Drawable(), Drawable.Callback {
        private val clipPath = Path()
        private val clipRect = RectF()
        private var cornerRadius = 0f

        init {
            child.callback = this
        }

        fun setCornerRadius(radius: Float) {
            cornerRadius = radius
        }

        override fun onBoundsChange(bounds: android.graphics.Rect) {
            updateChildBounds(bounds)
        }

        private fun updateChildBounds(bounds: android.graphics.Rect): Boolean {
            val safeInset = inset.coerceAtMost(minOf(bounds.width(), bounds.height()) / 2)
            clipRect.set(
                (bounds.left + safeInset).toFloat(),
                (bounds.top + safeInset).toFloat(),
                (bounds.right - safeInset).toFloat(),
                (bounds.bottom - safeInset).toFloat(),
            )
            if (clipRect.isEmpty) return false
            child.setBounds(
                clipRect.left.toInt(),
                clipRect.top.toInt(),
                clipRect.right.toInt(),
                clipRect.bottom.toInt(),
            )
            return true
        }

        override fun draw(canvas: Canvas) {
            val bounds = bounds
            if (!updateChildBounds(bounds)) return
            clipPath.reset()
            clipPath.addRoundRect(clipRect, cornerRadius, cornerRadius, Path.Direction.CW)
            val save = canvas.save()
            canvas.clipPath(clipPath)
            child.draw(canvas)
            canvas.restoreToCount(save)
        }

        override fun setAlpha(alpha: Int) {
            child.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            child.colorFilter = colorFilter
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
            child.callback = null
            callback = null
        }
    }

    private data class RefreshTarget(
        val contentView: WeakReference<Any>,
        val updateMethod: Method,
        val promoted: Boolean,
        val type: IslandType?,
        val stateField: java.lang.reflect.Field,
    )

    private data class PendingBlur(
        val shapeView: WeakReference<View>,
        val type: IslandType,
        val requireVisibleGeometry: Boolean = false,
    )

    private enum class IslandType { SMALL, BIG, EXPAND }

}
