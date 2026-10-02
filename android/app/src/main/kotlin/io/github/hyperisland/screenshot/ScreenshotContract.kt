package io.github.hyperisland.screenshot

/**
 * 截屏隐藏场景的跨进程协议。
 *
 * 链路：`com.miui.screenshot`（截屏进程）在真正抓取屏幕前广播“开始”，
 * SystemUI 收到后临时隐藏超级岛；截屏流程结束后广播“结束”，SystemUI 恢复显示。
 *
 * 之所以必须跨进程：截屏由 `com.miui.screenshot` 通过 `IWindowManager.captureDisplay`
 * 同步抓取，超级岛窗口属于 SystemUI 进程，只能由 SystemUI 自己隐藏。
 */
object ScreenshotContract {

    const val MODULE_PACKAGE = "io.github.hyperisland"

    /** 小米截屏应用包名，需要登记到 xposed scope.list。 */
    const val TARGET_PACKAGE = "com.miui.screenshot"

    /** 承载截图主流程的服务（非混淆类名，各版本稳定）。 */
    const val TAKE_SERVICE_CLASS = "com.miui.screenshot.TakeScreenshotService"

    /** 截屏进程 → SystemUI 的状态广播。 */
    const val ACTION_STATE = "io.github.hyperisland.action.SCREENSHOT_STATE"

    /** boolean：true 表示截屏进行中，false 表示已结束。 */
    const val EXTRA_ACTIVE = "hyperisland_screenshot_active"

    /** 截屏隐藏开关（与 SystemUI 侧同一把开关）。 */
    const val PREF_ENABLED = "pref_temp_hide_screenshot"

    /** 截屏进程无法满足“结束”回调时，SystemUI 侧的最长隐藏兜底时间。 */
    const val FALLBACK_HIDE_TIMEOUT_MS = 2_000L

    /** 隐藏窗口期间重新压制可见性的间隔，防止系统在隐藏窗口内把岛重新显示出来。 */
    const val REASSERT_INTERVAL_MS = 16L

    /**
     * 从“开始”广播发出到真正抓屏之间要求的最小间隔。
     *
     * 广播是异步的，若 SystemUI 还没处理完隐藏就可能已经抓完屏。截屏进程在真正调用
     * `captureDisplay` 前补齐这个间隔，保证岛至少已经不可见并被合成出来。
     */
    const val MIN_CAPTURE_LEAD_MS = 160L

    /**
     * “开始”信号的有效期。超过这个时间再遇到抓屏调用，视作新的一次截屏重新发信号。
     *
     * 长截屏 / 连拍会在同一次会话里多次抓屏，靠这个间隔避免每条都重复广播。
     */
    const val SESSION_GAP_MS = 1_000L

    /**
     * 抓屏闸门自己发起隐藏后，多久补发一次“结束”。
     *
     * 1.6.3.x 起 `TakeScreenshotService` 不再有稳定的“会话结束”回调，只能靠闸门兜底；
     * 真正的 `onUnbind` / `onDestroy` 若先到达会提前恢复。SystemUI 侧另有
     * [FALLBACK_HIDE_TIMEOUT_MS] 兜底，这里是第二层保险。
     */
    const val GATE_AUTO_RELEASE_MS = 600L
}
