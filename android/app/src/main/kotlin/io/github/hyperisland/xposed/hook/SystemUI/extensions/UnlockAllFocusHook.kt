package io.github.hyperisland.xposed.hook.SystemUI.extensions

import android.content.Context
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModule
import java.util.Collections
import java.util.WeakHashMap

/**
 * 移除焦点通知白名单限制。
 *
 * 作用域：com.android.systemui（系统界面）
 *
 * Hook NotificationSettingsManager.canShowFocus 和 canCustomFocus，
 * 当用户开关启用时直接返回 true，使所有应用均可发送焦点通知。
 *
 * 设置 key：pref_unlock_all_focus（布尔，默认 false）
 */
object UnlockAllFocusHook : BaseHook() {

    private const val TAG = "HyperIsland[UnlockAllFocusHook]"
    private const val SETTINGS_KEY = "pref_unlock_all_focus"
    private const val TARGET_CLASS = "miui.systemui.notification.NotificationSettingsManager"
    private const val SIGNATURE_CHECKER_CLASS =
        "miui.systemui.notification.focus.SignatureChecker"
    private const val PLUGIN_FACTORY_CLASS =
        "com.android.systemui.shared.plugins.PluginInstance\$PluginFactory"

    private val hookedClassLoaders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())
    )

    override fun getTag() = TAG

    private fun isEnabled(): Boolean = ConfigManager.getBoolean(SETTINGS_KEY, false)

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        if (!isEnabled()) {
            log(module, "disabled, skipping hook for ${param.packageName}")
            return
        }
        val hookedDirectly = hookFocusWhitelist(module, param.defaultClassLoader)
        if (!hookedDirectly) {
            hookPluginClassLoader(module, param.defaultClassLoader)
        }
    }

    private fun hookPluginClassLoader(module: XposedModule, classLoader: ClassLoader) {
        try {
            val factoryClass = classLoader.loadClass(PLUGIN_FACTORY_CLASS)
            val methods = factoryClass.declaredMethods.filter { it.name == "createPluginContext" }
            if (methods.isEmpty()) {
                logError(module, "createPluginContext not found in $PLUGIN_FACTORY_CLASS")
                return
            }

            methods.forEach { method ->
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    val pluginClassLoader = (result as? Context)?.classLoader
                    if (pluginClassLoader != null) {
                        hookFocusWhitelist(module, pluginClassLoader)
                    }
                    result
                }
            }
            log(module, "waiting for focus notification plugin class loader")
        } catch (e: Throwable) {
            logError(module, "failed to hook plugin class loader — ${e.message}")
        }
    }

    private fun hookFocusWhitelist(module: XposedModule, classLoader: ClassLoader): Boolean {
        if (hookedClassLoaders.contains(classLoader)) return true

        var hooked = false
        try {
            val clazz = classLoader.loadClass(TARGET_CLASS)
            clazz.declaredMethods
                .filter {
                    (it.name == "canShowFocus" || it.name == "canCustomFocus") &&
                        it.returnType == Boolean::class.javaPrimitiveType
                }
                .forEach { method ->
                    module.hook(method).intercept { true }
                    log(module, "hooked ${method.name}(${method.parameterTypes.joinToString { it.simpleName }})")
                    hooked = true
                }
        } catch (_: ClassNotFoundException) {
            // On newer HyperOS builds this class is loaded from the focus notification plugin.
        } catch (e: Throwable) {
            logError(module, "failed to hook focus whitelist — ${e.message}")
        }

        try {
            val clazz = classLoader.loadClass(SIGNATURE_CHECKER_CLASS)
            clazz.declaredMethods
                .filter {
                    it.name == "checkSignatures" &&
                        it.returnType == Boolean::class.javaPrimitiveType
                }
                .forEach { method ->
                    module.hook(method).intercept { true }
                    log(module, "hooked SignatureChecker.${method.name}")
                    hooked = true
                }
        } catch (_: ClassNotFoundException) {
            // SignatureChecker is only present on newer focus notification plugins.
        } catch (e: Throwable) {
            logError(module, "failed to hook focus signature checker — ${e.message}")
        }

        if (hooked) {
            hookedClassLoaders.add(classLoader)
        }
        return hooked
    }
}
