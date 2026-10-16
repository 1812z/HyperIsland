package io.github.hyperisland.xposed.hook.SystemUI.extensions

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.drawable.Icon
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.ConnectivityManager
import android.net.Network
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

    /**
     * 息屏省电开关（默认关闭）。
     *
     * 开启后：息屏即丢弃 WS 事件，并在息屏满 [SCREEN_OFF_DISCONNECT_MS] 后断开常驻连接；
     * 亮屏立即恢复。关闭时一切照旧，连接生命周期只跟随 SystemUI 进程与网络状态。
     */
    @Volatile private var runtimeScreenOffPowerSave = false

    /** 当前屏幕是否亮着。初始化时用 PowerManager 取初值，之后由 [screenStateReceiver] 驱动。 */
    @Volatile private var screenInteractive = true

    @Volatile private var session: HaSession? = null

    /** 是否有可用网络。由 [registerNetworkCallback] 的 Android 网络回调驱动，不做任何轮询。 */
    @Volatile private var networkAvailable = false

    /** 当前默认网络，用于分辨「先 onAvailable(新) 后 onLost(旧)」的网络切换。 */
    @Volatile private var activeNetwork: Network? = null

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

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

    /**
     * 设备面板左侧图标用的实体图片位图缓存（LRU）。
     *
     * key 是 [entityImageKey]（绝对地址 + 图片版本号）：HA 的 image 实体换图时版本号会变，
     * 换 key 即失效，**图片没换就不会重新下载**，比定时轮询省电得多。
     * 面板图标只有 58dp，采样上限 [ENTITY_IMAGE_MAX_PX] 足够，把每个位图控制在 ~80KB。
     * 所有访问都必须拿 [entityImageCache] 的锁（主线程与下载线程都会碰）。
     */
    private val entityImageCache = object : LinkedHashMap<String, CachedEntityImage>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedEntityImage>): Boolean =
            size > ENTITY_IMAGE_CACHE_MAX
    }

    /** 在途下载的 key 集合：同一个 key 只允许一次请求。 */
    private val entityImagePending = java.util.Collections.synchronizedSet(HashSet<String>())

    /** key → 上次发起下载的时间；失败时限流重试，成功且未过期时不会再发。 */
    private val entityImageAttempts = ConcurrentHashMap<String, Long>()

    /** 实体图片下载线程（单线程串行，避免同时在拉多张图）。 */
    private val entityImageExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "HyperIsland-HA-image").apply { isDaemon = true }
    }

    private data class CachedEntityImage(val bitmap: Bitmap, val at: Long, val url: String)

    private const val ENTITY_IMAGE_CACHE_MAX = 6
    private const val ENTITY_IMAGE_MAX_PX = 144
    private const val ENTITY_IMAGE_TIMEOUT_MS = 4000

    /**
     * 兜底刷新间隔，**只对拿不到版本号的实体生效**（camera 等）。
     *
     * HA 的 image 实体有 `image_last_updated`，换图会随 state_changed 推过来，走的是版本驱动、
     * 零轮询；这类实体完全不吃这个 TTL。只有不会随图片变化而改状态的实体（camera 快照）
     * 才按这个间隔、在面板因其它数据重投时顺带刷新一次。
     */
    private const val ENTITY_IMAGE_TTL_MS = 5 * 60 * 1000L

    /** 同一 key 的最短重试间隔，避免下载失败后每次状态更新都打一次 HA。 */
    private const val ENTITY_IMAGE_RETRY_MS = 60 * 1000L

    /** 失败限流记录的条数上限，超过就顺手清掉已过期的。 */
    private const val ENTITY_IMAGE_ATTEMPTS_MAX = 32

    private const val EXTRA_SUBSCRIPTION_ID = "ha_subscription_id"
    private const val ACTION_SWITCH_CHARGING_CHART = "io.github.hyperisland.action.HA_SWITCH_CHARGING_CHART"

    /**
     * 断网后多久撤岛（上限）。
     *
     * 实际用时取它与订阅自己的显示时长的**较小值**：用户设置成 10 秒时岛早就自己消失了，
     * 这里跟随它即可；常驻（0）或大于 1 分钟的，统一 1 分钟收尾。
     */
    private const val OFFLINE_CLEANUP_MS = 60_000L

    /**
     * 息屏省电：息屏多久后断开常驻连接。
     *
     * 息屏瞬间只是停收事件（连接还在），留 [SCREEN_OFF_DISCONNECT_MS] 的宽限期，
     * 避免「看一眼锁屏时间又点亮」这种短促息屏把连接来回拆建。
     */
    private const val SCREEN_OFF_DISCONNECT_MS = 60_000L

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

        // 息屏省电依赖屏幕状态：先取一次初值（SystemUI 可能在息屏后才被拉起），再挂广播。
        screenInteractive = context.getSystemService(android.os.PowerManager::class.java)
            ?.isInteractive ?: true
        registerScreenStateReceiver(context)

        readConfigFromPrefs()
        // 先挂上网络回调再起会话：回调里也会尝试建会话，谁先到都不会漏。
        registerNetworkCallback(context)
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
            if (intent.hasExtra(HomeAssistantContract.EXTRA_SCREEN_OFF_POWER_SAVE)) {
                runtimeScreenOffPowerSave = intent.getBooleanExtra(
                    HomeAssistantContract.EXTRA_SCREEN_OFF_POWER_SAVE,
                    runtimeScreenOffPowerSave,
                )
            }
            applyConfig(reason = "broadcast")
        }
    }

    /**
     * 息屏 / 亮屏广播。
     *
     * 只在息屏省电开关打开时才做任何事，关闭时完全等价于不存在这个接收器。
     * 广播本身没有额外开销（系统屏幕事件本来就会发），且不注册前台服务、不轮询。
     */
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> onScreenOff()
                Intent.ACTION_SCREEN_ON -> onScreenOn()
            }
        }
    }

    private fun registerScreenStateReceiver(context: Context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(screenStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(screenStateReceiver, filter)
        }
    }

    /**
     * 息屏：立即停收 WS 事件（[sessionListener.onStateChanged] 里丢弃），并安排到点断开连接。
     *
     * 不在这里直接断连是刻意的：短促息屏（看一眼锁屏又点亮）不该把连接来回拆建。
     */
    private fun onScreenOff() {
        screenInteractive = false
        if (!runtimeScreenOffPowerSave) return
        moduleRef?.let {
            log(it) { "screen off → 暂停 HA 事件，${SCREEN_OFF_DISCONNECT_MS / 1000}s 后断开连接" }
        }
        scheduleScreenOffDisconnect()
    }

    /** 亮屏：撤销待执行的断连并立即恢复连接。 */
    private fun onScreenOn() {
        screenInteractive = true
        cancelScreenOffDisconnect()
        if (!runtimeScreenOffPowerSave) return
        moduleRef?.let { log(it) { "screen on → 恢复 HA 连接" } }
        startSessionIfNeeded(reason = "screen_on")
    }

    /** 息屏省电的断连倒计时任务（至多一个）。 */
    private var screenOffDisconnectTask: Runnable? = null

    private fun scheduleScreenOffDisconnect() {
        cancelScreenOffDisconnect()
        val task = Runnable {
            screenOffDisconnectTask = null
            // 倒计时期间亮屏或用户关掉开关，都不该再断连。
            if (screenInteractive || !runtimeScreenOffPowerSave) return@Runnable
            moduleRef?.let { log(it) { "screen off too long → 断开 HA 常驻连接" } }
            stopSession()
        }
        screenOffDisconnectTask = task
        mainHandler.postDelayed(task, SCREEN_OFF_DISCONNECT_MS)
    }

    private fun cancelScreenOffDisconnect() {
        screenOffDisconnectTask?.let(mainHandler::removeCallbacks)
        screenOffDisconnectTask = null
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
        runtimeScreenOffPowerSave = ConfigManager.getBoolean(
            HomeAssistantContract.PREF_SCREEN_OFF_POWER_SAVE,
            false,
        )
    }

    /** 配置变更：先拆掉旧会话与岛，再按新配置重建。 */
    private fun applyConfig(reason: String) {
        stopSession()
        cancelAllIslands()
        // 配置变化时息屏倒计时一并作废：下面会按最新开关重新决定是否连接。
        cancelScreenOffDisconnect()
        startSessionIfNeeded(reason)
    }

    /**
     * 按当前配置建会话；已经有会话、配置不完整、或**当前没网**时什么都不做。
     *
     * 没网时直接返回是有意的：此时不该有任何连接尝试，等 Android 的网络回调通知我们恢复。
     */
    private fun startSessionIfNeeded(reason: String) {
        if (session != null) return

        // 息屏省电：息屏期间不建立任何连接，等亮屏广播再恢复。
        if (runtimeScreenOffPowerSave && !screenInteractive) {
            moduleRef?.let { log(it) { "screen off & power save on, skip session ($reason)" } }
            return
        }

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
        if (!networkAvailable) {
            // 没网时不该有任何连接尝试：等 Android 的网络回调通知恢复。
            moduleRef?.let { log(it) { "offline, skip session ($reason)" } }
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

    /**
     * 把「联网 / 断网」完全交给 Android 判定，而不是自己定时重试。
     *
     * 用 [ConnectivityManager.registerDefaultNetworkCallback] 订阅默认网络：
     * - **有网** → 立刻建会话（回调是事件驱动的，不在任何轮询里）；
     * - **断网 / 飞行模式 / 切换网络** → 直接把会话拆掉，[HaSession] 的退避重连随之停止，
     *   不会出现「关着网还在每 20 秒重试一次」。
     *
     * [activeNetwork] 用来在「WiFi → 蜂窝」切换时分辨先后：新网络先 [onAvailable]、旧网络后
     * [onLost] 时不能把新连接一起拆掉。
     *
     * 注册失败（权限/框架异常）时退化为「假设在线」，交由 [HaSession] 自己的退避重连接管。
     */
    private fun registerNetworkCallback(context: Context) {
        if (networkCallback != null) return
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: run {
            networkAvailable = true
            return
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val switched = activeNetwork != null && activeNetwork != network
                activeNetwork = network
                networkAvailable = true
                // 网络回来了就别再撤岛（可能在断网清理的倒计时里）。
                cancelAllOfflineCleanups()
                if (switched) {
                    // WiFi ↔ 蜂窝 这类换网：旧 TCP 必然已残废，直接重建，不必等心跳超时。
                    moduleRef?.let { log(it) { "network switched → restart session" } }
                    stopSession()
                } else {
                    moduleRef?.let { log(it) { "network available → start session" } }
                }
                // 只保证「会话存在」，不撤岛：网络抖动不该把岛上已有的内容清掉。
                startSessionIfNeeded(reason = "network")
            }

            override fun onLost(network: Network) {
                if (activeNetwork != network) return
                activeNetwork = null
                networkAvailable = false
                moduleRef?.let { log(it) { "network lost → 暂停连接（停止重试，等系统回调）" } }
                stopSession()
                // 断网不当场撤岛（可能只是切了个 WiFi），但也不能一直挂着过期内容。
                scheduleOfflineCleanup()
            }
        }
        networkCallback = callback
        runCatching { manager.registerDefaultNetworkCallback(callback, mainHandler) }
            .onFailure {
                networkCallback = null
                networkAvailable = true
                moduleRef?.let { module -> logError(module, "network callback unavailable: ${it.message}") }
            }
    }

    /** 网络回调、轮播与各种重建都往主线程投，避免在这些路径上再开线程。 */
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 断网后待执行的撤岛任务：订阅 id → Runnable。 */
    private val offlineCleanupTasks = ConcurrentHashMap<String, Runnable>()

    /**
     * 断网倒计时撤岛。
     *
     * 断网瞬间不撤（可能只是切个 WiFi / 路由重启，岛上的内容还是有效的），
     * 但太久没网就没有意义了：**到点按「订阅自己的显示时长」与 [OFFLINE_CLEANUP_MS] 取小者**撤岛。
     * 用户设置的自动消失更短时跟随它 —— 那种情况下岛本来就已经自己消失了，这里只是把账清干净。
     *
     * 网络恢复（[ConnectivityManager.NetworkCallback.onAvailable]）或配置变更时任务会被取消。
     */
    private fun scheduleOfflineCleanup() {
        val context = appContext ?: return
        activeNotifIds.keys.toList().forEach { id ->
            val subscription = runtimeSubscriptions.firstOrNull { it.id == id } ?: return@forEach
            val delay = minOf(OFFLINE_CLEANUP_MS, islandTimeoutSecs(subscription) * 1_000L)
            cancelOfflineCleanup(id)
            val task = Runnable {
                offlineCleanupTasks.remove(id)
                // 倒计时期间网络回来了就不撤，交给正常链路。
                if (networkAvailable) return@Runnable
                val current = runtimeSubscriptions.firstOrNull { it.id == id } ?: return@Runnable
                moduleRef?.let { log(it) { "offline too long → cancel island id=${current.triggerEntityId}" } }
                cancelIsland(context, current)
            }
            offlineCleanupTasks[id] = task
            mainHandler.postDelayed(task, delay)
        }
    }

    private fun cancelOfflineCleanup(id: String) {
        offlineCleanupTasks.remove(id)?.let(mainHandler::removeCallbacks)
    }

    private fun cancelAllOfflineCleanups() {
        offlineCleanupTasks.values.forEach(mainHandler::removeCallbacks)
        offlineCleanupTasks.clear()
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
            // get_states 只是基线、不走 onStateChanged；断线期间错过的变化（image 换图、令牌轮换）
            // 只能靠这次全量态补上 —— 对仍在匹配中的订阅重跑评估，签名不同就会补投一次。
            val context = appContext
            if (context != null && matchedSubscriptions.isNotEmpty()) {
                matchedSubscriptions.keys.toList().forEach { id ->
                    runtimeSubscriptions.firstOrNull { it.id == id }?.let { subscription ->
                        evaluateSubscription(context, subscription)
                    }
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
            // 息屏省电：息屏期间丢弃所有 WS 事件（连接可能还活着，但不做任何渲染与投递）。
            if (runtimeScreenOffPowerSave && !screenInteractive) return
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
            evaluateSubscription(context, subscription)
        }
    }

    /**
     * 按**当前缓存态**评估一条订阅：条件不成立就撤岛，成立则按内容签名决定要不要重投。
     *
     * 签名由当前值算出（只和上一次投递比较），所以除了状态事件，重连后拿全量态重跑一遍也能
     * 精准补投 —— 断线期间错过的变化（image 换图、令牌轮换）不会一直停在旧内容上。
     */
    private fun evaluateSubscription(context: Context, subscription: HaSubscription) {
        val triggerEntity = entityCache[subscription.triggerEntityId] ?: return
        val current = triggerEntity.valueOf(subscription.triggerAttribute)
        if (!subscription.conditionType.matches(current, subscription.value)) {
            matchedSubscriptions.remove(subscription.id)
            cancelIsland(context, subscription)
            return
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
        if (lastSignature[subscription.id] == signature) return
        lastSignature[subscription.id] = signature
        if (subscription.islandEnabled || subscription.focusEnabled) {
            postIsland(context, subscription, islandTexts, title, content)
        }
        if (subscription.islandEnabled && subscription.islandCarouselInterval > 0) scheduleCarousel(context, subscription)
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
            timeoutSecs = islandTimeoutSecs(subscription),
            firstFloat = subscription.islandFirstFloat && !activeNotifIds.containsKey(subscription.id),
            enableFloat = false,
            showNotification = subscription.focusEnabled,
            preserveStatusBarSmallIcon = false,
            isOngoing = true,
            islandOnly = !subscription.focusEnabled,
            islandEnabled = subscription.islandEnabled,
            showIslandIcon = subscription.showIslandIcon,
            updatable = true,
            focusRemoteViews = panelViews?.light,
            focusNightRemoteViews = panelViews?.night,
            focusIslandExpandRemoteViews = panelViews?.expand,
            focusAodRemoteViews = panelViews?.aod,
            focusFullAodRemoteViews = panelViews?.aod,
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
     * 充电模板与设备面板模板各自返回 日间 / 夜间 / 展开 / 息屏 四份 RemoteViews。
     */
    private fun panelRemoteViews(
        context: Context,
        subscription: HaSubscription,
    ): HaPanelViews? = when {
        !subscription.focusEnabled -> null
        subscription.templateType == HaTemplateType.CHARGING -> chargingRemoteViews(context, subscription)
        subscription.templateType == HaTemplateType.DEVICE -> deviceRemoteViews(context, subscription)
        else -> null
    }

    /**
     * 一套焦点面板需要的四份 RemoteViews。
     *
     * 息屏那一份必须单独给：`miui.focus.rvAod` 缺省时系统会回退用日间那份渲染 AOD，
     * 而 AOD 是黑底 —— 日间那份是给亮屏用的黑字，落到息屏就是一片黑（表现：亮屏正常、息屏变黑）。
     *
     * 各槽位会各序列化一次，所以能复用的（展开、息屏）就复用同一个实例。
     */
    private data class HaPanelViews(
        /** 亮屏日间 → `miui.focus.rv`。 */
        val light: RemoteViews,
        /** 亮屏夜间 → `miui.focus.rvNight`。 */
        val night: RemoteViews,
        /** 展开态 → `miui.focus.rv.island.expand`。 */
        val expand: RemoteViews,
        /** 息屏 → `miui.focus.rvAod` / `miui.focus.rv.fullAod`。 */
        val aod: RemoteViews,
    )

    /** Home Assistant 充电模板使用与 KeepIsland 相同的 RemoteViews 布局。 */
    private fun chargingRemoteViews(
        context: Context,
        subscription: HaSubscription,
    ): HaPanelViews {
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
        // 曲线位图只画两张（日/夜），夜间那张给夜间/展开/息屏三份复用 —— 同一个 Bitmap 实例
        // 在同一 Binder 事务里只会序列化一次，能省掉三分之二体积。
        val samples = history.toList()
        val lightChart = chargingChart(samples, dark = false, mode = mode)
        val darkChart = chargingChart(samples, dark = true, mode = mode)
        fun build(primaryColor: Int, secondaryColor: Int, chart: Bitmap, dark: Boolean, interactive: Boolean = true): RemoteViews = RemoteViews(context.moduleContext().packageName, io.github.hyperisland.R.layout.focus_notification_charging).apply {
            setTextViewText(io.github.hyperisland.R.id.charging_primary, primary)
            setTextViewText(io.github.hyperisland.R.id.charging_details, "$current · $voltage")
            setTextViewText(io.github.hyperisland.R.id.charging_mode, modeLabel)
            setTextColor(io.github.hyperisland.R.id.charging_primary, primaryColor)
            setTextColor(io.github.hyperisland.R.id.charging_details, secondaryColor)
            setTextColor(io.github.hyperisland.R.id.charging_mode, secondaryColor)
            setImageViewBitmap(io.github.hyperisland.R.id.charging_chart, chart)
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
            if (interactive) {
                setOnClickPendingIntent(io.github.hyperisland.R.id.charging_mode, switchIntent)
                setOnClickPendingIntent(io.github.hyperisland.R.id.charging_chart_container, switchIntent)
            } else {
                // 息屏（AOD）没有触摸，按 KeepIsland 同口径把模式按钮收起来。
                setViewVisibility(io.github.hyperisland.R.id.charging_mode, View.GONE)
            }
        }
        // 日间 / 夜间两份 + 息屏一份（展开复用夜间）：系统按 rv / rvNight / rv.island.expand / rvAod 取。
        val light = build(Color.BLACK, Color.DKGRAY, lightChart, dark = false)
        val dark = build(Color.WHITE, Color.LTGRAY, darkChart, dark = true)
        // 息屏单独一份：AOD 不接受触摸，模式按钮收起（与 KeepIsland 充电面板同口径）。
        val aod = build(Color.WHITE, Color.LTGRAY, darkChart, dark = true, interactive = false)
        return HaPanelViews(light = light, night = dark, expand = dark, aod = aod)
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
     * 设备面板模板：复用 KeepIsland 的设备布局（左侧图标 + 3 行文本 + 1~2 条百分比进度）。
     *
     * 与 KeepIsland 不同的是，这里所有文本都来自订阅里的自定义模板，进度来自用户选择的百分比实体，
     * 左侧图标可以是本地图片，也可以是某个 image / camera 实体的图片（异步下载，见 [panelLogo]）。
     */
    private fun deviceRemoteViews(
        context: Context,
        subscription: HaSubscription,
    ): HaPanelViews {
        val values = entityCache.toMap()
        val lines = HaTemplate.deviceLines(subscription, values)
        val left = values[subscription.deviceLeftEntityId]?.let(HaTemplate::percentOf)
        val metricCount = subscription.deviceMetricCount.coerceIn(1, 2)
        val right = if (metricCount >= 2) {
            values[subscription.deviceRightEntityId]?.let(HaTemplate::percentOf)
        } else {
            null
        }
        val logo = panelLogo(subscription, values)
        // 颜色必须显式下发：RemoteViews 是在 SystemUI/超级岛插件进程里膨胀的，布局里的
        // `@color/focus_panel_text_*` 取不到本应用的夜间限定符，只引用资源的话恒为日间黑字。
        // 口径与 KeepIsland 的 PanelPalette、以及本文件的充电面板完全一致。
        fun build(palette: HaPanelPalette): RemoteViews = RemoteViews(
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
            setTextColor(io.github.hyperisland.R.id.device_name, palette.primary)
            setTextColor(io.github.hyperisland.R.id.device_chipset, palette.secondary)
            setTextColor(io.github.hyperisland.R.id.device_uptime, palette.tertiary)
            setTextColor(io.github.hyperisland.R.id.device_cpu_label, palette.secondary)
            setTextColor(io.github.hyperisland.R.id.device_memory_label, palette.secondary)
            setTextColor(io.github.hyperisland.R.id.device_cpu_value, palette.primary)
            setTextColor(io.github.hyperisland.R.id.device_memory_value, palette.primary)
            setColorStateList(
                io.github.hyperisland.R.id.device_cpu_progress,
                "setProgressTintList",
                ColorStateList.valueOf(palette.primary),
            )
            setColorStateList(
                io.github.hyperisland.R.id.device_cpu_progress,
                "setProgressBackgroundTintList",
                ColorStateList.valueOf(palette.track),
            )
            setColorStateList(
                io.github.hyperisland.R.id.device_memory_progress,
                "setProgressTintList",
                ColorStateList.valueOf(palette.primary),
            )
            setColorStateList(
                io.github.hyperisland.R.id.device_memory_progress,
                "setProgressBackgroundTintList",
                ColorStateList.valueOf(palette.track),
            )
            // 每帧都必须显式写可见性：RemoteViews 的更新会落在同一个已膨胀的视图上，
            // 少写一次就会沿用上一帧的状态（数量从 2 改成 1 后右列不会消失）。
            setViewVisibility(
                io.github.hyperisland.R.id.device_metric_right,
                if (metricCount >= 2) View.VISIBLE else View.GONE,
            )
            if (logo != null) {
                setImageViewIcon(io.github.hyperisland.R.id.device_logo, logo)
                setViewVisibility(io.github.hyperisland.R.id.device_logo, View.VISIBLE)
            } else {
                // 实体图片是异步下载的：没就绪时先留空（保留左侧空位，避免文字左右跳动）。
                setViewVisibility(io.github.hyperisland.R.id.device_logo, View.INVISIBLE)
            }
        }
        // 日间 / 夜间两份（展开与息屏复用夜间）：系统按 rv / rvNight / rvAod 选，翻夜与息屏都不用重建。
        val light = build(HaPanelPalette.LIGHT)
        val dark = build(HaPanelPalette.DARK)
        return HaPanelViews(light = light, night = dark, expand = dark, aod = dark)
    }

    /**
     * 设备面板的深浅色字色。
     *
     * 为什么不能只靠 `values-night`：RemoteViews 的布局是在 SystemUI（超级岛插件）进程里膨胀的，
     * 那一侧解析本应用资源时拿不到夜间限定符，`@color/focus_panel_text_*` 会恒定取到日间黑字，
     * 表现就是夜间设备面板一片黑。所以必须按日/夜各构建一份 RemoteViews 并显式设色。
     *
     * 数值与 `values/colors.xml` / `values-night/colors.xml` 及 KeepIsland 的 `PanelPalette` 对齐。
     */
    private data class HaPanelPalette(
        val primary: Int,
        val secondary: Int,
        val tertiary: Int,
        val track: Int,
    ) {
        companion object {
            val LIGHT = HaPanelPalette(
                primary = Color.argb(230, 0, 0, 0),
                secondary = Color.argb(166, 0, 0, 0),
                tertiary = Color.argb(115, 0, 0, 0),
                track = Color.argb(31, 0, 0, 0),
            )
            val DARK = HaPanelPalette(
                primary = Color.WHITE,
                secondary = Color.argb(191, 255, 255, 255),
                tertiary = Color.argb(143, 255, 255, 255),
                track = Color.argb(40, 255, 255, 255),
            )
        }
    }

    private fun entityTitleOf(entityId: String, values: Map<String, HaEntity>): String =
        values[entityId]?.title.orEmpty()

    /**
     * 设备面板左侧图标：实体图片（image / camera 的 `entity_picture`）或本地自定义图片。
     *
     * 实体图片要带 Bearer 令牌拉取，不能在渲染线程同步下载（渲染既可能发生在主线程的轮播里，
     * 也可能发生在 WS 回调线程）。所以这里只读缓存：未命中就丢给后台线程下载并先返回 null，
     * 下载完成后再重投一次（见 [requestEntityImage]）。
     */
    private fun panelLogo(subscription: HaSubscription, values: Map<String, HaEntity>): Icon? {
        val entityId = subscription.deviceImageEntityId
        if (entityId.isBlank()) return loadCustomIcon(subscription.deviceIconPath)
        val entity = values[entityId]
        val path = entity?.entityPictureUrl.orEmpty()
        if (path.isBlank()) return null
        val url = haAbsoluteUrl(path)
        val version = entity?.imageVersion.orEmpty()
        val key = entityImageKey(url, version)
        // 换图时新旧位图是两个 key：新图没下完之前先拿同一地址的旧图顶上，避免图标闪一下空白。
        val cached = cachedEntityImage(key, url, allowStale = true)
        // 有版本号（image 实体）：只有版本变化才取图，图片没换一次都不请求；
        // 没版本号（camera 等）：退回 TTL，过期后跟着面板重投顺带刷新。
        val stale = cached == null ||
            (version.isBlank() && System.currentTimeMillis() - cached.at >= ENTITY_IMAGE_TTL_MS)
        // 只有「这条订阅正在显示」时才允许取图：条件已不满足（岛已撤）、订阅被禁用、
        // 或配置刚重载还没重新评估时，一张都不许拉 —— 不然面板都没了图片还在后台下载，纯白耗电。
        if (stale && isSubscriptionShowing(subscription)) requestEntityImage(key, url, entityId)
        return cached?.let { Icon.createWithBitmap(it.bitmap) }
    }

    /**
     * 这条订阅当前是否处于显示状态。
     *
     * [matchedSubscriptions] 的写入时机很关键：[evaluateSubscription] 是**先**标记匹配、
     * **再**算签名、**最后**才投递，所以投递路径上这个判断永远为真，不会误伤首帧取图。
     * 反过来，条件不再满足（[cancelIsland] 移除标记）或整块配置重载（[cancelAllIslands] 清表）
     * 之后，所有取图请求都会被挡掉。
     */
    private fun isSubscriptionShowing(subscription: HaSubscription): Boolean =
        subscription.enabled && matchedSubscriptions.containsKey(subscription.id)

    /**
     * 缓存键：绝对地址 + 图片版本号。
     *
     * 版本号为空（实体不提供换图信号）时退化成纯地址键，由 TTL 兜底。
     */
    private fun entityImageKey(url: String, version: String): String =
        if (version.isBlank()) url else "$url#$version"

    /**
     * 读缓存。[allowStale] 为 true 时，精确 key 未命中会退回**同一地址**的旧版本位图
     * （面板先显示旧图，新图下完再替换）；为 false 时只认精确 key，用于签名里的就绪判定。
     */
    private fun cachedEntityImage(key: String, url: String, allowStale: Boolean): CachedEntityImage? =
        synchronized(entityImageCache) {
            entityImageCache[key] ?: if (allowStale) entityImageCache.values.lastOrNull { it.url == url } else null
        }

    /**
     * 图片接口的绝对地址。
     *
     * ⚠️ `runtimeUrl` 是用户填的 **WebSocket** 地址（`ws://` / `wss://`，也允许 `http(s)://`），
     * 直接拼出来会是 `wss://host:8124/api/image_proxy/…` —— HttpURLConnection 不认识 wss，
     * 连握手都进不去（日志里只会看到一句 download failed）。所以 scheme 必须映射回 http(s)，
     * 并沿用用户地址里的端口与反向代理路径前缀。
     */
    private fun haAbsoluteUrl(path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val base = haBaseUrl()
        val suffix = if (path.startsWith("/")) path else "/$path"
        val origin = haOrigin(base)
        val prefix = if (origin.isEmpty()) "" else base.removePrefix(origin)
        // 服务端给的路径本身已带代理前缀时不要再叠加一次。
        return if (prefix.isNotEmpty() && suffix.startsWith("$prefix/")) origin + suffix else base + suffix
    }

    /** HA 的 HTTP(S) 根地址：用户地址换 scheme 并去掉 `/api/websocket` 尾巴。 */
    private fun haBaseUrl(): String {
        val url = runtimeUrl.trim().trimEnd('/')
        val schemeEnd = url.indexOf("://")
        val scheme = when {
            schemeEnd <= 0 -> "http://"
            url.startsWith("wss://", true) || url.startsWith("https://", true) -> "https://"
            else -> "http://"
        }
        val body = if (schemeEnd > 0) url.substring(schemeEnd + 3) else url
        return scheme + body.removeSuffix("/api/websocket")
    }

    /** 取 `scheme://host:port`，不含路径。 */
    private fun haOrigin(url: String): String {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd <= 0) return ""
        val body = url.substring(schemeEnd + 3)
        val slash = body.indexOf('/')
        return url.substring(0, schemeEnd + 3) + if (slash < 0) body else body.substring(0, slash)
    }

    /** http ↔ https 互换，用于主地址连接层面失败时的兜底重试。 */
    private fun alternateSchemeUrl(url: String): String? = when {
        url.startsWith("https://") -> "http://" + url.removePrefix("https://")
        url.startsWith("http://") -> "https://" + url.removePrefix("http://")
        else -> null
    }

    /**
     * 后台下载实体图片；成功后清掉相关订阅的内容签名，让下一次评估把图片补上。
     *
     * [key] 是带版本号的缓存键（限流、去重、落缓存都用它），[url] 才是真正要请求的地址。
     */
    private fun requestEntityImage(key: String, url: String, entityId: String) {
        val now = System.currentTimeMillis()
        entityImageAttempts[key]?.let { if (now - it < ENTITY_IMAGE_RETRY_MS) return }
        // key 带版本号，会随每次换图新增；失败记录必须限长，否则这个 map 会一直长。
        if (entityImageAttempts.size > ENTITY_IMAGE_ATTEMPTS_MAX) {
            entityImageAttempts.entries.removeAll { now - it.value >= ENTITY_IMAGE_RETRY_MS }
        }
        synchronized(entityImagePending) { if (!entityImagePending.add(key)) return }
        entityImageAttempts[key] = now
        val token = runtimeToken
        runCatching {
            entityImageExecutor.execute {
                val outcome = runCatching { downloadEntityImage(url, token) }
                val bitmap = outcome.getOrNull()
                synchronized(entityImagePending) { entityImagePending.remove(key) }
                if (bitmap == null) {
                    val detail = outcome.exceptionOrNull()
                        ?.let { " (${it.javaClass.simpleName} ${it.message.orEmpty()})" }
                        .orEmpty()
                    moduleRef?.let { logError(it, "entity image download failed:$detail $url") }
                    return@execute
                }
                synchronized(entityImageCache) {
                    // 换图后 key 会变，顺手淘汰同一地址的旧版本，别让旧图把 LRU 占满。
                    val iterator = entityImageCache.entries.iterator()
                    while (iterator.hasNext()) {
                        val existing = iterator.next().key
                        if (existing != key && existing.startsWith("$url#")) iterator.remove()
                    }
                    entityImageCache[key] = CachedEntityImage(bitmap, System.currentTimeMillis(), url)
                }
                // 成功了就不用再限流：后续重复请求会被缓存直接挡掉。
                entityImageAttempts.remove(key)
                moduleRef?.let { log(it) { "entity image ready entity=$entityId ${bitmap.width}x${bitmap.height}" } }
                // 图片是否就绪是设备面板签名的一部分，清掉签名后重新评估就会补投一次。
                runtimeSubscriptions.forEach { subscription ->
                    if (subscription.deviceImageEntityId == entityId) lastSignature.remove(subscription.id)
                }
                val entity = entityCache[entityId]
                if (entity != null) {
                    Handler(Looper.getMainLooper()).post {
                        appContext?.let { context -> handleStateChanged(context, entity) }
                    }
                }
            }
        }.onFailure {
            synchronized(entityImagePending) { entityImagePending.remove(key) }
        }
    }

    /**
     * 拉取实体图片。
     *
     * `wss://` → `https://` 只是我们的推断，所以主地址在**连接层面**失败时再换一种协议试一次；
     * HTTP 错误码（401 / 404）说明地址本身是通的，换协议没有意义，直接放弃。
     */
    private fun downloadEntityImage(url: String, token: String): Bitmap? {
        for ((index, candidate) in listOfNotNull(url, alternateSchemeUrl(url)).withIndex()) {
            var networkError = false
            val bytes = try {
                fetchEntityImageBytes(candidate, token)
            } catch (e: Exception) {
                networkError = true
                moduleRef?.let {
                    logError(it, "entity image request error: ${e.javaClass.simpleName} ${e.message.orEmpty()} $candidate")
                }
                null
            }
            if (bytes != null) return decodeSampledImage(bytes)
            if (!networkError) return null
            if (index == 0) moduleRef?.let { log(it) { "entity image retry with alternate scheme" } }
        }
        return null
    }

    /** 单次请求：HTTP 报错返回 null，网络 / TLS 失败直接抛出交给调用方决定是否换协议。 */
    private fun fetchEntityImageBytes(url: String, token: String): ByteArray? {
        val raw = java.net.URL(url).openConnection()
        // 与 WS 客户端保持一致：WS 走裸 SSLSocket，JSSE 默认不做主机名校验。
        // 这里同样只校验证书链、不校验主机名，否则会出现「订阅连得上、图标却握手失败」的割裂。
        (raw as? javax.net.ssl.HttpsURLConnection)?.let { https ->
            https.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
        }
        val connection = (raw as java.net.HttpURLConnection).apply {
            connectTimeout = ENTITY_IMAGE_TIMEOUT_MS
            readTimeout = ENTITY_IMAGE_TIMEOUT_MS
            setRequestProperty("Accept", "image/*")
            // 官方示例里的 `?time=` 是给浏览器 / 代理用的缓存破除参数。我们故意不写进 URL：
            // URL 同时是本地 LRU 的键和设备面板签名的一部分，带时间戳会让它每帧都变。
            // 改用请求头达到同样效果。
            setRequestProperty("Cache-Control", "no-cache")
            if (token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) {
                moduleRef?.let { logError(it, "entity image http $code: $url") }
                null
            } else {
                connection.inputStream.use { it.readBytes() }.takeIf { it.isNotEmpty() }
            }
        } finally {
            connection.disconnect()
        }
    }

    /** 按 [ENTITY_IMAGE_MAX_PX] 采样解码；面板图标只有 58dp，不需要原图。 */
    private fun decodeSampledImage(bytes: ByteArray): Bitmap? {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > ENTITY_IMAGE_MAX_PX || bounds.outHeight / sampleSize > ENTITY_IMAGE_MAX_PX) {
            sampleSize *= 2
        }
        return android.graphics.BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            android.graphics.BitmapFactory.Options().apply { inSampleSize = sampleSize },
        )
    }

    /** 设备面板的内容签名：3 行文本 + 进度（按组件数量）+ 图标来源状态。 */
    private fun deviceSignature(s: HaSubscription, values: Map<String, HaEntity>): String {
        val metricCount = s.deviceMetricCount.coerceIn(1, 2)
        val left = percentText(values[s.deviceLeftEntityId]?.let(HaTemplate::percentOf))
        val right = if (metricCount >= 2) {
            percentText(values[s.deviceRightEntityId]?.let(HaTemplate::percentOf))
        } else {
            "-"
        }
        val imageEntityId = s.deviceImageEntityId
        val logo = if (imageEntityId.isBlank()) {
            s.deviceIconPath
        } else {
            val entity = values[imageEntityId]
            val path = entity?.entityPictureUrl.orEmpty()
            val version = entity?.imageVersion.orEmpty()
            val url = if (path.isBlank()) "" else haAbsoluteUrl(path)
            // 精确 key（不吃过渡旧图）：换图 → 版本变 → 签名变 → 面板重投并把新图取回来。
            val ready = url.isNotEmpty() &&
                cachedEntityImage(entityImageKey(url, version), url, allowStale = false) != null
            // 版本必须进签名：图片变了但地址没变时，只有它能触发重投。
            path + "|" + version + "|" + (if (ready) "ready" else "pending")
        }
        return HaTemplate.deviceLines(s, values).joinToString("|") +
            "|" + left + "|" + right + "|" + metricCount + "|" + logo
    }

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

    /**
     * 岛 / 通知的显示时长。
     *
     * - 默认「常驻」→ [Int.MAX_VALUE]：条件满足就一直显示，条件不再满足由 [cancelIsland] 撤掉；
     * - 用户选了自定义秒数 → 投递后到点自动消失（计时在系统侧，我们不维护定时器）。
     *
     * ⚠️ 状态轮播会周期性重投，等于不断续期：开了轮播 + 自定义时长，实际表现仍是常驻
     * （轮播本身的语义就是「一直显示」）。
     */
    private fun islandTimeoutSecs(subscription: HaSubscription): Int =
        subscription.displaySeconds.takeIf { it > 0 } ?: Int.MAX_VALUE

    private fun cancelIsland(context: Context, subscription: HaSubscription) {
        cancelOfflineCleanup(subscription.id)
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
        // 岛都撤了，断网倒计时也就没有意义了；不清的话它会在之后把「重建起来的岛」误撤掉。
        cancelAllOfflineCleanups()
        // 清掉匹配标记 = 所有取图请求立刻被 isSubscriptionShowing() 挡掉（见 panelLogo）。
        matchedSubscriptions.clear()
        entityCache.clear()
        // entityImageCache 故意不清：配置每次改动都会走到这里（applyConfig），
        // 清了反而让下一次显示重新下载一遍，更费电；它是 LRU 6 项、每张 ~80KB，留着更划算。
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
                IslandDispatcher.post(context, IslandRequest(title = island.first, content = island.second, focusTitle = focus.first, focusContent = focus.second, icon = icon, focusIcon = focusIcon, notifId = current.notifId, timeoutSecs = islandTimeoutSecs(current), firstFloat = false, enableFloat = false, showNotification = current.focusEnabled, preserveStatusBarSmallIcon = false, islandOnly = !current.focusEnabled, islandEnabled = current.islandEnabled, showIslandIcon = current.showIslandIcon, updatable = true, isOngoing = true, focusRemoteViews = panelViews?.light, focusNightRemoteViews = panelViews?.night, focusIslandExpandRemoteViews = panelViews?.expand, focusAodRemoteViews = panelViews?.aod, focusFullAodRemoteViews = panelViews?.aod, sourcePackage = "io.github.hyperisland", sourceChannelId = HomeAssistantContract.SOURCE_CHANNEL_ID))
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
//            moduleRef?.let { module ->
//                log(module) {
//                    "ha-icon resolve subscription=${subscription.id} entity=${entity.entityId} " +
//                        "input=${result.input} normalized=${result.normalized} enum=${result.matchedEnum} " +
//                        "pixels=${result.renderedPixels} reason=${result.reason}"
//                }
//            }
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
