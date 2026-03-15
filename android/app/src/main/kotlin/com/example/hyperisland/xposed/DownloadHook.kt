package com.example.hyperisland.xposed

import android.app.Notification
import android.graphics.drawable.Icon
import android.os.Bundle
import com.example.hyperisland.xposed.templates.DownloadIslandNotification
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.Field
import java.util.regex.Pattern

/**
 * Xposed Hook — 拦截下载通知并注入小米超级岛参数
 */
class DownloadHook : IXposedHookLoadPackage {

    companion object {
        var extrasField: Field? = null

        private val processedNotifications = mutableMapOf<String, NotificationInfo>()
        private val downloadIdMap = mutableMapOf<Long, String>()

        data class NotificationInfo(
            var lastProgress: Int,
            var lastProcessTime: Long,
            var appName: String,
            var downloadId: Long = -1L
        )

        // key = "${tag}_${id}"，仅供 cancel hook 降级使用
        val notifSnapshots = mutableMapOf<String, InProcessController.DownloadNotifSnapshot>()

        init {
            try {
                extrasField = Notification::class.java.getDeclaredField("extras")
                extrasField?.isAccessible = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pkg = lpparam.packageName

        // Hook 自身进程：将 MainActivity.isModuleActive() 替换为返回 true
        if (pkg == "com.example.hyperisland") {
            try {
                val mainActivityClass = lpparam.classLoader
                    .loadClass("com.example.hyperisland.MainActivity")
                XposedHelpers.findAndHookMethod(
                    mainActivityClass,
                    "isModuleActive",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            param.result = true
                        }
                    }
                )
                XposedBridge.log("HyperIsland: hooked MainActivity.isModuleActive()")
            } catch (e: Throwable) {
                XposedBridge.log("HyperIsland: failed to hook isModuleActive: ${e.message}")
            }
            return
        }

        // 只处理下载相关的包，避免干扰其他应用
        val isTarget = pkg == "com.android.providers.downloads" ||
                       pkg == "com.xiaomi.android.app.downloadmanager"
        if (!isTarget) return

        XposedBridge.log("HyperIsland: handleLoadPackage pkg=$pkg")

        try {
            hookNotificationBuilder(lpparam)

            val nmClass = lpparam.classLoader.loadClass("android.app.NotificationManager")
            hookNotifyMethod(nmClass, lpparam, hasTag = true)
            hookNotifyMethod(nmClass, lpparam, hasTag = false)


            // 在下载 Manager App 进程里 Hook MiuiDownloadManager
            if (pkg == "com.xiaomi.android.app.downloadmanager") {
                InProcessController.hookMiuiDownloadManager(lpparam)
            }

            hookDownloadManagerService(lpparam)
        } catch (e: Throwable) {
            XposedBridge.log("HyperIsland: Error hooking $pkg: ${e.message}")
        }
    }

    // ─── Notification.Builder.build() Hook ───────────────────────────────────

    private fun hookNotificationBuilder(lpparam: XC_LoadPackage.LoadPackageParam) {
        val builderClasses = listOf(
            "android.app.Notification\$Builder",
            "android.app.Notification.Builder"
        )
        for (builderClassName in builderClasses) {
            try {
                val builderClass = lpparam.classLoader.loadClass(builderClassName)
                XposedHelpers.findAndHookMethod(builderClass, "build", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val notif = param.result as? Notification ?: return
                        val extras = extrasField?.get(notif) as? Bundle ?: return

                        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
                        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                        val channelId = notif.channelId ?: ""
                        // 记录所有通知，用于排查未命中的渠道
                        XposedBridge.log("HyperIsland: [RAW/Builder] ch=$channelId | title=$title | text=$text | extras=${extras.keySet().joinToString()}")
                        if (!isDownloadNotification(title, text, extras) && channelId.isEmpty()) return

                        val appName = lpparam.packageName.substringAfterLast(".").replaceFirstChar { it.uppercase() }
                        val fileName = extractFileName(title, text, extras)
                        val downloadId = extractDownloadId(extras)
                        val progress = extractProgress(title, text, extras)
                        val key = "${lpparam.packageName}_${notif.hashCode()}"
                        val now = System.currentTimeMillis()

                        var isNew = false
                        val info = processedNotifications.getOrPut(key) {
                            isNew = true
                            NotificationInfo(progress, now, appName, downloadId)
                        }
                        if (!isNew && info.lastProgress == progress) return
                        info.lastProgress = progress; info.lastProcessTime = now; info.appName = appName
                        if (downloadId > 0) { info.downloadId = downloadId; downloadIdMap[downloadId] = lpparam.packageName }
                        processedNotifications.entries.removeIf { now - it.value.lastProcessTime > 10000 }

                        // 打印所有 extras key，用于确认真实 downloadId 字段名
                        if (downloadId <= 0) {
                            XposedBridge.log("HyperIsland: [Builder] extras keys=${extras.keySet().joinToString()}")
                        }
                        XposedBridge.log("HyperIsland: [Builder] $appName | $fileName | $progress% | id=$downloadId")

                        val context = getContext(lpparam) ?: return
                        InProcessController.ensureRegistered(context)
                        val appIcon = if (InProcessController.useHookAppIconEnabled)
                            InProcessController.getAppIcon(context, lpparam.packageName) else null
                        DownloadIslandNotification.inject(context, extras, title, text, progress, appName, fileName, downloadId, lpparam.packageName, appIcon = appIcon)
                        // 不在此处设置 hyperisland_processed，让 Notify hook 继续运行设置 notif.actions
                    }
                })
                XposedBridge.log("HyperIsland: Hooked $builderClassName.build()")
                break
            } catch (_: Throwable) {}
        }
    }

    // ─── NotificationManager.notify() Hook ───────────────────────────────────

    private fun hookNotifyMethod(nmClass: Class<*>, lpparam: XC_LoadPackage.LoadPackageParam, hasTag: Boolean) {
        try {
            val paramTypes = if (hasTag)
                arrayOf(String::class.java, Int::class.javaPrimitiveType, Notification::class.java)
            else
                arrayOf(Int::class.javaPrimitiveType, Notification::class.java)

            XposedHelpers.findAndHookMethod(nmClass, "notify", *paramTypes, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val tag = if (hasTag) param.args[0] as? String else null
                    val id = if (hasTag) param.args[1] as Int else param.args[0] as Int
                    val notif = (if (hasTag) param.args[2] else param.args[1]) as Notification
                    handleNotification(notif, lpparam, id, tag)
                }
            })
        } catch (e: Throwable) {
            XposedBridge.log("HyperIsland: notify hook failed: ${e.message}")
        }
    }

    private fun handleNotification(notif: Notification, lpparam: XC_LoadPackage.LoadPackageParam, id: Int, tag: String?) {
        try {
            val extras = extrasField?.get(notif) as? Bundle ?: return
            if (extras.getBoolean("hyperisland_processed", false)) return

            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val channelId = notif.channelId ?: ""
            XposedBridge.log("HyperIsland: [RAW/Notify] ch=$channelId | title=$title | text=$text")
            if (!isDownloadNotification(title, text, extras) && channelId.isEmpty()) return

            val appName = lpparam.packageName.substringAfterLast(".").replaceFirstChar { it.uppercase() }
            val fileName = extractFileName(title, text, extras)
            // 优先 extras，其次解析 tag（AOSP tag 格式: "2162:2163:"），最后用 notifId
            val downloadId = extractDownloadId(extras).takeIf { it > 0 }
                ?: extractIdFromTag(tag).takeIf { it > 0 }
                ?: id.toLong()
            val progress = extractProgress(title, text, extras)
            val key = "${lpparam.packageName}_${tag ?: "null"}_$id"
            val now = System.currentTimeMillis()

            var isNew = false
            val info = processedNotifications.getOrPut(key) { isNew = true; NotificationInfo(progress, now, appName, downloadId) }
            if (!isNew && info.lastProgress == progress) return
            info.lastProgress = progress; info.lastProcessTime = now; info.appName = appName
            if (downloadId > 0) { info.downloadId = downloadId; downloadIdMap[downloadId] = lpparam.packageName }
            processedNotifications.entries.removeIf { now - it.value.lastProcessTime > 10000 }

            XposedBridge.log("HyperIsland: [Notify] $appName | $fileName | $progress% | notifId=$id | tag=$tag | downloadId=$downloadId")

            val context = getContext(lpparam) ?: return
            InProcessController.ensureRegistered(context)
            val appIcon = if (InProcessController.useHookAppIconEnabled)
                InProcessController.getAppIcon(context, lpparam.packageName) else null

            // 把 pause/cancel 写入标准 notification.actions[]
            // MIUI 超级岛点击按钮时，触发的是 actions[] 里的 PendingIntent
            val isComplete = progress >= 100
            val isMultiFile = Regex("""\d+个文件""").containsMatchIn(title + text)
            val pauseIntent  = if (isMultiFile) InProcessController.pauseAllIntent(context)  else InProcessController.pauseIntent(context, downloadId)
            val cancelIntent = if (isMultiFile) InProcessController.cancelAllIntent(context) else InProcessController.cancelIntent(context, downloadId)
            val pauseLabel   = if (isMultiFile) "全部暂停" else "暂停"
            val cancelLabel  = if (isMultiFile) "全部取消" else "取消"
            val isWaiting = !isComplete &&
                            (title.contains("等待") || text.contains("等待") ||
                             title.contains("准备中") || text.contains("准备中"))
            val cancelAction = Notification.Action.Builder(
                Icon.createWithResource(context, android.R.drawable.ic_delete),
                cancelLabel,
                cancelIntent
            ).build()
            // 完成/等待中时清空按钮；下载中显示暂停+取消
            notif.actions = when {
                isComplete || isWaiting -> emptyArray()
                else -> arrayOf(
                    Notification.Action.Builder(
                        Icon.createWithResource(context, android.R.drawable.ic_media_pause),
                        pauseLabel,
                        pauseIntent
                    ).build(),
                    cancelAction
                )
            }

            DownloadIslandNotification.inject(context, extras, title, text, progress, appName, fileName, downloadId, lpparam.packageName, appIcon = appIcon)
            extras.putBoolean("hyperisland_processed", true)

            // 同步最新快照给 InProcessController，供暂停后重建覆盖通知
            val snapshotKey = "${tag}_$id"
            if (isComplete) {
                notifSnapshots.remove(snapshotKey)
            } else {
                val snapshot = InProcessController.DownloadNotifSnapshot(
                    notifId = id, notifTag = tag,
                    channelId = notif.channelId ?: "download",
                    fileName = fileName, progress = progress,
                    downloadId = downloadId, isMultiFile = isMultiFile,
                    packageName = lpparam.packageName
                )
                notifSnapshots[snapshotKey] = snapshot
                InProcessController.lastDownloadSnapshot = snapshot
            }

        } catch (e: Throwable) {
            XposedBridge.log("HyperIsland: handleNotification error: ${e.message}")
        }
    }

    // ─── DownloadManager Hook ─────────────────────────────────────────────────

    private fun hookDownloadManagerService(lpparam: XC_LoadPackage.LoadPackageParam) {
        val candidates = listOf(
            "com.android.providers.downloads.DownloadProvider",
            "com.android.providers.downloads.DownloadThread",
            "com.android.providers.downloads.DownloadManager",
            "android.app.DownloadManager"
        )
        for (className in candidates) {
            try {
                val clazz = lpparam.classLoader.loadClass(className)
                for (method in clazz.declaredMethods) {
                    val name = method.name.lowercase()
                    when {
                        name.contains("pause") -> hookLogMethod(clazz, method.name, "Pause")
                        name.contains("resume") -> hookLogMethod(clazz, method.name, "Resume")
                        name.contains("cancel") || name.contains("remove") || name.contains("delete") ->
                            hookLogMethod(clazz, method.name, "Cancel")
                    }
                }
            } catch (_: ClassNotFoundException) {
            } catch (e: Throwable) {
                XposedBridge.log("HyperIsland: DownloadManager hook error ($className): ${e.message}")
            }
        }
    }

    private fun hookLogMethod(clazz: Class<*>, methodName: String, label: String) {
        try {
            XposedHelpers.findAndHookMethod(clazz, methodName, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    XposedBridge.log("HyperIsland: [$label] $methodName called")
                }
            })
        } catch (_: Throwable) {}
    }

    // ─── Utility ──────────────────────────────────────────────────────────────

    private fun getContext(lpparam: XC_LoadPackage.LoadPackageParam): android.content.Context? {
        return try {
            val activityThread = lpparam.classLoader.loadClass("android.app.ActivityThread")
            activityThread.getMethod("currentApplication").invoke(null) as? android.content.Context
        } catch (e: Exception) {
            try {
                val activityThread = lpparam.classLoader.loadClass("android.app.ActivityThread")
                (activityThread.getMethod("getSystemContext").invoke(null) as? android.content.Context)?.applicationContext
            } catch (_: Exception) { null }
        }
    }

    private fun isDownloadNotification(title: String, text: String, extras: Bundle): Boolean =
        extras.containsKey("extra_download_id") ||       // MiuiDownloadManager 真实 key
        extras.containsKey("extra_download_current_bytes") ||
        title.contains("正在下载") ||
        title.contains("下载", ignoreCase = true) ||
        title.contains("download", ignoreCase = true) ||
        title.contains("等待中") ||
        text.contains("下载", ignoreCase = true) ||
        text.contains("准备", ignoreCase = true) ||
        text.contains("等待中") ||
        extras.containsKey("progress")

    private fun extractProgress(title: String, text: String, extras: Bundle): Int {
        // 优先使用 MiuiDownloadManager 的字节数计算精确进度
        val current = extras.getLong("extra_download_current_bytes", -1L)
        val total   = extras.getLong("extra_download_total_bytes",   -1L)
        if (current >= 0 && total > 0) return ((current * 100) / total).toInt().coerceIn(0, 100)

        // 下载完成检测
        val combined = title + text
        if (combined.contains("下载完成") || combined.contains("完成下载") || combined.contains("下载成功")) return 100

        // 通用字段降级
        extras.getInt("progress", -1).takeIf { it >= 0 }?.let { return it }
        extras.getInt("android.progress", -1).takeIf { it >= 0 }?.let { return it }
        extras.getInt("percent", -1).takeIf { it >= 0 }?.let { return it }
        val m = Pattern.compile("(\\d+)%").matcher(combined)
        if (m.find()) return m.group(1)?.toIntOrNull() ?: -1
        return -1
    }

    private fun extractDownloadId(extras: Bundle): Long {
        // MiuiDownloadManager.EXTRA_DOWNLOAD_ID（最高优先）
        extras.getLong("extra_download_id", -1L).takeIf { it > 0 }?.let { return it }
        extras.getInt("extra_download_id", -1).takeIf { it > 0 }?.let { return it.toLong() }

        // 通用降级
        for (key in listOf("android.downloadId", "downloadId", "notification_id")) {
            extras.getLong(key, -1L).takeIf { it > 0 }?.let { return it }
        }
        val intId = extras.getInt("android.downloadId", -1)
        return if (intId > 0) intId.toLong() else -1L
    }

    /** 从 AOSP tag 格式（如 "2162:2163:" 或 "downloading://2162"）提取第一个 ID */
    private fun extractIdFromTag(tag: String?): Long {
        if (tag.isNullOrEmpty()) return -1L
        // 尝试直接解析（tag 本身就是数字）
        tag.toLongOrNull()?.takeIf { it > 0 }?.let { return it }
        // 匹配 tag 里第一个数字序列
        val m = Pattern.compile("(\\d{3,})").matcher(tag)
        if (m.find()) return m.group(1)?.toLongOrNull() ?: -1L
        return -1L
    }

    private fun extractFileName(title: String, text: String, extras: Bundle): String {
        extractFileNameFromText(title).takeIf { it.isNotEmpty() }?.let { return it }
        extractFileNameFromText(text).takeIf { it.isNotEmpty() }?.let { return it }
        val extraText = extras.getString("android.title") ?: extras.getString("android.text")
        if (extraText != null) extractFileNameFromText(extraText).takeIf { it.isNotEmpty() }?.let { return it }
        return "下载文件"
    }

    private fun extractFileNameFromText(text: String): String {
        if (text.isEmpty()) return ""
        var s = text
        for (prefix in listOf("正在下载", "下载中", "下载", "Downloading", "Download")) {
            if (s.startsWith(prefix)) { s = s.substring(prefix.length).trim(); break }
        }
        for (suffix in listOf("下载中...", "下载中", "下载...", "下载", "Downloading", "Download")) {
            if (s.endsWith(suffix)) { s = s.substring(0, s.length - suffix.length).trim(); break }
        }
        val m = Pattern.compile("([\\u4e00-\\u9fa5\\w\\s\\-_.]+(?:\\.[a-zA-Z0-9]{2,5})?)", Pattern.CASE_INSENSITIVE).matcher(s)
        if (m.find()) {
            val name = m.group(1)?.trim() ?: ""
            return if (name.length > 30) name.substring(0, 27) + "..." else name
        }
        return if (s.length > 30) s.substring(0, 27) + "..." else s
    }
}
