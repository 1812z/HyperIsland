package io.github.hyperisland.compose.page.settings.extensions.homeassistant

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.hyperisland.homeassistant.HaEntity
import io.github.hyperisland.homeassistant.HaSession
import io.github.hyperisland.homeassistant.HaStatus
import java.util.LinkedHashMap

/** 设置页展示用的连接状态。 */
internal enum class HaUiStatus {
    /** 总开关关闭。 */
    Disabled,
    /** 已开启但地址 / 令牌还没填。 */
    Unconfigured,
    Connecting,
    Connected,
    AuthFailed,
    Failed,
}

/**
 * 设置页专用的 HA 连接：只用于「读实体列表 + 实时预览状态」，不参与通知投递。
 *
 * 通知那条链路完全在 SystemUI 进程的 Hook 里跑，这里连一下只是为了让用户在选择实体时
 * 能看到真实的状态与单位，并确认地址 / 令牌是否正确。离开页面即断开。
 */
internal class HomeAssistantConnection {

    var status by mutableStateOf(HaUiStatus.Disabled)
        private set
    var detail by mutableStateOf<String?>(null)
        private set
    var entities by mutableStateOf<List<HaEntity>>(emptyList())
        private set

    /** 服务端最后一条命令类消息的摘要；只用于排查「连上但没数据」。 */
    var diagnostics by mutableStateOf<String?>(null)
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var session: HaSession? = null
    private var enabled = false
    private var url = ""
    private var token = ""
    private var signature = SIGNATURE_NONE
    private var uiUpdatesSuspended = false
    private val pendingStateUpdates = LinkedHashMap<String, HaEntity>()
    private var stateUpdatePosted = false

    /** 幂等：参数没变则什么都不做，避免每次重组都重连。 */
    fun sync(enabled: Boolean, url: String, token: String, force: Boolean = false) {
        val next = if (enabled) "$url|$token" else SIGNATURE_NONE
        if (!force && next == signature) return
        signature = next
        this.enabled = enabled
        this.url = url
        this.token = token
        restart()
    }

    /** 手动重连：带上当前草稿值强制重建，不依赖上游状态是否已经刷新。 */
    fun reconnect(url: String = this.url, token: String = this.token) =
        sync(enabled, url, token, force = true)

    fun shutdown() {
        session?.stop()
        session = null
        signature = SIGNATURE_NONE
        synchronized(pendingStateUpdates) {
            pendingStateUpdates.clear()
            stateUpdatePosted = false
        }
        entities = emptyList()
        diagnostics = null
    }

    /** BottomSheet 编辑期间冻结实体列表，避免后台 HA 事件不断触发整页重组。 */
    fun setUiUpdatesSuspended(suspended: Boolean) {
        uiUpdatesSuspended = suspended
        if (!suspended) flushPendingStateUpdates()
    }

    fun entity(entityId: String): HaEntity? = entities.firstOrNull { it.entityId == entityId }

    private fun restart() {
        session?.stop()
        session = null
        entities = emptyList()
        detail = null
        diagnostics = null
        when {
            !enabled -> status = HaUiStatus.Disabled
            url.isBlank() || token.isBlank() -> status = HaUiStatus.Unconfigured
            else -> {
                status = HaUiStatus.Connecting
                val created = HaSession(url, token, object : HaSession.Listener {
                    override fun onEntities(entities: List<HaEntity>) = post {
                        this@HomeAssistantConnection.entities = entities
                    }

                    override fun onStateChanged(entity: HaEntity) = post {
                        enqueueStateUpdate(entity)
                    }

                    override fun onStatus(status: HaStatus, message: String?) = post {
                        this@HomeAssistantConnection.status = status.toUi()
                        detail = message
                    }

                    override fun onError(message: String) = post {
                        // 连接还在，只是命令被拒（例如 HA 报 invalid_format）。
                        // 显示到 detail 上，避免「已连接但实体 0 个」这种无声失败。
                        detail = message
                        entities = emptyList()
                    }

                    override fun onServerMessage(summary: String) = post {
                        diagnostics = summary
                    }
                })
                session = created
                created.start()
            }
        }
    }

    private inline fun post(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            handler.post { block() }
        }
    }

    /**
     * HA 可能在短时间内推送大量实体事件。设置页只需要最终快照，不能为每个事件都在主线程
     * 重建完整实体列表并触发整页重组，否则输入事件会被连接回调长期挤占。
     */
    private fun enqueueStateUpdate(entity: HaEntity) {
        if (uiUpdatesSuspended) {
            synchronized(pendingStateUpdates) { pendingStateUpdates[entity.entityId] = entity }
            return
        }
        val shouldPost = synchronized(pendingStateUpdates) {
            pendingStateUpdates[entity.entityId] = entity
            if (stateUpdatePosted) false else {
                stateUpdatePosted = true
                true
            }
        }
        if (!shouldPost) return
        handler.postDelayed({
            flushPendingStateUpdates()
        }, STATE_UPDATE_BATCH_DELAY_MS)
    }

    private fun flushPendingStateUpdates() {
        val updates = synchronized(pendingStateUpdates) {
            val copy = pendingStateUpdates.toMap()
            pendingStateUpdates.clear()
            stateUpdatePosted = false
            copy
        }
        if (updates.isEmpty() || uiUpdatesSuspended) return
        entities = entities.map { current -> updates[current.entityId] ?: current }
    }

    private fun HaStatus.toUi(): HaUiStatus = when (this) {
        HaStatus.Idle -> HaUiStatus.Disabled
        HaStatus.Connecting -> HaUiStatus.Connecting
        HaStatus.Connected -> HaUiStatus.Connected
        HaStatus.AuthFailed -> HaUiStatus.AuthFailed
        HaStatus.Failed -> HaUiStatus.Failed
    }

    private companion object {
        const val SIGNATURE_NONE = ""
        const val STATE_UPDATE_BATCH_DELAY_MS = 100L
    }
}

/**
 * 页面级连接持有者：进入页面时按当前配置连接，离开时断开。
 *
 * 返回值直接参与组合（`connection.entities` 等），所以用 `mutableStateOf` 承接后台回调。
 */
@Composable
internal fun rememberHomeAssistantConnection(
    enabled: Boolean,
    url: String,
    token: String,
): HomeAssistantConnection {
    val connection = remember { HomeAssistantConnection() }
    LaunchedEffect(enabled, url, token) { connection.sync(enabled, url, token) }
    DisposableEffect(Unit) {
        onDispose { connection.shutdown() }
    }
    return connection
}
