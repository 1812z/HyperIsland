package io.github.hyperisland.xposed.hook.ScreenRecorder

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.MediaCodec
import android.media.MediaMuxer
import android.os.Bundle
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
import java.util.WeakHashMap
import java.util.concurrent.CopyOnWriteArraySet

internal object ScreenRecorderControlClient {
    private const val TAG = "HyperIsland[ScreenRecorder]"

    @Volatile
    var snapshot = RecorderSnapshot()
        private set

    private val listeners = CopyOnWriteArraySet<(RecorderSnapshot) -> Unit>()
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
        callback(snapshot)
        bindIfNeeded()
        send(ScreenRecorderContract.MSG_QUERY_STATE)
    }

    fun observe(listener: (RecorderSnapshot) -> Unit): () -> Unit {
        listeners += listener
        listener(snapshot)
        return { listeners -= listener }
    }

    fun reportStarting() {
        publishSnapshot(
            RecorderSnapshot(
                state = ScreenRecorderContract.STATE_STARTING,
                snapshotElapsedRealtime = SystemClock.elapsedRealtime(),
                startedAtWallClock = System.currentTimeMillis(),
            ),
        )
        send(ScreenRecorderContract.MSG_REPORT_STARTING)
    }

    fun reportStarted() {
        if (
            snapshot.state != ScreenRecorderContract.STATE_RECORDING &&
            snapshot.state != ScreenRecorderContract.STATE_PAUSED
        ) {
            publishSnapshot(
                RecorderSnapshot(
                    state = ScreenRecorderContract.STATE_RECORDING,
                    snapshotElapsedRealtime = SystemClock.elapsedRealtime(),
                    startedAtWallClock = snapshot.startedAtWallClock.takeIf { it > 0L }
                        ?: System.currentTimeMillis(),
                ),
            )
        }
        send(ScreenRecorderContract.MSG_REPORT_STARTED)
    }

    fun reportIdle() {
        publishSnapshot(RecorderSnapshot())
        send(ScreenRecorderContract.MSG_REPORT_IDLE)
    }

    fun pause() {
        val now = SystemClock.elapsedRealtime()
        if (snapshot.state == ScreenRecorderContract.STATE_RECORDING) {
            publishSnapshot(
                snapshot.copy(
                    state = ScreenRecorderContract.STATE_PAUSED,
                    durationMillis = snapshot.durationAt(now),
                    snapshotElapsedRealtime = now,
                ),
            )
        }
        commandHandler?.invoke(ScreenRecorderContract.MSG_COMMAND_PAUSE)
        send(ScreenRecorderContract.MSG_COMMAND_PAUSE)
    }

    fun resume() {
        val now = SystemClock.elapsedRealtime()
        if (snapshot.state == ScreenRecorderContract.STATE_PAUSED) {
            publishSnapshot(
                snapshot.copy(
                    state = ScreenRecorderContract.STATE_RECORDING,
                    snapshotElapsedRealtime = now,
                ),
            )
        }
        commandHandler?.invoke(ScreenRecorderContract.MSG_COMMAND_RESUME)
        send(ScreenRecorderContract.MSG_COMMAND_RESUME)
    }

    fun stop() {
        commandHandler?.invoke(ScreenRecorderContract.MSG_COMMAND_STOP)
        send(ScreenRecorderContract.MSG_COMMAND_STOP)
    }

    fun start() {
        reportStarting()
        commandHandler?.invoke(ScreenRecorderContract.MSG_COMMAND_START)
    }

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
                publishSnapshot(updated)
            }
            ScreenRecorderContract.MSG_COMMAND_PAUSE,
            ScreenRecorderContract.MSG_COMMAND_RESUME,
            ScreenRecorderContract.MSG_COMMAND_STOP,
            ScreenRecorderContract.MSG_COMMAND_START,
            -> commandHandler?.invoke(message.what)
        }
        return true
    }

    private fun publishSnapshot(updated: RecorderSnapshot) {
        snapshot = updated
        listeners.forEach { it(updated) }
    }
}

/** Keeps pause bookkeeping local to the process that owns the active MediaMuxer. */
internal object MediaMuxerPauseGate {
    private val activeMuxers = java.util.Collections.newSetFromMap(
        IdentityHashMap<MediaMuxer, Boolean>(),
    )
    private val lastPresentationTimes = IdentityHashMap<MediaMuxer, MutableMap<Int, Long>>()
    private val videoTrackIndexes = IdentityHashMap<MediaMuxer, Int>()
    private val awaitingVideoKeyframes = java.util.Collections.newSetFromMap(
        IdentityHashMap<MediaMuxer, Boolean>(),
    )
    private val syncWaitStartedMicros = IdentityHashMap<MediaMuxer, Long>()
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
    fun onTrackAdded(muxer: MediaMuxer, trackIndex: Int, mime: String?) {
        if (mime?.startsWith("video/") == true) {
            videoTrackIndexes[muxer] = trackIndex
        }
    }

    @Synchronized
    fun onStopped(muxer: MediaMuxer): Boolean {
        val removed = activeMuxers.remove(muxer)
        lastPresentationTimes.remove(muxer)
        videoTrackIndexes.remove(muxer)
        awaitingVideoKeyframes.remove(muxer)
        syncWaitStartedMicros.remove(muxer)
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
    fun resume(requestSyncFrames: () -> Unit): Boolean {
        if (!paused) return false
        val now = elapsedRealtimeMicros()
        totalPausedMicros += (now - pausedAtMicros).coerceAtLeast(0L)
        paused = false
        pausedAtMicros = 0L
        activeMuxers.forEach { muxer ->
            if (videoTrackIndexes.containsKey(muxer)) {
                awaitingVideoKeyframes += muxer
                syncWaitStartedMicros[muxer] = now
            }
        }
        requestSyncFrames()
        return true
    }

    @Synchronized
    fun shouldDrop(muxer: MediaMuxer, trackIndex: Int, flags: Int): Boolean {
        if (paused && muxer in activeMuxers) return true
        if (muxer !in awaitingVideoKeyframes) return false
        val isVideoKeyframe =
            trackIndex == videoTrackIndexes[muxer] &&
                flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
        if (!isVideoKeyframe) return true

        val now = elapsedRealtimeMicros()
        val waitStarted = syncWaitStartedMicros.remove(muxer) ?: now
        totalPausedMicros += (now - waitStarted).coerceAtLeast(0L)
        awaitingVideoKeyframes.remove(muxer)
        return false
    }

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
        videoTrackIndexes.clear()
        awaitingVideoKeyframes.clear()
        syncWaitStartedMicros.clear()
        resetTiming()
    }

    private fun resetTiming() {
        paused = false
        pausedAtMicros = 0L
        totalPausedMicros = 0L
        awaitingVideoKeyframes.clear()
        syncWaitStartedMicros.clear()
    }

    private fun elapsedRealtimeMicros(): Long = SystemClock.elapsedRealtimeNanos() / 1_000L
}

internal object VideoEncoderSyncFrameRequester {
    private val encoders = WeakHashMap<MediaCodec, Boolean>()

    @Synchronized
    fun register(codec: MediaCodec) {
        encoders[codec] = false
    }

    @Synchronized
    fun setActive(codec: MediaCodec, active: Boolean) {
        if (encoders.containsKey(codec)) encoders[codec] = active
    }

    @Synchronized
    fun remove(codec: MediaCodec) {
        encoders.remove(codec)
    }

    @Synchronized
    fun requestSyncFrames(): Int {
        var requested = 0
        encoders.forEach { (codec, active) ->
            if (active) {
                runCatching {
                    codec.setParameters(Bundle().apply {
                        putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
                    })
                }.onSuccess { requested++ }
            }
        }
        return requested
    }
}
