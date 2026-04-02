package io.github.hyperisland.xposed

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 在 SystemUI 进程中注册 [IslandDispatcher] 的轻量 Hook。
 *
 * 通过 hook [android.app.Application.onCreate] 在 SystemUI 启动早期获取
 * ApplicationContext，完成 [IslandDispatcher] 的 BroadcastReceiver 注册。
 * 此后 HyperIsland 应用可通过广播以 SystemUI（system UID）身份发送超级岛通知，
 * 绕过 HyperOS 对前台应用岛通知的抑制。
 */
class IslandDispatcherHook : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.android.systemui") return

        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Application",
                lpparam.classLoader,
                "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val app = param.thisObject as? android.app.Application ?: return
                        IslandDispatcher.register(app)
                        // 在 SystemUI 进程早期初始化 ConfigManager，后续 Hook 无需等待 app 后台
                        ConfigManager.init(app)
                    }
                }
            )
            XposedBridge.log("HyperIsland[DispatcherHook]: hooked Application.onCreate in SystemUI")
        } catch (e: Throwable) {
            XposedBridge.log("HyperIsland[DispatcherHook]: hook failed: ${e.message}")
        }
    }
}
