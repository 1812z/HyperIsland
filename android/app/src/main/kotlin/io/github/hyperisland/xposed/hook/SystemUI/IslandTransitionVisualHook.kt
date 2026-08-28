package io.github.hyperisland.xposed.hook.SystemUI

import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.View
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.hyperisland.xposed.hook.IslandBackgroundHook
import io.github.hyperisland.xposed.hook.SystemUI.BackGround.IslandBlurHook
import io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.IslandBlurRuntime
import io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.model.IslandType
import io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.model.MaterialType
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
    private const val CONTENT_VIEW_CLASS =
        "miui.systemui.dynamicisland.window.content.DynamicIslandContentView"
    private const val ANIMATION_DELEGATE_CLASS =
        "miui.systemui.dynamicisland.anim.DynamicIslandAnimationDelegate"
    private const val FAKE_VIEW_ANIMATOR_CLASS =
        "miui.systemui.dynamicisland.anim.ui.animator.FakeViewAnimator"
    private const val ISLAND_PROPERTY_UPDATER_CLASS =
        "miui.systemui.dynamicisland.anim.ui.animator.IslandPropertyUpdater"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val hookedFakeClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    )
    private val hookedContentClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    )
    private val hookedDelegateClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    )
    private val hookedAnimatorClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    )
    private val hookedPropertyUpdaterClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    )
    private val hookedBackgroundSetterClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    )
    private val fakeViews = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
    )
    private val targets = Collections.synchronizedMap(
        WeakHashMap<View, TransitionTarget>()
    )
    private val fakeLayerTargets = Collections.synchronizedMap(
        WeakHashMap<Any, FakeLayerTarget>()
    )
    private val softLayerOwners = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
    )
    private val realSoftOwners = Collections.synchronizedMap(
        WeakHashMap<Any, IslandType>()
    )
    private val opaqueHandoffBackgrounds = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
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
    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        if (param.packageName != "com.android.systemui") return
        hookSharedContainerBackgroundWriters(module)
        hookPlugin(module, param.defaultClassLoader)
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { classLoader ->
            hookPlugin(module, classLoader)
        }
    }

    override fun onConfigChanged() {
        // This callback can arrive before IslandBlurHook reloads the shared snapshot.
        IslandBlurRuntime.configStore.reload()
        mainHandler.post {
            synchronized(fakeViews) { fakeViews.toList() }.forEach(::refreshFakeView)
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
        // EXPAND differs from SMALL/BIG: SystemUI configures fakeExpandedView first,
        // then updateExpandedView replaces its entire child tree. Native Bionics
        // state installed before that replacement is no longer reliable. Reapply
        // only after the authoritative content transaction has completed.
        fakeClass.declaredMethods
            .filter { it.name == "updateExpandedView" && it.parameterCount == 3 }
            .forEach { method ->
                hookRefreshAfter(module, method) { owner ->
                    access.expanded(owner)?.let {
                        IslandBlurRuntime.transitionBlurController.invalidateSoftGlass(it)
                    }
                    refreshFakeView(owner, access)
                }
            }
        hookFakeVisibility(module, fakeClass, access)
        hookDeclaredRefreshAfter(module, fakeClass, "onDetachedFromWindow") { owner ->
            releaseFakeView(owner, access)
        }

        hookAnimationClasses(module, classLoader, access)
        log(module, "fake island transition visuals hooked")
    }

    /**
     * Rejects the stock solid-black drawable at its write boundary. This also covers
     * XML inflation, before the first frame, instead of clearing the drawable later.
     */
    private fun hookSharedContainerBackgroundWriters(module: XposedModule) {
        val viewClass = View::class.java
        if (!hookedBackgroundSetterClasses.add(viewClass)) return
        sequenceOf(
            runCatching {
                viewClass.getDeclaredMethod("setBackground", Drawable::class.java)
            }.getOrNull(),
            runCatching {
                viewClass.getDeclaredMethod("setBackgroundDrawable", Drawable::class.java)
            }.getOrNull(),
            runCatching {
                viewClass.getDeclaredMethod(
                    "setBackgroundResource",
                    Int::class.javaPrimitiveType!!,
                )
            }.getOrNull(),
        ).filterNotNull().forEach { method ->
            runCatching {
                method.isAccessible = true
                module.hook(method).intercept { chain ->
                    val view = chain.thisObject as? View ?: return@intercept chain.proceed()
                    val removesBackground = when (val value = chain.args.getOrNull(0)) {
                        null -> true
                        is Int -> value == 0
                        else -> false
                    }
                    if (!removesBackground && shouldRejectSharedContainerBackground(view)) {
                        return@intercept null
                    }
                    chain.proceed()
                }
            }.onFailure { error ->
                logError(module, "failed to hook View.${method.name}: ${error.message}")
            }
        }
    }

    private fun hookAnimationClasses(
        module: XposedModule,
        classLoader: ClassLoader,
        access: FakeViewAccess,
    ) {
        hookRealContentLifecycle(module, classLoader)
        hookAnimationDelegate(module, classLoader, access)
        hookFakeViewAnimator(module, classLoader, access)
        hookIslandPropertyUpdater(module, classLoader)
    }

    private fun hookRealContentLifecycle(
        module: XposedModule,
        classLoader: ClassLoader,
    ) {
        val contentClass = runCatching {
            Class.forName(CONTENT_VIEW_CLASS, false, classLoader)
        }.getOrNull() ?: return
        if (!hookedContentClasses.add(contentClass)) return

        sequenceOf("onFinishInflate", "onAttachedToWindow", "updateViewStateWhenCloseEnd")
            .forEach { name ->
                contentClass.declaredMethods
                    .filter { it.name == name && it.parameterCount == 0 }
                    .forEach { method ->
                        runCatching {
                            hookRefreshAfter(module, method, ::refreshRealContentView)
                        }.onFailure { error ->
                            logError(module, "failed to hook ${contentClass.name}.$name: ${error.message}")
                        }
                    }
            }
        contentClass.declaredMethods
            .filter { it.name == "onVisibilityAggregated" && it.parameterCount == 1 }
            .forEach { method ->
                runCatching {
                    hookRefreshAfter(module, method) { owner ->
                        refreshRealContentView(owner)
                        (owner as? View)?.post { refreshRealContentView(owner) }
                    }
                }.onFailure { error ->
                    logError(module, "failed to hook ${contentClass.name}.${method.name}: ${error.message}")
                }
            }
        contentClass.methods.firstOrNull {
            it.name == "updateDarkLightMode" && it.parameterCount == 4
        }?.let { method ->
            runCatching {
                hookRefreshAfter(module, method, ::refreshRealContentView)
            }.onFailure { error ->
                logError(module, "failed to hook ${method.declaringClass.name}.${method.name}: ${error.message}")
            }
        }
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
        val hasDedicatedFakeAnimator = runCatching {
            Class.forName(FAKE_VIEW_ANIMATOR_CLASS, false, classLoader)
        }.isSuccess
        val refreshMethods = if (hasDedicatedFakeAnimator) {
            sequenceOf("containerScheduleUpdate")
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
     * HyperOS 4 moved fake-view frame updates into FakeViewAnimator. During BIG/EXPAND
     * cross-fades it applies setMiSelfBlur(0..100) to the same FrameLayouts that own
     * our Bionics material. That extra RenderNode self-blur turns the transparent
     * glass handoff into an opaque black layer. Keep the native alpha/geometry
     * animation, but suppress only this extra self-blur for SOFT-owned fake views.
     */
    private fun hookFakeViewAnimator(
        module: XposedModule,
        classLoader: ClassLoader,
        access: FakeViewAccess,
    ) {
        val animatorClass = runCatching {
            Class.forName(FAKE_VIEW_ANIMATOR_CLASS, false, classLoader)
        }.getOrNull() ?: return
        if (!hookedAnimatorClasses.add(animatorClass)) return
        val getFakeView = findMethod(animatorClass, "getFakeView") ?: return

        animatorClass.declaredMethods
            .firstOrNull {
                it.name == "updateContentBlur" &&
                    it.parameterCount == 2 &&
                    View::class.java.isAssignableFrom(it.parameterTypes[0])
            }
            ?.let { method ->
                runCatching {
                    method.isAccessible = true
                    module.hook(method).intercept { chain ->
                        val view = chain.args.getOrNull(0) as? View
                        if (view != null &&
                            IslandBlurRuntime.transitionBlurController.hasManagedSoftGlass(view)
                        ) {
                            clearFakeSelfBlur(view)
                            return@intercept null
                        }
                        chain.proceed()
                    }
                }.onFailure { error ->
                    logError(module, "failed to hook ${animatorClass.name}.${method.name}: ${error.message}")
                }
            }

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
                        refreshFakeAnimationFrame(fakeView, access)
                    }
                }.onFailure { error ->
                    logError(module, "failed to hook ${animatorClass.name}.${method.name}: ${error.message}")
                }
            }
    }

    /**
     * The real BIG/EXPAND swipe path is separate from FakeViewAnimator. In Bionics
     * EXPAND, IslandPropertyUpdater installs dynamic_island_background (solid black)
     * on the shared content container and drives its alpha from gesture progress.
     * It also applies a 0..40 self-blur to the real state View. Both are stock
     * handoff masks and must be suppressed while that state is owned by SOFT.
     */
    private fun hookIslandPropertyUpdater(
        module: XposedModule,
        classLoader: ClassLoader,
    ) {
        val updaterClass = runCatching {
            Class.forName(ISLAND_PROPERTY_UPDATER_CLASS, false, classLoader)
        }.getOrNull() ?: return
        if (!hookedPropertyUpdaterClasses.add(updaterClass)) return
        val viewField = findField(updaterClass, "view") ?: return

        updaterClass.declaredMethods
            .firstOrNull {
                it.name == "updateContentBlur" &&
                    it.parameterCount == 2 &&
                    View::class.java.isAssignableFrom(it.parameterTypes[0])
            }
            ?.let { method ->
                runCatching {
                    method.isAccessible = true
                    module.hook(method).intercept { chain ->
                        val view = chain.args.getOrNull(0) as? View
                        if (view != null &&
                            IslandBlurRuntime.transitionBlurController.hasManagedSoftGlass(view)
                        ) {
                            clearFakeSelfBlur(view)
                            return@intercept null
                        }
                        chain.proceed()
                    }
                }.onFailure { error ->
                    logError(module, "failed to hook ${updaterClass.name}.${method.name}: ${error.message}")
                }
            }

        updaterClass.declaredMethods
            .firstOrNull { it.name == "updateContainer" && it.parameterCount == 1 }
            ?.let { method ->
                runCatching {
                    method.isAccessible = true
                    module.hook(method).intercept { chain ->
                        chain.thisObject?.let { updater ->
                            runCatching { viewField.get(updater) }.getOrNull()
                                ?.let(::rememberRealSoftOwner)
                        }
                        // The View background writer hook below rejects the stock black
                        // assignment synchronously while this method proceeds.
                        chain.proceed()
                    }
                }.onFailure { error ->
                    logError(module, "failed to hook ${updaterClass.name}.${method.name}: ${error.message}")
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
                        // The fake subtree is reused across animations. Xiaomi's
                        // native material is RenderNode state and may be discarded
                        // while the subtree is invisible even though the View and
                        // our weak ownership entry both survive.
                        access.forEach(owner) { _, view ->
                            if (view.visibility == View.VISIBLE) {
                                IslandBlurRuntime.transitionBlurController.invalidateSoftGlass(view)
                            }
                        }
                        refreshFakeView(owner, access)
                    } else {
                        // Handoff completion hides the reusable fake root. Re-applying all
                        // three hidden Bionics slots here changes the window RenderNode/radius
                        // after the real island is already visible and produces one flash.
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
        if (IslandBlurRuntime.configStore.materialFor(IslandType.valueOf(typeName)).type ==
            MaterialType.SOFT
        ) {
            if (!IslandBlurRuntime.transitionBlurController.hasManagedSoftGlass(target)) {
                IslandBlurRuntime.transitionBlurController.apply(target, typeName)
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

    private fun refreshFakeView(fakeView: Any, access: FakeViewAccess) {
        if ((fakeView as? View)?.visibility == View.VISIBLE) {
            armRealBackgroundHandoff(fakeView, access)
        }
        access.forEach(fakeView) { typeName, view ->
            // The fake subtree keeps all three state slots attached and reuses them.
            // Only VISIBLE slots participate in the current transition. Submitting glass
            // to hidden siblings mutates the shared window radius/material at handoff.
            if (view.visibility != View.VISIBLE) return@forEach
            val target = synchronized(targets) {
                targets.getOrPut(view) { TransitionTarget(view.background) }
            }
            if (IslandBlurRuntime.transitionBlurController.isEnabled(typeName)) {
                if (target.customDrawable != null) restoreStockBackground(view, target)
                // apply() updates already-owned Bionics/blur resources as well, which
                // is necessary when only a material parameter changed.
                target.managedVisual =
                    IslandBlurRuntime.transitionBlurController.apply(view, typeName)
                if (target.managedVisual &&
                    IslandBlurRuntime.transitionBlurController.hasManagedSoftGlass(view)
                ) {
                    clearFakeSelfBlur(view)
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
        // OS3 and OS4 both animate through this fake subtree. Newer OS4 builds restore these
        // shared layers more aggressively, but the ownership rule is version-independent:
        // a SOFT session must never restore fake_container's solid-black stock background,
        // including the frames where every state child is temporarily invisible while shrinking.
        access.stateType(fakeView)?.let { typeName ->
            val type = IslandType.valueOf(typeName)
            if (IslandBlurRuntime.configStore.materialFor(type).type == MaterialType.SOFT) {
                softLayerOwners.add(fakeView)
            } else {
                softLayerOwners.remove(fakeView)
            }
        }
        val visibleTargets = access.visibleViews(fakeView)
        val clearSharedMask = softLayerOwners.contains(fakeView) ||
            (root.visibility == View.VISIBLE && visibleTargets.isNotEmpty() &&
                visibleTargets.all { (_, view) ->
                    targets[view]?.managedVisual == true &&
                        (view.background != null ||
                            IslandBlurRuntime.transitionBlurController.hasManagedSoftGlass(view))
                })
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

    private fun refreshFakeAnimationFrame(fakeView: Any, access: FakeViewAccess) {
        access.forEach(fakeView) { _, view ->
            if (IslandBlurRuntime.transitionBlurController.hasManagedSoftGlass(view)) {
                clearFakeSelfBlur(view)
            }
        }
        updateFakeLayerMask(fakeView, access)
    }

    private fun refreshRealContentView(contentView: Any) {
        clearRealSoftContainerMask(contentView)
    }

    private fun shouldRejectSharedContainerBackground(view: View): Boolean {
        val entryName = runCatching {
            view.resources.getResourceEntryName(view.id)
        }.getOrNull() ?: return false
        if (entryName != "container" && entryName != "fake_container" &&
            entryName != "fake_content"
        ) return false

        var current: View? = view
        while (current != null) {
            val className = current.javaClass.name
            if (className == FAKE_VIEW_CLASS) {
                val state = findMethod(current.javaClass, "getState")
                    ?.let { runCatching { it.invoke(current) }.getOrNull() }
                    ?.let(::typeFromState)
                if (state != null) {
                    return IslandBlurRuntime.configStore.materialFor(state).type == MaterialType.SOFT
                }
                return softLayerOwners.contains(current) || anySoftMaterialEnabled()
            }
            if (className == CONTENT_VIEW_CLASS) {
                val state = currentContentType(current)
                if (state != null) {
                    return IslandBlurRuntime.configStore.materialFor(state).type == MaterialType.SOFT
                }
                return realSoftOwners.containsKey(current) || anySoftMaterialEnabled()
            }
            current = current.parent as? View
        }

        // During XML construction the shared container has no parent yet. Its resource
        // id is nevertheless authoritative; a later non-SOFT state writer is allowed
        // once the owning content View and state are available.
        return anySoftMaterialEnabled()
    }

    private fun anySoftMaterialEnabled(): Boolean = IslandType.entries.any { type ->
        IslandBlurRuntime.configStore.materialFor(type).type == MaterialType.SOFT
    }

    private fun currentContentType(contentView: Any): IslandType? {
        val stateType = findMethod(contentView.javaClass, "getState")
            ?.let { runCatching { it.invoke(contentView) }.getOrNull() }
            ?.let(::typeFromState)
        if (stateType != null) return stateType
        return sequenceOf(
            IslandType.EXPAND to "getExpandedView",
            IslandType.BIG to "getBigIslandView",
            IslandType.SMALL to "getSmallIslandView",
        ).firstOrNull { (_, getterName) ->
            val child = findMethod(contentView.javaClass, getterName)
                ?.let { runCatching { it.invoke(contentView) as? View }.getOrNull() }
            child?.visibility == View.VISIBLE && child.alpha > 0f
        }?.first
    }

    private fun clearRealSoftContainerMask(contentView: Any) {
        rememberRealSoftOwner(contentView)
        realSoftOwners[contentView] ?: return
        val container = findMethod(contentView.javaClass, "getContainer")
            ?.let { runCatching { it.invoke(contentView) as? View }.getOrNull() }
            ?: return
        if (container.background != null) {
            IslandBackgroundHook.clearManagedVisualMask(container)
        }
        container.invalidate()
        IslandBlurHook.clearSoftGlassOuter(contentView, "soft-glass-content-guard")
    }

    private fun rememberRealSoftOwner(contentView: Any) {
        val currentType = currentContentType(contentView) ?: return
        if (IslandBlurRuntime.configStore.materialFor(currentType).type == MaterialType.SOFT) {
            realSoftOwners[contentView] = currentType
        } else {
            realSoftOwners.remove(contentView)
        }
    }

    private fun typeFromState(state: Any): IslandType? {
        val name = state.javaClass.simpleName
        return when {
            name.contains("SmallIsland") -> IslandType.SMALL
            name.contains("BigIsland") -> IslandType.BIG
            name.contains("Expanded") -> IslandType.EXPAND
            else -> null
        }
    }

    private fun releaseFakeView(fakeView: Any, access: FakeViewAccess) {
        fakeViews.remove(fakeView)
        fakeLayerTargets.remove(fakeView)
        softLayerOwners.remove(fakeView)
        realBackgroundTarget(fakeView, access)?.let { target ->
            opaqueHandoffBackgrounds.remove(target.view)
        }
        access.forEach(fakeView) { _, view ->
            IslandBlurRuntime.transitionBlurController.release(view)
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

    private fun findMethod(clazz: Class<*>, name: String): Method? {
        var current: Class<*>? = clazz
        while (current != null) {
            runCatching {
                return current.getDeclaredMethod(name).apply { isAccessible = true }
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

    private fun clearFakeSelfBlur(view: View) {
        runCatching { setMiSelfBlurMethod?.invoke(view, 0, null) }
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

        fun expanded(owner: Any): View? = invokeView(owner, expand)

        fun realView(owner: Any): Any? = runCatching { realView?.invoke(owner) }.getOrNull()

        fun stateType(owner: Any): String? {
            val currentState = runCatching { state?.invoke(owner) }.getOrNull()
            val name = currentState?.javaClass?.simpleName.orEmpty()
            return when {
                name.contains("SmallIsland") -> "SMALL"
                name.contains("BigIsland") -> "BIG"
                name.contains("Expanded") -> "EXPAND"
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
    }

    private data class RealBackgroundTarget(
        val view: View,
        val typeName: String,
    )

}
