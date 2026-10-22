package io.github.hyperisland.xposed.hook

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.islanddispatch.IslandDispatcher
import io.github.hyperisland.xposed.islanddispatch.definition.IslandDispatchContract
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedInterface.HookHandle
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * 调试工具：手动触发一次系统防烧屏位移。
 *
 * 完全复用系统实现，不改动系统参数，因此位移后的状态与真机常亮 1 分钟后的状态一致：
 * `miui.systemui.dynamicisland.display.AvoidScreenBurnInHelper#updateViewForAvoidingScreenBurnIn(view, dir)`
 *   - `0` 左移 / `1` 上移 / `2` 右移 / `3` 下移，单步 = 系统 `translationStep`（默认 4px）
 *   - `-1` 重置（派发 AvoidScreenReset 事件），`5` 淡化
 * 该方法内部只对 SmallIsland / BigIsland 状态生效，展开态调用无副作用。
 *
 * 一次点击走 5 步（约 20px），方向按 左 → 上 → 右 → 下 循环，点满四次即回到原位。
 *
 * 总开关 `pref_debug_mode`（调试工具页顶部）关闭时**一个 Hook 都不注册**：
 *   - 进程启动时关闭：`onInit` 直接返回，零注入；
 *   - 运行中关闭：`onConfigChanged` 里把手上的 [HookHandle] 全部 `unhook()` 并清空缓存；
 *   - 运行中打开：重新按当前已就绪的 ClassLoader 挂载，无需重启系统界面。
 */
object IslandBurnInDebugHook : BaseHook() {

    private const val TAG = "HyperIsland[BurnInDebug]"
    private const val CONTENT_VIEW_CLASS =
        "miui.systemui.dynamicisland.window.content.DynamicIslandContentView"
    private const val HELPER_CLASS =
        "miui.systemui.dynamicisland.display.AvoidScreenBurnInHelper"
    private const val SHIFT_METHOD = "updateViewForAvoidingScreenBurnIn"

    private const val STEPS_PER_TAP = 5
    private const val DIRECTION_COUNT = 4
    private const val MAX_CONTENT_VIEWS = 8

    private val mainHandler = Handler(Looper.getMainLooper())
    private val contentViews = mutableListOf<WeakReference<View>>()
    private val hookedContentLoaders = ConcurrentHashMap.newKeySet<Int>()
    private val hookedHelperLoaders = ConcurrentHashMap.newKeySet<Int>()
    private val handles = mutableListOf<HookHandle>()

    @Volatile private var moduleRef: XposedModule? = null
    @Volatile private var baseClassLoader: ClassLoader? = null
    @Volatile private var installed = false
    @Volatile private var receiverRegistered = false

    private var helperRef: WeakReference<Any>? = null
    private var shiftMethod: Method? = null
    private var nextDirection = 0

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        moduleRef = module
        baseClassLoader = param.defaultClassLoader
        if (!ConfigManager.isDebugModeEnabled()) {
            log(module) { "debug mode off, skip all hooks" }
            return
        }
        installHooks(module, "package-loaded")
    }

    override fun onConfigChanged() {
        val enabled = ConfigManager.isDebugModeEnabled()
        if (enabled == installed) return
        val module = moduleRef ?: return
        log(module) { "debug mode -> $enabled, rescheduling hooks" }
        mainHandler.post {
            if (enabled) {
                installHooks(module, "config-changed")
            } else {
                uninstallHooks(module)
            }
        }
    }

    // ── 装载 / 卸载 ────────────────────────────────────────────────────────────

    @Synchronized
    private fun installHooks(module: XposedModule, reason: String) {
        if (installed) return
        val loader = baseClassLoader ?: return
        installed = true
        log(module) { "installing debug hooks ($reason)" }
        HookUtils.hookDynamicClassLoaders(module, loader) { classLoader ->
            if (!installed) return@hookDynamicClassLoaders
            hookContentViews(module, classLoader)
            hookAvoidScreenBurnInHelper(module, classLoader)
        }
        // 进程启动时 Application 早已创建，无法再等 onCreate 回调，直接从 ActivityThread 取。
        // 取不到也不要紧：后续任一 contentView 构造时会用它的 Context 补注册。
        systemUiApplicationContext()?.let(::registerTriggerReceiver)
    }

    @Synchronized
    private fun uninstallHooks(module: XposedModule) {
        if (!installed) return
        installed = false
        val removed = synchronized(handles) { handles.toList().also { handles.clear() } }
        removed.forEach { handle -> runCatching { handle.unhook() } }
        hookedContentLoaders.clear()
        hookedHelperLoaders.clear()
        helperRef = null
        shiftMethod = null
        synchronized(contentViews) { contentViews.clear() }
        log(module) { "debug hooks removed (${removed.size} handles)" }
    }

    private fun rememberHandle(handle: HookHandle) {
        synchronized(handles) { handles.add(handle) }
    }

    // ── 目标类挂载 ────────────────────────────────────────────────────────────

    /** 记录所有 contentView 实例：系统不会主动通知我们哪个实例正在显示。 */
    private fun hookContentViews(module: XposedModule, classLoader: ClassLoader) {
        if (!HookUtils.isIslandLoaderReady(classLoader)) return
        val clId = System.identityHashCode(classLoader)
        if (!hookedContentLoaders.add(clId)) return
        runCatching {
            val clazz = classLoader.loadClass(CONTENT_VIEW_CLASS)
            clazz.declaredConstructors.forEach { constructor ->
                rememberHandle(
                    module.hook(constructor).intercept { chain ->
                        val result = chain.proceed()
                        (chain.thisObject as? View)?.let(::rememberContentView)
                        result
                    },
                )
            }
            log(module) { "hooked ${clazz.name} constructors" }
        }.onFailure {
            hookedContentLoaders.remove(clId)
            logError(module, "hook content view failed: ${it.message}")
        }
    }

    /** 记录 helper 单例与其位移方法；顺带在系统自己位移时补一次实例记录。 */
    private fun hookAvoidScreenBurnInHelper(module: XposedModule, classLoader: ClassLoader) {
        if (!HookUtils.isIslandLoaderReady(classLoader)) return
        val clId = System.identityHashCode(classLoader)
        if (!hookedHelperLoaders.add(clId)) return
        runCatching {
            val clazz = classLoader.loadClass(HELPER_CLASS)
            val shift = clazz.declaredMethods.firstOrNull {
                it.name == SHIFT_METHOD && it.parameterCount == 2
            } ?: error("$SHIFT_METHOD not found")
            runCatching { shift.isAccessible = true }
            shiftMethod = shift
            clazz.declaredConstructors.forEach { constructor ->
                rememberHandle(
                    module.hook(constructor).intercept { chain ->
                        val result = chain.proceed()
                        val helper = chain.thisObject
                        if (helper != null) helperRef = WeakReference(helper)
                        result
                    },
                )
            }
            rememberHandle(
                module.hook(shift).intercept { chain ->
                    val helper = chain.thisObject
                    if (helper != null) helperRef = WeakReference(helper)
                    (chain.args.getOrNull(0) as? View)?.let(::rememberContentView)
                    chain.proceed()
                },
            )
            log(module) { "hooked ${clazz.name}#$SHIFT_METHOD" }
        }.onFailure {
            hookedHelperLoaders.remove(clId)
            logError(module, "hook avoid-screen-burn-in failed: ${it.message}")
        }
    }

    private fun rememberContentView(view: View) {
        if (!receiverRegistered) registerTriggerReceiver(view.context)
        synchronized(contentViews) {
            contentViews.removeAll { it.get() == null }
            if (contentViews.any { it.get() === view }) return
            if (contentViews.size >= MAX_CONTENT_VIEWS) contentViews.removeAt(0)
            contentViews.add(WeakReference(view))
        }
    }

    // ── 触发广播 ──────────────────────────────────────────────────────────────

    @Synchronized
    private fun registerTriggerReceiver(context: Context) {
        if (receiverRegistered) return
        receiverRegistered = true
        val appContext = context.applicationContext ?: context
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action != IslandDispatchContract.ACTION_DEBUG_BURN_IN_SHIFT) return
                if (!installed) return
                mainHandler.post { triggerShift() }
            }
        }
        val filter = IntentFilter(IslandDispatchContract.ACTION_DEBUG_BURN_IN_SHIFT)
        val module = moduleRef
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                appContext.registerReceiver(
                    receiver,
                    filter,
                    IslandDispatcher.PERM,
                    null,
                    Context.RECEIVER_EXPORTED,
                )
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                appContext.registerReceiver(receiver, filter, IslandDispatcher.PERM, null)
            }
        }.onFailure {
            receiverRegistered = false
            module?.let { m -> logError(m, "register debug trigger failed: ${it.message}") }
        }.onSuccess {
            module?.let { m -> log(m) { "debug trigger receiver registered" } }
        }
    }

    private fun systemUiApplicationContext(): Context? = runCatching {
        val activityThread = Class.forName("android.app.ActivityThread")
        val app = activityThread.getMethod("currentApplication").invoke(null) as? Application
        app?.applicationContext
    }.getOrNull()

    private fun triggerShift() {
        val module = moduleRef
        if (!installed) return
        val helper = helperRef?.get()
        val method = shiftMethod
        if (helper == null || method == null) {
            module?.let { log(it) { "skip: avoid-screen-burn-in helper unavailable" } }
            return
        }
        val view = pickContentView()
        if (view == null) {
            module?.let { log(it) { "skip: no attached island content view" } }
            return
        }
        val direction = nextDirection
        nextDirection = (direction + 1) % DIRECTION_COUNT
        var applied = 0
        repeat(STEPS_PER_TAP) {
            if (runCatching { method.invoke(helper, view, direction) }.isSuccess) applied++
        }
        module?.let {
            log(it) {
                "manual burn-in shift dir=$direction steps=$applied/$STEPS_PER_TAP " +
                    "tx=${view.translationX} ty=${view.translationY} " +
                    "state=${stateName(view)} attached=${view.isAttachedToWindow}"
            }
        }
    }

    private fun pickContentView(): View? = synchronized(contentViews) {
        contentViews.removeAll { it.get() == null }
        val alive = contentViews.mapNotNull { it.get() }
        alive.firstOrNull { it.isAttachedToWindow && isShiftableState(it) }
            ?: alive.firstOrNull { it.isAttachedToWindow }
    }

    private fun isShiftableState(view: View): Boolean {
        val name = stateName(view)
        return name.contains("BigIsland") || name.contains("SmallIsland")
    }

    private fun stateName(view: View): String =
        runCatching { view.javaClass.getMethod("getState").invoke(view)?.javaClass?.simpleName }
            .getOrNull()
            .orEmpty()
}
