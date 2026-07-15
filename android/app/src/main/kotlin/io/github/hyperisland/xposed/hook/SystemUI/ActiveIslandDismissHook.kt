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

    @Volatile
    private var focusControllerRef: WeakReference<Any>? = null

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        hookFocusNotificationController(module, param.defaultClassLoader)
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { classLoader ->
            hookFocusNotificationController(module, classLoader)
        }
    }

    fun dismiss(notificationKey: String) {
        if (notificationKey.isBlank()) return
        mainHandler.post {
            val controller = focusControllerRef?.get()
            if (controller == null) {
                diag("focus controller unavailable key=$notificationKey")
                return@post
            }
            try {
                val method = controller.javaClass.getMethod(
                    "access\$removeByKey",
                    controller.javaClass,
                    String::class.java,
                )
                method.invoke(null, controller, notificationKey)
                diag("focus removeByKey invoked key=$notificationKey")
            } catch (e: Throwable) {
                diag(
                    "focus removeByKey failed key=$notificationKey " +
                        "error=${e.cause?.message ?: e.message}",
                )
            }
        }
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
            clazz.declaredMethods
                .filter { it.name == "onNotificationPosted" && it.parameterCount >= 1 }
                .forEach { method ->
                    module.hook(method).intercept { chain ->
                        val sbn = chain.args.getOrNull(0) as? StatusBarNotification
                        if (sbn != null) {
                            focusControllerRef = WeakReference(chain.thisObject)
                            diag("focus controller captured key=${sbn.key}")
                        }
                        chain.proceed()
                    }
                }
        } catch (_: Throwable) {
            hookedClassLoaders.remove(classLoaderId)
        }
    }

    private fun diag(message: String) {
        if (!ConfigManager.isDebugLogEnabled()) return
        ConfigManager.module()?.log("HyperIsland[IslandDismissDiag] $message")
    }
}
