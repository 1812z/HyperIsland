package io.github.hyperisland.xposed.hook.SystemUI

import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.View
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.hyperisland.xposed.hook.IslandBackgroundHook
import io.github.hyperisland.xposed.hook.SystemUI.BackGround.IslandBlurHook
import io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.IslandBlurRuntime
import io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.lifecycle.IslandStateResolver
import io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.model.IslandType
import io.github.hyperisland.xposed.hook.SystemUI.SoftGlass.SoftGlassController
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

/** Keeps custom island visuals visible while SystemUI animates its fake transition views. */
object IslandTransitionVisualHook : BaseHook() {
    private const val TAG = "HyperIsland[TransitionVisual]"
    private const val FAKE_VIEW_CLASS =
        "miui.systemui.dynamicisland.window.content.DynamicIslandContentFakeView"
    private const val ANIMATION_DELEGATE_CLASS =
        "miui.systemui.dynamicisland.anim.DynamicIslandAnimationDelegate"
    private const val SELF_BLUR_COMPANION_CLASS =
        "miui.systemui.dynamicisland.anim.DynamicIslandAnimationController\$Companion"
    private const val NO_FAKE_SLOT = "HIDDEN"
    private const val MAX_FAKE_SELECTIONS = 128
    private val FAKE_VIEW_ANIMATOR_CLASSES = arrayOf(
        "miui.systemui.dynamicisland.anim.ui.animator.FakeViewAnimator",
        "miui.systemui.dynamicisland.anim.p110ui.animator.FakeViewAnimator",
        "miui.systemui.dynamicisland.anim.p120ui.animator.FakeViewAnimator",
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val hookedFakeClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    )
    private val hookedDelegateClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    )
    private val hookedAnimatorClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    )
    private val hookedSelfBlurClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    )
    private val fakeViews = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
    )
    private val targets = Collections.synchronizedMap(
        WeakHashMap<View, TransitionTarget>()
    )
    private val fakeSlotTypes = Collections.synchronizedMap(
        WeakHashMap<View, String>()
    )
    private val activeFakeTypes = Collections.synchronizedMap(
        WeakHashMap<Any, String>()
    )
    private val fakeLayerTargets = Collections.synchronizedMap(
        WeakHashMap<Any, FakeLayerTarget>()
    )
    private val opaqueHandoffBackgrounds = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
    )
    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        if (param.packageName != "com.android.systemui") return
        hookPlugin(module, param.defaultClassLoader)
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { classLoader ->
            hookPlugin(module, classLoader)
        }
    }

    override fun onConfigChanged() {
        // IslandBlurHook is registered first and owns the single shared snapshot reload.
        // Reloading it again here increments the revision twice and resubmits every fake slot.
        mainHandler.post {
            synchronized(fakeViews) { fakeViews.toList() }.forEach { fakeView ->
                refreshFakeView(fakeView)
            }
        }
    }

    private fun hookPlugin(module: XposedModule, classLoader: ClassLoader) {
        val fakeClass = runCatching {
            Class.forName(FAKE_VIEW_CLASS, false, classLoader)
        }.getOrNull() ?: return
        val access = fakeAccess(fakeClass)
        if (!hookedFakeClasses.add(fakeClass)) {
            hookAnimationClasses(module, classLoader, access)
            return
        }
        hookDeclaredRefreshAfter(module, fakeClass, "onFinishInflate") { owner ->
            fakeViews.add(owner)
            refreshFakeView(owner, access)
            if ((owner as? View)?.visibility == View.VISIBLE) {
                armRealBackgroundHandoff(owner, access)
            }
        }
        hookDeclaredRefreshAfter(
            module,
            fakeClass,
            "updateLiveUpdateExpandedView",
            Boolean::class.javaPrimitiveType!!,
        ) { owner ->
            refreshFakeView(owner, access)
        }
        hookFakeVisibility(module, fakeClass, access)
        hookRestoreFakeBackground(module, fakeClass, access)
        hookSelfBlurSource(module, classLoader)
        hookDeclaredRefreshAfter(module, fakeClass, "onDetachedFromWindow") { owner ->
            releaseFakeView(owner, access)
        }

        hookAnimationClasses(module, classLoader, access)
        log(module) { "fake island transition visuals hooked" }
    }

    private fun hookAnimationClasses(
        module: XposedModule,
        classLoader: ClassLoader,
        access: FakeViewAccess,
    ) {
        hookAnimationDelegate(module, classLoader, access)
        hookFakeViewAnimator(module, classLoader, access)
    }

    private fun hookAnimationDelegate(
        module: XposedModule,
        classLoader: ClassLoader,
        access: FakeViewAccess,
    ) {
        val delegateClass = runCatching {
            Class.forName(ANIMATION_DELEGATE_CLASS, false, classLoader)
        }.getOrNull() ?: return
        if (!hookedDelegateClasses.add(delegateClass)) return
        val backgroundClass = runCatching {
            Class.forName(
                "miui.systemui.dynamicisland.DynamicIslandBackgroundView",
                false,
                classLoader,
            )
        }.getOrNull()
        if (backgroundClass != null) hookHandoffBackgroundAlpha(module, backgroundClass)
        val getFakeView = findMethod(delegateClass, "getFakeView") ?: return
        val hasDedicatedFakeAnimator = findMethod(delegateClass, "getFakeViewAnimator") != null ||
            findFirstClass(classLoader, FAKE_VIEW_ANIMATOR_CLASSES) != null
        val refreshMethods = if (hasDedicatedFakeAnimator) {
            emptySequence<String>()
        } else {
            sequenceOf("updateFakeViewAnimState", "containerScheduleUpdate", "scheduleUpdate")
        }
        refreshMethods.forEach { name ->
            val method = delegateClass.declaredMethods.firstOrNull {
                it.name == name && it.parameterCount == 0
            } ?: return@forEach
            runCatching {
                hookRefreshAfter(module, method) { delegate ->
                    val fakeView = runCatching { getFakeView.invoke(delegate) }.getOrNull()
                        ?: return@hookRefreshAfter
                    if (fakeView is View && fakeView.visibility != View.VISIBLE) {
                        return@hookRefreshAfter
                    }
                    fakeViews.add(fakeView)
                    armRealBackgroundHandoff(fakeView, access)
                    refreshFakeView(fakeView, access)
                }
            }.onFailure { error ->
                logError(module, "failed to hook ${delegateClass.name}.$name: ${error.message}")
            }
        }
    }

    /**
     * FakeViewAnimator and IslandPropertyUpdater converge here before View.setMiSelfBlur().
     * Skipping either caller can leave an earlier 40/100 value on a reused RenderNode, so clear
     * the actual property at this single source while leaving alpha/geometry animation intact.
     */
    private fun hookSelfBlurSource(module: XposedModule, classLoader: ClassLoader) {
        val companionClass = runCatching {
            Class.forName(SELF_BLUR_COMPANION_CLASS, false, classLoader)
        }.getOrNull() ?: return
        if (!hookedSelfBlurClasses.add(companionClass)) return
        companionClass.declaredMethods
            .filter { method ->
                method.name == "setMiSelfBlurCompat" &&
                    method.parameterCount == 2 &&
                    View::class.java.isAssignableFrom(method.parameterTypes[0])
            }
            .forEach { method ->
                runCatching {
                    method.isAccessible = true
                    module.hook(method).intercept { chain ->
                        val view = chain.args.getOrNull(0) as? View
                        if (view != null && ownsSoftGlass(view)) {
                            // Keep SystemUI's own reflection/error handling and change only the
                            // conflicting value. Calling the source with zero also clears a value
                            // left before this View acquired SOFT ownership.
                            return@intercept chain.proceed(arrayOf(view, 0))
                        }
                        chain.proceed()
                    }
                }.onFailure { error ->
                    logError(module, "failed to hook ${companionClass.name}.${method.name}: ${error.message}")
                }
            }
    }

    private fun ownsSoftGlass(view: View): Boolean {
        // OS3 uses the Gaussian fallback for the same SOFT config. Its self-blur animation is
        // stock behavior and must not be affected by the OS4 Bionics-only source interception.
        if (!SoftGlassController.isSystemBionicsActive(view)) return false
        if (SoftGlassController.isManaged(view)) return true
        val typeName = fakeSlotTypes[view]
            ?: IslandStateResolver.forView(view)?.name
            ?: return false
        return IslandBlurRuntime.transitionBlurController.isSoftGlass(typeName)
    }

    /** Keeps fake geometry and mask ownership aligned with the system animator. */
    private fun hookFakeViewAnimator(
        module: XposedModule,
        classLoader: ClassLoader,
        access: FakeViewAccess,
    ) {
        val delegateClass = runCatching { classLoader.loadClass(ANIMATION_DELEGATE_CLASS) }.getOrNull()
        val animatorClass = delegateClass?.let { findMethod(it, "getFakeViewAnimator")?.returnType }
            ?: findFirstClass(classLoader, FAKE_VIEW_ANIMATOR_CLASSES) ?: return
        val getFakeView = findMethod(animatorClass, "getFakeView") ?: return
        if (!hookedAnimatorClasses.add(animatorClass)) return
        log(module) { "fake animator source hooked class=${animatorClass.name}" }

        animatorClass.declaredMethods.forEach { method ->
            val targetType = when (method.name) {
                "fakeViewToExpanded", "fakeViewToNormalExpanded" -> "EXPAND"
                "fakeViewToBigIsland" -> "BIG"
                "fakeViewToSmallIsland" -> "SMALL"
                "fakeViewToHidden", "fakeViewToDeleted" -> NO_FAKE_SLOT
                else -> return@forEach
            }
            runCatching {
                method.isAccessible = true
                module.hook(method).intercept { chain ->
                    val fakeView = runCatching { getFakeView.invoke(chain.thisObject) as? View }.getOrNull()
                        ?: return@intercept chain.proceed()
                    synchronized(activeFakeTypes) {
                        if (fakeView in activeFakeTypes || activeFakeTypes.size < MAX_FAKE_SELECTIONS) {
                            activeFakeTypes[fakeView] = targetType
                        }
                    }
                    val result = chain.proceed()
                    fakeViews.add(fakeView)
                    refreshFakeView(fakeView, access)
                    result
                }
            }.onFailure { error ->
                logError(module, "failed to hook ${animatorClass.name}.${method.name}: ${error.message}")
            }
        }

        animatorClass.declaredMethods
            .firstOrNull { it.name == "updateFakeViewAnimState" && it.parameterCount == 0 }
            ?.let { method ->
                runCatching {
                    hookRefreshAfter(module, method) { animator ->
                        val fakeView = runCatching { getFakeView.invoke(animator) }.getOrNull()
                            ?: return@hookRefreshAfter
                        updateFakeFrame(fakeView, access)
                    }
                }.onFailure { error ->
                    logError(module, "failed to hook ${animatorClass.name}.${method.name}: ${error.message}")
                }
            }
    }

    private fun hookDeclaredRefreshAfter(
        module: XposedModule,
        clazz: Class<*>,
        name: String,
        vararg parameterTypes: Class<*>,
        refresh: (Any) -> Unit,
    ) {
        val method = runCatching {
            clazz.getDeclaredMethod(name, *parameterTypes)
        }.getOrNull() ?: return
        runCatching {
            hookRefreshAfter(module, method, refresh)
        }.onFailure { error ->
            logError(module, "failed to hook ${clazz.name}.$name: ${error.message}")
        }
    }

    private fun hookRefreshAfter(
        module: XposedModule,
        method: Method,
        refresh: (Any) -> Unit,
    ) {
        method.isAccessible = true
        module.hook(method).intercept { chain ->
            val result = chain.proceed()
            chain.thisObject?.let { owner ->
                runCatching { refresh(owner) }.onFailure { error ->
                    logError(module, "${method.declaringClass.name}.${method.name} refresh failed: ${error.message}")
                }
            }
            result
        }
    }

    private fun refreshFakeView(fakeView: Any) {
        refreshFakeView(fakeView, fakeAccess(fakeView.javaClass))
    }

    private fun fakeAccess(clazz: Class<*>) = FakeViewAccess(
        small = findMethod(clazz, "getFakeSmallIsland"),
        big = findMethod(clazz, "getFakeBigIsland"),
        expand = findMethod(clazz, "getFakeExpandedView"),
        container = findMethod(clazz, "getFakeContainer"),
        mask = findMethod(clazz, "getFakeMask"),
        realView = findMethod(clazz, "getRealView"),
        state = findMethod(clazz, "getState"),
    )

    /** The animator owns fake Bionics slots even when updateExpandedViewMaterial refreshes all of them. */
    internal fun handleFakeSoftBackground(owner: View, target: View, typeName: String): Boolean {
        if (owner.javaClass !in hookedFakeClasses) return false
        if (!owner.isShown || target.visibility != View.VISIBLE) return true
        val access = fakeAccess(owner.javaClass)
        if (renderedFakeType(owner, access) == typeName && access.viewFor(owner, typeName) === target) {
            targets[target]?.materialRevision = -1
            refreshFakeView(owner, access)
        }
        return true
    }

    private fun hookFakeVisibility(
        module: XposedModule,
        fakeClass: Class<*>,
        access: FakeViewAccess,
    ) {
        val method = runCatching {
            fakeClass.getDeclaredMethod("setVisibility", Int::class.javaPrimitiveType!!)
        }.getOrNull() ?: return
        runCatching {
            method.isAccessible = true
            module.hook(method).intercept { chain ->
                val owner = chain.thisObject as? View ?: return@intercept chain.proceed()
                val nextVisibility = chain.args.getOrNull(0) as? Int
                val leaving = owner.visibility == View.VISIBLE && nextVisibility != View.VISIBLE
                if (leaving && nextVisibility == View.INVISIBLE) {
                    prepareRealBackground(owner, access)
                }
                val result = chain.proceed()
                if (leaving) {
                    // Commit the real target exposed by SystemUI before releasing the fake lease.
                    prepareRealSoftGlass(owner, access)
                    if (access.realStateType(owner) == null) {
                        realBackground(owner, access)?.let(opaqueHandoffBackgrounds::remove)
                    }
                    if (activeFakeTypes[owner] != NO_FAKE_SLOT) {
                        activeFakeTypes.remove(owner)
                    }
                }
                refreshFakeView(owner, access)
                result
            }
        }.onFailure { error ->
            logError(module, "failed to hook ${fakeClass.name}.setVisibility: ${error.message}")
        }
    }

    private fun armRealBackgroundHandoff(fakeView: Any, access: FakeViewAccess) {
        val background = realBackground(fakeView, access) ?: return
        val typeName = access.realStateType(fakeView)
        if (typeName == null || IslandBlurRuntime.transitionBlurController.isEnabled(typeName)) {
            opaqueHandoffBackgrounds.remove(background)
        } else {
            opaqueHandoffBackgrounds.add(background)
        }
    }

    private fun prepareRealBackground(fakeView: Any, access: FakeViewAccess) {
        val typeName = access.realStateType(fakeView) ?: return
        val backgroundView = realBackground(fakeView, access) ?: return
        if (IslandBlurRuntime.transitionBlurController.isEnabled(typeName)) {
            opaqueHandoffBackgrounds.remove(backgroundView)
            return
        }
        val alphaField = findField(backgroundView.javaClass, "backgroundAlpha") ?: return
        opaqueHandoffBackgrounds.add(backgroundView)
        runCatching {
            alphaField.setFloat(backgroundView, 1f)
            findMethod(backgroundView.javaClass, "scheduleUpdate")?.invoke(backgroundView)
            backgroundView.invalidate()
        }
        backgroundView.post { opaqueHandoffBackgrounds.remove(backgroundView) }
    }

    private fun prepareRealSoftGlass(fakeView: Any, access: FakeViewAccess) {
        val realView = access.realView(fakeView) ?: return
        val typeName = access.realStateType(fakeView) ?: return
        val getterName = when (typeName) {
            "SMALL" -> "getSmallIslandView"
            "BIG" -> "getBigIslandView"
            "EXPAND" -> "getExpandedView"
            else -> return
        }
        val target = findMethod(realView.javaClass, getterName)
            ?.let { runCatching { it.invoke(realView) as? View }.getOrNull() }
            ?: return
        val controller = IslandBlurRuntime.transitionBlurController
        if (!controller.isSoftGlass(typeName) || !target.isShown) return
        if (!controller.isApplied(target, typeName) && !controller.apply(target, typeName)) return
        SoftGlassController.beginRendering(target)
        IslandBlurHook.clearSoftGlassOuter(realView, "fake-to-real")
        IslandBackgroundHook.clearCommittedSoftLayers(realView, typeName)
    }

    private fun realBackground(fakeView: Any, access: FakeViewAccess): View? {
        val realView = access.realView(fakeView) ?: return null
        return findMethod(realView.javaClass, "getBackgroundView")
            ?.let { runCatching { it.invoke(realView) as? View }.getOrNull() }
    }

    private fun hookHandoffBackgroundAlpha(module: XposedModule, backgroundClass: Class<*>) {
        val method = runCatching {
            backgroundClass.getDeclaredMethod(
                "alphaAnimation",
                Float::class.javaPrimitiveType!!,
            )
        }.getOrNull() ?: return
        val alphaField = findField(backgroundClass, "backgroundAlpha") ?: return
        val scheduleUpdate = findMethod(backgroundClass, "scheduleUpdate")
        runCatching {
            method.isAccessible = true
            module.hook(method).intercept { chain ->
                val backgroundView = chain.thisObject
                if (!opaqueHandoffBackgrounds.contains(backgroundView)) {
                    return@intercept chain.proceed()
                }
                alphaField.setFloat(backgroundView, 1f)
                scheduleUpdate?.invoke(backgroundView)
                (backgroundView as? View)?.invalidate()
                null
            }
        }.onFailure { error ->
            logError(module, "failed to hook ${backgroundClass.name}.alphaAnimation: ${error.message}")
        }
    }

    private fun refreshFakeView(
        fakeView: Any,
        access: FakeViewAccess,
    ) {
        val rootVisible = (fakeView as? View)?.isShown == true
        val type = if (rootVisible) renderedFakeType(fakeView, access) else null
        if (rootVisible) armRealBackgroundHandoff(fakeView, access)
        val activeView = type?.let { access.viewFor(fakeView, it) }
            ?.takeIf { it.visibility == View.VISIBLE }
        if (type != null && activeView != null) {
            // Acquire the destination before a previous slot can release the last window lease.
            fakeSlotTypes[activeView] = type
            applyFakeVisual(activeView, type)
            if (SoftGlassController.isManaged(activeView)) releaseHiddenRealSoftGlass(fakeView, access)
        }
        access.forEach(fakeView) { typeName, view ->
            fakeSlotTypes[view] = typeName
            if (view !== activeView && SoftGlassController.isManaged(view)) {
                IslandBlurRuntime.transitionBlurController.suspend(view)
                targets[view]?.managedVisual = false
            }
        }
        updateFakeLayerMask(fakeView, access, type)
    }

    private fun applyFakeVisual(view: View, type: String) {
        val controller = IslandBlurRuntime.transitionBlurController
        val target = synchronized(targets) {
            targets.getOrPut(view) { TransitionTarget(view.background) }
        }
        val revision = IslandBlurRuntime.configStore.revision
        if (controller.isEnabled(type)) {
            if (target.customDrawable != null) restoreStockBackground(view, target)
            target.managedVisual = if (target.materialRevision == revision && controller.isApplied(view, type)) {
                true
            } else {
                controller.apply(view, type)
            }
            if (target.managedVisual) {
                target.materialRevision = revision
                if (controller.isSoftGlass(type)) SoftGlassController.beginRendering(view)
            }
            return
        }
        controller.release(view)
        val drawable = IslandBackgroundHook.createTransitionBackground(view, type)
        if (drawable != null) {
            if (view.background !== target.customDrawable) {
                target.customDrawable?.callback = null
                target.customDrawable = drawable
                view.background = drawable
            }
            target.managedVisual = true
        } else {
            restoreStockBackground(view, target)
        }
        target.materialRevision = revision
    }

    private fun updateFakeFrame(fakeView: Any, access: FakeViewAccess) {
        if ((fakeView as? View)?.isShown != true) return
        val type = renderedFakeType(fakeView, access)
        val target = type?.let { access.viewFor(fakeView, it) }
        val controller = IslandBlurRuntime.transitionBlurController
        if (type != null && target != null && target.visibility == View.VISIBLE &&
            controller.isSoftGlass(type) && !controller.isApplied(target, type) &&
            SoftGlassController.isSystemBionicsActive(target)
        ) {
            // Folme's onBegin makes SMALL visible after fakeViewToSmallIsland has returned.
            // Its next alpha update is still before traversal, so commit that concrete slot here.
            refreshFakeView(fakeView, access)
        } else {
            updateFakeLayerMask(fakeView, access, type)
        }
    }

    /** Prevents restoreFakeViewBackground() from ever installing its two opaque drawables. */
    private fun hookRestoreFakeBackground(
        module: XposedModule,
        fakeClass: Class<*>,
        access: FakeViewAccess,
    ) {
        val method = fakeClass.declaredMethods.firstOrNull {
            it.name == "restoreFakeViewBackground" && it.parameterCount == 0
        } ?: return
        method.isAccessible = true
        module.hook(method).intercept { chain ->
            val owner = chain.thisObject as? View ?: return@intercept chain.proceed()
            val typeName = renderedFakeType(owner, access)
            val target = typeName?.let { access.viewFor(owner, it) }
            val managed = owner.isShown && target != null &&
                target.visibility == View.VISIBLE && typeName != null &&
                IslandBlurRuntime.transitionBlurController.isSoftGlass(typeName) &&
                SoftGlassController.isManaged(target)
            val result = if (managed) {
                clearSharedFakeLayers(owner, access)
                null
            } else {
                chain.proceed()
            }
            refreshFakeView(owner, access)
            result
        }
    }

    private fun releaseHiddenRealSoftGlass(fakeView: Any, access: FakeViewAccess) {
        val real = access.realView(fakeView) as? View ?: return
        if (real.isShown) return
        IslandType.entries.forEach { type ->
            IslandStateResolver.concreteView(real, type)?.let(SoftGlassController::suspend)
        }
    }

    private fun clearSharedFakeLayers(fakeView: Any, access: FakeViewAccess) {
        val root = fakeView as? View ?: return
        val container = access.container(fakeView)
        val mask = access.mask(fakeView)
        synchronized(fakeLayerTargets) {
            fakeLayerTargets.getOrPut(fakeView) {
                FakeLayerTarget(root.background, container?.background, mask?.background)
            }
        }
        IslandBackgroundHook.clearManagedVisualMask(root)
        if (container !== root && container != null) {
            IslandBackgroundHook.clearManagedVisualMask(container)
        }
        if (mask != null) IslandBackgroundHook.clearManagedVisualMask(mask)
    }

    private fun updateFakeLayerMask(fakeView: Any, access: FakeViewAccess, activeType: String?) {
        val root = fakeView as? View ?: return
        if (!root.isShown || activeType == null) return
        val activeView = access.viewFor(fakeView, activeType) ?: return
        if (activeView.visibility != View.VISIBLE) return
        val clearSharedMask = targets[activeView]?.managedVisual == true &&
            (activeView.background != null ||
                IslandBlurRuntime.transitionBlurController.hasManagedSoftGlass(activeView))
        if (clearSharedMask) {
            clearSharedFakeLayers(fakeView, access)
        } else {
            val target = fakeLayerTargets.remove(fakeView) ?: return
            val container = access.container(fakeView)
            val mask = access.mask(fakeView)
            if (root.background == null) root.background = target.rootBackground.get()
            if (container !== root && container?.background == null) {
                container?.background = target.containerBackground.get()
            }
            if (mask?.background == null) mask?.background = target.maskBackground.get()
        }
    }

    private fun renderedFakeType(fakeView: Any, access: FakeViewAccess): String? {
        val destination = activeFakeTypes[fakeView] ?: access.stateType(fakeView) ?: return null
        if (destination == NO_FAKE_SLOT) return null
        if (!IslandBlurRuntime.transitionBlurController.isSoftGlass(destination)) return destination
        val destinationView = access.viewFor(fakeView, destination)
        if (destinationView != null && destinationView.visibility == View.VISIBLE &&
            SoftGlassController.isManaged(destinationView)
        ) return destination
        var rendered = destination
        var alpha = destinationView?.takeIf { it.visibility == View.VISIBLE }?.alpha ?: 0f
        // The stock animation can keep both children VISIBLE during its alpha crossover. Keep
        // the outgoing material until the destination draws more strongly, then switch once.
        access.forEach(fakeView) { type, view ->
            if (view.visibility == View.VISIBLE && view.alpha > alpha && SoftGlassController.isManaged(view)) {
                rendered = type
                alpha = view.alpha
            }
        }
        return rendered
    }

    private fun releaseFakeView(fakeView: Any, access: FakeViewAccess) {
        fakeViews.remove(fakeView)
        activeFakeTypes.remove(fakeView)
        fakeLayerTargets.remove(fakeView)
        realBackground(fakeView, access)?.let(opaqueHandoffBackgrounds::remove)
        access.forEach(fakeView) { _, view ->
            IslandBlurRuntime.transitionBlurController.release(view)
            fakeSlotTypes.remove(view)
            targets.remove(view)?.customDrawable?.callback = null
        }
    }

    private fun restoreStockBackground(view: View, target: TransitionTarget) {
        target.customDrawable?.callback = null
        target.customDrawable = null
        val stock = target.stockDrawable.get()
        if (view.background !== stock) view.background = stock
        target.managedVisual = false
    }

    private fun findMethod(
        clazz: Class<*>,
        name: String,
        vararg parameterTypes: Class<*>,
    ): Method? {
        var current: Class<*>? = clazz
        while (current != null) {
            runCatching {
                return current.getDeclaredMethod(name, *parameterTypes).apply { isAccessible = true }
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

    private fun findFirstClass(classLoader: ClassLoader, names: Array<String>): Class<*>? {
        return names.firstNotNullOfOrNull { name ->
            runCatching { Class.forName(name, false, classLoader) }.getOrNull()
        }
    }

    private class FakeViewAccess(
        private val small: Method?,
        private val big: Method?,
        private val expand: Method?,
        private val container: Method?,
        private val mask: Method?,
        private val realView: Method?,
        private val state: Method?,
    ) {
        fun container(owner: Any): View? = invokeView(owner, container)

        fun mask(owner: Any): View? = invokeView(owner, mask)

        fun realView(owner: Any): Any? = runCatching { realView?.invoke(owner) }.getOrNull()

        fun realStateType(owner: Any): String? {
            val real = realView(owner) ?: return null
            val name = stateName(real)
            // AppExpanded and MiniWindowExpanded are window animation states, not real slots.
            if (name.contains("AppExpanded") || name.contains("MiniWindowExpanded")) return null
            return typeForState(name)
        }

        fun stateType(owner: Any): String? {
            // The fake object can retain its previous state during a handoff. SystemUI makes
            // the destination decision from DynamicIslandContentView, so prefer that state
            // and only fall back to the fake object's inherited field.
            val name = realView(owner)?.let(::stateName).orEmpty().ifEmpty { stateName(owner) }
            return typeForState(name)
        }

        private fun stateName(owner: Any): String =
            runCatching { state?.invoke(owner)?.javaClass?.simpleName }.getOrNull().orEmpty()

        private fun typeForState(name: String): String? = when {
            name.contains("SmallIsland") -> "SMALL"
            name.contains("BigIsland") -> "BIG"
            name.contains("Expanded") -> "EXPAND"
            else -> null
        }

        fun viewFor(owner: Any, typeName: String): View? = when (typeName) {
            "SMALL" -> invokeView(owner, small)
            "BIG" -> invokeView(owner, big)
            "EXPAND" -> invokeView(owner, expand)
            else -> null
        }

        fun forEach(owner: Any, action: (String, View) -> Unit) {
            apply(owner, "SMALL", small, action)
            apply(owner, "BIG", big, action)
            apply(owner, "EXPAND", expand, action)
        }

        private fun apply(
            owner: Any,
            typeName: String,
            getter: Method?,
            action: (String, View) -> Unit,
        ) {
            val view = invokeView(owner, getter) ?: return
            action(typeName, view)
        }

        private fun invokeView(owner: Any, getter: Method?): View? {
            return runCatching { getter?.invoke(owner) as? View }.getOrNull()
        }

    }

    private class FakeLayerTarget(
        rootBackground: Drawable?,
        containerBackground: Drawable?,
        maskBackground: Drawable?,
    ) {
        val rootBackground = WeakReference(rootBackground)
        val containerBackground = WeakReference(containerBackground)
        val maskBackground = WeakReference(maskBackground)
    }

    private class TransitionTarget(stockDrawable: Drawable?) {
        val stockDrawable = WeakReference(stockDrawable)
        var customDrawable: Drawable? = null
        var managedVisual = false
        var materialRevision = -1
    }

}
