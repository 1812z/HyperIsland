package io.github.hyperisland.xposed.hook

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import io.github.hyperisland.R
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.islanddispatch.IslandDispatcher
import io.github.hyperisland.xposed.islanddispatch.definition.IslandRequest
import io.github.hyperisland.xposed.utils.moduleContext
import org.json.JSONObject

object FaceUnlockFocusController {

    enum class FaceState {
        AUTHENTICATING,
        SUCCESS,
        FAILED,
        STOPPED,
    }

    private const val NOTIFICATION_ID = 0x48494641
    private const val TERMINAL_CANCEL_DELAY_MS = 10_000L
    private const val PREF_FIRST_FLOAT = "pref_face_unlock_island_first_float"

    private const val FACE_RECOGNITION = "face_recognition"
    private const val FACE_RECOGNITION_SMALL = "face_recognition_small"
    private const val FACE_RECOGNITION_SUCCESS = "face_recognition_success"
    private const val FACE_RECOGNITION_SUCCESS_SMALL = "face_recognition_success_small"
    private const val FACE_RECOGNITION_FAILED = "face_recognition_failed"
    private const val FACE_RECOGNITION_FAILED_SMALL = "face_recognition_failed_small"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val stateLock = Any()

    @Volatile private var currentState = FaceState.STOPPED
    @Volatile private var terminalCancel: Runnable? = null

    fun onFaceState(context: Context, state: FaceState) {
        val appContext = context.applicationContext ?: context
        val updateState = Runnable {
            synchronized(stateLock) {
                if (
                    state == currentState &&
                    state != FaceState.SUCCESS &&
                    state != FaceState.FAILED
                ) {
                    return@synchronized
                }
                when (state) {
                    FaceState.AUTHENTICATING -> {
                        cancelTerminalRemoval()
                        currentState = state
                        postFaceNotification(appContext, state)
                    }
                    FaceState.SUCCESS,
                    FaceState.FAILED -> {
                        cancelTerminalRemoval()
                        currentState = state
                        postFaceNotification(appContext, state)
                        scheduleTerminalRemoval(appContext)
                    }
                    FaceState.STOPPED -> {
                        cancelTerminalRemoval()
                        currentState = state
                        cancelNotification(appContext)
                    }
                }
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            updateState.run()
        } else {
            mainHandler.post(updateState)
        }
    }

    fun onFaceAuthenticationActivity(context: Context) {
        onFaceState(context, FaceState.AUTHENTICATING)
    }

    fun onFaceRunningStopped(context: Context) {
        onFaceState(context, FaceState.STOPPED)
    }

    private fun postFaceNotification(context: Context, state: FaceState) {
        val moduleContext = context.moduleContext()
        val remoteViews = RemoteViews(moduleContext.packageName, R.layout.focus_notification_face_unlock)
        val faceType = when (state) {
            FaceState.AUTHENTICATING -> FACE_RECOGNITION
            FaceState.SUCCESS -> FACE_RECOGNITION_SUCCESS
            FaceState.FAILED -> FACE_RECOGNITION_FAILED
            FaceState.STOPPED -> return
        }
        val firstFloat = ConfigManager.getBoolean(PREF_FIRST_FLOAT, true)
        val extras = Bundle().apply {
            putParcelable("miui.focus.rv", remoteViews)
            putParcelable("miui.focus.rv.island.expand", remoteViews)
            putInt("face_id", R.id.face_unlock_animation_container)
            putString("miui.focus.param.custom", buildFocusParam(faceType, state, firstFloat))
            putBoolean("miui.island.firstFloat", firstFloat)
            putBoolean("miui.enableFloat", true)
            putBoolean("show_notification", true)
            putBoolean("hyperisland_focus_proxy", true)
        }
        IslandDispatcher.post(
            context,
            IslandRequest(
                title = "Face unlock",
                content = "",
                notifId = NOTIFICATION_ID,
                isOngoing = state == FaceState.AUTHENTICATING,
                preserveStatusBarSmallIcon = false,
                notificationExtras = extras,
                notificationVisibility = android.app.Notification.VISIBILITY_SECRET,
                notificationOnlyAlertOnce = true,
                notificationSilent = true,
                bypassSceneBehavior = true,
            ),
        )
    }

    private fun buildFocusParam(
        faceType: String,
        state: FaceState,
        firstFloat: Boolean,
    ): String {
        val smallPic = when (state) {
            FaceState.AUTHENTICATING -> FACE_RECOGNITION_SMALL
            FaceState.SUCCESS -> FACE_RECOGNITION_SUCCESS_SMALL
            FaceState.FAILED -> FACE_RECOGNITION_FAILED_SMALL
            FaceState.STOPPED -> FACE_RECOGNITION_SMALL
        }
        val repeatCount = if (state == FaceState.AUTHENTICATING) -1 else 1
        val bigPicInfo = JSONObject()
            .put("type", 7)
            .put("pic", smallPic)
            .put("number", repeatCount)
            .put("autoplay", true)
            .put("loop", state == FaceState.AUTHENTICATING)
        val smallPicInfo = JSONObject()
            .put("type", 2)
            .put("pic", smallPic)
            .put("number", repeatCount)
            .put("autoplay", true)
            .put("loop", state == FaceState.AUTHENTICATING)
        val unlockTextInfo = JSONObject()
            .put("title", " ")
            .put("showHighlightColor", false)
        val unlockImageText = JSONObject()
            .put("type", 1)
            .put("picInfo", bigPicInfo)
            .put("textInfo", unlockTextInfo)
        val bigIslandArea = JSONObject()
            .put("imageTextInfoLeft", unlockImageText)

        val island = JSONObject()
            .put("business", FACE_RECOGNITION)
            .put("islandProperty", 0)
            .put("islandPriority", 0)
            .put("islandTimeout", if (state == FaceState.AUTHENTICATING) 60 else 2)
            .put("expandedTime", if (state == FaceState.AUTHENTICATING) 60 else 2)
            .put("bigIslandArea", bigIslandArea)
            .put("smallIslandArea", JSONObject().put("picInfo", smallPicInfo))

        return JSONObject()
            .put("scene", FACE_RECOGNITION)
            .put("face_type", faceType)
            .put("isShowNotification", true)
            .put("islandFirstFloat", firstFloat)
            .put("enableFloat", true)
            .put("updatable", true)
            .put("timeout", if (state == FaceState.AUTHENTICATING) 1 else -1)
            .put("param_island", island)
            .toString()
    }

    private fun scheduleTerminalRemoval(context: Context) {
        val runnable = Runnable {
            synchronized(stateLock) {
                cancelNotification(context)
                currentState = FaceState.STOPPED
                terminalCancel = null
            }
        }
        terminalCancel = runnable
        mainHandler.postDelayed(runnable, TERMINAL_CANCEL_DELAY_MS)
    }

    private fun cancelTerminalRemoval() {
        terminalCancel?.let(mainHandler::removeCallbacks)
        terminalCancel = null
    }

    private fun cancelNotification(context: Context) {
        IslandDispatcher.cancel(context, NOTIFICATION_ID)
    }
}
