package io.github.hyperisland.xposed.hook

import io.github.hyperisland.xposed.diagnostics.profiledHook

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.hyperisland.xposed.utils.WeakMemberCache
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

/**
 * 焦点通知文本颜色的“通用”实现，颜色计算复用 [TextColorPolicy]。
 *
 * 关键点：焦点通知模板里的颜色通常是**缺省**的（holder 的 `getXxxColor()` 返回 null），
 * 此时子类根本不会调用 `setTextColor`，文字直接用布局默认色（白色）。所以不能靠拦截
 * `setTextColor` 来改色，必须在 holder 绑定后主动遍历它自己的模块子树并重刷颜色。
 *
 * 作用范围严格限定在**动态岛展开态**的焦点通知模块（getIsland/getDynamicIsland 等标记方法
 * 返回 true；焦点通知卡片——即下拉通知中心里的那一条——返回 false，必须排除），不触碰超级岛、
 * 媒体通知、通知中心与状态栏。带 `miui.focus.rv` 的自定义焦点通知（如来电）不走 ModuleViewHolder，
 * 另经 FocusNotificationContent#setIslandExpandedView 覆盖。
 */
object FocusNotificationTextColorHook : BaseHook() {

    private const val TAG = "HyperIsland[FocusNotificationTextColor]"
    private const val KEY_TEXT_COLOR_MODE = "pref_focus_notification_text_color_mode"

    private val mainHandler = Handler(Looper.getMainLooper())

    private val hookedClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    )

    /** 本 Hook 处理过的焦点 TextView（用于重刷）。弱引用键。 */
    private val trackedViews = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<TextView, Boolean>())
    )

    private val pendingWalks = NotificationColorPreDraw { root ->
        val mode = currentMode()
        if (TextColorPolicy.isConfigured(mode)) {
            runCatching { applyTree(root, mode, statusBarTintBlack()) }
        }
    }
    private val getters = WeakMemberCache<Method> { type, name ->
        runCatching { type.getMethod(name) }.getOrNull()
    }

    private val statusBarTintListener: (Int) -> Unit = {
        if (isStatusBarMode()) refreshTrackedColors()
    }

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        StatusBarTextColorHook.addReadableTintListener(statusBarTintListener)
        hookClasses(module, param.defaultClassLoader)
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { classLoader ->
            hookClasses(module, classLoader)
        }
    }

    override fun onConfigChanged() {
        refreshTrackedColors()
    }

    // ---------------------------------------------------------------------------------------------
    // Holder 生命周期：绑定后遍历它自己的模块子树
    // ---------------------------------------------------------------------------------------------

    private fun hookClasses(module: XposedModule, classLoader: ClassLoader) {
        runCatching {
            val holderClass = classLoader.loadClass(FOCUS_HOLDER_CLASS)
            if (hookedClasses.add(holderClass)) {
                hookHolderLifecycle(module, holderClass)
                hookTimerData(module, holderClass)
            }
        }.onFailure { error ->
            if (error !is ClassNotFoundException) {
                logError(module, "failed to hook $FOCUS_HOLDER_CLASS: ${error.message}")
            }
        }

        runCatching {
            val preHandlerClass = classLoader.loadClass(FOCUS_PRE_HANDLER_CLASS)
            if (hookedClasses.add(preHandlerClass)) hookCreateCustomView(module, preHandlerClass)
        }.onFailure { error ->
            if (error !is ClassNotFoundException) logError(module, "failed to hook focus pre-handler: $error")
        }

        runCatching {
            val contentClass = classLoader.loadClass(FOCUS_CONTENT_CLASS)
            if (hookedClasses.add(contentClass)) {
                hookIslandExpandedView(module, contentClass)
            }
        }.onFailure { error ->
            if (error !is ClassNotFoundException) {
                logError(module, "failed to hook $FOCUS_CONTENT_CLASS: ${error.message}")
            }
        }
    }

    /**
     * 自定义焦点通知（带 `miui.focus.rv` RemoteViews，如来电/通话中）不走
     * [FOCUS_HOLDER_CLASS]（ModuleViewHolder），文字位于 app 下发的 RemoteViews 里。
     * 这里只拦截“焦点岛展开视图”的设入点（`setIslandExpandedView`/`setIslandExpandedViewFake`）
     * 并遍历其子树，绝不触碰下拉通知中心里的焦点通知卡片。
     */
    private fun hookIslandExpandedView(module: XposedModule, contentClass: Class<*>) {
        contentClass.declaredMethods
            .filter { method ->
                (method.name == "setIslandExpandedView" ||
                    method.name == "setIslandExpandedViewFake") &&
                    method.parameterTypes.size == 1 &&
                    View::class.java.isAssignableFrom(method.parameterTypes[0])
            }
            .forEach { method ->
                method.isAccessible = true
                module.profiledHook("FocusNotificationTextColorHook", method).intercept { chain ->
                    val result = chain.proceed()
                    val view = chain.args.getOrNull(0) as? View
                    if (view != null && TextColorPolicy.isConfigured(currentMode())) {
                        scheduleViewWalk(view)
                    }
                    result
                }
                log(module) { "hooked ${contentClass.name}#${method.name}" }
            }
    }

    private fun hookCreateCustomView(module: XposedModule, preHandlerClass: Class<*>) {
        preHandlerClass.declaredMethods.filter { it.name == "createCustomView" }.forEach { method ->
            val contentIndex = method.parameterTypes.indexOfFirst { it.name == FOCUS_CONTENT_CLASS }
            if (contentIndex < 0) return@forEach
            method.isAccessible = true
            module.profiledHook("FocusNotificationTextColorHook", method).intercept { chain ->
                val result = chain.proceed()
                val content = chain.args[contentIndex]
                if (content != null && TextColorPolicy.isConfigured(currentMode())) {
                    runCatching {
                        for (name in CUSTOM_VIEW_GETTERS) {
                            val root = getters.get(content.javaClass, name)?.invoke(content) as? View
                            if (root != null) scheduleViewWalk(root)
                        }
                    }
                }
                result
            }
        }
    }

    private fun hookHolderLifecycle(module: XposedModule, holderClass: Class<*>) {
        holderClass.declaredMethods
            .filter { method ->
                ((method.name == "bind" || method.name == "updatePartial") &&
                    method.parameterTypes.size == 2) ||
                    (method.name == "initTextAndColor" && method.parameterTypes.size == 1)
            }
            .forEach { method ->
                method.isAccessible = true
                module.profiledHook("FocusNotificationTextColorHook", method).intercept { chain ->
                    val result = chain.proceed()
                    val holder = chain.thisObject
                    if (holder != null && TextColorPolicy.isConfigured(currentMode()) &&
                        isFocusHolder(holder)
                    ) {
                        getHolderView(holder)?.let(::scheduleViewWalk)
                    }
                    result
                }
                log(module) { "hooked ${holderClass.name}#${method.name}" }
            }
    }

    private fun scheduleViewWalk(root: View) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            pendingWalks.schedule(root)
        } else {
            val reference = WeakReference(root)
            mainHandler.post { reference.get()?.let(pendingWalks::schedule) }
        }
    }

    private fun applyTree(view: View, mode: String, sbBlack: Boolean) {
        if (view is TextView) applyFocusTextColor(view, mode, sbBlack)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                applyTree(view.getChildAt(index), mode, sbBlack)
            }
        }
    }

    private fun applyFocusTextColor(
        textView: TextView,
        mode: String = currentMode(),
        sbBlack: Boolean = statusBarTintBlack(),
    ) {
        synchronized(trackedViews) {
            if (trackedViews.size < 256) trackedViews.add(textView)
        }
        TextColorPolicy.applyForTextView(textView, mode, regionDark = false, statusBarTintBlack = sbBlack)
    }

    // ---------------------------------------------------------------------------------------------
    // 秒表（HyperChronometer）：不显式设色，单独兜底
    // ---------------------------------------------------------------------------------------------

    private fun hookTimerData(module: XposedModule, holderClass: Class<*>) {
        holderClass.declaredMethods
            .filter { method -> method.name == "setTimerData" && method.parameterTypes.size == 2 }
            .forEach { method ->
                method.isAccessible = true
                module.profiledHook("FocusNotificationTextColorHook", method).intercept { chain ->
                    val result = chain.proceed()
                    val holder = chain.thisObject
                    if (holder != null && isFocusHolder(holder)) {
                        runCatching {
                            val root = getHolderView(holder)
                            val viewId = chain.args.firstOrNull() as? Int ?: View.NO_ID
                            val chronometer = (
                                if (root != null && viewId != View.NO_ID) {
                                    root.findViewById<View>(viewId) as? TextView
                                } else {
                                    null
                                }
                                ) ?: root?.let(TextColorPolicy::findHyperChronometer)
                            if (chronometer != null) applyFocusTextColor(chronometer)
                        }.onFailure { logError(module, "focus timer color failed: ${it.message}") }
                    }
                    result
                }
                log(module) { "hooked ${holderClass.name}#${method.name}" }
            }
    }

    // ---------------------------------------------------------------------------------------------
    // 配置变更 / 状态栏明暗变化重刷
    // ---------------------------------------------------------------------------------------------

    private fun refreshTrackedColors() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            refreshTrackedColorsOnMain()
        } else {
            mainHandler.post { refreshTrackedColorsOnMain() }
        }
    }

    private fun refreshTrackedColorsOnMain() {
        val mode = currentMode()
        val sbBlack = statusBarTintBlack()
        val views = synchronized(trackedViews) { trackedViews.toList() }
        views.forEach { textView ->
            val original = TextColorPolicy.originalOf(textView) ?: return@forEach
            runCatching { TextColorPolicy.retarget(textView, original, mode, false, sbBlack) }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 作用范围与工具
    // ---------------------------------------------------------------------------------------------

    /**
     * 判定是否为焦点通知模块的“岛”（展开态）holder。
     *
     * getIsland/getDynamicIsland/getFakeDynamicIsland/isDynamicIsland 只有焦点模块各布局变体
     * 才声明，且只有动态岛展开态变体返回 true；焦点通知卡片（下拉通知中心里的那一条）返回
     * false。这里必须要求返回 true，否则会误改通知中心的文本颜色。
     */
    private fun isFocusHolder(holder: Any): Boolean =
        ISLAND_MARKER_METHODS.any { methodName ->
            runCatching {
                getters.get(holder.javaClass, methodName)?.invoke(holder) as? Boolean
            }.getOrNull() == true
        }

    private fun getHolderView(holder: Any): View? = runCatching {
        getters.get(holder.javaClass, "getView")?.invoke(holder) as? View
    }.getOrNull()

    private fun currentMode(): String =
        when (val mode = ConfigManager.getString(KEY_TEXT_COLOR_MODE, TextColorPolicy.MODE_DEFAULT)) {
            TextColorPolicy.MODE_BLACK,
            TextColorPolicy.MODE_FOLLOW_STATUS_BAR,
            TextColorPolicy.MODE_INVERT_STATUS_BAR -> mode
            else -> TextColorPolicy.MODE_DEFAULT
        }

    private fun isStatusBarMode(): Boolean = currentMode().let {
        it == TextColorPolicy.MODE_FOLLOW_STATUS_BAR || it == TextColorPolicy.MODE_INVERT_STATUS_BAR
    }

    private fun statusBarTintBlack(): Boolean =
        StatusBarTextColorHook.getReadableTint() == Color.BLACK

    private val ISLAND_MARKER_METHODS = listOf(
        "getIsland",
        "getDynamicIsland",
        "getFakeDynamicIsland",
        "isDynamicIsland",
    )

    private const val FOCUS_HOLDER_CLASS =
        "miui.systemui.notification.focus.moduleV3.ModuleViewHolder"
    private const val FOCUS_CONTENT_CLASS =
        "com.android.systemui.plugins.miui.notification.FocusNotificationContent"
    private const val FOCUS_PRE_HANDLER_CLASS = "miui.systemui.notification.focus.FocusNotifPreHandler"
    private val CUSTOM_VIEW_GETTERS = listOf("getIslandExpandedView", "getIslandExpandedViewFake")
}
