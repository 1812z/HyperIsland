package io.github.hyperisland.screenrecorder

import android.os.Bundle

object ScreenRecorderContract {
    const val MODULE_PACKAGE = "io.github.hyperisland"
    const val TARGET_PACKAGE = "com.miui.screenrecorder"
    const val CONTROL_SERVICE_CLASS =
        "io.github.hyperisland.screenrecorder.ScreenRecorderControlService"
    const val EXTRA_CONFIRMED_START = "hyperisland_recorder_start_confirmed"
    const val EXTRA_TOGGLE_PAUSE = "hyperisland_recorder_toggle_pause"
    const val EXTRA_CONTROL_STOP = "hyperisland_recorder_control_stop"

    // ── 录屏应用稳定入口（小米自有 action / extras，非混淆标识） ─────────────
    const val RECORDER_SERVICE_ACTION = "miui.intent.screenrecorder.RECORDER_SERVICE"
    const val EXTRA_IS_START_IMMEDIATELY = "is_start_immediately"
    const val EXTRA_NEED_CHECK_AUDIO_PERMISSION = "need_check_audio_permission"
    const val EXTRA_STOP_SCREENRECORDER = "stop_screenrecorder"

    const val PREF_RESOLUTION = "miui.screenrecorder.resolution"
    const val PREF_SOUND = "miui.screenrecorder.sound"

    // ── 跨应用 API ───────────────────────────────────────────────────────────
    /** 第三方应用调用控制服务需申请的权限（normal 级，安装即授予）。 */
    const val API_PERMISSION = "io.github.hyperisland.permission.CONTROL_SCREEN_RECORDER"

    /** 第三方应用绑定控制服务时使用的 action。 */
    const val API_SERVICE_ACTION = "io.github.hyperisland.action.SCREEN_RECORDER_CONTROL"

    const val API_MSG_REGISTER = 20
    const val API_MSG_UNREGISTER = 21
    const val API_MSG_QUERY = 22
    const val API_MSG_STATE = 23
    const val API_MSG_CONTROL = 24
    const val API_MSG_RESULT = 25

    const val API_OP_START = "start"
    const val API_OP_PAUSE = "pause"
    const val API_OP_RESUME = "resume"
    const val API_OP_STOP = "stop"

    const val API_EXTRA_OP = "op"
    const val API_EXTRA_RESOLUTION = "resolution"
    const val API_EXTRA_SOUND = "sound"
    const val API_EXTRA_MOTION_PHOTO = "motion_photo"
    const val API_EXTRA_SUCCESS = "success"
    const val API_EXTRA_ERROR = "error"

    const val STATE_IDLE = 0
    const val STATE_STARTING = 1
    const val STATE_RECORDING = 2
    const val STATE_PAUSED = 3

    const val MSG_REGISTER = 1
    const val MSG_UNREGISTER = 2
    const val MSG_QUERY_STATE = 3
    const val MSG_STATE_CHANGED = 4
    const val MSG_REPORT_STARTING = 5
    const val MSG_REPORT_STARTED = 6
    const val MSG_REPORT_IDLE = 7
    const val MSG_COMMAND_PAUSE = 8
    const val MSG_COMMAND_RESUME = 9
    const val MSG_COMMAND_STOP = 10
    const val MSG_COMMAND_START = 11

    const val KEY_STATE = "state"
    const val KEY_DURATION_MILLIS = "duration_millis"
    const val KEY_SNAPSHOT_ELAPSED = "snapshot_elapsed"
    const val KEY_STARTED_AT_WALL_CLOCK = "started_at_wall_clock"

    fun snapshotFrom(bundle: Bundle?): RecorderSnapshot = RecorderSnapshot(
        state = bundle?.getInt(KEY_STATE, STATE_IDLE) ?: STATE_IDLE,
        durationMillis = bundle?.getLong(KEY_DURATION_MILLIS, 0L) ?: 0L,
        snapshotElapsedRealtime = bundle?.getLong(KEY_SNAPSHOT_ELAPSED, 0L) ?: 0L,
        startedAtWallClock = bundle?.getLong(KEY_STARTED_AT_WALL_CLOCK, 0L) ?: 0L,
    )

    fun snapshotBundle(snapshot: RecorderSnapshot) = Bundle().apply {
        putInt(KEY_STATE, snapshot.state)
        putLong(KEY_DURATION_MILLIS, snapshot.durationMillis)
        putLong(KEY_SNAPSHOT_ELAPSED, snapshot.snapshotElapsedRealtime)
        putLong(KEY_STARTED_AT_WALL_CLOCK, snapshot.startedAtWallClock)
    }
}

data class RecorderSnapshot(
    val state: Int = ScreenRecorderContract.STATE_IDLE,
    val durationMillis: Long = 0L,
    val snapshotElapsedRealtime: Long = 0L,
    val startedAtWallClock: Long = 0L,
) {
    val isSessionActive: Boolean
        get() = state != ScreenRecorderContract.STATE_IDLE

    fun durationAt(elapsedRealtime: Long): Long {
        val runningDelta = if (
            state == ScreenRecorderContract.STATE_RECORDING && snapshotElapsedRealtime > 0L
        ) {
            (elapsedRealtime - snapshotElapsedRealtime).coerceAtLeast(0L)
        } else {
            0L
        }
        return (durationMillis + runningDelta).coerceAtLeast(0L)
    }
}
