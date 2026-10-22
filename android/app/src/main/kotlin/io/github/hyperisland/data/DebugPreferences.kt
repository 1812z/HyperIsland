package io.github.hyperisland.data

/**
 * 调试工具总开关。
 *
 * 关闭时 Hook 端不会注册任何调试用 Hook（见 `IslandBurnInDebugHook`），
 * 因此默认值必须是 false —— 普通用户不该为调试代码付任何运行时开销。
 */
object DebugPreferences {
    const val MODE = "pref_debug_mode"
    const val DEFAULT_MODE = false
}
