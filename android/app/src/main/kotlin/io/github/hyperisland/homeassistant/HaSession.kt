package io.github.hyperisland.homeassistant

import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

/** 连接状态，App 与 Hook 共用。 */
enum class HaStatus {
    /** 未启用或未配置。 */
    Idle,
    Connecting,
    Connected,
    /** 令牌被拒，重连无意义。 */
    AuthFailed,
    /** 网络/协议错误，会自动退避重连。 */
    Failed,
}

/**
 * HA 会话：负责「连接 → 认证 → 拉全量状态 → 订阅 state_changed → 断线退避重连」。
 *
 * 一次 [start] 会持续工作到 [stop]；期间所有回调都在内部工作线程上发出，
 * 调用方自行切线程（Hook 侧直接用，App 侧切主线程）。
 */
internal class HaSession(
    private val url: String,
    private val token: String,
    private val listener: Listener,
    private val watchedEntityIds: Set<String> = emptySet(),
) {

    interface Listener {
        /** 已连接并拿到全量实体列表。 */
        fun onEntities(entities: List<HaEntity>)

        /** `state_changed` 事件（已过滤掉 state 与 attributes 都未变化的空事件）。 */
        fun onStateChanged(entity: HaEntity)

        fun onStatus(status: HaStatus, message: String? = null)

        /**
         * 服务端回了一个业务错误（`success=false`）。
         *
         * 必须有这条回调：HA 对格式非法的命令（例如 id 为 0）**不会断开连接**，
         * 只回一条 error result —— 如果静默忽略，表现就是「已连接但实体 0 个」。
         */
        fun onError(message: String)

        /**
         * 诊断用：每条**非事件**服务端消息的摘要（`type` / `id` / `success` / 条数）。
         *
         * 「已连接但没有数据」这类问题只能靠它定位：能看出命令有没有发出去、
         * 服务端回了什么、是不是被 `id` 不匹配挡掉了。
         */
        fun onServerMessage(summary: String) = Unit
    }

    private val stopped = AtomicBoolean(false)
    private var thread: Thread? = null
    private var client: HaWebSocketClient? = null

    @Volatile private var reconnectAttempt = 0

    /**
     * 命令 id 必须从 1 开始。
     *
     * HA 的 `ActiveConnection.async_handle` 会做 `not (cur_id := msg.get("id"))` 判断，
     * id = 0 属于 falsy 直接被判为 `invalid_format` 丢掉（另有 `cur_id <= last_id` 的
     * 递增校验，0 同样过不了）。这里从 1 起步并严格递增。
     */
    private var nextRequestId = 1
    private var statesRequestId = -1
    private var subscribeRequestId = -1

    /** 最近一次全量快照，避免重连后把「进程启动时就有的状态」当成变化。 */
    @Volatile private var lastResolved: Map<String, String> = emptyMap()

    private val lock = Any()

    fun start() {
        if (thread != null) return
        thread = Thread({ runLoop() }, THREAD_NAME).apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        if (!stopped.compareAndSet(false, true)) return
        client?.stop()
        thread?.interrupt()
    }

    /** 从最近一次快照里取实体（App 侧列表预览用）。 */
    fun snapshot(): List<HaEntity> = synchronized(lock) { latestEntities }

    private var latestEntities: List<HaEntity> = emptyList()

    private fun runLoop() {
        while (!stopped.get()) {
            var keepGoing = true
            try {
                keepGoing = runOnce()
            } catch (_: InterruptedException) {
                // stop() 会中断，交给下面的 stopped 判断收尾。
            } catch (t: Throwable) {
                // 兜住一切异常：这个线程一旦意外死掉，就再也不会有任何重连
                // （表现 = 连接断了、界面还显示已连接、怎么等都不恢复）。
                if (!stopped.get()) {
                    runCatching {
                        listener.onStatus(HaStatus.Failed, t.message ?: t.javaClass.simpleName)
                    }
                }
            }
            if (stopped.get() || !keepGoing) break
            reconnectAttempt++
            if (!sleepBackoff(reconnectAttempt)) break
        }
        if (stopped.get()) runCatching { listener.onStatus(HaStatus.Idle) }
    }

    /** 跑一轮「连接 → 断开」；返回 false 表示不再重连（令牌被拒，或已经被 [stop]）。 */
    private fun runOnce(): Boolean {
        val latch = CountDownLatch(1)
        val socket = HaWebSocketClient(url, token, object : HaWebSocketClient.Listener {
            override fun onAuthenticated() {
                reconnectAttempt = 0
                listener.onStatus(HaStatus.Connected)
                requestStatesAndSubscribe()
            }

            override fun onJson(message: JSONObject) {
                handleMessage(message)
            }

            override fun onFailure(message: String, authFailed: Boolean) {
                lastFailureMessage = message
                if (authFailed) {
                    this@HaSession.authFailedMessage = message
                }
                latch.countDown()
            }

            override fun onStopped() {
                latch.countDown()
            }
        })
        try {
            client = socket
            listener.onStatus(HaStatus.Connecting)
            socket.start()
            try {
                latch.await()
            } catch (_: InterruptedException) {
                // stop() 会中断，直接走退出分支。
            }
        } finally {
            // 异常路径也必须收干净：否则旧 socket 会一直挂着，泄漏 fd 与线程。
            runCatching { socket.stop() }
            client = null
        }
        if (stopped.get()) return false

        val failure = authFailedMessage
        if (failure != null) {
            authFailedMessage = null
            listener.onStatus(HaStatus.AuthFailed, failure)
            return false
        }
        // 把具体原因带给界面：否则用户只看到「连接失败」，无从判断是地址、端口还是证书问题。
        listener.onStatus(HaStatus.Failed, lastFailureMessage)
        lastFailureMessage = null
        return true
    }

    @Volatile private var authFailedMessage: String? = null

    /** 最近一次失败原因，随 [HaStatus.Failed] 一起回调给界面。 */
    @Volatile private var lastFailureMessage: String? = null

    private fun requestStatesAndSubscribe() {
        val socket = client ?: run {
            listener.onError("内部错误：连接尚未注册就请求了实体列表")
            return
        }
        val statesId: Int
        val subscribeId: Int
        synchronized(lock) {
            statesId = nextRequestId++
            subscribeId = nextRequestId++
            subscribeRequestId = subscribeId
        }
        statesRequestId = statesId
        socket.send("{\"id\":$statesId,\"type\":\"get_states\"}")
        // HA 的 subscribe_events 只支持按 event_type 订阅，不支持 entity_id 过滤。
        // 因此每个连接只建立一个 state_changed 订阅，再在 handleEvent 中按
        // watchedEntityIds 过滤，避免发送服务端不接受的 entity_id 参数。
        socket.send("{\"id\":$subscribeId,\"type\":\"subscribe_events\",\"event_type\":\"state_changed\"}")
    }

    private fun handleMessage(message: JSONObject) {
        val type = message.optString("type")
        // 事件消息量大，不上报；命令类消息全留痕，排查「连上没数据」用。
        if (type != "event") listener.onServerMessage(describeMessage(message))
        when (type) {
            "result" -> {
                val id = message.optInt("id", -1)
                if (id == subscribeRequestId) {
                    if (!message.optBoolean("success", false)) {
                        listener.onError("subscribe_events 失败：" + describeError(message))
                    }
                    return
                }
                if (id != statesRequestId) return
                if (!message.optBoolean("success", false)) {
                    // 不再静默 return：否则「已连接但实体 0 个」这种问题无从查起。
                    listener.onError("get_states 失败：" + describeError(message))
                    return
                }
                val array = message.opt("result") as? JSONArray
                if (array == null) {
                    listener.onError("get_states 返回格式异常")
                    return
                }
                val entities = buildList {
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        add(HaEntity.fromJson(item))
                    }
                }.sortedBy { it.title.lowercase() }
                synchronized(lock) { latestEntities = entities }
                // 只作为基线：不触发任何通知，避免「连上就弹一堆」。
                lastResolved = entities.associate { it.entityId to signature(it) }
                listener.onEntities(entities)
            }

            "event" -> handleEvent(message.optJSONObject("event") ?: return)
        }
    }

    private fun handleEvent(event: JSONObject) {
        val data = event.optJSONObject("data") ?: return
        val newState = data.optJSONObject("new_state") ?: return
        val entity = HaEntity.fromJson(newState)
        if (entity.entityId.isEmpty()) return
        if (watchedEntityIds.isNotEmpty() && entity.entityId !in watchedEntityIds) return
        val previous = lastResolved[entity.entityId]
        val currentSignature = signature(entity)
        synchronized(lock) {
            latestEntities = latestEntities.map { existing ->
                if (existing.entityId == entity.entityId) entity else existing
            }
        }
        if (previous == currentSignature) return
        lastResolved = lastResolved + (entity.entityId to currentSignature)
        listener.onStateChanged(entity)
    }

    private fun signature(entity: HaEntity): String =
        entity.state + "\u0000" + entity.attributes.entries.joinToString("\u0001") { "${it.key}=${it.value}" }

    /** 服务端消息摘要，只保留排查必需字段，避免把整个实体列表带进 UI。 */
    private fun describeMessage(message: JSONObject): String {
        val type = message.optString("type")
        val id = message.optInt("id", -1)
        if (type != "result") return "$type id=$id"
        return buildString {
            append("result id=").append(id)
            append(" success=").append(message.optBoolean("success", false))
            (message.opt("result") as? JSONArray)?.let { append(" size=").append(it.length()) }
            message.optJSONObject("error")?.optString("code")?.takeIf { it.isNotBlank() }
                ?.let { append(" error=").append(it) }
        }
    }

    /** 从 error result 里提取 `{"code":"...","message":"..."}`。 */
    private fun describeError(message: JSONObject): String {
        val error = message.optJSONObject("error")
        val code = error?.optString("code").orEmpty()
        val text = error?.optString("message").orEmpty()
        return listOf(code, text).filter { it.isNotBlank() }.joinToString(" · ")
            .ifBlank { "未知错误" }
    }

    private fun sleepBackoff(attempt: Int): Boolean {
        // 上限压到 20 秒：局域网内重试的代价只是一次 TCP 连接，
        // 而 45 秒的间隔在用户侧看起来就是「断了不自动连」。
        val seconds = when {
            attempt <= 1 -> 3
            attempt == 2 -> 6
            attempt == 3 -> 12
            else -> 20
        }
        var remaining = seconds * 1_000L
        while (remaining > 0 && !stopped.get()) {
            val slice = minOf(remaining, 500L)
            try {
                Thread.sleep(slice)
            } catch (_: InterruptedException) {
                return !stopped.get()
            }
            remaining -= slice
        }
        return !stopped.get()
    }

    private companion object {
        const val THREAD_NAME = "HyperIsland-HA-Session"
    }
}
