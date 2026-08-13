package io.github.hyperisland.xposed.hook.SystemUI

import android.os.Bundle
import android.graphics.Region
import android.service.notification.StatusBarNotification
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.log
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

/** Publishes SystemUI's semantic island show/hide transitions without frame polling. */
object DynamicIslandVisibilityHook : BaseHook() {
    private const val TAG = "HyperIsland[IslandVisibility]"
    private const val GLOBAL_REGION_KEY = "__status_bar_island_region__"
    private const val EVENT_COORDINATOR_CLASS =
        "miui.systemui.dynamicisland.event.DynamicIslandEventCoordinator"
    private const val CONTENT_VIEW_CLASS =
        "miui.systemui.dynamicisland.window.content.DynamicIslandContentView"
    private const val STATUS_BAR_ISLAND_CONTROLLER_CLASS =
        "com.android.systemui.statusbar.StatusBarIslandControllerImpl"
    private const val DYNAMIC_ISLAND_CONTROLLER_CLASS =
        "com.android.systemui.statusbar.notification.DynamicIslandController"
    private const val ANIMATION_CONTROLLER_CLASS =
        "miui.systemui.dynamicisland.anim.DynamicIslandAnimationController"

    private val hookedCoordinatorClassLoaders = ConcurrentHashMap.newKeySet<Int>()
    private val hookedContentClassLoaders = ConcurrentHashMap.newKeySet<Int>()
    private val hookedStatusBarClassLoaders = ConcurrentHashMap.newKeySet<Int>()
    private val hookedRegionClassLoaders = ConcurrentHashMap.newKeySet<Int>()
    private val hookedAnimationClassLoaders = ConcurrentHashMap.newKeySet<Int>()
    private val listeners = ConcurrentHashMap.newKeySet<(Event) -> Unit>()
    private val visibleKeysByView = Collections.synchronizedMap(WeakHashMap<Any, String>())
    private val visibilityByKey = ConcurrentHashMap<String, Boolean>()

    @Volatile
    private var globalVisibility: Boolean? = null

    @Volatile
    var available = false
        private set

    @Volatile
    private var lifecycleAvailable = false

    @Volatile
    private var statusBarCountAvailable = false

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        hookStatusBarIslandCount(module, param.defaultClassLoader)
        hookStatusBarIslandRegion(module, param.defaultClassLoader)
        hookContentLifecycle(module, param.defaultClassLoader)
        hookCoordinator(module, param.defaultClassLoader)
        hookAnimationFallback(module, param.defaultClassLoader)
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { classLoader ->
            hookStatusBarIslandCount(module, classLoader)
            hookStatusBarIslandRegion(module, classLoader)
            hookContentLifecycle(module, classLoader)
            hookCoordinator(module, classLoader)
            hookAnimationFallback(module, classLoader)
        }
    }

    private fun hookStatusBarIslandRegion(module: XposedModule, classLoader: ClassLoader) {
        val classLoaderId = System.identityHashCode(classLoader)
        if (!hookedRegionClassLoaders.add(classLoaderId)) return
        try {
            val clazz = try {
                classLoader.loadClass(DYNAMIC_ISLAND_CONTROLLER_CLASS)
            } catch (_: ClassNotFoundException) {
                hookedRegionClassLoaders.remove(classLoaderId)
                return
            }
            val methods = clazz.declaredMethods.filter { method ->
                method.name == "onIslandViewChanged" && method.parameterCount == 1 &&
                    method.parameterTypes[0] == Bundle::class.java
            }
            methods.forEach { method ->
                module.hook(method).intercept { chain ->
                    val bundle = chain.args.getOrNull(0) as? Bundle
                    if (bundle?.getString("action_key") == "action_back_island_width_changed") {
                        val region = bundle.getParcelable(
                            "extra_back_island_region",
                            Region::class.java,
                        )
                        dispatchGlobalVisibility(region != null && !region.isEmpty)
                    }
                    chain.proceed()
                }
            }
            log(module, "hooked status bar island region (cl=$classLoaderId, methods=${methods.size})")
        } catch (e: Throwable) {
            hookedRegionClassLoaders.remove(classLoaderId)
            logError(module, "status bar region hook failed cl=$classLoaderId: ${e.message}")
        }
    }

    private fun hookStatusBarIslandCount(module: XposedModule, classLoader: ClassLoader) {
        val classLoaderId = System.identityHashCode(classLoader)
        if (!hookedStatusBarClassLoaders.add(classLoaderId)) return
        try {
            val clazz = try {
                classLoader.loadClass(STATUS_BAR_ISLAND_CONTROLLER_CLASS)
            } catch (_: ClassNotFoundException) {
                hookedStatusBarClassLoaders.remove(classLoaderId)
                return
            }
            val methods = clazz.declaredMethods.filter { method ->
                method.name == "onIslandCountChanged" && method.parameterCount == 3 &&
                    method.parameterTypes[0] == Boolean::class.javaPrimitiveType &&
                    method.parameterTypes[2] == String::class.java
            }
            methods.forEach { method ->
                module.hook(method).intercept { chain ->
                    val added = chain.args.getOrNull(0) as? Boolean
                    val prop = chain.args.getOrNull(1) as? Int
                    val key = chain.args.getOrNull(2) as? String
                    if (added != null && key != null) dispatchStatusBarCount(added, prop, key)
                    chain.proceed()
                }
            }
            if (methods.isNotEmpty()) {
                statusBarCountAvailable = true
                available = true
            }
            log(module, "hooked status bar island count (cl=$classLoaderId, methods=${methods.size})")
        } catch (e: Throwable) {
            hookedStatusBarClassLoaders.remove(classLoaderId)
            logError(module, "status bar count hook failed cl=$classLoaderId: ${e.message}")
        }
    }

    private fun hookContentLifecycle(module: XposedModule, classLoader: ClassLoader) {
        val classLoaderId = System.identityHashCode(classLoader)
        if (!hookedContentClassLoaders.add(classLoaderId)) return
        try {
            val clazz = try {
                classLoader.loadClass(CONTENT_VIEW_CLASS)
            } catch (_: ClassNotFoundException) {
                hookedContentClassLoaders.remove(classLoaderId)
                return
            }
            var hookedMethods = 0
            clazz.declaredMethods
                .filter { method ->
                    method.parameterCount == 0 &&
                        (method.name == "showIslandLayout" || method.name == "hideIslandLayout")
                }
                .forEach { method ->
                    val visible = method.name == "showIslandLayout"
                    module.hook(method).intercept { chain ->
                        if (!statusBarCountAvailable) {
                            dispatchView(chain.thisObject, visible, method.name)
                        }
                        chain.proceed()
                    }
                    hookedMethods++
                }
            if (hookedMethods > 0) {
                lifecycleAvailable = true
                available = true
            }
            log(module, "hooked content lifecycle (cl=$classLoaderId, methods=$hookedMethods)")
        } catch (e: Throwable) {
            hookedContentClassLoaders.remove(classLoaderId)
            logError(module, "content lifecycle hook failed cl=$classLoaderId: ${e.message}")
        }
    }

    fun addListener(listener: (Event) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (Event) -> Unit) {
        listeners.remove(listener)
    }

    private fun hookCoordinator(module: XposedModule, classLoader: ClassLoader) {
        val classLoaderId = System.identityHashCode(classLoader)
        if (!hookedCoordinatorClassLoaders.add(classLoaderId)) return
        try {
            val clazz = try {
                classLoader.loadClass(EVENT_COORDINATOR_CLASS)
            } catch (_: ClassNotFoundException) {
                hookedCoordinatorClassLoaders.remove(classLoaderId)
                return
            }
            val methods = clazz.declaredMethods.filter { method ->
                method.name == "onStateChange" && method.parameterCount == 2 &&
                    method.parameterTypes[0] == String::class.java
            }
            methods.forEach { method ->
                module.hook(method).intercept { chain ->
                    val transition = chain.args.getOrNull(0) as? String
                    val view = chain.args.getOrNull(1)
                    if (transition != null && view != null) {
                        dispatch(transition, view)
                    }
                    chain.proceed()
                }
            }
            if (methods.isNotEmpty()) {
                available = true
            }
            log(module, "hooked onStateChange (cl=$classLoaderId, methods=${methods.size})")
        } catch (e: Throwable) {
            hookedCoordinatorClassLoaders.remove(classLoaderId)
            logError(module, "hook failed cl=$classLoaderId: ${e.message}")
        }
    }

    private fun hookAnimationFallback(module: XposedModule, classLoader: ClassLoader) {
        val classLoaderId = System.identityHashCode(classLoader)
        if (!hookedAnimationClassLoaders.add(classLoaderId)) return
        try {
            val clazz = try {
                classLoader.loadClass(ANIMATION_CONTROLLER_CLASS)
            } catch (_: ClassNotFoundException) {
                hookedAnimationClassLoaders.remove(classLoaderId)
                return
            }
            clazz.declaredMethods
                .filter { it.name == "onStateChange" && it.parameterCount >= 1 }
                .forEach { method ->
                    module.hook(method).intercept { chain ->
                        val result = chain.proceed()
                        dispatchLegacyState(chain.args.getOrNull(0))
                        result
                    }
                }
        } catch (e: Throwable) {
            hookedAnimationClassLoaders.remove(classLoaderId)
            logError(module, "fallback hook failed cl=$classLoaderId: ${e.message}")
        }
    }

    private fun dispatch(transition: String, view: Any) {
        val visible = when (transition) {
            "hidden_to_big", "hidden_to_small", "hidden_to_expanded",
            "temp_hidden_to_show" -> true

            "big_to_hidden", "small_to_hidden", "expanded_to_hidden",
            "show_to_temp_hidden", "app_to_hidden", "sub_app_to_hidden",
            "mini_window_to_hidden", "sub_mini_window_to_hidden" -> false
            else -> return
        }
        dispatchView(view, visible, transition)
    }

    private fun dispatchStatusBarCount(added: Boolean, prop: Int?, key: String) {
        val event = Event(
            key = key,
            visible = added,
            notificationId = notificationIdFromKey(key),
            sourcePackage = key.split('|').getOrNull(1)?.takeIf { it.contains('.') },
            sourceChannel = null,
        )
        //diag("source=statusBarIslandCount key=$key visible=$added prop=$prop id=${event.notificationId}")
        // Removal only unregisters status-bar island info; the rendered small island may remain.
        if (!added) return
        val previous = visibilityByKey.put(key, true)
        if (previous == true) return
        listeners.forEach { listener -> runCatching { listener(event) } }
    }

    private fun dispatchGlobalVisibility(visible: Boolean) {
        val previous = globalVisibility
        if (previous == visible) return
        globalVisibility = visible
        //diag("source=statusBarIslandRegion visible=$visible")
        val event = Event(
            key = GLOBAL_REGION_KEY,
            visible = visible,
            notificationId = null,
            sourcePackage = null,
            sourceChannel = null,
            global = true,
        )
        listeners.forEach { listener -> runCatching { listener(event) } }
    }

    private fun dispatchView(view: Any, visible: Boolean, source: String) {
        val data = invokeNoArg(view, "getCurrentIslandData")
        val extras = data?.let { invokeNoArg(it, "getExtras") as? Bundle }
        val sbn = extras?.getParcelable("miui.sbn", StatusBarNotification::class.java)
        val notificationExtras = sbn?.notification?.extras
        val currentKey = (invokeNoArg(view, "getIslandKey") as? String)
            ?: (data?.let { invokeNoArg(it, "getKey") } as? String)
            ?: sbn?.key
        val key = if (visible) {
            currentKey?.also { visibleKeysByView[view] = it }
        } else {
            visibleKeysByView.remove(view) ?: currentKey
        } ?: return
        val event = Event(
            key = key,
            visible = visible,
            notificationId = sbn?.id ?: notificationIdFromKey(key),
            sourcePackage = notificationExtras?.getString("hyperisland_source_pkg")
                ?: extras?.getString("hyperisland_source_pkg")
                ?: extras?.getString("miui.pkg.name")
                ?: sbn?.packageName,
            sourceChannel = notificationExtras?.getString("hyperisland_source_channel")
                ?: extras?.getString("hyperisland_source_channel"),
        )
        val previous = visibilityByKey.put(key, visible)
        if (previous == visible) return
        //diag("source=$source key=$key visible=$visible pkg=${event.sourcePackage} channel=${event.sourceChannel}")
        listeners.forEach { listener -> runCatching { listener(event) } }
    }

    private fun dispatchLegacyState(state: Any?) {
        state ?: return
        val stateText = invokeNoArg(state, "getState")?.toString() ?: return
        val visible = when {
            stateText.contains("Deleted") -> false
            stateText.contains("BigIsland") || stateText.contains("SmallIsland") ||
                stateText.contains("Expand") -> true
            else -> return
        }
        val data = sequenceOf("getCurrentIslandData", "getIslandData", "getData")
            .mapNotNull { invokeNoArg(state, it) }
            .firstOrNull()
        val extras = (data?.let { invokeNoArg(it, "getExtras") }
            ?: invokeNoArg(state, "getExtras")) as? Bundle
        val key = (data?.let { invokeNoArg(it, "getKey") } as? String)
            ?: extras?.getString("key")
            ?: extras?.getString("miui.notif.key")
            ?: return
        listeners.forEach { listener ->
            runCatching {
                listener(
                    Event(
                        key = key,
                        visible = visible,
                        notificationId = notificationIdFromKey(key),
                        sourcePackage = extras?.getString("hyperisland_source_pkg")
                            ?: extras?.getString("miui.pkg.name")
                            ?: key.split('|').getOrNull(1)?.takeIf { it.contains('.') },
                        sourceChannel = extras?.getString("hyperisland_source_channel"),
                    ),
                )
            }
        }
    }

    private fun invokeNoArg(target: Any, name: String): Any? = runCatching {
        var clazz: Class<*>? = target.javaClass
        while (clazz != null) {
            clazz.declaredMethods.firstOrNull { it.name == name && it.parameterCount == 0 }
                ?.let { method ->
                    method.isAccessible = true
                    return@runCatching method.invoke(target)
                }
            clazz = clazz.superclass
        }
        null
    }.getOrNull()

    private fun notificationIdFromKey(key: String): Int? =
        key.split('|').getOrNull(2)?.toIntOrNull()

    private fun diag(message: String) {
        if (!ConfigManager.isDebugLogEnabled()) return
        ConfigManager.module()?.log("$TAG $message")
    }

    data class Event(
        val key: String,
        val visible: Boolean,
        val notificationId: Int?,
        val sourcePackage: String?,
        val sourceChannel: String?,
        val global: Boolean = false,
    )
}
