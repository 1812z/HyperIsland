package io.github.hyperisland.xposed.hook

import io.github.hyperisland.xposed.ConfigManager
import io.github.libxposed.api.XposedHelpers
import io.github.libxposed.api.XposedInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModule

/**
 * 移除焦点通知白名单签名验证。
 *
 * 作用域：com.xiaomi.xmsf（小米服务框架 / XMSF）
 *
 * Hook [AuthSession.b(error)]：当 error 不为 null（验证失败）时，
 * 将 errorCode 字段 `a` 强制置 0 并触发成功回调，跳过原方法。
 *
 * 设置 key：pref_unlock_focus_auth（布尔，默认 false）
 */
object UnlockFocusAuthHook {

    private const val TAG = "HyperIsland[UnlockFocusAuthHook]"
    private const val SETTINGS_KEY = "pref_unlock_focus_auth"
    private const val AUTH_SESSION_CLASS = "com.xiaomi.xms.auth.AuthSession"

    private fun isEnabled(): Boolean = ConfigManager.getBoolean(SETTINGS_KEY, false)

    fun init(module: XposedModule, param: PackageLoadedParam) {
        // 在 XMSF Application.onCreate 初始化 ConfigManager
        try {
            val appOnCreate = param.classLoader
                .loadClass("android.app.Application")
                .getDeclaredMethod("onCreate")
            module.hook(appOnCreate).intercept { chain ->
                val result = chain.proceed()
                ConfigManager.init()
                result
            }
        } catch (_: Throwable) {}

        hookAuthSession(module, param.classLoader)
    }

    private fun hookAuthSession(module: XposedModule, classLoader: ClassLoader) {
        try {
            val authSessionClass = classLoader.loadClass(AUTH_SESSION_CLASS)

            val targetMethod = authSessionClass.declaredMethods
                .filter { it.name == "b" && it.parameterCount == 1 }
                .firstOrNull()

            if (targetMethod == null) {
                module.log("$TAG: method 'b(error)' not found in $AUTH_SESSION_CLASS")
                return
            }

            module.hook(targetMethod).intercept { chain ->
                val error = chain.args[0]
                if (error == null || !isEnabled()) return@intercept chain.proceed()

                try {
                    val originalCode = XposedHelpers.getIntField(error, "a")
                    module.log("$TAG: auth error intercepted, original errorCode=$originalCode, forcing to 0")
                    XposedHelpers.setIntField(error, "a", 0)
                    val successResult = XposedHelpers.callMethod(chain.thisObject, "h")
                    module.log("$TAG: auth bypassed successfully")
                    successResult  // skip original
                } catch (e: Throwable) {
                    module.log("$TAG: bypass failed — ${e.message}")
                    chain.proceed()
                }
            }

            module.log("$TAG: hooked AuthSession.b(error)")
        } catch (e: Throwable) {
            module.log("$TAG: failed to hook $AUTH_SESSION_CLASS — ${e.message}")
        }
    }
}
