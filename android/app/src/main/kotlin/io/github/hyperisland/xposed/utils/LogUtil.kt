package io.github.hyperisland.xposed

import android.util.Log
import io.github.libxposed.api.XposedModule

// ── 扩展函数：持有 XposedModule 引用时使用（hook init 等） ───────────────────

fun XposedModule.log(message: String) =
    log(Log.DEBUG, "HyperIsland", message)

fun XposedModule.logWarn(message: String) =
    log(Log.WARN, "HyperIsland", message)

fun XposedModule.logError(message: String) =
    log(Log.ERROR, "HyperIsland", message)

// ── 独立函数：无 module 引用时使用（template、object 等） ────────────────────

fun log(message: String) =
    ConfigManager.module()?.log(Log.DEBUG, "HyperIsland", message)

fun logWarn(message: String) =
    ConfigManager.module()?.log(Log.WARN, "HyperIsland", message)

fun logError(message: String) =
    ConfigManager.module()?.log(Log.ERROR, "HyperIsland", message)
