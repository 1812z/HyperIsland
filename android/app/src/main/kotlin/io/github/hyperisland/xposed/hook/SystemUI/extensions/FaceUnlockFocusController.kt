package io.github.hyperisland.xposed.hook.SystemUI.extensions

import android.app.KeyguardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Icon
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.widget.RemoteViews
import io.github.hyperisland.R
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.islanddispatch.IslandDispatcher
import io.github.hyperisland.xposed.islanddispatch.definition.IslandRequest
import io.github.hyperisland.xposed.utils.moduleContext
import org.json.JSONObject

object FaceUnlockFocusController {

    enum class FaceState {
        LOCKED,
        AUTHENTICATING,
        SUCCESS,
        FAILED,
        STOPPED,
    }

    private const val NOTIFICATION_ID = 0x48494641
    private const val MIN_TERMINAL_VISIBLE_MS = 800L
    private const val TERMINAL_CANCEL_DELAY_MS = 1_200L
    private const val STATIC_SUCCESS_POST_DELAY_MS = 1_150L
    private const val SCREEN_ON_RECONCILE_DELAY_MS = 250L
    private const val PREF_FIRST_FLOAT = "pref_face_unlock_island_first_float"
    private const val PREF_ANIMATION_STYLE = "pref_face_unlock_island_animation_style"
    private const val PREF_KEEP_UNTIL_KEYGUARD_HIDDEN =
        "pref_face_unlock_island_keep_until_keyguard_hidden"
    private const val ANIMATION_STYLE_LOCK = "lock"
    private const val LOCK_PICTURE_KEY_PREFIX = "miui.focus.pic_hyperisland_unlock_lock"
    private const val LOCK_FRAME_DELAY_MS = 70L
    private const val LOCK_FRAME_COUNT = 8

    private const val FACE_RECOGNITION = "face_recognition"
    private const val FACE_RECOGNITION_SMALL = "face_recognition_small"
    private const val FACE_RECOGNITION_SUCCESS = "face_recognition_success"
    private const val FACE_RECOGNITION_SUCCESS_SMALL = "face_recognition_success_small"
    private const val FACE_RECOGNITION_FAILED = "face_recognition_failed"
    private const val FACE_RECOGNITION_FAILED_SMALL = "face_recognition_failed_small"
    private const val FACE_SUCCESS_EXPAND_PICTURE = "hyperisland_face_success_expand"
    private const val FACE_SUCCESS_SMALL_PICTURE = "hyperisland_face_success_small"
    private const val STATIC_SUCCESS_EXPANDED_SECONDS = 2
    private const val PERSISTENT_TIMEOUT_SECONDS = 24 * 60 * 60

    private val mainHandler = Handler(Looper.getMainLooper())
    private val stateLock = Any()

    @Volatile private var currentState = FaceState.STOPPED
    @Volatile private var terminalCancel: Runnable? = null
    @Volatile private var staticSuccessPost: Runnable? = null
    @Volatile private var animationGeneration = 0
    @Volatile private var animationSequence = 0L
    @Volatile private var keyguardShowing = false
    @Volatile private var unlockCommitted = false
    @Volatile private var faceUnlockSucceeded = false
    @Volatile private var terminalStartedAt = 0L
    @Volatile private var screenInteractive = false
    @Volatile private var screenReconcileGeneration = 0
    @Volatile private var screenOnSettling = false
    private val lockFrameCache = HashMap<Int, Bitmap>(LOCK_FRAME_COUNT)
    @Volatile private var faceSuccessExpandBitmap: Bitmap? = null
    @Volatile private var faceSuccessSmallBitmap: Bitmap? = null
    @Volatile private var cachedModuleContext: Context? = null

    fun onFaceState(context: Context, state: FaceState) {
        val appContext = context.applicationContext ?: context
        val updateState = Runnable {
            synchronized(stateLock) {
                if (
                    unlockCommitted &&
                    state != FaceState.LOCKED
                ) {
                    val canRecoverLockedCycle =
                        isKeyguardLocked(appContext) &&
                            state == FaceState.SUCCESS
                    if (!canRecoverLockedCycle) return@synchronized
                    keyguardShowing = true
                    unlockCommitted = false
                    faceUnlockSucceeded = false
                    terminalStartedAt = 0L
                    cancelTerminalRemoval()
                    animationGeneration++
                    currentState = FaceState.LOCKED
                    if (usesLockAnimation()) {
                        postLockNotification(appContext, 0f, terminal = false)
                    }
                }
                if (
                    (currentState == FaceState.SUCCESS || currentState == FaceState.FAILED) &&
                    state != FaceState.LOCKED &&
                    state != FaceState.SUCCESS &&
                    state != FaceState.FAILED
                ) {
                    return@synchronized
                }
                if (
                    state == currentState &&
                    state != FaceState.FAILED &&
                    !(state == FaceState.LOCKED && unlockCommitted)
                ) {
                    return@synchronized
                }
                when (state) {
                    FaceState.LOCKED -> {
                        keyguardShowing = true
                        unlockCommitted = false
                        faceUnlockSucceeded = false
                        terminalStartedAt = 0L
                        cancelTerminalRemoval()
                        animationGeneration++
                        currentState = state
                        if (usesLockAnimation()) {
                            postLockNotification(appContext, 0f, terminal = false)
                        } else {
                            cancelNotification(appContext)
                        }
                    }
                    FaceState.AUTHENTICATING -> {
                        cancelTerminalRemoval()
                        faceUnlockSucceeded = false
                        if (usesLockAnimation()) {
                            return@synchronized
                        } else {
                            screenOnSettling = false
                            screenReconcileGeneration++
                            currentState = state
                            postFaceNotification(appContext, state)
                        }
                    }
                    FaceState.SUCCESS,
                    FaceState.FAILED -> {
                        cancelTerminalRemoval()
                        faceUnlockSucceeded = state == FaceState.SUCCESS
                        if (usesLockAnimation()) {
                            if (state == FaceState.SUCCESS && keyguardShowing) {
                                screenOnSettling = false
                                screenReconcileGeneration++
                                startLockTerminal(appContext, committed = false)
                                scheduleTerminalRemoval(appContext)
                            }
                            return@synchronized
                        } else {
                            screenOnSettling = false
                            screenReconcileGeneration++
                            currentState = state
                            terminalStartedAt = SystemClock.uptimeMillis()
                            postFaceNotification(appContext, state)
                            if (shouldKeepFaceSuccessUntilKeyguardHidden()) {
                                scheduleStaticFaceSuccess(appContext)
                            } else {
                                scheduleTerminalRemoval(appContext)
                            }
                        }
                    }
                    FaceState.STOPPED -> {
                        if (currentState == FaceState.SUCCESS || currentState == FaceState.FAILED) {
                            return@synchronized
                        }
                        if (usesLockAnimation()) {
                            return@synchronized
                        } else {
                            cancelTerminalRemoval()
                            animationGeneration++
                            faceUnlockSucceeded = false
                            currentState = state
                            cancelNotification(appContext)
                        }
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
        if (!usesLockAnimation()) {
            reconcileLockedCycle(context)
        }
        onFaceState(context, FaceState.AUTHENTICATING)
    }

    fun onFaceRunningStopped(context: Context) {
        onFaceState(context, FaceState.STOPPED)
    }

    fun onKeyguardLocked(context: Context) {
        val appContext = context.applicationContext ?: context
        runOnMain {
            synchronized(stateLock) {
                val actuallyLocked = isKeyguardLocked(appContext)
                if (!actuallyLocked) return@synchronized
                if (keyguardShowing && !unlockCommitted) return@synchronized
            }
            onFaceState(appContext, FaceState.LOCKED)
        }
    }

    fun initialize(context: Context, locked: Boolean, interactive: Boolean) {
        val appContext = context.applicationContext ?: context
        runOnMain {
            synchronized(stateLock) {
                screenInteractive = interactive
                screenOnSettling = false
                if (!interactive) {
                    normalizeHidden(appContext)
                    return@synchronized
                } else if (locked) {
                    if (keyguardShowing) return@synchronized
                } else {
                    normalizeHidden(appContext)
                    return@synchronized
                }
            }
            onFaceState(appContext, FaceState.LOCKED)
        }
    }

    fun onKeyguardGoingAway(context: Context) {
        val appContext = context.applicationContext ?: context
        runOnMain {
            synchronized(stateLock) {
                if (unlockCommitted) return@synchronized
                if (!isScreenInteractive(appContext)) {
                    normalizeHidden(appContext)
                    return@synchronized
                }
                if (screenOnSettling && terminalStartedAt == 0L) {
                    normalizeHidden(appContext)
                    return@synchronized
                }
                if (usesLockAnimation()) {
                    startLockTerminal(appContext, committed = true)
                    scheduleTerminalRemoval(appContext)
                } else if (currentState != FaceState.SUCCESS) {
                    unlockCommitted = true
                    cancelTerminalRemoval()
                    currentState = FaceState.STOPPED
                    cancelNotification(appContext)
                }
            }
        }
    }

    private fun startLockTerminal(context: Context, committed: Boolean) {
        if (!isScreenInteractive(context)) {
            normalizeHidden(context)
            return
        }
        if (committed) unlockCommitted = true
        if (currentState == FaceState.SUCCESS && terminalStartedAt > 0L) return
        cancelTerminalRemoval()
        currentState = FaceState.SUCCESS
        terminalStartedAt = SystemClock.uptimeMillis()
        startLockOpeningAnimation(context)
    }

    private fun reconcileLockedCycle(context: Context) {
        val appContext = context.applicationContext ?: context
        runOnMain {
            val shouldReset = synchronized(stateLock) {
                isKeyguardLocked(appContext) &&
                    (unlockCommitted || !keyguardShowing || currentState == FaceState.STOPPED)
            }
            if (shouldReset) onFaceState(appContext, FaceState.LOCKED)
        }
    }

    private fun isKeyguardLocked(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

    private fun isScreenInteractive(context: Context): Boolean =
        screenInteractive &&
            context.getSystemService(PowerManager::class.java)?.isInteractive == true

    fun onScreenOff(context: Context) {
        val appContext = context.applicationContext ?: context
        runOnMain {
            synchronized(stateLock) {
                screenInteractive = false
                screenOnSettling = false
                screenReconcileGeneration++
                normalizeHidden(appContext)
            }
        }
    }

    fun onScreenOn(context: Context) {
        val appContext = context.applicationContext ?: context
        runOnMain {
            val generation = synchronized(stateLock) {
                screenInteractive = true
                screenOnSettling = true
                screenReconcileGeneration++
                cancelNotification(appContext)
                screenReconcileGeneration
            }
            mainHandler.postDelayed({
                synchronized(stateLock) {
                    if (generation != screenReconcileGeneration ||
                        !isScreenInteractive(appContext)) {
                        return@synchronized
                    }
                    screenOnSettling = false
                    if (!isKeyguardLocked(appContext)) {
                        normalizeHidden(appContext)
                        return@synchronized
                    }
                    if (
                        keyguardShowing &&
                        currentState == FaceState.LOCKED &&
                        !unlockCommitted
                    ) {
                        return@synchronized
                    }
                    keyguardShowing = true
                    unlockCommitted = false
                    terminalStartedAt = 0L
                    currentState = FaceState.LOCKED
                    animationGeneration++
                    cancelTerminalRemoval()
                    if (usesLockAnimation()) {
                        postLockNotification(appContext, 0f, terminal = false)
                    }
                }
            }, SCREEN_ON_RECONCILE_DELAY_MS)
        }
    }

    fun onKeyguardHidden(context: Context) {
        val appContext = context.applicationContext ?: context
        runOnMain {
            var needsFallback = false
            synchronized(stateLock) {
                screenReconcileGeneration++
                screenOnSettling = false
                if (!isScreenInteractive(appContext)) {
                    normalizeHidden(appContext)
                    return@synchronized
                }
                if (shouldKeepFaceSuccessUntilKeyguardHidden()) {
                    normalizeHidden(appContext)
                } else if (!unlockCommitted && usesLockAnimation() && keyguardShowing) {
                    needsFallback = true
                } else {
                    keyguardShowing = false
                    if (terminalStartedAt > 0L) {
                        scheduleTerminalRemoval(
                            appContext,
                            (terminalStartedAt + MIN_TERMINAL_VISIBLE_MS -
                                SystemClock.uptimeMillis()).coerceAtLeast(0L),
                        )
                    } else {
                        animationGeneration++
                        currentState = FaceState.STOPPED
                        cancelNotification(appContext)
                    }
                }
            }
            if (needsFallback) {
                onKeyguardGoingAway(appContext)
                synchronized(stateLock) { keyguardShowing = false }
            }
        }
    }

    private fun postFaceNotification(context: Context, state: FaceState) {
        if (!isScreenInteractive(context)) return
        val moduleContext = getModuleContext(context)
        val remoteViews = RemoteViews(moduleContext.packageName, R.layout.focus_notification_face_unlock)
        val faceType = when (state) {
            FaceState.LOCKED -> return
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
            putString(
                "miui.focus.param.custom",
                buildFocusParam(faceType, state, firstFloat),
            )
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

    private fun postStaticFaceSuccessNotification(context: Context) {
        val moduleContext = getModuleContext(context)
        val expandBitmap = getFaceSuccessBitmap(moduleContext, small = false)
        val smallBitmap = getFaceSuccessBitmap(moduleContext, small = true)
        val remoteViews = RemoteViews(
            moduleContext.packageName,
            R.layout.focus_notification_lock_unlock,
        ).apply {
            setImageViewBitmap(R.id.lock_unlock_bitmap, expandBitmap)
        }
        val pictures = Bundle().apply {
            putParcelable(FACE_SUCCESS_EXPAND_PICTURE, Icon.createWithBitmap(smallBitmap))
            putParcelable(FACE_SUCCESS_SMALL_PICTURE, Icon.createWithBitmap(smallBitmap))
        }
        val sequence = ++animationSequence
        val extras = Bundle().apply {
            putParcelable("miui.focus.rv", remoteViews)
            putParcelable("miui.focus.rv.island.expand", remoteViews)
            putBundle("miui.focus.pics", pictures)
            putString("miui.focus.ticker", " ")
            putString(
                "miui.focus.param.custom",
                buildStaticFaceSuccessFocusParam(sequence),
            )
            putBoolean("miui.island.firstFloat", false)
            putBoolean("miui.enableFloat", false)
            putBoolean("show_notification", false)
            putBoolean("hyperisland_focus_proxy", true)
        }
        IslandDispatcher.post(
            context,
            IslandRequest(
                title = "Face unlock",
                content = "",
                notifId = NOTIFICATION_ID,
                isOngoing = true,
                preserveStatusBarSmallIcon = false,
                notificationExtras = extras,
                notificationVisibility = android.app.Notification.VISIBILITY_SECRET,
                notificationOnlyAlertOnce = true,
                notificationSilent = true,
                bypassSceneBehavior = true,
            ),
        )
    }

    private fun getFaceSuccessBitmap(context: Context, small: Boolean): Bitmap {
        val cached = if (small) faceSuccessSmallBitmap else faceSuccessExpandBitmap
        if (cached != null) return cached
        return synchronized(this) {
            val current = if (small) faceSuccessSmallBitmap else faceSuccessExpandBitmap
            if (current != null) return@synchronized current
            val resource = if (small) {
                R.drawable.face_unlock_success_small
            } else {
                R.drawable.face_unlock_success_expand
            }
            requireNotNull(BitmapFactory.decodeResource(context.resources, resource)).also {
                if (small) faceSuccessSmallBitmap = it else faceSuccessExpandBitmap = it
            }
        }
    }

    private fun buildStaticFaceSuccessFocusParam(
        sequence: Long,
    ): String {
        val expandPicInfo = JSONObject()
            .put("type", 1)
            .put("pic", FACE_SUCCESS_EXPAND_PICTURE)
        val smallPicInfo = JSONObject()
            .put("type", 1)
            .put("pic", FACE_SUCCESS_SMALL_PICTURE)
        val leftArea = JSONObject()
            .put("type", 1)
            .put("picInfo", expandPicInfo)
            .put(
                "textInfo",
                JSONObject()
                    .put("title", " ")
                    .put("showHighlightColor", false),
            )
        val island = JSONObject()
            .put("islandProperty", 0)
            .put("islandPriority", 0)
            .put("islandTimeout", Int.MAX_VALUE)
            .put("expandedTime", STATIC_SUCCESS_EXPANDED_SECONDS)
            .put(
                "bigIslandArea",
                JSONObject().put("imageTextInfoLeft", leftArea),
            )
            .put(
                "smallIslandArea",
                JSONObject().put("picInfo", smallPicInfo),
            )

        return JSONObject()
            .put("isShowNotification", false)
            .put("islandFirstFloat", false)
            .put("enableFloat", false)
            .put("updatable", true)
            .put("sequence", sequence)
            .put("timeout", PERSISTENT_TIMEOUT_SECONDS)
            .put("param_island", island)
            .toString()
    }

    private fun buildFocusParam(
        faceType: String,
        state: FaceState,
        firstFloat: Boolean,
    ): String {
        val smallPic = when (state) {
            FaceState.LOCKED -> FACE_RECOGNITION_SMALL
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

        val duration = when {
            state == FaceState.AUTHENTICATING -> 60
            else -> 2
        }
        val island = JSONObject()
            .put("business", FACE_RECOGNITION)
            .put("islandProperty", 0)
            .put("islandPriority", 0)
            .put("islandTimeout", duration)
            .put("expandedTime", duration)
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

    private fun postLockNotification(context: Context, progress: Float, terminal: Boolean) {
        if (!isScreenInteractive(context)) return
        val moduleContext = getModuleContext(context)
        val firstFloat = ConfigManager.getBoolean(PREF_FIRST_FLOAT, true)
        val keepUntilKeyguardHidden = terminal && shouldKeepFaceSuccessUntilKeyguardHidden()
        val bitmap = getLockFrame(progress.coerceIn(0f, 1f))
        val sequence = ++animationSequence
        val pictureKey = "${LOCK_PICTURE_KEY_PREFIX}_$sequence"
        val remoteViews = RemoteViews(
            moduleContext.packageName,
            R.layout.focus_notification_lock_unlock,
        ).apply {
            setImageViewBitmap(R.id.lock_unlock_bitmap, bitmap)
        }
        val pictures = Bundle().apply {
            putParcelable(pictureKey, Icon.createWithBitmap(bitmap))
        }
        val extras = Bundle().apply {
            putParcelable("miui.focus.rv", remoteViews)
            putParcelable("miui.focus.rv.island.expand", remoteViews)
            putBundle("miui.focus.pics", pictures)
            putString("miui.focus.ticker", " ")
            putString(
                "miui.focus.param.custom",
                buildLockFocusParam(
                    pictureKey,
                    sequence,
                    terminal,
                    firstFloat,
                    keepUntilKeyguardHidden,
                ),
            )
            putBoolean("miui.island.firstFloat", firstFloat)
            putBoolean("miui.enableFloat", true)
            putBoolean("show_notification", false)
            putBoolean("hyperisland_focus_proxy", true)
        }
        IslandDispatcher.post(
            context,
            IslandRequest(
                title = "Unlock",
                content = "",
                notifId = NOTIFICATION_ID,
                isOngoing = !terminal || keepUntilKeyguardHidden,
                preserveStatusBarSmallIcon = false,
                notificationExtras = extras,
                notificationVisibility = android.app.Notification.VISIBILITY_SECRET,
                notificationOnlyAlertOnce = true,
                notificationSilent = true,
                bypassSceneBehavior = true,
            ),
        )
    }

    private fun buildLockFocusParam(
        pictureKey: String,
        sequence: Long,
        terminal: Boolean,
        firstFloat: Boolean,
        keepUntilKeyguardHidden: Boolean,
    ): String {
        val picInfo = JSONObject()
            .put("type", 1)
            .put("pic", pictureKey)
        val leftArea = JSONObject()
            .put("type", 1)
            .put("picInfo", picInfo)
            .put(
                "textInfo",
                JSONObject()
                    .put("title", " ")
                    .put("showHighlightColor", false),
            )
        val duration = when {
            keepUntilKeyguardHidden -> Int.MAX_VALUE
            terminal -> 2
            else -> 60
        }
        val island = JSONObject()
            .put("islandProperty", 0)
            .put("islandPriority", 0)
            .put("islandTimeout", duration)
            .put("expandedTime", duration)
            .put(
                "bigIslandArea",
                JSONObject().put("imageTextInfoLeft", leftArea),
            )
            .put(
                "smallIslandArea",
                JSONObject().put("picInfo", picInfo),
            )

        return JSONObject()
            .put("ticker", " ")
            .put("isShowNotification", false)
            .put("islandFirstFloat", firstFloat)
            .put("enableFloat", true)
            .put("updatable", true)
            .put("sequence", sequence)
            .put(
                "timeout",
                when {
                    keepUntilKeyguardHidden -> PERSISTENT_TIMEOUT_SECONDS
                    terminal -> -1
                    else -> 1
                },
            )
            .put("param_island", island)
            .toString()
    }

    private fun startLockOpeningAnimation(context: Context) {
        val generation = ++animationGeneration
        postLockNotification(context, 0f, terminal = true)
        for (frame in 1 until LOCK_FRAME_COUNT) {
            mainHandler.postDelayed({
                synchronized(stateLock) {
                    if (generation != animationGeneration || currentState != FaceState.SUCCESS) {
                        return@synchronized
                    }
                    val raw = frame.toFloat() / (LOCK_FRAME_COUNT - 1)
                    val eased = 1f - (1f - raw) * (1f - raw)
                    postLockNotification(context, eased, terminal = true)
                }
            }, frame * LOCK_FRAME_DELAY_MS)
        }
    }

    private fun getLockFrame(progress: Float): Bitmap =
        lockFrameCache.getOrPut(progress.toBits()) { drawLockFrame(progress) }

    private fun drawLockFrame(progress: Float): Bitmap {
        val size = 192
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 13f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val bodyTop = 86f
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(48f, bodyTop, 144f, 164f), 22f, 22f, paint)
        paint.color = Color.BLACK
        canvas.drawCircle(96f, 122f, 8f, paint)
        canvas.drawRoundRect(RectF(92f, 122f, 100f, 145f), 4f, 4f, paint)

        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        val lift = 13f * progress
        canvas.save()
        canvas.rotate(42f * progress, 132f, bodyTop)
        canvas.translate(0f, -lift)
        val shackle = Path().apply {
            moveTo(132f, bodyTop + 2f)
            lineTo(132f, 67f)
            cubicTo(132f, 38f, 60f, 38f, 60f, 67f)
            lineTo(60f, bodyTop + 2f)
        }
        canvas.drawPath(shackle, paint)
        canvas.restore()
        return bitmap
    }

    private fun usesLockAnimation(): Boolean =
        ConfigManager.getString(PREF_ANIMATION_STYLE, "default") == ANIMATION_STYLE_LOCK

    private fun shouldKeepFaceSuccessUntilKeyguardHidden(): Boolean =
        faceUnlockSucceeded &&
            ConfigManager.getBoolean(PREF_KEEP_UNTIL_KEYGUARD_HIDDEN, false)

    private fun getModuleContext(context: Context): Context =
        cachedModuleContext ?: synchronized(this) {
            cachedModuleContext ?: context.moduleContext().also { cachedModuleContext = it }
        }

    private fun scheduleTerminalRemoval(context: Context) {
        scheduleTerminalRemoval(context, TERMINAL_CANCEL_DELAY_MS)
    }

    private fun scheduleTerminalRemoval(context: Context, delayMs: Long) {
        if (shouldKeepFaceSuccessUntilKeyguardHidden()) {
            cancelTerminalRemoval()
            return
        }
        cancelTerminalRemoval()
        val generation = animationGeneration
        val runnable = Runnable {
            synchronized(stateLock) {
                if (generation != animationGeneration) return@synchronized
                cancelNotification(context)
                currentState = FaceState.STOPPED
                faceUnlockSucceeded = false
                animationGeneration++
                terminalStartedAt = 0L
                terminalCancel = null
            }
        }
        terminalCancel = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    private fun cancelTerminalRemoval() {
        terminalCancel?.let(mainHandler::removeCallbacks)
        terminalCancel = null
        staticSuccessPost?.let(mainHandler::removeCallbacks)
        staticSuccessPost = null
    }

    private fun scheduleStaticFaceSuccess(context: Context) {
        staticSuccessPost?.let(mainHandler::removeCallbacks)
        val generation = animationGeneration
        val runnable = Runnable {
            synchronized(stateLock) {
                if (
                    generation != animationGeneration ||
                    currentState != FaceState.SUCCESS ||
                    !shouldKeepFaceSuccessUntilKeyguardHidden() ||
                    !keyguardShowing
                ) {
                    return@synchronized
                }
                postStaticFaceSuccessNotification(context)
                staticSuccessPost = null
            }
        }
        staticSuccessPost = runnable
        mainHandler.postDelayed(runnable, STATIC_SUCCESS_POST_DELAY_MS)
    }

    private fun cancelNotification(context: Context) {
        IslandDispatcher.cancel(context, NOTIFICATION_ID)
    }

    private fun normalizeHidden(context: Context) {
        keyguardShowing = false
        unlockCommitted = true
        faceUnlockSucceeded = false
        screenOnSettling = false
        terminalStartedAt = 0L
        currentState = FaceState.STOPPED
        animationGeneration++
        cancelTerminalRemoval()
        cancelNotification(context)
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}
