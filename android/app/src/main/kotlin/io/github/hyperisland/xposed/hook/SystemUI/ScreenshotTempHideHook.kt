package io.github.hyperisland.xposed.hook.SystemUI

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import io.github.hyperisland.screenshot.ScreenshotContract
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * 截屏期间临时隐藏超级岛（SystemUI 侧执行端）。
 *
 * 超级岛窗口属于 SystemUI 进程（`DynamicIslandWindowView` 是窗口根 FrameLayout），而抓屏由
 * `com.miui.screenshot` 完成，因此只能由本进程把岛藏起来。收到“开始”广播后：
 *
 * 1. 走系统自己的临时隐藏入口 `onIslandTempHide(true, TempHiddenType)`，让柔光玻璃的采样租约
 *    按系统语义暂停（见 ISLAND.md 第 34 条），避免恢复时出现黑底；
 * 2. 立即把窗口根 View 置为 INVISIBLE —— 状态机切换带动画，只有改可见性能保证当前帧就没有岛。
 *
 * 收到“结束”或超过兜底时长后反向执行并还原可见性。
 *
 * 已核对 `miui.systemui.plugin` 17.1.3.76.0：`DynamicIslandWindowView` 只给子 View 设
 * visibility，从不设置自己，因此不需要拦截 `setVisibility`（该类也未声明此方法）。
 */
object ScreenshotTempHideHook : BaseHook() {

    private const val TAG = "HyperIsland[ScreenshotHide]"
    private const val PREF_MASTER = "pref_temp_hide_behavior_enabled"
    private const val PREF_ENABLED = "pref_temp_hide_screenshot"
    private const val WINDOW_VIEW_CLASS =
        "miui.systemui.dynamicisland.window.DynamicIslandWindowView"
    private const val WINDOW_STATE_CLASS =
        "miui.systemui.dynamicisland.window.DynamicIslandWindowState"

    /**
     * 借用的临时隐藏类型。枚举里没有 SCREENSHOT，挑一个语义最无害的：
     * 避开 SHOW_ONCE_PROP_ISLAND（IslandTempHiddenEventCoordinator 会跳过收起）、
     * MIPLAY_SHOW（setTempHide 会删除 showOnce 岛）、SCREEN_LOCKED（中低端机有专属分支）。
     */
    private const val TEMP_HIDDEN_TYPE = "SCREEN_PINNING_ACTIVE"

    private val hookedClassLoaders = ConcurrentHashMap.newKeySet<Int>()

    /** 已观察到的岛窗口 View（弱引用，避免泄漏窗口）。 */
    private val islandViews = Collections.newSetFromMap(
        Collections.synchronizedMap(WeakHashMap<View, Boolean>()),
    )

    /** 隐藏前各自的可见性，恢复时逐个还原，避免把本应 GONE 的窗口点亮。 */
    private val savedVisibility = Collections.synchronizedMap(WeakHashMap<View, Int>())

    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()

    @Volatile
    private var hidden = false

    @Volatile
    private var registered = false

    @Volatile
    private var moduleRef: XposedModule? = null

    /** `onIslandTempHide(boolean, TempHiddenType)` 与枚举实例，解析一次后缓存。 */
    @Volatile
    private var tempHideMethod: Method? = null

    @Volatile
    private var tempHiddenType: Any? = null

    private val restoreRunnable = Runnable { restoreInternal() }
    private val reassertRunnable = object : Runnable {
        override fun run() {
            if (!hidden) return
            applyHideInternal()
            mainHandler.postDelayed(this, ScreenshotContract.REASSERT_INTERVAL_MS)
        }
    }

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        moduleRef = module
        hookApplicationOnCreate(module, param)
        hookIslandWindowClass(module, param.defaultClassLoader)
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { classLoader ->
            hookIslandWindowClass(module, classLoader)
        }
    }

    private fun hookApplicationOnCreate(module: XposedModule, param: PackageLoadedParam) {
        runCatching {
            val method = param.defaultClassLoader
                .loadClass("android.app.Application")
                .getDeclaredMethod("onCreate")
            module.hook(method).intercept { chain ->
                val result = chain.proceed()
                val app = chain.thisObject as? android.app.Application
                if (app != null) registerReceiver(app.applicationContext, module)
                result
            }
        }.onFailure {
            logError(module, "Application.onCreate hook failed: ${it.message}")
        }
    }

    private fun registerReceiver(context: Context, module: XposedModule) {
        if (registered) return
        synchronized(this) {
            if (registered) return
            val filter = IntentFilter(ScreenshotContract.ACTION_STATE)
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(receiver, filter)
            }
            registered = true
            log(module) { "screenshot state receiver registered" }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ScreenshotContract.ACTION_STATE) return
            if (!enabled()) return
            val active = intent.getBooleanExtra(ScreenshotContract.EXTRA_ACTIVE, false)
            if (active) hide() else restore()
        }
    }

    // ── 岛窗口 View 采集 ────────────────────────────────────────────────────

    private fun hookIslandWindowClass(module: XposedModule, classLoader: ClassLoader) {
        val classLoaderId = System.identityHashCode(classLoader)
        if (!hookedClassLoaders.add(classLoaderId)) return
        val clazz = runCatching { classLoader.loadClass(WINDOW_VIEW_CLASS) }.getOrNull()
        if (clazz == null) {
            hookedClassLoaders.remove(classLoaderId)
            return
        }
        resolveTempHideApi(module, clazz)
        var count = 0
        // onAttachedToWindow 是主采集点：所有实例必然经过，且 hook 在 Application.onCreate 就装好。
        // 其余回调用于兜底（hook 装晚 / 实例被重建）。
        listOf(
            "onAttachedToWindow" to emptyArray<Class<*>>(),
            "onKeyguardShowing" to arrayOf(Boolean::class.javaPrimitiveType!!),
            "onConfigChanged" to arrayOf(Configuration::class.java),
            "onIslandTempHide" to null, // 2 参、首参 boolean，第二参 TempHiddenType，单独匹配
        ).forEach { (name, types) ->
            clazz.declaredMethods.filter { method ->
                method.name == name &&
                    if (types == null) {
                        method.parameterCount == 2 &&
                            method.parameterTypes[0] == Boolean::class.javaPrimitiveType
                    } else {
                        method.parameterTypes.contentEquals(types)
                    }
            }.forEach { method ->
                module.hook(method).intercept { chain ->
                    val view = chain.thisObject as? View
                    // 这里只能登记实例：回调里再触发隐藏会在 onIslandTempHide 上形成递归。
                    if (view != null) synchronized(lock) { islandViews.add(view) }
                    chain.proceed()
                }
                count++
            }
        }
        if (count == 0) hookedClassLoaders.remove(classLoaderId)
        log(module) { "hooked $WINDOW_VIEW_CLASS methods=$count (cl=$classLoaderId)" }
    }

    private fun resolveTempHideApi(module: XposedModule, windowViewClass: Class<*>) {
        val method = runCatching {
            windowViewClass.declaredMethods.firstOrNull { candidate ->
                candidate.name == "onIslandTempHide" &&
                    candidate.parameterCount == 2 &&
                    candidate.parameterTypes[0] == Boolean::class.javaPrimitiveType
            }?.apply { isAccessible = true }
        }.getOrNull()
        if (method == null) {
            logWarn(module, "onIslandTempHide not found; visibility-only hiding")
            return
        }
        val enumValue = runCatching {
            val loader = windowViewClass.classLoader ?: error("no class loader")
            val stateClass = loader.loadClass(WINDOW_STATE_CLASS)
            val enumClass = stateClass.declaredClasses
                .firstOrNull { it.simpleName == "TempHiddenType" }
                ?: loader.loadClass("$WINDOW_STATE_CLASS\$TempHiddenType")
            enumClass.getField(TEMP_HIDDEN_TYPE).get(null)
        }.onFailure {
            logWarn(module, "TempHiddenType.$TEMP_HIDDEN_TYPE unavailable: ${it.message}")
        }.getOrNull()
        if (enumValue == null) {
            logWarn(module, "visibility-only hiding; temp-hide event not dispatched")
            return
        }
        tempHideMethod = method
        tempHiddenType = enumValue
        log(module) { "temp hide api ready: ${method.name}(boolean, $TEMP_HIDDEN_TYPE)" }
    }

    // ── 隐藏 / 恢复 ─────────────────────────────────────────────────────────

    private fun enabled(): Boolean =
        ConfigManager.getBoolean(PREF_MASTER, false) && ConfigManager.getBoolean(PREF_ENABLED, true)

    private fun hide() {
        hidden = true
        mainHandler.removeCallbacks(restoreRunnable)
        mainHandler.post {
            applyHideInternal()
            mainHandler.removeCallbacks(reassertRunnable)
            mainHandler.postDelayed(reassertRunnable, ScreenshotContract.REASSERT_INTERVAL_MS)
            mainHandler.postDelayed(restoreRunnable, ScreenshotContract.FALLBACK_HIDE_TIMEOUT_MS)
        }
        moduleRef?.let { log(it) { "island hidden for screenshot" } }
    }

    private fun restore() {
        hidden = false
        mainHandler.removeCallbacks(reassertRunnable)
        mainHandler.removeCallbacks(restoreRunnable)
        mainHandler.post { restoreInternal() }
        moduleRef?.let { log(it) { "island restored after screenshot" } }
    }

    private fun applyHideInternal() {
        val snapshot = synchronized(lock) { islandViews.toList() }
        snapshot.forEach { view ->
            if (savedVisibility[view] == null && view.visibility == View.VISIBLE) {
                savedVisibility[view] = view.visibility
                dispatchTempHide(view, true)
                view.visibility = View.INVISIBLE
            }
        }
    }

    private fun restoreInternal() {
        hidden = false
        val entries = synchronized(lock) { savedVisibility.entries.toList() }
        savedVisibility.clear()
        entries.forEach { (view, previous) ->
            if (view.visibility == View.INVISIBLE) {
                view.visibility = previous
                dispatchTempHide(view, false)
            }
        }
    }

    /** 走系统自己的临时隐藏生命周期，让柔光玻璃租约同步暂停 / 恢复。 */
    private fun dispatchTempHide(view: View, hide: Boolean) {
        val method = tempHideMethod ?: return
        val type = tempHiddenType ?: return
        runCatching { method.invoke(view, hide, type) }.onFailure { error ->
            moduleRef?.let { logWarn(it, "onIslandTempHide($hide) failed: ${error.message}") }
        }
    }

    /** 供同进程其它触发源复用。 */
    fun requestHide() {
        if (enabled()) hide() else Unit
    }
}
