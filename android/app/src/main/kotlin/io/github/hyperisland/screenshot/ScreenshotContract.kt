package io.github.hyperisland.screenshot

/**
 * 截屏隐藏场景的跨进程协议。
 *
 * 链路：`com.miui.screenshot`（截屏进程）在真正抓取屏幕前广播“开始”，
 * SystemUI 收到后临时隐藏超级岛；截屏流程结束后广播“结束”，SystemUI 恢复显示。
 *
 * 之所以必须跨进程：截屏由 `com.miui.screenshot` 通过 `IWindowManager.captureDisplay`
 * 同步抓取，超级岛窗口属于 SystemUI 进程，只能由 SystemUI 自己隐藏。
 *
 * 已核对的版本：
 * - `miui.systemui.plugin` 18.3.2.57.0（HyperOS 4）；
 * - `com.miui.screenshot` RELEASE-1.6.3.41。
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

    /**
     * 截屏隐藏开关（与 SystemUI 侧同一把开关）。
     *
     * 作为 Hook 拓展独立生效，默认关闭，不依赖隐藏行为总开关。
     */
    const val PREF_ENABLED = "pref_screenshot_temp_hide"

    /**
     * 官方隐藏岛链路的入口类。
     *
     * 系统自己就是靠这些方法驱动临时隐藏的（`TempHiddenBehaviorHook` 同样挂在这里）：
     * [HIDE_METHOD] 会写入 `TempHiddenType.STATUS_BAR_DISAPPEARANCE`，
     * 再经状态流派发到 `DynamicIslandWindowView.onIslandTempHide(boolean, TempHiddenType)`。
     * 17.1.3.76.0（OS3）与 18.3.2.57.0（OS4）均已确认存在且签名一致。
     */
    const val WINDOW_CONTROLLER_CLASS =
        "miui.systemui.dynamicisland.window.DynamicIslandWindowViewController"

    /**
     * 岛窗口根容器。只在 `onIslandTempHide` 里拿一次实例，用于截屏期间临时把 alpha 压 0，
     * 消掉官方隐藏链路在挖孔位置留下的黑色圆圈。
     */
    const val ISLAND_VIEW_CLASS = "miui.systemui.dynamicisland.window.DynamicIslandWindowView"

    /**
     * 官方隐藏岛开关方法：true 收起、false 恢复。
     *
     * ⚠️ 不要用 `screenPinningActive`：它写入的是 `SCREEN_PINNING_ACTIVE`，而
     * `DynamicIslandWindowState.tempHidden` 里 `screenPinning` 的作用是**抑制隐藏**
     * （屏幕固定期间保持岛可见），调它反而不会收起。
     */
    const val HIDE_METHOD = "statusBarAppearance"

    /**
     * 兜底入口：`DynamicIslandWindowState.updateStatusBarDisappearance(boolean)`。
     *
     * 与 [HIDE_METHOD] 等价，只是少了 `updateStatusBarVisible` 的视图联动；
     * 控制器方法被改名时用它继续工作。
     */
    const val STATE_HIDE_METHOD = "updateStatusBarDisappearance"

    /** 截屏进程无法满足“结束”回调时，SystemUI 侧的最长隐藏兜底时间。 */
    const val FALLBACK_HIDE_TIMEOUT_MS = 2_000L

    /**
     * 从“开始”广播发出到真正抓屏之间要求的最小间隔。
     *
     * 广播是异步的，若 SystemUI 还没处理完隐藏就可能已经抓完屏。截屏进程在真正调用
     * `captureDisplay` 前补齐这个间隔，保证隐藏请求已经派发。
     *
     * 取 420ms：广播投递 + 主线程派发约 40–80ms，再给官方收起动作留一段余量。
     * 注意这个时长**不能**单独解决挖孔位置的残留圆圈（240ms / 420ms 都能复现），
     * 那部分由 SystemUI 侧的 alpha 兜底负责。
     */
    const val MIN_CAPTURE_LEAD_MS = 420L

    /**
     * “开始”信号的有效期。超过这个时间再遇到抓屏调用，视作新的一次截屏重新发信号。
     *
     * 长截屏 / 连拍会在同一次会话里多次抓屏，靠这个间隔避免每条都重复广播。
     */
    const val SESSION_GAP_MS = 1_000L

    /**
     * 抓屏闸门自己发起隐藏后，多久补发一次“结束”。
     *
     * 1.6.3.x 起 `TakeScreenshotService` 没有稳定的“会话结束”语义，只能靠闸门兜底；
     * 真正的 `onUnbind` / `onDestroy` 若先到达会提前恢复。SystemUI 侧另有
     * [FALLBACK_HIDE_TIMEOUT_MS] 兜底，这里是第二层保险。
     */
    const val GATE_AUTO_RELEASE_MS = 600L
}
