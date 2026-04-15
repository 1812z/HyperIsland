package io.github.hyperisland.xposed

import io.github.hyperisland.xposed.hook.BigIslandMinWidthHook
import io.github.hyperisland.xposed.hook.DownloadHook
import io.github.hyperisland.xposed.hook.FocusNotifStatusBarIconHook
import io.github.hyperisland.xposed.hook.GenericProgressHook
import io.github.hyperisland.xposed.hook.IslandDispatcherHook
import io.github.hyperisland.xposed.hook.IslandOuterGlowHook
import io.github.hyperisland.xposed.hook.MarqueeHook
import io.github.hyperisland.xposed.hook.ToastUiInterceptHook
import io.github.hyperisland.xposed.hook.UnlockAllFocusHook
import io.github.hyperisland.xposed.hook.UnlockFocusAuthHook
import io.github.hyperisland.xposed.log
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModule

/**
 * 模块主入口，继承 XposedModule。
 * 框架在各目标进程加载时回调 [onPackageLoaded]，由此分发到各子 Hook。
 */
class HyperIslandModule : XposedModule() {

    private var configManagerInitialized = false

    override fun onPackageLoaded(param: PackageLoadedParam) {
        initializeConfigManager()
        log("onPackageLoaded: pkg=${param.packageName}")
        
        when (param.packageName) {
            "com.android.systemui"-> {
                IslandDispatcherHook.init(this, param)
                GenericProgressHook.init(this, param)
                MarqueeHook.init(this, param)
                BigIslandMinWidthHook.init(this, param)
                UnlockAllFocusHook.init(this, param)
                FocusNotifStatusBarIconHook.init(this, param)
                IslandOuterGlowHook.init(this, param)
                ToastUiInterceptHook.init(this, param)
            }

            "com.android.providers.downloads",
            "com.xiaomi.android.app.downloadmanager" ->
                DownloadHook.init(this, param)

            "com.xiaomi.xmsf" ->
                UnlockFocusAuthHook.init(this, param)

        }
    }

    private fun initializeConfigManager() {
        if (!configManagerInitialized) {
            ConfigManager.init(this)
            configManagerInitialized = true
        }
    }
}
