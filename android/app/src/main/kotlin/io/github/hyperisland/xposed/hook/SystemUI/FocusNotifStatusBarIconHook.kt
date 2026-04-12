package io.github.hyperisland.xposed.hook

import android.app.Notification
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModule
import kotlin.jvm.JvmStatic

/**
 * 定向保留 HyperIsland 代理焦点通知的状态栏左上角小图标。
 *
 * 作用域：com.android.systemui（系统界面）
 */
object FocusNotifStatusBarIconHook : BaseHook() {

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

    override fun getTag() = TAG

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

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        if (hooked) return
        hooked = true
        val classLoader = param.defaultClassLoader
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
                    setFieldValue(model, "isFocusNotification", false)
                } catch (e: Throwable) {
                    logError(module, "failed to override isFocusNotification — ${e.message}")
                }
                result
            }
            log(module, "hooked ActiveNotificationsStoreBuilder.toModel(NotificationEntry)")
        } catch (e: Throwable) {
            logError(module, "ActiveNotificationsStoreBuilder.toModel hook failed — ${e.message}")
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
                    log(module, "icon area restored")
                }
                result
            }
            log(module, "hooked MiuiCollapsedStatusBarFragment.updateStatusBarVisibilities(boolean)")
        } catch (e: Throwable) {
            logError(module, "updateStatusBarVisibilities hook failed — ${e.message}")
        }
    }

    private fun forceShowNotificationIconsModel(module: XposedModule, fragment: Any?) {
        if (fragment == null) return
        try {
            val oldModel = getObjectFieldOrNull(fragment, "mLastModifiedVisibility") ?: return
            if (getObjectFieldOrNull(oldModel, "showNotificationIcons") is Boolean) {
                setFieldValue(oldModel, "showNotificationIcons", true)
                setFieldValue(fragment, "mLastModifiedVisibility", oldModel)
                return
            }

            val modelClass = fragment.javaClass.classLoader!!.loadClass(VISIBILITY_MODEL_CLASS)
            val newModel = newInstance(
                modelClass,
                getBooleanFieldValue(oldModel, "showClock"),
                true,
                getBooleanFieldValue(oldModel, "showPrimaryOngoingActivityChip"),
                getBooleanFieldValue(oldModel, "showSecondaryOngoingActivityChip"),
                getBooleanFieldValue(oldModel, "showSystemInfo"),
                getBooleanFieldValue(oldModel, "showNotifPromptView")
            ) ?: return
            setFieldValue(fragment, "mLastModifiedVisibility", newModel)
        } catch (e: Throwable) {
            logError(module, "forceShowNotificationIconsModel failed — ${e.message}")
        }
    }

    private fun refreshNotificationIconArea(module: XposedModule, fragment: Any?) {
        if (fragment == null) return
        try {
            callMethod(fragment, "updateNotificationIconAreaAndOngoingActivityChip", false)
        } catch (e: Throwable) {
            logError(module, "refreshNotificationIconArea failed — ${e.message}")
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
        var c: Class<*>? = instance.javaClass
        while (c != null) {
            try {
                val f = c.getDeclaredField(fieldName)
                f.isAccessible = true
                return f.get(instance)
            } catch (_: NoSuchFieldException) { c = c.superclass }
        }
        return null
    }

    private fun setFieldValue(instance: Any, fieldName: String, value: Any?) {
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

    private fun getBooleanFieldValue(instance: Any, fieldName: String): Boolean =
        getObjectFieldOrNull(instance, fieldName) as? Boolean ?: false

    private fun callMethodOrNull(instance: Any, methodName: String): Any? {
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

    private fun callMethod(instance: Any, methodName: String, vararg args: Any?): Any? {
        var c: Class<*>? = instance.javaClass
        while (c != null) {
            for (m in c.declaredMethods) {
                if (m.name == methodName && m.parameterCount == args.size) {
                    m.isAccessible = true
                    try { return m.invoke(instance, *args) } catch (_: Throwable) {}
                }
            }
            c = c.superclass
        }
        return null
    }

    private fun newInstance(clazz: Class<*>, vararg args: Any?): Any? {
        for (ctor in clazz.declaredConstructors) {
            if (ctor.parameterCount == args.size) {
                ctor.isAccessible = true
                try { return ctor.newInstance(*args) } catch (_: Throwable) {}
            }
        }
        return null
    }
}
