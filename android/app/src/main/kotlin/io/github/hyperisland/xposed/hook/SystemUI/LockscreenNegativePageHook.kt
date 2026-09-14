package io.github.hyperisland.xposed.hook.SystemUI

import io.github.hyperisland.xposed.hook.BaseHook
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/** Composition root. Providers do not install or depend on the SystemUI animation engine. */
object LockscreenNegativePageHook : BaseHook() {
    override fun getTag() = "HyperIsland[LockscreenNegativePage]"

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        when (param.packageName) {
            "com.android.systemui" -> {
                if (LockscreenWidgetPageHook.isWidgetMode()) {
                    // Widget mode replaces the page content in-process and reuses the local
                    // translation path. The device-center hooks must not run: they hide the very
                    // container the widget page lives in.
                    LockscreenWidgetPageHook.install(module, param.defaultClassLoader)
                } else {
                    LockscreenActivityPageHook.install(
                        module,
                        param.defaultClassLoader,
                        LockscreenDeviceCenterHook.page,
                    )
                }
            }
            LockscreenDeviceCenterHook.page.packageName -> {
                if (!LockscreenWidgetPageHook.isWidgetMode()) {
                    LockscreenDeviceCenterHook.init(module, param)
                }
            }
        }
    }
}
