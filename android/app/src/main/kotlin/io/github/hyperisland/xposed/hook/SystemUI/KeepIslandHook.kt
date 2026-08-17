package io.github.hyperisland.xposed.hook

import android.app.Application
import android.app.ActivityManager
import android.app.PendingIntent
import android.os.BatteryManager
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Icon
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Display
import android.view.Surface
import android.view.View
import android.widget.RemoteViews
import io.github.hyperisland.R
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.hook.SystemUI.DynamicIslandVisibilityHook
import io.github.hyperisland.xposed.islanddispatch.IslandDispatcher
import io.github.hyperisland.xposed.islanddispatch.definition.IslandRequest
import io.github.hyperisland.xposed.utils.moduleContext
import io.github.hyperisland.xposed.utils.toRounded
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt
import org.json.JSONArray

object KeepIslandHook : BaseHook() {

    private const val TAG = "HyperIsland[KeepIsland]"
    private const val PREF_KEY = "pref_keep_island"

    private const val PREF_KEY_DISPLAY_TIMING = "pref_keep_island_display_timing"

    private const val PREF_KEY_SHOW_NOTIFICATION = "pref_keep_island_show_notification"

    private const val PREF_KEY_AUTO_HIDE = "pref_keep_island_auto_hide"

    private const val PREF_KEY_HIDE_LANDSCAPE = "pref_keep_island_hide_landscape"

    private const val PREF_KEY_HIGHLIGHT_COLOR = "pref_keep_island_highlight_color"

    private const val PREF_KEY_LEFT_HIGHLIGHT = "pref_keep_island_left_highlight"

    private const val PREF_KEY_RIGHT_HIGHLIGHT = "pref_keep_island_right_highlight"

    private const val PREF_KEY_LEFT_CONTENT = "pref_keep_island_left_content"

    private const val PREF_KEY_RIGHT_CONTENT = "pref_keep_island_right_content"

    private const val PREF_KEY_CAROUSEL_INTERVAL = "pref_keep_island_carousel_interval_seconds"

    private const val PREF_KEY_FOCUS_NOTIFICATION = "pref_keep_island_focus_notification"

    private const val PREF_KEY_FOCUS_CONTENT_TYPE = "pref_keep_island_focus_content_type"

    private const val PREF_KEY_EXPAND_TEXT_COLOR_MODE =
        "pref_keep_island_expand_text_color_mode"

    private const val PREF_KEY_NOTIFICATION_TITLE = "pref_keep_island_notification_title"

    private const val PREF_KEY_NOTIFICATION_CONTENT = "pref_keep_island_notification_content"

    private const val PREF_KEY_SHOW_ISLAND_ICON = "pref_keep_island_show_island_icon"

    private const val PREF_KEY_CUSTOM_ICON_PATH = "pref_keep_island_custom_icon_path"

    private const val KEEP_ISLAND_NOTIF_ID = 0x4B494B49

    private const val KEEP_ISLAND_CHANNEL = "keep_island"

    const val ACTION_REFRESH_KEEP_ISLAND =
        "io.github.hyperisland.action.REFRESH_KEEP_ISLAND"

    private const val RESTORE_DELAY_MS = 500L

    private const val DATA_UPDATE_INTERVAL_MS = 1000L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var appContext: android.content.Context? = null
    @Volatile
    private var posted = false

    private var cachedModule: XposedModule? = null

    private val activeRealKeys = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var autoHideTrackingEnabled = false

    private var restoreRunnable: Runnable? = null

    private var periodicDataUpdateRunnable: Runnable? = null

    private var carouselIndex = 0L

    private var lastCarouselAdvanceAt = 0L

    private var cachedContentConfig: ContentConfig? = null

    private val dataChangedListener: () -> Unit = { scheduleContentUpdateFromDataChange() }
    private val islandVisibilityListener: (DynamicIslandVisibilityHook.Event) -> Unit = { event ->
        handleIslandVisibilityEvent(event)
    }

    private var dataListenerRegistered = false
    private var islandVisibilityListenerRegistered = false

    private var keepIslandContentCustomized = false

    private var lastContentUpdateAt = 0L

    private var lastContentUpdateSignature: String? = null

    private var cachedPanelFocusContent: FocusContent? = null

    private val cpuTrend = ArrayDeque<Float>()

    private val gpuTrend = ArrayDeque<Float>()

    private val memoryTrend = ArrayDeque<Float>()

    private var lastPerformanceSampleAt = 0L

    private val chargingTrend = ArrayDeque<ChargingSample>()

    private var chargingSessionStartedAt = 0L

    private var lastChargingSampleAt = 0L

    private var chargingSampleIntervalMs = CHARGING_SAMPLE_INTERVAL_MS

    private var chargingChartMode = ChargingChartMode.POWER

    private var chargingSessionIsCharging: Boolean? = null

    private var chargingReceiverRegistered = false

    private var displayTimingReceiverRegistered = false

    @Volatile
    private var powerConnected = false

    private var configurationCallbacksRegistered = false

    private var displayListenerRegistered = false
    private var statusBarTintListenerRegistered = false

    override fun getTag() = TAG

    private fun refreshFromSettings() {
        mainHandler.postDelayed({
            cachedContentConfig = null
            cachedPanelFocusContent = null
            carouselIndex = 0L
            lastCarouselAdvanceAt = System.currentTimeMillis()
            if (ConfigManager.getString(PREF_KEY_FOCUS_CONTENT_TYPE, FOCUS_CONTENT_NOTIFICATION) !=
                FOCUS_CONTENT_CHARGING
            ) {
                resetChargingSession()
            }
            evaluateKeepIsland()
            if (posted) {
                appContext?.let { postKeepIsland(it, restore = true) }
                if (hasConfiguredKeepIslandContent()) {
                    schedulePeriodicDataUpdate()
                } else {
                    cancelPeriodicDataUpdate()
                }
            }
        }, 500)
    }

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        cachedModule = module
        log(module, "onInit pkg=${param.packageName}")
        registerIslandVisibilityListener()
        hookApplicationOnCreate(module, param)
    }

    private fun hookApplicationOnCreate(module: XposedModule, param: PackageLoadedParam) {
        try {
            val method = param.defaultClassLoader
                .loadClass("android.app.Application")
                .getDeclaredMethod("onCreate")
            module.hook(method).intercept { chain ->
                val result = chain.proceed()
                val app = chain.thisObject as? Application
                if (app != null) {
                    appContext = app.applicationContext
                    registerConfigurationCallbacks(app.applicationContext)
                    registerDisplayListener(app.applicationContext)
                    registerIslandDataManager(app.applicationContext)
                    registerChargingPanelReceiver(app.applicationContext)
                    registerDisplayTimingReceiver(app.applicationContext)
                    registerSettingsRefreshReceiver(app.applicationContext)
                    registerStatusBarTintListener()
                    mainHandler.post { evaluateKeepIsland() }
                    mainHandler.postDelayed({ evaluateKeepIsland() }, 3000)
                }
                result
            }
            log(module, "hooked Application.onCreate")
        } catch (e: Throwable) {
            logError(module, "Application.onCreate hook failed: ${e.message}")
        }
    }

    private fun handleIslandVisibilityEvent(event: DynamicIslandVisibilityHook.Event) {
        val ctx = appContext ?: return
        if (!autoHideTrackingEnabled) return
        if (event.global) {
            diag("ignored global island region visible=${event.visible}")
            return
        }
        if (event.notificationId == KEEP_ISLAND_NOTIF_ID ||
            event.sourceChannel == KEEP_ISLAND_CHANNEL
        ) {
            diag("ignored keep island key=${event.key} visible=${event.visible}")
            return
        }
        if (event.visible && event.sourcePackage != "com.android.systemui" &&
            event.sourcePackage == foregroundPackage(ctx)
        ) {
            diag("ignored foreground island key=${event.key} pkg=${event.sourcePackage}")
            return
        }
        if (event.visible) markRealIslandVisible(ctx, event.key) else markRealIslandHidden(event.key)
        diag("accepted island key=${event.key} visible=${event.visible} active=${activeRealKeys.size}")
    }

    private fun registerIslandVisibilityListener() {
        if (islandVisibilityListenerRegistered) return
        islandVisibilityListenerRegistered = true
        DynamicIslandVisibilityHook.addListener(islandVisibilityListener)
    }

    private fun diag(message: String) {
        if (!ConfigManager.isDebugLogEnabled()) return
        cachedModule?.let { log(it, message) }
    }

    private fun markRealIslandVisible(ctx: Context, key: String) {
        cancelPendingRestore()
        val added = activeRealKeys.add(key)
        if (added && activeRealKeys.size == 1 && posted) {
            updateKeepIslandContent(ctx, force = true)
        }
    }

    private fun markRealIslandHidden(key: String) {
        if (activeRealKeys.remove(key) && activeRealKeys.isEmpty()) {
            scheduleRestore()
        }
    }

    private fun evaluateKeepIsland() {
        val ctx = appContext ?: return
        val islandEnabled = ConfigManager.getBoolean(PREF_KEY, false)
        val autoHide = ConfigManager.getBoolean(PREF_KEY_AUTO_HIDE, true)
        if (!islandEnabled || !autoHide) {
            activeRealKeys.clear()
        }
        autoHideTrackingEnabled = islandEnabled && autoHide && shouldPostKeepNotification()
        if (shouldPostKeepNotification()) {
            cancelPendingRestore()
            if (!posted) {
                postKeepIsland(ctx, restore = true)
            } else {
                updateKeepIslandContent(ctx, force = true)
            }
        } else {
            cancelPendingRestore()
            activeRealKeys.clear()
            if (posted) cancelKeepIsland(ctx)
        }
    }

    private fun shouldPostKeepNotification(): Boolean =
        matchesDisplayTiming() && (
                ConfigManager.getBoolean(PREF_KEY, false) ||
                        ConfigManager.getBoolean(PREF_KEY_SHOW_NOTIFICATION, false)
                )

    private fun matchesDisplayTiming(): Boolean =
        ConfigManager.getString(PREF_KEY_DISPLAY_TIMING, DISPLAY_TIMING_ALWAYS) !=
                DISPLAY_TIMING_CHARGING || powerConnected

    private fun focusContentActive(): Boolean =
        ConfigManager.getBoolean(PREF_KEY_FOCUS_NOTIFICATION, false) ||
                ConfigManager.getBoolean(PREF_KEY_SHOW_NOTIFICATION, false)

    private fun shouldEnableIsland(context: Context): Boolean {
        if (!ConfigManager.getBoolean(PREF_KEY, false)) return false
        val hideForRealNotification = ConfigManager.getBoolean(PREF_KEY_AUTO_HIDE, true) &&
                (activeRealKeys.isNotEmpty() || restoreRunnable != null)
        if (hideForRealNotification) return false
        val hideForLandscape = ConfigManager.getBoolean(PREF_KEY_HIDE_LANDSCAPE, false) &&
                isLandscape(context)
        return !hideForLandscape
    }

    private fun postKeepIsland(context: android.content.Context, restore: Boolean) {
        try {
            val highlightColor = ConfigManager.getString(PREF_KEY_HIGHLIGHT_COLOR, "")
                .takeIf { it.isNotBlank() }
            val showLeftHighlight = highlightColor != null &&
                    ConfigManager.getBoolean(PREF_KEY_LEFT_HIGHLIGHT, false)
            val showRightHighlight = highlightColor != null &&
                    ConfigManager.getBoolean(PREF_KEY_RIGHT_HIGHLIGHT, false)
            val texts: Pair<String, String> = resolveKeepIslandTexts()
            val focusEnabled = ConfigManager.getBoolean(PREF_KEY_FOCUS_NOTIFICATION, false)
            val showNotification = ConfigManager.getBoolean(PREF_KEY_SHOW_NOTIFICATION, false)
            val focusContent = resolveFocusContent(context, focusEnabled || showNotification)
            val islandEnabled = shouldEnableIsland(context)
            val showIslandIcon = ConfigManager.getBoolean(PREF_KEY_SHOW_ISLAND_ICON, false)
            val customIconPath = ConfigManager.getString(PREF_KEY_CUSTOM_ICON_PATH, "")
            val contentIntent = createLaunchAppIntent(context)
            val request = IslandRequest(
                title = texts.first,
                content = texts.second,
                icon = loadCustomIcon(customIconPath),
                notifId = KEEP_ISLAND_NOTIF_ID,
                timeoutSecs = Int.MAX_VALUE,
                firstFloat = false,
                enableFloat = false,
                showNotification = showNotification,
                preserveStatusBarSmallIcon = false,
                isOngoing = true,
                showIslandIcon = showIslandIcon,
                contentIntent = contentIntent,
                clearBeforePost = true,
                sourcePackage = "io.github.hyperisland",
                sourceChannelId = KEEP_ISLAND_CHANNEL,
                highlightColor = highlightColor,
                showLeftHighlightColor = showLeftHighlight,
                showRightHighlightColor = showRightHighlight,
                islandOnly = !focusEnabled,
                dismissIsland = !islandEnabled,
                islandEnabled = true,
                focusTitle = focusContent.title,
                focusContent = focusContent.content,
                focusRemoteViews = focusContent.remoteViews,
                focusNightRemoteViews = focusContent.nightRemoteViews,
                focusIslandExpandRemoteViews = if (focusEnabled) {
                    focusContent.islandExpandRemoteViews
                } else {
                    null
                },
                focusAodRemoteViews = focusContent.aodRemoteViews,
                focusFullAodRemoteViews = focusContent.aodRemoteViews,
            )
            IslandDispatcher.post(context, request)
            posted = true
            lastContentUpdateSignature = contentSignature(
                texts,
                focusEnabled,
                focusContent.signature,
                showIslandIcon,
                customIconPath,
                highlightColor,
                showLeftHighlight,
                showRightHighlight,
            )
            lastContentUpdateAt = System.currentTimeMillis()
            keepIslandContentCustomized = texts.first != " " || texts.second.isNotEmpty()
            cachedModule?.let { log(it, "keep island ${if (restore) "restored" else "posted"}") }
            if (hasConfiguredKeepIslandContent()) schedulePeriodicDataUpdate()
        } catch (e: Exception) {
            cachedModule?.let { logError(it, "keep island post failed: ${e.message}") }
        }
    }

    private fun updateKeepIslandContent(
        context: android.content.Context,
        force: Boolean = false,
    ) {
        if (!posted || !shouldPostKeepNotification()) return
        if (defaultDisplayState(context) == Display.STATE_OFF) return
        val texts: Pair<String, String> = resolveKeepIslandTexts()
        try {
            val highlightColor = ConfigManager.getString(PREF_KEY_HIGHLIGHT_COLOR, "")
                .takeIf { it.isNotBlank() }
            val showLeftHighlight = highlightColor != null &&
                    ConfigManager.getBoolean(PREF_KEY_LEFT_HIGHLIGHT, false)
            val showRightHighlight = highlightColor != null &&
                    ConfigManager.getBoolean(PREF_KEY_RIGHT_HIGHLIGHT, false)
            val focusEnabled = ConfigManager.getBoolean(PREF_KEY_FOCUS_NOTIFICATION, false)
            val showNotification = ConfigManager.getBoolean(PREF_KEY_SHOW_NOTIFICATION, false)
            val focusContent = resolveFocusContent(context, focusEnabled || showNotification)
            val islandEnabled = shouldEnableIsland(context)
            val showIslandIcon = ConfigManager.getBoolean(PREF_KEY_SHOW_ISLAND_ICON, false)
            val customIconPath = ConfigManager.getString(PREF_KEY_CUSTOM_ICON_PATH, "")
            val contentIntent = createLaunchAppIntent(context)
            val signature = contentSignature(
                texts,
                focusEnabled,
                focusContent.signature,
                showIslandIcon,
                customIconPath,
                highlightColor,
                showLeftHighlight,
                showRightHighlight,
            )
            if (!force && signature == lastContentUpdateSignature) return
            val now = System.currentTimeMillis()
            if (!force && now - lastContentUpdateAt < DATA_UPDATE_INTERVAL_MS) return
            val request = IslandRequest(
                title = texts.first,
                content = texts.second,
                icon = loadCustomIcon(customIconPath),
                notifId = KEEP_ISLAND_NOTIF_ID,
                timeoutSecs = Int.MAX_VALUE,
                firstFloat = false,
                enableFloat = false,
                showNotification = showNotification,
                preserveStatusBarSmallIcon = false,
                isOngoing = true,
                showIslandIcon = showIslandIcon,
                contentIntent = contentIntent,
                clearBeforePost = false,
                sourcePackage = "io.github.hyperisland",
                sourceChannelId = KEEP_ISLAND_CHANNEL,
                highlightColor = highlightColor,
                showLeftHighlightColor = showLeftHighlight,
                showRightHighlightColor = showRightHighlight,
                islandOnly = !focusEnabled,
                dismissIsland = !islandEnabled,
                islandEnabled = true,
                focusTitle = focusContent.title,
                focusContent = focusContent.content,
                focusRemoteViews = focusContent.remoteViews,
                focusNightRemoteViews = focusContent.nightRemoteViews,
                focusIslandExpandRemoteViews = if (focusEnabled) {
                    focusContent.islandExpandRemoteViews
                } else {
                    null
                },
                focusAodRemoteViews = focusContent.aodRemoteViews,
                focusFullAodRemoteViews = focusContent.aodRemoteViews,
                bypassSceneBehavior = true,
                notificationSilent = true
            )
            IslandDispatcher.post(context, request)
            lastContentUpdateAt = now
            lastContentUpdateSignature = signature
            keepIslandContentCustomized = texts.first != " " || texts.second.isNotEmpty()
            cachedModule?.let { log(it, "keep island content updated left=${texts.first} right=${texts.second}") }
        } catch (e: Exception) {
            cachedModule?.let { logError(it, "keep island update failed: ${e.message}") }
        }
    }

    private fun resolveKeepIslandTexts(): Pair<String, String> {
        if (!ConfigManager.getBoolean(PREF_KEY, false)) return " " to ""
        val config = contentConfig()
        advanceCarouselIfNeeded(config)
        val leftExpression = config.left.getOrNull((carouselIndex % config.left.size).toInt()).orEmpty()
        val rightExpression = config.right.getOrNull((carouselIndex % config.right.size).toInt()).orEmpty()
        if (leftExpression.isBlank() && rightExpression.isBlank()) {
            return " " to ""
        }

        val left = renderExpressionSafely(leftExpression).ifBlank { " " }
        val right = renderExpressionSafely(rightExpression).let { rendered ->
            if (rendered.isBlank()) " " else rendered
        }
        return left to right
    }

    private fun resolveFocusNotificationTexts(): Pair<String, String> {
        val title = renderExpressionSafely(
            ConfigManager.getString(PREF_KEY_NOTIFICATION_TITLE, ""),
        ).ifBlank { " " }
        val content = renderExpressionSafely(
            ConfigManager.getString(PREF_KEY_NOTIFICATION_CONTENT, ""),
        )
        return title to content
    }

    private fun resolveFocusContent(context: Context, enabled: Boolean): FocusContent {
        if (!enabled) return FocusContent(" ", "", null, null, null, null, "disabled")
        return when (ConfigManager.getString(PREF_KEY_FOCUS_CONTENT_TYPE, FOCUS_CONTENT_NOTIFICATION)) {
            FOCUS_CONTENT_PERFORMANCE -> resolvePerformanceFocusContent(context)
            FOCUS_CONTENT_DEVICE -> resolveDeviceFocusContent(context)
            FOCUS_CONTENT_CHARGING -> resolveChargingFocusContent(context)
            else -> {
                val texts = resolveFocusNotificationTexts()
                FocusContent(
                    title = texts.first,
                    content = texts.second,
                    remoteViews = null,
                    nightRemoteViews = null,
                    islandExpandRemoteViews = null,
                    aodRemoteViews = null,
                    signature = "notification\u0000${texts.first}\u0000${texts.second}",
                )
            }
        }
    }

    private fun resolvePerformanceFocusContent(context: Context): FocusContent {
        val snapshot = IslandDataManager.performanceSnapshot()
        samplePerformanceTrend(snapshot)
        val cpuText = formatPercent(snapshot.cpuUsagePercent)
        val gpuText = formatPercent(snapshot.gpuUsagePercent)
        val memoryText = formatPercent(snapshot.memoryUsagePercent)
        val cpuTemperature = snapshot.cpuTemperatureCelsius?.let {
            String.format(Locale.US, "%.0f", it)
        } ?: "--"
        val batteryTemperature = snapshot.batteryTemperatureCelsius?.let {
            String.format(Locale.US, "%.0f", it)
        } ?: "--"
        val batteryPower = snapshot.batteryPowerWatt?.let {
            String.format(Locale.US, "%.1fW", it)
        } ?: "--W"
        val temperatureText =
            "CPU $cpuTemperature°C · BATTERY $batteryTemperature°C · $batteryPower"
        val networkText =
            "↓ ${formatRate(snapshot.downloadBytesPerSecond)}  ↑ ${formatRate(snapshot.uploadBytesPerSecond)}"
        val expandPalette = resolveExpandPalette()
        val signature = buildString {
            append("performance\u0000")
            append(cpuText).append('\u0000')
            append(gpuText).append('\u0000')
            append(memoryText).append('\u0000')
            append(temperatureText).append('\u0000')
            append(networkText).append('\u0000')
            append(cpuTrend.joinToString(",") { it.roundToInt().toString() }).append('\u0000')
            append(gpuTrend.joinToString(",") { it.roundToInt().toString() }).append('\u0000')
            append(memoryTrend.joinToString(",") { it.roundToInt().toString() }).append('\u0000')
            append(expandPalette.primary)
        }
        cachedPanelFocusContent?.takeIf { it.signature == signature }?.let { return it }
        val moduleContext = context.moduleContext()
        val lightChart = drawPerformanceChart(context, darkMode = false)
        val darkChart = drawPerformanceChart(context, darkMode = true)
        fun buildRemoteViews(palette: PanelPalette, chart: Bitmap) = RemoteViews(
            moduleContext.packageName,
            R.layout.focus_notification_performance,
        ).apply {
            setTextViewText(R.id.performance_cpu, cpuText)
            setTextViewText(R.id.performance_gpu, gpuText)
            setTextViewText(R.id.performance_memory, memoryText)
            setTextViewText(R.id.performance_temperature, temperatureText)
            setTextViewText(R.id.performance_network, networkText)
            setTextColor(R.id.performance_cpu_label, palette.secondary)
            setTextColor(R.id.performance_gpu_label, palette.secondary)
            setTextColor(R.id.performance_memory_label, palette.secondary)
            setTextColor(R.id.performance_cpu, palette.cpu)
            setTextColor(R.id.performance_gpu, palette.gpu)
            setTextColor(R.id.performance_memory, palette.memory)
            setTextColor(R.id.performance_temperature, palette.secondary)
            setTextColor(R.id.performance_network, palette.secondary)
            setImageViewBitmap(
                R.id.performance_chart,
                chart,
            )
        }
        val lightRemoteViews = buildRemoteViews(PanelPalette.LIGHT, lightChart)
        val darkRemoteViews = buildRemoteViews(PanelPalette.DARK, darkChart)
        val expandRemoteViews = buildRemoteViews(
            expandPalette,
            if (expandPalette.dark) darkChart else lightChart,
        )
        val aodRemoteViews = buildRemoteViews(PanelPalette.AOD, darkChart)
        return FocusContent(
            title = "性能概览",
            content = "$cpuText · $memoryText",
            remoteViews = lightRemoteViews,
            nightRemoteViews = darkRemoteViews,
            islandExpandRemoteViews = expandRemoteViews,
            aodRemoteViews = aodRemoteViews,
            signature = signature,
        ).also { cachedPanelFocusContent = it }
    }

    private fun resolveDeviceFocusContent(context: Context): FocusContent {
        val snapshot = IslandDataManager.devicePanelSnapshot()
        val cpuPercent = snapshot.cpuUsagePercent?.roundToInt()?.coerceIn(0, 100) ?: 0
        val memoryPercent = snapshot.memoryUsagePercent?.roundToInt()?.coerceIn(0, 100) ?: 0
        val cpuText = snapshot.cpuUsagePercent?.let { "$cpuPercent%" } ?: "--%"
        val memoryText = snapshot.memoryUsagePercent?.let { "$memoryPercent%" } ?: "--%"
        val customIconPath = ConfigManager.getString(PREF_KEY_CUSTOM_ICON_PATH, "")
        val expandPalette = resolveExpandPalette()
        val signature = listOf(
            "device",
            snapshot.manufacturer,
            snapshot.model,
            snapshot.chipset,
            snapshot.uptime,
            cpuText,
            memoryText,
            customIconPath,
            expandPalette.primary,
        ).joinToString("\u0000")
        cachedPanelFocusContent?.takeIf { it.signature == signature }?.let { return it }
        val moduleContext = context.moduleContext()
        val logo = (
            loadCustomIcon(customIconPath)
                ?: Icon.createWithResource(moduleContext, R.drawable.ic_launcher)
            ).toRounded(context)
        fun buildRemoteViews(palette: PanelPalette) = RemoteViews(
            moduleContext.packageName,
            R.layout.focus_notification_device,
        ).apply {
            setImageViewIcon(R.id.device_logo, logo)
            setTextViewText(
                R.id.device_name,
                listOf(snapshot.manufacturer, snapshot.model)
                    .filter { it.isNotBlank() }
                    .joinToString(" "),
            )
            setTextViewText(R.id.device_chipset, snapshot.chipset)
            setTextViewText(R.id.device_uptime, "运行时间 ${snapshot.uptime}")
            setTextViewText(R.id.device_cpu_value, cpuText)
            setTextViewText(R.id.device_memory_value, memoryText)
            setProgressBar(R.id.device_cpu_progress, 100, cpuPercent, false)
            setProgressBar(R.id.device_memory_progress, 100, memoryPercent, false)
            setColorStateList(
                R.id.device_cpu_progress,
                "setProgressTintList",
                ColorStateList.valueOf(palette.primary),
            )
            setColorStateList(
                R.id.device_cpu_progress,
                "setProgressBackgroundTintList",
                ColorStateList.valueOf(palette.progressTrack),
            )
            setColorStateList(
                R.id.device_memory_progress,
                "setProgressTintList",
                ColorStateList.valueOf(palette.primary),
            )
            setColorStateList(
                R.id.device_memory_progress,
                "setProgressBackgroundTintList",
                ColorStateList.valueOf(palette.progressTrack),
            )
            setTextColor(R.id.device_name, palette.primary)
            setTextColor(R.id.device_chipset, palette.secondary)
            setTextColor(R.id.device_uptime, palette.tertiary)
            setTextColor(R.id.device_cpu_label, palette.secondary)
            setTextColor(R.id.device_memory_label, palette.secondary)
            setTextColor(R.id.device_cpu_value, palette.primary)
            setTextColor(R.id.device_memory_value, palette.primary)
        }
        val lightRemoteViews = buildRemoteViews(PanelPalette.LIGHT)
        val darkRemoteViews = buildRemoteViews(PanelPalette.DARK)
        val expandRemoteViews = buildRemoteViews(expandPalette)
        val aodRemoteViews = buildRemoteViews(PanelPalette.AOD)
        return FocusContent(
            title = snapshot.manufacturer,
            content = snapshot.model,
            remoteViews = lightRemoteViews,
            nightRemoteViews = darkRemoteViews,
            islandExpandRemoteViews = expandRemoteViews,
            aodRemoteViews = aodRemoteViews,
            signature = signature,
        ).also { cachedPanelFocusContent = it }
    }

    private fun resolveChargingFocusContent(context: Context): FocusContent {
        val snapshot = IslandDataManager.chargingPanelSnapshot()
        updateChargingSession(snapshot)
        val powerText = snapshot.powerWatt?.let { String.format(Locale.US, "%.1fW", it) } ?: "--W"
        val primaryText = if (snapshot.isCharging || snapshot.powerWatt == null) powerText else "-$powerText"
        val currentText = snapshot.currentAmp?.let { String.format(Locale.US, "%.2fA", it) } ?: "--A"
        val voltageText = snapshot.voltageVolt?.let { String.format(Locale.US, "%.2fV", it) } ?: "--V"
        val temperatureText = snapshot.temperatureCelsius?.let {
            String.format(Locale.US, "%.1f°C", it)
        } ?: "--°C"
        val expandPalette = resolveExpandPalette()
        val signature = buildString {
            append("charging\u0000")
            append(snapshot.isCharging).append('\u0000')
            append(primaryText).append('\u0000')
            append(currentText).append('\u0000')
            append(voltageText).append('\u0000')
            append(temperatureText).append('\u0000')
            append(chargingChartMode.name).append('\u0000')
            append(chargingTrend.size).append('\u0000')
            chargingTrend.lastOrNull()?.let {
                append(it.elapsedMillis).append(':')
                append(it.powerWatt).append(':')
                append(it.levelPercent).append(':')
                append(it.temperatureCelsius)
            }
            append('\u0000').append(expandPalette.primary)
        }
        cachedPanelFocusContent?.takeIf { it.signature == signature }?.let { return it }
        val moduleContext = context.moduleContext()
        val switchIntent = PendingIntent.getBroadcast(
            context,
            CHARGING_MODE_REQUEST_CODE,
            Intent(ACTION_SWITCH_CHARGING_CHART).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val lightChart = drawChargingChart(context, darkMode = false)
        val darkChart = drawChargingChart(context, darkMode = true)
        fun buildRemoteViews(
            palette: PanelPalette,
            chart: Bitmap,
            interactive: Boolean = true,
        ) = RemoteViews(
            moduleContext.packageName,
            R.layout.focus_notification_charging,
        ).apply {
            setTextViewText(R.id.charging_primary, primaryText)
            setTextViewText(
                R.id.charging_mode,
                if (!snapshot.isCharging && chargingChartMode == ChargingChartMode.POWER) {
                    "耗电"
                } else {
                    chargingChartMode.label
                },
            )
            setTextViewText(
                R.id.charging_details,
                "$currentText · $voltageText · $temperatureText",
            )
            setTextColor(R.id.charging_primary, palette.primary)
            setTextColor(R.id.charging_details, palette.secondary)
            setTextColor(R.id.charging_mode, palette.secondary)
            setImageViewBitmap(R.id.charging_chart, chart)
            if (interactive) {
                setOnClickPendingIntent(R.id.charging_mode, switchIntent)
                setOnClickPendingIntent(R.id.charging_chart_container, switchIntent)
            } else {
                setViewVisibility(R.id.charging_mode, View.GONE)
            }
        }
        val lightRemoteViews = buildRemoteViews(PanelPalette.LIGHT, lightChart)
        val darkRemoteViews = buildRemoteViews(PanelPalette.DARK, darkChart)
        val expandRemoteViews = buildRemoteViews(
            expandPalette,
            if (expandPalette.dark) darkChart else lightChart,
        )
        val aodRemoteViews = buildRemoteViews(
            PanelPalette.AOD,
            darkChart,
            interactive = false,
        )
        return FocusContent(
            title = if (snapshot.isCharging) "充电" else "耗电",
            content = primaryText,
            remoteViews = lightRemoteViews,
            nightRemoteViews = darkRemoteViews,
            islandExpandRemoteViews = expandRemoteViews,
            aodRemoteViews = aodRemoteViews,
            signature = signature,
        ).also { cachedPanelFocusContent = it }
    }

    private fun updateChargingSession(snapshot: IslandDataManager.ChargingPanelSnapshot) {
        val now = SystemClock.elapsedRealtime()
        if (chargingSessionStartedAt == 0L || chargingSessionIsCharging != snapshot.isCharging) {
            chargingTrend.clear()
            chargingSessionStartedAt = now
            lastChargingSampleAt = 0L
            chargingSampleIntervalMs = CHARGING_SAMPLE_INTERVAL_MS
            chargingChartMode = ChargingChartMode.POWER
            chargingSessionIsCharging = snapshot.isCharging
        }
        if (lastChargingSampleAt != 0L && now - lastChargingSampleAt < chargingSampleIntervalMs) return
        lastChargingSampleAt = now
        chargingTrend.addLast(
            ChargingSample(
                elapsedMillis = now - chargingSessionStartedAt,
                powerWatt = snapshot.powerWatt,
                levelPercent = snapshot.levelPercent,
                temperatureCelsius = snapshot.temperatureCelsius,
            ),
        )
        if (chargingTrend.size > CHARGING_MAX_SAMPLES) {
            if (snapshot.isCharging) {
                compactChargingTrend()
            } else {
                chargingTrend.removeFirst()
            }
        }
    }

    private fun resetChargingSession() {
        chargingTrend.clear()
        chargingSessionStartedAt = 0L
        lastChargingSampleAt = 0L
        chargingSampleIntervalMs = CHARGING_SAMPLE_INTERVAL_MS
        chargingChartMode = ChargingChartMode.POWER
        chargingSessionIsCharging = null
    }

    private fun compactChargingTrend() {
        if (chargingTrend.size < 3) return
        val values = chargingTrend.toList()
        chargingTrend.clear()
        chargingTrend.addLast(values.first())
        var index = 1
        while (index < values.lastIndex) {
            val first = values[index]
            val second = values.getOrNull(index + 1) ?: first
            chargingTrend.addLast(
                ChargingSample(
                    elapsedMillis = second.elapsedMillis,
                    powerWatt = averageNullable(first.powerWatt, second.powerWatt),
                    levelPercent = averageNullable(first.levelPercent, second.levelPercent),
                    temperatureCelsius = averageNullable(
                        first.temperatureCelsius,
                        second.temperatureCelsius,
                    ),
                ),
            )
            index += 2
        }
        if (chargingTrend.last().elapsedMillis != values.last().elapsedMillis) {
            chargingTrend.addLast(values.last())
        }
        chargingSampleIntervalMs *= 2L
    }

    private fun averageNullable(first: Double?, second: Double?): Double? = when {
        first != null && second != null -> (first + second) / 2.0
        first != null -> first
        else -> second
    }

    private fun drawChargingChart(context: Context, darkMode: Boolean): Bitmap {
        val density = context.resources.displayMetrics.density.coerceAtMost(2f)
        val width = 660
        val height = 168
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val left = 78f
        val right = width - 10f
        val bottom = height - 25f
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (darkMode) Color.argb(145, 255, 255, 255) else Color.argb(160, 0, 0, 0)
            textSize = 18f * density.coerceAtMost(1.4f)
        }
        val axisLabelPaint = Paint(labelPaint).apply {
            textAlign = Paint.Align.RIGHT
        }
        val top = 3f - axisLabelPaint.fontMetrics.ascent
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (darkMode) Color.argb(25, 255, 255, 255) else Color.argb(30, 0, 0, 0)
            strokeWidth = density
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = chargingChartMode.color(darkMode)
            strokeWidth = 2f * density
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            val lineColor = chargingChartMode.color(darkMode)
            color = Color.argb(
                if (darkMode) 38 else 48,
                Color.red(lineColor),
                Color.green(lineColor),
                Color.blue(lineColor),
            )
            style = Paint.Style.FILL
        }
        val samples = chargingTrend.toList()
        val values = samples.mapNotNull { sample ->
            chargingChartMode.value(sample)?.let { sample.elapsedMillis to it }
        }
        val rawMax = values.maxOfOrNull { it.second } ?: chargingChartMode.defaultMax
        val maxValue = chargingChartMode.axisMax(rawMax)
        for (row in 0..2) {
            val y = top + (bottom - top) * row / 2f
            canvas.drawLine(left, y, right, y, gridPaint)
            val value = maxValue * (2 - row) / 2.0
            val baseline = if (row == 0) top else y - axisLabelPaint.fontMetrics.ascent / 2f -
                    axisLabelPaint.fontMetrics.descent / 2f
            canvas.drawText(
                chargingChartMode.formatAxis(value),
                left - 10f,
                baseline,
                axisLabelPaint,
            )
        }
        val firstElapsed = samples.firstOrNull()?.elapsedMillis ?: 0L
        val maxElapsed = samples.lastOrNull()?.elapsedMillis?.coerceAtLeast(firstElapsed + 1L)
            ?: (firstElapsed + 1L)
        val visibleElapsed = (maxElapsed - firstElapsed).coerceAtLeast(1L)
        canvas.drawText("0", left, height - 4f, labelPaint)
        val middleElapsedLabel = formatChargingElapsed(visibleElapsed / 2L)
        canvas.drawText(
            middleElapsedLabel,
            (left + right) / 2f - labelPaint.measureText(middleElapsedLabel) / 2f,
            height - 4f,
            labelPaint,
        )
        val elapsedLabel = formatChargingElapsed(visibleElapsed)
        canvas.drawText(
            elapsedLabel,
            right - labelPaint.measureText(elapsedLabel),
            height - 4f,
            labelPaint,
        )
        if (values.size >= 2) {
            val linePath = Path()
            var firstX = left
            var lastX = left
            values.forEachIndexed { index, (elapsed, value) ->
                val x = left + (right - left) * (elapsed - firstElapsed) / visibleElapsed.toFloat()
                val y = bottom - (bottom - top) * (value / maxValue).coerceIn(0.0, 1.0).toFloat()
                if (index == 0) {
                    firstX = x
                    linePath.moveTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                }
                lastX = x
            }
            val fillPath = Path(linePath).apply {
                lineTo(lastX, bottom)
                lineTo(firstX, bottom)
                close()
            }
            canvas.drawPath(fillPath, fillPaint)
            canvas.drawPath(linePath, linePaint)
        }
        return bitmap
    }

    private fun formatChargingElapsed(elapsedMillis: Long): String {
        if (elapsedMillis < 60000L) return "${elapsedMillis / 1000L}s"
        val minutes = elapsedMillis / 60000L
        return if (minutes >= 60L) "${minutes / 60L}h${minutes % 60L}m" else "${minutes}m"
    }

    private fun registerChargingPanelReceiver(context: Context) {
        if (chargingReceiverRegistered) return
        chargingReceiverRegistered = true
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action != ACTION_SWITCH_CHARGING_CHART) return
                if (ConfigManager.getString(PREF_KEY_FOCUS_CONTENT_TYPE, FOCUS_CONTENT_NOTIFICATION) !=
                    FOCUS_CONTENT_CHARGING
                ) return
                chargingChartMode = chargingChartMode.next()
                appContext?.let { updateKeepIslandContent(it, force = true) }
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(
                receiver,
                IntentFilter(ACTION_SWITCH_CHARGING_CHART),
                Context.RECEIVER_NOT_EXPORTED,
            )
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, IntentFilter(ACTION_SWITCH_CHARGING_CHART))
        }
    }

    private fun registerDisplayTimingReceiver(context: Context) {
        if (displayTimingReceiverRegistered) return
        displayTimingReceiverRegistered = true
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                val nextPowerConnected = when (intent?.action) {
                    Intent.ACTION_POWER_CONNECTED -> true
                    Intent.ACTION_POWER_DISCONNECTED -> false
                    Intent.ACTION_BATTERY_CHANGED ->
                        intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                    else -> return
                }
                val powerStateChanged = powerConnected != nextPowerConnected
                powerConnected = nextPowerConnected
                mainHandler.post {
                    if (powerStateChanged) {
                        if (!matchesDisplayTiming()) resetChargingSession()
                        evaluateKeepIsland()
                    }
                    if (intent.action == Intent.ACTION_BATTERY_CHANGED &&
                        isDozeDisplayState(context, defaultDisplayState(context))
                    ) {
                        scheduleContentUpdateFromDataChange()
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        val stickyIntent = if (android.os.Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        powerConnected = stickyIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
    }

    private fun registerSettingsRefreshReceiver(context: Context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action == ACTION_REFRESH_KEEP_ISLAND) refreshFromSettings()
            }
        }
        val filter = IntentFilter(ACTION_REFRESH_KEEP_ISLAND)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(
                receiver,
                filter,
                IslandDispatcher.PERM,
                null,
                Context.RECEIVER_EXPORTED,
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter, IslandDispatcher.PERM, null)
        }
    }

    private fun createLaunchAppIntent(context: Context): PendingIntent? {
        val intent = context.packageManager
            .getLaunchIntentForPackage("io.github.hyperisland")
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            ?: return null
        return PendingIntent.getActivity(
            context,
            KEEP_ISLAND_NOTIF_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun samplePerformanceTrend(snapshot: IslandDataManager.PerformanceSnapshot) {
        val now = System.currentTimeMillis()
        if (now - lastPerformanceSampleAt < DATA_UPDATE_INTERVAL_MS) return
        lastPerformanceSampleAt = now
        appendTrend(cpuTrend, snapshot.cpuUsagePercent?.toFloat() ?: 0f)
        appendTrend(gpuTrend, snapshot.gpuUsagePercent?.toFloat() ?: 0f)
        appendTrend(memoryTrend, snapshot.memoryUsagePercent?.toFloat() ?: 0f)
    }

    private fun appendTrend(trend: ArrayDeque<Float>, value: Float) {
        trend.addLast(value.coerceIn(0f, 100f))
        while (trend.size > PERFORMANCE_TREND_POINTS) trend.removeFirst()
    }

    private fun drawPerformanceChart(context: Context, darkMode: Boolean): Bitmap {
        val density = context.resources.displayMetrics.density.coerceAtMost(2f)
        val width = 660
        val height = 128
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val horizontalPadding = 3f * density
        val verticalPadding = 4f * density
        val chartWidth = width - horizontalPadding * 2f
        val chartHeight = height - verticalPadding * 2f
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (darkMode) Color.argb(24, 255, 255, 255) else Color.argb(28, 0, 0, 0)
            strokeWidth = density
        }
        val cpuLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (darkMode) Color.rgb(70, 190, 240) else Color.rgb(0, 118, 158)
            strokeWidth = 2f * density
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val ramLinePaint = Paint(cpuLinePaint).apply {
            color = if (darkMode) Color.rgb(117, 212, 130) else Color.rgb(40, 122, 48)
        }
        val gpuLinePaint = Paint(cpuLinePaint).apply {
            color = if (darkMode) Color.rgb(255, 184, 107) else Color.rgb(168, 82, 0)
        }
        val cpuFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(cpuLinePaint.color, if (darkMode) 42 else 50)
            style = Paint.Style.FILL
        }
        val ramFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(ramLinePaint.color, if (darkMode) 30 else 42)
            style = Paint.Style.FILL
        }
        val gpuFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(gpuLinePaint.color, if (darkMode) 24 else 38)
            style = Paint.Style.FILL
        }
        for (row in 0..4) {
            val y = verticalPadding + chartHeight * row / 4f
            canvas.drawLine(horizontalPadding, y, width - horizontalPadding, y, gridPaint)
        }
        for (column in 0..6) {
            val x = horizontalPadding + chartWidth * column / 6f
            canvas.drawLine(x, verticalPadding, x, height - verticalPadding, gridPaint)
        }
        drawTrend(
            canvas = canvas,
            values = memoryTrend,
            width = chartWidth,
            height = chartHeight,
            offsetX = horizontalPadding,
            offsetY = verticalPadding,
            linePaint = ramLinePaint,
            fillPaint = ramFillPaint,
        )
        drawTrend(
            canvas = canvas,
            values = gpuTrend,
            width = chartWidth,
            height = chartHeight,
            offsetX = horizontalPadding,
            offsetY = verticalPadding,
            linePaint = gpuLinePaint,
            fillPaint = gpuFillPaint,
        )
        drawTrend(
            canvas = canvas,
            values = cpuTrend,
            width = chartWidth,
            height = chartHeight,
            offsetX = horizontalPadding,
            offsetY = verticalPadding,
            linePaint = cpuLinePaint,
            fillPaint = cpuFillPaint,
        )
        return bitmap
    }

    private fun isDarkMode(context: Context): Boolean =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES

    private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(
        alpha,
        Color.red(color),
        Color.green(color),
        Color.blue(color),
    )

    private fun drawTrend(
        canvas: Canvas,
        values: ArrayDeque<Float>,
        width: Float,
        height: Float,
        offsetX: Float,
        offsetY: Float,
        linePaint: Paint,
        fillPaint: Paint,
    ) {
        if (values.size < 2) return
        val step = width / (PERFORMANCE_TREND_POINTS - 1)
        val start = PERFORMANCE_TREND_POINTS - values.size
        val linePath = Path()
        var firstX = 0f
        var lastX = 0f
        values.forEachIndexed { index, value ->
            val x = offsetX + (start + index) * step
            val y = offsetY + height * (1f - value / 100f)
            if (index == 0) {
                firstX = x
                linePath.moveTo(x, y)
            } else {
                linePath.lineTo(x, y)
            }
            lastX = x
        }
        val fillPath = Path(linePath).apply {
            lineTo(lastX, offsetY + height)
            lineTo(firstX, offsetY + height)
            close()
        }
        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(linePath, linePaint)
    }

    private fun formatPercent(value: Double?): String =
        value?.let { "${it.roundToInt().coerceIn(0, 100)}%" } ?: "--%"

    private fun formatRate(bytesPerSecond: Double): String {
        val value = bytesPerSecond.coerceAtLeast(0.0)
        return when {
            value >= 1024.0 * 1024.0 -> String.format(Locale.US, "%.1fM", value / 1024.0 / 1024.0)
            value >= 1024.0 -> String.format(Locale.US, "%.0fK", value / 1024.0)
            else -> "${value.roundToInt()}B"
        }
    }

    private fun contentSignature(
        texts: Pair<String, String>,
        focusEnabled: Boolean,
        focusSignature: String,
        showIslandIcon: Boolean,
        customIconPath: String,
        highlightColor: String?,
        showLeftHighlight: Boolean,
        showRightHighlight: Boolean,
    ): String {
        return "${texts.first}\u0000${texts.second}\u0000$focusEnabled\u0000" +
                "$focusSignature\u0000$showIslandIcon\u0000" +
                "$customIconPath\u0000${highlightColor.orEmpty()}\u0000" +
                "$showLeftHighlight\u0000$showRightHighlight"
    }

    private fun loadCustomIcon(path: String): Icon? {
        if (path.isBlank()) return null
        return runCatching {
            val file = File(path)
            if (!file.isFile || !file.canRead()) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sampleSize = 1
            while (bounds.outWidth / sampleSize > 512 || bounds.outHeight / sampleSize > 512) {
                sampleSize *= 2
            }
            val bitmap = BitmapFactory.decodeFile(
                path,
                BitmapFactory.Options().apply { inSampleSize = sampleSize },
            ) ?: return null
            Icon.createWithBitmap(bitmap)
        }.getOrNull()
    }

    private fun renderExpressionSafely(expression: String): String {
        if (expression.isBlank()) return ""
        return runCatching { IslandDataManager.renderExpression(expression) }
            .getOrDefault("")
    }

    private fun scheduleContentUpdateFromDataChange() {
        val ctx = appContext ?: return
        if (!posted || !shouldPostKeepNotification() || !hasConfiguredKeepIslandContent()) return
        val displayState = defaultDisplayState(ctx)
        if (displayState == Display.STATE_OFF) return
        val minimumInterval = if (isDozeDisplayState(ctx, displayState)) {
            AOD_DATA_UPDATE_INTERVAL_MS
        } else {
            DATA_UPDATE_INTERVAL_MS
        }
        if (System.currentTimeMillis() - lastContentUpdateAt < minimumInterval) return
        mainHandler.post { updateKeepIslandContent(ctx) }
    }

    private fun schedulePeriodicDataUpdate() {
        if (periodicDataUpdateRunnable != null) return
        cancelPeriodicDataUpdate()
        val initialContext = appContext ?: return
        if (defaultDisplayState(initialContext) != Display.STATE_ON) return
        periodicDataUpdateRunnable = Runnable {
            val ctx = appContext
            if (ctx != null && defaultDisplayState(ctx) == Display.STATE_ON && posted &&
                shouldPostKeepNotification() && hasConfiguredKeepIslandContent()
            ) {
                val alignToSecond = hasSecondTimePlaceholder()
                updateKeepIslandContent(ctx, force = alignToSecond)
                mainHandler.postDelayed(
                    periodicDataUpdateRunnable!!,
                    nextDataUpdateDelay(alignToSecond),
                )
            } else {
                periodicDataUpdateRunnable = null
            }
        }
        mainHandler.postDelayed(
            periodicDataUpdateRunnable!!,
            nextDataUpdateDelay(hasSecondTimePlaceholder()),
        )
    }

    private fun nextDataUpdateDelay(alignToSecond: Boolean): Long {
        if (!alignToSecond) return DATA_UPDATE_INTERVAL_MS
        return 1000L - System.currentTimeMillis() % 1000L
    }

    private fun hasSecondTimePlaceholder(): Boolean {
        if (!ConfigManager.getBoolean(PREF_KEY, false)) return false
        val config = contentConfig()
        return (config.left + config.right).any { expression ->
            SECOND_TIME_PLACEHOLDERS.any(expression::contains)
        }
    }

    private fun defaultDisplayState(context: Context): Int? = runCatching {
        context.getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
            ?.state
    }.getOrNull()

    private fun isDozeDisplayState(context: Context, state: Int?): Boolean {
        if (state == Display.STATE_DOZE || state == Display.STATE_DOZE_SUSPEND) return true
        if (state != null && state != Display.STATE_UNKNOWN) return false
        val powerManager = context.getSystemService(PowerManager::class.java)
        return powerManager?.isInteractive == false
    }

    private fun cancelPeriodicDataUpdate() {
        periodicDataUpdateRunnable?.let { mainHandler.removeCallbacks(it) }
        periodicDataUpdateRunnable = null
    }

    private fun hasConfiguredKeepIslandContent(): Boolean {
        val config = contentConfig()
        val hasIslandContent = ConfigManager.getBoolean(PREF_KEY, false) &&
                (config.left.any { it.isNotBlank() } || config.right.any { it.isNotBlank() })
        if (hasIslandContent) return true
        if (!focusContentActive()) return false
        if (ConfigManager.getString(PREF_KEY_FOCUS_CONTENT_TYPE, FOCUS_CONTENT_NOTIFICATION) in
            setOf(FOCUS_CONTENT_PERFORMANCE, FOCUS_CONTENT_DEVICE, FOCUS_CONTENT_CHARGING)
        ) return true
        return ConfigManager.getString(PREF_KEY_NOTIFICATION_TITLE, "").isNotBlank() ||
                ConfigManager.getString(PREF_KEY_NOTIFICATION_CONTENT, "").isNotBlank()
    }

    private fun contentConfig(): ContentConfig {
        cachedContentConfig?.let { return it }
        return ContentConfig(
            left = decodeContentList(
                ConfigManager.getString(PREF_KEY_LEFT_CONTENT, ""),
                DEFAULT_LEFT_CONTENT,
                ConfigManager.contains(PREF_KEY_LEFT_CONTENT),
            ),
            right = decodeContentList(
                ConfigManager.getString(PREF_KEY_RIGHT_CONTENT, ""),
                DEFAULT_RIGHT_CONTENT,
                ConfigManager.contains(PREF_KEY_RIGHT_CONTENT),
            ),
            intervalMillis = ConfigManager.getInt(PREF_KEY_CAROUSEL_INTERVAL, 5)
                .coerceIn(1, 6000) * 1000L,
        ).also { cachedContentConfig = it }
    }
    
    private fun decodeContentList(raw: String, defaultContent: String, isConfigured: Boolean): List<String> {
        if (!isConfigured) return listOf(defaultContent)
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index -> array.optString(index) }
                .ifEmpty { listOf("") }
        }.getOrElse { listOf(raw) }
    }

    private fun advanceCarouselIfNeeded(config: ContentConfig) {
        val now = System.currentTimeMillis()
        if (lastCarouselAdvanceAt == 0L) {
            lastCarouselAdvanceAt = now
            return
        }
        val elapsed = now - lastCarouselAdvanceAt
        if (elapsed < config.intervalMillis) return
        carouselIndex += elapsed / config.intervalMillis
        lastCarouselAdvanceAt += elapsed / config.intervalMillis * config.intervalMillis
    }

    private fun cancelKeepIsland(context: android.content.Context) {
        try {
            IslandDispatcher.cancel(context, KEEP_ISLAND_NOTIF_ID)
            posted = false
            autoHideTrackingEnabled = false
            lastContentUpdateSignature = null
            cachedPanelFocusContent = null
            cancelPeriodicDataUpdate()
            cachedModule?.let { log(it, "keep island cancelled") }
        } catch (e: Exception) {
            cachedModule?.let { logError(it, "keep island cancel failed: ${e.message}") }
        }
    }

    private fun scheduleRestore() {
        cancelPendingRestore()
        val runnable = Runnable {
            restoreRunnable = null
            if (activeRealKeys.isNotEmpty()) return@Runnable
            evaluateKeepIsland()
        }
        restoreRunnable = runnable
        mainHandler.postDelayed(runnable, RESTORE_DELAY_MS)
    }

    private fun cancelPendingRestore() {
        restoreRunnable?.let { mainHandler.removeCallbacks(it) }
        restoreRunnable = null
    }

    private fun registerIslandDataManager(context: Context) {
        try {
            IslandDataManager.register(context)
            if (!dataListenerRegistered) {
                IslandDataManager.addListener(dataChangedListener)
                dataListenerRegistered = true
            }
        } catch (e: Throwable) {
            cachedModule?.let { logWarn(it, "island data manager register failed: ${e.message}") }
        }
    }

    private fun registerConfigurationCallbacks(context: Context) {
        if (configurationCallbacksRegistered) return
        configurationCallbacksRegistered = true
        context.registerComponentCallbacks(object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                mainHandler.post { evaluateKeepIsland() }
            }

            @Suppress("OVERRIDE_DEPRECATION")
            override fun onLowMemory() = Unit
        })
    }

    private fun registerStatusBarTintListener() {
        if (statusBarTintListenerRegistered) return
        statusBarTintListenerRegistered = true
        IslandTextColorHook.addStatusBarTintListener {
            val mode = ConfigManager.getString(
                PREF_KEY_EXPAND_TEXT_COLOR_MODE,
                EXPAND_TEXT_COLOR_WHITE,
            )
            if (mode != EXPAND_TEXT_COLOR_FOLLOW_STATUS_BAR &&
                mode != EXPAND_TEXT_COLOR_INVERT_STATUS_BAR
            ) return@addStatusBarTintListener
            mainHandler.post {
                val context = appContext ?: return@post
                if (posted && isPanelFocusContent()) {
                    updateKeepIslandContent(context, force = true)
                }
            }
        }
    }

    private fun resolveExpandPalette(): PanelPalette {
        val statusBarTint = IslandTextColorHook.getStatusBarTint()
        val primary = when (ConfigManager.getString(
            PREF_KEY_EXPAND_TEXT_COLOR_MODE,
            EXPAND_TEXT_COLOR_WHITE,
        )) {
            EXPAND_TEXT_COLOR_BLACK -> Color.BLACK
            EXPAND_TEXT_COLOR_FOLLOW_STATUS_BAR -> statusBarTint
            EXPAND_TEXT_COLOR_INVERT_STATUS_BAR ->
                if (isLightColor(statusBarTint)) Color.BLACK else Color.WHITE
            else -> Color.WHITE
        }
        return PanelPalette.fromPrimary(primary)
    }

    private fun isPanelFocusContent(): Boolean =
        ConfigManager.getString(PREF_KEY_FOCUS_CONTENT_TYPE, FOCUS_CONTENT_NOTIFICATION) in
                setOf(FOCUS_CONTENT_PERFORMANCE, FOCUS_CONTENT_DEVICE, FOCUS_CONTENT_CHARGING)

    private fun isLightColor(color: Int): Boolean =
        Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114 >= 128000

    private fun registerDisplayListener(context: Context) {
        if (displayListenerRegistered) return
        val displayManager = context.getSystemService(DisplayManager::class.java) ?: return
        displayListenerRegistered = true
        displayManager.registerDisplayListener(
            object : DisplayManager.DisplayListener {
                override fun onDisplayAdded(displayId: Int) = Unit

                override fun onDisplayRemoved(displayId: Int) = Unit


                override fun onDisplayChanged(displayId: Int) {
                    if (displayId != Display.DEFAULT_DISPLAY) return
                    mainHandler.post {
                        val state = defaultDisplayState(context)
                        cancelPeriodicDataUpdate()
                        if (state == Display.STATE_OFF) return@post
                        evaluateKeepIsland()
                        if (state == Display.STATE_ON && posted && hasConfiguredKeepIslandContent()) {
                            schedulePeriodicDataUpdate()
                        }
                    }
                }
            },
            mainHandler,
        )
    }

    private fun isLandscape(context: Context): Boolean {
        if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            return true
        }
        val rotation = runCatching {
            context.getSystemService(DisplayManager::class.java)
                ?.getDisplay(Display.DEFAULT_DISPLAY)
                ?.rotation
        }.getOrNull()
        return rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270
    }

    private fun foregroundPackage(context: Context): String {
        return runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            @Suppress("DEPRECATION")
            am.getRunningTasks(1).firstOrNull()?.topActivity?.packageName.orEmpty()
        }.getOrDefault("")
    }

    private data class ContentConfig(
        val left: List<String>,
        val right: List<String>,
        val intervalMillis: Long,
    )

    private data class FocusContent(
        val title: String,
        val content: String,
        val remoteViews: RemoteViews?,
        val nightRemoteViews: RemoteViews?,
        val islandExpandRemoteViews: RemoteViews?,
        val aodRemoteViews: RemoteViews?,
        val signature: String,
    )

    private data class PanelPalette(
        val primary: Int,
        val secondary: Int,
        val tertiary: Int,
        val cpu: Int,
        val gpu: Int,
        val memory: Int,
        val dark: Boolean,
    ) {
        val progressTrack: Int
            get() = Color.argb(
                if (dark) 40 else 31,
                Color.red(primary),
                Color.green(primary),
                Color.blue(primary),
            )

        companion object {
            val LIGHT = PanelPalette(
                primary = Color.argb(230, 0, 0, 0),
                secondary = Color.argb(166, 0, 0, 0),
                tertiary = Color.argb(115, 0, 0, 0),
                cpu = Color.rgb(0, 118, 158),
                gpu = Color.rgb(168, 82, 0),
                memory = Color.rgb(40, 122, 48),
                dark = false,
            )
            val DARK = PanelPalette(
                primary = Color.WHITE,
                secondary = Color.argb(191, 255, 255, 255),
                tertiary = Color.argb(143, 255, 255, 255),
                cpu = Color.rgb(114, 214, 255),
                gpu = Color.rgb(255, 184, 107),
                memory = Color.rgb(158, 228, 147),
                dark = true,
            )
            val AOD = DARK

            fun fromPrimary(primary: Int): PanelPalette {
                val useLightForeground = isLightColor(primary)
                return if (useLightForeground) {
                    DARK.copy(
                        primary = primary,
                        secondary = withAlpha(primary, 191),
                        tertiary = withAlpha(primary, 143),
                    )
                } else {
                    LIGHT.copy(
                        primary = primary,
                        secondary = withAlpha(primary, 166),
                        tertiary = withAlpha(primary, 115),
                    )
                }
            }

            private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(
                alpha,
                Color.red(color),
                Color.green(color),
                Color.blue(color),
            )

            private fun isLightColor(color: Int): Boolean =
                Color.red(color) * 299 + Color.green(color) * 587 +
                        Color.blue(color) * 114 >= 128000
        }
    }

    private data class ChargingSample(
        val elapsedMillis: Long,
        val powerWatt: Double?,
        val levelPercent: Double?,
        val temperatureCelsius: Double?,
    )

    private enum class ChargingChartMode(
        val label: String,
        val darkColor: Int,
        val lightColor: Int,
        val defaultMax: Double,
    ) {
        POWER("功率", Color.rgb(93, 220, 145), Color.rgb(20, 125, 68), 20.0),
        LEVEL("电量", Color.rgb(100, 190, 255), Color.rgb(0, 103, 170), 100.0),
        TEMPERATURE("温度", Color.rgb(255, 176, 90), Color.rgb(174, 78, 0), 45.0);

        fun color(darkMode: Boolean): Int = if (darkMode) darkColor else lightColor

        fun next(): ChargingChartMode = entries[(ordinal + 1) % entries.size]

        fun value(sample: ChargingSample): Double? = when (this) {
            POWER -> sample.powerWatt
            LEVEL -> sample.levelPercent
            TEMPERATURE -> sample.temperatureCelsius
        }

        fun axisMax(rawMax: Double): Double = when (this) {
            POWER -> ((rawMax.coerceAtLeast(5.0) / 5.0).toInt() + 1) * 5.0
            LEVEL -> 100.0
            TEMPERATURE -> ((rawMax.coerceAtLeast(30.0) / 5.0).toInt() + 1) * 5.0
        }

        fun formatAxis(value: Double): String = when (this) {
            POWER -> "${value.roundToInt()}W"
            LEVEL -> "${value.roundToInt()}%"
            TEMPERATURE -> "${value.roundToInt()}°"
        }
    }

    private const val DEFAULT_LEFT_CONTENT = "{time.HH:mm}"
    private const val DEFAULT_RIGHT_CONTENT = "{battery.level}"

    private const val FOCUS_CONTENT_NOTIFICATION = "notification"
    private const val FOCUS_CONTENT_PERFORMANCE = "performance"
    private const val FOCUS_CONTENT_DEVICE = "device"
    private const val FOCUS_CONTENT_CHARGING = "charging"

    private const val EXPAND_TEXT_COLOR_WHITE = "white"
    private const val EXPAND_TEXT_COLOR_FOLLOW_STATUS_BAR = "follow_status_bar"
    private const val EXPAND_TEXT_COLOR_INVERT_STATUS_BAR = "invert_status_bar"
    private const val EXPAND_TEXT_COLOR_BLACK = "black"
    private const val DISPLAY_TIMING_ALWAYS = "always"
    private const val DISPLAY_TIMING_CHARGING = "charging"
    private const val PERFORMANCE_TREND_POINTS = 24
    private const val CHARGING_MAX_SAMPLES = 120
    private const val CHARGING_SAMPLE_INTERVAL_MS = 5000L
    private const val AOD_DATA_UPDATE_INTERVAL_MS = 59000L
    private val SECOND_TIME_PLACEHOLDERS = listOf(
        "{time.ss}",
        "{time.HH:mm:ss}",
        "{time.h:mm:ss}",
        "{time.hh:mm:ss}",
    )
    private const val CHARGING_MODE_REQUEST_CODE = 0x434847
    private const val ACTION_SWITCH_CHARGING_CHART =
        "io.github.hyperisland.action.SWITCH_CHARGING_CHART"
}
