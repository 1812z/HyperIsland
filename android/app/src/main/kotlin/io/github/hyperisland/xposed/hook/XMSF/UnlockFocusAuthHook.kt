package io.github.hyperisland.xposed.hook

import io.github.hyperisland.xposed.ConfigManager
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
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
object UnlockFocusAuthHook : BaseHook() {

    private const val TAG = "HyperIsland[UnlockFocusAuthHook]"
    private const val SETTINGS_KEY = "pref_unlock_focus_auth"
    private const val AUTH_SESSION_CLASS = "com.xiaomi.xms.auth.AuthSession"

    override fun getTag() = TAG

    private fun isEnabled(): Boolean = ConfigManager.getBoolean(SETTINGS_KEY, false)

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        if (!isEnabled()) {
            log(module, "disabled, skipping hook for ${param.packageName}")
            return
        }
        hookAuthSession(module, param.defaultClassLoader)
    }

    private fun getIntField(instance: Any, fieldName: String): Int {
        var c: Class<*>? = instance.javaClass
        while (c != null) {
            try {
                val f = c.getDeclaredField(fieldName)
                f.isAccessible = true
                return (f.get(instance) as? Int) ?: 0
            } catch (_: NoSuchFieldException) { c = c.superclass }
        }
        return 0
    }

    private fun setField(instance: Any, fieldName: String, value: Any?) {
        var c: Class<*>? = instance.javaClass
        while (c != null) {
            try {
                val f = c.getDeclaredField(fieldName)
                f.isAccessible = true
                f.set(instance, value)
                return
            } catch (_: NoSuchFieldException) { c = c.superclass }
        }
    }

    private fun callMethod(instance: Any, methodName: String): Any? {
        var c: Class<*>? = instance.javaClass
        while (c != null) {
            try {
                val m = c.getDeclaredMethod(methodName)
                m.isAccessible = true
                return m.invoke(instance)
            } catch (_: NoSuchMethodException) { c = c.superclass }
        }
        return null
    }

    private fun hookAuthSession(module: XposedModule, classLoader: ClassLoader) {
        try {
            val authSessionClass = classLoader.loadClass(AUTH_SESSION_CLASS)

            val targetMethod = authSessionClass.declaredMethods
                .filter { it.name == "b" && it.parameterCount == 1 }
                .firstOrNull()

            if (targetMethod == null) {
                logError(module, "method 'b(error)' not found in $AUTH_SESSION_CLASS")
                return
            }

            module.hook(targetMethod).intercept { chain ->
                val error = chain.args[0]
                if (error == null) return@intercept chain.proceed()

                try {
                    val originalCode = getIntField(error, "a")
                    log(module, "auth error intercepted, original errorCode=$originalCode, forcing to 0")
                    setField(error, "a", 0)
                    val successResult = callMethod(chain.thisObject!!, "h")
                    log(module, "auth bypassed successfully")
                    successResult  // skip original
                } catch (e: Throwable) {
                    logError(module, "bypass failed — ${e.message}")
                    chain.proceed()
                }
            }

            log(module, "hooked AuthSession.b(error)")
        } catch (e: Throwable) {
            logError(module, "failed to hook $AUTH_SESSION_CLASS — ${e.message}")
        }
    }
}
