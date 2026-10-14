package io.github.hyperisland.xposed.hook.SystemUI.extensions

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import io.github.hyperisland.homeassistant.HaEntity
import io.github.hyperisland.homeassistant.HomeAssistantIconResolver
import io.github.hyperisland.homeassistant.HaSession
import io.github.hyperisland.homeassistant.HaStatus
import io.github.hyperisland.homeassistant.HaSubscription
import io.github.hyperisland.homeassistant.HaTemplate
import io.github.hyperisland.homeassistant.HaTemplateType
import io.github.hyperisland.homeassistant.HomeAssistantContract
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.hyperisland.xposed.islanddispatch.IslandDispatcher
import io.github.hyperisland.xposed.islanddispatch.definition.IslandRequest
import io.github.hyperisland.xposed.utils.moduleContext
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.concurrent.ConcurrentHashMap
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.RemoteViews
import android.app.PendingIntent

/**
 * Home Assistant 拓展：把 HA 实体状态实时映射成超级岛通知。
 *
 * 设计约束（与用户约定）：
 * - **不启动任何后台进程/前台服务**，只注入 SystemUI；连接生命周期跟随 SystemUI 进程，
 *   SystemUI 退出即全部释放；
 * - 通知链路完全复用现有 [IslandDispatcher]，不另造一套通知通道；
 * - 只有在「订阅的实体状态变化 + 条件命中 + 渲染结果与上次不同」时才投递，内容没变不更新；
 * - 每条订阅有稳定的 notifId，配合 `updatable` 复用同一个岛事件而不是不断新建。
 *
 * 配置来源与热更新：启动时从 [ConfigManager] 读；设置页改动后发
 * [HomeAssistantContract.ACTION_CONFIG_CHANGED]（值随 extra 带过来），收到即重连，
 * 因此总开关与订阅编辑都不需要重启 SystemUI。
 */
object HomeAssistantIslandHook : BaseHook() {

    private const val TAG = "HyperIsland[HomeAssistant]"

    /**
     * 自定义图标的采样上限（像素）。
     *
     * 图片会随通知 extras 过 Binder，192px 的 ARGB_8888 约 147KB，是这个链路上可以接受的量级；
     * 再大就会和面板图表位图抢 1MB 的事务预算。
     */
    private const val ICON_MAX_PX = 192

    @Volatile private var moduleRef: XposedModule? = null
    @Volatile private var appContext: Context? = null
    @Volatile private var initialized = false

    @Volatile private var runtimeEnabled = false
    @Volatile private var runtimeUrl = ""
    @Volatile private var runtimeToken = ""
    @Volatile private var runtimeSubscriptions: List<HaSubscription> = emptyList()

    @Volatile private var session: HaSession? = null

    /** 订阅 id → 上次投递的内容签名；用于「内容没变就不更新」。 */
    private val lastSignature = ConcurrentHashMap<String, String>()

    /** 订阅 id → 当前挂在岛上的 notifId；用于条件失效时精确取消。 */
    private val activeNotifIds = ConcurrentHashMap<String, Int>()

    /** 当前实体快照，供显示实体与跨实体占位符读取。 */
    private val entityCache = ConcurrentHashMap<String, HaEntity>()

    /** 仅一次模式的命中状态；离开条件后清零，下一次进入才再次投递。 */
    private val matchedSubscriptions = ConcurrentHashMap<String, Boolean>()
    private val carouselHandler = Handler(Looper.getMainLooper())
    private val carouselRunnables = ConcurrentHashMap<String, Runnable>()
    private val carouselIndices = ConcurrentHashMap<String, Int>()
    private val chargingHistory = ConcurrentHashMap<String, ArrayDeque<HaChargingSample>>()
    private val chargingModes = ConcurrentHashMap<String, HaChargingMode>()
    private const val EXTRA_SUBSCRIPTION_ID = "ha_subscription_id"
    private const val ACTION_SWITCH_CHARGING_CHART = "io.github.hyperisland.action.HA_SWITCH_CHARGING_CHART"

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            initialized = true
        }
        moduleRef = module
        runCatching {
            val method = param.defaultClassLoader
                .loadClass("android.app.Application")
                .getDeclaredMethod("onCreate")
            module.hook(method).intercept { chain ->
                val result = chain.proceed()
                val application = chain.thisObject as? android.app.Application
                if (application != null && appContext == null) initialize(application)
                result
            }
            log(module) { "hooked Application.onCreate" }
        }.onFailure {
            initialized = false
            logError(module, "hook failed: ${it.message}")
        }
    }

    private fun initialize(application: android.app.Application) {
        val context = application.applicationContext ?: application
        appContext = context
        moduleRef?.let { IslandDispatcher.register(context, it) }

        val filter = IntentFilter(HomeAssistantContract.ACTION_CONFIG_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(
                configReceiver,
                filter,
                IslandDispatcher.PERM,
                null,
                Context.RECEIVER_EXPORTED,
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(configReceiver, filter, IslandDispatcher.PERM, null)
        }

        val chargingFilter = IntentFilter(ACTION_SWITCH_CHARGING_CHART)
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(chargingModeReceiver, chargingFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(chargingModeReceiver, chargingFilter)
        }

        readConfigFromPrefs()
        applyConfig(reason = "init")
    }

    private val configReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            runtimeEnabled = intent.getBooleanExtra(
                HomeAssistantContract.EXTRA_ENABLED,
                runtimeEnabled,
            )
            intent.getStringExtra(HomeAssistantContract.EXTRA_URL)?.let { runtimeUrl = it }
            intent.getStringExtra(HomeAssistantContract.EXTRA_TOKEN)?.let { runtimeToken = it }
            intent.getStringExtra(HomeAssistantContract.EXTRA_SUBSCRIPTIONS)?.let {
                runtimeSubscriptions = HaSubscription.parseList(it)
            }
            applyConfig(reason = "broadcast")
        }
    }

    private val chargingModeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getStringExtra(EXTRA_SUBSCRIPTION_ID) ?: return
            val current = chargingModes[id] ?: HaChargingMode.POWER
            chargingModes[id] = HaChargingMode.entries[(current.ordinal + 1) % HaChargingMode.entries.size]
            val subscription = runtimeSubscriptions.firstOrNull { it.id == id } ?: return
            val values = entityCache.toMap()
            if (matchedSubscriptions[id] == true) {
                val island = HaTemplate.islandTexts(subscription, values)
                val focus = HaTemplate.focusTexts(subscription, values)
                postIsland(context, subscription, island, focus.first, focus.second)
            }
        }
    }

    private fun readConfigFromPrefs() {
        runtimeEnabled = ConfigManager.getBoolean(HomeAssistantContract.PREF_ENABLED, false)
        runtimeUrl = ConfigManager.getString(HomeAssistantContract.PREF_URL, "").trim()
        runtimeToken = ConfigManager.getString(HomeAssistantContract.PREF_TOKEN, "").trim()
        runtimeSubscriptions = HaSubscription.parseList(
            ConfigManager.getString(HomeAssistantContract.PREF_SUBSCRIPTIONS, ""),
        )
    }

    private fun applyConfig(reason: String) {
        stopSession()
        cancelAllIslands()

        val enabled = runtimeEnabled
        val url = runtimeUrl
        val token = runtimeToken
        val subscriptions = runtimeSubscriptions.filter { it.enabled && it.isValid }
        if (!enabled || url.isBlank() || token.isBlank() || subscriptions.isEmpty()) {
            moduleRef?.let {
                log(it) {
                    "idle ($reason): enabled=$enabled url=${url.isNotBlank()} " +
                        "token=${token.isNotBlank()} subscriptions=${subscriptions.size}"
                }
            }
            return
        }

        moduleRef?.let {
            log(it) { "starting session ($reason): ${subscriptions.size} subscription(s) → $url" }
        }
        val watchedEntityIds = subscriptions.flatMap { it.dataEntityIds }.toSet()
        val created = HaSession(url, token, sessionListener, watchedEntityIds)
        session = created
        created.start()
    }

    private fun stopSession() {
        session?.stop()
        session = null
    }

    private val sessionListener = object : HaSession.Listener {
        override fun onEntities(entities: List<HaEntity>) {
            val watched = runtimeSubscriptions.flatMap { it.dataEntityIds }.toSet()
            val relevant = if (watched.isEmpty()) emptyList() else entities.filter { it.entityId in watched }
            entityCache.clear()
            relevant.forEach { entityCache[it.entityId] = it }
            moduleRef?.let { module ->
                log(module) { "connected, ${relevant.size}/${entities.size} subscribed entities" }
                relevant.filter { it.icon.isNotBlank() }.forEach { entity ->
                    log(module) { "ha-icon snapshot entity=${entity.entityId} icon=${entity.icon} attrs=${entity.attributes.keys.sorted()}" }
                }
            }
        }

        override fun onStateChanged(entity: HaEntity) {
            entityCache[entity.entityId] = entity
            val watched = runtimeSubscriptions.any { it.dataEntityIds.contains(entity.entityId) }
            moduleRef?.let { module ->
                if (watched && entity.icon.isNotBlank()) log(module) {
                    "ha-icon event entity=${entity.entityId} icon=${entity.icon} state=${entity.state}"
                }
            }
            appContext?.let { handleStateChanged(it, entity) }
        }

        override fun onStatus(status: HaStatus, message: String?) {
            moduleRef?.let { module ->
                when (status) {
                    HaStatus.AuthFailed -> logError(module, "auth failed: ${message.orEmpty()}")
                    HaStatus.Failed -> logError(module, "connection lost: ${message.orEmpty()}")
                    else -> log(module) { "status=$status ${message.orEmpty()}" }
                }
            }
        }

        override fun onError(message: String) {
            // HA 拒绝命令时不会断开连接，只回一条 error；不记下来就会表现为「连上了但没数据」。
            moduleRef?.let { logError(it, "server error: $message") }
        }

        override fun onServerMessage(summary: String) {
            moduleRef?.let { log(it) { "server: $summary" } }
        }
    }

    private fun handleStateChanged(context: Context, entity: HaEntity) {
        runtimeSubscriptions.forEach { subscription ->
            if (!subscription.enabled) return@forEach
            val isTriggerEvent = subscription.triggerEntityId == entity.entityId
            val isDataEvent = subscription.dataEntityIds.contains(entity.entityId)
            if (!isTriggerEvent && !isDataEvent) return@forEach
            val triggerEntity = entityCache[subscription.triggerEntityId] ?: return@forEach
            val current = triggerEntity.valueOf(subscription.triggerAttribute)
            val matched = subscription.conditionType.matches(current, subscription.value)
            if (!matched) {
                matchedSubscriptions.remove(subscription.id)
                cancelIsland(context, subscription)
                return@forEach
            }
            matchedSubscriptions[subscription.id] = true
            val values = entityCache.toMap()
            val (title, content) = HaTemplate.focusTexts(subscription, values)
            val islandTexts = HaTemplate.islandTexts(subscription, values, carouselIndices[subscription.id] ?: 0)
            val signature = run {
                val base = HaTemplate.signature(title + islandTexts.first, content + islandTexts.second)
                // 设备面板的 3 行文本与两条进度不在 title/content 里，必须单独参与比较，否则进度变了岛不会刷新。
                if (subscription.templateType == HaTemplateType.DEVICE) {
                    base + "\u0000" + deviceSignature(subscription, values)
                } else {
                    base
                }
            }
            if (lastSignature[subscription.id] == signature) return@forEach
            lastSignature[subscription.id] = signature
            if (subscription.islandEnabled || subscription.focusEnabled) {
                postIsland(context, subscription, islandTexts, title, content)
            }
            if (subscription.islandEnabled && subscription.islandCarouselInterval > 0) scheduleCarousel(context, subscription)
        }
    }

    private fun postIsland(
        context: Context,
        subscription: HaSubscription,
        islandTexts: Pair<String, String>,
        focusTitle: String,
        focusContent: String,
    ) {
        val panelViews = panelRemoteViews(context, subscription)
        val values = entityCache.toMap()
        val request = IslandRequest(
            title = islandTexts.first,
            content = islandTexts.second,
            focusTitle = focusTitle,
            focusContent = focusContent,
            icon = islandIcon(subscription, values),
            focusIcon = resolveEntityIcon(subscription, values, focusIconSources(subscription)),
            notifId = subscription.notifId,
            timeoutSecs = Int.MAX_VALUE,
            firstFloat = subscription.islandFirstFloat && !activeNotifIds.containsKey(subscription.id),
            enableFloat = false,
            showNotification = subscription.focusEnabled,
            preserveStatusBarSmallIcon = false,
            isOngoing = true,
            islandOnly = !subscription.focusEnabled,
            islandEnabled = subscription.islandEnabled,
            showIslandIcon = subscription.showIslandIcon,
            updatable = true,
            focusRemoteViews = panelViews?.first,
            focusNightRemoteViews = panelViews?.second,
            focusIslandExpandRemoteViews = panelViews?.third,
            clearBeforePost = false,
            sourcePackage = "io.github.hyperisland",
            sourceChannelId = HomeAssistantContract.SOURCE_CHANNEL_ID,
        )
        val posted = IslandDispatcher.post(context, request)
        if (posted) {
            activeNotifIds[subscription.id] = subscription.notifId
        }
        moduleRef?.let {
            log(it) { "post island id=${subscription.triggerEntityId} content=${islandTexts.second} posted=$posted" }
        }
    }

    /**
     * 焦点面板统一入口（自定义模板没有面板时返回 null）：
     * 充电模板与设备面板模板各自返回 日间 / 夜间 / 展开 三份 RemoteViews。
     */
    private fun panelRemoteViews(
        context: Context,
        subscription: HaSubscription,
    ): Triple<RemoteViews, RemoteViews, RemoteViews>? = when {
        !subscription.focusEnabled -> null
        subscription.templateType == HaTemplateType.CHARGING -> chargingRemoteViews(context, subscription)
        subscription.templateType == HaTemplateType.DEVICE -> deviceRemoteViews(context, subscription)
        else -> null
    }

    /** Home Assistant 充电模板使用与 KeepIsland 相同的 RemoteViews 布局。 */
    private fun chargingRemoteViews(
        context: Context,
        subscription: HaSubscription,
    ): Triple<RemoteViews, RemoteViews, RemoteViews> {
        val values = entityCache.toMap()
        fun value(id: String, attribute: String): String {
            val entity = values[id] ?: return ""
            val raw = entity.valueOf(attribute)
            if (raw.isBlank()) return ""
            if (attribute.isBlank() || attribute == HaEntity.ATTRIBUTE_STATE) {
                return entity.displayValue
            }
            return raw
        }
        val power = value(subscription.chargingPowerEntityId, subscription.chargingPowerAttribute).ifBlank { "--W" }
        val current = value(subscription.chargingCurrentEntityId, subscription.chargingCurrentAttribute).ifBlank { "--A" }
        val voltage = value(subscription.chargingVoltageEntityId, subscription.chargingVoltageAttribute).ifBlank { "--V" }
        val mode = chargingModes[subscription.id] ?: HaChargingMode.POWER
        val history = chargingHistory.getOrPut(subscription.id) { ArrayDeque() }
        val now = android.os.SystemClock.elapsedRealtime()
        history.addLast(
            HaChargingSample(
                now,
                parseMeasurement(power),
                parseMeasurement(current),
                parseMeasurement(voltage),
            ),
        )
        while (history.size > 120) history.removeFirst()
        val switchIntent = PendingIntent.getBroadcast(context, subscription.notifId, Intent(ACTION_SWITCH_CHARGING_CHART).setPackage(context.packageName).putExtra(EXTRA_SUBSCRIPTION_ID, subscription.id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val primary = when (mode) {
            HaChargingMode.POWER -> power
            HaChargingMode.CURRENT -> current
            HaChargingMode.VOLTAGE -> voltage
        }
        val modeLabel = when (mode) {
            HaChargingMode.POWER -> "功率"
            HaChargingMode.CURRENT -> "电流"
            HaChargingMode.VOLTAGE -> "电压"
        }
        val iconName = focusIconName(subscription, values)
        fun build(primaryColor: Int, secondaryColor: Int, dark: Boolean): RemoteViews = RemoteViews(context.moduleContext().packageName, io.github.hyperisland.R.layout.focus_notification_charging).apply {
            setTextViewText(io.github.hyperisland.R.id.charging_primary, primary)
            setTextViewText(io.github.hyperisland.R.id.charging_details, "$current · $voltage")
            setTextViewText(io.github.hyperisland.R.id.charging_mode, modeLabel)
            setTextColor(io.github.hyperisland.R.id.charging_primary, primaryColor)
            setTextColor(io.github.hyperisland.R.id.charging_details, secondaryColor)
            setTextColor(io.github.hyperisland.R.id.charging_mode, secondaryColor)
            setImageViewBitmap(io.github.hyperisland.R.id.charging_chart, chargingChart(history.toList(), dark, mode))
            // 焦点通知面板同样调用 MDI 图标：直接拿数据源实体的 `mdi:*` 渲染成位图。
            val glyph = iconName?.let {
                HomeAssistantIconResolver.renderBitmap(it, context, if (dark) Color.WHITE else Color.BLACK)
            }
            if (glyph != null) {
                setImageViewBitmap(io.github.hyperisland.R.id.charging_icon, glyph)
                setViewVisibility(io.github.hyperisland.R.id.charging_icon, View.VISIBLE)
            } else {
                setViewVisibility(io.github.hyperisland.R.id.charging_icon, View.GONE)
            }
            setOnClickPendingIntent(io.github.hyperisland.R.id.charging_mode, switchIntent)
            setOnClickPendingIntent(io.github.hyperisland.R.id.charging_chart_container, switchIntent)
        }
        // 只生成日间 / 夜间两张位图，展开态复用夜间那张：同一个 Bitmap 实例在同一 Binder
        // 事务里只会序列化一次，能省掉三分之一体积。
        val light = build(Color.BLACK, Color.DKGRAY, false)
        val dark = build(Color.WHITE, Color.LTGRAY, true)
        return Triple(light, dark, dark)
    }
    private fun chargingChart(samples: List<HaChargingSample>, dark: Boolean, mode: HaChargingMode): Bitmap {
        // 位图会随通知 extras 走 Binder（nm.notify）：原设计 660x168 单张就 443KB，
        // 日间 + 夜间两张几乎顶满 1MB 事务上限，所以整体等比缩到 500x128。
        val width = 500f
        val height = 128f
        val k = width / 660f
        val bitmap = Bitmap.createBitmap(width.toInt(), height.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val left = 78f * k
        val right = width - 10f * k
        val bottom = height - 25f * k
        val top = 24f * k
        val lineColor = when (mode) {
            HaChargingMode.POWER -> if (dark) Color.rgb(93, 220, 145) else Color.rgb(20, 125, 68)
            HaChargingMode.CURRENT -> if (dark) Color.rgb(100, 190, 255) else Color.rgb(0, 103, 170)
            HaChargingMode.VOLTAGE -> if (dark) Color.rgb(255, 176, 90) else Color.rgb(174, 78, 0)
        }
        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (dark) Color.argb(25, 255, 255, 255) else Color.argb(30, 0, 0, 0)
            strokeWidth = 1f
        }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = lineColor; strokeWidth = 2f; style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(if (dark) 38 else 48, Color.red(lineColor), Color.green(lineColor), Color.blue(lineColor))
            style = Paint.Style.FILL
        }
        val labels = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (dark) Color.argb(145, 255, 255, 255) else Color.argb(160, 0, 0, 0)
            textSize = 18f * k
        }
        val values = samples.mapNotNull { sample ->
            val value = when (mode) {
                HaChargingMode.POWER -> sample.power
                HaChargingMode.CURRENT -> sample.current
                HaChargingMode.VOLTAGE -> sample.voltage
            }
            value?.let { sample.elapsed to it }
        }
        val max = when (mode) {
            HaChargingMode.POWER -> ((values.maxOfOrNull { it.second } ?: 5.0).coerceAtLeast(5.0) / 5.0).toInt().plus(1) * 5.0
            HaChargingMode.CURRENT -> ((values.maxOfOrNull { it.second } ?: 1.0).coerceAtLeast(1.0) / 1.0).toInt().plus(1).toDouble()
            HaChargingMode.VOLTAGE -> ((values.maxOfOrNull { it.second } ?: 5.0).coerceAtLeast(5.0) / 5.0).toInt().plus(1) * 5.0
        }
        for (row in 0..2) {
            val y = top + (bottom - top) * row / 2f
            canvas.drawLine(left, y, right, y, grid)
            val suffix = when (mode) { HaChargingMode.POWER -> "W"; HaChargingMode.CURRENT -> "A"; HaChargingMode.VOLTAGE -> "V" }
            canvas.drawText("${(max * (2 - row) / 2.0).toInt()}$suffix", 4f, y + 6f * k, labels)
        }
        canvas.drawText("0", left, height - 4f * k, labels)
        if (values.size >= 2) {
            val first = values.first().first
            val elapsed = (values.last().first - first).coerceAtLeast(1L).toFloat()
            val path = android.graphics.Path()
            var firstX = left
            var lastX = left
            values.forEachIndexed { index, (time, value) ->
                val x = left + (right - left) * (time - first) / elapsed
                val y = bottom - (bottom - top) * (value / max).coerceIn(0.0, 1.0).toFloat()
                if (index == 0) { path.moveTo(x, y); firstX = x } else path.lineTo(x, y)
                lastX = x
            }
            val fillPath = android.graphics.Path(path).apply { lineTo(lastX, bottom); lineTo(firstX, bottom); close() }
            canvas.drawPath(fillPath, fill)
            canvas.drawPath(path, line)
        }
        return bitmap
    }

    /**
     * 设备面板模板：复用 KeepIsland 的设备布局（左侧图标 + 3 行文本 + 2 条百分比进度）。
     *
     * 与 KeepIsland 不同的是，这里所有文本都来自订阅里的自定义模板，进度来自用户选择的百分比实体。
     */
    private fun deviceRemoteViews(
        context: Context,
        subscription: HaSubscription,
    ): Triple<RemoteViews, RemoteViews, RemoteViews> {
        val values = entityCache.toMap()
        val lines = HaTemplate.deviceLines(subscription, values)
        val left = values[subscription.deviceLeftEntityId]?.let(HaTemplate::percentOf)
        val right = values[subscription.deviceRightEntityId]?.let(HaTemplate::percentOf)
        val logo = loadCustomIcon(subscription.deviceIconPath)
        fun build(): RemoteViews = RemoteViews(
            context.moduleContext().packageName,
            io.github.hyperisland.R.layout.focus_notification_device,
        ).apply {
            setTextViewText(io.github.hyperisland.R.id.device_name, lines[0])
            setTextViewText(io.github.hyperisland.R.id.device_chipset, lines[1])
            setTextViewText(io.github.hyperisland.R.id.device_uptime, lines[2])
            setTextViewText(io.github.hyperisland.R.id.device_cpu_label, entityTitleOf(subscription.deviceLeftEntityId, values))
            setTextViewText(io.github.hyperisland.R.id.device_memory_label, entityTitleOf(subscription.deviceRightEntityId, values))
            setTextViewText(io.github.hyperisland.R.id.device_cpu_value, percentText(left))
            setTextViewText(io.github.hyperisland.R.id.device_memory_value, percentText(right))
            setProgressBar(io.github.hyperisland.R.id.device_cpu_progress, 100, (left ?: 0f).toInt(), false)
            setProgressBar(io.github.hyperisland.R.id.device_memory_progress, 100, (right ?: 0f).toInt(), false)
            if (logo != null) {
                setImageViewIcon(io.github.hyperisland.R.id.device_logo, logo)
            } else {
                setViewVisibility(io.github.hyperisland.R.id.device_logo, View.INVISIBLE)
            }
        }
        // 面板本身不含位图，日间 / 夜间 / 展开共用同一份 RemoteViews（Binder 只序列化一次）。
        val views = build()
        return Triple(views, views, views)
    }

    private fun entityTitleOf(entityId: String, values: Map<String, HaEntity>): String =
        values[entityId]?.title.orEmpty()

    /** 设备面板的内容签名：3 行文本 + 两条进度（用于「内容没变不更新」的比较）。 */
    private fun deviceSignature(s: HaSubscription, values: Map<String, HaEntity>): String =
        HaTemplate.deviceLines(s, values).joinToString("|") + "|" +
            percentText(values[s.deviceLeftEntityId]?.let(HaTemplate::percentOf)) + "|" +
            percentText(values[s.deviceRightEntityId]?.let(HaTemplate::percentOf))

    private fun percentText(percent: Float?): String = percent?.let { "${it.toInt()}%" } ?: "--%"

    /** 超级岛图标：默认跟随实体（读 `mdi:*`），自定义模式用用户选的图片；图片读不到时回退实体图标。 */
    private fun islandIcon(subscription: HaSubscription, values: Map<String, HaEntity>): Icon? =
        if (subscription.islandIconFollow || subscription.islandIconPath.isBlank()) {
            resolveEntityIcon(subscription, values, islandIconSources(subscription))
        } else {
            loadCustomIcon(subscription.islandIconPath)
                ?: resolveEntityIcon(subscription, values, islandIconSources(subscription))
        }

    /**
     * 读取用户选择的图标图片。
     *
     * 图片最终进通知 extras 走 Binder，所以强采样到 [ICON_MAX_PX] 以内；文件缺失或无权限返回 null。
     */
    private fun loadCustomIcon(path: String): Icon? {
        if (path.isBlank()) return null
        return runCatching {
            val file = java.io.File(path)
            if (!file.isFile || !file.canRead()) return null
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sampleSize = 1
            while (bounds.outWidth / sampleSize > ICON_MAX_PX || bounds.outHeight / sampleSize > ICON_MAX_PX) {
                sampleSize *= 2
            }
            val bitmap = android.graphics.BitmapFactory.decodeFile(
                path,
                android.graphics.BitmapFactory.Options().apply { inSampleSize = sampleSize },
            ) ?: return null
            Icon.createWithBitmap(bitmap)
        }.getOrNull()
    }

    private fun parseMeasurement(value: String): Double? =
        Regex("[-+]?\\d+(?:[.,]\\d+)?").find(value)?.value?.replace(',', '.')?.toDoubleOrNull()

    private data class HaChargingSample(val elapsed: Long, val power: Double?, val current: Double?, val voltage: Double?)
    private enum class HaChargingMode { POWER, CURRENT, VOLTAGE }

    private fun cancelIsland(context: Context, subscription: HaSubscription) {
        val notifId = activeNotifIds.remove(subscription.id) ?: return
        lastSignature.remove(subscription.id)
        IslandDispatcher.cancel(context, notifId)
        carouselRunnables.remove(subscription.id)?.let(carouselHandler::removeCallbacks)
        carouselIndices.remove(subscription.id)
        moduleRef?.let { log(it) { "cancel island id=${subscription.triggerEntityId}" } }
    }

    private fun cancelAllIslands() {
        val context = appContext ?: return
        activeNotifIds.forEach { (_, notifId) -> IslandDispatcher.cancel(context, notifId) }
        activeNotifIds.clear()
        lastSignature.clear()
        matchedSubscriptions.clear()
        entityCache.clear()
        carouselRunnables.values.forEach(carouselHandler::removeCallbacks)
        carouselRunnables.clear()
        carouselIndices.clear()
    }

    private fun scheduleCarousel(context: Context, subscription: HaSubscription) {
        if (carouselRunnables.containsKey(subscription.id)) return
        val runnable = object : Runnable {
            override fun run() {
                val current = runtimeSubscriptions.firstOrNull { it.id == subscription.id } ?: return
                if (!current.enabled || !current.islandEnabled || !matchedSubscriptions.containsKey(current.id)) {
                    carouselRunnables.remove(current.id)
                    return
                }
                carouselIndices[current.id] = (carouselIndices[current.id] ?: 0) + 1
                val values = entityCache.toMap()
                val island = HaTemplate.islandTexts(current, values, carouselIndices[current.id] ?: 0)
                val focus = HaTemplate.focusTexts(current, values)
                val icon = islandIcon(current, values)
                val focusIcon = resolveEntityIcon(current, values, focusIconSources(current))
                val panelViews = panelRemoteViews(context, current)
                IslandDispatcher.post(context, IslandRequest(title = island.first, content = island.second, focusTitle = focus.first, focusContent = focus.second, icon = icon, focusIcon = focusIcon, notifId = current.notifId, timeoutSecs = Int.MAX_VALUE, firstFloat = false, enableFloat = false, showNotification = current.focusEnabled, islandOnly = !current.focusEnabled, islandEnabled = current.islandEnabled, showIslandIcon = current.showIslandIcon, updatable = true, isOngoing = true, focusRemoteViews = panelViews?.first, focusNightRemoteViews = panelViews?.second, focusIslandExpandRemoteViews = panelViews?.third, sourcePackage = "io.github.hyperisland", sourceChannelId = HomeAssistantContract.SOURCE_CHANNEL_ID))
                carouselHandler.postDelayed(this, current.islandCarouselInterval.coerceAtLeast(1) * 1000L)
            }
        }
        carouselRunnables[subscription.id] = runnable
        carouselHandler.postDelayed(runnable, subscription.islandCarouselInterval.coerceAtLeast(1) * 1000L)
    }

    /**
     * 按数据源顺序解析实体自带的 MDI 图标（`attributes.icon`，形如 `mdi:flash`）。
     *
     * 按 [targets] 顺序取第一个带 `mdi:*` 图标的实体并渲染该字形；找不到时返回 null，
     * 交给下发方回退到默认图标。具体顺序见 [islandIconSources] / [focusIconSources]。
     */
    private fun resolveEntityIcon(
        subscription: HaSubscription,
        values: Map<String, HaEntity>,
        targets: List<String>,
    ): Icon? {
        val context = appContext ?: return null
        val candidates = targets.distinct().filter(String::isNotBlank).mapNotNull { values[it] }
        for (entity in candidates) {
            if (entity.icon.isBlank()) continue
            val result = HomeAssistantIconResolver.resolveWithDiagnostics(entity.icon, context)
            moduleRef?.let { module ->
                log(module) {
                    "ha-icon resolve subscription=${subscription.id} entity=${entity.entityId} " +
                        "input=${result.input} normalized=${result.normalized} enum=${result.matchedEnum} " +
                        "pixels=${result.renderedPixels} reason=${result.reason}"
                }
            }
            if (result.icon != null) return result.icon
        }
        moduleRef?.let { module -> log(module) { "ha-icon resolve subscription=${subscription.id} result=no-icon-candidate" } }
        return null
    }

    /** 超级岛图标的数据源顺序：触发实体 → 岛左/岛右模板里引用的实体（`{实体.属性}`）。 */
    private fun islandIconSources(s: HaSubscription): List<String> =
        listOf(s.triggerEntityId) + HaTemplate.referencedEntityIds(s.islandLeftTemplate, s.islandRightTemplate)

    /** 焦点通知图标的数据源顺序：标题/内容模板引用的实体 → 回退触发实体（充电模板为功率 → 电流 → 电压 → 触发实体）。 */
    private fun focusIconSources(s: HaSubscription): List<String> =
        if (s.templateType == io.github.hyperisland.homeassistant.HaTemplateType.CHARGING) {
            listOf(
                s.chargingPowerEntityId,
                s.chargingCurrentEntityId,
                s.chargingVoltageEntityId,
                s.triggerEntityId,
            )
        } else {
            HaTemplate.referencedEntityIds(s.focusTitleTemplate, s.focusContentTemplate) + s.triggerEntityId
        }

    /** 焦点通知数据源实体自带的 `mdi:*` 图标名；都没有图标时返回 null（面板隐藏图标位）。 */
    private fun focusIconName(s: HaSubscription, values: Map<String, HaEntity>): String? =
        focusIconSources(s).distinct().mapNotNull { values[it] }.firstOrNull { it.icon.isNotBlank() }?.icon

}
