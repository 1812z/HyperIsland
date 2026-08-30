package io.github.hyperisland.screenrecorder

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.os.SystemClock

/**
 * Owns the recorder session state independently from Xiaomi's notification lifecycle.
 * Target recorder processes communicate with this service through Messenger only.
 */
class ScreenRecorderControlService : Service() {
    private companion object {
        const val START_TIMEOUT_MILLIS = 20_000L
    }

    private val clients = linkedMapOf<IBinder, Messenger>()
    private val handler = Handler(Looper.getMainLooper(), ::handleMessage)
    private val messenger = Messenger(handler)

    private var state = ScreenRecorderContract.STATE_IDLE
    private var accumulatedMillis = 0L
    private var runningSinceElapsed = 0L
    private var startedAtWallClock = 0L
    private val startTimeout = Runnable {
        if (state == ScreenRecorderContract.STATE_STARTING) reportIdle()
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        handler.removeCallbacks(startTimeout)
        clients.clear()
        super.onDestroy()
    }

    private fun handleMessage(message: Message): Boolean {
        if (!isAllowedUid(message.sendingUid)) return true
        when (message.what) {
            ScreenRecorderContract.MSG_REGISTER -> registerClient(message.replyTo)
            ScreenRecorderContract.MSG_UNREGISTER -> unregisterClient(message.replyTo)
            ScreenRecorderContract.MSG_QUERY_STATE -> sendSnapshot(message.replyTo)
            ScreenRecorderContract.MSG_REPORT_STARTING -> reportStarting()
            ScreenRecorderContract.MSG_REPORT_STARTED -> reportStarted()
            ScreenRecorderContract.MSG_REPORT_IDLE -> reportIdle()
            ScreenRecorderContract.MSG_COMMAND_PAUSE -> pause()
            ScreenRecorderContract.MSG_COMMAND_RESUME -> resume()
            ScreenRecorderContract.MSG_COMMAND_STOP -> broadcastCommand(
                ScreenRecorderContract.MSG_COMMAND_STOP,
            )
            ScreenRecorderContract.MSG_COMMAND_START -> start()
        }
        return true
    }

    private fun isAllowedUid(uid: Int): Boolean {
        if (uid == applicationInfo.uid) return true
        return packageManager.getPackagesForUid(uid).orEmpty().any {
            it == ScreenRecorderContract.TARGET_PACKAGE
        }
    }

    private fun registerClient(client: Messenger?) {
        if (client == null) return
        val binder = client.binder
        clients[binder] = client
        runCatching {
            binder.linkToDeath({ handler.post { removeClient(binder) } }, 0)
        }
        sendSnapshot(client)
    }

    private fun unregisterClient(client: Messenger?) {
        if (client == null) return
        removeClient(client.binder)
    }

    private fun removeClient(binder: IBinder) {
        clients.remove(binder)
        if (clients.isEmpty()) reportIdle()
    }

    private fun reportStarting() {
        if (state != ScreenRecorderContract.STATE_IDLE) return
        state = ScreenRecorderContract.STATE_STARTING
        accumulatedMillis = 0L
        runningSinceElapsed = 0L
        startedAtWallClock = System.currentTimeMillis()
        handler.removeCallbacks(startTimeout)
        handler.postDelayed(startTimeout, START_TIMEOUT_MILLIS)
        broadcastSnapshot()
    }

    private fun start() {
        if (state != ScreenRecorderContract.STATE_IDLE) return
        reportStarting()
        broadcastCommand(ScreenRecorderContract.MSG_COMMAND_START)
    }

    private fun reportStarted() {
        val now = SystemClock.elapsedRealtime()
        if (
            state == ScreenRecorderContract.STATE_RECORDING ||
            state == ScreenRecorderContract.STATE_PAUSED
        ) {
            return
        }
        state = ScreenRecorderContract.STATE_RECORDING
        handler.removeCallbacks(startTimeout)
        accumulatedMillis = 0L
        runningSinceElapsed = now
        if (startedAtWallClock <= 0L) startedAtWallClock = System.currentTimeMillis()
        broadcastSnapshot()
    }

    private fun pause() {
        if (state != ScreenRecorderContract.STATE_RECORDING) return
        val now = SystemClock.elapsedRealtime()
        accumulatedMillis += (now - runningSinceElapsed).coerceAtLeast(0L)
        runningSinceElapsed = 0L
        state = ScreenRecorderContract.STATE_PAUSED
        broadcastCommand(ScreenRecorderContract.MSG_COMMAND_PAUSE)
        broadcastSnapshot()
    }

    private fun resume() {
        if (state != ScreenRecorderContract.STATE_PAUSED) return
        runningSinceElapsed = SystemClock.elapsedRealtime()
        state = ScreenRecorderContract.STATE_RECORDING
        broadcastCommand(ScreenRecorderContract.MSG_COMMAND_RESUME)
        broadcastSnapshot()
    }

    private fun reportIdle() {
        if (state == ScreenRecorderContract.STATE_IDLE) return
        state = ScreenRecorderContract.STATE_IDLE
        handler.removeCallbacks(startTimeout)
        accumulatedMillis = 0L
        runningSinceElapsed = 0L
        startedAtWallClock = 0L
        broadcastSnapshot()
    }

    private fun currentSnapshot(): RecorderSnapshot {
        val now = SystemClock.elapsedRealtime()
        val duration = accumulatedMillis + if (
            state == ScreenRecorderContract.STATE_RECORDING && runningSinceElapsed > 0L
        ) {
            (now - runningSinceElapsed).coerceAtLeast(0L)
        } else {
            0L
        }
        return RecorderSnapshot(
            state = state,
            durationMillis = duration,
            snapshotElapsedRealtime = now,
            startedAtWallClock = startedAtWallClock,
        )
    }

    private fun sendSnapshot(client: Messenger?) {
        if (client == null) return
        val message = Message.obtain(null, ScreenRecorderContract.MSG_STATE_CHANGED).apply {
            data = ScreenRecorderContract.snapshotBundle(currentSnapshot())
        }
        try {
            client.send(message)
        } catch (_: RemoteException) {
            removeClient(client.binder)
        }
    }

    private fun broadcastSnapshot() {
        clients.values.toList().forEach(::sendSnapshot)
    }

    private fun broadcastCommand(command: Int) {
        clients.values.toList().forEach { client ->
            try {
                client.send(Message.obtain(null, command))
            } catch (_: RemoteException) {
                removeClient(client.binder)
            }
        }
    }
}
