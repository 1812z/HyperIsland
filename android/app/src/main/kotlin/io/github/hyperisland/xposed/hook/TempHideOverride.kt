package io.github.hyperisland.xposed.hook

/**
 * 跨 Hook 的临时隐藏放行标记。
 *
 * 背景：[TempHiddenBehaviorHook] 会拦截 `DynamicIslandWindowViewController` 上的
 * `statusBarAppearance(boolean)` 等一系列官方隐藏入口：用户在「隐藏行为」里没勾选某个场景时，
 * 系统对该场景的隐藏调用会被强制改写成 `false`。
 *
 * 截屏隐藏同样是调 `statusBarAppearance(true)`，如果用户勾了隐藏行为总开关却**没勾「全屏应用」**，
 * 我们的隐藏就会被那条拦截吃掉，岛一点反应都没有 —— 而且两边平时都不打日志，极难排查。
 *
 * 所以由截屏侧在反射调用前后置位，让 [TempHiddenBehaviorHook] 放行这一段调用。
 * 这是用户显式打开「截屏时隐藏超级岛」后的预期行为，不代表系统自己的全屏隐藏场景被放行。
 */
internal object TempHideOverride {

    private val active = ThreadLocal.withInitial { false }

    /** 当前线程是否正处于模块自己发起的临时隐藏调用中。 */
    fun isActive(): Boolean = active.get() == true

    /** 在 [block] 期间放行，结束后恢复调用前的值（支持嵌套）。 */
    internal fun <T> allow(block: () -> T): T {
        val previous = active.get()
        active.set(true)
        return try {
            block()
        } finally {
            active.set(previous)
        }
    }
}
