package io.github.hyperisland.xposed.hook

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** Receives the light/dark and panel state which SystemUI already sends to Dynamic Island. */
internal object IslandUiStateSource {

    private val stateListeners = CopyOnWriteArrayList<() -> Unit>()
    private val hookedClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    )
    private val hookedContentMethods = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Method, Boolean>())
    )

    @Volatile private var readableTint: Int? = null
    @Volatile private var islandAnimationRunning = false
    @Volatile private var notificationPanelVisible = false
    @Volatile private var notificationPanelExpanded = false
    @Volatile private var notificationPanelHeightActive = false
    @Volatile private var notificationPanelBlurActive = false
    @Volatile private var controlCenterExpanded = false
    @Volatile private var controlCenterBlurActive = false

    fun addStateListener(listener: () -> Unit) = stateListeners.addIfAbsent(listener)

    fun getReadableTint(): Int? = readableTint

    /** Restores the last delivered endpoint when a panel event wins the same-frame race. */
    fun preserveReadableTint(tint: Int) {
        readableTint = tint
    }

    fun isPanelInteractionActive(): Boolean =
        notificationPanelVisible || notificationPanelExpanded ||
            notificationPanelHeightActive || notificationPanelBlurActive ||
            controlCenterExpanded || controlCenterBlurActive

    fun isColorFrozen(): Boolean = islandAnimationRunning || isPanelInteractionActive()

    fun hookClasses(module: XposedModule, classLoader: ClassLoader) {
        hookContentDiscovery(module, classLoader)
        hookIslandAnimationLifecycle(module, classLoader)
        hookPanelHeightFallback(module, classLoader)
    }

    /** The coroutine owns the concrete plugin-side DynamicIslandContent instance. */
    private fun hookContentDiscovery(module: XposedModule, classLoader: ClassLoader) {
        CONTENT_DISCOVERY_CLASSES.forEach { className ->
            runCatching {
                val targetClass = classLoader.loadClass(className)
                if (!hookedClasses.add(targetClass)) return@runCatching
                targetClass.declaredConstructors.forEach { constructor ->
                    constructor.isAccessible = true
                    module.hook(constructor).intercept { chain ->
                        val result = chain.proceed()
                        chain.args.firstOrNull { candidate ->
                            candidate != null && candidate.javaClass.methods.any(::isHandleDynamicIsland)
                        }?.let { content -> hookConcreteContent(module, content.javaClass) }
                        result
                    }
                }
            }.onFailure { error ->
                if (error !is ClassNotFoundException) {
                    log(module, "discovery hook failed: ${error.message}", Log.ERROR)
                }
            }
        }
    }

    private fun hookConcreteContent(module: XposedModule, contentClass: Class<*>) {
        val method = contentClass.methods.firstOrNull(::isHandleDynamicIsland) ?: return
        if (!hookedContentMethods.add(method)) return
        runCatching {
            method.isAccessible = true
            runCatching { module.deoptimize(method) }
            module.hook(method).intercept { chain ->
                (chain.args.firstOrNull() as? Bundle)?.let(::handleIslandAction)
                chain.proceed()
            }
            log(module, "hooked ${method.declaringClass.name}#handleDynamicIsland")
        }.onFailure { error ->
            hookedContentMethods.remove(method)
            log(module, "content hook failed: ${error.message}", Log.ERROR)
        }
    }

    private fun isHandleDynamicIsland(method: Method): Boolean =
        method.name == "handleDynamicIsland" &&
            method.parameterTypes.contentEquals(arrayOf(Bundle::class.java))

    private fun handleIslandAction(bundle: Bundle) {
        when (bundle.getString(ACTION_KEY)) {
            ACTION_UPDATE_LIGHT -> {
                // SystemUI sends extra_is_light = !(darkIntensity > 0.5).
                if (!isPanelInteractionActive() && bundle.containsKey(EXTRA_IS_LIGHT)) {
                    val tint = if (bundle.getBoolean(EXTRA_IS_LIGHT)) Color.WHITE else Color.BLACK
                    if (readableTint != tint) {
                        readableTint = tint
                        notifyStateChanged()
                    }
                }
            }
            ACTION_NOTIFICATION_PANEL_VISIBLE -> updatePanelFlag {
                notificationPanelVisible = bundle.getBoolean(EXTRA_NOTIFICATION_PANEL_VISIBLE)
            }
            ACTION_NOTIFICATION_PANEL_EXPANDED -> updatePanelFlag {
                notificationPanelExpanded = bundle.getBoolean(EXTRA_NOTIFICATION_PANEL_EXPANDED)
            }
            ACTION_NOTIFICATION_PANEL_EXPAND_HEIGHT -> updatePanelFlag {
                notificationPanelHeightActive =
                    bundle.getFloat(EXTRA_NOTIFICATION_PANEL_EXPANDED_HEIGHT, 0f) > 0f
            }
            ACTION_NOTIFICATION_PANEL_BLUR_RATIO -> updatePanelFlag {
                notificationPanelBlurActive =
                    bundle.getFloat(EXTRA_NOTIFICATION_PANEL_BLUR_RATIO, 0f) > 0f
            }
            ACTION_CONTROL_CENTER_EXPANDED -> updatePanelFlag {
                controlCenterExpanded = bundle.getBoolean(EXTRA_CONTROL_CENTER_EXPANDED)
            }
            ACTION_CONTROL_CENTER_BLUR_RATIO -> updatePanelFlag {
                controlCenterBlurActive =
                    bundle.getFloat(EXTRA_CONTROL_CENTER_BLUR_RATIO, 0f) > 0f
            }
        }
    }

    private inline fun updatePanelFlag(update: () -> Unit) {
        val before = isPanelInteractionActive()
        update()
        if (before != isPanelInteractionActive()) notifyStateChanged()
    }

    private fun hookIslandAnimationLifecycle(module: XposedModule, classLoader: ClassLoader) {
        runCatching {
            val coordinatorClass = classLoader.loadClass(ISLAND_EVENT_COORDINATOR_CLASS)
            if (!hookedClasses.add(coordinatorClass)) return
            coordinatorClass.declaredMethods.forEach { method ->
                when (method.name) {
                    "onAnimationStart" -> module.hook(method).intercept { chain ->
                        if (!islandAnimationRunning) {
                            islandAnimationRunning = true
                            notifyStateChanged()
                        }
                        chain.proceed()
                    }
                    "onAnimationFinished", "onAnimationCancel" ->
                        module.hook(method).intercept { chain ->
                            val result = chain.proceed()
                            if (islandAnimationRunning) {
                                islandAnimationRunning = false
                                notifyStateChanged()
                            }
                            result
                        }
                }
            }
        }.onFailure { error ->
            if (error !is ClassNotFoundException) {
                log(module, "animation hook failed: ${error.message}", Log.ERROR)
            }
        }
    }

    /** Low-frequency compatibility path for builds where plugin content is loaded unusually. */
    private fun hookPanelHeightFallback(module: XposedModule, classLoader: ClassLoader) {
        runCatching {
            val controllerClass = classLoader.loadClass(ISLAND_WINDOW_VIEW_CONTROLLER_CLASS)
            if (!hookedClasses.add(controllerClass)) return
            controllerClass.declaredMethods
                .filter { it.name == "notificationPanelExpandHeightChanged" }
                .forEach { method ->
                    module.hook(method).intercept { chain ->
                        val active = (chain.args.firstOrNull() as? Float)?.let { it > 0f }
                        if (active != null && notificationPanelHeightActive != active) {
                            notificationPanelHeightActive = active
                            notifyStateChanged()
                        }
                        chain.proceed()
                    }
                }
        }.onFailure { error ->
            if (error !is ClassNotFoundException) {
                log(module, "panel fallback failed: ${error.message}", Log.ERROR)
            }
        }
    }

    private fun notifyStateChanged() {
        stateListeners.forEach { listener -> runCatching(listener) }
    }

    private fun log(module: XposedModule, message: String, priority: Int = Log.DEBUG) {
        module.log(priority, "HyperIsland[IslandUiState]", message)
    }

    private val CONTENT_DISCOVERY_CLASSES = arrayOf(
        "com.android.systemui.statusbar.notification.domain.interactor." +
            "DynamicIslandContentInteractor\$listenForScreenOn\$1",
        "com.android.systemui.statusbar.notification.domain.interactor." +
            "DynamicIslandContentInteractor\$listenForNotifVisibleChanged\$1",
    )

    private const val ISLAND_EVENT_COORDINATOR_CLASS =
        "miui.systemui.dynamicisland.event.DynamicIslandEventCoordinator"
    private const val ISLAND_WINDOW_VIEW_CONTROLLER_CLASS =
        "miui.systemui.dynamicisland.window.DynamicIslandWindowViewController"

    private const val ACTION_KEY = "action_key"
    private const val ACTION_UPDATE_LIGHT = "action_update_light"
    private const val ACTION_NOTIFICATION_PANEL_VISIBLE = "action_notification_panel_visible"
    private const val ACTION_NOTIFICATION_PANEL_EXPANDED = "action_notification_panel_expanded"
    private const val ACTION_NOTIFICATION_PANEL_EXPAND_HEIGHT =
        "action_notification_panel_expand_height"
    private const val ACTION_NOTIFICATION_PANEL_BLUR_RATIO =
        "action_notification_panel_blur_ratio"
    private const val ACTION_CONTROL_CENTER_EXPANDED = "action_control_center_expanded"
    private const val ACTION_CONTROL_CENTER_BLUR_RATIO = "action_control_center_blur_ratio"
    private const val EXTRA_IS_LIGHT = "extra_is_light"
    private const val EXTRA_NOTIFICATION_PANEL_VISIBLE = "extra_notification_panel_visible"
    private const val EXTRA_NOTIFICATION_PANEL_EXPANDED = "extra_notification_panel_expanded"
    private const val EXTRA_NOTIFICATION_PANEL_EXPANDED_HEIGHT =
        "extra_notification_panel_expanded_height"
    private const val EXTRA_NOTIFICATION_PANEL_BLUR_RATIO =
        "extra_notification_panel_blur_ratio"
    private const val EXTRA_CONTROL_CENTER_EXPANDED = "extra_control_center_expanded"
    private const val EXTRA_CONTROL_CENTER_BLUR_RATIO = "extra_control_center_blur_ratio"
}
