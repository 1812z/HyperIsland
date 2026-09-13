package io.github.hyperisland.xposed.hook

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.concurrent.ConcurrentHashMap

/**
 * Starts Xiaomi's screen recognition when a supported pickup-code page becomes visible.
 *
 * AICR normally receives this request from its gesture path. Calling the same public
 * VoiceService entry point after Activity.onResume keeps the implementation independent
 * from each delivery app's view hierarchy and lets AICR continue to apply its own rules.
 */
object PickupPageRecognitionHook : BaseHook() {
    private const val TAG = "HyperIsland[PickupRecognition]"
    private const val VOICE_ASSIST_PACKAGE = "com.miui.voiceassist"
    private const val VOICE_ASSIST_SERVICE = "com.xiaomi.voiceassistant.VoiceService"
    private const val START_FROM = "three_gesture_up"
    private const val COOLDOWN_MS = 15_000L

    private val supportedPackages = setOf(
        "com.mxbc.mxsa",                 // 蜜雪冰城
        "com.sankuai.meituan",           // 美团
        "me.ele", "com.taobao.taobao",  // 饿了么 / 淘宝闪购
        "com.luckincoffee", "com.yumchina.yumchina",
    )
    private val lastRequest = ConcurrentHashMap<String, Long>()

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        if (param.packageName !in supportedPackages) return
        runCatching {
            val activityClass = param.defaultClassLoader.loadClass("android.app.Activity")
            val onResume = activityClass.getDeclaredMethod("onResume")
            module.hook(onResume).intercept { chain ->
                val result = chain.proceed()
                val activity = chain.thisObject as? Activity
                if (activity != null && isPickupPage(activity)) {
                    requestRecognition(activity)
                }
                result
            }
        }.onFailure { error ->
            logError(module, "failed to hook Activity.onResume: ${error.message}")
        }
    }

    private fun isPickupPage(activity: Activity): Boolean {
        val name = activity.javaClass.name.lowercase()
        // The Mixue rule is known and stable; other apps commonly expose these
        // semantic names even when their package-specific rules change.
        if (name == "com.mxbc.mxsa.modules.order.status.finish.orderfinishactivity") return true
        return name.contains("pickup") || name.contains("takeout") ||
            name.contains("take_out") || name.contains("orderfinish") ||
            name.contains("orderdetail") && (name.contains("status") || name.contains("finish"))
    }

    private fun requestRecognition(context: Context) {
        val key = context.packageName + ":" + (context as? Activity)?.javaClass?.name
        val now = SystemClock.elapsedRealtime()
        val previous = lastRequest[key]
        if (previous != null && now - previous < COOLDOWN_MS) return
        lastRequest[key] = now
        runCatching {
            val intent = Intent().setClassName(VOICE_ASSIST_PACKAGE, VOICE_ASSIST_SERVICE)
                .putExtra("voice_assist_start_from_key", START_FROM)
            context.startService(intent)
        }.onFailure {
            lastRequest.remove(key, now)
        }
    }
}
