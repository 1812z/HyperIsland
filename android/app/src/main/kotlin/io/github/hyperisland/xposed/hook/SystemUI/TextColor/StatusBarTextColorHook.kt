package io.github.hyperisland.xposed.hook

import android.content.res.Resources
import android.graphics.Color
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.widget.TextView
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/** Publishes the root SystemUI status-bar tint, coalesced to at most once per display frame. */
object StatusBarTextColorHook : BaseHook() {

    private const val TAG = "HyperIsland[StatusBarTintSource]"

    private data class DispatcherFields(
        val tintAreas: Field?,
        val darkIntensity: Field?,
        val iconTint: Field?,
        val lightColor: Field?,
        val darkColor: Field?,
        val useTint: Field?,
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val frameScheduled = AtomicBoolean(false)
    private val postFreezeRefreshScheduled = AtomicBoolean(false)
    private val rawTintListeners = CopyOnWriteArrayList<(Int) -> Unit>()
    private val readableTintListeners = CopyOnWriteArrayList<(Int) -> Unit>()
    private val hookedClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    )
    private val dispatcherReceivers = Collections.synchronizedMap(WeakHashMap<Any, Any>())
    private val dispatcherFields = Collections.synchronizedMap(
        WeakHashMap<Class<*>, DispatcherFields>()
    )

    @Volatile private var activeDispatcher = WeakReference<Any>(null)
    @Volatile private var activeReceiverRegistered = false
    @Volatile private var latestTint = Color.WHITE
    @Volatile private var dispatchedTint = Color.WHITE
    @Volatile private var dispatchedReadableTint = Color.WHITE
    @Volatile private var panelWasActive = false

    @Volatile private var tintAreas: List<Rect> = emptyList()
    @Volatile private var darkIntensity = 0f
    @Volatile private var iconTint = Color.WHITE
    @Volatile private var lightColor = Color.WHITE
    @Volatile private var darkColor = Color.BLACK
    @Volatile private var useTint = true

    private val tintFrameCallback = Choreographer.FrameCallback {
        frameScheduled.set(false)
        if (IslandUiStateSource.isColorFrozen()) return@FrameCallback
        val tint = latestTint
        if (dispatchedTint != tint) {
            dispatchedTint = tint
            rawTintListeners.forEach { listener -> runCatching { listener(tint) } }
        }
        val readable = IslandUiStateSource.getReadableTint() ?: toReadableTint(tint)
        if (dispatchedReadableTint != readable) {
            dispatchedReadableTint = readable
            readableTintListeners.forEach { listener -> runCatching { listener(readable) } }
        }
        if (latestTint != tint) scheduleFrameDispatch()
    }

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        IslandUiStateSource.addStateListener(::onIslandUiStateChanged)
        hookClasses(module, param.defaultClassLoader)
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { classLoader ->
            hookClasses(module, classLoader)
        }
    }

    fun getTint(): Int = latestTint

    fun addTintListener(listener: (Int) -> Unit) {
        rawTintListeners.addIfAbsent(listener)
    }

    fun getReadableTint(): Int =
        IslandUiStateSource.getReadableTint() ?: toReadableTint(latestTint)

    fun addReadableTintListener(listener: (Int) -> Unit) {
        readableTintListeners.addIfAbsent(listener)
    }

    private fun hookClasses(module: XposedModule, classLoader: ClassLoader) {
        IslandUiStateSource.hookClasses(module, classLoader)
        if (!hookDarkIconDispatcher(module, classLoader)) {
            hookLegacyClockFallback(module, classLoader)
        }
    }

    private fun hookDarkIconDispatcher(module: XposedModule, classLoader: ClassLoader): Boolean {
        return runCatching {
            val dispatcherClass = classLoader.loadClass(DARK_ICON_DISPATCHER_IMPL_CLASS)
            if (!hookedClasses.add(dispatcherClass)) return@runCatching true

            dispatcherClass.declaredConstructors.forEach { constructor ->
                constructor.isAccessible = true
                module.hook(constructor).intercept { chain ->
                    val result = chain.proceed()
                    val displayId = chain.args.firstOrNull { it is Int } as? Int
                    if (displayId == null || displayId == 0) {
                        registerDarkReceiver(module, chain.thisObject, classLoader)
                    }
                    result
                }
            }

            // Complete fallback: this also fires when only tintAreas changes.
            dispatcherClass.declaredMethods
                .filter { it.name == "applyIconTint" && it.parameterCount == 0 }
                .forEach { method ->
                    method.isAccessible = true
                    module.hook(method).intercept { chain ->
                        val result = chain.proceed()
                        val dispatcher = chain.thisObject
                        if (dispatcher === activeDispatcher.get() && !activeReceiverRegistered) {
                            captureDispatcherFields(dispatcher)
                        }
                        result
                    }
                }
            log(module, "hooked DarkIconDispatcherImpl root tint source")
            true
        }.getOrElse { error ->
            if (error !is ClassNotFoundException) {
                logError(module, "failed to hook root tint source: ${error.message}")
            }
            false
        }
    }

    private fun registerDarkReceiver(
        module: XposedModule,
        dispatcher: Any,
        classLoader: ClassLoader,
    ) {
        if (dispatcherReceivers.containsKey(dispatcher)) return
        activeDispatcher = WeakReference(dispatcher)
        activeReceiverRegistered = false
        runCatching {
            val receiverClass = classLoader.loadClass(DARK_RECEIVER_CLASS)
            val receiver = Proxy.newProxyInstance(
                receiverClass.classLoader,
                arrayOf(receiverClass),
            ) { proxy, method, args ->
                when (method.name) {
                    "onDarkChanged" -> {
                        updateDarkState(args)
                        null
                    }
                    "onLightDarkTintChanged" -> {
                        lightColor = args?.getOrNull(0) as? Int ?: lightColor
                        darkColor = args?.getOrNull(1) as? Int ?: darkColor
                        useTint = args?.getOrNull(2) as? Boolean ?: useTint
                        publishEffectiveTint()
                        null
                    }
                    "onDarkChangedWithContrast" -> null
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.getOrNull(0)
                    "toString" -> "HyperIslandDarkReceiver"
                    else -> null
                }
            }
            val addReceiver = dispatcher.javaClass.methods.firstOrNull { method ->
                method.name == "addDarkReceiver" && method.parameterCount == 1 &&
                    method.parameterTypes[0].isAssignableFrom(receiverClass)
            } ?: dispatcher.javaClass.declaredMethods.first { method ->
                method.name == "addDarkReceiver" && method.parameterCount == 1
            }
            addReceiver.isAccessible = true
            addReceiver.invoke(dispatcher, receiver)
            dispatcherReceivers[dispatcher] = receiver
            activeReceiverRegistered = true
            log(module, "registered Dynamic Island DarkReceiver")
        }.onFailure { error ->
            logError(module, "DarkReceiver registration failed, using applyIconTint: ${error.message}")
            captureDispatcherFields(dispatcher)
        }
    }

    private fun updateDarkState(args: Array<out Any?>?) {
        tintAreas = asRectList(args?.getOrNull(0))
        darkIntensity = args?.getOrNull(1) as? Float ?: darkIntensity
        iconTint = args?.getOrNull(2) as? Int ?: iconTint
        publishEffectiveTint()
    }

    private fun captureDispatcherFields(dispatcher: Any) {
        activeDispatcher = WeakReference(dispatcher)
        val fields = dispatcherFields.getOrPut(dispatcher.javaClass) {
            DispatcherFields(
                findField(dispatcher.javaClass, "mTintAreas"),
                findField(dispatcher.javaClass, "mDarkIntensity"),
                findField(dispatcher.javaClass, "mIconTint"),
                findField(dispatcher.javaClass, "mLightModeIconColorSingleTone"),
                findField(dispatcher.javaClass, "mDarkModeIconColorSingleTone"),
                findField(dispatcher.javaClass, "mUseTint"),
            )
        }
        runCatching {
            tintAreas = asRectList(fields.tintAreas?.get(dispatcher), tintAreas)
            darkIntensity = fields.darkIntensity?.getFloat(dispatcher) ?: darkIntensity
            iconTint = fields.iconTint?.getInt(dispatcher) ?: iconTint
            lightColor = fields.lightColor?.getInt(dispatcher) ?: lightColor
            darkColor = fields.darkColor?.getInt(dispatcher) ?: darkColor
            useTint = fields.useTint?.getBoolean(dispatcher) ?: useTint
            publishEffectiveTint()
        }
    }

    private fun findField(clazz: Class<*>, name: String): Field? =
        runCatching { clazz.getDeclaredField(name).apply { isAccessible = true } }.getOrNull()

    @Suppress("UNCHECKED_CAST")
    private fun asRectList(value: Any?, fallback: List<Rect> = emptyList()): List<Rect> =
        (value as? List<*>)?.let { it as List<Rect> } ?: fallback

    private fun publishEffectiveTint() {
        if (IslandUiStateSource.isPanelInteractionActive()) return
        val tint = when {
            !isCenterInTintAreas(tintAreas) -> if (useTint) Color.WHITE else lightColor
            useTint -> iconTint
            darkIntensity > 0f -> darkColor
            else -> lightColor
        }
        if (latestTint == tint) return
        latestTint = tint
        if (!IslandUiStateSource.isColorFrozen()) scheduleFrameDispatch()
    }

    /** Uses the fixed camera/island center, so expanding card width cannot cross an area edge. */
    private fun isCenterInTintAreas(areas: List<Rect>): Boolean {
        if (areas.isEmpty()) return true
        val centerX = Resources.getSystem().displayMetrics.widthPixels / 2
        return areas.any { area -> area.isEmpty || (area.top <= 0 && centerX in area.left until area.right) }
    }

    private fun onIslandUiStateChanged() {
        val panelActive = IslandUiStateSource.isPanelInteractionActive()
        if (panelActive && !panelWasActive) {
            // Discard a temporary shade tint that may have arrived earlier in the same frame.
            latestTint = dispatchedTint
            IslandUiStateSource.preserveReadableTint(dispatchedReadableTint)
        }
        panelWasActive = panelActive
        if (!IslandUiStateSource.isColorFrozen()) {
            schedulePostFreezeRefresh()
        }
    }

    /** Lets LightBar finish restoring its root state before reading once after a freeze. */
    private fun schedulePostFreezeRefresh() {
        if (!postFreezeRefreshScheduled.compareAndSet(false, true)) return
        val postFrame = {
            Choreographer.getInstance().postFrameCallback {
                postFreezeRefreshScheduled.set(false)
                if (IslandUiStateSource.isColorFrozen()) return@postFrameCallback
                activeDispatcher.get()?.let(::captureDispatcherFields)
                scheduleFrameDispatch()
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) postFrame() else mainHandler.post(postFrame)
    }

    /** Installed only when the upstream dispatcher class does not exist on this OS build. */
    private fun hookLegacyClockFallback(module: XposedModule, classLoader: ClassLoader) {
        LEGACY_CLOCK_CLASSES.forEach { className ->
            runCatching {
                val targetClass = classLoader.loadClass(className)
                if (!hookedClasses.add(targetClass)) return@runCatching
                targetClass.declaredMethods
                    .filter { it.name == "onDarkChanged" || it.name == "onLightDarkTintChanged" }
                    .forEach { method ->
                        module.hook(method).intercept { chain ->
                            val result = chain.proceed()
                            if (!IslandUiStateSource.isPanelInteractionActive()) {
                                (chain.thisObject as? TextView)?.currentTextColor?.let { tint ->
                                    if (latestTint != tint) {
                                        latestTint = tint
                                        scheduleFrameDispatch()
                                    }
                                }
                            }
                            result
                        }
                    }
            }
        }
    }

    private fun scheduleFrameDispatch() {
        if (!frameScheduled.compareAndSet(false, true)) return
        val postFrame = { Choreographer.getInstance().postFrameCallback(tintFrameCallback) }
        if (Looper.myLooper() == Looper.getMainLooper()) postFrame() else mainHandler.post(postFrame)
    }

    private fun toReadableTint(color: Int): Int =
        if (Color.red(color) * 299 + Color.green(color) * 587 +
            Color.blue(color) * 114 >= 128000
        ) Color.WHITE else Color.BLACK

    private val LEGACY_CLOCK_CLASSES = arrayOf(
        "com.android.systemui.statusbar.policy.Clock",
        "com.android.systemui.statusbar.views.MiuiClock",
    )

    private const val DARK_ICON_DISPATCHER_IMPL_CLASS =
        "com.android.systemui.statusbar.phone.DarkIconDispatcherImpl"
    private const val DARK_RECEIVER_CLASS =
        "com.android.systemui.plugins.DarkIconDispatcher\$DarkReceiver"
}
