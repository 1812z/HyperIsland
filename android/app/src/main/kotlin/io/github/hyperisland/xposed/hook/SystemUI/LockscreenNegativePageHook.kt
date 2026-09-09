package io.github.hyperisland.xposed.hook.SystemUI

import io.github.hyperisland.xposed.hook.BaseHook
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/** Composition root. Providers do not install or depend on the SystemUI animation engine. */
object LockscreenNegativePageHook : BaseHook() {
    override fun getTag() = "HyperIsland[LockscreenNegativePage]"

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        val page = LockscreenDeviceCenterHook.page
        when (param.packageName) {
            "com.android.systemui" ->
                LockscreenActivityPageHook.install(module, param.defaultClassLoader, page)
            page.packageName -> LockscreenDeviceCenterHook.init(module, param)
        }
    }
}
