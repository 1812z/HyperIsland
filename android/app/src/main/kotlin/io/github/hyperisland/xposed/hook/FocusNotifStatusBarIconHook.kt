package io.github.hyperisland.xposed.hook

import android.app.Notification
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import kotlin.jvm.JvmStatic

/**
 * 定向保留 HyperIsland 代理焦点通知的状态栏左上角小图标。
 *
 * 作用域：com.android.systemui（系统界面）
 *
 * 当前机型验证后，真正稳定生效的链路是：
 *   1. HyperIsland 发出代理焦点通知时记录一个短时活动窗口。
 *   2. ActiveNotificationsStoreBuilder.toModel(...) 阶段把代理通知的
 *      ActiveNotificationModel.isFocusNotification 改为 false，避免其在 icon pipeline 中被排除。
 *   3. MiuiCollapsedStatusBarFragment.updateStatusBarVisibilities(...) 刷新后，
 *      强制把 showNotificationIcons 设为 true，并立即刷新图标区域。
 *
 * 之所以不再保留更早期的 controller / bean 反射探测逻辑，是因为该机型上这些字段链路长期取不到
 * 当前焦点通知对象，属于无效复杂度。反射失败时仍一律放行，避免误伤系统原生焦点通知。
 */
class FocusNotifStatusBarIconHook : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "HyperIsland[FocusStatusBarIcon]"
        private const val TARGET_PACKAGE = "com.android.systemui"
        private const val TARGET_ENTRY_CLASS =
            "com.android.systemui.statusbar.notification.collection.NotificationEntry"
        private const val TARGET_STORE_BUILDER_CLASS =
            "com.android.systemui.statusbar.notification.domain.interactor.ActiveNotificationsStoreBuilder"
        private const val TARGET_FRAGMENT_CLASS =
            "com.android.systemui.statusbar.phone.MiuiCollapsedStatusBarFragment"
        private const val VISIBILITY_MODEL_CLASS =
            "com.android.systemui.statusbar.phone.fragment.StatusBarVisibilityModel"

        @Volatile private var cachedDirectProxyActiveUntilElapsed = 0L

        @JvmStatic
        internal fun markDirectProxyPosted(timeoutSecs: Int) {
            val safeTimeoutSecs = timeoutSecs.coerceAtLeast(3)
            cachedDirectProxyActiveUntilElapsed =
                SystemClock.elapsedRealtime() + (safeTimeoutSecs * 1000L) + 3000L
            XposedBridge.log(
                "$TAG: markDirectProxyPosted | timeoutSecs=$timeoutSecs | activeUntil=$cachedDirectProxyActiveUntilElapsed"
            )
        }

        @JvmStatic
        internal fun clearDirectProxyPosted() {
            cachedDirectProxyActiveUntilElapsed = 0L
            XposedBridge.log("$TAG: clearDirectProxyPosted")
        }

        private fun isDirectProxyActive(): Boolean {
            return cachedDirectProxyActiveUntilElapsed > SystemClock.elapsedRealtime()
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != TARGET_PACKAGE) return
        hookActiveNotificationModel(lpparam.classLoader)
        hookUpdateStatusBarVisibilities(lpparam.classLoader)
    }

    private fun hookActiveNotificationModel(classLoader: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                TARGET_STORE_BUILDER_CLASS,
                classLoader,
                "toModel",
                XposedHelpers.findClass(TARGET_ENTRY_CLASS, classLoader),
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val entry = param.args[0] ?: return
                        val model = param.result ?: return
                        val sbn = getObjectFieldOrNull(entry, "mSbn") ?: return
                        val notification = resolveNotificationFromSbnLike(sbn) ?: return
                        if (!isHyperIslandFocusProxy(notification.extras)) return

                        try {
                            XposedHelpers.setBooleanField(model, "isFocusNotification", false)
                            XposedBridge.log(
                                "$TAG: forced ActiveNotificationModel.isFocusNotification=false for HyperIsland proxy"
                            )
                        } catch (e: Throwable) {
                            XposedBridge.log(
                                "$TAG: failed to override ActiveNotificationModel.isFocusNotification — ${e.message}"
                            )
                        }
                    }
                }
            )
            XposedBridge.log("$TAG: hooked ActiveNotificationsStoreBuilder.toModel(NotificationEntry)")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: ActiveNotificationsStoreBuilder.toModel hook failed — ${e.message}")
        }
    }

    private fun hookUpdateStatusBarVisibilities(classLoader: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                TARGET_FRAGMENT_CLASS,
                classLoader,
                "updateStatusBarVisibilities",
                Boolean::class.javaPrimitiveType!!,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val fragment = param.thisObject
                        val keepIcons = isDirectProxyActive()

                        XposedBridge.log(
                            "$TAG: updateStatusBarVisibilities finished | keepIcons=$keepIcons"
                        )

                        if (!keepIcons) return

                        forceShowNotificationIconsModel(fragment)
                        restoreNotificationIconArea(fragment)
                        refreshNotificationIconArea(fragment)
                        XposedBridge.log(
                            "$TAG: re-show notification icon area after updateStatusBarVisibilities"
                        )
                    }
                }
            )
            XposedBridge.log("$TAG: hooked MiuiCollapsedStatusBarFragment.updateStatusBarVisibilities(boolean)")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: updateStatusBarVisibilities hook failed — ${e.message}")
        }
    }

    private fun forceShowNotificationIconsModel(fragment: Any?) {
        if (fragment == null) return
        try {
            val oldModel = getObjectFieldOrNull(fragment, "mLastModifiedVisibility") ?: return
            val modelClass = XposedHelpers.findClass(VISIBILITY_MODEL_CLASS, fragment.javaClass.classLoader)
            val newModel = XposedHelpers.newInstance(
                modelClass,
                XposedHelpers.getBooleanField(oldModel, "showClock"),
                true,
                XposedHelpers.getBooleanField(oldModel, "showPrimaryOngoingActivityChip"),
                XposedHelpers.getBooleanField(oldModel, "showSecondaryOngoingActivityChip"),
                XposedHelpers.getBooleanField(oldModel, "showSystemInfo"),
                XposedHelpers.getBooleanField(oldModel, "showNotifPromptView")
            )
            XposedHelpers.setObjectField(fragment, "mLastModifiedVisibility", newModel)
            XposedBridge.log("$TAG: forced StatusBarVisibilityModel.showNotificationIcons=true")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: forceShowNotificationIconsModel failed — ${e.message}")
        }
    }

    private fun refreshNotificationIconArea(fragment: Any?) {
        if (fragment == null) return
        try {
            XposedHelpers.callMethod(fragment, "updateNotificationIconAreaAndOngoingActivityChip", false)
            XposedBridge.log("$TAG: refreshed notification icon area")
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: refreshNotificationIconArea failed — ${e.message}")
        }
    }

    private fun restoreNotificationIconArea(fragment: Any?) {
        if (fragment == null) return
        setVisible(getObjectFieldOrNull(fragment, "mNotificationIconAreaInner") as? View)
        setVisible(getObjectFieldOrNull(fragment, "mNotificationIcons") as? View)
        setVisible(getObjectFieldOrNull(fragment, "mStatusBarIcons") as? View)
        setVisible(getObjectFieldOrNull(fragment, "mStatusContainer") as? View)
    }

    private fun setVisible(view: View?) {
        if (view == null) return
        view.visibility = View.VISIBLE
        view.alpha = 1f
        view.translationX = 0f
    }

    /**
     * 不同 HyperOS 版本里 sbn 具体类型可能是 StatusBarNotification、ExpandedNotification 或其包装对象，
     * 所以优先尝试 getNotification()，再兼容 notification / mNotification 字段逐级探测。
     * 若这一层仍失败，必须返回 null 并放行原逻辑，避免影响系统原生焦点通知。
     */
    private fun resolveNotificationFromSbnLike(sbn: Any?): Notification? {
        if (sbn == null) return null

        (callMethodOrNull(sbn, "getNotification") as? Notification)?.let { return it }
        (getObjectFieldOrNull(sbn, "notification") as? Notification)?.let { return it }
        (getObjectFieldOrNull(sbn, "mNotification") as? Notification)?.let { return it }

        return null
    }

    private fun isHyperIslandFocusProxy(extras: Bundle?): Boolean {
        if (extras == null) return false
        if (extras.getBoolean("hyperisland_focus_proxy", false)) return true
        if (extras.getBoolean("hyperisland_generic_processed", false)) return true
        if (extras.getBoolean("hyperisland_processed", false)) return true
        return false
    }

    /**
     * 字段名可能因 HyperOS 版本不同而变化，所以这里只尝试单个候选字段并把失败转为 null。
     * 上层会继续探测其他候选；反射失败时必须放行，避免误伤系统原生焦点通知。
     */
    private fun getObjectFieldOrNull(instance: Any, fieldName: String): Any? {
        return try {
            XposedHelpers.getObjectField(instance, fieldName)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * 方法名或可见性可能因 HyperOS 版本不同而变化，所以这里只做保守调用并把失败转为 null。
     * 上层会继续走其他探测路径；反射失败时必须放行，避免误伤系统原生焦点通知。
     */
    private fun callMethodOrNull(instance: Any, methodName: String): Any? {
        return try {
            XposedHelpers.callMethod(instance, methodName)
        } catch (_: Throwable) {
            null
        }
    }
}
