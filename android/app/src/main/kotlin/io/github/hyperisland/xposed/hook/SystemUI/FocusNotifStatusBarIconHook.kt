package io.github.hyperisland.xposed.hook

import io.github.hyperisland.xposed.diagnostics.profiledHook

import android.app.Notification
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import io.github.hyperisland.xposed.hook.SystemUI.animation.ExpandedVisibilitySuppression
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlin.jvm.JvmStatic

/**
 * 定向控制 HyperIsland 通知在状态栏左上角的小图标。
 *
 * - 强制显示：把代理焦点通知的 isFocusNotification 置 false，并强制图标区域可见；
 * - 强制不显示：把模型里的 statusBarIcon 置空，图标不进入状态栏图标集合（息屏 / 通知架走各自字段，不受影响）。
 *
 * 作用域：com.android.systemui（系统界面）
 */
object FocusNotifStatusBarIconHook : BaseHook() {

    private const val TAG = "HyperIsland[FocusStatusBarIcon]"
    private const val MARKER_PRESERVE_ICON = "hyperisland_preserve_status_bar_small_icon"
    private const val MARKER_HIDE_ICON = "hyperisland_hide_status_bar_small_icon"
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
    @Volatile private var expandedOcclusionSuppressed = false
    @Volatile private var statusBarFragmentRef = WeakReference<Any>(null)
    private val expandedOcclusionOwners = WeakHashMap<View, Boolean>()
    private data class SuppressedViewState(val visibility: Int, val alpha: Float)
    private val suppressedStatusBarViews = ExpandedVisibilitySuppression<View, SuppressedViewState>(
        capture = { SuppressedViewState(it.visibility, it.alpha) },
        hide = { view ->
            if (view.visibility == View.VISIBLE) {
                view.animate().cancel()
                view.visibility = View.INVISIBLE
            }
        },
        restore = { view, original ->
            view.animate().cancel()
            view.alpha = original.alpha
            view.visibility = original.visibility
        },
    )
    private data class StatusBarWatch(
        val preDraw: ViewTreeObserver.OnPreDrawListener,
        val attach: View.OnAttachStateChangeListener,
        var observer: WeakReference<ViewTreeObserver>? = null,
    )
    private val statusBarWatches = WeakHashMap<View, StatusBarWatch>()
    private val mainHandler = Handler(Looper.getMainLooper())
    /** 上次执行 icon 恢复操作的时间戳（elapsedRealtime），用于节流 */
    @Volatile private var lastRestoreElapsed = 0L
    private const val RESTORE_THROTTLE_MS = 200L

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
        lastRestoreElapsed = 0L
    }

    private fun isDirectProxyActive(): Boolean =
        cachedDirectProxyActiveUntilElapsed > SystemClock.elapsedRealtime()

    /** Hide the status container and notification icon subcontainers while a custom-gap island is expanded. */
    internal fun setExpandedOcclusionSuppressed(owner: View, suppressed: Boolean) {
        val apply: () -> Unit = {
            if (suppressed && (expandedOcclusionOwners.size < 128 || expandedOcclusionOwners.containsKey(owner))) {
                expandedOcclusionOwners[owner] = true
            }
            else expandedOcclusionOwners.remove(owner)
            expandedOcclusionSuppressed = expandedOcclusionOwners.isNotEmpty()
            applyExpandedOcclusionSuppression()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) apply() else mainHandler.post(apply)
    }

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        if (param.packageName != "com.android.systemui") return
        if (hooked) return
        hooked = true
        val classLoader = param.defaultClassLoader
        hookActiveNotificationModel(module, classLoader)
        hookStatusBarViewLifecycle(module, classLoader)
        hookUpdateStatusBarVisibilities(module, classLoader)
    }

    private fun hookStatusBarViewLifecycle(module: XposedModule, classLoader: ClassLoader) {
        // Newer SystemUI no longer has MiuiCollapsedStatusBarFragment. The phone View
        // still owns the clock, both notification icon parents, and the system icons.
        val viewClass = sequenceOf(
            "com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView",
            "com.android.systemui.statusbar.phone.PhoneStatusBarView",
        ).mapNotNull { runCatching { classLoader.loadClass(it) }.getOrNull() }.firstOrNull() ?: return
        listOf("onFinishInflate", "onAttachedToWindow", "onDetachedFromWindow").forEach { name ->
            val method = generateSequence(viewClass) { it.superclass }.mapNotNull { clazz ->
                runCatching { clazz.getDeclaredMethod(name) }.getOrNull()
            }.firstOrNull() ?: return@forEach
            try {
                module.profiledHook("FocusNotifStatusBarIconHook", method).intercept { chain ->
                    val result = chain.proceed()
                    runCatching {
                        val view = chain.thisObject as? View ?: return@runCatching
                        if (name == "onDetachedFromWindow") unwatchStatusBarView(view)
                        else watchStatusBarView(view)
                    }.onFailure { error ->
                        logFailureOnce(module, "status-bar-$name") { "$name: ${error.message}" }
                    }
                    result
                }
                log(module) { "hooked ${method.declaringClass.name}.$name for expanded occlusion" }
            } catch (error: Throwable) {
                logError(module, "status bar lifecycle hook failed ($name): ${error.message}")
            }
        }
    }

    private fun watchStatusBarView(view: View) {
        if (!statusBarWatches.containsKey(view)) {
            if (statusBarWatches.size >= 32) return
            val ref = WeakReference(view)
            val preDraw = ViewTreeObserver.OnPreDrawListener {
                if (ref.get() != null) applyExpandedOcclusionSuppression()
                true
            }
            val attach = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = watchStatusBarView(v)
                override fun onViewDetachedFromWindow(v: View) = unwatchStatusBarView(v)
            }
            statusBarWatches[view] = StatusBarWatch(preDraw, attach)
            view.addOnAttachStateChangeListener(attach)
        }
        applyExpandedOcclusionSuppression()
    }

    private fun unwatchStatusBarView(view: View) {
        val watch = statusBarWatches.remove(view) ?: return
        watch.observer?.get()?.takeIf { it.isAlive }?.removeOnPreDrawListener(watch.preDraw)
        view.removeOnAttachStateChangeListener(watch.attach)
        suppressedStatusBarViews.release(view)
    }

    private fun hookActiveNotificationModel(module: XposedModule, classLoader: ClassLoader) {
        try {
            val entryClass = classLoader.loadClass(TARGET_ENTRY_CLASS)
            val builderClass = classLoader.loadClass(TARGET_STORE_BUILDER_CLASS)
            val toModelMethod = builderClass.getDeclaredMethod("toModel", entryClass)
            module.profiledHook("FocusNotifStatusBarIconHook", toModelMethod).intercept { chain ->
                val result = chain.proceed()
                val entry = chain.args[0] ?: return@intercept result
                val model = result ?: return@intercept result
                val sbn = getObjectFieldOrNull(entry, "mSbn") ?: return@intercept result
                val notification = resolveNotificationFromSbnLike(sbn) ?: return@intercept result
                val extras = notification.extras

                // 强制不显示：置空 statusBarIcon，toIconInfo 返回 null，该通知不进入状态栏图标数据。
                if (extras?.getBoolean(MARKER_HIDE_ICON, false) == true) {
                    try {
                        setFieldValue(model, "statusBarIcon", null)
                    } catch (e: Throwable) {
                        logError(module, "failed to clear statusBarIcon — ${e.message}")
                    }
                    return@intercept result
                }

                if (!isHyperIslandFocusProxy(extras)) return@intercept result

                try {
                    setFieldValue(model, "isFocusNotification", false)
                } catch (e: Throwable) {
                    logError(module, "failed to override isFocusNotification — ${e.message}")
                }
                result
            }
            log(module) { "hooked ActiveNotificationsStoreBuilder.toModel(NotificationEntry)" }
        } catch (e: Throwable) {
            logError(module, "ActiveNotificationsStoreBuilder.toModel hook failed — ${e.message}")
        }
    }

    private fun hookUpdateStatusBarVisibilities(module: XposedModule, classLoader: ClassLoader) {
        val fragmentClass = runCatching {
            classLoader.loadClass(TARGET_FRAGMENT_CLASS)
        }.getOrNull()
        if (fragmentClass == null) {
            log(module) { "updateStatusBarVisibilities hook skipped — $TARGET_FRAGMENT_CLASS not found" }
            return
        }

        val method = runCatching {
            fragmentClass.getDeclaredMethod(
                "updateStatusBarVisibilities",
                Boolean::class.javaPrimitiveType!!
            )
        }.getOrNull()
        if (method == null) {
            log(module) { "updateStatusBarVisibilities hook skipped — method not found in ${fragmentClass.name}" }
            return
        }

        try {
            module.profiledHook("FocusNotifStatusBarIconHook", method).intercept { chain ->
                val fragment = chain.thisObject
                if (fragment != null) {
                    statusBarFragmentRef = WeakReference(fragment)
                }
                val result = chain.proceed()
                if (isDirectProxyActive()) {
                    // 节流：200ms 内只执行一次，避免高频状态栏刷新时重复做反射+UI操作
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastRestoreElapsed >= RESTORE_THROTTLE_MS) {
                        lastRestoreElapsed = now
                        forceShowNotificationIconsModel(module, fragment)
                        restoreNotificationIconArea(fragment)
                        refreshNotificationIconArea(module, fragment)
                        log(module) { "icon area restored" }
                    }
                }
                if (fragment != null) applyExpandedOcclusionSuppression()
                result
            }
            log(module) { "hooked ${method.declaringClass.name}.updateStatusBarVisibilities(boolean)" }
        } catch (e: Throwable) {
            log(module) { "updateStatusBarVisibilities hook skipped — ${e.message}" }
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

    private fun resolveStatusBarContentViews(fragment: Any): List<View> {
        val root = (callMethodOrNull(fragment, "getView") as? View)
            ?: (getObjectFieldOrNull(fragment, "mView") as? View)
        if (root != null) return listOf(root)
        val views = mutableListOf<View>()
        listOf(
            "mStatusContainer",
            "mNotificationIconAreaInner",
            "mNotificationIcons",
            "mStatusBarIcons",
            "mClockView",
        ).forEach { field ->
            val view = getObjectFieldOrNull(fragment, field) as? View ?: return@forEach
            if (views.none { it === view }) views.add(view)
        }
        return views
    }

    private fun applyExpandedOcclusionSuppression() {
        expandedOcclusionSuppressed = expandedOcclusionOwners.isNotEmpty()
        val displays = expandedOcclusionOwners.keys.mapNotNull { it.display?.displayId }.toSet()
        val roots = statusBarWatches.keys.toList().filter { it.isAttachedToWindow }
        val targets = if (roots.isNotEmpty()) roots else {
            statusBarFragmentRef.get()?.let(::resolveStatusBarContentViews).orEmpty()
                .filter { it.isAttachedToWindow }
        }
        // Keep the first snapshot for the entire expanded lifetime. A normal status bar
        // refresh may restore children, but must never release/re-capture our root state.
        suppressedStatusBarViews.update(
            targets.filter { it.display?.displayId in displays },
            active = expandedOcclusionSuppressed,
        )
        statusBarWatches.entries.toList().forEach { (view, watch) ->
            val observer = view.viewTreeObserver
            if (expandedOcclusionSuppressed && view.isAttachedToWindow && observer.isAlive) {
                if (watch.observer?.get() !== observer) {
                    watch.observer?.get()?.takeIf { it.isAlive }?.removeOnPreDrawListener(watch.preDraw)
                    observer.addOnPreDrawListener(watch.preDraw)
                    watch.observer = WeakReference(observer)
                }
            } else {
                watch.observer?.get()?.takeIf { it.isAlive }?.removeOnPreDrawListener(watch.preDraw)
                watch.observer = null
            }
        }
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
        return extras.getBoolean(MARKER_PRESERVE_ICON, false)
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
