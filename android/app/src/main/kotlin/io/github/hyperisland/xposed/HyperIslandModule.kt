package io.github.hyperisland.xposed

import android.content.Context
import io.github.hyperisland.xposed.hook.FocusNotifStatusBarIconHook
import io.github.hyperisland.xposed.hook.MarqueeHook
import io.github.hyperisland.xposed.hook.UnlockAllFocusHook
import io.github.hyperisland.xposed.hook.UnlockFocusAuthHook
import io.github.libxposed.api.XposedInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModule

/**
 * 模块主入口，继承 XposedModule。
 * 框架在各目标进程加载时回调 [onPackageLoaded]，由此分发到各子 Hook。
 */
class HyperIslandModule(base: Context, param: ModuleLoadedParam) : XposedModule(base, param) {

    override fun onPackageLoaded(param: PackageLoadedParam) {
        when (param.packageName) {
            "io.github.hyperisland" ->
                SelfHook.init(this, param)

            "com.android.systemui" -> {
                IslandDispatcherHook.init(this, param)
                GenericProgressHook.init(this, param)
                MarqueeHook.init(this, param)
                UnlockAllFocusHook.init(this, param)
                FocusNotifStatusBarIconHook.init(this, param)
            }

            "com.android.providers.downloads",
            "com.xiaomi.android.app.downloadmanager" ->
                DownloadHook.init(this, param)

            "com.xiaomi.xmsf" ->
                UnlockFocusAuthHook.init(this, param)
        }
    }
}
