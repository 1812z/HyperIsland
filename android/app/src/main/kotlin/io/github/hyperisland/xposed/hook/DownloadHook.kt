package io.github.hyperisland.xposed

import android.app.Notification
import android.graphics.drawable.Icon
import android.os.Bundle
import io.github.hyperisland.getAppIcon
import io.github.hyperisland.xposed.templates.DownloadIslandNotification
import io.github.libxposed.api.XposedInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.util.regex.Pattern

/**
 * Xposed Hook — 拦截下载通知并注入小米超级岛参数
 */
object DownloadHook {

    var extrasField: Field? = null

    private val processedNotifications = mutableMapOf<String, NotificationInfo>()
    private val downloadIdMap = mutableMapOf<Long, String>()

    data class NotificationInfo(
        var lastProgress: Int,
        var lastProcessTime: Long,
        var appName: String,
        var downloadId: Long = -1L
    )

    val notifSnapshots = mutableMapOf<String, InProcessController.DownloadNotifSnapshot>()

    init {
        try {
            extrasField = Notification::class.java.getDeclaredField("extras")
            extrasField?.isAccessible = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun init(module: XposedModule, param: PackageLoadedParam) {
        val classLoader = param.classLoader
        val pkg = param.packageName

        module.log("HyperIsland: handleLoadPackage pkg=$pkg")

        try {
            hookNotificationBuilder(module, classLoader, pkg)

            val nmClass = classLoader.loadClass("android.app.NotificationManager")
            hookNotifyMethod(module, nmClass, classLoader, pkg, hasTag = true)
            hookNotifyMethod(module, nmClass, classLoader, pkg, hasTag = false)

            if (pkg == "com.xiaomi.android.app.downloadmanager") {
                InProcessController.hookMiuiDownloadManager(module, classLoader)
            }

            hookDownloadManagerService(module, classLoader)
        } catch (e: Throwable) {
            module.log("HyperIsland: Error hooking $pkg: ${e.message}")
        }
    }

    // ─── Notification.Builder.build() Hook ───────────────────────────────────

    private fun hookNotificationBuilder(module: XposedModule, classLoader: ClassLoader, pkg: String) {
        val builderClasses = listOf(
            "android.app.Notification\$Builder",
            "android.app.Notification.Builder"
        )
        for (builderClassName in builderClasses) {
            try {
                val builderClass = classLoader.loadClass(builderClassName)
                val buildMethod = builderClass.getDeclaredMethod("build")
                module.hook(buildMethod).intercept { chain ->
                    val result = chain.proceed()
                    val notif = result as? Notification
                    if (notif != null) {
                        val extras = extrasField?.get(notif) as? Bundle
                        if (extras != null) {
                            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
                            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                            val channelId = notif.channelId ?: ""
                            module.log("HyperIsland: [RAW/Builder] ch=$channelId | title=$title | text=$text | extras=${extras.keySet().joinToString()}")
                            if (isDownloadNotification(title, text, extras) || channelId.isNotEmpty()) {
                                val appName = pkg.substringAfterLast(".").replaceFirstChar { it.uppercase() }
                                val fileName = extractFileName(title, text, extras)
                                val downloadId = extractDownloadId(extras)
                                val progress = extractProgress(title, text, extras)
                                val key = "${pkg}_${notif.hashCode()}"
                                val now = System.currentTimeMillis()

                                var isNew = false
                                val info = processedNotifications.getOrPut(key) {
                                    isNew = true
                                    NotificationInfo(progress, now, appName, downloadId)
                                }
                                if (isNew || info.lastProgress != progress) {
                                    info.lastProgress = progress; info.lastProcessTime = now; info.appName = appName
                                    if (downloadId > 0) { info.downloadId = downloadId; downloadIdMap[downloadId] = pkg }
                                    processedNotifications.entries.removeIf { now - it.value.lastProcessTime > 10000 }

                                    if (downloadId <= 0) {
                                        module.log("HyperIsland: [Builder] extras keys=${extras.keySet().joinToString()}")
                                    }
                                    module.log("HyperIsland: [Builder] $appName | $fileName | $progress% | id=$downloadId")

                                    val context = getContext(classLoader)
                                    if (context != null) {
                                        InProcessController.ensureRegistered(context)
                                        val appIcon = if (InProcessController.useHookAppIconEnabled)
                                            context.packageManager.getAppIcon(pkg) else null
                                        DownloadIslandNotification.inject(context, extras, title, text, progress, appName, fileName, downloadId, pkg, appIcon = appIcon, channelId = channelId)
                                    }
                                }
                            }
                        }
                    }
                    result
                }
                module.log("HyperIsland: Hooked $builderClassName.build()")
                break
            } catch (_: Throwable) {}
        }
    }

    // ─── NotificationManager.notify() Hook ───────────────────────────────────

    private fun hookNotifyMethod(
        module: XposedModule,
        nmClass: Class<*>,
        classLoader: ClassLoader,
        pkg: String,
        hasTag: Boolean
    ) {
        try {
            val method = if (hasTag)
                nmClass.getDeclaredMethod("notify", String::class.java, Int::class.javaPrimitiveType, Notification::class.java)
            else
                nmClass.getDeclaredMethod("notify", Int::class.javaPrimitiveType, Notification::class.java)

            module.hook(method).intercept { chain ->
                val tag = if (hasTag) chain.args[0] as? String else null
                val id = if (hasTag) chain.args[1] as Int else chain.args[0] as Int
                val notif = (if (hasTag) chain.args[2] else chain.args[1]) as Notification
                handleNotification(notif, module, classLoader, pkg, id, tag)
                chain.proceed()
            }
        } catch (e: Throwable) {
            module.log("HyperIsland: notify hook failed: ${e.message}")
        }
    }

    private fun handleNotification(notif: Notification, module: XposedModule, classLoader: ClassLoader, pkg: String, id: Int, tag: String?) {
        try {
            val extras = extrasField?.get(notif) as? Bundle ?: return
            if (extras.getBoolean("hyperisland_processed", false)) return

            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val channelId = notif.channelId ?: ""
            module.log("HyperIsland: [RAW/Notify] ch=$channelId | title=$title | text=$text")
            if (!isDownloadNotification(title, text, extras) && channelId.isEmpty()) return

            val appName = pkg.substringAfterLast(".").replaceFirstChar { it.uppercase() }
            val fileName = extractFileName(title, text, extras)
            val downloadId = extractDownloadId(extras).takeIf { it > 0 }
                ?: extractIdFromTag(tag).takeIf { it > 0 }
                ?: id.toLong()
            val progress = extractProgress(title, text, extras)
            val key = "${pkg}_${tag ?: "null"}_$id"
            val now = System.currentTimeMillis()

            var isNew = false
            val info = processedNotifications.getOrPut(key) { isNew = true; NotificationInfo(progress, now, appName, downloadId) }
            if (!isNew && info.lastProgress == progress) return
            info.lastProgress = progress; info.lastProcessTime = now; info.appName = appName
            if (downloadId > 0) { info.downloadId = downloadId; downloadIdMap[downloadId] = pkg }
            processedNotifications.entries.removeIf { now - it.value.lastProcessTime > 10000 }

            module.log("HyperIsland: [Notify] $appName | $fileName | $progress% | notifId=$id | tag=$tag | downloadId=$downloadId")

            val context = getContext(classLoader) ?: return
            InProcessController.ensureRegistered(context)
            val appIcon = if (InProcessController.useHookAppIconEnabled)
                context.packageManager.getAppIcon(pkg) else null

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
                cancelLabel, cancelIntent
            ).build()
            notif.actions = when {
                isComplete || isWaiting -> emptyArray()
                else -> arrayOf(
                    Notification.Action.Builder(
                        Icon.createWithResource(context, android.R.drawable.ic_media_pause),
                        pauseLabel, pauseIntent
                    ).build(),
                    cancelAction
                )
            }

            DownloadIslandNotification.inject(context, extras, title, text, progress, appName, fileName, downloadId, pkg, appIcon = appIcon, channelId = channelId)
            extras.putBoolean("hyperisland_processed", true)

            val snapshotKey = "${tag}_$id"
            if (isComplete) {
                notifSnapshots.remove(snapshotKey)
            } else {
                val snapshot = InProcessController.DownloadNotifSnapshot(
                    notifId = id, notifTag = tag,
                    channelId = notif.channelId ?: "download",
                    fileName = fileName, progress = progress,
                    downloadId = downloadId, isMultiFile = isMultiFile,
                    packageName = pkg
                )
                notifSnapshots[snapshotKey] = snapshot
                InProcessController.lastDownloadSnapshot = snapshot
            }

        } catch (e: Throwable) {
            module.log("HyperIsland: handleNotification error: ${e.message}")
        }
    }

    // ─── DownloadManager Hook ─────────────────────────────────────────────────

    private fun hookDownloadManagerService(module: XposedModule, classLoader: ClassLoader) {
        val candidates = listOf(
            "com.android.providers.downloads.DownloadProvider",
            "com.android.providers.downloads.DownloadThread",
            "com.android.providers.downloads.DownloadManager",
            "android.app.DownloadManager"
        )
        for (className in candidates) {
            try {
                val clazz = classLoader.loadClass(className)
                for (method in clazz.declaredMethods) {
                    val name = method.name.lowercase()
                    when {
                        name.contains("pause") -> hookLogMethod(module, clazz, method.name, "Pause")
                        name.contains("resume") -> hookLogMethod(module, clazz, method.name, "Resume")
                        name.contains("cancel") || name.contains("remove") || name.contains("delete") ->
                            hookLogMethod(module, clazz, method.name, "Cancel")
                    }
                }
            } catch (_: ClassNotFoundException) {
            } catch (e: Throwable) {
                module.log("HyperIsland: DownloadManager hook error ($className): ${e.message}")
            }
        }
    }

    private fun hookLogMethod(module: XposedModule, clazz: Class<*>, methodName: String, label: String) {
        try {
            val method = clazz.getDeclaredMethod(methodName)
            module.hook(method).intercept { chain ->
                module.log("HyperIsland: [$label] $methodName called")
                chain.proceed()
            }
        } catch (_: Throwable) {}
    }

    // ─── Utility ──────────────────────────────────────────────────────────────

    private fun getContext(classLoader: ClassLoader): android.content.Context? {
        return try {
            val at = classLoader.loadClass("android.app.ActivityThread")
            at.getMethod("currentApplication").invoke(null) as? android.content.Context
        } catch (_: Exception) {
            try {
                val at = classLoader.loadClass("android.app.ActivityThread")
                (at.getMethod("getSystemContext").invoke(null) as? android.content.Context)?.applicationContext
            } catch (_: Exception) { null }
        }
    }

    private fun isDownloadNotification(title: String, text: String, extras: Bundle): Boolean =
        extras.containsKey("extra_download_id") ||
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
        val current = extras.getLong("extra_download_current_bytes", -1L)
        val total   = extras.getLong("extra_download_total_bytes",   -1L)
        if (current >= 0 && total > 0) return ((current * 100) / total).toInt().coerceIn(0, 100)

        val combined = title + text
        if (combined.contains("下载完成") || combined.contains("完成下载") || combined.contains("下载成功")) return 100

        extras.getInt("progress", -1).takeIf { it >= 0 }?.let { return it }
        extras.getInt("android.progress", -1).takeIf { it >= 0 }?.let { return it }
        extras.getInt("percent", -1).takeIf { it >= 0 }?.let { return it }
        val m = Pattern.compile("(\\d+)%").matcher(combined)
        if (m.find()) return m.group(1)?.toIntOrNull() ?: -1
        return -1
    }

    private fun extractDownloadId(extras: Bundle): Long {
        extras.getLong("extra_download_id", -1L).takeIf { it > 0 }?.let { return it }
        extras.getInt("extra_download_id", -1).takeIf { it > 0 }?.let { return it.toLong() }
        for (key in listOf("android.downloadId", "downloadId", "notification_id")) {
            extras.getLong(key, -1L).takeIf { it > 0 }?.let { return it }
        }
        val intId = extras.getInt("android.downloadId", -1)
        return if (intId > 0) intId.toLong() else -1L
    }

    private fun extractIdFromTag(tag: String?): Long {
        if (tag.isNullOrEmpty()) return -1L
        tag.toLongOrNull()?.takeIf { it > 0 }?.let { return it }
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
