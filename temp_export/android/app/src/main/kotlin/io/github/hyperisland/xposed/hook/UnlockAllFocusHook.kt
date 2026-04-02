package io.github.hyperisland.xposed.hook

import android.content.Context
import de.robv.android.xposed.IXposedHookLoadPackage
import io.github.hyperisland.xposed.SettingsBridge
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 移除焦点通知白名单限制。
 *
 * 作用域：com.android.systemui（系统界面）
 *
 * 原理：
 *   SystemUI 内的 [miui.systemui.notification.NotificationSettingsManager] 提供两个方法
 *   控制哪些应用可以发送焦点通知：
 *     - canShowFocus(Context, String)  — 检查包名是否在「展示」白名单内
 *     - canCustomFocus(String)         — 检查包名是否在「自定义内容」白名单内
 *
 *   本 Hook 在这两个方法执行前检查用户开关，若已启用则直接将返回值替换为 true，
 *   从而使所有应用均可发送焦点通知，无需在系统白名单中显式声明。
 *
 *   注意：
 *     - canShowFocus 的真实签名为 canShowFocus(Context, String)（两个参数），
 *       通过 JADX 反编译 SystemUI APK 确认。
 *     - 设置查询必须在 hook 回调内进行，handleLoadPackage 阶段无真实 Context。
 *
 * 设置 key：pref_unlock_all_focus（布尔，默认 false）
 *
 * 参考：HyperCeiler — FocusNotifLyric.initLoader()
 */
class UnlockAllFocusHook : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "HyperIsland[UnlockAllFocusHook]"
        private const val SETTINGS_KEY = "pref_unlock_all_focus"
        private const val TARGET_CLASS =
            "miui.systemui.notification.NotificationSettingsManager"
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 仅 Hook SystemUI 进程
        if (lpparam.packageName != "com.android.systemui") return

        // handleLoadPackage 阶段尚无真实 Context，直接注册 hook，
        // 在回调内部再读取用户开关。
        hookCanShowFocus(lpparam.classLoader)
        hookCanCustomFocus(lpparam.classLoader)
    }

    // ─── 开关读取 ─────────────────────────────────────────────────────────────

    /**
     * 通过 ConfigManager 查询「移除焦点通知白名单」是否已启用。
     * 直接读取 SharedPreferences XML 文件，无需 HyperIsland 模块后台运行。
     * 查询失败时默认返回 false（保守策略，不影响系统正常行为）。
     */
    private fun isEnabled(context: Context?): Boolean {
        SettingsBridge.init(context)
        return SettingsBridge.getBoolean(SETTINGS_KEY, false)
    }

    // ─── Hook 方法 ────────────────────────────────────────────────────────────

    /**
     * Hook [NotificationSettingsManager.canShowFocus]，按开关决定是否强制返回 true。
     *
     * 真实签名（由 JADX 确认）：
     *   public boolean canShowFocus(Context context, String packageName)
     *
     * 第一个参数 context 直接用于读取开关，无需再从 thisObject 取。
     */
    private fun hookCanShowFocus(classLoader: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                TARGET_CLASS,
                classLoader,
                "canShowFocus",
                Context::class.java,   // 参数1：context
                String::class.java,    // 参数2：packageName
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (isEnabled(param.args[0] as? Context)) {
                            param.result = true
                        }
                    }
                }
            )
            XposedBridge.log("$TAG: hooked canShowFocus(Context, String)")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: failed to hook canShowFocus — ${e.message}")
        }
    }

    /**
     * Hook [NotificationSettingsManager.canCustomFocus]，按开关决定是否强制返回 true。
     *
     * 真实签名（由 JADX 确认）：
     *   public boolean canCustomFocus(String packageName)
     *
     * 此方法无 Context 参数，通过 thisObject 持有的 mContext 字段获取 Context。
     * 部分 HyperOS 版本可能不存在此方法，找不到时仅记录日志，不影响 canShowFocus 的 Hook。
     */
    private fun hookCanCustomFocus(classLoader: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                TARGET_CLASS,
                classLoader,
                "canCustomFocus",
                String::class.java,   // 参数：packageName
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val ctx = XposedHelpers.getObjectField(param.thisObject, "mContext") as? Context
                        if (isEnabled(ctx)) {
                            param.result = true
                        }
                    }
                }
            )
            XposedBridge.log("$TAG: hooked canCustomFocus(String)")
        } catch (e: Throwable) {
            // 部分系统版本无此方法，属预期情况
            XposedBridge.log("$TAG: canCustomFocus not found (may be expected) — ${e.message}")
        }
    }
}
