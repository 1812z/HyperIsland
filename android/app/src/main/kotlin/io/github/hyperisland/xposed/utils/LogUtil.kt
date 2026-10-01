package io.github.hyperisland.xposed

import android.util.Log
import io.github.libxposed.api.XposedModule

private const val DEFAULT_TAG = "HyperIsland"

// ---- 手里有 module 实例时用这组（Hook 端主路径）----

fun XposedModule.log(message: String) {
    if (ConfigManager.isDebugLogEnabled())
        log(Log.DEBUG, DEFAULT_TAG, message)
}

/** 带自定义 tag 的调试日志；已内置开关判断，调用方不要再判断。 */
fun XposedModule.logDebug(tag: String, message: String) {
    if (ConfigManager.isDebugLogEnabled())
        log(Log.DEBUG, tag, message)
}

fun XposedModule.logWarn(message: String) = log(Log.WARN, DEFAULT_TAG, message)

fun XposedModule.logWarn(tag: String, message: String) = log(Log.WARN, tag, message)

fun XposedModule.logError(message: String) = log(Log.ERROR, DEFAULT_TAG, message)

fun XposedModule.logError(tag: String, message: String) = log(Log.ERROR, tag, message)

// ---- 拿不到 module 实例时用这组（模板/工具类），统一委托上面的版本 ----

fun log(message: String) = ConfigManager.module()?.log(message)

fun logDebug(tag: String, message: String) = ConfigManager.module()?.logDebug(tag, message)

fun logWarn(message: String) = ConfigManager.module()?.logWarn(message)

fun logError(message: String) = ConfigManager.module()?.logError(message)
