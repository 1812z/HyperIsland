package io.github.hyperisland.xposed.hook.ScreenRecorder

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Application
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.TileService
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.content.edit
import io.github.hyperisland.R
import io.github.hyperisland.screenrecorder.RecorderSnapshot
import io.github.hyperisland.screenrecorder.ScreenRecorderContract
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

object ScreenRecorderHook : BaseHook() {
    private const val TAG = "HyperIsland[ScreenRecorder]"
    private const val MODULE_PACKAGE = "io.github.hyperisland"
    private const val SETTINGS_ACTION =
        "android.service.quicksettings.action.QS_TILE_PREFERENCES"
    private const val RECORDER_SERVICE_ACTION = "miui.intent.screenrecorder.RECORDER_SERVICE"
    private const val RECORDING_NOTIFICATION_ID = 110
    private const val HIGHLIGHT_COLOR = "#FB382F"

    private val hookedTileClasses = ConcurrentHashMap.newKeySet<Class<*>>()
    private val hookedRecorderServiceClasses = ConcurrentHashMap.newKeySet<Class<*>>()

    @Volatile
    private var recorderDialogVisible = false

    @Volatile
    private var recordingNotificationBuilder: WeakReference<Notification.Builder>? = null

    @Volatile
    private var recordingNotificationContext: WeakReference<Context>? = null

    @Volatile
    private var recordingNotificationBuilderMethod: Method? = null

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        if (param.packageName != ScreenRecorderContract.TARGET_PACKAGE) return
        logWarn(module, "init: screen recorder hook loaded")
        hookVideoEncoderSyncFrame(module)
        hookMediaMuxerLifecycle(module)
        hookOverlayWindowCreation(module)
        hookApplicationAttach(module, param.defaultClassLoader)
    }

    private fun hookApplicationAttach(module: XposedModule, classLoader: ClassLoader) {
        val attach = Application::class.java.getDeclaredMethod(
            "attach",
            Context::class.java,
        ).apply { isAccessible = true }
        module.hook(attach).intercept { chain ->
            val result = chain.proceed()
            val context = chain.args.firstOrNull() as? Context ?: return@intercept result
            ScreenRecorderControlClient.initialize(context) { command ->
                handleControlCommand(context, command, module)
            }
            if (Application.getProcessName() == ScreenRecorderContract.TARGET_PACKAGE) {
                ScreenRecorderControlClient.observe { snapshot ->
                    refreshRecordingNotification(snapshot, module)
                }
            }
            runCatching {
                discoverAndHookComponents(context, classLoader, module)
            }.onFailure {
                logError(module, "component discovery failed: ${it.message}")
            }
            result
        }
        logWarn(module, "init: installed adaptive component discovery")
    }

    @Suppress("DEPRECATION")
    private fun discoverAndHookComponents(
        context: Context,
        classLoader: ClassLoader,
        module: XposedModule,
    ) {
        val packageInfo = context.packageManager.getPackageInfo(
            ScreenRecorderContract.TARGET_PACKAGE,
            PackageManager.GET_SERVICES,
        )
        val recorderServiceName = context.packageManager.resolveService(
            Intent(RECORDER_SERVICE_ACTION).setPackage(ScreenRecorderContract.TARGET_PACKAGE),
            0,
        )?.serviceInfo?.name
        packageInfo.services.orEmpty().forEach { serviceInfo ->
            val serviceClass = runCatching { classLoader.loadClass(serviceInfo.name) }.getOrNull()
                ?: return@forEach
            if (TileService::class.java.isAssignableFrom(serviceClass)) {
                hookConcreteTileService(module, serviceClass)
            }
            if (
                serviceInfo.name == recorderServiceName ||
                isRecorderServiceClass(serviceClass)
            ) {
                hookRecordingNotification(module, serviceClass)
            }
        }
        logWarn(
            module,
            "init: discovered tiles=${hookedTileClasses.size} recorderServices=${hookedRecorderServiceClasses.size}",
        )
    }

    private fun hookConcreteTileService(
        module: XposedModule,
        serviceClass: Class<*>,
    ) {
        if (!hookedTileClasses.add(serviceClass)) return
        val onClick = serviceClass.declaredMethods.firstOrNull {
            it.name == "onClick" && it.parameterCount == 0
        } ?: run {
            logWarn(module, "tile: no onClick in ${serviceClass.name}")
            return
        }
        onClick.isAccessible = true
        module.hook(onClick).intercept { chain ->
            val service = chain.thisObject as? TileService ?: return@intercept chain.proceed()
            logWarn(module, "tile: intercepted ${service.javaClass.name}.onClick")
            runCatching {
                if (recorderDialogVisible) return@runCatching
                val settingsActivity = resolveSettingsActivity(service)
                    ?: error("QS_TILE_PREFERENCES activity not found")
                recorderDialogVisible = true
                ScreenRecorderControlClient.requestSnapshot { snapshot ->
                    runCatching {
                        logWarn(
                            module,
                            "dialog: state=${snapshot.state}, showing independent recorder dialog",
                        )
                        showRecorderDialog(service, settingsActivity, snapshot, module)
                    }.onFailure {
                        recorderDialogVisible = false
                        logError(module, "tile dialog render failed: ${it.message}")
                    }
                }
            }.onFailure {
                recorderDialogVisible = false
                logError(module, "tile dialog failed: ${it.message}")
                return@intercept chain.proceed()
            }
            null
        }
        logWarn(module, "init: hooked tile ${serviceClass.name}.onClick")
    }

    private fun hookOverlayWindowCreation(module: XposedModule) {
        val windowManagerClass = runCatching {
            Class.forName("android.view.WindowManagerImpl")
        }.getOrElse {
            logError(module, "WindowManagerImpl not found: ${it.message}")
            return
        }
        val addViewMethods = windowManagerClass.declaredMethods.filter { method ->
            method.name == "addView" &&
                method.parameterTypes.size == 2 &&
                View::class.java.isAssignableFrom(method.parameterTypes[0]) &&
                ViewGroup.LayoutParams::class.java.isAssignableFrom(method.parameterTypes[1])
        }
        addViewMethods.forEach { method ->
            method.isAccessible = true
            module.hook(method).intercept { chain ->
                val view = chain.args.getOrNull(0) as? View
                val params = chain.args.getOrNull(1) as? WindowManager.LayoutParams
                val windowType = params?.type
                @Suppress("DEPRECATION")
                val isRecorderOverlay = when (windowType) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                    -> true
                    else -> false
                }
                if (isRecorderOverlay && !RecorderOverlayGate.allows(view)) {
                    logWarn(
                        module,
                        "float: blocked overlay view=${view?.javaClass?.name} type=$windowType",
                    )
                    null
                } else {
                    if (isRecorderOverlay) {
                        logWarn(module, "dialog: allowed injected overlay host type=$windowType")
                    }
                    chain.proceed()
                }
            }
        }
        logWarn(module, "init: hooked WindowManagerImpl.addView count=${addViewMethods.size}")
    }

    private fun showRecorderDialog(
        context: Context,
        settingsActivity: String,
        recorderSnapshot: RecorderSnapshot,
        module: XposedModule,
    ) {
        if (recorderSnapshot.isSessionActive) {
            ScreenRecorderDialogInjector.show(
                context = context,
                resolutions = emptyList(),
                sounds = emptyList(),
                initialResolution = "",
                initialSound = 0,
                recordingSnapshot = recorderSnapshot,
                onCancel = {
                    recorderDialogVisible = false
                    logWarn(module, "dialog: recording status cancelled")
                },
                onOpenSettings = {},
                onStart = { _, _ -> },
                onPause = {
                    ScreenRecorderControlClient.pause()
                    logWarn(module, "control: recorder pause requested")
                },
                onResume = {
                    ScreenRecorderControlClient.resume()
                    logWarn(module, "control: recorder resume requested")
                },
                onStop = {
                    recorderDialogVisible = false
                    ScreenRecorderControlClient.stop()
                    logWarn(module, "control: recorder stop requested from status dialog")
                },
            )
            logWarn(module, "dialog: recording status shown, state=${recorderSnapshot.state}")
            return
        }

        val prefs = recorderPreferences(context)
        val currentResolution = prefs.getString(
            ScreenRecorderContract.PREF_RESOLUTION,
            "",
        ).orEmpty()
        val discoveredResolutions = readRecorderResolutions(context, module)
        val resolutions = if (
            currentResolution.isNotBlank() &&
            discoveredResolutions.none { it.second == currentResolution }
        ) {
            listOf("当前设置（${formatResolution(currentResolution)}）" to currentResolution) +
                discoveredResolutions
        } else {
            discoveredResolutions
        }
        val currentSound = prefs.getString(ScreenRecorderContract.PREF_SOUND, "0")
            ?.toIntOrNull() ?: 0
        val discoveredSounds = readRecorderSounds(context, currentSound, module)
        val sounds = if (discoveredSounds.none { it.second == currentSound }) {
            listOf("当前设置" to currentSound) + discoveredSounds
        } else {
            discoveredSounds
        }
        ScreenRecorderDialogInjector.show(
            context = context,
            resolutions = resolutions,
            sounds = sounds,
            initialResolution = currentResolution,
            initialSound = currentSound,
            recordingSnapshot = null,
            onCancel = {
                recorderDialogVisible = false
                logWarn(module, "dialog: cancelled")
            },
            onOpenSettings = {
                recorderDialogVisible = false
                openRecorderSettings(context, settingsActivity)
                logWarn(module, "dialog: opening original recorder settings")
            },
            onStart = { resolution, sound ->
                recorderDialogVisible = false
                recorderPreferences(context).edit {
                    if (resolution.isNotBlank()) {
                        putString(ScreenRecorderContract.PREF_RESOLUTION, resolution)
                    }
                    putString(ScreenRecorderContract.PREF_SOUND, sound.toString())
                }
                ScreenRecorderControlClient.reportStarting()
                requestRecorderStart(context)
                logWarn(module, "control: immediate recorder start requested")
            },
            onPause = {},
            onResume = {},
            onStop = {},
        )
        logWarn(
            module,
            "dialog: independent Miuix dialog shown, resolutions=${resolutions.size} sounds=${sounds.size}",
        )
    }

    private fun resolveSettingsActivity(context: Context): String? {
        return context.packageManager.resolveActivity(
            Intent(SETTINGS_ACTION).setPackage(ScreenRecorderContract.TARGET_PACKAGE),
            0,
        )?.activityInfo?.name
    }

    private fun openRecorderSettings(context: Context, settingsActivity: String) {
        val intent = Intent(SETTINGS_ACTION).apply {
            setClassName(ScreenRecorderContract.TARGET_PACKAGE, settingsActivity)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        PendingIntent.getActivity(
            context,
            0x4853,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        ).send()
    }

    @Suppress("DEPRECATION")
    private fun readRecorderResolutions(
        context: Context,
        module: XposedModule,
    ): List<Pair<String, String>> {
        val suffix = when (Build.DEVICE) {
            "cappu" -> "_c9"
            "lotus" -> "_f9"
            else -> ""
        }
        val thresholds = readStringArray(
            context,
            "screenrecorder_settings_resolution${suffix}_values",
        ).mapNotNull(String::toIntOrNull)
        val fullValues = readStringArray(
            context,
            "screenrecorder_settings_resolution${suffix}_full_values",
        )
        val metrics = android.util.DisplayMetrics()
        (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
            ?.defaultDisplay
            ?.getRealMetrics(metrics)
        val longEdge = maxOf(metrics.widthPixels, metrics.heightPixels).coerceAtLeast(2) and -2
        val shortEdge = minOf(metrics.widthPixels, metrics.heightPixels).coerceAtLeast(2) and -2
        val values = linkedSetOf("$longEdge*$shortEdge")
        if (thresholds.size >= 3 && fullValues.size >= 3) {
            if (shortEdge > thresholds[1]) values += fullValues[1]
            if (shortEdge > thresholds[2]) values += fullValues[2]
            logWarn(module, "dialog: recorder resolution arrays loaded options=$values")
        } else {
            if (shortEdge > 1080) values += "1920*1080"
            if (shortEdge > 720) values += "1280*720"
            logWarn(module, "dialog: recorder resolution arrays unavailable, using display fallback")
        }
        return values.map { value -> formatResolution(value) to value }
    }

    private fun readStringArray(context: Context, name: String): List<String> {
        val resourceId = context.resources.getIdentifier(
            name,
            "array",
            ScreenRecorderContract.TARGET_PACKAGE,
        )
        return if (resourceId == 0) {
            emptyList()
        } else {
            runCatching { context.resources.getStringArray(resourceId).toList() }
                .getOrDefault(emptyList())
        }
    }

    private fun readRecorderSounds(
        context: Context,
        currentSound: Int,
        module: XposedModule,
    ): List<Pair<String, Int>> {
        val labels = readStringArray(context, "screenrecorder_settings_sound")
        val values = readStringArray(context, "screenrecorder_settings_sound_values")
        val resourceOptions = labels.zip(values).mapNotNull { (label, value) ->
            value.toIntOrNull()?.let { label to it }
        }
        val supportsCombinedSound =
            currentSound == 3 ||
                readSystemPropertyInt("ro.vendor.audio.screenrecorder.bothrecord") > 0
        if (resourceOptions.isNotEmpty()) {
            val options = resourceOptions.toMutableList()
            if (supportsCombinedSound && options.none { it.second == 3 }) {
                options += "设备声音+麦克风" to 3
            }
            logWarn(module, "dialog: recorder sound arrays loaded options=$options")
            return options
        }

        val fallback = mutableListOf(
            "无声" to 0,
            "麦克风" to 1,
            "设备声音" to 2,
        )
        if (supportsCombinedSound) {
            fallback += "设备声音+麦克风" to 3
        }
        logWarn(module, "dialog: recorder sound arrays unavailable, fallback=$fallback")
        return fallback
    }

    private fun readSystemPropertyInt(key: String): Int = runCatching {
        val systemProperties = Class.forName("android.os.SystemProperties")
        val getInt = systemProperties.getDeclaredMethod(
            "getInt",
            String::class.java,
            Integer.TYPE,
        )
        (getInt.invoke(null, key, 0) as? Int) ?: 0
    }.getOrDefault(0)

    private fun formatResolution(value: String): String = value.replace("*", " × ")

    private fun isRecorderServiceClass(serviceClass: Class<*>): Boolean {
        return serviceClass.declaredMethods.any {
            it.parameterCount == 0 && it.returnType == Notification.Builder::class.java
        } && serviceClass.declaredMethods.any {
            it.name == "onStartCommand" && it.parameterCount == 3
        }
    }

    private fun hookRecordingNotification(
        module: XposedModule,
        serviceClass: Class<*>,
    ) {
        if (!hookedRecorderServiceClasses.add(serviceClass)) return
        serviceClass.declaredMethods.firstOrNull {
            it.name == "onStartCommand" && it.parameterCount == 3
        }?.let { onStartCommand ->
            onStartCommand.isAccessible = true
            module.hook(onStartCommand).intercept { chain ->
                val service = chain.thisObject as? Service
                    ?: return@intercept chain.proceed()
                val intent = chain.args.getOrNull(0) as? Intent
                    ?: return@intercept chain.proceed()
                logWarn(
                    module,
                    "service: onStartCommand action=${intent.action} extras=${intent.extras?.keySet()}",
                )
                if (isRecorderStopIntent(intent)) {
                    logWarn(module, "control: Xiaomi recorder stop intent observed")
                }
                if (intent.getBooleanExtra(ScreenRecorderContract.EXTRA_TOGGLE_PAUSE, false)) {
                    if (
                        ScreenRecorderControlClient.snapshot.state ==
                        ScreenRecorderContract.STATE_PAUSED
                    ) {
                        ScreenRecorderControlClient.resume()
                    } else {
                        ScreenRecorderControlClient.pause()
                    }
                    return@intercept Service.START_NOT_STICKY
                }
                if (intent.getBooleanExtra(ScreenRecorderContract.EXTRA_CONTROL_STOP, false)) {
                    ScreenRecorderControlClient.stop()
                    return@intercept Service.START_NOT_STICKY
                }
                if (isRecorderControlIntent(intent)) {
                    if (
                        intent.getBooleanExtra(
                            ScreenRecorderContract.EXTRA_CONFIRMED_START,
                            false,
                        )
                    ) {
                        logWarn(module, "control: confirmed start passed to Xiaomi recorder")
                    }
                    return@intercept chain.proceed()
                }
                if (recorderDialogVisible) {
                    logWarn(module, "dialog: ignored duplicate recorder service start")
                    return@intercept Service.START_NOT_STICKY
                }
                runCatching {
                    val settingsActivity = resolveSettingsActivity(service)
                        ?: error("QS_TILE_PREFERENCES activity not found")
                    recorderDialogVisible = true
                    logWarn(
                        module,
                        "dialog: recorder service start intercepted, showing independent window",
                    )
                    ScreenRecorderControlClient.requestSnapshot { snapshot ->
                        runCatching {
                            showRecorderDialog(service, settingsActivity, snapshot, module)
                        }.onFailure {
                            recorderDialogVisible = false
                            logError(module, "service dialog render failed: ${it.message}")
                        }
                    }
                }.onFailure {
                    recorderDialogVisible = false
                    logError(module, "service dialog launch failed: ${it.message}")
                    return@intercept chain.proceed()
                }
                Service.START_NOT_STICKY
            }
        }
        val builderMethod = serviceClass.declaredMethods.firstOrNull {
            it.parameterCount == 0 && it.returnType == Notification.Builder::class.java
        } ?: run {
            logError(module, "recording notification builder not found")
            return
        }
        builderMethod.isAccessible = true
        recordingNotificationBuilderMethod = builderMethod
        module.hook(builderMethod).intercept { chain ->
            val original = chain.proceed()
            val builder = original as? Notification.Builder ?: return@intercept original
            val context = chain.thisObject as? Context ?: return@intercept builder
            decorateRecordingNotification(
                builder,
                context,
                ScreenRecorderControlClient.snapshot,
            )
            builder
        }
        serviceClass.declaredMethods.firstOrNull {
            it.name == "onDestroy" && it.parameterCount == 0
        }?.let { onDestroy ->
            onDestroy.isAccessible = true
            module.hook(onDestroy).intercept { chain ->
                val result = chain.proceed()
                ScreenRecorderControlClient.reportIdle()
                recorderDialogVisible = false
                recordingNotificationBuilder = null
                recordingNotificationContext = null
                result
            }
        }
        logWarn(module, "init: hooked recorder notification service=${serviceClass.name}")
    }

    private fun isRecorderStopIntent(intent: Intent): Boolean {
        return intent.getBooleanExtra("stop_screenrecorder", false) ||
            intent.getBooleanExtra("stop_self", false) ||
            intent.getBooleanExtra("is_screen_off_auto_stop", false)
    }

    private fun isRecorderControlIntent(intent: Intent): Boolean {
        return intent.getBooleanExtra(ScreenRecorderContract.EXTRA_CONFIRMED_START, false) ||
            intent.getBooleanExtra(ScreenRecorderContract.EXTRA_TOGGLE_PAUSE, false) ||
            intent.getBooleanExtra(ScreenRecorderContract.EXTRA_CONTROL_STOP, false) ||
            intent.getBooleanExtra("stop_screenrecorder", false) ||
            intent.getBooleanExtra("stop_self", false) ||
            intent.getBooleanExtra("do_nothing", false) ||
            intent.getBooleanExtra("is_screen_off_auto_stop", false)
    }

    private fun decorateRecordingNotification(
        builder: Notification.Builder,
        context: Context,
        snapshot: RecorderSnapshot,
    ) {
        val nowWallClock = System.currentTimeMillis()
        val durationMillis = snapshot.durationAt(android.os.SystemClock.elapsedRealtime())
        val timerWhen = nowWallClock - durationMillis
        val isPaused = snapshot.state == ScreenRecorderContract.STATE_PAUSED
        val originalExtras = runCatching { Bundle(builder.build().extras) }.getOrElse { Bundle() }
        val pauseIntent = PendingIntent.getService(
            context,
            0x4851,
            Intent(RECORDER_SERVICE_ACTION).apply {
                setPackage(ScreenRecorderContract.TARGET_PACKAGE)
                putExtra(ScreenRecorderContract.EXTRA_TOGGLE_PAUSE, true)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            context,
            0x4852,
            Intent(RECORDER_SERVICE_ACTION).apply {
                setPackage(ScreenRecorderContract.TARGET_PACKAGE)
                putExtra(ScreenRecorderContract.EXTRA_CONTROL_STOP, true)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val mergedExtras = originalExtras.apply {
            putAll(
                buildFocusExtras(
                    context = context,
                    pauseIntent = pauseIntent,
                    stopIntent = stopIntent,
                    timerWhen = timerWhen,
                    timerSystemCurrent = nowWallClock,
                    isPaused = isPaused,
                ),
            )
        }
        val pauseAction = Notification.Action.Builder(
            Icon.createWithResource(
                MODULE_PACKAGE,
                if (isPaused) R.drawable.ic_focus_resume_light else R.drawable.ic_focus_pause_light,
            ),
            if (isPaused) "继续" else "暂停",
            pauseIntent,
        ).build()
        val stopAction = Notification.Action.Builder(
            Icon.createWithResource(MODULE_PACKAGE, R.drawable.ic_focus_stop_light),
            "停止",
            stopIntent,
        ).build()
        builder
            .setContentTitle(if (isPaused) "录制已暂停" else "正在录制屏幕")
            .setContentText(if (isPaused) "点击继续录制" else "可暂停或停止并保存录制")
            .setWhen(timerWhen)
            .setShowWhen(true)
            .setUsesChronometer(!isPaused)
            .setOnlyAlertOnce(true)
            .setActions(pauseAction, stopAction)
            .setExtras(mergedExtras)
        recordingNotificationBuilder = WeakReference(builder)
        recordingNotificationContext = WeakReference(context)
    }

    private fun refreshRecordingNotification(snapshot: RecorderSnapshot, module: XposedModule) {
        if (
            snapshot.state != ScreenRecorderContract.STATE_RECORDING &&
            snapshot.state != ScreenRecorderContract.STATE_PAUSED
        ) {
            return
        }
        val context = recordingNotificationContext?.get() ?: return
        val builder = recordingNotificationBuilder?.get()
            ?: runCatching {
                recordingNotificationBuilderMethod?.invoke(context) as? Notification.Builder
            }.getOrNull()
            ?: return
        runCatching {
            decorateRecordingNotification(builder, context, snapshot)
            context.getSystemService(NotificationManager::class.java)
                ?.notify(RECORDING_NOTIFICATION_ID, builder.build())
        }.onFailure {
            logError(module, "notification: recorder state refresh failed: ${it.message}")
        }
    }

    private fun requestRecorderStop(context: Context) {
        context.startService(Intent(RECORDER_SERVICE_ACTION).apply {
            setPackage(ScreenRecorderContract.TARGET_PACKAGE)
            putExtra("stop_screenrecorder", true)
        })
    }

    private fun handleControlCommand(context: Context, command: Int, module: XposedModule) {
        when (command) {
            ScreenRecorderContract.MSG_COMMAND_PAUSE -> {
                if (MediaMuxerPauseGate.pause()) {
                    logWarn(module, "control: MediaMuxer sample output paused")
                }
            }
            ScreenRecorderContract.MSG_COMMAND_RESUME -> {
                var requestedSyncFrames = 0
                if (
                    MediaMuxerPauseGate.resume {
                        requestedSyncFrames = VideoEncoderSyncFrameRequester.requestSyncFrames()
                    }
                ) {
                    logWarn(
                        module,
                        "control: MediaMuxer resumed, requested keyframes=$requestedSyncFrames",
                    )
                }
            }
            ScreenRecorderContract.MSG_COMMAND_STOP -> {
                if (Application.getProcessName() == ScreenRecorderContract.TARGET_PACKAGE) {
                    requestRecorderStop(context)
                    logWarn(module, "control: Xiaomi recorder stop dispatched")
                }
            }
            ScreenRecorderContract.MSG_COMMAND_START -> {
                if (Application.getProcessName() == ScreenRecorderContract.TARGET_PACKAGE) {
                    requestRecorderStart(context)
                    logWarn(module, "control: Xiaomi recorder start dispatched")
                }
            }
        }
    }

    private fun requestRecorderStart(context: Context) {
        context.startService(Intent(RECORDER_SERVICE_ACTION).apply {
            setPackage(ScreenRecorderContract.TARGET_PACKAGE)
            putExtra("is_start_immediately", true)
            putExtra(ScreenRecorderContract.EXTRA_CONFIRMED_START, true)
        })
    }

    private fun hookMediaMuxerLifecycle(module: XposedModule) {
        val addTrack = MediaMuxer::class.java.getDeclaredMethod(
            "addTrack",
            MediaFormat::class.java,
        )
        module.hook(addTrack).intercept { chain ->
            val result = chain.proceed()
            val trackIndex = result as? Int ?: return@intercept result
            val muxer = chain.thisObject as? MediaMuxer ?: return@intercept trackIndex
            val format = chain.args.firstOrNull() as? MediaFormat
            val mime = runCatching { format?.getString(MediaFormat.KEY_MIME) }.getOrNull()
            MediaMuxerPauseGate.onTrackAdded(muxer, trackIndex, mime)
            trackIndex
        }

        val start = MediaMuxer::class.java.getDeclaredMethod("start")
        module.hook(start).intercept { chain ->
            val result = chain.proceed()
            val muxer = chain.thisObject as? MediaMuxer ?: return@intercept result
            MediaMuxerPauseGate.onStarted(muxer)
            ScreenRecorderControlClient.reportStarted()
            logWarn(module, "state: MediaMuxer started, recording confirmed")
            result
        }

        listOf("stop", "release").forEach { methodName ->
            val method = MediaMuxer::class.java.getDeclaredMethod(methodName)
            module.hook(method).intercept { chain ->
                val muxer = chain.thisObject as? MediaMuxer
                try {
                    chain.proceed()
                } finally {
                    if (muxer != null && MediaMuxerPauseGate.onStopped(muxer)) {
                        ScreenRecorderControlClient.reportIdle()
                        logWarn(module, "state: MediaMuxer $methodName, recording ended")
                    }
                }
            }
        }

        val writeSampleData = MediaMuxer::class.java.getDeclaredMethod(
            "writeSampleData",
            Integer.TYPE,
            ByteBuffer::class.java,
            MediaCodec.BufferInfo::class.java,
        )
        module.hook(writeSampleData).intercept { chain ->
            val muxer = chain.thisObject as? MediaMuxer ?: return@intercept chain.proceed()
            val info = chain.args.getOrNull(2) as? MediaCodec.BufferInfo
                ?: return@intercept chain.proceed()
            val trackIndex = chain.args.getOrNull(0) as? Int
                ?: return@intercept chain.proceed()
            if (MediaMuxerPauseGate.shouldDrop(muxer, trackIndex, info.flags)) {
                return@intercept null
            }
            val originalPresentationTime = info.presentationTimeUs
            info.presentationTimeUs = MediaMuxerPauseGate.adjustedPresentationTime(
                muxer,
                trackIndex,
                originalPresentationTime,
            )
            try {
                chain.proceed()
            } finally {
                info.presentationTimeUs = originalPresentationTime
            }
        }
        logWarn(module, "init: hooked precise MediaMuxer lifecycle and pause gate")
    }

    private fun hookVideoEncoderSyncFrame(module: XposedModule) {
        MediaCodec::class.java.declaredMethods.filter { method ->
            method.name == "configure" &&
                method.parameterCount == 4 &&
                method.parameterTypes.firstOrNull() == MediaFormat::class.java &&
                method.parameterTypes.lastOrNull() == Integer.TYPE
        }.forEach { configure ->
            module.hook(configure).intercept { chain ->
                val result = chain.proceed()
                val codec = chain.thisObject as? MediaCodec ?: return@intercept result
                val format = chain.args.firstOrNull() as? MediaFormat
                val flags = chain.args.lastOrNull() as? Int ?: 0
                val mime = runCatching { format?.getString(MediaFormat.KEY_MIME) }.getOrNull()
                if (
                    flags and MediaCodec.CONFIGURE_FLAG_ENCODE != 0 &&
                    mime?.startsWith("video/") == true
                ) {
                    VideoEncoderSyncFrameRequester.register(codec)
                }
                result
            }
        }

        val start = MediaCodec::class.java.getDeclaredMethod("start")
        module.hook(start).intercept { chain ->
            val result = chain.proceed()
            (chain.thisObject as? MediaCodec)?.let {
                VideoEncoderSyncFrameRequester.setActive(it, true)
            }
            result
        }
        listOf("stop", "release").forEach { methodName ->
            val method = MediaCodec::class.java.getDeclaredMethod(methodName)
            module.hook(method).intercept { chain ->
                val codec = chain.thisObject as? MediaCodec
                try {
                    chain.proceed()
                } finally {
                    if (codec != null) {
                        if (methodName == "release") {
                            VideoEncoderSyncFrameRequester.remove(codec)
                        } else {
                            VideoEncoderSyncFrameRequester.setActive(codec, false)
                        }
                    }
                }
            }
        }
        logWarn(module, "init: hooked video encoder keyframe control")
    }

    private fun recorderPreferences(context: Context) = context.getSharedPreferences(
        "${ScreenRecorderContract.TARGET_PACKAGE}_preferences",
        Context.MODE_PRIVATE,
    )

    private fun buildFocusExtras(
        context: Context,
        pauseIntent: PendingIntent,
        stopIntent: PendingIntent,
        timerWhen: Long,
        timerSystemCurrent: Long,
        isPaused: Boolean,
    ): Bundle {
        val tickerKey = "miui.focus.pic_ticker"
        val pauseKey = if (isPaused) "miui.focus.pic_resume" else "miui.focus.pic_pause"
        val pauseDarkKey = if (isPaused) {
            "miui.focus.pic_resume_dark"
        } else {
            "miui.focus.pic_pause_dark"
        }
        val stopKey = "miui.focus.pic_stop"
        val stopDarkKey = "miui.focus.pic_stop_dark"
        val timerInfo = JSONObject().apply {
            put("timerWhen", timerWhen)
            put("timerType", if (isPaused) 2 else 1)
            put("timerSystemCurrent", timerSystemCurrent)
        }
        val param = JSONObject().apply {
            put("protocol", 1)
            put("updatable", true)
            put("enableFloat", false)
            put("business", "screen_recording")
            put("scene", "recorder")
            put("content", if (isPaused) "录制已暂停" else "正在录制屏幕")
            put("notifyId", "${context.packageName}$RECORDING_NOTIFICATION_ID")
            put("islandFirstFloat", false)
            put("ticker", if (isPaused) "录制已暂停" else "正在录制屏幕")
            put("tickerPic", tickerKey)
            put("tickerPicDark", tickerKey)
            put("param_island", JSONObject().apply {
                put("islandPriority", 1)
                put("islandTimeout", Int.MAX_VALUE)
                put("islandProperty", 2)
                put("highlightColor", HIGHLIGHT_COLOR)
                put("bigIslandArea", JSONObject().apply {
                    put("imageTextInfoLeft", JSONObject().apply {
                        put("type", 1)
                        put("picInfo", JSONObject().apply {
                            put("type", 1)
                            put("pic", tickerKey)
                        })
                    })
                    put("sameWidthDigitInfo", JSONObject().apply {
                        put("timerInfo", timerInfo)
                    })
                })
                put("smallIslandArea", JSONObject().apply {
                    put("picInfo", JSONObject().apply {
                        put("type", 1)
                        put("pic", tickerKey)
                    })
                })
            })
            put("animTextInfo", JSONObject().apply {
                put("timerInfo", timerInfo)
                put("animIconInfo", JSONObject().apply {
                    put("type", 1)
                    put("src", "voiceWaveBig")
                    put("number", 0)
                    put("loop", true)
                    put("autoplay", true)
                })
                put("picInfo", JSONObject().apply {
                    put("type", 1)
                    put("pic", tickerKey)
                })
            })
            put("actions", JSONArray().apply {
                put(JSONObject().apply {
                    put("actionIntentType", 0)
                    put("action", "miui.focus.action_1")
                    put("type", 0)
                    put("actionIcon", pauseKey)
                    put("actionIconDark", pauseDarkKey)
                })
                put(JSONObject().apply {
                    put("actionIntentType", 0)
                    put("action", "miui.focus.action_2")
                    put("type", 0)
                    put("actionIcon", stopKey)
                    put("actionIconDark", stopDarkKey)
                })
            })
        }
        val pauseAction = Notification.Action.Builder(
            null,
            if (isPaused) "继续" else "暂停",
            pauseIntent,
        ).build()
        val stopAction = Notification.Action.Builder(null, "停止", stopIntent).build()
        return Bundle().apply {
            putString("miui.focus.param", JSONObject().put("param_v2", param).toString())
            putBundle("miui.focus.actions", Bundle().apply {
                putParcelable("miui.focus.action_1", pauseAction)
                putParcelable("miui.focus.action_2", stopAction)
            })
            putBundle("miui.focus.pics", Bundle().apply {
                putParcelable(
                    tickerKey,
                    Icon.createWithResource(MODULE_PACKAGE, R.drawable.ic_focus_ticker_recorder),
                )
                putParcelable(
                    "miui.focus.pic_pause",
                    Icon.createWithResource(MODULE_PACKAGE, R.drawable.ic_focus_pause_light),
                )
                putParcelable(
                    "miui.focus.pic_resume",
                    Icon.createWithResource(MODULE_PACKAGE, R.drawable.ic_focus_resume_light),
                )
                putParcelable(
                    stopKey,
                    Icon.createWithResource(MODULE_PACKAGE, R.drawable.ic_focus_stop_light),
                )
                putParcelable(
                    "miui.focus.pic_pause_dark",
                    Icon.createWithResource(MODULE_PACKAGE, R.drawable.ic_focus_pause),
                )
                putParcelable(
                    "miui.focus.pic_resume_dark",
                    Icon.createWithResource(MODULE_PACKAGE, R.drawable.ic_focus_resume),
                )
                putParcelable(
                    stopDarkKey,
                    Icon.createWithResource(MODULE_PACKAGE, R.drawable.ic_focus_stop),
                )
            })
        }
    }
}
