package io.github.hyperisland.xposed.hook.SystemUI.extensions

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Build
import io.github.hyperisland.homeassistant.HaEntity
import io.github.hyperisland.homeassistant.HomeAssistantIconResolver
import io.github.hyperisland.homeassistant.HaSession
import io.github.hyperisland.homeassistant.HaStatus
import io.github.hyperisland.homeassistant.HaSubscription
import io.github.hyperisland.homeassistant.HaTemplate
import io.github.hyperisland.homeassistant.HomeAssistantContract
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.hyperisland.xposed.islanddispatch.IslandDispatcher
import io.github.hyperisland.xposed.islanddispatch.definition.IslandRequest
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.concurrent.ConcurrentHashMap
import android.os.Handler
import android.os.Looper

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
        val created = HaSession(url, token, sessionListener)
        session = created
        created.start()
    }

    private fun stopSession() {
        session?.stop()
        session = null
    }

    private val sessionListener = object : HaSession.Listener {
        override fun onEntities(entities: List<HaEntity>) {
            entityCache.clear()
            entities.forEach { entityCache[it.entityId] = it }
            moduleRef?.let { log(it) { "connected, ${entities.size} entities" } }
        }

        override fun onStateChanged(entity: HaEntity) {
            entityCache[entity.entityId] = entity
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
            val signature = HaTemplate.signature(title + islandTexts.first, content + islandTexts.second)
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
        val request = IslandRequest(
            title = islandTexts.first,
            content = islandTexts.second,
            focusTitle = focusTitle,
            focusContent = focusContent,
            icon = resolveEntityIcon(subscription, entityCache),
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
                val icon = resolveEntityIcon(current, values)
                IslandDispatcher.post(context, IslandRequest(title = island.first, content = island.second, focusTitle = focus.first, focusContent = focus.second, icon = icon, notifId = current.notifId, timeoutSecs = Int.MAX_VALUE, firstFloat = false, enableFloat = false, showNotification = current.focusEnabled, islandOnly = !current.focusEnabled, islandEnabled = current.islandEnabled, showIslandIcon = current.showIslandIcon, updatable = true, isOngoing = true, sourcePackage = "io.github.hyperisland", sourceChannelId = HomeAssistantContract.SOURCE_CHANNEL_ID))
                carouselHandler.postDelayed(this, current.islandCarouselInterval.coerceAtLeast(1) * 1000L)
            }
        }
        carouselRunnables[subscription.id] = runnable
        carouselHandler.postDelayed(runnable, subscription.islandCarouselInterval.coerceAtLeast(1) * 1000L)
    }

    private fun resolveEntityIcon(subscription: HaSubscription, values: Map<String, HaEntity>): Icon? {
        val entity = values[subscription.triggerEntityId] ?: values.values.firstOrNull() ?: return null
        return HomeAssistantIconResolver.resolve(entity.icon, appContext ?: return null)
    }

}
