package io.github.hyperisland.screenrecorder

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger

/**
 * 跨应用录屏 API。
 *
 * 供安全中心侧边栏等被 Hook（或持有 [ScreenRecorderContract.API_PERMISSION]）的进程调用，
 * 用于读取实时录制状态并下发录制控制。仅依赖框架 API 与稳定协议，不使用任何混淆方法。
 *
 * 用法：
 * ```
 * val connection = ScreenRecorderApi.connect(context, listener)
 * connection.start(ScreenRecorderApi.StartOptions(motionPhoto = true))
 * connection.close()
 * ```
 */
object ScreenRecorderApi {

    /** 录制参数；为 null 的字段保持录屏应用原有设置。 */
    data class StartOptions(
        /** 分辨率，如 "1920*1080"；null 表示跟随旧设置。 */
        val resolution: String? = null,
        /** 声音来源：0 无声、1 麦克风、2 设备声音、3 设备+麦克风；null 跟随旧设置。 */
        val sound: Int? = null,
        /** 本次录制是否保存为动态照片；null 跟随旧设置。 */
        val motionPhoto: Boolean? = null,
    )

    interface Listener {
        /** 每次状态变化（含注册/查询后的首次快照）都会回调。 */
        fun onState(snapshot: RecorderSnapshot)

        /** 控制请求的确认结果。 */
        fun onResult(success: Boolean, error: String?)
    }

    fun connect(context: Context, listener: Listener? = null): Connection =
        Connection(context.applicationContext ?: context, listener).also { it.connect() }

    /**
     * 供具备系统权限的调用方（如安全中心）一键录制动态照片。
     * 直接拉起录屏应用，规避后台启动限制。
     */
    fun startMotionPhotoDirect(context: Context): Boolean =
        runCatching {
            context.startService(
                buildStartIntent(StartOptions(motionPhoto = true)),
            )
        }.isSuccess

    /**
     * 供具备系统权限的调用方（如安全中心）直接拉起录屏应用，规避后台启动限制。
     * 使用小米稳定 action / extras；录屏进程内 Hook 会读取参数并一键开始录制。
     */
    fun buildStartIntent(options: StartOptions = StartOptions()): Intent =
        Intent(ScreenRecorderContract.RECORDER_SERVICE_ACTION).apply {
            setPackage(ScreenRecorderContract.TARGET_PACKAGE)
            putExtra(ScreenRecorderContract.EXTRA_IS_START_IMMEDIATELY, true)
            putExtra(ScreenRecorderContract.EXTRA_CONFIRMED_START, true)
            options.resolution?.let {
                putExtra(ScreenRecorderContract.API_EXTRA_RESOLUTION, it)
            }
            options.sound?.let { putExtra(ScreenRecorderContract.API_EXTRA_SOUND, it) }
            options.motionPhoto?.let {
                putExtra(ScreenRecorderContract.API_EXTRA_MOTION_PHOTO, it)
            }
        }

    class Connection internal constructor(
        private val context: Context,
        private val listener: Listener?,
    ) : ServiceConnection {
        private val incomingHandler = Handler(Looper.getMainLooper(), ::handleIncoming)
        private val incoming = Messenger(incomingHandler)

        @Volatile
        private var remote: Messenger? = null

        fun connect() {
            val intent = Intent(ScreenRecorderContract.API_SERVICE_ACTION)
                .setPackage(ScreenRecorderContract.MODULE_PACKAGE)
            runCatching {
                context.bindService(intent, this, Context.BIND_AUTO_CREATE)
            }
        }

        fun close() {
            runCatching { context.unbindService(this) }
            remote = null
        }

        fun query() = send(ScreenRecorderContract.API_MSG_QUERY)

        fun start(options: StartOptions = StartOptions()) =
            sendControl(ScreenRecorderContract.API_OP_START, options)

        fun pause() = sendControl(ScreenRecorderContract.API_OP_PAUSE, StartOptions())

        fun resume() = sendControl(ScreenRecorderContract.API_OP_RESUME, StartOptions())

        fun stop() = sendControl(ScreenRecorderContract.API_OP_STOP, StartOptions())

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            remote = service?.let(::Messenger)
            send(ScreenRecorderContract.API_MSG_REGISTER)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            remote = null
        }

        override fun onBindingDied(name: ComponentName?) {
            remote = null
            connect()
        }

        override fun onNullBinding(name: ComponentName?) {
            remote = null
        }

        private fun sendControl(op: String, options: StartOptions) {
            val data = Bundle().apply {
                putString(ScreenRecorderContract.API_EXTRA_OP, op)
                options.resolution?.let {
                    putString(ScreenRecorderContract.API_EXTRA_RESOLUTION, it)
                }
                options.sound?.let { putInt(ScreenRecorderContract.API_EXTRA_SOUND, it) }
                options.motionPhoto?.let {
                    putBoolean(ScreenRecorderContract.API_EXTRA_MOTION_PHOTO, it)
                }
            }
            send(ScreenRecorderContract.API_MSG_CONTROL, data)
        }

        private fun send(what: Int, data: Bundle? = null) {
            val service = remote ?: return
            val message = Message.obtain(null, what).apply {
                replyTo = incoming
                if (data != null) this.data = data
            }
            runCatching { service.send(message) }
        }

        private fun handleIncoming(message: Message): Boolean {
            when (message.what) {
                ScreenRecorderContract.API_MSG_STATE -> listener?.onState(
                    ScreenRecorderContract.snapshotFrom(message.data),
                )
                ScreenRecorderContract.API_MSG_RESULT -> listener?.onResult(
                    message.data?.getBoolean(
                        ScreenRecorderContract.API_EXTRA_SUCCESS,
                        false,
                    ) ?: false,
                    message.data?.getString(ScreenRecorderContract.API_EXTRA_ERROR),
                )
            }
            return true
        }
    }
}
