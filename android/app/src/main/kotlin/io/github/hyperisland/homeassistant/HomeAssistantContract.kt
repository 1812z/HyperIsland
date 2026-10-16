package io.github.hyperisland.homeassistant

/**
 * App 与 SystemUI Hook 之间的唯一契约。
 *
 * 配置键、广播 action、extra 名都在这一处定义：设置页写入 → 广播通知 Hook 热更新 →
 * Hook 也直接从 RemotePreferences 读同一批键，双方不会因为字符串写错而静默失效。
 */
object HomeAssistantContract {

    /** 设置页改动后通知 Hook 重连（值随 extra 一起带过去，避免与 RemotePreferences 同步竞态）。 */
    const val ACTION_CONFIG_CHANGED = "io.github.hyperisland.ACTION_HOME_ASSISTANT_CONFIG_CHANGED"

    const val EXTRA_ENABLED = "enabled"
    const val EXTRA_URL = "url"
    const val EXTRA_TOKEN = "token"
    const val EXTRA_SUBSCRIPTIONS = "subscriptions"
    const val EXTRA_SCREEN_OFF_POWER_SAVE = "screen_off_power_save"

    /** 总开关：关闭时 Hook 不建立任何连接。 */
    const val PREF_ENABLED = "pref_home_assistant"

    /** HA 地址，支持 `ws://host:8123` 或完整 `/api/websocket`。 */
    const val PREF_URL = "pref_home_assistant_url"

    /** 长期访问令牌。 */
    const val PREF_TOKEN = "pref_home_assistant_token"

    /** 订阅规则数组的 JSON 文本。 */
    const val PREF_SUBSCRIPTIONS = "pref_home_assistant_subscriptions"

    /**
     * 息屏省电：默认关闭。
     *
     * 开启后，息屏即停止接收 WS 事件，并在息屏满 1 分钟后断开常驻连接；亮屏立即恢复。
     */
    const val PREF_SCREEN_OFF_POWER_SAVE = "pref_home_assistant_screen_off_power_save"

    /** 代发通知的渠道标识，用于场景规则与回溯。 */
    const val SOURCE_CHANNEL_ID = "home_assistant"
}
