package io.github.hyperisland.xposed.hook.SystemUI

import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.View
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.hyperisland.xposed.hook.IslandBackgroundHook
import io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.IslandBlurRuntime
import io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.lifecycle.IslandStateResolver
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
    private val FAKE_VIEW_ANIMATOR_CLASSES = arrayOf(
        "miui.systemui.dynamicisland.anim.ui.animator.FakeViewAnimator",
        "miui.systemui.dynamicisland.anim.p110ui.animator.FakeViewAnimator",
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
        val access = FakeViewAccess(
            small = findMethod(fakeClass, "getFakeSmallIsland"),
            big = findMethod(fakeClass, "getFakeBigIsland"),
            expand = findMethod(fakeClass, "getFakeExpandedView"),
            container = findMethod(fakeClass, "getFakeContainer"),
            mask = findMethod(fakeClass, "getFakeMask"),
            realView = findMethod(fakeClass, "getRealView"),
            state = findMethod(fakeClass, "getState"),
        )
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
        hookDeclaredRefreshAfter(module, fakeClass, "restoreFakeViewBackground") { owner ->
            refreshFakeView(owner, access)
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
        hookDeclaredRefreshAfter(module, fakeClass, "onDetachedFromWindow") { owner ->
            releaseFakeView(owner, access)
        }

        hookAnimationClasses(module, classLoader, access)
        log(module, "fake island transition visuals hooked")
    }

    private fun hookAnimationClasses(
        module: XposedModule,
        classLoader: ClassLoader,
        access: FakeViewAccess,
    ) {
        hookAnimationDelegate(module, classLoader, access)
        hookSelfBlurSource(module, classLoader)
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
        val hasDedicatedFakeAnimator = findFirstClass(classLoader, FAKE_VIEW_ANIMATOR_CLASSES) != null
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
        val animatorClass = findFirstClass(classLoader, FAKE_VIEW_ANIMATOR_CLASSES) ?: return
        if (!hookedAnimatorClasses.add(animatorClass)) return
        val getFakeView = findMethod(animatorClass, "getFakeView") ?: return

        sequenceOf(
            "fakeViewToExpanded",
            "fakeViewToBigIsland",
            "fakeViewToSmallIsland",
        ).forEach { name ->
            animatorClass.declaredMethods
                .filter { it.name == name }
                .forEach { method ->
                    runCatching {
                        hookRefreshAfter(module, method) { animator ->
                            val fakeView = runCatching { getFakeView.invoke(animator) }.getOrNull()
                                ?: return@hookRefreshAfter
                            fakeViews.add(fakeView)
                            // Geometry/visibility stays owned by SystemUI. Native soft glass is
                            // persistent on all reused fake slots, so this is only an idempotent
                            // mask/config reconciliation and never a material reload.
                            refreshFakeView(fakeView, access)
                        }
                    }.onFailure { error ->
                        logError(module, "failed to hook ${animatorClass.name}.$name: ${error.message}")
                    }
                }
        }

        animatorClass.declaredMethods
            .firstOrNull { it.name == "updateFakeViewAnimState" && it.parameterCount == 0 }
            ?.let { method ->
                runCatching {
                    hookRefreshAfter(module, method) { animator ->
                        val fakeView = runCatching { getFakeView.invoke(animator) }.getOrNull()
                            ?: return@hookRefreshAfter
                        updateFakeLayerMask(fakeView, access)
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
        val clazz = fakeView.javaClass
        refreshFakeView(
            fakeView,
            FakeViewAccess(
                small = findMethod(clazz, "getFakeSmallIsland"),
                big = findMethod(clazz, "getFakeBigIsland"),
                expand = findMethod(clazz, "getFakeExpandedView"),
                container = findMethod(clazz, "getFakeContainer"),
                mask = findMethod(clazz, "getFakeMask"),
                realView = findMethod(clazz, "getRealView"),
                state = findMethod(clazz, "getState"),
            ),
        )
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
                val owner = chain.thisObject
                val nextVisibility = chain.args.getOrNull(0) as? Int
                if (owner != null && nextVisibility == View.INVISIBLE) {
                    prepareRealBackground(owner, access)
                }
                val result = chain.proceed()
                if (owner != null) {
                    if (nextVisibility == View.VISIBLE) {
                        armRealBackgroundHandoff(owner, access)
                        refreshFakeView(owner, access)
                    } else {
                        updateFakeLayerMask(owner, access)
                    }
                }
                result
            }
        }.onFailure { error ->
            logError(module, "failed to hook ${fakeClass.name}.setVisibility: ${error.message}")
        }
    }

    private fun armRealBackgroundHandoff(fakeView: Any, access: FakeViewAccess) {
        val target = realBackgroundTarget(fakeView, access) ?: return
        if (IslandBlurRuntime.transitionBlurController.isEnabled(target.typeName)) {
            opaqueHandoffBackgrounds.remove(target.view)
        } else {
            opaqueHandoffBackgrounds.add(target.view)
        }
    }

    private fun prepareRealBackground(fakeView: Any, access: FakeViewAccess) {
        prepareRealSoftGlass(fakeView, access)
        val target = realBackgroundTarget(fakeView, access) ?: return
        val backgroundView = target.view
        if (IslandBlurRuntime.transitionBlurController.isEnabled(target.typeName)) {
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
        val typeName = access.stateType(fakeView) ?: return
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
        if (controller.isSoftGlass(typeName)) {
            // Real state Views also persist across fake handoff. Only initialize an unowned
            // target; resubmitting setMiGlass on a visible View creates a white transition frame.
            if (!controller.isApplied(target, typeName)) {
                controller.apply(target, typeName)
            }
        }
    }

    private fun realBackgroundTarget(
        fakeView: Any,
        access: FakeViewAccess,
    ): RealBackgroundTarget? {
        val realView = access.realView(fakeView) ?: return null
        val typeName = access.stateType(fakeView) ?: return null
        val backgroundView = findMethod(realView.javaClass, "getBackgroundView")
            ?.let { runCatching { it.invoke(realView) as? View }.getOrNull() }
            ?: return null
        return RealBackgroundTarget(backgroundView, typeName)
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
        if ((fakeView as? View)?.visibility == View.VISIBLE) {
            armRealBackgroundHandoff(fakeView, access)
        }
        access.forEach(fakeView) { typeName, view ->
            fakeSlotTypes[view] = typeName
            val controller = IslandBlurRuntime.transitionBlurController
            val persistentNativeSoft = controller.isSoftGlass(typeName) &&
                SoftGlassController.isSystemBionicsActive(view)
            // SystemUI keeps all three fake slots attached and only changes geometry, alpha and
            // visibility. Install native glass once on every slot so asynchronous Folme onBegin
            // cannot expose an unprepared destination. Gaussian/custom drawables remain limited
            // to the visible slot, preserving the established OS3 path.
            if (!persistentNativeSoft && view.visibility != View.VISIBLE) {
                if (controller.hasManagedSoftGlass(view)) controller.release(view)
                return@forEach
            }
            val target = synchronized(targets) {
                targets.getOrPut(view) { TransitionTarget(view.background) }
            }
            if (IslandBlurRuntime.transitionBlurController.isEnabled(typeName)) {
                if (target.customDrawable != null) restoreStockBackground(view, target)
                val currentRevision = IslandBlurRuntime.configStore.revision
                target.managedVisual = if (
                    target.materialRevision != currentRevision ||
                    !controller.isApplied(view, typeName)
                ) {
                    controller.apply(view, typeName)
                } else {
                    true
                }
                if (target.managedVisual) {
                    target.materialRevision = currentRevision
                    if (controller.hasManagedSoftGlass(view)) {
                        // Hidden fake slots are prepared once. When SystemUI exposes one, refresh
                        // only the window-owned sampler/radius; never resubmit this View's glass.
                        SoftGlassController.refreshPassWindowBlur(view)
                    }
                }
                return@forEach
            }

            IslandBlurRuntime.transitionBlurController.release(view)
            val drawable = IslandBackgroundHook.createTransitionBackground(view, typeName)
            if (drawable != null) {
                if (view.background !== target.customDrawable) {
                    target.customDrawable?.callback = null
                    target.customDrawable = drawable
                    view.background = drawable
                }
                target.managedVisual = true
            } else {
                restoreStockBackground(view, target)
                target.managedVisual = false
            }
            target.materialRevision = IslandBlurRuntime.configStore.revision
        }
        updateFakeLayerMask(fakeView, access)
    }

    private fun updateFakeLayerMask(fakeView: Any, access: FakeViewAccess) {
        val root = fakeView as? View ?: return
        val container = access.container(fakeView)
        val mask = access.mask(fakeView)
        val target = synchronized(fakeLayerTargets) {
            fakeLayerTargets.getOrPut(fakeView) {
                FakeLayerTarget(
                    root.background,
                    container?.background,
                    mask?.background,
                )
            }
        }
        // Use the same ownership rule as Gaussian blur and custom backgrounds: the shared
        // fake mask is removed only when every currently rendered child owns its visual.
        val visibleTargets = access.visibleViews(fakeView)
        val clearSharedMask = root.visibility == View.VISIBLE && visibleTargets.isNotEmpty() &&
            visibleTargets.all { (_, view) ->
                targets[view]?.managedVisual == true &&
                    (view.background != null ||
                        IslandBlurRuntime.transitionBlurController.hasManagedSoftGlass(view))
            }
        if (clearSharedMask) {
            IslandBackgroundHook.clearManagedVisualMask(root)
            if (container !== root && container != null) {
                IslandBackgroundHook.clearManagedVisualMask(container)
            }
            if (mask != null) IslandBackgroundHook.clearManagedVisualMask(mask)
        } else {
            if (root.background == null) root.background = target.rootBackground
            if (container !== root && container?.background == null) {
                container?.background = target.containerBackground
            }
            if (mask?.background == null) mask?.background = target.maskBackground
        }
    }

    private fun releaseFakeView(fakeView: Any, access: FakeViewAccess) {
        fakeViews.remove(fakeView)
        fakeLayerTargets.remove(fakeView)
        realBackgroundTarget(fakeView, access)?.let { target ->
            opaqueHandoffBackgrounds.remove(target.view)
        }
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

        fun stateType(owner: Any): String? {
            // The fake object can retain its previous state during a handoff. SystemUI makes
            // the destination decision from DynamicIslandContentView, so prefer that state
            // and only fall back to the fake object's inherited field.
            val currentState = runCatching {
                realView(owner)?.let { state?.invoke(it) }
            }.getOrNull() ?: runCatching { state?.invoke(owner) }.getOrNull()
            val name = currentState?.javaClass?.simpleName.orEmpty()
            return when {
                name.contains("SmallIsland") -> "SMALL"
                name.contains("BigIsland") -> "BIG"
                name.contains("Expanded") -> "EXPAND"
                else -> null
            }
        }

        fun typeOf(owner: Any, target: View): String? {
            return when (target) {
                invokeView(owner, small) -> "SMALL"
                invokeView(owner, big) -> "BIG"
                invokeView(owner, expand) -> "EXPAND"
                else -> null
            }
        }

        fun visibleViews(owner: Any): List<Pair<String, View>> {
            return listOf(
                RenderedState("SMALL", invokeView(owner, small)),
                RenderedState("BIG", invokeView(owner, big)),
                RenderedState("EXPAND", invokeView(owner, expand)),
            ).filter { state ->
                state.view?.visibility == View.VISIBLE && state.view.alpha > 0f
            }.mapNotNull { state ->
                state.view?.let { state.type to it }
            }
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

        private data class RenderedState(
            val type: String,
            val view: View?,
        )
    }

    private class FakeLayerTarget(
        val rootBackground: Drawable?,
        val containerBackground: Drawable?,
        val maskBackground: Drawable?,
    )

    private class TransitionTarget(stockDrawable: Drawable?) {
        val stockDrawable = WeakReference(stockDrawable)
        var customDrawable: Drawable? = null
        var managedVisual = false
        var materialRevision = -1
    }

    private data class RealBackgroundTarget(
        val view: View,
        val typeName: String,
    )

}
