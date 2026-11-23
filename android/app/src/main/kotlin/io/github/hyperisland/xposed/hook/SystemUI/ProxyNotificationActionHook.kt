package io.github.hyperisland.xposed.hook.SystemUI

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.service.notification.StatusBarNotification
import android.view.View
import io.github.hyperisland.xposed.diagnostics.profiledHook
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.hyperisland.xposed.islanddispatch.definition.IslandDispatchContract
import io.github.hyperisland.xposed.utils.ClassLoaderAttemptGate
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.InvocationTargetException

object ProxyNotificationActionHook : BaseHook() {
    private const val TAG = "HyperIsland[ProxyNotificationAction]"
    private const val CLICK_HELPER =
        "miui.systemui.notification.focus.moduleV3.ext.ModuleViewHolderExtKt"
    private const val FOCUS_UTIL = "miui.systemui.util.FocusNotificationUtil"

    private val installAttempts = ClassLoaderAttemptGate()
    private val activeProxyAction = ThreadLocal<PendingIntent>()

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        install(module, param.defaultClassLoader)
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { loader ->
            install(module, loader)
        }
    }

    private fun install(module: XposedModule, loader: ClassLoader) {
        val clickHelper = runCatching { loader.loadClass(CLICK_HELPER) }.getOrNull() ?: return
        val pluginLoader = clickHelper.classLoader ?: return
        runCatching {
            val sendWithCollapse = clickHelper.getDeclaredMethod(
                "sendWithCollapse",
                PendingIntent::class.java,
                Notification.Action::class.java,
                Context::class.java,
                View::class.java,
                Boolean::class.javaPrimitiveType!!,
                StatusBarNotification::class.java,
            )
            val focusUtil = pluginLoader.loadClass(FOCUS_UTIL)
            val onActionClick = focusUtil.getDeclaredMethod("onActionClick", PendingIntent::class.java)
            val starterField = focusUtil.getDeclaredField("activityStarter").apply { isAccessible = true }
            val postStart = starterField.type.getMethod(
                "postStartActivityDismissingKeyguard", PendingIntent::class.java,
            )
            val isActivity = PendingIntent::class.java.getDeclaredMethod("isActivity")
                .apply { isAccessible = true }
            if (!installAttempts.enter(pluginLoader)) return

            val clickHook = module.profiledHook("ProxyNotificationActionHook", onActionClick).intercept { chain ->
                val pendingIntent = chain.args[0] as? PendingIntent
                if (pendingIntent == null || pendingIntent !== activeProxyAction.get()) {
                    return@intercept chain.proceed()
                }
                activeProxyAction.remove()
                val starter = runCatching { starterField.get(null) }.getOrNull()
                    ?: return@intercept chain.proceed()
                try {
                    // 调用原应用的令牌，保留创建者身份及临时 URI 授权。
                    postStart.invoke(starter, pendingIntent)
                } catch (error: InvocationTargetException) {
                    // 系统方法可能已将启动入队，避免再次发送同一动作。
                    logFailureOnce(module, "start") { "proxy action start failed: ${error.cause?.javaClass?.simpleName}" }
                } catch (_: IllegalAccessException) {
                    return@intercept chain.proceed()
                }
                null
            }

            try {
                module.profiledHook("ProxyNotificationActionHook", sendWithCollapse).intercept { chain ->
                    val pendingIntent = chain.args[0] as? PendingIntent
                    val action = chain.args[1] as? Notification.Action
                    val proxyAction = pendingIntent?.takeIf {
                        action?.actionIntent === it && runCatching {
                            action.extras.getBoolean(IslandDispatchContract.EXTRA_PROXY_ACTION) &&
                                isActivity.invoke(it) == true
                        }.getOrDefault(false)
                    }
                    val previous = activeProxyAction.get()
                    if (proxyAction == null && previous == null) return@intercept chain.proceed()

                    // 保留系统的防连点、动画检查和收起逻辑，只替换本次点击的启动方式。
                    if (proxyAction == null) activeProxyAction.remove() else activeProxyAction.set(proxyAction)
                    try {
                        chain.proceed()
                    } finally {
                        if (previous == null) activeProxyAction.remove() else activeProxyAction.set(previous)
                    }
                }
            } catch (error: Throwable) {
                runCatching { clickHook.unhook() }
                throw error
            }

            // onActionClick 可能已被内联到插件的点击分发方法。
            runCatching { module.deoptimize(sendWithCollapse) }
            log(module) { "hooked proxy notification action" }
        }.onFailure { error ->
            logFailureOnce(module, "install:${error.javaClass.name}") {
                "proxy action hook unavailable: ${error.message}"
            }
        }
    }
}
