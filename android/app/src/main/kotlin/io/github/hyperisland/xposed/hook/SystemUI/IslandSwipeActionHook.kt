package io.github.hyperisland.xposed.hook.SystemUI

import android.app.Notification
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.service.notification.StatusBarNotification
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.log
import io.github.hyperisland.xposed.hook.ActiveIslandDismissHook
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

/** Executes notification actions only after a matching user island swipe completes. */
object IslandSwipeActionHook : BaseHook() {
    private const val TAG = "HyperIsland[IslandSwipeAction]"
    private const val WINDOW_VIEW_CLASS =
        "miui.systemui.dynamicisland.window.DynamicIslandWindowView"
    private const val EVENT_COORDINATOR_CLASS =
        "miui.systemui.dynamicisland.event.DynamicIslandEventCoordinator"
    private const val ACTION_NONE = "none"
    private const val ACTION_CANCEL_NOTIFICATION = "cancel_notification"
    private const val ACTION_HIDE_ISLAND = "hide_island"
    private const val EXPANDED_ACTION_PREF = "pref_expanded_collapse_action"
    private const val BIG_ACTION_PREF = "pref_big_island_collapse_action"
    private const val IGNORE_ONGOING_PREF = "pref_island_swipe_ignore_ongoing"
    private const val COMPLETION_TIMEOUT_MS = 1500L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val hookedWindowClassLoaders = ConcurrentHashMap.newKeySet<Int>()
    private val hookedCoordinatorClassLoaders = ConcurrentHashMap.newKeySet<Int>()
    private val pendingExpandedByCoordinator = Collections.synchronizedMap(WeakHashMap<Any, Target>())
    private val pendingBigByView = Collections.synchronizedMap(WeakHashMap<Any, Target>())

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        hookWindowView(module, param.defaultClassLoader)
        hookCoordinator(module, param.defaultClassLoader)
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { classLoader ->
            hookWindowView(module, classLoader)
            hookCoordinator(module, classLoader)
        }
    }

    private fun hookWindowView(module: XposedModule, classLoader: ClassLoader) {
        val classLoaderId = System.identityHashCode(classLoader)
        if (!hookedWindowClassLoaders.add(classLoaderId)) return
        try {
            val clazz = try {
                classLoader.loadClass(WINDOW_VIEW_CLASS)
            } catch (_: ClassNotFoundException) {
                hookedWindowClassLoaders.remove(classLoaderId)
                return
            }
            clazz.declaredMethods
                .filter {
                    it.name == "collapse" && it.parameterCount == 1 &&
                        it.parameterTypes[0] == String::class.java
                }
                .forEach { method ->
                    module.hook(method).intercept { chain ->
                        val reason = chain.args.getOrNull(0) as? String
                        val action = expandedAction()
                        val view = if (reason == "swipe up" && action != ACTION_NONE) {
                            invokeNoArg(chain.thisObject, "getCurrentExpandedState")
                        } else {
                            null
                        }
                        val target = view?.let { targetFromView(it, action) }
                        val coordinator = view?.let {
                            invokeNoArg(it, "getDynamicIslandEventCoordinator")
                        }
                        if (target != null && coordinator != null) {
                            pendingExpandedByCoordinator[coordinator] = target
                            scheduleExpandedFallback(coordinator, target)
                            diag("expanded swipe captured key=${target.key} action=$action")
                        }
                        val result = chain.proceed()
                        if (view != null && coordinator != null &&
                            invokeNoArg(chain.thisObject, "getCurrentExpandedState") === view
                        ) {
                            pendingExpandedByCoordinator.remove(coordinator)
                            diag("expanded collapse skipped key=${target?.key}")
                        }
                        result
                    }
                }
        } catch (e: Throwable) {
            hookedWindowClassLoaders.remove(classLoaderId)
            logError(module, "window hook failed cl=$classLoaderId: ${e.message}")
        }
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
            clazz.declaredMethods
                .filter { it.name == "dispatchEvent" && it.parameterCount == 2 }
                .forEach { method ->
                    module.hook(method).intercept { chain ->
                        val eventName = chain.args.getOrNull(0)?.javaClass?.simpleName
                        val action = bigAction()
                        var targetView: Any? = null
                        var target: Target? = null
                        if (action != ACTION_NONE && (eventName == "SwipeLeft" || eventName == "SwipeRight")) {
                            val handler = invokeNoArg(chain.thisObject, "getBigIslandStateHandler")
                            targetView = handler?.let { invokeNoArg(it, "getCurrent") }
                            target = targetView?.let { targetFromView(it, action) }
                        }
                        if (targetView != null && target != null) {
                            pendingBigByView[targetView] = target
                            scheduleBigExpiry(targetView, target)
                            diag("big swipe captured key=${target.key} action=$action event=$eventName")
                        }
                        chain.proceed()
                    }
                }
            clazz.declaredMethods
                .filter {
                    it.name == "onWindowAnimExtendLifetimeEnd" && it.parameterCount == 1 &&
                        it.parameterTypes[0] == Bundle::class.java
                }
                .forEach { method ->
                    module.hook(method).intercept { chain ->
                        val result = chain.proceed()
                        pendingExpandedByCoordinator.remove(chain.thisObject)?.let {
                            execute(it, "expanded_complete")
                        }
                        result
                    }
                }
            clazz.declaredMethods
                .filter { it.name == "onStateChange" && it.parameterCount == 2 }
                .forEach { method ->
                    module.hook(method).intercept { chain ->
                        val result = chain.proceed()
                        val transition = chain.args.getOrNull(0) as? String
                        val view = chain.args.getOrNull(1)
                        if (transition == "big_to_hidden" && view != null) {
                            pendingBigByView.remove(view)?.let {
                                execute(it, "big_to_hidden")
                            }
                        }
                        result
                    }
                }
        } catch (e: Throwable) {
            hookedCoordinatorClassLoaders.remove(classLoaderId)
            logError(module, "coordinator hook failed cl=$classLoaderId: ${e.message}")
        }
    }

    private fun scheduleExpandedFallback(coordinator: Any, target: Target) {
        mainHandler.postDelayed({
            if (pendingExpandedByCoordinator[coordinator] === target) {
                pendingExpandedByCoordinator.remove(coordinator)
                execute(target, "expanded_timeout")
            }
        }, COMPLETION_TIMEOUT_MS)
    }

    private fun scheduleBigExpiry(view: Any, target: Target) {
        mainHandler.postDelayed({
            if (pendingBigByView[view] === target) pendingBigByView.remove(view)
        }, COMPLETION_TIMEOUT_MS)
    }

    private fun targetFromView(view: Any, action: String): Target? {
        val data = invokeNoArg(view, "getCurrentIslandData") ?: return null
        val extras = invokeNoArg(data, "getExtras") as? Bundle
        val sbn = extras?.getParcelable("miui.sbn", StatusBarNotification::class.java)
        if (sbn != null && shouldIgnore(sbn)) return null
        val key = (invokeNoArg(data, "getKey") as? String)
            ?: (invokeNoArg(view, "getIslandKey") as? String)
            ?: sbn?.key
            ?: return null
        return Target(key, sbn, action)
    }

    private fun shouldIgnore(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification
        if (!ConfigManager.getBoolean(IGNORE_ONGOING_PREF, true)) return false
        val ongoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0
        val updatable = hasUpdatableFocusParam(notification.extras)
        if (ongoing || updatable) {
            diag("ignored persistent key=${sbn.key} ongoing=$ongoing updatable=$updatable")
            return true
        }
        return false
    }

    private fun hasUpdatableFocusParam(extras: Bundle): Boolean {
        return sequenceOf("miui.focus.param", "miui.focus.param.custom")
            .mapNotNull(extras::getString)
            .any { raw ->
                runCatching {
                    val root = JSONObject(raw)
                    (root.optJSONObject("param_v2") ?: root).optBoolean("updatable", false)
                }.getOrDefault(false)
            }
    }

    private fun execute(target: Target, source: String) {
        when (target.action) {
            ACTION_CANCEL_NOTIFICATION -> {
                val sbn = target.sbn
                if (sbn == null) {
                    diag("cancel skipped without sbn key=${target.key} source=$source")
                    return
                }
                if (!cancelNotification(sbn)) return
            }
            ACTION_HIDE_ISLAND -> ActiveIslandDismissHook.dismiss(target.key)
        }
        diag("executed key=${target.key} action=${target.action} source=$source")
    }

    private fun cancelNotification(sbn: StatusBarNotification): Boolean {
        return runCatching {
            val serviceManager = Class.forName("android.os.ServiceManager")
            val binder = serviceManager.getMethod("getService", String::class.java)
                .invoke(null, "notification") as IBinder
            val stub = Class.forName("android.app.INotificationManager\$Stub")
            val manager = stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            val method = manager.javaClass.methods.firstOrNull {
                it.name == "cancelNotificationWithTag" && it.parameterCount == 5
            } ?: error("cancelNotificationWithTag unavailable")
            val notificationKey = sbn.key
            val packageName = if (notificationKey == null ||
                notificationKey.contains(sbn.packageName)
            ) {
                sbn.packageName
            } else {
                sbn.opPkg ?: sbn.packageName
            }
            method.invoke(
                manager,
                packageName,
                "com.android.systemui",
                sbn.tag,
                sbn.id,
                sbn.userId,
            )
            true
        }.onFailure {
            diag("cancel failed key=${sbn.key} error=${it.cause?.message ?: it.message}")
        }.getOrDefault(false)
    }

    private fun expandedAction(): String = when (
        val value = ConfigManager.getString(EXPANDED_ACTION_PREF, ACTION_NONE)
    ) {
        ACTION_CANCEL_NOTIFICATION, ACTION_HIDE_ISLAND -> value
        else -> ACTION_NONE
    }

    private fun bigAction(): String = when (
        val value = ConfigManager.getString(BIG_ACTION_PREF, ACTION_NONE)
    ) {
        ACTION_CANCEL_NOTIFICATION -> value
        else -> ACTION_NONE
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

    private fun diag(message: String) {
        if (!ConfigManager.isDebugLogEnabled()) return
        ConfigManager.module()?.log("$TAG $message")
    }

    private data class Target(
        val key: String,
        val sbn: StatusBarNotification?,
        val action: String,
    )
}
