package io.github.hyperisland.xposed

import io.github.libxposed.api.XposedInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModule

/**
 * Hook 模块自身进程：将 MainActivity.isModuleActive() 替换为返回 true，
 * 使 UI 能正确检测到模块已激活。
 */
object SelfHook {

    fun init(module: XposedModule, param: PackageLoadedParam) {
        try {
            val method = param.classLoader
                .loadClass("io.github.hyperisland.MainActivity")
                .getDeclaredMethod("isModuleActive")
            module.hook(method).intercept { _ ->
                true
            }
            module.log("HyperIsland: hooked MainActivity.isModuleActive()")
        } catch (e: Throwable) {
            module.log("HyperIsland: failed to hook isModuleActive: ${e.message}")
        }
    }
}
