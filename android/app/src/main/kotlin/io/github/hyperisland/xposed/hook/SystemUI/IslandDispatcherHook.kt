package io.github.hyperisland.xposed.hook

import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.islanddispatch.IslandDispatcher
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModule

/**
 * 在 SystemUI 进程中注册 [IslandDispatcher] 的轻量 Hook。
 *
 * 通过 hook [android.app.Application.onCreate] 在 SystemUI 启动早期获取
 * ApplicationContext，完成 [IslandDispatcher] 的 BroadcastReceiver 注册。
 */
object IslandDispatcherHook : BaseHook() {

    private const val TAG = "HyperIsland[DispatcherHook]"

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        try {
            val method = param.defaultClassLoader
                .loadClass("android.app.Application")
                .getDeclaredMethod("onCreate")
            module.hook(method).intercept { chain ->
                val result = chain.proceed()
                val app = chain.thisObject as? android.app.Application
                if (app != null) {
                    IslandDispatcher.register(app, module)
                    ConfigManager.init(module)
                }
                result
            }
            log(module, "hooked Application.onCreate in SystemUI")
        } catch (e: Throwable) {
            logError(module, "hook failed: ${e.message}")
        }
    }
}
