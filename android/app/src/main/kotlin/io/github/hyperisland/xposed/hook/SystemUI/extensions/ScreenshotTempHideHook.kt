package io.github.hyperisland.xposed.hook.SystemUI.extensions

import io.github.hyperisland.xposed.diagnostics.profiledHook

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import io.github.hyperisland.screenshot.ScreenshotContract
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.hyperisland.xposed.hook.TempHideOverride
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * 截屏期间临时隐藏超级岛（SystemUI 侧执行端，属于「Hook 拓展」）。
 *
 * 岛窗口属于 SystemUI 进程，抓屏由 `com.miui.screenshot` 完成，所以只能由本进程把岛收起来。
 * 收到“开始”广播后调用官方临时隐藏入口
 * `DynamicIslandWindowViewController.statusBarAppearance(true)`，收到“结束”或超过兜底时长后
 * 再调 `false` 恢复。
 *
 * 为什么是 `statusBarAppearance` 而不是 `screenPinningActive`（重要，已核对 18.3.2.57.0）：
 * 岛的最终可见性由 `DynamicIslandWindowState.tempHidden` 这个 combine 决定，`screenPinning`
 * 在其中的作用是**抑制隐藏**（`!statusBarDisappearance || screenPinning || immerse` 这一支），
 * 也就是说 `screenPinningActive(true)` 的效果是“屏幕固定期间保持岛可见”，调它反而不会收起。
 * 真正把 `tempHidden` 置为 true 的是 `statusBarDisappearance`：`statusBarAppearance(true)`
 * 会写入 `TempHiddenType.STATUS_BAR_DISAPPEARANCE` 并经状态流派发到
 * `DynamicIslandWindowView.onIslandTempHide(true, type)`，这正是系统在状态栏消失（全屏）时
 * 走的那条链路，17.1.3.76.0（OS3）与 18.3.2.57.0（OS4）实现一致。
 *
 * 收尾兜底（岛窗口残留）：只用官方链路时，真机截图在挖孔位置仍会看到一个黑色圆圈 ——
 * 把抓屏提前量从 240ms 加到 420ms 都没有消除，开启「常驻岛」时可稳定复现（具体是收起动画
 * 尾帧，还是常驻岛等其它绘制，尚未完全区分）。因此在官方链路派发后把岛窗口根 View 的
 * alpha 置 0，抓完再恢复 —— 不动 `visibility`、不碰子 View，也不参与系统的布局/状态机，
 * 只在截屏这一小段窗口内生效；另有 [ScreenshotContract.FALLBACK_HIDE_TIMEOUT_MS] 的
 * watchdog 保证一定会恢复。
 *
 * 刻意**不做**的事：
 * - 不遍历 / 收集岛的子 View，也不改它们的 `visibility`。官方链路带动画，靠改子 View 可见性
 *   抢帧会在 OS4 上出现收起后回滚清理没触发的问题，岛会留在隐藏态。
 * - 未开启时直接返回，一个 Hook 都不注入。
 * - 不覆盖系统本来就已经隐藏的场景：调用前先读一次当前状态，已经隐藏就直接跳过，
 *   避免截完屏把全屏应用里的岛“顶”出来。
 *
 * 开关时机：`onPackageLoaded` 只在进程启动时回调一次，如果那时开关还是关的，本类就什么都不做；
 * 随后 [onConfigChanged] 会在配置变更时补装 Hook（[install]）。但控制器实例在进程启动时
 * 就已经构造完、`start()` 也跑过了，运行期补装拿不到旧实例 —— 所以开关切换后仍建议按界面
 * 提示重启 SystemUI（下次启动时开关已打开，`onInit` 会照常装配）。
 * 关闭开关则相反：已装的 Hook 撤不掉，只能靠 [ScreenshotContract.PREF_ENABLED] 在
 * [receiver] 里短路。
 */
object ScreenshotTempHideHook : BaseHook() {

    private const val TAG = "HyperIsland[ScreenshotHide]"

    private val hookedClassLoaders = ConcurrentHashMap.newKeySet<Int>()
    private val warnedKeys = ConcurrentHashMap.newKeySet<String>()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()

    /** 控制器实例（弱引用，避免持有窗口）。 */
    @Volatile
    private var controllerRef: WeakReference<Any>? = null

    /** 岛窗口根 View（`DynamicIslandWindowView`，弱引用），用于兜底压 alpha。 */
    @Volatile
    private var islandViewRef: WeakReference<View>? = null

    /** `statusBarAppearance(boolean)`，解析一次后缓存。 */
    @Volatile
    private var controllerHideMethod: Method? = null

    /** 兜底：`DynamicIslandWindowState.updateStatusBarDisappearance(boolean)`。 */
    @Volatile
    private var stateHideMethod: Method? = null

    @Volatile
    private var getWindowStateMethod: Method? = null

    @Volatile
    private var getStatusBarDisappearanceMethod: Method? = null

    @Volatile
    private var flowGetValueMethod: Method? = null

    @Volatile
    private var enabled = false

    /** 本次进程内是否已经装过 Hook（含 [onConfigChanged] 补装）。 */
    @Volatile
    private var installed = false

    /** 当前的隐藏是不是本模块发起的（只有自己发起的才负责恢复）。 */
    @Volatile
    private var hiddenByUs = false

    @Volatile
    private var registered = false

    @Volatile
    private var moduleRef: XposedModule? = null

    /** SystemUI 的 PathClassLoader，补装时用来取 Context 和解析插件类。 */
    @Volatile
    private var rootClassLoader: ClassLoader? = null

    @Volatile
    private var loadParam: PackageLoadedParam? = null

    private val restoreRunnable = Runnable { restore() }

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        if (param.packageName != "com.android.systemui") return
        moduleRef = module
        rootClassLoader = param.defaultClassLoader
        loadParam = param
        loadConfig()
        // 未开启时直接返回：不注入任何 Hook。开启后由 onConfigChanged 补装。
        if (!enabled) return
        install(module, param)
    }

    override fun onConfigChanged() {
        val wasEnabled = enabled
        loadConfig()
        when {
            wasEnabled && !enabled -> {
                // 隐藏期间被关掉开关：立刻恢复，避免岛停在收起状态。
                restore()
                // 已装的 Hook 撤不掉，靠 receiver 的开关短路即可。
            }

            !wasEnabled && enabled -> {
                val module = moduleRef ?: return
                // 开关在进程启动后才打开：尝试补装。控制器实例可能早已创建，
                // 拿不到时会在下一次 createView() 重建控制器时补上。
                install(module, loadParam)
            }
        }
    }

    private fun loadConfig() {
        enabled = ConfigManager.getBoolean(ScreenshotContract.PREF_ENABLED, false)
    }

    /**
     * 装配本拓展所需的全部 Hook。
     *
     * [param] 为空表示本次是运行期补装 —— 那时 `Application.onCreate` 早已执行完，
     * 只能改用 [HookUtils.getContext] 拿当前 Context 注册广播。
     */
    private fun install(module: XposedModule, param: PackageLoadedParam?) {
        if (installed) return
        installed = true

        val classLoader = param?.defaultClassLoader
            ?: rootClassLoader
            ?: ClassLoader.getSystemClassLoader()
        rootClassLoader = classLoader

        hookController(module, classLoader)
        HookUtils.hookDynamicClassLoaders(
            module,
            ClassLoader.getSystemClassLoader(),
        ) { loader -> hookController(module, loader) }

        if (param != null) hookApplicationOnCreate(module, param)
        // 早期拿不到 Context 时返回 null，交给 Application.onCreate 回调补注册。
        ensureReceiver(module)
    }

    private fun hookApplicationOnCreate(module: XposedModule, param: PackageLoadedParam) {
        runCatching {
            val method = param.defaultClassLoader
                .loadClass("android.app.Application")
                .getDeclaredMethod("onCreate")
            module.profiledHook("ScreenshotTempHideHook", method).intercept { chain ->
                val result = chain.proceed()
                val app = chain.thisObject as? Application
                if (app != null) registerReceiver(app.applicationContext, module)
                result
            }
        }.onFailure {
            logError(module, "Application.onCreate hook failed: ${it.message}")
        }
    }

    private fun ensureReceiver(module: XposedModule) {
        val classLoader = rootClassLoader ?: return
        val context = HookUtils.getContext(classLoader) ?: return
        registerReceiver(context, module)
    }

    private fun registerReceiver(context: Context, module: XposedModule) {
        if (registered) return
        synchronized(lock) {
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
            if (!enabled) return
            if (intent.getBooleanExtra(ScreenshotContract.EXTRA_ACTIVE, false)) hide() else restore()
        }
    }

    // ── 官方隐藏岛链路 ──────────────────────────────────────────────────────

    private fun hookController(module: XposedModule, classLoader: ClassLoader) {
        val classLoaderId = System.identityHashCode(classLoader)
        if (!hookedClassLoaders.add(classLoaderId)) return
        val clazz = runCatching {
            classLoader.loadClass(ScreenshotContract.WINDOW_CONTROLLER_CLASS)
        }.getOrNull()
        if (clazz == null) {
            hookedClassLoaders.remove(classLoaderId)
            return
        }

        // 隐藏入口：控制器私有方法 statusBarAppearance(boolean)。
        val hideMethod = clazz.declaredMethods.firstOrNull { candidate ->
            candidate.name == ScreenshotContract.HIDE_METHOD &&
                candidate.parameterCount == 1 &&
                candidate.parameterTypes[0] == Boolean::class.javaPrimitiveType
        }
        if (hideMethod == null) {
            warnOnce(module, "hide-method") {
                "${ScreenshotContract.HIDE_METHOD}(boolean) not found in ${clazz.name}"
            }
        } else {
            hideMethod.isAccessible = true
            controllerHideMethod = hideMethod
            // 系统在状态栏消失 / 恢复时也会走它，顺手再兜一次控制器实例。
            module.profiledHook("ScreenshotTempHideHook", hideMethod).intercept { chain ->
                capture(chain.thisObject)
                chain.proceed()
            }
        }

        // 状态读取 + 兜底入口：getWindowState().updateStatusBarDisappearance(boolean)。
        resolveStateAccessors(module, clazz)

        // 岛窗口根 View：用于截屏期间兜底压 alpha。
        hookIslandView(module, classLoader)

        var count = 0
        // start() 是拿控制器实例的主入口；构造器再兜一次，防止 Hook 装晚、start() 已经跑过。
        clazz.declaredMethods.filter { candidate ->
            candidate.name == "start" && candidate.parameterCount == 0
        }.forEach { start ->
            module.profiledHook("ScreenshotTempHideHook", start).intercept { chain ->
                val result = chain.proceed()
                capture(chain.thisObject)
                result
            }
            count++
        }
        runCatching {
            clazz.declaredConstructors.forEach { constructor ->
                module.profiledHook("ScreenshotTempHideHook", constructor).intercept { chain ->
                    val result = chain.proceed()
                    capture(chain.thisObject)
                    result
                }
                count++
            }
        }.onFailure {
            log(module) { "controller constructor hook unavailable: ${it.javaClass.simpleName}" }
        }
        log(module) { "hooked ${clazz.simpleName} methods=$count (cl=$classLoaderId)" }
    }

    /**
     * 解析 `getWindowState()` / `updateStatusBarDisappearance(boolean)` /
     * `getStatusBarDisappearance()` 三个反射点。
     *
     * 前者用于兜底隐藏，后者用于“隐藏前先看一眼是否已经隐藏”，避免截完屏把全屏应用里的岛顶出来。
     */
    private fun resolveStateAccessors(module: XposedModule, controllerClass: Class<*>) {
        val getState = controllerClass.methods.firstOrNull {
            it.name == "getWindowState" && it.parameterCount == 0
        } ?: controllerClass.declaredMethods.firstOrNull {
            it.name == "getWindowState" && it.parameterCount == 0
        }
        if (getState == null) {
            log(module) { "getWindowState() unavailable; state snapshot disabled" }
            return
        }
        getState.isAccessible = true
        getWindowStateMethod = getState

        val stateClass = getState.returnType
        val stateHide = stateClass.declaredMethods.firstOrNull { candidate ->
            candidate.name == ScreenshotContract.STATE_HIDE_METHOD &&
                candidate.parameterCount == 1 &&
                candidate.parameterTypes[0] == Boolean::class.javaPrimitiveType
        }
        if (stateHide != null) {
            stateHide.isAccessible = true
            stateHideMethod = stateHide
        }
        getStatusBarDisappearanceMethod = stateClass.methods.firstOrNull {
            it.name == "getStatusBarDisappearance" && it.parameterCount == 0
        }
        if (getStatusBarDisappearanceMethod == null) {
            log(module) { "getStatusBarDisappearance() unavailable; state snapshot disabled" }
        }
    }

    /**
     * 捕获 `DynamicIslandWindowView`，用于截屏期间把它的 alpha 压到 0。
     *
     * 挂 `onIslandTempHide(boolean, TempHiddenType)`：系统每次收起 / 恢复岛都会经过它，
     * 顺手就能拿到窗口根 View，不需要遍历 View 树。
     */
    private fun hookIslandView(module: XposedModule, classLoader: ClassLoader) {
        val clazz = runCatching {
            classLoader.loadClass(ScreenshotContract.ISLAND_VIEW_CLASS)
        }.getOrNull() ?: return
        val method = clazz.declaredMethods.firstOrNull { candidate ->
            candidate.name == "onIslandTempHide" && candidate.parameterCount == 2
        }
        if (method == null) {
            log(module) { "onIslandTempHide not found; island alpha fallback disabled" }
            return
        }
        module.profiledHook("ScreenshotTempHideHook", method).intercept { chain ->
            (chain.thisObject as? View)?.let { islandViewRef = WeakReference(it) }
            chain.proceed()
        }
        log(module) { "hooked ${clazz.simpleName}.onIslandTempHide for alpha fallback" }
    }

    /** 只在主线程调用；alpha 不参与布局，恢复时不会触发系统的 View 清理逻辑。 */
    private fun setIslandAlpha(alpha: Float) {
        val view = islandViewRef?.get() ?: return
        if (view.alpha != alpha) view.alpha = alpha
    }

    /** 只输出一次的警告：始终可见，不依赖调试日志开关。 */
    private fun warnOnce(module: XposedModule, key: String, message: () -> String) {
        if (!warnedKeys.add(key)) return
        logWarn(module, message())
    }

    private fun capture(instance: Any?) {
        if (instance == null) return
        if (controllerRef?.get() === instance) return
        controllerRef = WeakReference(instance)
    }

    // ── 隐藏 / 恢复 ─────────────────────────────────────────────────────────

    private fun hide() {
        mainHandler.removeCallbacks(restoreRunnable)
        mainHandler.post {
            val controller = controllerRef?.get()
            val module = moduleRef ?: return@post
            if (controller == null) {
                warnOnce(module, "controller-unavailable") {
                    "island controller unavailable; skip screenshot temp hide"
                }
                return@post
            }
            // 系统本来就隐藏着（全屏 / 锁屏等）：不要插手，否则恢复时会把岛顶出来。
            if (readStatusBarHidden(controller) == true) {
                if (hiddenByUs) {
                    // 已经是我们隐藏的：只把兜底计时往后顺延（长截屏 / 连拍）。
                    mainHandler.postDelayed(
                        restoreRunnable,
                        ScreenshotContract.FALLBACK_HIDE_TIMEOUT_MS,
                    )
                } else {
                    log(module) { "status bar already hidden; skip" }
                }
                return@post
            }
            if (!dispatchHide(controller, true)) return@post
            hiddenByUs = true
            // 官方链路已在跑收起动画，这里再把根 View 压透明，保证抓屏那一帧什么都没有。
            // 恢复走 restore()，另有 2s watchdog 兜底。
            setIslandAlpha(0f)
            warnOnce(module, "hide-dispatched") { "island temp hidden for screenshot" }
            mainHandler.postDelayed(
                restoreRunnable,
                ScreenshotContract.FALLBACK_HIDE_TIMEOUT_MS,
            )
        }
        moduleRef?.let { log(it) { "hide request received from screenshot process" } }
    }

    private fun restore() {
        mainHandler.removeCallbacks(restoreRunnable)
        mainHandler.post {
            if (!hiddenByUs) return@post
            hiddenByUs = false
            val controller = controllerRef?.get()
            if (controller != null) dispatchHide(controller, false)
            // 无论官方链路有没有恢复成功，都必须把 alpha 还原，避免岛一直不可见。
            setIslandAlpha(1f)
        }
        moduleRef?.let { log(it) { "island temp hide released" } }
    }

    /** 返回是否真的调用到了隐藏入口。 */
    private fun dispatchHide(controller: Any, hide: Boolean): Boolean {
        val module = moduleRef ?: return false
        val direct = controllerHideMethod
        if (direct != null) {
            // 走 TempHideOverride 放行：否则用户勾了隐藏行为总开关却没勾「全屏」时，
            // TempHiddenBehaviorHook 会把这次 statusBarAppearance(true) 改写成 false，岛纹丝不动。
            val ok = runCatching {
                TempHideOverride.allow { direct.invoke(controller, hide) }
            }.onFailure { error ->
                logWarn(module, "${ScreenshotContract.HIDE_METHOD}($hide) failed: ${error.message}")
            }.isSuccess
            if (ok) return true
        }
        val stateMethod = stateHideMethod ?: return false
        val state = runCatching { getWindowStateMethod?.invoke(controller) }.getOrNull() ?: return false
        return runCatching {
            TempHideOverride.allow { stateMethod.invoke(state, hide) }
        }.onFailure { error ->
            logWarn(module, "${ScreenshotContract.STATE_HIDE_METHOD}($hide) failed: ${error.message}")
        }.isSuccess
    }

    /** 读 `windowState.statusBarDisappearance.value`，读不到返回 null。 */
    private fun readStatusBarHidden(controller: Any): Boolean? {
        val state = runCatching { getWindowStateMethod?.invoke(controller) }.getOrNull() ?: return null
        val getter = getStatusBarDisappearanceMethod ?: return null
        val flow = runCatching { getter.invoke(state) }.getOrNull() ?: return null
        val getValue = flowGetValueMethod ?: runCatching {
            flow.javaClass.getMethod("getValue").also { flowGetValueMethod = it }
        }.getOrNull() ?: return null
        return runCatching { getValue.invoke(flow) as? Boolean }.getOrNull()
    }
}
