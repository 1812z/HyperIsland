package io.github.hyperisland.xposed.hook

import android.app.Notification
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import io.github.libxposed.api.XposedHelpers
import io.github.libxposed.api.XposedInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModule
import kotlin.jvm.JvmStatic

/**
 * 定向保留 HyperIsland 代理焦点通知的状态栏左上角小图标。
 *
 * 作用域：com.android.systemui（系统界面）
 */
object FocusNotifStatusBarIconHook {

    private const val TAG = "HyperIsland[FocusStatusBarIcon]"
    private const val TARGET_ENTRY_CLASS =
        "com.android.systemui.statusbar.notification.collection.NotificationEntry"
    private const val TARGET_STORE_BUILDER_CLASS =
        "com.android.systemui.statusbar.notification.domain.interactor.ActiveNotificationsStoreBuilder"
    private const val TARGET_FRAGMENT_CLASS =
        "com.android.systemui.statusbar.phone.MiuiCollapsedStatusBarFragment"
    private const val VISIBILITY_MODEL_CLASS =
        "com.android.systemui.statusbar.phone.fragment.StatusBarVisibilityModel"

    @Volatile private var cachedDirectProxyActiveUntilElapsed = 0L
    @Volatile private var hooked = false

    @JvmStatic
    internal fun markDirectProxyPosted(timeoutSecs: Int) {
        val safeTimeoutSecs = timeoutSecs.coerceAtLeast(3)
        cachedDirectProxyActiveUntilElapsed =
            SystemClock.elapsedRealtime() + (safeTimeoutSecs * 1000L) + 3000L
    }

    @JvmStatic
    internal fun clearDirectProxyPosted() {
        cachedDirectProxyActiveUntilElapsed = 0L
    }

    private fun isDirectProxyActive(): Boolean =
        cachedDirectProxyActiveUntilElapsed > SystemClock.elapsedRealtime()

    fun init(module: XposedModule, param: PackageLoadedParam) {
        if (hooked) return
        hooked = true
        val classLoader = param.classLoader
        hookActiveNotificationModel(module, classLoader)
        hookUpdateStatusBarVisibilities(module, classLoader)
    }

    private fun hookActiveNotificationModel(module: XposedModule, classLoader: ClassLoader) {
        try {
            val entryClass = classLoader.loadClass(TARGET_ENTRY_CLASS)
            val builderClass = classLoader.loadClass(TARGET_STORE_BUILDER_CLASS)
            val toModelMethod = builderClass.getDeclaredMethod("toModel", entryClass)
            module.hook(toModelMethod).intercept { chain ->
                val result = chain.proceed()
                val entry = chain.args[0] ?: return@intercept result
                val model = result ?: return@intercept result
                val sbn = getObjectFieldOrNull(entry, "mSbn") ?: return@intercept result
                val notification = resolveNotificationFromSbnLike(sbn) ?: return@intercept result
                if (!isHyperIslandFocusProxy(notification.extras)) return@intercept result

                try {
                    XposedHelpers.setBooleanField(model, "isFocusNotification", false)
                } catch (e: Throwable) {
                    module.log("$TAG: failed to override isFocusNotification — ${e.message}")
                }
                result
            }
            module.log("$TAG: hooked ActiveNotificationsStoreBuilder.toModel(NotificationEntry)")
        } catch (e: Throwable) {
            module.log("$TAG: ActiveNotificationsStoreBuilder.toModel hook failed — ${e.message}")
        }
    }

    private fun hookUpdateStatusBarVisibilities(module: XposedModule, classLoader: ClassLoader) {
        try {
            val fragmentClass = classLoader.loadClass(TARGET_FRAGMENT_CLASS)
            val method = fragmentClass.getDeclaredMethod("updateStatusBarVisibilities", Boolean::class.javaPrimitiveType!!)
            module.hook(method).intercept { chain ->
                val result = chain.proceed()
                val fragment = chain.thisObject
                if (isDirectProxyActive()) {
                    forceShowNotificationIconsModel(module, fragment)
                    restoreNotificationIconArea(fragment)
                    refreshNotificationIconArea(module, fragment)
                    module.log("$TAG: icon area restored")
                }
                result
            }
            module.log("$TAG: hooked MiuiCollapsedStatusBarFragment.updateStatusBarVisibilities(boolean)")
        } catch (e: Throwable) {
            module.log("$TAG: updateStatusBarVisibilities hook failed — ${e.message}")
        }
    }

    private fun forceShowNotificationIconsModel(module: XposedModule, fragment: Any?) {
        if (fragment == null) return
        try {
            val oldModel = getObjectFieldOrNull(fragment, "mLastModifiedVisibility") ?: return
            val modelClass = fragment.javaClass.classLoader!!.loadClass(VISIBILITY_MODEL_CLASS)
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
        } catch (e: Throwable) {
            module.log("$TAG: forceShowNotificationIconsModel failed — ${e.message}")
        }
    }

    private fun refreshNotificationIconArea(module: XposedModule, fragment: Any?) {
        if (fragment == null) return
        try {
            XposedHelpers.callMethod(fragment, "updateNotificationIconAreaAndOngoingActivityChip", false)
        } catch (e: Throwable) {
            module.log("$TAG: refreshNotificationIconArea failed — ${e.message}")
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

    private fun resolveNotificationFromSbnLike(sbn: Any?): Notification? {
        if (sbn == null) return null
        (callMethodOrNull(sbn, "getNotification") as? Notification)?.let { return it }
        (getObjectFieldOrNull(sbn, "notification") as? Notification)?.let { return it }
        (getObjectFieldOrNull(sbn, "mNotification") as? Notification)?.let { return it }
        return null
    }

    private fun isHyperIslandFocusProxy(extras: Bundle?): Boolean {
        if (extras == null) return false
        return extras.getBoolean("hyperisland_preserve_status_bar_small_icon", false)
    }

    private fun getObjectFieldOrNull(instance: Any, fieldName: String): Any? {
        return try {
            XposedHelpers.getObjectField(instance, fieldName)
        } catch (_: Throwable) { null }
    }

    private fun callMethodOrNull(instance: Any, methodName: String): Any? {
        return try {
            XposedHelpers.callMethod(instance, methodName)
        } catch (_: Throwable) { null }
    }
}
