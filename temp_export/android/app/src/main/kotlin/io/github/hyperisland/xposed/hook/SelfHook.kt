package io.github.hyperisland.xposed

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Hook 模块自身进程：将 MainActivity.isModuleActive() 替换为返回 true，
 * 使 UI 能正确检测到模块已激活。
 */
class SelfHook : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "io.github.hyperisland") return
        try {
            val mainActivityClass = lpparam.classLoader
                .loadClass("io.github.hyperisland.MainActivity")
            XposedHelpers.findAndHookMethod(
                mainActivityClass,
                "isModuleActive",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = true
                    }
                }
            )
            XposedHelpers.findAndHookMethod(
                mainActivityClass,
                "getApiVersion",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = XposedBridge.getXposedVersion()
                    }
                }
            )
            XposedBridge.log("HyperIsland: hooked MainActivity.isModuleActive() and getApiVersion()")
        } catch (e: Throwable) {
            XposedBridge.log("HyperIsland: failed to hook isModuleActive: ${e.message}")
        }
    }
}
