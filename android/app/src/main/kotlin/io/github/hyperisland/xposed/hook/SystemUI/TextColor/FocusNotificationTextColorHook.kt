package io.github.hyperisland.xposed.hook

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.Collections
import java.util.WeakHashMap

/**
 * 焦点通知文本颜色的“通用”实现，颜色计算复用 [TextColorPolicy]。
 *
 * 关键点：焦点通知模板里的颜色通常是**缺省**的（holder 的 `getXxxColor()` 返回 null），
 * 此时子类根本不会调用 `setTextColor`，文字直接用布局默认色（白色）。所以不能靠拦截
 * `setTextColor` 来改色，必须在 holder 绑定后主动遍历它自己的模块子树并重刷颜色。
 *
 * 作用范围严格限定在焦点通知模块（按 getIsland/getDynamicIsland 等标记方法是否声明判定，
 * 岛/焦点卡片/fake 岛各布局变体都会命中，不依赖其返回值），不触碰超级岛、媒体通知与状态栏。
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

    /** 模块根视图正在排队的一次遍历，避免一次 bind 重复遍历。弱引用键。 */
    private val pendingWalks = Collections.synchronizedMap(WeakHashMap<View, Any>())

    @Volatile private var islandAnimationRunning = false
    @Volatile private var pendingTintRefresh = false
    @Volatile private var tintRefreshScheduled = false

    private val statusBarTintListener: (Int) -> Unit = {
        if (isStatusBarMode()) {
            if (islandAnimationRunning || tintRefreshScheduled) {
                pendingTintRefresh = true
            } else {
                refreshTrackedColors()
            }
        }
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
            val coordinatorClass = classLoader.loadClass(DYNAMIC_ISLAND_EVENT_COORDINATOR_CLASS)
            if (hookedClasses.add(coordinatorClass)) {
                hookAnimationLifecycle(module, coordinatorClass)
            }
        }.onFailure { error ->
            if (error !is ClassNotFoundException) {
                logError(module, "failed to hook $DYNAMIC_ISLAND_EVENT_COORDINATOR_CLASS: ${error.message}")
            }
        }
    }

    private fun hookHolderLifecycle(module: XposedModule, holderClass: Class<*>) {
        holderClass.declaredMethods
            .filter { method ->
                (method.name == "bind" && method.parameterTypes.size == 2) ||
                    (method.name == "initTextAndColor" && method.parameterTypes.size == 1)
            }
            .forEach { method ->
                method.isAccessible = true
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    val holder = chain.thisObject
                    if (holder != null && isFocusHolder(holder)) {
                        scheduleWalk(holder)
                    }
                    result
                }
                log(module) { "hooked ${holderClass.name}#${method.name}" }
            }
    }

    private fun scheduleWalk(holder: Any) {
        if (!TextColorPolicy.isConfigured(currentMode())) return
        val root = getHolderView(holder) ?: return
        val token = Any()
        synchronized(pendingWalks) { pendingWalks[root] = token }
        mainHandler.post {
            val current = synchronized(pendingWalks) { pendingWalks[root] }
            if (current !== token) return@post
            synchronized(pendingWalks) { pendingWalks.remove(root) }
            runCatching { applyTree(root) }
        }
    }

    /** 遍历该焦点模块自己的视图子树，重刷灰度文本颜色。 */
    private fun applyTree(view: View) {
        if (view is TextView) {
            applyFocusTextColor(view)
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                applyTree(view.getChildAt(index))
            }
        }
    }

    private fun applyFocusTextColor(textView: TextView) {
        trackedViews.add(textView)
        TextColorPolicy.applyForTextView(
            textView,
            currentMode(),
            regionDark = false,
            statusBarTintBlack = statusBarTintBlack(),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // 秒表（HyperChronometer）：不显式设色，单独兜底
    // ---------------------------------------------------------------------------------------------

    private fun hookTimerData(module: XposedModule, holderClass: Class<*>) {
        holderClass.declaredMethods
            .filter { method -> method.name == "setTimerData" && method.parameterTypes.size == 2 }
            .forEach { method ->
                method.isAccessible = true
                module.hook(method).intercept { chain ->
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

    private fun hookAnimationLifecycle(module: XposedModule, coordinatorClass: Class<*>) {
        coordinatorClass.declaredMethods
            .filter { method -> method.name == "onAnimationStart" }
            .forEach { method ->
                module.hook(method).intercept { chain ->
                    islandAnimationRunning = true
                    chain.proceed()
                }
            }
        coordinatorClass.declaredMethods
            .filter { method ->
                method.name == "onAnimationFinished" || method.name == "onAnimationCancel"
            }
            .forEach { method ->
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    islandAnimationRunning = false
                    pendingTintRefresh = true
                    schedulePendingTintRefresh()
                    result
                }
            }
        log(module) { "hooked ${coordinatorClass.name} animation lifecycle" }
    }

    private fun schedulePendingTintRefresh() {
        if (!pendingTintRefresh || tintRefreshScheduled) return
        tintRefreshScheduled = true
        Choreographer.getInstance().postFrameCallback {
            tintRefreshScheduled = false
            if (islandAnimationRunning || !pendingTintRefresh) return@postFrameCallback
            pendingTintRefresh = false
            refreshTrackedColors()
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 作用范围与工具
    // ---------------------------------------------------------------------------------------------

    /**
     * 判定是否为焦点通知模块 holder。
     *
     * 只按“是否声明了岛标记方法”判定，**不看返回值**：这些 getIsland/getDynamicIsland 等
     * 方法在焦点模块各布局变体上都会声明（岛=false、焦点卡片=false、fake 岛=true），返回值
     * 只代表布局朝向而非是否焦点模块。若要求返回 true，焦点卡片（如来电/通话中）会被整段跳过。
     */
    private fun isFocusHolder(holder: Any): Boolean =
        ISLAND_MARKER_METHODS.any { methodName ->
            runCatching {
                holder.javaClass.methods.any { method ->
                    method.name == methodName &&
                        method.parameterTypes.isEmpty() &&
                        (method.returnType == java.lang.Boolean.TYPE ||
                            method.returnType == java.lang.Boolean::class.java)
                }
            }.getOrDefault(false)
        }

    private fun getHolderView(holder: Any): View? = runCatching {
        holder.javaClass.methods.firstOrNull { method ->
            method.name == "getView" && method.parameterTypes.isEmpty()
        }?.invoke(holder) as? View
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
    private const val DYNAMIC_ISLAND_EVENT_COORDINATOR_CLASS =
        "miui.systemui.dynamicisland.event.DynamicIslandEventCoordinator"
}
