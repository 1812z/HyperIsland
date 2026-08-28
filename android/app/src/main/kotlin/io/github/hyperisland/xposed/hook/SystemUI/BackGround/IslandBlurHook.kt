package io.github.hyperisland.xposed.hook.SystemUI.BackGround

import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
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
    private const val CONTENT_VIEW_CONTROLLER_CLASS =
        "miui.systemui.dynamicisland.window.content.DynamicIslandContentViewController"
    private const val EXPANDED_VIEW_CLASS =
        "miui.systemui.dynamicisland.view.DynamicIslandExpandedView"
    private const val NO_CONTENT_CLEANUP_POLL_MS = 16L
    private const val NO_CONTENT_CLEANUP_TIMEOUT_MS = 1_500L
    private val hookedContentClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    )
    private val refreshTargets = Collections.synchronizedMap(
        WeakHashMap<View, RefreshTarget>()
    )
    private val controllerVisibility = Collections.synchronizedMap(
        WeakHashMap<Any, Boolean>()
    )
    private val noContentCleanupRunnables = Collections.synchronizedMap(
        WeakHashMap<Any, Runnable>()
    )
    private val mainHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = Runnable { refreshTrackedViews() }
    private val islandTypeHolder = ThreadLocal<IslandType>()
    private val configStore = IslandBlurRuntime.configStore
    private val outerBlurRegistry = IslandBlurRuntime.outerBlurRegistry

    @Volatile
    private var lastIslandType: IslandType? = null

    @Volatile
    private var islandTempHidden = false

    @Volatile
    private var activeSoftGlassType: IslandType? = null

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
            val softType = activeSoftGlassType
            if (softType == null || materialForType(softType).type != MaterialType.SOFT) {
                activeSoftGlassType = null
                SoftGlassController.clearWindowSession()
            } else {
                SoftGlassController.activateWindowSession(
                    materialForType(softType).softGlass
                )
            }
            outerBlurRegistry.onConfigChanged()
        }
        mainHandler.removeCallbacks(refreshRunnable)
        if (configStore.anyMaterialEnabled) {
            mainHandler.postDelayed(refreshRunnable, 80L)
        }
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
            SoftGlassController.bindRuntime(contentClass, compatClass)
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
            SoftGlassController.hookWindowLifecycle(module, windowViewClass)
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
            hookContentVisibility(module, classLoader, backgroundViewField)
            hookTempHiddenLifecycle(module, windowViewClass)
            hookExpandedContentInstall(
                module,
                classLoader,
            )
            module.hook(updateMethod).intercept { chain ->
                val result = chain.proceed()
                val view = chain.args.getOrNull(0) as? View ?: return@intercept result
                val contentView = chain.thisObject ?: return@intercept result
                val type = IslandStateResolver.forView(view)
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
                    IslandStateResolver.fromState(stateField.get(contentView))
                }.getOrNull() ?: lastIslandType
                // The shared state field can still contain BIG while expanded_view is
                // already laid out for a focus notification. The target view is authoritative.
                if (backgroundView == null) return@intercept result

                val material = materialForType(type)
                val staleUpdate = stateType != null && type != stateType
                // Native soft glass belongs to each concrete island View. SystemUI
                // updates the hidden BIG view while EXPAND is active; treating that
                // callback as stale lets the stock gray background overwrite BIG,
                // which is then exposed unchanged when the island collapses.
                // The stale-state guard is only needed by the shared outer drawable.
                val active = if (material.type == MaterialType.SOFT) {
                    if (!staleUpdate) {
                        deactivateOuterBlur(backgroundView, outerDrawableField, "soft-glass")
                    }
                    val softActive = SoftGlassController.apply(view, material.softGlass)
                    if (softActive) {
                        if (!staleUpdate) {
                            activateSoftGlassSession(type, material)
                        }
                        true
                    }
                    softActive
                } else if (staleUpdate) {
                    SoftGlassController.onSystemMaterialReplaced(view)
                    false
                } else if (config.isActive) {
                    SoftGlassController.onSystemMaterialReplaced(view)
                    applyOuterBlur(
                        backgroundView,
                        view,
                        type,
                        config,
                        outerDrawableField,
                    )
                } else {
                    SoftGlassController.onSystemMaterialReplaced(view)
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
            if (type != null) {
                islandTypeHolder.set(type)
                lastIslandType = type
                cancelNoContentCleanup(chain.thisObject)
            }
            try {
                val result = chain.proceed()
                if (type != null && materialForType(type).isCustom) {
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
                        if (materialForType(type).isCustom) {
                            refreshConcreteIslandViews(chain.thisObject, updateMethod, type)
                        }
                    }
                } else if (IslandStateResolver.isNoContent(state)) {
                    val contentView = chain.thisObject
                    scheduleNoContentCleanup(
                        contentView,
                        stateField,
                        backgroundViewField,
                        outerDrawableField,
                    )
                }
                result
            } finally {
                islandTypeHolder.remove()
            }
        }
    }

    private fun materialForType(type: IslandType): MaterialConfig = configStore.materialFor(type)

    /**
     * Hidden/Deleted is emitted at the start of the OS4 disappearance animation. Releasing the
     * BlurDrawable there exposes SystemUI's stock black drawable for the remaining shrink frames.
     * Keep the blur until the animated background has actually faded or lost its geometry.
     */
    private fun scheduleNoContentCleanup(
        contentView: Any,
        stateField: java.lang.reflect.Field,
        backgroundViewField: java.lang.reflect.Field,
        outerDrawableField: java.lang.reflect.Field,
    ) {
        cancelNoContentCleanup(contentView)
        val deadline = SystemClock.uptimeMillis() + NO_CONTENT_CLEANUP_TIMEOUT_MS
        lateinit var cleanup: Runnable
        cleanup = Runnable {
            val currentState = runCatching { stateField.get(contentView) }.getOrNull()
            if (!IslandStateResolver.isNoContent(currentState)) {
                noContentCleanupRunnables.remove(contentView)
                return@Runnable
            }
            val backgroundView = runCatching {
                backgroundViewField.get(contentView) as? View
            }.getOrNull()
            if (backgroundView == null) {
                noContentCleanupRunnables.remove(contentView)
                return@Runnable
            }

            val internalAlpha = runCatching {
                findField(backgroundView.javaClass, "backgroundAlpha")?.getFloat(backgroundView)
            }.getOrNull() ?: backgroundView.alpha
            val stillDrawing = backgroundView.isAttachedToWindow &&
                backgroundView.visibility == View.VISIBLE &&
                outerBlurRegistry.hasDrawableBounds(backgroundView) &&
                internalAlpha > 0.01f
            if (stillDrawing && SystemClock.uptimeMillis() < deadline) {
                mainHandler.postDelayed(cleanup, NO_CONTENT_CLEANUP_POLL_MS)
                return@Runnable
            }

            noContentCleanupRunnables.remove(contentView)
            deactivateOuterBlur(backgroundView, outerDrawableField, "no-content-animation-finished")
            activeSoftGlassType = null
            SoftGlassController.clearWindowSession()
            if (outerBlurRegistry.isEmpty()) {
                lastIslandType = null
            }
        }
        noContentCleanupRunnables[contentView] = cleanup
        mainHandler.post(cleanup)
    }

    private fun cancelNoContentCleanup(contentView: Any?) {
        if (contentView == null) return
        noContentCleanupRunnables.remove(contentView)?.let(mainHandler::removeCallbacks)
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

    private fun hookBackgroundDrawing(
        module: XposedModule,
        backgroundClass: Class<*>,
        drawableField: java.lang.reflect.Field,
    ) {
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

    /** Restores the 2.4.8 visibility edge that was removed while retaining blur reuse. */
    private fun hookContentVisibility(
        module: XposedModule,
        classLoader: ClassLoader,
        backgroundViewField: java.lang.reflect.Field,
    ) {
        val controllerClass = runCatching {
            Class.forName(CONTENT_VIEW_CONTROLLER_CLASS, false, classLoader)
        }.getOrNull() ?: return
        val currentIslandVisible = findMethod(controllerClass, "currentIslandVisible") ?: return
        val getView = findMethod(controllerClass, "getView") ?: return
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
                    if (previouslyVisible != false && visible == false) {
                        val contentView = runCatching { getView.invoke(controller) }.getOrNull()
                        val backgroundView = runCatching {
                            backgroundViewField.get(contentView) as? View
                        }.getOrNull()
                        if (backgroundView != null) {
                            outerBlurRegistry.hideEdgeHighlight(backgroundView)
                        }
                    }
                    result
                }
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
            deactivateOuterBlur(backgroundView, outerDrawableField, "soft-glass-sync")
            if (shapeView != null) {
                if (SoftGlassController.apply(
                        shapeView,
                        material.softGlass,
                    )
                ) {
                    activateSoftGlassSession(type, material)
                }
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

    /** Rewrites native Bionics parameters even when SystemUI emits no state callback. */
    private fun refreshTrackedSoftGlassViews() {
        val targets = synchronized(refreshTargets) {
            refreshTargets.entries.mapNotNull { (view, target) ->
                target.type?.let { type -> view to type }
            }
        }
        targets.forEach { (view, type) ->
            if (!view.isAttachedToWindow) return@forEach
            val material = materialForType(type)
            if (material.type == MaterialType.SOFT) {
                SoftGlassController.apply(view, material.softGlass)
            } else if (SoftGlassController.isManaged(view)) {
                SoftGlassController.release(view)
            }
        }
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

    private fun activateSoftGlassSession(type: IslandType, material: MaterialConfig) {
        activeSoftGlassType = type
        SoftGlassController.activateWindowSession(material.softGlass)
    }

    /**
     * EXPAND installs its content after updateBackgroundBg(), unlike SMALL/BIG.
     * Apply native Bionics parameters after the child-tree transaction so a
     * notification RemoteViews update cannot invalidate the material RenderNode.
     */
    private fun hookExpandedContentInstall(
        module: XposedModule,
        classLoader: ClassLoader,
    ) {
        val expandedClass = runCatching {
            Class.forName(EXPANDED_VIEW_CLASS, false, classLoader)
        }.getOrNull() ?: return
        expandedClass.declaredMethods
            .filter { method ->
                method.name.startsWith("setContentView") &&
                    method.parameterTypes.contentEquals(arrayOf(View::class.java))
            }
            .forEach { method ->
                method.isAccessible = true
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    val expandedView = chain.thisObject as? View
                        ?: return@intercept result
                    val material = materialForType(IslandType.EXPAND)
                    if (material.type == MaterialType.SOFT) {
                        // setContentView* may replace the RemoteViews subtree without a
                        // subsequent state callback. Re-submit Xiaomi's native token directly
                        // to the authoritative expanded View after every replacement.
                        if (SoftGlassController.apply(expandedView, material.softGlass)) {
                            activateSoftGlassSession(IslandType.EXPAND, material)
                        }
                    }
                    result
                }
            }
    }

    private data class RefreshTarget(
        val contentView: WeakReference<Any>,
        val updateMethod: Method,
        val promoted: Boolean,
        val type: IslandType?,
        val stateField: java.lang.reflect.Field,
    )

}
