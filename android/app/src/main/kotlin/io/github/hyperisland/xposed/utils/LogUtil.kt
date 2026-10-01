package io.github.hyperisland.xposed

import android.util.Log
import io.github.libxposed.api.XposedModule

@PublishedApi
internal const val DEFAULT_TAG = "HyperIsland"

// ───────────────────────────────────────────────────────────────────────────
// 调试日志（受 pref_debug_log 控制）
//
// message 一律是 lambda，不是 String：
//     module.log { "xxx $value" }
//
// 关键点：开关判断发生在 lambda 执行之前。关闭调试时直接短路返回，
// lambda 根本不会执行 —— 字符串拼接、toString、joinToString、反射探测
// 全部零开销。调用方不要再自己判断开关。
// ───────────────────────────────────────────────────────────────────────────

/** 有 module 实例时的调试日志（Hook 主路径）。 */
inline fun XposedModule.log(message: () -> String) {
    if (ConfigManager.isDebugLogEnabled()) {
        log(Log.DEBUG, DEFAULT_TAG, message())
    }
}

/** 有 module 实例、带自定义 tag 的调试日志；已内置开关判断，调用方不要再判断。 */
inline fun XposedModule.logDebug(tag: String, message: () -> String) {
    if (ConfigManager.isDebugLogEnabled()) {
        log(Log.DEBUG, tag, message())
    }
}

// ── 警告 / 错误始终输出，不受调试开关影响，保持 String 参数（无需延迟求值）──

fun XposedModule.logWarn(message: String) = log(Log.WARN, DEFAULT_TAG, message)

fun XposedModule.logWarn(tag: String, message: String) = log(Log.WARN, tag, message)

fun XposedModule.logWarn(tag: String, message: String, throwable: Throwable) =
    log(Log.WARN, tag, "$message: ${Log.getStackTraceString(throwable)}")

fun XposedModule.logError(message: String) = log(Log.ERROR, DEFAULT_TAG, message)

fun XposedModule.logError(tag: String, message: String) = log(Log.ERROR, tag, message)

fun XposedModule.logError(tag: String, message: String, throwable: Throwable) =
    log(Log.ERROR, tag, "$message: ${Log.getStackTraceString(throwable)}")

// ───────────────────────────────────────────────────────────────────────────
// 拿不到 module 实例时用这组（模板 / 工具类），统一委托上面的版本。
// ───────────────────────────────────────────────────────────────────────────

inline fun log(message: () -> String) {
    ConfigManager.module()?.log(message)
}

inline fun logDebug(tag: String, message: () -> String) {
    ConfigManager.module()?.logDebug(tag, message)
}

fun logWarn(message: String) = ConfigManager.module()?.logWarn(message)

fun logWarn(tag: String, message: String) = ConfigManager.module()?.logWarn(tag, message)

fun logWarn(tag: String, message: String, throwable: Throwable) =
    ConfigManager.module()?.logWarn(tag, message, throwable)

fun logError(message: String) = ConfigManager.module()?.logError(message)

fun logError(tag: String, message: String) = ConfigManager.module()?.logError(tag, message)

fun logError(tag: String, message: String, throwable: Throwable) =
    ConfigManager.module()?.logError(tag, message, throwable)
