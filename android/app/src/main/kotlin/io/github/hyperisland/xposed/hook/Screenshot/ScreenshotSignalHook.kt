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
 * 临时隐藏超级岛。
 *
 * ⚠️ 两代结构差异（已用 1.6.2.19 与 1.6.3.40 对比确认）：
 * - 1.6.2.x：`TakeScreenshotService` 内部有一个 `Handler` 子类承接 `handleMessage`，
 *   另有无参 `s()` 表示会话结束。
 * - 1.6.3.x：内部不再有 `Handler` 子类，改成
 *   `new Handler(Looper.getMainLooper(), new d(this))`，其中 `d implements Handler.Callback`；
 *   会话结束回调也换成了 `ScreenshotFinishSession` / `onUnbind`，`s()` 消失。
 * ⇒ 入口必须同时探测 `Handler` 子类与 `Handler.Callback` 实现，且不能只靠它们：
 *   真正保底的是 [hookCaptureGate] —— 它挂在框架层抓屏入口上，即使请求入口没探到，
 *   也能在抓屏那一刻自己补发“开始”。
 */
object ScreenshotSignalHook : BaseHook() {

    private const val TAG = "HyperIsland[ScreenshotSignal]"
    private const val SYSTEMUI_PACKAGE = "com.android.systemui"

    @Volatile
    private var appContext: WeakReference<Context>? = null

    @Volatile
    private var hooked = false

    /** 最近一次“开始”的时间戳，用于给隐藏留出最小提前量。 */
    @Volatile
    private var lastRequestAtMs = 0L

    /** 抓屏序号：只有最后一次抓屏对应的自动恢复才真正生效。 */
    @Volatile
    private var captureSeq = 0

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        if (param.packageName != ScreenshotContract.TARGET_PACKAGE) return
        if (hooked) return
        hookApplicationOnCreate(module, param)
        hookTakeScreenshotService(module, param.defaultClassLoader)
        hookCaptureGate(module)
        hooked = true
    }

    /**
     * 真正抓屏前的同步闸门 —— 本功能唯一的保底链路。
     *
     * 广播是异步的：如果“开始”还没被 SystemUI 处理完就抓屏，岛仍会被拍进去。这里挂在
     * 框架层的抓屏入口（`IWindowManager$Stub$Proxy.captureDisplay` / `SurfaceControl
     * .captureDisplay`）上，必要时自己补发“开始”，并补齐
     * [ScreenshotContract.MIN_CAPTURE_LEAD_MS] 的最小提前量。
     *
     * 两个入口在 1.6.3.40 上依然存在（分别对应 `e1.c` / `e1.d` 与 `e1.b` 三个抓屏策略），
     * `miui.util.ScreenshotUtils` 则在该版本被移除 —— 探测不到就跳过，不影响其余路径。
     */
    private fun hookCaptureGate(module: XposedModule) {
        var count = 0
        listOf(
            "android.view.IWindowManager\$Stub\$Proxy",
            "android.view.SurfaceControl",
            // MIUI 框架自己的截图入口，走通时不会经过上面两个 framework 类。
            "miui.util.ScreenshotUtils",
        ).forEach { className ->
            val clazz = runCatching { Class.forName(className) }.getOrNull()
            if (clazz == null) {
                logWarn(module, "capture gate class missing: $className")
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
        log(module) { "capture gate hooked methods=$count" }
    }

    /**
     * 保证“开始”已经发出。
     *
     * 请求入口（Handler / Handler.Callback）探到时会先发一次；这里只在没有信号或信号已过期
     * 时补发，避免同一次会话里的多次抓屏反复广播。返回本次抓屏的序号。
     */
    private fun ensureHideStarted(module: XposedModule): Int {
        val seq = ++captureSeq
        if (!ConfigManager.getBoolean(ScreenshotContract.PREF_ENABLED, true)) return seq
        val started = lastRequestAtMs
        val now = android.os.SystemClock.uptimeMillis()
        if (started == 0L || now - started > ScreenshotContract.SESSION_GAP_MS) {
            signal(module, true, "captureGate")
        }
        return seq
    }

    private fun awaitHideLeadTime(module: XposedModule) {
        if (!ConfigManager.getBoolean(ScreenshotContract.PREF_ENABLED, true)) return
        val started = lastRequestAtMs
        if (started == 0L) return
        val remaining = ScreenshotContract.MIN_CAPTURE_LEAD_MS -
            (android.os.SystemClock.uptimeMillis() - started)
        if (remaining <= 0) return
        runCatching { Thread.sleep(remaining) }
        log(module) { "capture delayed ${remaining}ms for island hide" }
    }

    /**
     * 闸门自己发起的隐藏没有对应的“会话结束”回调（1.6.3.x 尤其如此），延迟补发一次“结束”。
     *
     * 若期间又发生了新的抓屏，序号会变化，本次定时作废；SystemUI 侧还有
     * [ScreenshotContract.FALLBACK_HIDE_TIMEOUT_MS] 兜底，这里是第二层保险。
     */
    private fun scheduleAutoRelease(module: XposedModule, seq: Int) {
        thread(name = "hi-screenshot-release") {
            runCatching { Thread.sleep(ScreenshotContract.GATE_AUTO_RELEASE_MS) }
            if (captureSeq == seq) signal(module, false, "captureGateAutoRelease")
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

        // 1.6.2.x 的会话结束（releaseFrameworkCall），仅在存在时挂载。
        serviceClass.declaredMethods
            .filter { it.name == "s" && it.parameterCount == 0 && it.returnType == Void.TYPE }
            .forEach { method ->
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    signal(module, false, "sessionFinished")
                    result
                }
                log(module) { "hooked TakeScreenshotService.s (session finish)" }
            }
    }

    /**
     * 截图请求入口：优先挂 `TakeScreenshotService` 内部的 `Handler` 子类（1.6.2.x），
     * 找不到再挂实现 `Handler.Callback` 的内部类（1.6.3.x 起）。
     *
     * 探不到时**不算失败** —— 闸门 [hookCaptureGate] 仍会在真正抓屏前补发“开始”。
     */
    private fun hookRequestEntry(module: XposedModule, serviceClass: Class<*>) {
        val handlerClass = serviceClass.declaredClasses.firstOrNull { clazz ->
            Handler::class.java.isAssignableFrom(clazz) && clazz.hasHandleMessage()
        }
        if (handlerClass != null) {
            hookHandleMessage(module, handlerClass)
            return
        }
        val callbacks = serviceClass.declaredClasses.filter { clazz ->
            Handler.Callback::class.java.isAssignableFrom(clazz) && clazz.hasHandleMessage()
        }
        if (callbacks.isEmpty()) {
            logWarn(
                module,
                "no Handler/Handler.Callback entry inside TakeScreenshotService; " +
                    "falling back to capture gate only",
            )
            return
        }
        callbacks.forEach { hookHandleMessage(module, it) }
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
        if (active) lastRequestAtMs = android.os.SystemClock.uptimeMillis()
        val context = appContext?.get()
        if (context == null) {
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
        log(module) { "screenshot state active=$active source=$source" }
    }
}
