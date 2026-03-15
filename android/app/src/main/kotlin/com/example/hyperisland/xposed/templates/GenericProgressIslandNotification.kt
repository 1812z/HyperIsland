package com.example.hyperisland.xposed.templates

import android.app.Notification
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Bundle
import com.example.hyperisland.xposed.IslandTemplate
import com.example.hyperisland.xposed.NotifData
import com.xzakota.hyper.notification.focus.FocusNotification
import de.robv.android.xposed.XposedBridge

/**
 * 通用进度条灵动岛通知构建器。
 * 适用于任意含进度条的通知，按钮直接取自原通知（最多 2 个），不硬编码暂停/取消。
 */
object GenericProgressIslandNotification : IslandTemplate {

    // const val 在调用处被编译器内联，供无 Xposed 依赖的 TemplateManifest 安全引用
    const val TEMPLATE_ID    = "generic_progress"
    const val TEMPLATE_NAME  = "下载"

    override val id          = TEMPLATE_ID
    override val displayName = TEMPLATE_NAME

    override fun inject(context: Context, extras: Bundle, data: NotifData) = inject(
        context         = context,
        extras          = extras,
        title           = data.title,
        subtitle        = data.subtitle,
        progress        = data.progress,
        actions         = data.actions,
        notifIcon       = data.notifIcon,
        largeIcon       = data.largeIcon,
        appIconRaw      = data.appIconRaw,
        iconMode        = data.iconMode,
        focusNotif      = data.focusNotif,
        firstFloat      = data.firstFloat,
        enableFloatMode = data.enableFloatMode,
    )

    /**
     * 判断文本是否属于"状态噪声"（速度、百分比、下载状态词等），
     * 这类文本不适合作为摘要右侧的内容标题。
     */
    private val NOISE_REGEX = Regex(
        // 文件大小进度：33MB/320MB  |  1.2 GB / 4 GB  |  500KB/1024KB
        """(?i)\d+(\.\d+)?\s*(b|kb|mb|gb|tb|kib|mib|gib)\s*/\s*\d+(\.\d+)?\s*(b|kb|mb|gb|tb|kib|mib|gib)""" +
        // 网速：12.3 MB/s  |  5 KB/s  |  100 Mbps  |  兆/秒 等
        """|(?i)\d+(\.\d+)?\s*(mb/s|kb/s|gb/s|mib/s|kib/s|mbps|kbps|gbps|m/s|兆/秒|兆字节/秒)""" +
        // 百分比：31%  |  100 %
        """|(?i)\d+\s*%""" +
        // 中文下载状态词
        """|下载中|正在下载|准备下载|开始下载|等待下载|排队中|等待中|连接中|获取中|暂停中|已暂停|下载完成|下载失败|下载错误""" +
        // 时间剩余
        """|剩余\s*\d+|还有\s*\d+|剩余时间""" +
        // 英文状态词
        """|(?i)\bdownloading\b|\bdownload\b|\bqueued\b|\bpending\b|\bwaiting\b|\bpaused\b|\bconnecting\b|\bpreparing\b|\bremaining\b"""
    )

    private fun isStatusNoise(text: String): Boolean = NOISE_REGEX.containsMatchIn(text)

    /**
     * 去除标题里常见的下载前缀（如"正在下载 "、"下载中："），
     * 以便在两者都有噪声时尽量提取出有意义的文件名部分。
     */
    private fun stripDownloadPrefix(text: String): String {
        var s = text.trim()
        for (prefix in listOf("正在下载", "下载中", "下载", "Downloading", "Download")) {
            if (s.startsWith(prefix, ignoreCase = true)) {
                s = s.removePrefix(prefix).trimStart(':', '：', ' ', '-')
                break
            }
        }
        return s.trim()
    }

    /**
     * 从 title / subtitle 中挑选适合显示在摘要右侧的内容文本：
     * - 优先选无噪声的一方
     * - 两者都无噪声时优先副标题
     * - 两者都有噪声时对 title 去除下载前缀后返回
     */
    private fun pickContent(title: String, subtitle: String): String {
        val subClean   = subtitle.isNotEmpty() && !isStatusNoise(subtitle)
        val titleClean = title.isNotEmpty()    && !isStatusNoise(title)
        return when {
            subClean              -> subtitle
            titleClean            -> title
            subtitle.isNotEmpty() -> subtitle
            else                  -> stripDownloadPrefix(title)
        }
    }

    private fun inject(
        context: Context,
        extras: Bundle,
        title: String,
        subtitle: String,
        progress: Int,
        actions: List<Notification.Action>,
        notifIcon: Icon?,
        largeIcon: Icon?,
        appIconRaw: Icon?,
        iconMode: String,
        focusNotif: String,
        firstFloat: String,
        enableFloatMode: String,
    ) {
        try {
            val combined   = "$title $subtitle"
            val isComplete = progress >= 100 ||
                combined.contains("完成") || combined.contains("成功") ||
                combined.contains("complete", ignoreCase = true) ||
                combined.contains("finished", ignoreCase = true) ||
                combined.contains("done",     ignoreCase = true)
            val isPaused   = !isComplete && (
                combined.contains("暂停") || combined.contains("已暂停") || combined.contains("暂停中") ||
                combined.contains("paused", ignoreCase = true)
            )
            val isWaiting  = !isComplete && !isPaused && (
                combined.contains("等待") || combined.contains("准备中") ||
                combined.contains("队列") || combined.contains("排队") ||
                combined.contains("pending",  ignoreCase = true) ||
                combined.contains("queued",   ignoreCase = true) ||
                combined.contains("waiting",  ignoreCase = true)
            )

            val stateLabel = when {
                isComplete -> "已完成"
                isPaused   -> "已暂停"
                isWaiting  -> "等待中"
                else       -> "下载中"
            }
            val rightContent   = pickContent(title, subtitle)
            val displayContent = subtitle.ifEmpty { title }

            val iconRes   = if (isComplete) android.R.drawable.stat_sys_download_done
                            else            android.R.drawable.stat_sys_download
            val tintColor = when {
                isComplete            -> 0xFF4CAF50.toInt()  // 绿
                isPaused || isWaiting -> 0xFFFF9800.toInt()  // 橙
                else                  -> 0xFF2196F3.toInt()  // 蓝
            }
            val fallbackIcon = Icon.createWithResource(context, iconRes).apply { setTint(tintColor) }
            val displayIcon  = when (iconMode) {
                "notif_small" -> notifIcon ?: fallbackIcon
                "notif_large" -> largeIcon ?: notifIcon ?: fallbackIcon
                "app_icon"    -> appIconRaw ?: fallbackIcon
                else          -> notifIcon ?: largeIcon ?: fallbackIcon  // auto
            }

            val resolvedFirstFloat  = when (firstFloat)      { "on" -> true; "off" -> false; else -> false }
            val resolvedEnableFloat = when (enableFloatMode)  { "on" -> true; "off" -> false; else -> false }
            val showIsland          = focusNotif != "off"

            val islandExtras = FocusNotification.buildV3 {
                val iconKey = createPicture("key_generic_progress_icon", displayIcon)

                islandFirstFloat = resolvedFirstFloat
                enableFloat      = resolvedEnableFloat
                updatable        = !isComplete && !isPaused

                if (showIsland) island {
                    islandProperty = 1
                    bigIslandArea {
                        imageTextInfoLeft {
                            type = 1
                            picInfo {
                                type = 1
                                pic  = iconKey
                            }
                            textInfo {
                                this.title = stateLabel
                            }
                        }
                        if (!isComplete && progress > 0) {
                            progressTextInfo {
                                textInfo {
                                    this.title = rightContent
                                    narrowFont = true
                                }
                                progressInfo {
                                    this.progress = progress
                                }
                            }
                        } else {
                            imageTextInfoRight {
                                type = 2
                                textInfo {
                                    this.title = rightContent
                                    narrowFont = true
                                }
                            }
                        }
                    }
                    smallIslandArea {
                        picInfo {
                            type = 1
                            pic  = iconKey
                        }
                        if (!isComplete && !isPaused && !isWaiting) {
                            progressInfo {
                                this.progress = progress
                            }
                        }
                    }
                }

                iconTextInfo {
                    this.title = title
                    content    = displayContent
                    animIconInfo {
                        type = 0
                        src  = iconKey
                    }
                }

                val effectiveActions = actions.take(2)
                if (effectiveActions.isNotEmpty()) {
                    textButton {
                        effectiveActions.forEachIndexed { index, action ->
                            addActionInfo {
                                val btnIcon = action.getIcon()
                                    ?: Icon.createWithResource(context, android.R.drawable.ic_menu_send)
                                val wrappedAction = Notification.Action.Builder(
                                    btnIcon,
                                    action.title ?: "",
                                    action.actionIntent,
                                ).build()
                                this.action = createAction("action_generic_$index", wrappedAction)
                                actionTitle  = action.title?.toString() ?: ""
                            }
                        }
                    }
                }
            }

            extras.putAll(islandExtras)

            val stateTag = when {
                isComplete -> "done"
                isPaused   -> "paused"
                isWaiting  -> "waiting"
                else       -> "${progress}%"
            }
            XposedBridge.log("HyperIsland[Generic]: Island injected — $title ($stateTag) buttons=${actions.size}")

        } catch (e: Exception) {
            XposedBridge.log("HyperIsland[Generic]: Island injection error: ${e.message}")
        }
    }
}
