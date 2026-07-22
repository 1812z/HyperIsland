package io.github.hyperisland.xposed.hook.SystemUI

import android.graphics.Color
import android.graphics.Canvas
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
import io.github.hyperisland.xposed.log
import io.github.hyperisland.xposed.logError
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
    private var anyBlurEnabled = false

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
                deactivateOuterBlur(view, outer.drawableField)
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
    }

    private fun readConfig(
        enabledKey: String,
        radiusKey: String,
        colorKey: String,
    ): BlurConfig {
        return BlurConfig(
            enabled = ConfigManager.getBoolean(enabledKey, false),
            radius = ConfigManager.getInt(radiusKey, DEFAULT_RADIUS).coerceIn(0, 275),
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
            hookTransitionBlur(module, animationDelegateClass, fakeViewClass, methods)
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
                } else if (config.enabled) {
                    applyOuterBlur(
                        backgroundView,
                        view,
                        type,
                        config,
                        outerDrawableField,
                    )
                } else {
                    deactivateOuterBlur(backgroundView, outerDrawableField)
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
            val type = resolveIslandType(chain.args.getOrNull(0))
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
        if (config.enabled && shapeView != null) {
            applyOuterBlur(backgroundView, shapeView, type, config, outerDrawableField)
        } else {
            // expanded_view can be inflated after the state callback. Never leave
            // the prior state's blur visible while waiting for that concrete View.
            deactivateOuterBlur(backgroundView, outerDrawableField)
            IslandBackgroundHook.restoreCustomBackground(backgroundView, type.name)
        }
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

    private fun applyOuterBlur(
        backgroundView: View,
        shapeView: View,
        type: IslandType,
        config: BlurConfig,
        drawableField: java.lang.reflect.Field,
    ): Boolean {
        return runCatching {
            if (!backgroundView.isAttachedToWindow) return@runCatching false
            val current = outerBlurs[backgroundView]
            val outer = if (current?.owned?.type == type) {
                current
            } else {
                val stock = current?.stockDrawable ?: drawableField.get(backgroundView) as? Drawable
                val owned = createBackgroundBlurDrawable(backgroundView, type)
                    ?: return@runCatching false
                current?.release()
                OuterBlur(owned, stock, drawableField).also { outerBlurs[backgroundView] = it }
            }
            ensureDetachCleanup(backgroundView)
            updateOwnedBlur(backgroundView, outer.owned, config, shapeView)
            val outlineEnabled = IslandOutlineHook.isOutlineEnabled(type == IslandType.EXPAND)
            if (IslandOutlineHook.hasOutline(outer.renderDrawable) != outlineEnabled) {
                IslandOutlineHook.releaseOutline(outer.renderDrawable)
                outer.renderDrawable.callback = null
                outer.renderDrawable = IslandOutlineHook.withOutline(
                    backgroundView,
                    outer.owned.drawable,
                    type == IslandType.EXPAND,
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
            val failed = outerBlurs.remove(backgroundView)
            failed?.release()
            if (failed != null) {
                runCatching { drawableField.set(backgroundView, failed.stockDrawable) }
                backgroundView.invalidate()
            }
        }.getOrDefault(false)
    }

    private fun deactivateOuterBlur(
        backgroundView: View,
        drawableField: java.lang.reflect.Field,
    ) {
        val outer = outerBlurs[backgroundView] ?: return
        runCatching { outer.owned.methods.setRadius.invoke(outer.owned.effectDrawable, 0) }
        if (runCatching { drawableField.get(backgroundView) }.getOrNull() === outer.renderDrawable) {
            runCatching { drawableField.set(backgroundView, outer.stockDrawable) }
        }
        outer.owned.active = false
        outer.active = false
        IslandOutlineHook.releaseOutline(outer.renderDrawable)
        outer.renderDrawable.callback = null
        outerBlurs.remove(backgroundView)
        backgroundView.invalidate()
    }

    private fun ensureDetachCleanup(backgroundView: View) {
        synchronized(detachListeners) {
            if (detachListeners.containsKey(backgroundView)) return
            val listener = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) = Unit

                override fun onViewDetachedFromWindow(view: View) {
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
            val clippedDrawable = ClippedBlurDrawable(
                drawable,
                resolveStrokeWidth(view),
            )
            OwnedBlur(
                drawable = clippedDrawable,
                effectDrawable = drawable,
                clippedDrawable = clippedDrawable,
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
        }.getOrNull()
    }

    private fun updateOwnedBlur(
        view: View,
        owned: OwnedBlur,
        config: BlurConfig,
        shapeView: View,
    ) {
        owned.methods.setRadius.invoke(owned.effectDrawable, config.radius)
        val radius = resolveCornerRadius(view, owned.type, shapeView)
        owned.clippedDrawable.setCornerRadius(radius)
        owned.methods.setCornerRadius.invoke(
            owned.effectDrawable,
            radius,
            radius,
            radius,
            radius,
        )
        owned.methods.setColor.invoke(owned.effectDrawable, config.blendColor)
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

    private fun resolveStrokeWidth(view: View): Int {
        return runCatching {
            (findMethod(view.javaClass, "getStokeWidth")?.invoke(view) as? Int) ?: 0
        }.getOrDefault(0).coerceAtLeast(0)
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
    ) {
        val enabled = enabled
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
        val stockDrawable: Drawable?,
        val drawableField: java.lang.reflect.Field,
        var active: Boolean = false,
    ) {
        var renderDrawable: Drawable = owned.drawable

        fun release() {
            runCatching { owned.methods.setRadius.invoke(owned.effectDrawable, 0) }
            IslandOutlineHook.releaseOutline(renderDrawable)
            renderDrawable.callback = null
            owned.active = false
            active = false
        }
    }

    private class OwnedBlur(
        val drawable: Drawable,
        val effectDrawable: Drawable,
        val clippedDrawable: ClippedBlurDrawable,
        val type: IslandType,
        val methods: BlurDrawableMethods,
        var active: Boolean = false,
    )

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

        override fun draw(canvas: Canvas) {
            val bounds = bounds
            val safeInset = inset.coerceAtMost(minOf(bounds.width(), bounds.height()) / 2)
            clipRect.set(
                (bounds.left + safeInset).toFloat(),
                (bounds.top + safeInset).toFloat(),
                (bounds.right - safeInset).toFloat(),
                (bounds.bottom - safeInset).toFloat(),
            )
            if (clipRect.isEmpty) return
            child.bounds = android.graphics.Rect(
                clipRect.left.toInt(),
                clipRect.top.toInt(),
                clipRect.right.toInt(),
                clipRect.bottom.toInt(),
            )
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
    }

    private data class RefreshTarget(
        val contentView: WeakReference<Any>,
        val updateMethod: Method,
        val promoted: Boolean,
        val type: IslandType?,
        val stateField: java.lang.reflect.Field,
    )

    private enum class IslandType { SMALL, BIG, EXPAND }
}
