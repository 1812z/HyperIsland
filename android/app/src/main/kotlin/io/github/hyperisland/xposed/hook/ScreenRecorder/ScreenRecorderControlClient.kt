package io.github.hyperisland.xposed.hook.ScreenRecorder

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.os.SystemClock
import android.util.Log
import io.github.hyperisland.screenrecorder.RecorderSnapshot
import io.github.hyperisland.screenrecorder.ScreenRecorderContract
import java.util.IdentityHashMap
import java.util.concurrent.CopyOnWriteArraySet
import android.media.MediaMuxer

internal object ScreenRecorderControlClient {
    private const val TAG = "HyperIsland[ScreenRecorder]"

    @Volatile
    var snapshot = RecorderSnapshot()
        private set

    private val listeners = CopyOnWriteArraySet<(RecorderSnapshot) -> Unit>()
    private val pendingSnapshotCallbacks = mutableListOf<(RecorderSnapshot) -> Unit>()
    private val incomingHandler = Handler(Looper.getMainLooper(), ::handleIncoming)
    private val incoming = Messenger(incomingHandler)

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var remote: Messenger? = null

    @Volatile
    private var bindRequested = false
    private var commandHandler: ((Int) -> Unit)? = null

    @Volatile
    private var pendingStateReport: Int? = null

    @Volatile
    private var pendingCommand: Int? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            remote = service?.let(::Messenger)
            Log.w(TAG, "control: state service connected")
            pendingStateReport?.let { report ->
                pendingStateReport = null
                send(report)
            }
            send(ScreenRecorderContract.MSG_REGISTER)
            pendingCommand?.let { command ->
                pendingCommand = null
                send(command)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            remote = null
        }

        override fun onBindingDied(name: ComponentName?) {
            remote = null
            bindRequested = false
            bindIfNeeded()
        }

        override fun onNullBinding(name: ComponentName?) {
            remote = null
            bindRequested = false
        }
    }

    fun initialize(context: Context, onCommand: (Int) -> Unit) {
        appContext = context.applicationContext
        commandHandler = onCommand
        bindIfNeeded()
    }

    fun requestSnapshot(callback: (RecorderSnapshot) -> Unit) {
        synchronized(pendingSnapshotCallbacks) {
            pendingSnapshotCallbacks += callback
        }
        bindIfNeeded()
        send(ScreenRecorderContract.MSG_QUERY_STATE)
        incomingHandler.postDelayed({
            val shouldFallback = synchronized(pendingSnapshotCallbacks) {
                pendingSnapshotCallbacks.remove(callback)
            }
            if (shouldFallback) callback(snapshot)
            if (shouldFallback) {
                Log.w(TAG, "control: state query timeout, cached state=${snapshot.state}")
            }
        }, 1_200L)
    }

    fun observe(listener: (RecorderSnapshot) -> Unit): () -> Unit {
        listeners += listener
        listener(snapshot)
        return { listeners -= listener }
    }

    fun reportStarting() = send(ScreenRecorderContract.MSG_REPORT_STARTING)

    fun reportStarted() = send(ScreenRecorderContract.MSG_REPORT_STARTED)

    fun reportIdle() = send(ScreenRecorderContract.MSG_REPORT_IDLE)

    fun pause() = send(ScreenRecorderContract.MSG_COMMAND_PAUSE)

    fun resume() = send(ScreenRecorderContract.MSG_COMMAND_RESUME)

    fun stop() = send(ScreenRecorderContract.MSG_COMMAND_STOP)

    fun start() = send(ScreenRecorderContract.MSG_COMMAND_START)

    private fun bindIfNeeded() {
        val context = appContext ?: return
        if (bindRequested || remote != null) return
        bindRequested = true
        val intent = Intent().setClassName(
            ScreenRecorderContract.MODULE_PACKAGE,
            ScreenRecorderContract.CONTROL_SERVICE_CLASS,
        )
        val bound = runCatching {
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }.getOrDefault(false)
        if (!bound) {
            bindRequested = false
            Log.w(TAG, "control: state service bind failed")
        }
    }

    private fun send(what: Int) {
        val service = remote ?: run {
            if (what in ScreenRecorderContract.MSG_REPORT_STARTING..ScreenRecorderContract.MSG_REPORT_IDLE) {
                pendingStateReport = what
            }
            if (what in ScreenRecorderContract.MSG_COMMAND_PAUSE..ScreenRecorderContract.MSG_COMMAND_START) {
                pendingCommand = what
            }
            bindIfNeeded()
            return
        }
        val message = Message.obtain(null, what).apply { replyTo = incoming }
        try {
            service.send(message)
        } catch (_: RemoteException) {
            if (what in ScreenRecorderContract.MSG_REPORT_STARTING..ScreenRecorderContract.MSG_REPORT_IDLE) {
                pendingStateReport = what
            }
            if (what in ScreenRecorderContract.MSG_COMMAND_PAUSE..ScreenRecorderContract.MSG_COMMAND_START) {
                pendingCommand = what
            }
            remote = null
            bindRequested = false
            bindIfNeeded()
        }
    }

    private fun handleIncoming(message: Message): Boolean {
        when (message.what) {
            ScreenRecorderContract.MSG_STATE_CHANGED -> {
                val updated = ScreenRecorderContract.snapshotFrom(message.data)
                snapshot = updated
                listeners.forEach { it(updated) }
                val callbacks = synchronized(pendingSnapshotCallbacks) {
                    pendingSnapshotCallbacks.toList().also { pendingSnapshotCallbacks.clear() }
                }
                callbacks.forEach { it(updated) }
            }
            ScreenRecorderContract.MSG_COMMAND_PAUSE,
            ScreenRecorderContract.MSG_COMMAND_RESUME,
            ScreenRecorderContract.MSG_COMMAND_STOP,
            ScreenRecorderContract.MSG_COMMAND_START,
            -> commandHandler?.invoke(message.what)
        }
        return true
    }
}

/** Keeps pause bookkeeping local to the process that owns the active MediaMuxer. */
internal object MediaMuxerPauseGate {
    private val activeMuxers = java.util.Collections.newSetFromMap(
        IdentityHashMap<MediaMuxer, Boolean>(),
    )
    private val lastPresentationTimes = IdentityHashMap<MediaMuxer, MutableMap<Int, Long>>()
    private var paused = false
    private var pausedAtMicros = 0L
    private var totalPausedMicros = 0L

    @Synchronized
    fun onStarted(muxer: MediaMuxer) {
        if (activeMuxers.isEmpty()) resetTiming()
        activeMuxers += muxer
        lastPresentationTimes[muxer] = mutableMapOf()
    }

    @Synchronized
    fun onStopped(muxer: MediaMuxer): Boolean {
        val removed = activeMuxers.remove(muxer)
        lastPresentationTimes.remove(muxer)
        val sessionEnded = removed && activeMuxers.isEmpty()
        if (sessionEnded) resetTiming()
        return sessionEnded
    }

    @Synchronized
    fun pause(): Boolean {
        if (activeMuxers.isEmpty() || paused) return false
        paused = true
        pausedAtMicros = elapsedRealtimeMicros()
        return true
    }

    @Synchronized
    fun resume(): Boolean {
        if (!paused) return false
        totalPausedMicros += (elapsedRealtimeMicros() - pausedAtMicros).coerceAtLeast(0L)
        paused = false
        pausedAtMicros = 0L
        return true
    }

    @Synchronized
    fun shouldDrop(muxer: MediaMuxer): Boolean = paused && muxer in activeMuxers

    @Synchronized
    fun adjustedPresentationTime(
        muxer: MediaMuxer,
        trackIndex: Int,
        originalMicros: Long,
    ): Long {
        if (muxer !in activeMuxers) return originalMicros
        val adjusted = (originalMicros - totalPausedMicros).coerceAtLeast(0L)
        val trackTimes = lastPresentationTimes.getOrPut(muxer) { mutableMapOf() }
        val monotonic = maxOf(adjusted, (trackTimes[trackIndex] ?: -1L) + 1L)
        trackTimes[trackIndex] = monotonic
        return monotonic
    }

    @Synchronized
    fun reset() {
        activeMuxers.clear()
        lastPresentationTimes.clear()
        resetTiming()
    }

    private fun resetTiming() {
        paused = false
        pausedAtMicros = 0L
        totalPausedMicros = 0L
    }

    private fun elapsedRealtimeMicros(): Long = SystemClock.elapsedRealtimeNanos() / 1_000L
}
