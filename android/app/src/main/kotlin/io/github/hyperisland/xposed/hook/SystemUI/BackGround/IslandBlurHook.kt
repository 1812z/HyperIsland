package io.github.hyperisland.xposed.hook.SystemUI.BackGround

import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.hyperisland.xposed.hook.IslandBackgroundHook
import io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.IslandBlurRuntime
import io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.SystemUiReflection.findField
import io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.SystemUiReflection.findMethod
import io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.lifecycle.IslandStateResolver
import io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.model.BlurConfig
import io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.model.IslandType
import io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.model.MaterialConfig
import io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.model.MaterialType
import io.github.hyperisland.xposed.hook.SystemUI.SoftGlass.SoftGlassController
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
    private const val FAKE_CONTENT_VIEW_CLASS =
        "miui.systemui.dynamicisland.window.content.DynamicIslandContentFakeView"
    private const val BACKGROUND_VIEW_CLASS =
        "miui.systemui.dynamicisland.DynamicIslandBackgroundView"
    private const val CONTENT_VIEW_CONTROLLER_CLASS =
        "miui.systemui.dynamicisland.window.content.DynamicIslandContentViewController"
    private const val ANIMATION_DELEGATE_CLASS =
        "miui.systemui.dynamicisland.anim.DynamicIslandAnimationDelegate"
    private const val NO_CONTENT_CLEANUP_TIMEOUT_MS = 700L
    private val hookedContentClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    )
    private val hookedPreparationClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    )
    private val softOuterBackgrounds = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    )
    private val refreshTargets = Collections.synchronizedMap(
        WeakHashMap<View, RefreshTarget>()
    )
    private val controllerVisibility = Collections.synchronizedMap(
        WeakHashMap<Any, Boolean>()
    )
    private val controllerTargets = Collections.synchronizedMap(
        WeakHashMap<Any, WeakReference<View>>()
    )
    private val pendingNoContentCleanups = Collections.synchronizedMap(
        WeakHashMap<Any, PendingNoContentCleanup>()
    )
    private val contentLastTypes = Collections.synchronizedMap(
        WeakHashMap<Any, IslandType>()
    )
    private val pendingSoftCommits = Collections.synchronizedMap(
        WeakHashMap<View, PendingSoftCommit>()
    )
    private val mainHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = Runnable { refreshTrackedViews() }
    private val islandTypeHolder = ThreadLocal<IslandType>()
    private val preparingTypeHolder = ThreadLocal<IslandType>()
    private val configStore = IslandBlurRuntime.configStore
    private val outerBlurRegistry = IslandBlurRuntime.outerBlurRegistry

    @Volatile
    private var lastIslandType: IslandType? = null

    @Volatile
    private var islandTempHidden = false

    @Volatile
    private var tempHideRecoveryPending = false

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        if (param.packageName != "com.android.systemui") return
        configStore.reload()
        hookPlugin(module, param.defaultClassLoader)
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { classLoader ->
            hookPlugin(module, classLoader)
        }
    }

    override fun onConfigChanged() {
        configStore.reload()
        mainHandler.post {
            refreshTrackedSoftGlassViews()
            outerBlurRegistry.onConfigChanged()
        }
        mainHandler.removeCallbacks(refreshRunnable)
        if (configStore.anyMaterialEnabled) {
            mainHandler.postDelayed(refreshRunnable, 80L)
        }
    }

    private fun hookPlugin(module: XposedModule, classLoader: ClassLoader) {
        var installStage = "content-class"
        try {
            val contentClass = Class.forName(CONTENT_VIEW_CLASS, false, classLoader)
            installStage = "background-class"
            val backgroundClass = Class.forName(BACKGROUND_VIEW_CLASS, false, classLoader)
            installStage = "state-class"
            val stateClass = Class.forName(
                "miui.systemui.dynamicisland.event.DynamicIslandState",
                false,
                classLoader,
            )
            installStage = "window-class"
            val windowViewClass = Class.forName(
                "miui.systemui.dynamicisland.window.DynamicIslandWindowView",
                false,
                classLoader,
            )
            installStage = "blur-compat"
            val compatClass = sequenceOf(
                "miui.systemui.util.MiBlurCompat",
                "miui.util.MiBlurCompat",
            ).mapNotNull { name ->
                runCatching { Class.forName(name, false, classLoader) }.getOrNull()
            }.firstOrNull() ?: throw ClassNotFoundException("MiBlurCompat")
            installStage = "runtime-bind"
            SoftGlassController.bindRuntime(module, contentClass, compatClass)
            SoftGlassController.observeWindowLifecycle(module, windowViewClass)
            hookPreparationSources(module, classLoader)
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
            hookContentVisibility(
                module,
                classLoader,
                backgroundViewField,
                outerDrawableField,
            )
            hookAnimationCompletion(module, classLoader)
            hookTempHiddenLifecycle(module, windowViewClass)
            module.hook(updateMethod).intercept { chain ->
                val view = chain.args.getOrNull(0) as? View
                val contentViewBeforeUpdate = chain.thisObject
                val viewTypeBeforeUpdate = view?.let(IslandStateResolver::forView)
                val typeBeforeUpdate = viewTypeBeforeUpdate
                    ?: islandTypeHolder.get()
                    ?: runCatching {
                        IslandStateResolver.fromState(stateField.get(contentViewBeforeUpdate))
                    }.getOrNull()
                    ?: lastIslandType
                val materialBeforeUpdate = typeBeforeUpdate?.let(::materialForType)
                val preparingType = preparingTypeHolder.get()
                val stateTypeBeforeUpdate = islandTypeHolder.get() ?: runCatching {
                    IslandStateResolver.fromState(stateField.get(contentViewBeforeUpdate))
                }.getOrNull() ?: lastIslandType
                // updateExpandedView() initializes EXPAND before changing the ContentView state
                // or replacing its notification child. Mark that structural preparation so it
                // cannot be confused with either a stale callback or a visible EXPAND refresh.
                val preparingDestination = preparingType != null && preparingType == typeBeforeUpdate
                val hiddenPreparation = preparingDestination && stateTypeBeforeUpdate != typeBeforeUpdate
                val staleBeforeUpdate = typeBeforeUpdate != null && stateTypeBeforeUpdate != null &&
                    typeBeforeUpdate != stateTypeBeforeUpdate && !preparingDestination
                if (view != null && materialBeforeUpdate?.type != MaterialType.SOFT &&
                    SoftGlassController.isManaged(view)
                ) {
                    // Remove our old Bionics transaction before SystemUI or the Gaussian path
                    // installs the next material. Doing this afterwards would clear the new one.
                    SoftGlassController.release(
                        view,
                        restoreBackground = false,
                        releaseSampling = false,
                    )
                }
                if (view != null && materialBeforeUpdate?.type != MaterialType.SOFT) {
                    cancelVisibleSoftCommit(view)
                }
                val realOwner = contentViewBeforeUpdate?.javaClass?.name != FAKE_CONTENT_VIEW_CLASS
                var directSoftApplied = false
                var deferredSoftCommit = false
                val result = if (view != null && materialBeforeUpdate?.type == MaterialType.SOFT) {
                    if (staleBeforeUpdate || hiddenPreparation) {
                        // A hidden/non-current state must own neither a sampler lease nor a
                        // Bionics RenderNode. In particular, updateExpandedView() prepares the
                        // future EXPAND child while SMALL/BIG is still stable. Installing the
                        // material here lets its full-size crop contaminate the compact island
                        // even without a rendering lease. The fake-to-real handoff/onPreDraw
                        // installs it again before EXPAND can submit its first visible frame.
                        cancelVisibleSoftCommit(view)
                        SoftGlassController.release(view, restoreBackground = false)
                        null
                    } else if (realOwner && !isActuallyVisible(view)) {
                        // ShowOnceBigIsland/tempShow is created in a secondary ContentView. Its
                        // updateBackgroundBg callback runs while the concrete View is still
                        // detached/0x0, and the controller-level onPreDraw only observes the
                        // primary ContentView. Installing Bionics now prepares a RenderNode but
                        // cannot acquire a sampler lease; Xiaomi then closes pass blur in the
                        // same transaction and the settled charging island becomes transparent.
                        // Defer both material and lease to this exact View's first visible frame.
                        SoftGlassController.release(view, restoreBackground = false)
                        deferredSoftCommit = true
                        null
                    } else {
                        // Same lifecycle edge as Gaussian, different renderer implementation.
                        directSoftApplied = SoftGlassController.apply(
                            view,
                            materialBeforeUpdate.softGlass,
                        )
                        if (directSoftApplied) null else chain.proceed()
                    }
                } else {
                    chain.proceed()
                }
                view ?: return@intercept result
                val contentView = chain.thisObject ?: return@intercept result
                val type = typeBeforeUpdate
                if (type != null && !hiddenPreparation) contentLastTypes[contentView] = type
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
                val stateType = stateTypeBeforeUpdate
                // The shared state field can still contain BIG while expanded_view is
                // already laid out for a focus notification. The target view is authoritative.
                if (backgroundView == null) return@intercept result

                val material = materialForType(type)
                val staleUpdate = stateType != null && type != stateType && !preparingDestination
                // updateExpandedView() prepares a hidden EXPAND child while SMALL/BIG still owns
                // this shared outer drawable. No material may replace or release that outer here.
                val outerShapeView = if (viewTypeBeforeUpdate != null) {
                    view
                } else {
                    IslandStateResolver.concreteView(contentView, type)
                }
                val active = if (hiddenPreparation) {
                    false
                } else if (material.type == MaterialType.SOFT) {
                    if (deferredSoftCommit) {
                        // Do not mutate the currently visible SMALL/BIG outer layer for a hidden
                        // EXPAND preparation. This callback is ownership metadata only.
                        if (deferredSoftCommit && realOwner) {
                            armVisibleSoftCommit(
                                view,
                                type,
                                backgroundViewField,
                                outerDrawableField,
                            )
                        }
                        false
                    } else if (directSoftApplied) {
                        softOuterBackgrounds.add(backgroundView)
                        if (realOwner && !hiddenPreparation && !staleUpdate &&
                            isActuallyVisible(view)
                        ) {
                            SoftGlassController.beginRendering(view)
                        }
                        clearOuterForSoftGlass(
                            backgroundView,
                            outerDrawableField,
                            "soft-glass",
                        )
                        true
                    } else if (staleUpdate) {
                        // OS3 has no Bionics renderer and reaches the Gaussian fallback below.
                        // That fallback owns the shared outer drawable, so a hidden stale state
                        // must not replace the currently visible SMALL/BIG/EXPAND drawable.
                        false
                    } else {
                        // Unsupported Bionics devices use the exact Gaussian host pipeline.
                        val shapeView = outerShapeView ?: return@intercept result
                        softOuterBackgrounds.add(backgroundView)
                        IslandBackgroundHook.clearManagedVisualMask(shapeView)
                        applyOuterBlur(
                            backgroundView,
                            shapeView,
                            type,
                            material.softFallback(),
                            outerDrawableField,
                        )
                    }
                } else if (staleUpdate) {
                    false
                } else if (config.isActive) {
                    val shapeView = outerShapeView ?: return@intercept result
                    softOuterBackgrounds.remove(backgroundView)
                    applyOuterBlur(
                        backgroundView,
                        shapeView,
                        type,
                        config,
                        outerDrawableField,
                    )
                } else {
                    softOuterBackgrounds.remove(backgroundView)
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
            module.logWarn("native island blur hook installed loader=$classLoader")
        } catch (e: ClassNotFoundException) {
            // Most process/plugin loaders are irrelevant and legitimately miss the content
            // class. Once that class resolves, every later miss is a real compatibility fault.
            if (installStage != "content-class") {
                module.logWarn("hook installation stopped at $installStage: ${e.message}")
            }
        } catch (e: Throwable) {
            module.logError("hook installation failed at $installStage: ${e.stackTraceToString()}")
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
                IslandStateResolver.fromState(target.stateField.get(contentView))
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
            val type = IslandStateResolver.fromState(state)
            val noContent = IslandStateResolver.isNoContent(state)
            val contentView = chain.thisObject
            val previousType = contentView?.let { contentLastTypes[it] } ?: lastIslandType
            val leavingSoft = noContent && previousType?.let(::materialForType)?.type ==
                MaterialType.SOFT
            if (type != null) {
                if (contentView != null) contentLastTypes[contentView] = type
                islandTypeHolder.set(type)
                lastIslandType = type
                cancelNoContentCleanup(contentView)
            }
            try {
                val result = chain.proceed()
                if (type != null && materialForType(type).isCustom &&
                    materialForType(type).type != MaterialType.SOFT
                ) {
                    mainHandler.removeCallbacks(refreshRunnable)
                    mainHandler.post(refreshRunnable)
                }
                if (type != null) {
                    val material = materialForType(type)
                    if (material.type == MaterialType.SOFT) {
                        // Acquire destination before releasing the previous state, matching the
                        // Gaussian handoff's continuous ownership of the same window surface.
                        refreshConcreteIslandViews(chain.thisObject, updateMethod, type)
                        releaseInactiveConcreteSoftGlass(chain.thisObject, type)
                        synchronizeOuterVisual(
                            chain.thisObject,
                            type,
                            stateField,
                            backgroundViewField,
                            outerDrawableField,
                        )
                    } else {
                        // A default/Gaussian EXPAND must not retain the hidden BIG Bionics
                        // RenderNode; otherwise the continuous pass texture keeps the compact crop.
                        releaseAllConcreteSoftGlass(
                            chain.thisObject,
                            preserveSystemSampling = true,
                        )
                        mainHandler.post {
                            synchronizeOuterVisual(
                                chain.thisObject,
                                type,
                                stateField,
                                backgroundViewField,
                                outerDrawableField,
                            )
                            if (material.isCustom) {
                                refreshConcreteIslandViews(chain.thisObject, updateMethod, type)
                            }
                        }
                    }
                } else if (noContent) {
                    if (leavingSoft) {
                        releaseAllConcreteSoftGlass(contentView)
                        // Hidden/Deleted installs the stock dark outer drawable at the start of
                        // the shrink. The fake Bionics View is the complete SOFT transition, so
                        // remove that black writer while SystemUI owns the sampling lifecycle.
                        val backgroundView = runCatching {
                            backgroundViewField.get(contentView) as? View
                        }.getOrNull()
                        if (backgroundView != null) {
                            clearOuterForSoftGlass(
                                backgroundView,
                                outerDrawableField,
                                "soft-glass-no-content",
                            )
                        }
                    }
                    // SOFT owns no outer BlurDrawable after the source clear above. Polling its
                    // geometry until timeout was an obsolete workaround and only added UI-thread
                    // work. Gaussian materials still need the delayed drawable release below.
                    if (!leavingSoft) {
                        scheduleNoContentCleanup(
                            contentView,
                            stateField,
                            backgroundViewField,
                            outerDrawableField,
                        )
                    }
                }
                result
            } finally {
                islandTypeHolder.remove()
            }
        }
    }

    private fun materialForType(type: IslandType): MaterialConfig = configStore.materialFor(type)

    /** Hidden/Deleted starts the disappearance animation; its completion hook performs release. */
    private fun scheduleNoContentCleanup(
        contentView: Any,
        stateField: java.lang.reflect.Field,
        backgroundViewField: java.lang.reflect.Field,
        outerDrawableField: java.lang.reflect.Field,
    ) {
        cancelNoContentCleanup(contentView)
        val cleanup = Runnable {
            val currentState = runCatching { stateField.get(contentView) }.getOrNull()
            if (!IslandStateResolver.isNoContent(currentState)) {
                pendingNoContentCleanups.remove(contentView)
                return@Runnable
            }
            finishNoContentCleanup(contentView, "no-content-timeout")
        }
        pendingNoContentCleanups[contentView] = PendingNoContentCleanup(
            cleanup,
            stateField,
            backgroundViewField,
            outerDrawableField,
        )
        mainHandler.postDelayed(cleanup, NO_CONTENT_CLEANUP_TIMEOUT_MS)
    }

    private fun cancelNoContentCleanup(contentView: Any?) {
        if (contentView == null) return
        pendingNoContentCleanups.remove(contentView)?.let { pending ->
            mainHandler.removeCallbacks(pending.timeout)
        }
    }

    private fun finishNoContentCleanup(contentView: Any?, reason: String) {
        contentView ?: return
        val pending = pendingNoContentCleanups.remove(contentView) ?: return
        mainHandler.removeCallbacks(pending.timeout)
        val currentState = runCatching { pending.stateField.get(contentView) }.getOrNull()
        if (!IslandStateResolver.isNoContent(currentState)) return
        val backgroundView = runCatching {
            pending.backgroundViewField.get(contentView) as? View
        }.getOrNull() ?: return
        deactivateOuterBlur(backgroundView, pending.outerDrawableField, reason)
        contentLastTypes.remove(contentView)
        if (outerBlurRegistry.isEmpty()) lastIslandType = null
    }

    /** SystemUI toggles this flag from both Folme completion and cancellation callbacks. */
    private fun hookAnimationCompletion(module: XposedModule, classLoader: ClassLoader) {
        val delegateClass = runCatching {
            Class.forName(ANIMATION_DELEGATE_CLASS, false, classLoader)
        }.getOrNull() ?: return
        val contentViewField = findField(delegateClass, "view") ?: return
        delegateClass.declaredMethods
            .filter { method ->
                method.name == "setAnimating" &&
                    method.parameterCount == 1 &&
                    method.parameterTypes[0] == Boolean::class.javaPrimitiveType
            }
            .forEach { method ->
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    if (chain.args.getOrNull(0) == false) {
                        val contentView = runCatching {
                            contentViewField.get(chain.thisObject)
                        }.getOrNull()
                        finishNoContentCleanup(contentView, "no-content-animation-finished")
                    }
                    result
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
            IslandType.EXPAND -> "getExpandedView"
        }
        // SOFT intentionally has no BlurDrawable config, so toBlurConfig().isActive is false.
        // Gating this refresh on BlurConfig silently skipped the real SMALL/BIG views while
        // the fake animation FrameLayouts still received Bionics, producing a transparent
        // settled island after an apparently-correct transition.
        val material = materialForType(type)
        if (material.type != MaterialType.SOFT && !configForType(type).isActive) return
        val getter = findMethod(contentView.javaClass, getterName)
        val view = runCatching { getter?.invoke(contentView) as? View }.getOrNull()
        if (view == null) return
        if (material.type == MaterialType.SOFT && SoftGlassController.isActive(view)) return
        runCatching { updateMethod.invoke(contentView, view, false) }
    }

    /** Marks View creation methods whose background callback prepares a future logical state. */
    private fun hookPreparationSources(module: XposedModule, classLoader: ClassLoader) {
        sequenceOf(
            "miui.systemui.dynamicisland.window.content.DynamicIslandContentView",
            "miui.systemui.dynamicisland.window.content.DynamicIslandContentFakeView",
        ).mapNotNull { name ->
            runCatching { Class.forName(name, false, classLoader) }.getOrNull()
        }.forEach { clazz ->
            if (!hookedPreparationClasses.add(clazz)) return@forEach
            clazz.declaredMethods
                .filter { it.name == "updateExpandedView" }
                .forEach { method ->
                    method.isAccessible = true
                    module.hook(method).intercept { chain ->
                        preparingTypeHolder.set(IslandType.EXPAND)
                        try {
                            chain.proceed()
                        } finally {
                            preparingTypeHolder.remove()
                        }
                    }
                }
        }
    }

    /** Only the settled real state may own a Bionics RenderNode crop. */
    private fun releaseInactiveConcreteSoftGlass(contentView: Any?, activeType: IslandType) {
        contentView ?: return
        IslandType.entries.asSequence()
            .filter { it != activeType }
            .mapNotNull { IslandStateResolver.concreteView(contentView, it) }
            .filter(SoftGlassController::isManaged)
            .forEach(SoftGlassController::suspend)
    }

    private fun releaseAllConcreteSoftGlass(
        contentView: Any?,
        preserveSystemSampling: Boolean = false,
    ) {
        contentView ?: return
        IslandType.entries.asSequence()
            .mapNotNull { IslandStateResolver.concreteView(contentView, it) }
            .filter(SoftGlassController::isManaged)
            .forEach { view ->
                SoftGlassController.release(
                    view,
                    restoreBackground = false,
                    releaseSampling = !preserveSystemSampling,
                )
            }
    }

    private fun hookBackgroundDrawing(
        module: XposedModule,
        backgroundClass: Class<*>,
        drawableField: java.lang.reflect.Field,
    ) {
        val setDrawable = backgroundClass.getDeclaredMethod(
            "setDrawable",
            android.graphics.drawable.Drawable::class.java,
        )
        module.hook(setDrawable).intercept { chain ->
            val backgroundView = chain.thisObject as? View ?: return@intercept chain.proceed()
            val transitionType = islandTypeHolder.get()
            val softConfigured = if (transitionType != null) {
                materialForType(transitionType).type == MaterialType.SOFT
            } else {
                softOuterBackgrounds.contains(backgroundView)
            }
            val suppressStock = softConfigured
            if (!suppressStock) return@intercept chain.proceed()
            // updateDarkLightMode writes the opaque outer drawable before any post-hook can run.
            // Reject it at the owning setter so no black drawable can enter a submitted frame.
            drawableField.set(backgroundView, null)
            backgroundView.invalidate()
            null
        }
        val method = backgroundClass.getDeclaredMethod("onDraw", Canvas::class.java)
        module.hook(method).intercept { chain ->
            val backgroundView = chain.thisObject as? View ?: return@intercept chain.proceed()
            if (!configStore.anyBlurEnabled) return@intercept chain.proceed()

            outerBlurRegistry.prepareDraw(backgroundView, drawableField, islandTempHidden)
            // The stock black/custom drawable belongs to SystemUI or the background hook.
            chain.proceed()
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
                true -> if (!wasHidden) {
                    tempHideRecoveryPending = false
                    (chain.thisObject as? View)?.let(SoftGlassController::pauseWindow)
                    enterTempHidden()
                }
                false, null -> Unit
            }
            val result = chain.proceed()
            if (hidden == false && wasHidden) {
                // The false callback precedes restoration of visible geometry. Keep
                // the reusable drawable protected until a real target can be drawn.
                islandTempHidden = false
                tempHideRecoveryPending = true
                mainHandler.removeCallbacks(refreshRunnable)
                mainHandler.post(refreshRunnable)
                val recoveryViews = outerBlurRegistry.rebuildRecoveryQueue()
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
        return outerBlurRegistry.shouldKeepOpaque(backgroundView)
    }

    private fun enterTempHidden() {
        islandTempHidden = true
        outerBlurRegistry.enterTempHidden()
    }

    /**
     * Commits SOFT on the concrete View's own first visible frame.
     *
     * The controller exposes only its primary ContentView. ShowOnceBigIsland (charging and
     * similar transient islands) lives in currentTempShow, so a controller-only pre-draw hook
     * can never acquire its sampler lease. A target-local listener covers every real content
     * slot without maintaining a template/event whitelist.
     */
    private fun armVisibleSoftCommit(
        view: View,
        expectedType: IslandType,
        backgroundViewField: java.lang.reflect.Field,
        outerDrawableField: java.lang.reflect.Field,
    ) {
        synchronized(pendingSoftCommits) {
            if (pendingSoftCommits.containsKey(view)) return
        }

        val targetRef = WeakReference(view)
        lateinit var preDrawListener: ViewTreeObserver.OnPreDrawListener
        lateinit var attachListener: View.OnAttachStateChangeListener

        preDrawListener = ViewTreeObserver.OnPreDrawListener {
            val target = targetRef.get() ?: return@OnPreDrawListener true
            val actualType = IslandStateResolver.forView(target) ?: expectedType
            if (actualType != expectedType ||
                materialForType(actualType).type != MaterialType.SOFT
            ) {
                cancelVisibleSoftCommit(target)
                return@OnPreDrawListener true
            }
            val owner = findOwningContentView(target)
            if (owner == null) return@OnPreDrawListener true
            val ownerState = runCatching {
                findField(owner.javaClass, "state")?.get(owner)
            }.getOrNull()
            val ownerType = IslandStateResolver.fromState(ownerState)
            if (IslandStateResolver.isNoContent(ownerState) ||
                (ownerType != null && ownerType != expectedType)
            ) {
                cancelVisibleSoftCommit(target)
                return@OnPreDrawListener true
            }
            if (!isActuallyVisible(target)) return@OnPreDrawListener true

            commitVisibleSoftTarget(
                owner,
                target,
                actualType,
                backgroundViewField,
                outerDrawableField,
            )
            cancelVisibleSoftCommit(target)
            true
        }
        attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(target: View) {
                registerVisibleSoftPreDraw(target)
            }

            override fun onViewDetachedFromWindow(target: View) {
                cancelVisibleSoftCommit(target)
            }
        }
        val pending = PendingSoftCommit(
            preDrawListener = preDrawListener,
            attachListener = attachListener,
        )
        pendingSoftCommits[view] = pending
        view.addOnAttachStateChangeListener(attachListener)
        if (view.isAttachedToWindow) registerVisibleSoftPreDraw(view)
    }

    private fun registerVisibleSoftPreDraw(view: View) {
        val pending = pendingSoftCommits[view] ?: return
        val observer = view.viewTreeObserver
        val previousObserver = pending.observer?.get()
        if (!observer.isAlive || previousObserver === observer) return
        previousObserver?.takeIf(ViewTreeObserver::isAlive)
            ?.removeOnPreDrawListener(pending.preDrawListener)
        observer.addOnPreDrawListener(pending.preDrawListener)
        pending.observer = WeakReference(observer)
    }

    private fun cancelVisibleSoftCommit(view: View) {
        val pending = pendingSoftCommits.remove(view) ?: return
        pending.observer?.get()?.takeIf(ViewTreeObserver::isAlive)
            ?.removeOnPreDrawListener(pending.preDrawListener)
        view.removeOnAttachStateChangeListener(pending.attachListener)
    }

    private fun findOwningContentView(view: View): Any? {
        var current: View? = view
        while (current != null) {
            var clazz: Class<*>? = current.javaClass
            while (clazz != null) {
                if (clazz.name == CONTENT_VIEW_CLASS) return current
                clazz = clazz.superclass
            }
            current = current.parent as? View
        }
        return null
    }

    private fun commitVisibleSoftTarget(
        contentView: Any,
        target: View,
        type: IslandType,
        backgroundViewField: java.lang.reflect.Field,
        outerDrawableField: java.lang.reflect.Field,
    ): Boolean {
        val material = materialForType(type)
        if (material.type != MaterialType.SOFT || !isActuallyVisible(target)) return false

        val backgroundView = runCatching {
            backgroundViewField.get(contentView) as? View
        }.getOrNull()
        val nativeReady = SoftGlassController.isManaged(target) ||
            SoftGlassController.apply(target, material.softGlass)
        if (nativeReady) {
            SoftGlassController.beginRendering(target)
            if (backgroundView != null) {
                softOuterBackgrounds.add(backgroundView)
                clearOuterForSoftGlass(backgroundView, outerDrawableField, "target-visible")
            }
            IslandBackgroundHook.clearCommittedSoftLayers(contentView, type.name)
            return true
        }

        // OS3 and unusual View hosts retain the same source lifecycle but use the supported
        // Gaussian host renderer. Do not clear the stock layer until that renderer commits.
        if (backgroundView == null) return false
        softOuterBackgrounds.add(backgroundView)
        val fallbackReady = applyOuterBlur(
            backgroundView,
            target,
            type,
            material.softFallback(),
            outerDrawableField,
        )
        if (fallbackReady && outerBlurRegistry.hasActiveVisual(backgroundView)) {
            IslandBackgroundHook.clearManagedVisualMask(target)
        }
        return fallbackReady
    }

    /** Restores the 2.4.8 visibility edge that was removed while retaining blur reuse. */
    private fun hookContentVisibility(
        module: XposedModule,
        classLoader: ClassLoader,
        backgroundViewField: java.lang.reflect.Field,
        outerDrawableField: java.lang.reflect.Field,
    ) {
        val controllerClass = runCatching {
            Class.forName(CONTENT_VIEW_CONTROLLER_CLASS, false, classLoader)
        }.getOrNull() ?: return
        val currentIslandVisible = findMethod(controllerClass, "currentIslandVisible") ?: return
        val getView = findMethod(controllerClass, "getView") ?: return
        controllerClass.declaredMethods
            .filter { method ->
                method.name == "onViewAttached" &&
                    method.parameterCount == 1 &&
                    method.parameterTypes[0] == Boolean::class.javaPrimitiveType
            }
            .forEach { method ->
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    if (chain.args.getOrNull(0) == false) {
                        val contentView = runCatching { getView.invoke(chain.thisObject) }.getOrNull()
                        finishNoContentCleanup(contentView, "no-content-detached")
                        controllerVisibility.remove(chain.thisObject)
                        controllerTargets.remove(chain.thisObject)
                    }
                    result
                }
            }
        controllerClass.declaredMethods
            .filter { it.name == "onPreDraw" && it.parameterCount == 0 }
            .forEach { method ->
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    val controller = chain.thisObject
                    val visible = runCatching {
                        currentIslandVisible.invoke(controller) as? Boolean
                    }.getOrNull()
                    val previouslyVisible = if (visible != null) {
                        controllerVisibility.put(controller, visible)
                    } else {
                        null
                    }
                    val contentView = runCatching { getView.invoke(controller) }.getOrNull()
                    val stateType = contentView?.let { owner ->
                        runCatching {
                            IslandStateResolver.fromState(
                                findField(owner.javaClass, "state")?.get(owner),
                            )
                        }.getOrNull() ?: contentLastTypes[owner]
                    }
                    val stateConcrete = if (contentView != null && stateType != null) {
                        IslandStateResolver.concreteView(contentView, stateType)
                    } else {
                        null
                    }
                    val rendered = if (stateConcrete != null && isActuallyVisible(stateConcrete)) {
                        stateType?.let { it to stateConcrete }
                    } else if (contentView != null) {
                        sequenceOf(IslandType.EXPAND, IslandType.BIG, IslandType.SMALL)
                            .mapNotNull { candidate ->
                                IslandStateResolver.concreteView(contentView, candidate)
                                    ?.takeIf(::isActuallyVisible)
                                    ?.let { candidate to it }
                            }
                            .firstOrNull()
                    } else {
                        null
                    }
                    val type = rendered?.first ?: stateType
                    val concrete = rendered?.second ?: stateConcrete
                    val visibleConcrete = concrete?.takeIf(::isActuallyVisible)
                    val previousTarget = if (visibleConcrete != null) {
                        controllerTargets.put(controller, WeakReference(visibleConcrete))?.get()
                    } else {
                        controllerTargets[controller]?.get()
                    }
                    val targetChanged = visibleConcrete != null && previousTarget !== visibleConcrete
                    val noContent = contentView != null && pendingNoContentCleanups.containsKey(contentView)
                    val contentAnimating = contentView?.let { owner ->
                        runCatching {
                            findMethod(owner.javaClass, "isAnimating")?.invoke(owner) as? Boolean
                        }.getOrNull()
                    } == true
                    if (visible == false && noContent && !contentAnimating && !islandTempHidden) {
                        // Covers direct removal where Folme never starts and has no completion
                        // callback. Allow two frames for a deferred Folme start before release.
                        mainHandler.postDelayed({
                            if (!pendingNoContentCleanups.containsKey(contentView)) return@postDelayed
                            val visibleNow = runCatching {
                                currentIslandVisible.invoke(controller) as? Boolean
                            }.getOrNull()
                            val animatingNow = runCatching {
                                findMethod(contentView.javaClass, "isAnimating")
                                    ?.invoke(contentView) as? Boolean
                            }.getOrNull() == true
                            if (visibleNow == false && !animatingNow && !islandTempHidden) {
                                finishNoContentCleanup(contentView, "no-content-not-rendered")
                            }
                        }, 32L)
                    }
                    if (previouslyVisible != false && visible == false) {
                        val backgroundView = runCatching {
                            backgroundViewField.get(contentView) as? View
                        }.getOrNull()
                        if (backgroundView != null) {
                            outerBlurRegistry.hideEdgeHighlight(backgroundView)
                        }
                        if (concrete != null && SoftGlassController.isManaged(concrete)) {
                            if (islandTempHidden) {
                                SoftGlassController.pauseRendering(concrete)
                            } else {
                                // SystemUI's own currentIslandVisible() is authoritative. A
                                // sibling hidden by EXPAND must no longer submit a compact crop.
                                SoftGlassController.release(concrete, restoreBackground = false)
                            }
                        }
                    } else if (visible == true &&
                        (previouslyVisible != true || tempHideRecoveryPending || targetChanged)
                    ) {
                        // This is the source pre-draw edge for both first appearance and temporary
                        // hide recovery. Acquire before the frame instead of repairing afterwards.
                        if (contentView != null && visibleConcrete != null && type != null &&
                            materialForType(type).type == MaterialType.SOFT
                        ) {
                            commitVisibleSoftTarget(
                                contentView,
                                visibleConcrete,
                                type,
                                backgroundViewField,
                                outerDrawableField,
                            )
                            tempHideRecoveryPending = false
                        }
                    }
                    result
                }
            }
    }

    private fun isActuallyVisible(view: View): Boolean {
        if (!view.isShown || view.windowVisibility != View.VISIBLE || view.alpha <= 0.01f) {
            return false
        }
        var current: View? = view
        while (current != null) {
            if (current.visibility != View.VISIBLE || current.alpha <= 0.01f) return false
            current = current.parent as? View
        }
        return true
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
                IslandStateResolver.fromState(stateField.get(contentView))
            }.getOrNull() != type
        ) return
        val backgroundView = runCatching {
            backgroundViewField.get(contentView) as? View
        }.getOrNull() ?: return
        val config = configForType(type)
        val shapeView = IslandStateResolver.concreteView(contentView, type)
        val material = materialForType(type)
        if (material.type == MaterialType.SOFT) {
            val active = shapeView != null && SoftGlassController.isActive(shapeView)
            if (active) {
                clearOuterForSoftGlass(backgroundView, outerDrawableField, "soft-glass-sync")
            }
            return
        }
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

    private fun configForType(type: IslandType): BlurConfig = configStore.blurFor(type)

    /** Re-runs SystemUI's own material writer when module configuration changes. */
    private fun refreshTrackedSoftGlassViews() {
        val targets = synchronized(refreshTargets) {
            refreshTargets.entries.mapNotNull { (view, target) ->
                val contentView = target.contentView.get() ?: return@mapNotNull null
                target.type?.let { type -> Triple(view, contentView, target) }
            }
        }
        targets.forEach { (view, contentView, target) ->
            if (!view.isAttachedToWindow) return@forEach
            val type = target.type ?: return@forEach
            val material = materialForType(type)
            val currentType = runCatching {
                IslandStateResolver.fromState(target.stateField.get(contentView))
            }.getOrNull()
            if (currentType != type) {
                if (SoftGlassController.isManaged(view)) {
                    SoftGlassController.release(
                        view,
                        restoreBackground = false,
                        releaseSampling = false,
                    )
                }
                return@forEach
            }
            if (material.type == MaterialType.SOFT) {
                runCatching { target.updateMethod.invoke(contentView, view, target.promoted) }
            } else {
                if (SoftGlassController.isManaged(view)) {
                    SoftGlassController.release(
                        view,
                        restoreBackground = false,
                        releaseSampling = false,
                    )
                }
                runCatching { target.updateMethod.invoke(contentView, view, target.promoted) }
                val backgroundView = findMethod(contentView.javaClass, "getBackgroundView")
                    ?.let { method ->
                        runCatching { method.invoke(contentView) as? View }.getOrNull()
                    }
                if (backgroundView != null) {
                    softOuterBackgrounds.remove(backgroundView)
                    IslandBackgroundHook.restoreSuppressedSystemDrawable(backgroundView)
                }
            }
        }
    }

    private fun clearOuterForSoftGlass(
        backgroundView: View,
        drawableField: java.lang.reflect.Field,
        reason: String,
    ) {
        deactivateOuterBlur(backgroundView, drawableField, reason)
        runCatching { drawableField.set(backgroundView, null) }
        backgroundView.invalidate()
    }

    /** Clears the shared stock drawable for a real content View owned by SOFT. */
    internal fun clearSoftGlassOuter(contentView: Any, reason: String) {
        val backgroundView = findMethod(contentView.javaClass, "getBackgroundView")
            ?.let { runCatching { it.invoke(contentView) as? View }.getOrNull() }
            ?: return
        val drawableField = findField(backgroundView.javaClass, "drawable") ?: return
        clearOuterForSoftGlass(backgroundView, drawableField, reason)
    }

    private fun applyOuterBlur(
        backgroundView: View,
        shapeView: View,
        type: IslandType,
        config: BlurConfig,
        drawableField: java.lang.reflect.Field,
    ): Boolean = outerBlurRegistry.apply(
        backgroundView,
        shapeView,
        type,
        config,
        drawableField,
        islandTempHidden,
    )

    private fun deactivateOuterBlur(
        backgroundView: View,
        drawableField: java.lang.reflect.Field,
        reason: String = "state-disabled",
    ) = outerBlurRegistry.deactivate(backgroundView, drawableField, reason)

    private data class RefreshTarget(
        val contentView: WeakReference<Any>,
        val updateMethod: Method,
        val promoted: Boolean,
        val type: IslandType?,
        val stateField: java.lang.reflect.Field,
    )

    private data class PendingSoftCommit(
        val preDrawListener: ViewTreeObserver.OnPreDrawListener,
        val attachListener: View.OnAttachStateChangeListener,
        var observer: WeakReference<ViewTreeObserver>? = null,
    )

    private data class PendingNoContentCleanup(
        val timeout: Runnable,
        val stateField: java.lang.reflect.Field,
        val backgroundViewField: java.lang.reflect.Field,
        val outerDrawableField: java.lang.reflect.Field,
    )

}
