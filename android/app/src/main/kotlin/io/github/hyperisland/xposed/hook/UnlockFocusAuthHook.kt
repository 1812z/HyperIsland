package io.github.hyperisland.xposed.hook

import android.app.Application
import android.content.Context
import android.net.Uri
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 移除焦点通知白名单签名验证。
 *
 * 作用域：com.xiaomi.xmsf（小米服务框架 / XMSF）
 *
 * 原理：
 *   XMSF 在向设备（手表/手环）推送焦点通知之前，会通过 [com.xiaomi.xms.auth.AuthSession]
 *   验证发起方应用的签名是否在白名单内。方法 `b(error)` 接收验证结果：
 *   - 若 error 不为 null，说明验证失败（签名不在白名单），正常流程会中断推送。
 *
 *   本 Hook 在 `b` 方法执行前拦截，当发现 error 不为 null 时：
 *   1. 将 error 对象的错误码字段 `a` 强制置为 0（成功）。
 *   2. 主动调用实例的 `h()` 方法触发成功回调，并将原方法返回值设为该结果，
 *      跳过原方法的失败处理逻辑。
 *
 * 设置 key：pref_unlock_focus_auth（布尔，默认 false）
 *
 * 参考：HyperCeiler — UnlockFoucsAuth.kt
 */
class UnlockFocusAuthHook : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "HyperIsland[UnlockFocusAuthHook]"
        private const val SETTINGS_KEY = "pref_unlock_focus_auth"
        private const val TARGET_PACKAGE = "com.xiaomi.xmsf"
        private const val AUTH_SESSION_CLASS = "com.xiaomi.xms.auth.AuthSession"
    }

    @Volatile private var appContext: Context? = null

    // ─── 开关读取 ─────────────────────────────────────────────────────────────

    /**
     * 通过 SettingsProvider 查询「移除焦点通知白名单签名验证」是否已启用。
     * 查询失败时默认返回 false（保守策略，不影响系统正常行为）。
     */
    private fun isEnabled(ctx: Context): Boolean {
        return try {
            val uri = Uri.parse(
                "content://io.github.hyperisland.settings/$SETTINGS_KEY"
            )
            ctx.contentResolver
                .query(uri, null, null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getInt(0) == 1 else false
                } ?: false
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: failed to read setting — ${e.message}")
            false
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 仅 Hook 小米服务框架进程
        if (lpparam.packageName != TARGET_PACKAGE) return

        // 通过 Application.onCreate 捕获进程 Context
        XposedHelpers.findAndHookMethod(
            "android.app.Application",
            lpparam.classLoader,
            "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    appContext = param.thisObject as? Application
                }
            }
        )

        hookAuthSession(lpparam.classLoader)
    }

    // ─── Hook 方法 ────────────────────────────────────────────────────────────

    /**
     * Hook [AuthSession.b(error)]：验证结果分发方法。
     *
     * 方法签名（混淆后）：fun b(error: Any?)
     *   - 参数 error 为 null  → 验证成功，原方法正常执行
     *   - 参数 error 不为 null → 验证失败，本 Hook 强制将其改写为成功
     *
     * 字段 `a`：error 对象内的 errorCode 整型字段，置 0 表示无错误。
     * 方法 `h`：AuthSession 实例上的成功回调触发方法。
     */
    private fun hookAuthSession(classLoader: ClassLoader) {
        try {
            val authSessionClass = XposedHelpers.findClass(AUTH_SESSION_CLASS, classLoader)

            // 找到参数数量为 1 的方法 `b`（即接收 error 对象的那个重载）
            val targetMethod = authSessionClass.declaredMethods
                .filter { it.name == "b" && it.parameterCount == 1 }
                .firstOrNull()

            if (targetMethod == null) {
                XposedBridge.log("$TAG: method 'b(error)' not found in $AUTH_SESSION_CLASS")
                return
            }

            XposedBridge.hookMethod(targetMethod, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val error = param.args[0] ?: return // error 为 null 说明验证已成功，无需干预

                    // 检查用户开关，未启用则不干预
                    val ctx = appContext ?: return
                    if (!isEnabled(ctx)) return

                    try {
                        // 读取原始错误码，仅用于日志
                        val originalCode = XposedHelpers.getIntField(error, "a")
                        XposedBridge.log(
                            "$TAG: auth error intercepted, original errorCode=$originalCode, forcing to 0"
                        )

                        // 将错误码字段置为 0，伪装成验证通过
                        XposedHelpers.setIntField(error, "a", 0)

                        // 主动调用成功回调 h()，并把返回值设为 hook 结果，跳过原方法
                        val successResult = XposedHelpers.callMethod(param.thisObject, "h")
                        param.result = successResult

                        XposedBridge.log("$TAG: auth bypassed successfully")
                    } catch (e: Throwable) {
                        // 字段/方法名混淆可能变化，记录日志但不 crash
                        XposedBridge.log("$TAG: bypass failed — ${e.message}")
                    }
                }
            })

            XposedBridge.log("$TAG: hooked AuthSession.b(error)")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: failed to hook $AUTH_SESSION_CLASS — ${e.message}")
        }
    }
}
