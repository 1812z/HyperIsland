package io.github.hyperisland.compose.service

import android.content.Context
import io.github.hyperisland.utils.SystemPropertyReader

/**
 * 服务端下发的一条公告。
 *
 * [hash] 是服务端按「这台设备真正看到的那份正文」算出来的指纹：变了才算一条新公告，
 * 没变就说明用户已经看过并关掉过，不再打扰。客户端不解释版本语义，只比对它。
 */
internal data class AppNotice(
    val hash: String,
    val content: String,
    val hyperOs: String,
)

/**
 * 公告的本地状态与系统版本探测。
 *
 * 「关闭后不再出现」只能记在客户端：服务端不认设备、也没有用户维度（配置共享是匿名设计），
 * 它连哈希都不存。这里只保存**一个**哈希 —— 公告全局只有一条，存集合没有意义。
 *
 * 为什么用独立的 SharedPreferences 而不是模块配置（`flutter.pref_*`）：
 * 那份配置会被同步进程整体搬到 Xposed 端，这里只是一条纯 UI 状态，
 * 混进去等于每次同步都白搬一次，还会让配置分片多出一个无意义的字段。
 */
internal object NoticeService {
    private const val STATE_PREFS = "HyperIslandNoticeState"
    private const val KEY_DISMISSED = "dismissed_hash"

    /**
     * 系统版本名（`OS3.0` / `OS4.0`），取自 `ro.mi.os.version.name`。
     *
     * 取不到时返回 "unknown"：服务端把认不出的值当「未知」处理，这类设备一律会弹。
     * 不要在这里兜底成 OS3.0 之类的猜测值 —— 那会让本该全发的公告被筛掉。
     */
    fun hyperOsVersionName(): String =
        SystemPropertyReader.get("ro.mi.os.version.name").ifBlank { "unknown" }

    /** 这个哈希是否已经被关掉过。空哈希视为「已读」，避免异常数据反复弹窗。 */
    fun isDismissed(context: Context, hash: String): Boolean {
        if (hash.isBlank()) return true
        val prefs = context.applicationContext.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DISMISSED, null) == hash
    }

    /** 记下已关闭的哈希。只留最新一条：旧公告的哈希没有任何用处。 */
    fun markDismissed(context: Context, hash: String) {
        if (hash.isBlank()) return
        context.applicationContext
            .getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DISMISSED, hash)
            .apply()
    }
}
