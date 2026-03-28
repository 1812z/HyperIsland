package io.github.hyperisland.xposed.hook

import android.content.Context
import io.github.hyperisland.xposed.ConfigManager
import io.github.libxposed.api.XposedInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModule

/**
 * 移除焦点通知白名单限制。
 *
 * 作用域：com.android.systemui（系统界面）
 *
 * Hook [NotificationSettingsManager.canShowFocus] 和 [canCustomFocus]，
 * 当用户开关启用时直接返回 true，使所有应用均可发送焦点通知。
 *
 * 设置 key：pref_unlock_all_focus（布尔，默认 false）
 */
object UnlockAllFocusHook {

    private const val TAG = "HyperIsland[UnlockAllFocusHook]"
    private const val SETTINGS_KEY = "pref_unlock_all_focus"
    private const val TARGET_CLASS = "miui.systemui.notification.NotificationSettingsManager"

    private fun isEnabled(): Boolean = ConfigManager.getBoolean(SETTINGS_KEY, false)

    fun init(module: XposedModule, param: PackageLoadedParam) {
        hookCanShowFocus(module, param.classLoader)
        hookCanCustomFocus(module, param.classLoader)
    }

    private fun hookCanShowFocus(module: XposedModule, classLoader: ClassLoader) {
        try {
            val clazz = classLoader.loadClass(TARGET_CLASS)
            val method = clazz.getDeclaredMethod("canShowFocus", Context::class.java, String::class.java)
            module.hook(method).intercept { chain ->
                if (isEnabled()) return@intercept true
                chain.proceed()
            }
            module.log("$TAG: hooked canShowFocus(Context, String)")
        } catch (e: Throwable) {
            module.log("$TAG: failed to hook canShowFocus — ${e.message}")
        }
    }

    private fun hookCanCustomFocus(module: XposedModule, classLoader: ClassLoader) {
        try {
            val clazz = classLoader.loadClass(TARGET_CLASS)
            val method = clazz.getDeclaredMethod("canCustomFocus", String::class.java)
            module.hook(method).intercept { chain ->
                if (isEnabled()) return@intercept true
                chain.proceed()
            }
            module.log("$TAG: hooked canCustomFocus(String)")
        } catch (e: Throwable) {
            module.log("$TAG: canCustomFocus not found (may be expected) — ${e.message}")
        }
    }
}
