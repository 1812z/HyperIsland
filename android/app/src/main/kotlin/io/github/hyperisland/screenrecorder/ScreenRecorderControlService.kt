package io.github.hyperisland.screenrecorder

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
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
 * Third-party processes (e.g. Security Center) may use the cross-app API surface below.
 */
class ScreenRecorderControlService : Service() {
    private companion object {
        const val START_TIMEOUT_MILLIS = 20_000L
        const val SECURITY_CENTER_PACKAGE = "com.miui.securitycenter"
    }

    private val clients = linkedMapOf<IBinder, Messenger>()

    /** 跨应用 API 客户端（只接收状态、下发控制，不参与录屏生命周期判定）。 */
    private val apiClients = linkedMapOf<IBinder, Messenger>()
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
        apiClients.clear()
        super.onDestroy()
    }

    private fun handleMessage(message: Message): Boolean {
        when (message.what) {
            ScreenRecorderContract.API_MSG_REGISTER,
            ScreenRecorderContract.API_MSG_UNREGISTER,
            ScreenRecorderContract.API_MSG_QUERY,
            ScreenRecorderContract.API_MSG_CONTROL,
            -> {
                if (!isAllowedApiUid(message.sendingUid)) return true
                handleApiMessage(message)
                return true
            }
        }
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

    /**
     * 跨应用调用鉴权：自身、录屏进程、安全中心始终放行；
     * 其他应用需持有 [ScreenRecorderContract.API_PERMISSION]。
     */
    private fun isAllowedApiUid(uid: Int): Boolean {
        if (isAllowedUid(uid)) return true
        val packages = packageManager.getPackagesForUid(uid).orEmpty()
        if (packages.any { it == SECURITY_CENTER_PACKAGE }) return true
        return checkPermission(
            ScreenRecorderContract.API_PERMISSION,
            -1,
            uid,
        ) == PackageManager.PERMISSION_GRANTED
    }

    // ── 跨应用 API ───────────────────────────────────────────────────────────

    private fun handleApiMessage(message: Message) {
        when (message.what) {
            ScreenRecorderContract.API_MSG_REGISTER -> registerApiClient(message.replyTo)
            ScreenRecorderContract.API_MSG_UNREGISTER -> unregisterApiClient(message.replyTo)
            ScreenRecorderContract.API_MSG_QUERY -> sendApiState(message.replyTo)
            ScreenRecorderContract.API_MSG_CONTROL -> handleApiControl(message)
        }
    }

    private fun handleApiControl(message: Message) {
        val data = message.data ?: Bundle()
        val op = data.getString(ScreenRecorderContract.API_EXTRA_OP)
        var success = true
        var error: String? = null
        when (op) {
            ScreenRecorderContract.API_OP_START -> {
                if (state != ScreenRecorderContract.STATE_IDLE) {
                    success = false
                    error = "already_active"
                } else if (!start(data)) {
                    success = false
                    error = "start_failed"
                }
            }
            ScreenRecorderContract.API_OP_PAUSE -> {
                if (state != ScreenRecorderContract.STATE_RECORDING) {
                    success = false
                    error = "not_recording"
                } else {
                    pause()
                }
            }
            ScreenRecorderContract.API_OP_RESUME -> {
                if (state != ScreenRecorderContract.STATE_PAUSED) {
                    success = false
                    error = "not_paused"
                } else {
                    resume()
                }
            }
            ScreenRecorderContract.API_OP_STOP -> {
                if (state == ScreenRecorderContract.STATE_IDLE) {
                    success = false
                    error = "not_active"
                } else {
                    broadcastCommand(ScreenRecorderContract.MSG_COMMAND_STOP)
                }
            }
            else -> {
                success = false
                error = "unknown_op"
            }
        }
        sendApiResult(message.replyTo, success, error)
    }

    private fun registerApiClient(client: Messenger?) {
        if (client == null) return
        val binder = client.binder
        apiClients[binder] = client
        runCatching {
            binder.linkToDeath({ handler.post { apiClients.remove(binder) } }, 0)
        }
        sendApiState(client)
    }

    private fun unregisterApiClient(client: Messenger?) {
        if (client == null) return
        apiClients.remove(client.binder)
    }

    private fun sendApiState(client: Messenger?) {
        if (client == null) return
        val bundle = ScreenRecorderContract.snapshotBundle(currentSnapshot())
        val message = Message.obtain(null, ScreenRecorderContract.API_MSG_STATE).apply {
            data = bundle
        }
        try {
            client.send(message)
        } catch (_: RemoteException) {
            apiClients.remove(client.binder)
        }
    }

    private fun sendApiResult(client: Messenger?, success: Boolean, error: String?) {
        if (client == null) return
        val bundle = ScreenRecorderContract.snapshotBundle(currentSnapshot()).apply {
            putBoolean(ScreenRecorderContract.API_EXTRA_SUCCESS, success)
            if (error != null) putString(ScreenRecorderContract.API_EXTRA_ERROR, error)
        }
        val message = Message.obtain(null, ScreenRecorderContract.API_MSG_RESULT).apply {
            data = bundle
        }
        try {
            client.send(message)
        } catch (_: RemoteException) {
            apiClients.remove(client.binder)
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

    private fun start(options: Bundle? = null): Boolean {
        if (state != ScreenRecorderContract.STATE_IDLE) return false
        reportStarting()
        val extras = sanitizedOptions(options)
        return if (clients.isEmpty()) {
            dispatchStartToRecorder(extras)
        } else {
            broadcastCommand(ScreenRecorderContract.MSG_COMMAND_START, extras)
            true
        }
    }

    /** 仅透传已知的录制选项键，避免把协议字段带进录屏应用。 */
    private fun sanitizedOptions(options: Bundle?): Bundle? {
        if (options == null) return null
        val extras = Bundle()
        if (options.containsKey(ScreenRecorderContract.API_EXTRA_RESOLUTION)) {
            extras.putString(
                ScreenRecorderContract.API_EXTRA_RESOLUTION,
                options.getString(ScreenRecorderContract.API_EXTRA_RESOLUTION),
            )
        }
        if (options.containsKey(ScreenRecorderContract.API_EXTRA_SOUND)) {
            extras.putInt(
                ScreenRecorderContract.API_EXTRA_SOUND,
                options.getInt(ScreenRecorderContract.API_EXTRA_SOUND, 0),
            )
        }
        if (options.containsKey(ScreenRecorderContract.API_EXTRA_MOTION_PHOTO)) {
            extras.putBoolean(
                ScreenRecorderContract.API_EXTRA_MOTION_PHOTO,
                options.getBoolean(ScreenRecorderContract.API_EXTRA_MOTION_PHOTO, false),
            )
        }
        return extras.takeIf { !it.isEmpty }
    }

    /**
     * 录屏进程不在线时，由本服务直接拉起录屏应用。
     * 使用小米稳定的 action / extras，由录屏进程内的 Hook 读取参数后启动。
     */
    private fun dispatchStartToRecorder(options: Bundle?): Boolean {
        val intent = Intent(ScreenRecorderContract.RECORDER_SERVICE_ACTION).apply {
            setPackage(ScreenRecorderContract.TARGET_PACKAGE)
            putExtra(ScreenRecorderContract.EXTRA_IS_START_IMMEDIATELY, true)
            putExtra(ScreenRecorderContract.EXTRA_CONFIRMED_START, true)
            if (options != null) putExtras(options)
            if (options?.containsKey(ScreenRecorderContract.API_EXTRA_SOUND) == true) {
                val sound = options.getInt(ScreenRecorderContract.API_EXTRA_SOUND, 0)
                if (sound == 1 || sound == 3) {
                    putExtra(ScreenRecorderContract.EXTRA_NEED_CHECK_AUDIO_PERMISSION, true)
                }
            }
        }
        val started = runCatching { startService(intent) }.isSuccess
        if (!started) {
            // 冷启动失败（后台启动限制等），回退到空闲，避免状态残留。
            reportIdle()
        }
        return started
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
        apiClients.values.toList().forEach(::sendApiState)
    }

    private fun broadcastCommand(command: Int, data: Bundle? = null) {
        clients.values.toList().forEach { client ->
            try {
                client.send(Message.obtain(null, command).apply {
                    if (data != null) this.data = Bundle(data)
                })
            } catch (_: RemoteException) {
                removeClient(client.binder)
            }
        }
    }
}
