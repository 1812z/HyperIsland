package io.github.hyperisland.xposed.hook.Screenshot

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Message
import io.github.hyperisland.screenshot.ScreenshotContract
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.ref.WeakReference
import kotlin.concurrent.thread

/**
 * 截屏进程侧的“截屏进行中”信号源。
 *
 * 小米截屏由 `com.miui.screenshot` 的 `TakeScreenshotService` 承接：SystemUI 通过 Messenger
 * 把截图请求投递给它，真正抓屏发生在后续流程中。这里在请求到达时立刻广播“开始”，
 * 在服务解绑 / 销毁时广播“结束”，让 SystemUI 侧的 ScreenshotTempHideHook 有机会
 * 走官方链路临时隐藏超级岛。
 *
 * 未开启时直接返回，不注入任何 Hook；开关在本进程内被打开时由 [onConfigChanged] 补装。
 *
 * ⚠️ 已核对的版本差异：
 * - 1.6.2.x：`TakeScreenshotService` 内部有一个 `Handler` 子类承接 `handleMessage`，
 *   另有无参 `s()` 表示会话结束（releaseFrameworkCall）。
 * - 1.6.3.40 / 1.6.3.41：内部不再有 `Handler` 子类，改成
 *   `new Handler(getMainLooper(), new Handler.Callback() { ... })`，匿名实现类会被 R8
 *   改名为 `com.miui.screenshot.n0` 之类的合成类；会话结束改走 `onUnbind` / `onDestroy`。
 *   ⚠️ 1.6.3.41 里的无参 `s()` 是 `unregisterReceiver`，**不是**会话结束，绝不能挂。
 * ⇒ 入口同时探测 `Handler` 子类与 `Handler.Callback` 实现，并额外兜一层服务自身的
 *   `(Message) -> boolean` 请求方法；真正保底的仍是 [hookCaptureGate] —— 它挂在框架层
 *   抓屏入口上，即使请求入口没探到，也能在抓屏那一刻自己补发“开始”。
 */
object ScreenshotSignalHook : BaseHook() {

    private const val TAG = "HyperIsland[ScreenshotSignal]"
    private const val SYSTEMUI_PACKAGE = "com.android.systemui"

    private val warnedKeys = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var appContext: WeakReference<Context>? = null

    @Volatile
    private var hooked = false

    @Volatile
    private var enabled = false

    @Volatile
    private var moduleRef: XposedModule? = null

    @Volatile
    private var loadParam: PackageLoadedParam? = null

    /** 最近一次“开始”的时间戳，用于给隐藏留出最小提前量。 */
    @Volatile
    private var lastRequestAtMs = 0L

    /** 抓屏序号：只有最后一次抓屏对应的自动恢复才真正生效。 */
    @Volatile
    private var captureSeq = 0

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        if (param.packageName != ScreenshotContract.TARGET_PACKAGE) return
        moduleRef = module
        loadParam = param
        loadConfig()
        // 未开启时直接返回：不注入任何 Hook。开启后由 onConfigChanged 补装。
        if (!enabled) return
        install(module, param)
    }

    override fun onConfigChanged() {
        val wasEnabled = enabled
        loadConfig()
        // 进程生命周期内开关被打开：当场补装，不用等下次截屏进程重启。
        if (!wasEnabled && enabled) {
            val module = moduleRef ?: return
            install(module, loadParam)
        }
    }

    private fun install(module: XposedModule, param: PackageLoadedParam?) {
        if (hooked) return
        val loaded = param ?: return
        hooked = true
        hookApplicationOnCreate(module, loaded)
        hookTakeScreenshotService(module, loaded.defaultClassLoader)
        hookCaptureGate(module)
    }

    private fun loadConfig() {
        enabled = ConfigManager.getBoolean(ScreenshotContract.PREF_ENABLED, false)
    }

    /**
     * 真正抓屏前的同步闸门 —— 本功能唯一的保底链路。
     *
     * 广播是异步的：如果“开始”还没被 SystemUI 处理完就抓屏，岛仍会被拍进去。这里挂在
     * 框架层的抓屏入口（`IWindowManager$Stub$Proxy.captureDisplay` / `SurfaceControl
     * .captureDisplay`）上，必要时自己补发“开始”，并补齐
     * [ScreenshotContract.MIN_CAPTURE_LEAD_MS] 的最小提前量。
     *
     * 1.6.3.41 的三条抓屏策略（`e1.b` / `e1.c` / `e1.d`）最终都会落到这两个 framework
     * 入口，无论走直接调用还是反射 invoke；`miui.util.ScreenshotUtils` 已被移除，探测不到
     * 就跳过，不影响其余路径。
     */
    private fun hookCaptureGate(module: XposedModule) {
        var count = 0
        listOf(
            "android.view.IWindowManager\$Stub\$Proxy",
            "android.view.SurfaceControl",
            // MIUI 框架自己的截图入口，走通时不会经过上面两个 framework 类。旧版本才有。
            "miui.util.ScreenshotUtils",
        ).forEach { className ->
            val clazz = runCatching { Class.forName(className) }.getOrNull()
            if (clazz == null) {
                log(module) { "capture gate class missing: $className" }
                return@forEach
            }
            clazz.declaredMethods.filter {
                it.name == "captureDisplay" || it.name == "getScreenshot"
            }.forEach { method ->
                module.hook(method).intercept { chain ->
                    val seq = ensureHideStarted(module)
                    awaitHideLeadTime(module)
                    val result = chain.proceed()
                    scheduleAutoRelease(module, seq)
                    result
                }
                count++
            }
        }
        if (count == 0) {
            warnOnce(module, "gate-empty") { "no capture gate hooked; screenshot hide may not work" }
        }
        log(module) { "capture gate hooked methods=$count" }
    }

    /**
     * 保证“开始”已经发出。
     *
     * 请求入口（Handler / Handler.Callback / 服务请求方法）探到时会先发一次；这里只在没有
     * 信号或信号已过期时补发，避免同一次会话里的多次抓屏反复广播。返回本次抓屏的序号。
     */
    private fun ensureHideStarted(module: XposedModule): Int {
        val seq = ++captureSeq
        if (!enabled) return seq
        val started = lastRequestAtMs
        val now = android.os.SystemClock.uptimeMillis()
        if (started == 0L || now - started > ScreenshotContract.SESSION_GAP_MS) {
            signal(module, true, "captureGate")
        }
        return seq
    }

    private fun awaitHideLeadTime(module: XposedModule) {
        if (!enabled) return
        val started = lastRequestAtMs
        if (started == 0L) return
        val remaining = ScreenshotContract.MIN_CAPTURE_LEAD_MS -
            (android.os.SystemClock.uptimeMillis() - started)
        if (remaining <= 0) return
        runCatching { Thread.sleep(remaining) }
        // 首次抓屏打一条可见日志：确认闸门真的生效、实际补了多少提前量。
        warnOnce(module, "gate-lead") {
            "capture gate delayed ${remaining}ms before captureDisplay"
        }
        log(module) { "capture delayed ${remaining}ms for island hide" }
    }

    /**
     * 闸门自己发起的隐藏没有对应的“会话结束”回调（1.6.3.x 尤其如此），延迟补发一次“结束”。
     *
     * 若期间又发生了新的抓屏，序号会变化，本次定时作废；SystemUI 侧还有
     * [ScreenshotContract.FALLBACK_HIDE_TIMEOUT_MS] 兜底，这里是第二层保险。
     */
    private fun scheduleAutoRelease(module: XposedModule, seq: Int) {
        if (!enabled) return
        thread(name = "hi-screenshot-release") {
            runCatching { Thread.sleep(ScreenshotContract.GATE_AUTO_RELEASE_MS) }
            if (enabled && captureSeq == seq) signal(module, false, "captureGateAutoRelease")
        }
    }

    private fun hookApplicationOnCreate(module: XposedModule, param: PackageLoadedParam) {
        runCatching {
            val method = param.defaultClassLoader
                .loadClass("android.app.Application")
                .getDeclaredMethod("onCreate")
            module.hook(method).intercept { chain ->
                val result = chain.proceed()
                val app = chain.thisObject as? Application
                if (app != null) appContext = WeakReference(app.applicationContext)
                result
            }
        }.onFailure {
            logError(module, "Application.onCreate hook failed: ${it.message}")
        }
    }

    private fun hookTakeScreenshotService(module: XposedModule, classLoader: ClassLoader) {
        val serviceClass = runCatching {
            classLoader.loadClass(ScreenshotContract.TAKE_SERVICE_CLASS)
        }.onFailure {
            logError(module, "${ScreenshotContract.TAKE_SERVICE_CLASS} not found: ${it.message}")
        }.getOrNull() ?: return

        hookServiceLifecycle(module, serviceClass)
        hookRequestEntry(module, serviceClass)
    }

    /** 服务创建 / 解绑 / 销毁：取得 Context，并在会话收尾时补发“结束”。 */
    private fun hookServiceLifecycle(module: XposedModule, serviceClass: Class<*>) {
        runCatching {
            serviceClass.getDeclaredMethod("onCreate")
        }.onFailure {
            logWarn(module, "TakeScreenshotService.onCreate not found: ${it.message}")
        }.getOrNull()?.let { method ->
            module.hook(method).intercept { chain ->
                val result = chain.proceed()
                val context = chain.thisObject as? Context
                if (context != null) appContext = WeakReference(context.applicationContext)
                result
            }
        }

        runCatching {
            serviceClass.getDeclaredMethod("onDestroy")
        }.onFailure {
            logWarn(module, "TakeScreenshotService.onDestroy not found: ${it.message}")
        }.getOrNull()?.let { method ->
            module.hook(method).intercept { chain ->
                val result = chain.proceed()
                signal(module, false, "onDestroy")
                result
            }
        }

        // 解绑即一次会话结束，比 onDestroy 早得多（1.6.3.x 主要靠它）。
        serviceClass.declaredMethods
            .filter { it.name == "onUnbind" && it.parameterCount == 1 }
            .forEach { method ->
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    signal(module, false, "onUnbind")
                    result
                }
                log(module) { "hooked TakeScreenshotService.onUnbind" }
            }
    }

    /**
     * 截图请求入口：优先挂 `TakeScreenshotService` 内部的 `Handler` 子类（1.6.2.x），
     * 找不到再挂实现 `Handler.Callback` 的内部类（1.6.3.x 起）。
     *
     * 两者都探不到时退回服务自身的请求方法 `(Message) -> boolean`（1.6.3.x 里回调只是
     * 转调它）。仍然探不到也不算失败 —— 闸门 [hookCaptureGate] 会在真正抓屏前补发“开始”。
     */
    private fun hookRequestEntry(module: XposedModule, serviceClass: Class<*>) {
        // declaredClasses / declaredMethods 都可能因为内部类引用了已被裁剪的类型而抛错，
        // 这里全部按“探不到”处理，交给下一层兜底。
        val declaredClasses = runCatching { serviceClass.declaredClasses }.getOrDefault(emptyArray())
        val handlerClass = runCatching {
            declaredClasses.firstOrNull { clazz ->
                Handler::class.java.isAssignableFrom(clazz) && clazz.hasHandleMessage()
            }
        }.getOrNull()
        if (handlerClass != null) {
            hookHandleMessage(module, handlerClass)
            return
        }
        val callbacks = runCatching {
            declaredClasses.filter { clazz ->
                Handler.Callback::class.java.isAssignableFrom(clazz) && clazz.hasHandleMessage()
            }
        }.getOrDefault(emptyList())
        if (callbacks.isNotEmpty()) {
            callbacks.forEach { hookHandleMessage(module, it) }
            return
        }
        if (hookServiceRequestMethod(module, serviceClass)) return
        warnOnce(
            module,
            "entry-missing",
        ) {
            "no Handler/Handler.Callback entry inside TakeScreenshotService; " +
                "falling back to capture gate only"
        }
    }

    /** 兜底入口：服务自己处理 Message 的方法（1.6.3.x 的 `j(Message): boolean`）。 */
    private fun hookServiceRequestMethod(module: XposedModule, serviceClass: Class<*>): Boolean {
        val candidates = serviceClass.declaredMethods.filter { method ->
            !method.isSynthetic &&
                method.name != "handleMessage" &&
                method.parameterCount == 1 &&
                method.parameterTypes[0] == Message::class.java &&
                method.returnType == Boolean::class.javaPrimitiveType
        }
        if (candidates.isEmpty()) return false
        candidates.forEach { method ->
            module.hook(method).intercept { chain ->
                val what = (chain.args.getOrNull(0) as? Message)?.what
                signal(module, true, "serviceRequest(what=$what)")
                chain.proceed()
            }
            log(module) { "hooked ${serviceClass.name}.${method.name}(Message) as request entry" }
        }
        return true
    }

    private fun Class<*>.hasHandleMessage(): Boolean = declaredMethods.any { isHandleMessage(it) }

    private fun isHandleMessage(method: java.lang.reflect.Method): Boolean =
        method.name == "handleMessage" &&
            method.parameterCount == 1 &&
            method.parameterTypes[0] == Message::class.java

    private fun hookHandleMessage(module: XposedModule, clazz: Class<*>) {
        clazz.declaredMethods.filter { isHandleMessage(it) }.forEach { method ->
            module.hook(method).intercept { chain ->
                val what = (chain.args.getOrNull(0) as? Message)?.what
                signal(module, true, "handleMessage(what=$what)")
                chain.proceed()
            }
            log(module) { "hooked ${clazz.name}.handleMessage" }
        }
    }

    private fun signal(module: XposedModule, active: Boolean, source: String) {
        // 运行期关掉开关后 Hook 撤不掉，这里做最后一道短路。
        if (!enabled) return
        if (active) lastRequestAtMs = android.os.SystemClock.uptimeMillis()
        val context = appContext?.get()
        if (context == null) {
            // 拿不到 Context 就发不出去广播，而且这一步平时只有调试日志会记录，
            // 所以首次出现时用警告提示一次，便于在没有开调试日志时定位。
            warnOnce(module, "no-context") { "no context; signal dropped active=$active source=$source" }
            log(module) { "skip signal active=$active source=$source: no context" }
            return
        }
        runCatching {
            context.sendBroadcast(
                Intent(ScreenshotContract.ACTION_STATE)
                    .setPackage(SYSTEMUI_PACKAGE)
                    .putExtra(ScreenshotContract.EXTRA_ACTIVE, active)
                    .addFlags(
                        Intent.FLAG_RECEIVER_FOREGROUND or Intent.FLAG_RECEIVER_REGISTERED_ONLY,
                    ),
            )
        }.onFailure {
            logError(module, "send screenshot state failed: ${it.message}")
        }
        warnOnce(module, "signal-sent") { "broadcast sent active=$active source=$source" }
        log(module) { "screenshot state active=$active source=$source" }
    }

    /** 只输出一次的警告：始终可见，不依赖调试日志开关。 */
    private fun warnOnce(module: XposedModule, key: String, message: () -> String) {
        if (!warnedKeys.add(key)) return
        logWarn(module, message())
    }
}
