package io.github.hyperisland.xposed.hook.SystemUI

import android.graphics.Color
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method
import java.lang.ref.WeakReference
import java.io.File
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean

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
    private const val KEY_SMALL_BACKGROUND = "pref_island_bg_small_path"
    private const val KEY_BIG_BACKGROUND = "pref_island_bg_big_path"
    private const val KEY_EXPAND_BACKGROUND = "pref_island_bg_expand_path"

    private const val DEFAULT_RADIUS = 80
    private const val DEFAULT_BLEND_COLOR = 0x20FFFFFF
    private val hookedClassLoaders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())
    )
    private val refreshTargets = Collections.synchronizedMap(
        WeakHashMap<View, RefreshTarget>()
    )
    private val backgroundStates = Collections.synchronizedMap(
        WeakHashMap<Any, BackgroundState>()
    )
    private val ownedBlurs = Collections.synchronizedMap(
        WeakHashMap<View, WeakReference<OwnedBlur>>()
    )
    private val transitionOverlays = Collections.synchronizedMap(
        WeakHashMap<View, View>()
    )
    private val mainHandler = Handler(Looper.getMainLooper())
    private val applyFailureLogged = AtomicBoolean(false)
    private var lastDiagAt = 0L
    private var lastDiagKey = ""
    private val refreshRunnable = Runnable { refreshTrackedViews() }
    private val islandTypeHolder = ThreadLocal<IslandType>()

    @Volatile
    private var lastIslandType: IslandType? = null

    @Volatile
    private var configs = BlurConfigs.disabled()

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        if (param.packageName != "com.android.systemui") return
        loadConfig()
        diag(
            module,
            "init config small=${configSummary(configs.small)} " +
                "big=${configSummary(configs.big)} expand=${configSummary(configs.expand)}",
        )
        hookPlugin(module, param.defaultClassLoader)
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { classLoader ->
            hookPlugin(module, classLoader)
        }
    }

    override fun onConfigChanged() {
        loadConfig()
        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.postDelayed(refreshRunnable, 80L)
    }

    private fun diag(module: XposedModule, message: String) {
        if (!ConfigManager.isDebugLogEnabled()) return
        val now = System.currentTimeMillis()
        synchronized(this) {
            if (now - lastDiagAt < 300L && message == lastDiagKey) return
            lastDiagAt = now
            lastDiagKey = message
        }
        log(module, message)
    }

    private fun loadConfig() {
        configs = BlurConfigs(
            small = readConfig(
                KEY_SMALL_ENABLED,
                KEY_SMALL_RADIUS,
                KEY_SMALL_COLOR,
                KEY_SMALL_BACKGROUND,
            ),
            big = readConfig(
                KEY_BIG_ENABLED,
                KEY_BIG_RADIUS,
                KEY_BIG_COLOR,
                KEY_BIG_BACKGROUND,
            ),
            expand = readConfig(
                KEY_EXPAND_ENABLED,
                KEY_EXPAND_RADIUS,
                KEY_EXPAND_COLOR,
                KEY_EXPAND_BACKGROUND,
            ),
        )
    }

    private fun readConfig(
        enabledKey: String,
        radiusKey: String,
        colorKey: String,
        backgroundKey: String,
    ): BlurConfig {
        return BlurConfig(
            enabled = ConfigManager.getBoolean(enabledKey, false),
            radius = ConfigManager.getInt(radiusKey, DEFAULT_RADIUS).coerceIn(0, 275),
            blendColor = parseColor(ConfigManager.getString(colorKey)),
            hasCustomBackground = ConfigManager.getString(backgroundKey).let { path ->
                path.isNotBlank() && File(path).let { it.isFile && it.canRead() }
            },
        )
    }

    private fun parseColor(value: String): Int {
        if (value.isBlank()) return DEFAULT_BLEND_COLOR
        return runCatching { Color.parseColor(value.trim()) }.getOrDefault(DEFAULT_BLEND_COLOR)
    }

    private fun hookPlugin(module: XposedModule, classLoader: ClassLoader) {
        if (!hookedClassLoaders.add(classLoader)) return

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
            val compatClass = sequenceOf(
                "miui.systemui.util.MiBlurCompat",
                "miui.util.MiBlurCompat",
            ).mapNotNull { name ->
                runCatching { Class.forName(name, false, classLoader) }.getOrNull()
            }.firstOrNull() ?: throw ClassNotFoundException("MiBlurCompat")
            val methods = BlurMethods(
                isBlurOpened = compatClass.getDeclaredMethod(
                    "getBackgroundBlurOpened",
                    android.content.Context::class.java,
                ).apply { isAccessible = true },
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
            hookIslandState(module, contentClass, stateClass, updateMethod)
            hookBackgroundDrawing(module, backgroundClass)
            hookTransitionBlur(module, animationDelegateClass, fakeViewClass, methods)
            module.hook(updateMethod).intercept { chain ->
                val result = chain.proceed()
                val view = chain.args.getOrNull(0) as? View ?: return@intercept result
                val contentView = chain.thisObject ?: return@intercept result
                refreshTargets[view] = RefreshTarget(
                    contentView = WeakReference(contentView),
                    updateMethod = updateMethod,
                    promoted = chain.args.getOrNull(1) as? Boolean ?: false,
                    type = typeForView(view),
                    stateField = stateField,
                )
                val type = typeForView(view) ?: return@intercept result
                val backgroundView = runCatching {
                    backgroundViewField.get(contentView) as? View
                }.getOrNull()
                val backgroundState = if (backgroundView != null) {
                    synchronized(backgroundStates) {
                        backgroundStates[contentView] ?: BackgroundState(
                            contentView = WeakReference(contentView),
                            backgroundView = WeakReference(backgroundView),
                            stateField = stateField,
                        ).also { backgroundStates[contentView] = it }
                    }
                } else {
                    null
                }
                val config = configForType(type)
                val stateType = islandTypeHolder.get() ?: runCatching {
                    resolveIslandType(stateField.get(contentView))
                }.getOrNull() ?: lastIslandType
                diag(
                    module,
                    "update view=${describe(view)} type=$type state=$stateType " +
                        "enabled=${config.enabled} radius=${config.radius} " +
                        "size=${view.width}x${view.height} outer=${describe(backgroundView)}",
                )
                // The shared state field can still contain BIG while expanded_view is
                // already laid out for a focus notification. The target view is authoritative.
                if (backgroundView == null) return@intercept result

                deactivateBlur(view)
                val staleExpandedUpdate = type == IslandType.EXPAND && stateType != IslandType.EXPAND
                val active = if (staleExpandedUpdate) {
                    diag(module, "skip expanded view while state=$stateType view=${describe(view)}")
                    false
                } else if (config.enabled && !config.hasCustomBackground) {
                    applyContentBlur(module, view, type, config, methods)
                } else {
                    deactivateBlur(view)
                    false
                }
                if (backgroundState != null) {
                    backgroundState.updateContentView(contentView)
                    backgroundState.setTarget(type, view)
                    backgroundState.setActive(type, active)
                }
                // Install the new concrete layer before releasing the previous
                // one. SystemUI can draw the shared outer view between these
                // operations; releasing first exposes its black transition mask.
                backgroundState?.deactivateOtherTypes(type)?.forEach { oldView ->
                    deactivateBlur(oldView)
                }
                if (backgroundView != null && backgroundState != null) {
                    backgroundView.invalidate()
                }
                result
            }
            log(module, "native island blur hook installed")
        } catch (_: ClassNotFoundException) {
            hookedClassLoaders.remove(classLoader)
        } catch (e: Throwable) {
            hookedClassLoaders.remove(classLoader)
            logError(module, "hook installation failed: ${e.message}")
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
    ) {
        val method = contentClass.getDeclaredMethod(
            "updateDarkLightMode",
            stateClass,
            String::class.java,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
        )
        module.hook(method).intercept { chain ->
            val type = resolveIslandType(chain.args.getOrNull(0))
            if (type != null) {
                islandTypeHolder.set(type)
                lastIslandType = type
            }
            try {
                val result = chain.proceed()
                if (type != null) {
                    mainHandler.removeCallbacks(refreshRunnable)
                    mainHandler.post(refreshRunnable)
                    mainHandler.post {
                        refreshConcreteIslandViews(
                            chain.thisObject,
                            updateMethod,
                            type,
                        )
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
        val module = ConfigManager.module() ?: return
        val getterName = when (type) {
            IslandType.SMALL -> "getSmallIslandView"
            IslandType.BIG -> "getBigIslandView"
            IslandType.EXPAND -> return
        }
        val getter = findMethod(contentView.javaClass, getterName)
        val view = runCatching { getter?.invoke(contentView) as? View }.getOrNull()
        if (view == null) {
            diag(module, "concrete getter=$getterName result=null")
            return
        }
        diag(module, "concrete getter=$getterName view=${describe(view)}")
        runCatching {
            updateMethod.invoke(contentView, view, false)
        }.onFailure { error ->
            diag(module, "concrete refresh failed getter=$getterName error=${error.message}")
        }
    }

    private fun hookBackgroundDrawing(module: XposedModule, backgroundClass: Class<*>) {
        val method = backgroundClass.getDeclaredMethod("onDraw", Canvas::class.java)
        module.hook(method).intercept { chain ->
            val backgroundView = chain.thisObject as? View ?: return@intercept chain.proceed()
            // The outer drawable is always the stock black island underneath
            // concrete blur layers. This must run before state lookup because a
            // new/fake transition can draw before BackgroundState is registered.
            val blurLifecycleActive = sequenceOf(configs.small, configs.big, configs.expand).any {
                it.enabled && !it.hasCustomBackground
            }
            if (blurLifecycleActive) return@intercept null

            val states = synchronized(backgroundStates) {
                backgroundStates.values.filter { it.backgroundView.get() === backgroundView }
            }
            if (states.isEmpty()) return@intercept chain.proceed()
            val activeTarget = states.asSequence()
                .flatMap { state ->
                    IslandType.values().asSequence().filter { state.isActive(it) }.mapNotNull { type ->
                        state.target(type)?.get()?.let { type to it }
                    }
                }
                .mapNotNull { (type, target) ->
                    ownedBlurs[target]?.get()?.takeIf {
                        it.active && target.background === it.drawable
                    }?.let { type to (target to it) }
                }
                .firstOrNull()
                ?: return@intercept chain.proceed()
            val type = activeTarget.first
            val target = activeTarget.second.first
            val owned = activeTarget.second.second

            // All state blur layers live on their concrete content View. Suppress only
            // the shared outer black island drawable.
            diag(
                module,
                "suppress outer type=$type target=${describe(target)} " +
                    "blur=${System.identityHashCode(owned.drawable)}",
            )
            null
        }
    }

    private fun hookTransitionBlur(
        module: XposedModule,
        animationDelegateClass: Class<*>,
        fakeViewClass: Class<*>,
        methods: BlurMethods,
    ) {
        val updateMethod = animationDelegateClass.getDeclaredMethod("updateFakeViewAnimState")
        val getFakeView = findMethod(animationDelegateClass, "getFakeView")
        module.hook(updateMethod).intercept { chain ->
            val result = chain.proceed()
            val fakeView = runCatching { getFakeView?.invoke(chain.thisObject) }.getOrNull()
            applyTransitionBlur(module, fakeView, methods)
            result
        }

        val containerUpdate = animationDelegateClass.getDeclaredMethod("containerScheduleUpdate")
        val viewField = animationDelegateClass.getDeclaredField("view").apply { isAccessible = true }
        module.hook(containerUpdate).intercept { chain ->
            val result = chain.proceed()
            if (sequenceOf(configs.small, configs.big, configs.expand).any {
                    it.enabled && !it.hasCustomBackground
                }) {
                val contentView = runCatching { viewField.get(chain.thisObject) }.getOrNull()
                clearTransitionContainer(contentView, methods)
                val fakeView = runCatching { getFakeView?.invoke(chain.thisObject) }.getOrNull()
                clearTransitionContainer(fakeView, methods)
            }
            result
        }

        val finishInflate = fakeViewClass.getDeclaredMethod("onFinishInflate")
        module.hook(finishInflate).intercept { chain ->
            val result = chain.proceed()
            applyTransitionBlur(module, chain.thisObject, methods)
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
                applyTransitionBlur(module, fakeView, methods)
            } else if (fakeView is View) {
                transitionOverlays[fakeView]?.let {
                    deactivateBlur(it)
                    it.visibility = View.INVISIBLE
                }
            }
            result
        }
    }

    private fun applyTransitionBlur(module: XposedModule, fakeView: Any?, methods: BlurMethods) {
        if (fakeView !is View) return
        // The fake root covers the animation window. A child overlay is positioned
        // at SystemUI's animated roundedRect so BackgroundBlurDrawable never uses
        // the oversized root bounds.
        deactivateBlur(fakeView)
        fakeView.background = null
        val realView = runCatching {
            findMethod(fakeView.javaClass, "getRealView")?.invoke(fakeView)
        }.getOrNull()
        val state = runCatching {
            realView?.let { findMethod(it.javaClass, "getState")?.invoke(it) }
        }.getOrNull()
        val type = resolveIslandType(state) ?: lastIslandType
        if (type != null) {
            val config = configForType(type)
            if (config.enabled && !config.hasCustomBackground && fakeView.isShown) {
                val overlay = transitionOverlay(fakeView)
                ownedBlurs[overlay]?.get()?.takeIf { it.type != type }?.let {
                    deactivateBlur(overlay)
                    ownedBlurs.remove(overlay)
                }
                syncTransitionOverlay(fakeView, overlay)
                applyContentBlur(module, overlay, type, config, methods)
            } else {
                transitionOverlays[fakeView]?.let(::deactivateBlur)
            }
        }
        transitionMaskViews(fakeView).forEach { child ->
            runCatching { methods.setViewMode.invoke(null, child, 0) }
            runCatching { methods.clearBlend.invoke(null, child) }
            child.background = null
        }
        transitionExpandedView(fakeView)?.let { expanded ->
            // Keep SystemUI's transition surface and outline, but do not treat it
            // as the stable expanded target or add another BlurDrawable.
            runCatching { methods.clearBlend.invoke(null, expanded) }
            expanded.background = null
        }
        runCatching {
            (findMethod(fakeView.javaClass, "getFakeMask")?.invoke(fakeView) as? View)?.apply {
                visibility = View.INVISIBLE
                background = null
            }
        }
    }

    private fun transitionMaskViews(fakeView: Any): List<View> {
        return listOf(
            "getFakeContainer",
            "getFakeSmallIsland",
            "getFakeBigIsland",
        ).mapNotNull { getterName ->
            runCatching {
                findMethod(fakeView.javaClass, getterName)?.invoke(fakeView) as? View
            }.getOrNull()
        }
    }

    private fun transitionExpandedView(fakeView: Any): View? {
        return runCatching {
            findMethod(fakeView.javaClass, "getFakeExpandedView")?.invoke(fakeView) as? View
        }.getOrNull()
    }

    private fun clearTransitionContainer(owner: Any?, methods: BlurMethods) {
        if (owner == null) return
        listOf("getContainer", "getFakeContainer").forEach { getterName ->
            val container = runCatching {
                findMethod(owner.javaClass, getterName)?.invoke(owner) as? View
            }.getOrNull() ?: return@forEach
            runCatching { methods.setViewMode.invoke(null, container, 0) }
            runCatching { methods.clearBlend.invoke(null, container) }
            container.background = null
        }
    }

    private fun transitionOverlay(fakeView: View): View {
        synchronized(transitionOverlays) {
            transitionOverlays[fakeView]?.let { return it }
            val parent = fakeView as? ViewGroup ?: return fakeView
            return View(fakeView.context).apply {
                layoutParams = FrameLayout.LayoutParams(1, 1)
                isClickable = false
                isFocusable = false
                parent.addView(this, 0)
                transitionOverlays[fakeView] = this
            }
        }
    }

    private fun syncTransitionOverlay(fakeView: View, overlay: View) {
        val roundedRect = runCatching {
            findMethod(fakeView.javaClass, "getRoundedRect")?.invoke(fakeView) as? android.graphics.RectF
        }.getOrNull() ?: return
        val width = roundedRect.width().toInt().coerceAtLeast(1)
        val height = roundedRect.height().toInt().coerceAtLeast(1)
        val params = (overlay.layoutParams as? FrameLayout.LayoutParams)
            ?: FrameLayout.LayoutParams(width, height)
        if (params.width != width || params.height != height) {
            params.width = width
            params.height = height
            overlay.layoutParams = params
        }
        overlay.layout(0, 0, width, height)
        overlay.translationX = roundedRect.left
        overlay.translationY = roundedRect.top
        overlay.visibility = View.VISIBLE
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

    private fun configForType(type: IslandType): BlurConfig = when (type) {
        IslandType.SMALL -> configs.small
        IslandType.BIG -> configs.big
        IslandType.EXPAND -> configs.expand
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

    private fun deactivateBlur(view: View) {
        val owned = ownedBlurs[view]?.get() ?: return
        runCatching { owned.methods.setRadius.invoke(owned.effectDrawable, 0) }
        if (view.background === owned.drawable && owned.stockDrawableCaptured) {
            view.background = owned.stockDrawable
        }
        owned.active = false
        ConfigManager.module()?.let { module ->
            diag(module, "content deactivated type=${owned.type} view=${describe(view)}")
        }
    }

    private fun applyContentBlur(
        module: XposedModule,
        view: View,
        type: IslandType,
        config: BlurConfig,
        methods: BlurMethods,
    ): Boolean {
        val applied = runCatching {
            if (!view.isAttachedToWindow || view.width <= 0 || view.height <= 0) {
                return@runCatching false
            }
            val blurOpened = methods.isBlurOpened.invoke(null, view.context) as? Boolean ?: false
            if (!blurOpened) return@runCatching false

            val owned = synchronized(ownedBlurs) {
                ownedBlurs[view]?.get()?.takeIf { it.type == type }
                    ?: createBackgroundBlurDrawable(view, type)?.also {
                        ownedBlurs[view] = WeakReference(it)
                    }
            } ?: return@runCatching false

            if (!owned.stockDrawableCaptured && view.background !== owned.drawable) {
                owned.stockDrawable = view.background
                owned.stockDrawableCaptured = true
            }
            updateOwnedBlur(module, view, owned, config, view)
            val modeResult = methods.setViewMode.invoke(null, view, 0)
            val blendResult = methods.clearBlend.invoke(null, view)
            view.background = owned.drawable
            owned.active = true
            view.invalidate()
            diag(
                module,
                "content applied type=$type radius=${config.radius} " +
                    "blur=${System.identityHashCode(owned.effectDrawable)} " +
                    "mode=$modeResult blend=$blendResult view=${describe(view)}",
            )
            true
        }.onFailure { error ->
            if (applyFailureLogged.compareAndSet(false, true)) {
                logError(module, "content blur application failed: ${error.message}")
            }
            diag(module, "content failed type=$type radius=${config.radius} error=${error.message}")
        }.getOrDefault(false)

        if (!applied) deactivateBlur(view)
        return applied
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
            OwnedBlur(
                drawable = drawable,
                effectDrawable = drawable,
                type = type,
                methods = BlurDrawableMethods(
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
                ),
            )
        }.onFailure { error ->
            ConfigManager.module()?.let { module ->
                diag(module, "drawable create failed type=$type view=${describe(view)} error=${error.message}")
            }
        }.getOrNull().also { owned ->
            ConfigManager.module()?.let { module ->
                diag(
                    module,
                    "drawable create type=$type view=${describe(view)} " +
                        "result=${owned != null} class=${owned?.drawable?.javaClass?.name}",
                )
            }
        }
    }

    private fun updateOwnedBlur(
        module: XposedModule,
        view: View,
        owned: OwnedBlur,
        config: BlurConfig,
        shapeView: View,
    ) {
        val radiusResult = owned.methods.setRadius.invoke(owned.effectDrawable, config.radius)
        val radius = resolveCornerRadius(view, owned.type, shapeView)
        val cornerResult = owned.methods.setCornerRadius.invoke(
            owned.effectDrawable,
            radius,
            radius,
            radius,
            radius,
        )
        val colorResult = owned.methods.setColor.invoke(owned.effectDrawable, config.blendColor)
        diag(
            module,
            "drawable update type=${owned.type} requested=${config.radius} " +
                "radiusResult=$radiusResult corner=$radius cornerResult=$cornerResult " +
                "colorResult=$colorResult",
        )
    }

    private fun describe(view: View?): String {
        if (view == null) return "null"
        val name = view.javaClass.simpleName
        val id = runCatching {
            if (view.id == View.NO_ID) "no-id" else view.resources.getResourceEntryName(view.id)
        }.getOrDefault("?")
        return "$name($id)"
    }

    private fun configSummary(config: BlurConfig): String {
        return "enabled=${config.enabled},radius=${config.radius},custom=${config.hasCustomBackground}"
    }

    private fun resolveCornerRadius(view: View, type: IslandType, shapeView: View? = null): Float {
        if (type == IslandType.EXPAND) {
            val target = shapeView ?: view
            // The expanded view inherits the pill outline (77dp on this device).
            // It is not the focus card's rounded-rectangle corner radius.
            return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                32f,
                target.resources.displayMetrics,
            )
        }
        val resourceId = view.resources.getIdentifier(
            "island_radius",
            "dimen",
            "com.android.systemui",
        )
        return if (resourceId > 0) {
            view.resources.getDimension(resourceId)
        } else {
            view.height / 2f
        }
    }

    private fun findMethod(clazz: Class<*>, name: String, vararg types: Class<*>): Method? {
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
        val hasCustomBackground: Boolean,
    ) {
        val enabled = enabled
        val blendColor = blendColor
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
                    false,
                )
                return BlurConfigs(disabled, disabled, disabled)
            }
        }
    }

    private data class BlurMethods(
        val isBlurOpened: Method,
        val setViewMode: Method,
        val clearBlend: Method,
    )

    private data class BlurDrawableMethods(
        val setRadius: Method,
        val setCornerRadius: Method,
        val setColor: Method,
    )

    private class OwnedBlur(
        val drawable: Drawable,
        val effectDrawable: Drawable,
        val type: IslandType,
        val methods: BlurDrawableMethods,
        var active: Boolean = false,
        var stockDrawable: Drawable? = null,
        var stockDrawableCaptured: Boolean = false,
    )

    private data class RefreshTarget(
        val contentView: WeakReference<Any>,
        val updateMethod: Method,
        val promoted: Boolean,
        val type: IslandType?,
        val stateField: java.lang.reflect.Field,
    )

    private class BackgroundState(
        contentView: WeakReference<Any>,
        val backgroundView: WeakReference<View>,
        val stateField: java.lang.reflect.Field,
    ) {
        var contentView = contentView
            private set
        private var smallActive = false
        private var bigActive = false
        private var expandActive = false
        private var smallTarget: WeakReference<View>? = null
        private var bigTarget: WeakReference<View>? = null
        private var expandTarget: WeakReference<View>? = null

        fun updateContentView(value: Any) {
            contentView = WeakReference(value)
        }

        fun setTarget(type: IslandType, view: View) {
            val target = WeakReference(view)
            when (type) {
                IslandType.SMALL -> smallTarget = target
                IslandType.BIG -> bigTarget = target
                IslandType.EXPAND -> expandTarget = target
            }
        }

        fun target(type: IslandType): WeakReference<View>? = when (type) {
            IslandType.SMALL -> smallTarget
            IslandType.BIG -> bigTarget
            IslandType.EXPAND -> expandTarget
        }

        fun setActive(type: IslandType, active: Boolean) {
            when (type) {
                IslandType.SMALL -> smallActive = active
                IslandType.BIG -> bigActive = active
                IslandType.EXPAND -> expandActive = active
            }
        }

        fun deactivateOtherTypes(current: IslandType): List<View> {
            val oldViews = mutableListOf<View>()
            if (current != IslandType.SMALL && smallActive) {
                smallTarget?.get()?.let(oldViews::add)
                smallActive = false
            }
            if (current != IslandType.BIG && bigActive) {
                bigTarget?.get()?.let(oldViews::add)
                bigActive = false
            }
            if (current != IslandType.EXPAND && expandActive) {
                expandTarget?.get()?.let(oldViews::add)
                expandActive = false
            }
            return oldViews
        }

        fun isActive(type: IslandType): Boolean = when (type) {
            IslandType.SMALL -> smallActive
            IslandType.BIG -> bigActive
            IslandType.EXPAND -> expandActive
        }

        fun activeType(): IslandType? = when {
            expandActive -> IslandType.EXPAND
            bigActive -> IslandType.BIG
            smallActive -> IslandType.SMALL
            else -> null
        }

    }

    private enum class IslandType { SMALL, BIG, EXPAND }
}
