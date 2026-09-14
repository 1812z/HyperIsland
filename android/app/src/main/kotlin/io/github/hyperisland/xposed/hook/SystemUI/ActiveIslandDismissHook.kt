package io.github.hyperisland.xposed.hook

import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.log
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

object ActiveIslandDismissHook : BaseHook() {
    private const val TAG = "HyperIsland[IslandDismiss]"
    private const val FOCUS_NOTIFICATION_CONTROLLER_CLASS =
        "miui.systemui.notification.focus.FocusNotificationController"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val hookedClassLoaders = ConcurrentHashMap.newKeySet<Int>()
    private val pendingDismissals = ConcurrentHashMap<String, PendingDismissal>()

    private class PendingDismissal {
        @Volatile var cancelled = false
        var expectedSbn: StatusBarNotification? = null
        lateinit var runnable: Runnable
    }

    @Volatile
    private var focusControllerRef: WeakReference<Any>? = null

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        hookFocusNotificationController(module, param.defaultClassLoader)
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { classLoader ->
            hookFocusNotificationController(module, classLoader)
        }
    }

    fun dismiss(notificationKey: String, expectedSbn: StatusBarNotification? = null) {
        if (notificationKey.isBlank()) return
        val request = PendingDismissal()
        request.expectedSbn = expectedSbn ?: focusControllerRef?.get()?.let {
            resolveSbn(it, notificationKey)
        }
        request.runnable = Runnable {
            if (request.cancelled || pendingDismissals[notificationKey] !== request) {
                diag("stale dismiss ignored key=$notificationKey")
                return@Runnable
            }
            pendingDismissals.remove(notificationKey, request)
            val controller = focusControllerRef?.get()
            if (controller == null) {
                diag("focus controller unavailable key=$notificationKey")
                return@Runnable
            }
            val currentSbn = resolveSbn(controller, notificationKey)
            if (request.expectedSbn != null && !sameNotification(currentSbn, request.expectedSbn)) {
                diag("stale dismiss ignored after notification replacement key=$notificationKey")
                return@Runnable
            }
            try {
                // OS4 的 removeByKey 会同时删除通知中心使用的 FocusNotificationContent
                // 和岛数据，通知本体仍存在时会留下空白通知。OS3/OS4 均优先探测并调用
                // “只移除岛”的内部入口；没有该入口的旧版再回退到原有 removeByKey。
                val islandOnly = controller.javaClass.declaredMethods.firstOrNull {
                    it.name == "removeIslandDataByKey" && it.parameterCount == 2 &&
                        it.parameterTypes[0] == String::class.java &&
                        it.parameterTypes[1] == Boolean::class.javaPrimitiveType
                }
                if (islandOnly != null) {
                    val updateNoFloat = resolveIslandUpdateNoFloat(controller, notificationKey)
                    islandOnly.isAccessible = true
                    islandOnly.invoke(controller, notificationKey, updateNoFloat)
                    diag("focus island-only remove invoked key=$notificationKey")
                    return@Runnable
                }

                val direct = controller.javaClass.declaredMethods.firstOrNull {
                    it.name == "removeByKey" && it.parameterCount == 1 &&
                        it.parameterTypes[0] == String::class.java
                }
                if (direct != null) {
                    direct.isAccessible = true
                    direct.invoke(controller, notificationKey)
                } else {
                    val synthetic = controller.javaClass.declaredMethods.firstOrNull {
                        it.name == "access\$removeByKey" && it.parameterCount == 2 &&
                            it.parameterTypes[1] == String::class.java
                    } ?: error("removeByKey method unavailable")
                    synthetic.isAccessible = true
                    synthetic.invoke(null, controller, notificationKey)
                }
                diag("focus removeByKey invoked key=$notificationKey")
            } catch (e: Throwable) {
                diag(
                    "focus removeByKey failed key=$notificationKey " +
                        "error=${e.cause?.message ?: e.message}",
                )
            }
        }
        pendingDismissals.put(notificationKey, request)?.let { previous ->
            previous.cancelled = true
            mainHandler.removeCallbacks(previous.runnable)
        }
        mainHandler.post(request.runnable)
    }

    fun invalidate(notificationKey: String) {
        invalidateDismissals(notificationKey)
    }

    /**
     * A notification update can reuse the same key. Invalidate dismiss work that
     * was queued for the previous contents before it can remove the new island.
     */
    private fun invalidateDismissals(notificationKey: String) {
        if (notificationKey.isBlank()) return
        pendingDismissals.remove(notificationKey)?.let { request ->
            request.cancelled = true
            mainHandler.removeCallbacks(request.runnable)
        }
    }

    private fun resolveIslandUpdateNoFloat(controller: Any, notificationKey: String): Boolean {
        val sbn = resolveSbn(controller, notificationKey)
        return sbn?.notification?.extras
            ?.getBoolean("miui.island.updateNoFloat", false) == true
    }

    private fun resolveSbn(controller: Any, notificationKey: String): StatusBarNotification? {
        var current: Class<*>? = controller.javaClass
        while (current != null) {
            val clazz = current
            val field = runCatching { clazz.getDeclaredField("sbnMap") }.getOrNull()
            if (field != null) {
                val sbn = runCatching {
                    field.isAccessible = true
                    (field.get(controller) as? Map<*, *>)?.get(notificationKey) as? StatusBarNotification
                }.getOrNull()
                return sbn
            }
            current = clazz.superclass
        }
        return null
    }

    private fun sameNotification(
        current: StatusBarNotification?,
        expected: StatusBarNotification?,
    ): Boolean {
        if (current === expected) return true
        if (current == null || expected == null) return false
        return current.key == expected.key &&
            current.postTime == expected.postTime &&
            current.uid == expected.uid &&
            current.id == expected.id &&
            current.tag == expected.tag
    }

    private fun hookFocusNotificationController(module: XposedModule, classLoader: ClassLoader) {
        val classLoaderId = System.identityHashCode(classLoader)
        if (!hookedClassLoaders.add(classLoaderId)) return
        try {
            val clazz = try {
                classLoader.loadClass(FOCUS_NOTIFICATION_CONTROLLER_CLASS)
            } catch (_: ClassNotFoundException) {
                hookedClassLoaders.remove(classLoaderId)
                return
            }
            clazz.declaredConstructors.forEach { constructor ->
                module.hook(constructor).intercept { chain ->
                    val result = chain.proceed()
                    focusControllerRef = WeakReference(chain.thisObject)
                    result
                }
            }
            clazz.declaredMethods
                .filter { it.name == "onNotificationPosted" && it.parameterCount >= 1 }
                .forEach { method ->
                    module.hook(method).intercept { chain ->
                        val sbn = chain.args.getOrNull(0) as? StatusBarNotification
                        if (sbn != null) {
                            focusControllerRef = WeakReference(chain.thisObject)
                            invalidateDismissals(sbn.key)
                            clearTimeoutRemovedKey(chain.thisObject, sbn.key)
                            //diag("focus controller captured key=${sbn.key}")
                        }
                        chain.proceed()
                    }
                }
        } catch (_: Throwable) {
            hookedClassLoaders.remove(classLoaderId)
        }
    }

    private fun clearTimeoutRemovedKey(controller: Any, notificationKey: String) {
        var current: Class<*>? = controller.javaClass
        while (current != null) {
            val clazz = current
            val field = runCatching {
                clazz.getDeclaredField("islandTimeoutRemovedList")
            }.getOrNull()
            if (field != null) {
                runCatching {
                    field.isAccessible = true
                    (field.get(controller) as? MutableCollection<*>)?.remove(notificationKey)
                }.onFailure {
                    diag("timeout-removed cleanup failed key=$notificationKey error=${it.message}")
                }
                return
            }
            current = clazz.superclass
        }
    }

    private fun diag(message: String) {
        if (!ConfigManager.isDebugLogEnabled()) return
        ConfigManager.module()?.log("HyperIsland[IslandDismissDiag] $message")
    }
}
