package com.example.hyperisland.xposed

import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle

/**
 * 超级岛展示请求，跨进程通过 Intent extras 传递。
 *
 * ### 两种使用方式
 * - **SystemUI 进程内（其他 Xposed 模块）**：
 *   ```kotlin
 *   IslandDispatcher.post(context, IslandRequest(title = "标题", content = "内容"))
 *   ```
 * - **外部进程（HyperIsland 应用或其他模块）**：
 *   ```kotlin
 *   IslandDispatcher.sendBroadcast(context, IslandRequest(title = "标题", content = "内容"))
 *   ```
 */
data class IslandRequest(
    /** 主标题（大岛左侧 / 焦点通知标题）*/
    val title: String,
    /** 副标题（大岛右侧 / 焦点通知内容）*/
    val content: String,
    /**
     * 岛图标实例（大岛、小岛、焦点通知共用）。
     * 为 null 时 [IslandDispatcher] 自动回退到 HyperIsland 应用自身图标。
     * 推荐使用 [Icon.createWithBitmap] 以保证跨进程可用。
     */
    val icon: Icon? = null,
    /** 通知 ID；相同 ID 的旧通知会先被取消以触发岛动画。*/
    val notifId: Int = IslandDispatcher.NOTIF_ID,
    /** 岛自动收起超时，单位秒，默认 5。*/
    val timeoutSecs: Int = 5,
    /** 首次弹出时是否自动展开大岛。*/
    val firstFloat: Boolean = true,
    /** 后续更新时是否自动展开大岛。*/
    val enableFloat: Boolean = true,
    /** 是否在通知栏显示持久通知（焦点通知），默认 true。*/
    val showNotification: Boolean = true,
    /**
     * 岛边框高亮颜色，十六进制字符串，如 `"#E040FB"`。
     * null 表示不设置，使用系统默认颜色。
     */
    val highlightColor: String? = null,
    /**
     * 为 true 时强制立即关闭当前正在显示的岛，不展示新内容。
     * 常用于主动收起进行中的岛通知。
     */
    val dismissIsland: Boolean = false,
    /**
     * 点击通知时触发的 PendingIntent，对应原通知的 contentIntent。
     * 代发焦点通知时传入，使点击行为与原通知一致。
     */
    val contentIntent: android.app.PendingIntent? = null,
) {
    fun toBundle(): Bundle = Bundle().apply {
        putString(KEY_TITLE,          title)
        putString(KEY_CONTENT,        content)
        putParcelable(KEY_ICON,       icon)
        putInt(KEY_NOTIF_ID,          notifId)
        putInt(KEY_TIMEOUT,           timeoutSecs)
        putBoolean(KEY_FIRST_FLOAT,   firstFloat)
        putBoolean(KEY_ENABLE_FLOAT,  enableFloat)
        putBoolean(KEY_SHOW_NOTIF,    showNotification)
        putString(KEY_HIGHLIGHT,      highlightColor)
        putBoolean(KEY_DISMISS,       dismissIsland)
        putParcelable(KEY_CONTENT_INTENT, contentIntent)
    }

    companion object {
        private const val KEY_TITLE          = "title"
        private const val KEY_CONTENT        = "content"
        private const val KEY_ICON           = "icon"
        private const val KEY_NOTIF_ID       = "notifId"
        private const val KEY_TIMEOUT        = "timeoutSecs"
        private const val KEY_FIRST_FLOAT    = "firstFloat"
        private const val KEY_ENABLE_FLOAT   = "enableFloat"
        private const val KEY_SHOW_NOTIF     = "showNotification"
        private const val KEY_HIGHLIGHT      = "highlightColor"
        private const val KEY_DISMISS        = "dismissIsland"
        private const val KEY_CONTENT_INTENT = "contentIntent"

        fun fromBundle(b: Bundle) = IslandRequest(
            title            = b.getString(KEY_TITLE, ""),
            content          = b.getString(KEY_CONTENT, ""),
            icon             = iconFromBundle(b),
            notifId          = b.getInt(KEY_NOTIF_ID, IslandDispatcher.NOTIF_ID),
            timeoutSecs      = b.getInt(KEY_TIMEOUT, 5),
            firstFloat       = b.getBoolean(KEY_FIRST_FLOAT, true),
            enableFloat      = b.getBoolean(KEY_ENABLE_FLOAT, true),
            showNotification = b.getBoolean(KEY_SHOW_NOTIF, true),
            highlightColor   = b.getString(KEY_HIGHLIGHT),
            dismissIsland    = b.getBoolean(KEY_DISMISS, false),
            contentIntent    = pendingIntentFromBundle(b),
        )

        private fun iconFromBundle(b: Bundle): Icon? =
            if (Build.VERSION.SDK_INT >= 33)
                b.getParcelable(KEY_ICON, Icon::class.java)
            else
                @Suppress("DEPRECATION") b.getParcelable(KEY_ICON)

        private fun pendingIntentFromBundle(b: Bundle): android.app.PendingIntent? =
            if (Build.VERSION.SDK_INT >= 33)
                b.getParcelable(KEY_CONTENT_INTENT, android.app.PendingIntent::class.java)
            else
                @Suppress("DEPRECATION") b.getParcelable(KEY_CONTENT_INTENT)

        fun fromIntent(intent: Intent) = fromBundle(intent.extras ?: Bundle())
    }
}
