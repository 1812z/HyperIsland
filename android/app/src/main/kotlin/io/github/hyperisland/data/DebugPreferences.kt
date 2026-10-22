package io.github.hyperisland.data

/**
 * 调试工具总开关。
 *
 * 关闭时 Hook 端不会注册任何调试用 Hook（见 `IslandBurnInDebugHook`），
 * 并且忽略 [DISABLED_HOOKS] 里的停用名单 —— 因此默认值必须是 false，
 * 普通用户不该为调试代码付任何运行时开销。
 */
object DebugPreferences {
    const val MODE = "pref_debug_mode"
    const val DEFAULT_MODE = false

    /** 被强制停用的 Hook 简单类名，逗号分隔（与 `pref_generic_whitelist` 同风格）。 */
    const val DISABLED_HOOKS = "pref_debug_disabled_hooks"
}
