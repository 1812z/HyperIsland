package io.github.hyperisland.xposed

import io.github.libxposed.api.XposedInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModule

/**
 * 在 SystemUI 进程中注册 [IslandDispatcher] 的轻量 Hook。
 *
 * 通过 hook [android.app.Application.onCreate] 在 SystemUI 启动早期获取
 * ApplicationContext，完成 [IslandDispatcher] 的 BroadcastReceiver 注册。
 */
object IslandDispatcherHook {

    fun init(module: XposedModule, param: PackageLoadedParam) {
        try {
            val method = param.classLoader
                .loadClass("android.app.Application")
                .getDeclaredMethod("onCreate")
            module.hook(method).intercept { chain ->
                val result = chain.proceed()
                val app = chain.thisObject as? android.app.Application
                if (app != null) {
                    IslandDispatcher.register(app)
                    ConfigManager.init()
                }
                result
            }
            module.log("HyperIsland[DispatcherHook]: hooked Application.onCreate in SystemUI")
        } catch (e: Throwable) {
            module.log("HyperIsland[DispatcherHook]: hook failed: ${e.message}")
        }
    }
}
