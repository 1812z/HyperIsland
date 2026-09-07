package io.github.hyperisland.xposed

import android.app.DownloadManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import io.github.libxposed.api.XposedModule

/**
 * 进程内下载控制器。
 * 不硬编码类名，从 getSystemService 的运行时类直接反射。
 * pause/resume 完全复刻 MiuiDownloadManager 的 ContentProvider 逻辑。
 */
object InProcessController {

    private const val TAG = "HyperIsland[InProcessController]"

    private const val ACTION          = "io.github.hyperisland.INTERNAL_CTRL"
    private const val EXTRA_CMD       = "cmd"
    private const val EXTRA_ID        = "dlId"
    private const val EXTRA_NOTIF_ID  = "notifId"
    private const val EXTRA_NOTIF_TAG = "notifTag"

    const val CMD_PAUSE   = "pause"
    const val CMD_RESUME  = "resume"
    const val CMD_CANCEL  = "cancel"
    const val CMD_DISMISS = "dismiss"
    const val EXTRA_PAUSED_OVERLAY = "io.github.hyperisland.extra.PAUSED_DOWNLOAD_OVERLAY"
    const val EXTRA_HAS_TASK_ICON = "io.github.hyperisland.extra.HAS_DOWNLOAD_TASK_ICON"

    private const val STATUS_PENDING       = 190
    private const val STATUS_RUNNING       = 192
    private const val STATUS_PAUSED_BY_APP = 193
    private const val STATUS_NOT_FOUND     = Int.MIN_VALUE
    private const val CONTROL_RUN          = 0
    private const val CONTROL_PAUSED       = 1

    private val DOWNLOADS_URI     = Uri.parse("content://downloads/my_downloads")
    private val DOWNLOADS_URI_ALL = Uri.parse("content://downloads/all_downloads")

    @Volatile private var registered = false
    @Volatile private var resumeNotificationEnabled = true
    @Volatile private var showTaskIconEnabled = true
    @Volatile private var module: XposedModule? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    data class DownloadNotifSnapshot(
        val notifId: Int,
        val notifTag: String?,
        val channelId: String,
        val fileName: String,
        val progress: Int,
        val downloadId: Long,
        val isMultiFile: Boolean,
        val packageName: String,
        val taskIcon: Icon? = null,
        val downloadIdReliable: Boolean = true,
    )

    @Volatile var lastDownloadSnapshot: DownloadNotifSnapshot? = null
    private const val PAUSED_OVERLAY_ID = 0x48594F01

    private fun loadSettings() {
        resumeNotificationEnabled = ConfigManager.getBoolean("pref_resume_notification", true)
        showTaskIconEnabled = ConfigManager.getBoolean("pref_download_show_task_icon", true)
        module?.log(
            "$TAG: settings loaded — resumeNotification=$resumeNotificationEnabled, " +
                "showTaskIcon=$showTaskIconEnabled"
        )
    }

    fun ensureRegistered(context: Context, xposedModule: XposedModule) {
        if (registered) return
        val appCtx = context.applicationContext ?: context
        module = xposedModule

        ConfigManager.init(xposedModule)
        loadSettings()
        ConfigManager.addChangeListener { loadSettings() }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(EXTRA_ID, -1L)
                val cmd = intent.getStringExtra(EXTRA_CMD)
                when (cmd) {
                    CMD_PAUSE -> {
                        val isAll = id <= 0
                        val snapshot = lastDownloadSnapshot
                        if (isAll) pauseAll(appCtx) else pause(appCtx, id)
                        schedulePausedOverlay(appCtx, isAll, snapshot)
                    }
                    CMD_RESUME -> {
                        lastDownloadSnapshot = null
                        if (id > 0) resume(appCtx, id) else resumeAll(appCtx)
                        cancelPausedOverlay(appCtx)
                    }
                    CMD_CANCEL -> {
                        lastDownloadSnapshot = null
                        if (id > 0) cancel(appCtx, id) else cancelAll(appCtx)
                        cancelPausedOverlay(appCtx)
                    }
                    CMD_DISMISS -> {
                        val notifId  = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
                        val notifTag = intent.getStringExtra(EXTRA_NOTIF_TAG)
                        if (notifId > 0) {
                            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                            nm?.cancel(notifTag, notifId)
                        }
                    }
                }
            }
        }

        val filter = IntentFilter(ACTION)
        if (Build.VERSION.SDK_INT >= 33) {
            appCtx.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            appCtx.registerReceiver(receiver, filter)
        }
        registerDownloadObserver(appCtx)
        registered = true
        xposedModule.log("$TAG: registered in pid=${android.os.Process.myPid()}")
    }

    /**
     * The paused notification is owned by this module, not DownloadProvider. Observe its table so
     * deleting or otherwise ending the paused task outside our action buttons also removes it.
     */
    private fun registerDownloadObserver(context: Context) {
        val reconcile = Runnable { reconcilePausedOverlay(context) }
        val observer = object : ContentObserver(mainHandler) {
            override fun onChange(selfChange: Boolean) {
                mainHandler.removeCallbacks(reconcile)
                mainHandler.postDelayed(reconcile, 200L)
            }

            override fun onChange(selfChange: Boolean, uri: Uri?) = onChange(selfChange)
        }
        var registeredAny = false
        for (uri in listOf(DOWNLOADS_URI_ALL, DOWNLOADS_URI)) {
            try {
                context.contentResolver.registerContentObserver(uri, true, observer)
                registeredAny = true
            } catch (e: Exception) {
                module?.logError("$TAG: register observer uri=$uri err=${e.message}")
            }
        }
        if (!registeredAny) module?.logError("$TAG: unable to observe download changes")
    }

    private fun reconcilePausedOverlay(context: Context) {
        val snapshot = lastDownloadSnapshot ?: return
        val state = queryDownloadState(context, snapshot.downloadId)
        val shouldRemove = when {
            snapshot.isMultiFile || !snapshot.downloadIdReliable ->
                queryHasPausedDownload(context) == false
            state == null -> false
            else -> state.deleted || state.status == STATUS_NOT_FOUND
        }
        if (shouldRemove) {
            lastDownloadSnapshot = null
            cancelPausedOverlay(context)
            module?.log("$TAG: removed stale paused overlay for id=${snapshot.downloadId}")
        }
    }

    private data class DownloadState(val status: Int, val deleted: Boolean)

    private fun queryDownloadState(context: Context, downloadId: Long): DownloadState? {
        if (downloadId <= 0) return DownloadState(STATUS_NOT_FOUND, deleted = true)
        var queried = false
        for (uri in listOf(DOWNLOADS_URI_ALL, DOWNLOADS_URI)) {
            try {
                context.contentResolver.query(
                    uri,
                    arrayOf("status", "deleted"),
                    "_id = ?",
                    arrayOf(downloadId.toString()),
                    null,
                )?.use { cursor ->
                    queried = true
                    if (cursor.moveToFirst()) {
                        return DownloadState(cursor.getInt(0), cursor.getInt(1) != 0)
                    }
                }
            } catch (_: Exception) {}
        }
        return if (queried) DownloadState(STATUS_NOT_FOUND, deleted = true) else null
    }

    private fun queryHasPausedDownload(context: Context): Boolean? {
        var queried = false
        for (uri in listOf(DOWNLOADS_URI_ALL, DOWNLOADS_URI)) {
            try {
                context.contentResolver.query(
                    uri,
                    arrayOf("_id"),
                    "status = ? AND (deleted IS NULL OR deleted != 1)",
                    arrayOf(STATUS_PAUSED_BY_APP.toString()),
                    null,
                )?.use { cursor ->
                    queried = true
                    if (cursor.moveToFirst()) return true
                }
            } catch (_: Exception) {}
        }
        return if (queried) false else null
    }

    /**
     * Rebuild the minimum notification state from DownloadProvider. This is needed when the
     * provider process is recreated: DownloadService.onCreate clears notifications before an
     * in-memory snapshot from the previous process can exist.
     */
    private fun queryPausedSnapshot(context: Context): DownloadNotifSnapshot? {
        val projection = arrayOf(
            "_id",
            "title",
            "current_bytes",
            "total_bytes",
            "notificationpackage",
        )
        for (uri in listOf(DOWNLOADS_URI_ALL, DOWNLOADS_URI)) {
            try {
                context.contentResolver.query(
                    uri,
                    projection,
                    "status = ? AND (deleted IS NULL OR deleted != 1)",
                    arrayOf(STATUS_PAUSED_BY_APP.toString()),
                    "lastmod DESC",
                )?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use
                    val count = cursor.count.coerceAtLeast(1)
                    val currentBytes = cursor.getLong(2)
                    val totalBytes = cursor.getLong(3)
                    val progress = if (totalBytes > 0L) {
                        ((currentBytes.coerceAtLeast(0L) * 100L) / totalBytes)
                            .coerceIn(0L, 100L)
                            .toInt()
                    } else {
                        -1
                    }
                    val title = cursor.getString(1)?.takeIf(String::isNotBlank)
                        ?: if (count > 1) "$count 个文件" else "下载任务"
                    return DownloadNotifSnapshot(
                        notifId = PAUSED_OVERLAY_ID,
                        notifTag = null,
                        channelId = "active",
                        fileName = title,
                        progress = progress,
                        downloadId = cursor.getLong(0),
                        isMultiFile = count > 1,
                        packageName = cursor.getString(4)?.takeIf(String::isNotBlank)
                            ?: context.packageName,
                        downloadIdReliable = true,
                    )
                }
            } catch (e: Exception) {
                module?.logError("$TAG: rebuild paused snapshot uri=$uri err=${e.message}")
            }
        }
        return null
    }

    fun resolveTaskIcon(extras: Bundle): Icon? {
        if (!showTaskIconEnabled || !extras.getBoolean(EXTRA_HAS_TASK_ICON, false)) return null
        return extractTaskIcon(extras)
    }

    fun isTaskIconEnabled(): Boolean = showTaskIconEnabled

    fun resolveTaskThumbnailUrl(context: Context, ownerPackage: String?): String? {
        if (!showTaskIconEnabled) return null
        val selections = buildList {
            if (!ownerPackage.isNullOrBlank()) {
                add(
                    "notificationpackage = ? AND status IN (?, ?, ?) AND " +
                        "(deleted IS NULL OR deleted != 1)" to
                        arrayOf(
                            ownerPackage,
                            STATUS_PENDING.toString(),
                            STATUS_RUNNING.toString(),
                            STATUS_PAUSED_BY_APP.toString(),
                        )
                )
            }
            add(
                "status IN (?, ?, ?) AND (deleted IS NULL OR deleted != 1)" to
                    arrayOf(
                        STATUS_PENDING.toString(),
                        STATUS_RUNNING.toString(),
                        STATUS_PAUSED_BY_APP.toString(),
                    )
            )
        }
        for (uri in listOf(DOWNLOADS_URI_ALL, DOWNLOADS_URI)) {
            for ((selection, args) in selections) {
                try {
                    context.contentResolver.query(
                        uri,
                        arrayOf("download_task_thumbnail"),
                        selection,
                        args,
                        "lastmod DESC",
                    )?.use { cursor ->
                        while (cursor.moveToNext()) {
                            cursor.getString(0)?.takeIf(String::isNotBlank)?.let { return it }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        return null
    }

    fun applyTaskIcon(extras: Bundle, icon: Icon?) {
        if (!showTaskIconEnabled || icon == null) {
            extras.remove(Notification.EXTRA_LARGE_ICON)
            extras.remove("android.largeIcon.big")
            extras.remove("miui.appIcon")
            return
        }
        extras.putBoolean(EXTRA_HAS_TASK_ICON, true)
        extras.putParcelable(Notification.EXTRA_LARGE_ICON, icon)
        extras.putParcelable("android.largeIcon.big", icon)
    }

    private fun extractTaskIcon(extras: Bundle): Icon? {
        val keys = listOf(
            Notification.EXTRA_LARGE_ICON,
            "android.largeIcon.big",
            "miui.appIcon",
            "extra_download_icon",
            "download_icon",
            "task_icon",
        )
        for (key in keys) {
            @Suppress("DEPRECATION")
            when (val value = extras.get(key)) {
                is Icon -> return value
                is Bitmap -> return Icon.createWithBitmap(value)
            }
        }
        return null
    }

    fun onDownloadDeleted(context: Context, downloadId: Long, title: String?) {
        val snapshot = lastDownloadSnapshot ?: return
        val sameTask = (snapshot.downloadIdReliable && snapshot.downloadId == downloadId) ||
            (!title.isNullOrBlank() && normalizeTitle(snapshot.fileName) == normalizeTitle(title))
        if (!snapshot.isMultiFile && sameTask) {
            lastDownloadSnapshot = null
            cancelPausedOverlay(context)
            module?.log("$TAG: removed paused overlay after UI delete id=$downloadId")
        } else {
            mainHandler.postDelayed({ reconcilePausedOverlay(context) }, 100L)
        }
    }

    private fun normalizeTitle(value: String): String =
        value.lowercase().filter { it.isLetterOrDigit() || it == '.' }

    /**
     * Hook MiuiDownloadManager 方法（仅用于日志/调试）。
     * 在 com.xiaomi.android.app.downloadmanager 进程中调用。
     */
    fun hookMiuiDownloadManager(module: XposedModule, classLoader: ClassLoader) {
        val candidates = listOf(
            "com.xiaomi.android.app.downloadmanager.MiuiDownloadManager",
            "com.android.providers.downloads.MiuiDownloadManager",
            "miui.app.MiuiDownloadManager"
        )
        for (className in candidates) {
            try {
                val clazz = classLoader.loadClass(className)
                module.log("$TAG: Found MiuiDownloadManager: $className")

                val pauseMethod = clazz.getDeclaredMethod("pauseDownload", LongArray::class.java)
                module.hook(pauseMethod).intercept { chain ->
                    val ids = chain.args[0] as? LongArray
                    module.log("$TAG: pauseDownload called ids=${ids?.toList()}")
                    chain.proceed()
                }
                module.log("$TAG: Hooked pauseDownload in $className")
                break
            } catch (_: Throwable) {}
        }
    }

    // ── PendingIntent 工厂 ────────────────────────────────────────────────────

    fun pauseIntent(context: Context, downloadId: Long)  = makeIntent(context, CMD_PAUSE,  downloadId, reqCode(downloadId, 0))
    fun resumeIntent(context: Context, downloadId: Long) = makeIntent(context, CMD_RESUME, downloadId, reqCode(downloadId, 1))
    fun cancelIntent(context: Context, downloadId: Long) = makeIntent(context, CMD_CANCEL, downloadId, reqCode(downloadId, 2))

    fun pauseAllIntent(context: Context)  = makeIntent(context, CMD_PAUSE,  -1L, 9000001)
    fun cancelAllIntent(context: Context) = makeIntent(context, CMD_CANCEL, -1L, 9000002)
    fun resumeAllIntent(context: Context) = makeIntent(context, CMD_RESUME, -1L, 9000003)

    fun dismissIntent(context: Context, notifId: Int, notifTag: String?): PendingIntent {
        val intent = Intent(ACTION).apply {
            putExtra(EXTRA_CMD, CMD_DISMISS)
            putExtra(EXTRA_NOTIF_ID, notifId)
            if (notifTag != null) putExtra(EXTRA_NOTIF_TAG, notifTag)
        }
        return PendingIntent.getBroadcast(
            context, notifId + 100000, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun reqCode(id: Long, offset: Int) = ((id and 0xFFFFF) * 3 + offset).toInt()

    private fun makeIntent(context: Context, cmd: String, downloadId: Long, requestCode: Int): PendingIntent {
        val intent = Intent(ACTION).apply {
            putExtra(EXTRA_CMD, cmd)
            putExtra(EXTRA_ID, downloadId)
        }
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // ── 控制逻辑 ──────────────────────────────────────────────────────────────

    private fun pause(context: Context, downloadId: Long) {
        val realIds = queryActiveIds(context)
        val idsToTry = (listOf(downloadId) + realIds).distinct()
        val values = ContentValues().apply {
            put("status",  STATUS_PAUSED_BY_APP)
            put("control", CONTROL_PAUSED)
        }
        for (id in idsToTry) {
            for (uri in listOf(DOWNLOADS_URI_ALL, DOWNLOADS_URI)) {
                try {
                    val rows = context.contentResolver.update(uri, values, "_id = ?", arrayOf(id.toString()))
                    if (rows > 0) return
                } catch (e: Exception) {
                    module?.logError("$TAG: pause id=$id uri=$uri err=${e.message}")
                }
            }
        }
        pauseAll(context)
    }

    private fun resume(context: Context, downloadId: Long) {
        val realIds = queryPausedIds(context)
        val idsToTry = (listOf(downloadId) + realIds).distinct()
        val values = ContentValues().apply {
            put("status",  STATUS_RUNNING)
            put("control", CONTROL_RUN)
        }
        for (id in idsToTry) {
            for (uri in listOf(DOWNLOADS_URI_ALL, DOWNLOADS_URI)) {
                try {
                    val rows = context.contentResolver.update(uri, values, "_id = ?", arrayOf(id.toString()))
                    if (rows > 0) return
                } catch (e: Exception) {
                    module?.logError("$TAG: resume id=$id uri=$uri err=${e.message}")
                }
            }
        }
        resumeAll(context)
    }

    private fun queryActiveIds(context: Context): List<Long> {
        return try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            val cursor = dm?.query(
                DownloadManager.Query().setFilterByStatus(
                    DownloadManager.STATUS_RUNNING or DownloadManager.STATUS_PENDING
                )
            )
            val ids = mutableListOf<Long>()
            cursor?.use {
                val col = it.getColumnIndex(DownloadManager.COLUMN_ID)
                while (it.moveToNext()) if (col >= 0) ids.add(it.getLong(col))
            }
            ids
        } catch (e: Exception) {
            module?.logError("$TAG: queryActiveIds err=${e.message}")
            emptyList()
        }
    }

    private fun queryPausedIds(context: Context): List<Long> {
        return try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            val cursor = dm?.query(DownloadManager.Query().setFilterByStatus(DownloadManager.STATUS_PAUSED))
            val ids = mutableListOf<Long>()
            cursor?.use {
                val col = it.getColumnIndex(DownloadManager.COLUMN_ID)
                while (it.moveToNext()) if (col >= 0) ids.add(it.getLong(col))
            }
            ids
        } catch (e: Exception) {
            module?.logError("$TAG: queryPausedIds err=${e.message}")
            emptyList()
        }
    }

    private fun cancel(context: Context, downloadId: Long) {
        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            val n = dm?.remove(downloadId) ?: 0
            if (n == 0) cancelAll(context)
        } catch (e: Exception) {
            module?.logError("$TAG: cancel failed: ${e.message}")
            cancelAll(context)
        }
    }

    private fun pauseAll(context: Context) {
        val values = ContentValues().apply {
            put("status",  STATUS_PAUSED_BY_APP)
            put("control", CONTROL_PAUSED)
        }
        for (uri in listOf(DOWNLOADS_URI_ALL, DOWNLOADS_URI)) {
            try {
                val rows = context.contentResolver.update(
                    uri, values,
                    "status = ? OR status = ?",
                    arrayOf(STATUS_RUNNING.toString(), STATUS_PENDING.toString())
                )
                if (rows > 0) return
            } catch (e: Exception) {
                module?.logError("$TAG: pauseAll uri=$uri err=${e.message}")
            }
        }
    }

    private fun resumeAll(context: Context) {
        try {
            val values = ContentValues().apply {
                put("status",  STATUS_RUNNING)
                put("control", CONTROL_RUN)
            }
            context.contentResolver.update(
                DOWNLOADS_URI, values, "status = ?", arrayOf(STATUS_PAUSED_BY_APP.toString())
            )
        } catch (e: Exception) {
            module?.logError("$TAG: resumeAll failed: ${e.message}")
        }
    }

    private fun cancelAll(context: Context) {
        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            val cursor = context.contentResolver.query(
                DOWNLOADS_URI, arrayOf("_id"),
                "status = ? OR status = ? OR status = ?",
                arrayOf(
                    STATUS_RUNNING.toString(),
                    STATUS_PENDING.toString(),
                    STATUS_PAUSED_BY_APP.toString(),
                ),
                null,
            )
            val ids = mutableListOf<Long>()
            cursor?.use { while (it.moveToNext()) ids.add(it.getLong(0)) }
            if (ids.isNotEmpty()) dm?.remove(*ids.toLongArray())
        } catch (e: Exception) {
            module?.logError("$TAG: cancelAll failed: ${e.message}")
        }
    }

    // ── 暂停覆盖通知 ──────────────────────────────────────────────────────────

    private fun postPausedOverlay(
        context: Context,
        isAll: Boolean,
        pausedSnapshot: DownloadNotifSnapshot?,
    ) {
        if (!resumeNotificationEnabled) return
        val snap = pausedSnapshot ?: lastDownloadSnapshot ?: return
        if (lastDownloadSnapshot == null || !isSnapshotStillPaused(context, snap)) return
        val overlaySnap = snap.copy(
            notifId    = PAUSED_OVERLAY_ID,
            notifTag   = null,
            isMultiFile = isAll || snap.isMultiFile
        )
        repostAsPaused(context, overlaySnap)
    }

    private fun schedulePausedOverlay(
        context: Context,
        isAll: Boolean,
        snapshot: DownloadNotifSnapshot?,
    ) {
        for (delay in longArrayOf(300L, 1_200L)) {
            mainHandler.postDelayed(
                {
                    val resolved = snapshot ?: lastDownloadSnapshot ?: queryPausedSnapshot(context)
                    if (resolved != null) {
                        lastDownloadSnapshot = resolved
                        postPausedOverlay(context, isAll, resolved)
                    }
                },
                delay,
            )
        }
    }

    private fun isSnapshotStillPaused(context: Context, snapshot: DownloadNotifSnapshot): Boolean {
        if (snapshot.isMultiFile || !snapshot.downloadIdReliable) {
            return queryHasPausedDownload(context) != false
        }
        val state = queryDownloadState(context, snapshot.downloadId) ?: return true
        return state.status == STATUS_PAUSED_BY_APP && !state.deleted
    }

    fun restorePausedOverlayAfterNotificationClear(context: Context) {
        mainHandler.postDelayed(
            {
                if (!resumeNotificationEnabled) return@postDelayed
                val snapshot = lastDownloadSnapshot ?: queryPausedSnapshot(context) ?: return@postDelayed
                lastDownloadSnapshot = snapshot
                module?.log(
                    "$TAG: restoring paused overlay after DownloadNotifier.cancelAll " +
                        "downloadId=${snapshot.downloadId}"
                )
                postPausedOverlay(context, snapshot.isMultiFile, snapshot)
            },
            250L,
        )
    }

    private fun repostAsPaused(context: Context, snapshot: DownloadNotifSnapshot) {
        try {
            val extras = Bundle().apply {
                if (snapshot.downloadIdReliable) putLong("extra_download_id", snapshot.downloadId)
                putBoolean("extra_download_is_multi_file", snapshot.isMultiFile)
                putBoolean(EXTRA_PAUSED_OVERLAY, true)
            }
            val notif = Notification.Builder(context, snapshot.channelId)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .also { builder -> snapshot.taskIcon?.let(builder::setLargeIcon) }
                .addExtras(extras)
                .setProgress(
                    100,
                    snapshot.progress.coerceIn(0, 100),
                    snapshot.progress !in 0..100,
                )
                .setContentTitle(if (snapshot.isMultiFile) "${snapshot.fileName} 已暂停" else "已暂停")
                .setContentText(snapshot.fileName)
                .setOngoing(true)
                .setAutoCancel(false)
                .build()

            val resumeIntent = if (snapshot.isMultiFile) resumeAllIntent(context) else resumeIntent(context, snapshot.downloadId)
            val cancelIntent = if (snapshot.isMultiFile) cancelAllIntent(context) else cancelIntent(context, snapshot.downloadId)
            val resumeLabel = if (snapshot.isMultiFile) "全部恢复" else "恢复"
            val cancelLabel = if (snapshot.isMultiFile) "全部取消" else "取消"

            notif.actions = arrayOf(
                Notification.Action.Builder(
                    Icon.createWithResource(context, android.R.drawable.ic_media_play),
                    resumeLabel, resumeIntent
                ).build(),
                Notification.Action.Builder(
                    Icon.createWithResource(context, android.R.drawable.ic_delete),
                    cancelLabel, cancelIntent
                ).build()
            )

            val nm = context.getSystemService(NotificationManager::class.java)
            nm?.notify(null, snapshot.notifId, notif)
            module?.log(
                "$TAG: posted paused overlay id=${snapshot.notifId} " +
                    "channel=${snapshot.channelId} downloadId=${snapshot.downloadId}"
            )
        } catch (e: Exception) {
            module?.logError("$TAG: repostAsPaused failed: ${e.message}")
        }
    }

    private fun cancelPausedOverlay(context: Context) {
        try {
            context.getSystemService(NotificationManager::class.java)?.cancel(PAUSED_OVERLAY_ID)
        } catch (e: Exception) {
            module?.logError("$TAG: cancelPausedOverlay failed: ${e.message}")
        }
    }
}
