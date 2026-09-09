package io.github.hyperisland.xposed.hook.SystemUI

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.util.Log
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.animation.PathInterpolator
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Drives Xiaomi's actual unocclude leash, leaving keyguard visibility and hierarchy to the OS. */
internal object LockscreenDeviceCenterReturn {
    private const val TAG = "HyperIsland[DeviceCenterReturn]"
    private const val EXTRA = "io.github.hyperisland.extra.RETURN_CONTROLLER"
    private const val DESCRIPTOR = "io.github.hyperisland.DeviceCenterReturn"
    private const val PACKAGE = "com.milink.service"
    private val main by lazy { Handler(Looper.getMainLooper()) }
    private var moduleRef: WeakReference<XposedModule>? = null
    private var helperRef: WeakReference<Any>? = null
    private var monitorRef: WeakReference<Any>? = null
    private var listener: Any? = null
    private var endpoint: IBinder? = null
    @Volatile private var allowedUid = -1
    @Volatile private var taskId = -1
    private var ready = false
    @Volatile private var session: Session? = null
    private lateinit var positionMethod: Method
    private lateinit var finishMethod: Method
    private lateinit var resetLeashMethod: Method

    private class Session(val downTime: Long, val downX: Float, val width: Float, val task: Int) {
        var x = 0f
        var target: WeakReference<Any>? = null
        var tracker: VelocityTracker? = VelocityTracker.obtain()
        var animator: ValueAnimator? = null
        var released = false
        var settled = false
        var commit = true
        var finishReason: Any? = null
        var timeout: Runnable? = null
    }

    fun attachController(intent: Intent) {
        endpoint?.let { binder -> intent.putExtras(Bundle().apply { putBinder(EXTRA, binder) }) }
    }

    fun install(module: XposedModule, loader: ClassLoader, helperClass: Class<*>) {
        moduleRef = WeakReference(module)
        runCatching {
            positionMethod = method(helperClass, "setLeashPositionOnRtFrameCallback", 2)
            finishMethod = method(helperClass, "onAnimationFinished", 2)
            resetLeashMethod = method(helperClass, "resetAnimationLeash", 1)
            val injectorClass = loader.loadClass("com.android.keyguard.injector.KeyguardPanelViewInjector")
            val setTranslation = method(injectorClass, "setLeftViewTranslation", 2)
            val getRoot = method(helperClass, "getViewRootImpl", 0)
            module.hook(getRoot).intercept { chain ->
                val helper = chain.thisObject
                if (helper != null) {
                    helperRef = WeakReference(helper)
                    if (listener == null) runCatching { registerMonitor(loader, helper) }
                        .onFailure { errorLog("gesture monitor unavailable", it) }
                }
                chain.proceed()
            }
            module.hook(positionMethod).intercept { chain ->
                val current = session
                val target = current?.target?.get()
                val leash = target?.let { field(it.javaClass, "leash")?.get(it) }
                if (current != null && leash != null && chain.args[0] === leash) {
                    val args = chain.args.toTypedArray()
                    args[1] = current.x
                    chain.proceed(args)
                } else chain.proceed()
            }
            module.hook(setTranslation).intercept { chain ->
                // The stock closedAnim continues ticking, but must not independently move the
                // negative page or fade its keyguard components while our finger owns the leash.
                if (session?.target?.get() != null) null else chain.proceed()
            }
            module.hook(finishMethod).intercept { chain ->
                val current = session
                val reason = chain.args.getOrNull(1)
                if (current != null && reason.toString().contains("FINISH_UNOCCLUDE")) {
                    current.finishReason = reason
                    if (current.settled) main.post { complete(current) }
                    null
                } else chain.proceed()
            }
            module.hook(resetLeashMethod).intercept { chain ->
                if (session?.target?.get() === chain.args.getOrNull(0) && session != null) {
                    null
                } else chain.proceed()
            }
            ready = true
            endpoint = object : Binder() {
                override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                    if (code !in IBinder.FIRST_CALL_TRANSACTION..IBinder.FIRST_CALL_TRANSACTION + 1) {
                        return super.onTransact(code, data, reply, flags)
                    }
                    data.enforceInterface(DESCRIPTOR)
                    if (Binder.getCallingUid() != allowedUid) return false
                    if (code == IBinder.FIRST_CALL_TRANSACTION + 1) {
                        main.post { session?.let { discard(it) } }
                        reply?.writeNoException()
                        return true
                    }
                    val downTime = data.readLong()
                    val downX = data.readFloat()
                    val currentX = data.readFloat()
                    val width = data.readInt()
                    val requestedTask = data.readInt()
                    val latch = CountDownLatch(1)
                    val expired = java.util.concurrent.atomic.AtomicBoolean(false)
                    var accepted = false
                    main.post {
                        try {
                            if (!expired.get() && ready && session == null && listener != null &&
                                requestedTask == taskId && width > 0 && downX.isFinite() && currentX.isFinite()
                            ) {
                                val current = Session(downTime, downX, width.toFloat(), requestedTask)
                                current.x = (currentX - downX).coerceIn(-current.width, 0f)
                                session = current
                                // The monitor is already listening before DOWN. Pilfer this stream
                                // so neither the newly revealed lockscreen nor MiLink handles it.
                                val monitor = monitorRef?.get() ?: error("monitor was released")
                                val input = field(monitor.javaClass, "mGestureInputMonitor")?.get(monitor)
                                    ?: error("input monitor unavailable")
                                method(input.javaClass, "pilferPointers", 0).invoke(input)
                                current.timeout = Runnable {
                                    if (session === current) {
                                        current.commit = true
                                        current.x = -current.width
                                        current.settled = true
                                        applyPosition(current)
                                        complete(current, force = true)
                                    }
                                }.also { main.postDelayed(it, 4000L) }
                                accepted = true
                                debug("interactive unocclude accepted task=$requestedTask")
                            }
                        } catch (error: Throwable) {
                            session?.let(::discard)
                            errorLog("cannot begin interactive return", error)
                        } finally { latch.countDown() }
                    }
                    if (!latch.await(350, TimeUnit.MILLISECONDS)) {
                        expired.set(true)
                        main.post { session?.takeIf { it.downTime == downTime }?.let(::discard) }
                        accepted = false
                    }
                    reply?.writeNoException()
                    reply?.writeInt(if (accepted) 1 else 0)
                    return true
                }
            }
        }.onFailure { errorLog("interactive return unavailable", it) }
    }

    fun onRemoteTarget(target: Any) {
        runCatching {
            val mode = field(target.javaClass, "mode")?.getInt(target)
            val info = field(target.javaClass, "taskInfo")?.get(target) ?: return
            val id = field(info.javaClass, "taskId")?.getInt(info) ?: return
            if (mode == 0) taskId = id
            val current = session
            if (mode == 1 && current != null && current.task == id) {
                current.target = WeakReference(target)
                debug("closing leash captured task=$id")
            }
        }.onFailure { errorLog("cannot identify remote target", it) }
    }

    private fun registerMonitor(loader: ClassLoader, helper: Any) {
        val context = field(helper.javaClass, "mContext")?.get(helper) as? Context ?: return
        allowedUid = context.packageManager.getApplicationInfo(PACKAGE, 0).uid
        val clazz = loader.loadClass("com.miui.keyguard.biometrics.fod.MiuiGestureMonitor")
        val monitor = method(clazz, "getInstance", 1).invoke(null, context) ?: return
        val register = method(clazz, "registerPointerEventListener", 1)
        val iface = register.parameterTypes[0]
        val proxy = Proxy.newProxyInstance(loader, arrayOf(iface)) { self, invoked, args ->
            when (invoked.name) {
                "hashCode" -> System.identityHashCode(self)
                "equals" -> self === args?.getOrNull(0)
                "toString" -> "HyperIslandDeviceCenterReturn"
                else -> {
                    val event = args?.getOrNull(0) as? MotionEvent
                    if (event != null && session != null) {
                        val copy = MotionEvent.obtain(event)
                        main.post {
                            try { onPointer(copy) } finally { copy.recycle() }
                        }
                    }
                    null
                }
            }
        }
        register.invoke(monitor, proxy)
        monitorRef = WeakReference(monitor)
        listener = proxy // One process-lifetime monitor; proxy never captures a View or Activity.
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) session?.let { current ->
                    current.commit = true
                    complete(current, force = true)
                }
            }
        }, IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED)
    }

    private fun onPointer(event: MotionEvent) {
        val current = session ?: return
        if (event.downTime != current.downTime || current.released) return
        current.tracker?.addMovement(event)
        current.x = (event.rawX - current.downX).coerceIn(-current.width, 0f)
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> applyPosition(current)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                current.tracker?.computeCurrentVelocity(1000)
                val velocity = current.tracker?.xVelocity ?: 0f
                val density = context()?.resources?.displayMetrics?.density ?: 1f
                current.commit = event.actionMasked != MotionEvent.ACTION_CANCEL &&
                    (-current.x >= current.width * 0.28f || velocity <= -900f * density)
                current.released = true
                current.tracker?.recycle()
                current.tracker = null
                current.animator = ValueAnimator.ofFloat(current.x, if (current.commit) -current.width else 0f).apply {
                    duration = 220L
                    interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
                    addUpdateListener { current.x = it.animatedValue as Float; applyPosition(current) }
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            if (session === current) {
                                current.settled = true
                                complete(current)
                            }
                        }
                    })
                    start()
                }
            }
        }
    }

    private fun context(): Context? = helperRef?.get()?.let {
        field(it.javaClass, "mContext")?.get(it) as? Context
    }

    private fun applyPosition(current: Session) {
        if (session !== current) return
        runCatching {
            val helper = helperRef?.get() ?: return
            val target = current.target?.get() ?: return
            val leash = field(target.javaClass, "leash")?.get(target) ?: return
            positionMethod.invoke(helper, leash, current.x)
        }.onFailure { errorLog("cannot update return leash", it) }
    }

    private fun complete(current: Session, force: Boolean = false) {
        if (session !== current || (!force && current.finishReason == null)) return
        val helper = helperRef?.get()
        val target = current.target?.get()
        val reason = current.finishReason ?: finishMethod.parameterTypes[1].enumConstants
            ?.firstOrNull { it.toString() == "FINISH_UNOCCLUDE_ANIM" }
        // Bring back the existing task on cancellation; do not recreate or send a new Intent.
        if (!current.commit) runCatching {
            context()?.getSystemService(ActivityManager::class.java)?.moveTaskToFront(current.task, 0)
        }.onFailure { errorLog("cannot restore cancelled device center task", it) }
        discard(current)
        runCatching {
            if (helper != null && reason != null) {
                val callback = field(helper.javaClass, "mUnoccludeFinishedCallback")?.get(helper)
                if (callback != null) finishMethod.invoke(helper, callback, reason)
            }
        }.onFailure { errorLog("cannot finish interactive unocclude", it) }
        if (target != null) runCatching { resetLeashMethod.invoke(null, target) }
        debug("interactive return finished commit=${current.commit} timeout=$force")
    }

    private fun discard(current: Session) {
        if (session !== current) return
        session = null
        current.timeout?.let(main::removeCallbacks)
        current.animator?.cancel()
        current.tracker?.recycle()
        current.tracker = null
    }

    fun begin(activity: Activity, event: MotionEvent, downX: Float): Boolean {
        val binder = activity.intent?.extras?.getBinder(EXTRA) ?: return false
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeLong(event.downTime)
            data.writeFloat(downX)
            data.writeFloat(event.rawX)
            data.writeInt(activity.window.decorView.width)
            data.writeInt(activity.taskId)
            if (!binder.transact(IBinder.FIRST_CALL_TRANSACTION, data, reply, 0)) false else {
                reply.readException()
                (reply.readInt() == 1).also { accepted ->
                    if (!accepted) debug("return controller not ready; using system back")
                }
            }
        } catch (error: Exception) {
            errorLog("return controller rejected gesture", error)
            false
        } finally { data.recycle(); reply.recycle() }
    }

    fun abort(activity: Activity) {
        val binder = activity.intent?.extras?.getBinder(EXTRA) ?: return
        val data = Parcel.obtain()
        try {
            data.writeInterfaceToken(DESCRIPTOR)
            binder.transact(IBinder.FIRST_CALL_TRANSACTION + 1, data, null, IBinder.FLAG_ONEWAY)
        } catch (_: Exception) { } finally { data.recycle() }
    }

    private fun debug(message: String) {
        val module = moduleRef?.get()
        if (module != null) module.log(Log.INFO, TAG, message) else Log.i(TAG, message)
    }

    private fun errorLog(message: String, error: Throwable) {
        val module = moduleRef?.get()
        if (module != null) module.log(Log.ERROR, TAG, "$message: $error")
        else Log.e(TAG, message, error)
    }

    private fun method(clazz: Class<*>, name: String, count: Int): Method =
        clazz.methods.firstOrNull { it.name == name && it.parameterCount == count }
            ?.apply { isAccessible = true } ?: error("${clazz.name}.$name/$count missing")

    private fun field(clazz: Class<*>, name: String): Field? {
        var type: Class<*>? = clazz
        while (type != null) {
            try { return type.getDeclaredField(name).apply { isAccessible = true } }
            catch (_: NoSuchFieldException) { type = type.superclass }
        }
        return null
    }
}
