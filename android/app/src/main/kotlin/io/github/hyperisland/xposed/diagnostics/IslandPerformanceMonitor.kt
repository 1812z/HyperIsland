package io.github.hyperisland.xposed.diagnostics

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.ResultReceiver
import android.view.Choreographer
import android.view.View
import io.github.hyperisland.BuildConfig
import io.github.hyperisland.data.IslandPerformanceContract as Contract
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.hyperisland.xposed.hook.SystemUI.SoftGlass.SoftGlassController
import io.github.hyperisland.xposed.islanddispatch.definition.IslandDispatchContract
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedInterface.HookHandle
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.concurrent.Executors

/** SystemUI-only bounded capture. No notification text, package extras, or file I/O in hooks. */
internal object IslandPerformanceMonitor : BaseHook(), Choreographer.FrameCallback {
    private val handler = Handler(Looper.getMainLooper())
    private val reportExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "HI-performance-report").apply { isDaemon = true }
    }
    private val lock = Any()
    private val coverage = linkedSetOf<String>()
    private val loaders = mutableListOf<WeakReference<ClassLoader>>()
    private val observerHandles = mutableListOf<HookHandle>()
    private val observedClasses = mutableSetOf<Class<*>>()
    private val views = mutableListOf<WeakReference<View>>()
    private var registered = false
    private var initialized = false
    private var moduleRef: XposedModule? = null
    private var lastSession: Session? = null
    private var previousVsync = 0L
    private var previousBudget = 0L
    private var previousVisible = false
    private val timeout = Runnable { stop("timeout") }

    @Volatile var currentSession: Session? = null
        private set

    internal class Session(val start: Long = System.nanoTime()) {
        var end = 0L
        var reason = "recording"
        var frames = 0
        var missedVsyncs = 0
        var delayedFrames = 0
        var maxGap = 0L
        var maxDelay = 0L
        var lastBudget = 0L
        var lastSamplingSnapshot = 0L
        var truncatedHooks = 0
        var observers = 0
        val frameHooks = linkedMapOf<String, Long>()
        val hooks = linkedMapOf<String, HookStats>()
        val slow = ArrayDeque<JSONObject>()
        val events = ArrayDeque<JSONObject>()
    }

    internal class HookStats {
        var calls = 0L
        var self = 0L
        var mainSelf = 0L
        var maxSelf = 0L
        var maxTotal = 0L
    }

    override fun getTag() = "HyperIsland[Performance]"

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        moduleRef = module
        initialized = true
        HookUtils.hookDynamicClassLoaders(module, param.defaultClassLoader) { loader ->
            handler.post {
                loaders.removeAll { it.get() == null }
                if (loaders.none { it.get() === loader } && loaders.size < 32) {
                    loaders.add(WeakReference(loader))
                }
                if (currentSession != null) observeLoader(loader)
            }
        }
        HookUtils.getContext(param.defaultClassLoader)?.let(::registerReceiver)
    }

    override fun onConfigChanged() {
        if (currentSession != null && !ConfigManager.isDebugModeEnabled()) {
            handler.post { stop("debug-disabled") }
        }
    }

    fun register(label: String) {
        synchronized(lock) { if (coverage.size < 2048) coverage.add(label) }
    }

    @Synchronized
    fun registerReceiver(context: Context) {
        if (!initialized || registered) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != Contract.ACTION) return
                val reply = intent.getParcelableExtra(Contract.REPLY, ResultReceiver::class.java) ?: return
                if (!ConfigManager.isDebugModeEnabled()) {
                    reply.send(1, Bundle().apply { putString(Contract.ERROR, "debug-disabled") })
                    return
                }
                val pending = goAsync()
                try {
                    when (intent.getStringExtra(Contract.COMMAND)) {
                        Contract.START -> start()
                        Contract.STOP -> stop("manual")
                        Contract.STATUS -> Unit
                        else -> error("Unknown performance command")
                    }
                    val snapshot = snapshot()
                    reportExecutor.execute {
                        try {
                            reply.send(0, Bundle().apply { putString(Contract.REPORT, snapshot.toString()) })
                        } finally {
                            pending.finish()
                        }
                    }
                } catch (error: Exception) {
                    reply.send(1, Bundle().apply { putString(Contract.ERROR, error.javaClass.simpleName) })
                    pending.finish()
                }
            }
        }
        runCatching {
            context.applicationContext.registerReceiver(
                receiver, IntentFilter(Contract.ACTION), IslandDispatchContract.PERM,
                handler, Context.RECEIVER_EXPORTED,
            )
        }.onSuccess { registered = true }
            .onFailure { moduleRef?.let { module -> logError(module, "performance receiver: ${it.message}") } }
    }

    private fun start() {
        if (currentSession != null) return
        synchronized(lock) { currentSession = Session() }
        previousVsync = 0L
        previousBudget = 0L
        previousVisible = false
        loaders.mapNotNull { it.get() }.forEach(::observeLoader)
        Choreographer.getInstance().postFrameCallback(this)
        handler.postDelayed(timeout, Contract.MAX_DURATION_MS)
        event("capture-start")
    }

    private fun stop(reason: String) {
        synchronized(lock) {
            val session = currentSession ?: return
            session.end = System.nanoTime()
            session.reason = reason
            lastSession = session
            currentSession = null
        }
        handler.removeCallbacks(timeout)
        Choreographer.getInstance().removeFrameCallback(this)
        observerHandles.forEach { runCatching { it.unhook() } }
        observerHandles.clear()
        observedClasses.clear()
        views.clear()
    }

    /** These source observers exist only for the duration of a recording. */
    private fun observeLoader(loader: ClassLoader) {
        val module = moduleRef ?: return
        val names = listOf(
            "miui.systemui.dynamicisland.window.content.DynamicIslandBaseContentView",
            "miui.systemui.dynamicisland.window.content.DynamicIslandContentView",
            "miui.systemui.dynamicisland.window.content.DynamicIslandContentFakeView",
            "miui.systemui.dynamicisland.anim.DynamicIslandAnimationDelegate",
            "miui.systemui.dynamicisland.event.DynamicIslandEventCoordinator",
        )
        names.forEach { name ->
            val clazz = runCatching { Class.forName(name, false, loader) }.getOrNull() ?: return@forEach
            if (observedClasses.size >= 64 || !observedClasses.add(clazz)) return@forEach
            val methods = runCatching { clazz.declaredMethods }.getOrDefault(emptyArray())
            val getView = methods.firstOrNull { it.name == "getView" && it.parameterCount == 0 }
            methods.filter { it.name in SOURCE_METHODS }.forEach { method ->
                runCatching {
                    method.isAccessible = true
                    observerHandles.add(module.hook(method).intercept { chain ->
                        val owner = chain.thisObject
                        val view = owner as? View ?: runCatching { getView?.invoke(owner) as? View }.getOrNull()
                        view?.let(::rememberView)
                        val important = method.name in EVENT_METHODS
                        val state = if (method.name == "updateDarkLightMode") {
                            ":${chain.args.firstOrNull()?.javaClass?.simpleName.orEmpty()}"
                        } else ""
                        if (important) event("${clazz.simpleName}.${method.name}:begin$state")
                        val start = System.nanoTime()
                        try {
                            chain.proceed()
                        } finally {
                            if (important) event("${clazz.simpleName}.${method.name}:end", System.nanoTime() - start)
                        }
                    })
                    currentSession?.observers = observerHandles.size
                }.onFailure { event("observer-unavailable:${clazz.simpleName}.${method.name}") }
            }
        }
        observeExternalBlur(module, loader)
    }

    private fun observeExternalBlur(module: XposedModule, loader: ClassLoader) {
        val compat = runCatching { Class.forName("miui.systemui.util.MiBlurCompat", false, loader) }
            .getOrNull() ?: return
        if (observedClasses.size >= 64 || !observedClasses.add(compat)) return
        runCatching { compat.declaredMethods }.getOrDefault(emptyArray())
            .filter { it.name in setOf("setMiBackgroundBlurModeCompat", "setPassWindowBlurEnabledCompat") }
            .forEach { method ->
                runCatching {
                    method.isAccessible = true
                    observerHandles.add(module.hook(method).intercept { chain ->
                        val result = chain.proceed()
                        val target = chain.args.getOrNull(0) as? View
                        val requested = chain.args.getOrNull(1)
                        if (currentSession != null && target != null && target.isShown &&
                            (requested == true || requested == 1) && !isIslandView(target)
                        ) {
                            // Record activation in the same report as the failed island. This
                            // observes other windows without changing any of their blur settings.
                            val identity = "${target.javaClass.simpleName}@${System.identityHashCode(target)}"
                            event("external-blur:${method.name}:$identity")
                            SoftGlassController.recordSamplingState("external-blur:$identity")
                        }
                        result
                    })
                    currentSession?.observers = observerHandles.size
                }.onFailure { event("observer-unavailable:${compat.simpleName}.${method.name}") }
            }
    }

    private fun isIslandView(view: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (current.javaClass.name.startsWith("miui.systemui.dynamicisland.")) return true
            current = current.parent as? View
        }
        return false
    }

    private fun rememberView(view: View) {
        if (Looper.myLooper() != Looper.getMainLooper()) return
        views.removeAll { it.get() == null }
        if (views.any { it.get() === view }) return
        if (views.size >= 16) {
            val hidden = views.indexOfFirst { it.get()?.isShown != true }
            views.removeAt(if (hidden >= 0) hidden else 0)
        }
        views.add(WeakReference(view))
    }

    override fun doFrame(frameTimeNanos: Long) {
        val session = currentSession ?: return
        val now = System.nanoTime()
        val visible = views.firstNotNullOfOrNull { ref ->
            ref.get()?.takeIf { it.isShown && it.alpha > 0.01f && it.width > 0 && it.height > 0 }
        }
        val rate = visible?.display?.refreshRate?.takeIf { it.isFinite() && it > 0 } ?: 60f
        val budget = (1_000_000_000.0 / rate).toLong()
        val delay = (now - frameTimeNanos).coerceAtLeast(0L)
        val gap = frameTimeNanos - previousVsync
        synchronized(lock) {
            if (currentSession === session && visible != null) {
                session.frames++
                session.lastBudget = budget
                session.maxDelay = maxOf(session.maxDelay, delay)
                // Do not count first visibility or refresh-rate switches as dropped frames.
                if (previousVisible && previousBudget == budget && previousVsync != 0L) {
                    val missed = PerformanceMath.missedVsyncs(gap, budget)
                    session.missedVsyncs += missed
                    session.maxGap = maxOf(session.maxGap, gap)
                    if (missed > 0 || delay > budget) {
                        session.delayedFrames++
                        appendBounded(session.slow, JSONObject()
                            .put("kind", "frame-gap").put("atMs", ms(now - session.start))
                            .put("gapMs", ms(gap)).put("delayMs", ms(delay))
                            .put("budgetMs", ms(budget)).put("missedVsyncs", missed)
                            .put("mainHookSelfMs", ms(session.frameHooks.values.sum()))
                            .put("nearbyHooks", JSONArray(session.frameHooks.entries
                                .sortedByDescending { it.value }.take(4).map {
                                    JSONObject().put("hook", it.key.take(200)).put("selfMs", ms(it.value))
                                })))
                    }
                }
            }
        }
        synchronized(lock) { session.frameHooks.clear() }
        previousVsync = frameTimeNanos
        previousBudget = budget
        previousVisible = visible != null
        if (visible != null && now - session.lastSamplingSnapshot >= 500_000_000L) {
            session.lastSamplingSnapshot = now
            SoftGlassController.recordSamplingState("capture-frame")
        }
        if (currentSession === session) Choreographer.getInstance().postFrameCallback(this)
    }

    fun recordHook(session: Session, label: String, start: Long, end: Long, downstream: Long) {
        if (currentSession !== session) return
        val total = end - start
        val self = PerformanceMath.selfNanos(total, downstream)
        val main = Looper.myLooper() == Looper.getMainLooper()
        synchronized(lock) {
            if (currentSession !== session) return
            val stats = session.hooks[label] ?: if (session.hooks.size < 192) {
                HookStats().also { session.hooks[label] = it }
            } else {
                session.truncatedHooks++
                return
            }
            stats.calls++
            stats.self += self
            if (main) {
                stats.mainSelf += self
                if (session.frameHooks.size < 192 || label in session.frameHooks) {
                    session.frameHooks[label] = (session.frameHooks[label] ?: 0L) + self
                }
            }
            stats.maxSelf = maxOf(stats.maxSelf, self)
            stats.maxTotal = maxOf(stats.maxTotal, total)
            if (self >= 1_000_000L || total >= 8_000_000L) {
                appendBounded(session.slow, JSONObject().put("kind", "hook")
                    .put("atMs", ms(start - session.start)).put("hook", label.take(200))
                    .put("selfMs", ms(self)).put("totalMs", ms(total)).put("mainThread", main))
            }
        }
    }

    fun event(name: String, duration: Long = 0L) {
        val session = currentSession ?: return
        synchronized(lock) {
            if (currentSession !== session) return
            appendBounded(session.events, JSONObject().put("atMs", ms(System.nanoTime() - session.start))
                .put("event", name.take(160)).put("durationMs", ms(duration)))
        }
    }

    fun samplingEvent(reason: String, root: View, mode: Int?, radius: Int?, pass: Boolean?, nativeState: Map<String, Any?>) {
        val session = currentSession ?: return
        synchronized(lock) {
            if (currentSession !== session) return
            appendBounded(session.events, JSONObject()
                .put("atMs", ms(System.nanoTime() - session.start)).put("event", "sampling-state")
                .put("reason", reason.take(160)).put("root", System.identityHashCode(root))
                .put("width", root.width).put("height", root.height)
                .put("attached", root.isAttachedToWindow).put("hardwareAccelerated", root.isHardwareAccelerated)
                .put("backgroundMode", mode ?: JSONObject.NULL).put("backgroundRadius", radius ?: JSONObject.NULL)
                .put("passWindowEnabled", pass ?: JSONObject.NULL)
                .put("nativeState", JSONObject().apply {
                    nativeState.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) }
                }))
        }
    }

    private fun snapshot(): JSONObject = synchronized(lock) {
        val session = currentSession ?: lastSession
        val rows = session?.hooks?.entries?.sortedByDescending { it.value.mainSelf }?.map { (label, s) ->
            JSONObject().put("hook", label.take(200)).put("calls", s.calls)
                .put("selfMs", ms(s.self)).put("mainSelfMs", ms(s.mainSelf))
                .put("maxSelfMs", ms(s.maxSelf)).put("maxTotalMs", ms(s.maxTotal))
        }.orEmpty()
        JSONObject().put("schemaVersion", 1).put("recording", currentSession != null)
            .put("version", BuildConfig.VERSION_NAME).put("pid", Process.myPid())
            .put("durationMs", session?.let { ms((it.end.takeIf { end -> end > 0 } ?: System.nanoTime()) - it.start) } ?: 0)
            .put("reason", session?.reason ?: "idle").put("frames", session?.frames ?: 0)
            .put("missedVsyncs", session?.missedVsyncs ?: 0).put("delayedFrames", session?.delayedFrames ?: 0)
            .put("maxGapMs", ms(session?.maxGap ?: 0)).put("maxDelayMs", ms(session?.maxDelay ?: 0))
            .put("budgetMs", ms(session?.lastBudget ?: 0)).put("instrumentedMethods", coverage.size)
            .put("instrumentedOwners", JSONArray(coverage.map { it.substringBefore(':') }.distinct().sorted()))
            .put("observerCount", session?.observers ?: 0).put("truncatedHooks", session?.truncatedHooks ?: 0)
            .put("hooks", JSONArray(rows)).put("slowEvents", JSONArray(session?.slow?.toList().orEmpty()))
            .put("events", JSONArray(session?.events?.toList().orEmpty()))
            .put("limits", "60s; 192 hook rows; last 96 slow events and 96 lifecycle events")
            .put("measurement", "UI vsync gaps/delay, not GPU frame time. self excludes chain.proceed; direct nested calls and asynchronous work are not exclusive attribution. Correlation does not prove causation.")
    }

    private fun appendBounded(queue: ArrayDeque<JSONObject>, value: JSONObject) {
        if (queue.size >= 96) queue.removeFirst()
        queue.addLast(value)
    }

    private fun ms(nanos: Long): Double = nanos / 1_000_000.0
    private val EVENT_METHODS = setOf(
        "updateDarkLightMode", "onTrackingFakeViewStart", "onTrackingFakeViewEnd",
        "onTrackingFakeViewReset", "updateExpandedFakeViewToReal", "setAnimating",
    )
    private val SOURCE_METHODS = EVENT_METHODS + setOf(
        "setVisibility", "onFakeViewTrackingParamsUpdated", "containerScheduleUpdate", "scheduleUpdate",
    )
}
