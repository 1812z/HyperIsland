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
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import io.github.hyperisland.getAppIcon
import io.github.hyperisland.xposed.templates.DownloadIslandNotification
import io.github.libxposed.api.XposedModule

/**
 * 进程内下载控制器。
 * 不硬编码类名，从 getSystemService 的运行时类直接反射。
 * pause/resume 完全复刻 MiuiDownloadManager 的 ContentProvider 逻辑。
 */
object InProcessController {

    private const val ACTION          = "io.github.hyperisland.INTERNAL_CTRL"
    private const val EXTRA_CMD       = "cmd"
    private const val EXTRA_ID        = "dlId"
    private const val EXTRA_NOTIF_ID  = "notifId"
    private const val EXTRA_NOTIF_TAG = "notifTag"

    const val CMD_PAUSE   = "pause"
    const val CMD_RESUME  = "resume"
    const val CMD_CANCEL  = "cancel"
    const val CMD_DISMISS = "dismiss"

    private const val STATUS_PENDING       = 190
    private const val STATUS_RUNNING       = 192
    private const val STATUS_PAUSED_BY_APP = 193
    private const val CONTROL_RUN          = 0
    private const val CONTROL_PAUSED       = 1

    private val DOWNLOADS_URI     = Uri.parse("content://downloads/my_downloads")
    private val DOWNLOADS_URI_ALL = Uri.parse("content://downloads/all_downloads")

    @Volatile private var registered = false
    @Volatile private var resumeNotificationEnabled = true
    @Volatile var useHookAppIconEnabled = true

    data class DownloadNotifSnapshot(
        val notifId: Int,
        val notifTag: String?,
        val channelId: String,
        val fileName: String,
        val progress: Int,
        val downloadId: Long,
        val isMultiFile: Boolean,
        val packageName: String
    )

    @Volatile var lastDownloadSnapshot: DownloadNotifSnapshot? = null
    private const val PAUSED_OVERLAY_ID = 0x48594F01

    private fun log(msg: String) = Log.d("HyperIsland", msg)

    private fun loadSettings() {
        log("HyperIsland: loadSettings: starting...")
        ConfigManager.debugDump()
        
        resumeNotificationEnabled = ConfigManager.getBoolean("pref_resume_notification", true)
        log("HyperIsland: loadSettings: pref_resume_notification=$resumeNotificationEnabled")
        
        useHookAppIconEnabled = ConfigManager.getBoolean("pref_use_hook_app_icon", true)
        log("HyperIsland: loadSettings: pref_use_hook_app_icon=$useHookAppIconEnabled")
        
        log("HyperIsland: settings loaded — resumeNotification=$resumeNotificationEnabled useHookAppIcon=$useHookAppIconEnabled")
    }

    fun ensureRegistered(context: Context, module: XposedModule) {
        log("HyperIsland: ensureRegistered called, registered=$registered, pid=${android.os.Process.myPid()}")
        if (registered) return
        val appCtx = context.applicationContext ?: context

        log("HyperIsland: calling ConfigManager.init with context=$appCtx...")
        ConfigManager.init(module)
        
        log("HyperIsland: calling loadSettings...")
        loadSettings()
        
        log("HyperIsland: adding change listener...")
        ConfigManager.addChangeListener {
            loadSettings()
            log("HyperIsland: settings reloaded via Observer")
        }

        runCatching {
            val dm = appCtx.getSystemService(Context.DOWNLOAD_SERVICE)
            log("HyperIsland: DownloadManager runtime class = ${dm?.javaClass?.name}")
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(EXTRA_ID, -1L)
                val cmd = intent.getStringExtra(EXTRA_CMD)
                log("HyperIsland: onReceive cmd=$cmd id=$id")
                when (cmd) {
                    CMD_PAUSE -> {
                        val isAll = id <= 0
                        if (isAll) pauseAll(appCtx) else pause(appCtx, id)
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            postPausedOverlay(appCtx, isAll)
                        }, 300)
                    }
                    CMD_RESUME -> {
                        if (id > 0) resume(appCtx, id) else resumeAll(appCtx)
                        cancelPausedOverlay(appCtx)
                    }
                    CMD_CANCEL -> {
                        if (id > 0) cancel(appCtx, id) else cancelAll(appCtx)
                        cancelPausedOverlay(appCtx)
                    }
                    CMD_DISMISS -> {
                        val notifId  = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
                        val notifTag = intent.getStringExtra(EXTRA_NOTIF_TAG)
                        if (notifId > 0) {
                            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                            nm?.cancel(notifTag, notifId)
                            log("HyperIsland: dismiss notifId=$notifId tag=$notifTag")
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
        registered = true
        log("HyperIsland: InProcessController registered in pid=${android.os.Process.myPid()}")
    }

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
                module.log("HyperIsland: Found MiuiDownloadManager: $className")

                val pauseMethod = clazz.getDeclaredMethod("pauseDownload", LongArray::class.java)
                module.hook(pauseMethod).intercept { chain ->
                    val ids = chain.args[0] as? LongArray
                    module.log("HyperIsland: pauseDownload called ids=${ids?.toList()}")
                    chain.proceed()
                }
                module.log("HyperIsland: Hooked pauseDownload in $className")
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
        log("HyperIsland: pause notifId=$downloadId realIds=$realIds")
        val idsToTry = (listOf(downloadId) + realIds).distinct()
        val values = ContentValues().apply {
            put("status",  STATUS_PAUSED_BY_APP)
            put("control", CONTROL_PAUSED)
        }
        for (id in idsToTry) {
            for (uri in listOf(DOWNLOADS_URI_ALL, DOWNLOADS_URI)) {
                try {
                    val rows = context.contentResolver.update(uri, values, "_id = ?", arrayOf(id.toString()))
                    log("HyperIsland: pause id=$id uri=$uri rows=$rows")
                    if (rows > 0) return
                } catch (e: Exception) {
                    log("HyperIsland: pause id=$id uri=$uri err=${e.message}")
                }
            }
        }
        pauseAll(context)
    }

    private fun resume(context: Context, downloadId: Long) {
        val realIds = queryPausedIds(context)
        log("HyperIsland: resume notifId=$downloadId realIds=$realIds")
        val idsToTry = (listOf(downloadId) + realIds).distinct()
        val values = ContentValues().apply {
            put("status",  STATUS_RUNNING)
            put("control", CONTROL_RUN)
        }
        for (id in idsToTry) {
            for (uri in listOf(DOWNLOADS_URI_ALL, DOWNLOADS_URI)) {
                try {
                    val rows = context.contentResolver.update(uri, values, "_id = ?", arrayOf(id.toString()))
                    log("HyperIsland: resume id=$id uri=$uri rows=$rows")
                    if (rows > 0) return
                } catch (e: Exception) {
                    log("HyperIsland: resume id=$id uri=$uri err=${e.message}")
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
            log("HyperIsland: queryActiveIds err=${e.message}")
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
            log("HyperIsland: queryPausedIds err=${e.message}")
            emptyList()
        }
    }

    private fun cancel(context: Context, downloadId: Long) {
        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            val n = dm?.remove(downloadId) ?: 0
            log("HyperIsland: cancel dm.remove($downloadId)=$n")
            if (n == 0) cancelAll(context)
        } catch (e: Exception) {
            log("HyperIsland: cancel failed: ${e.message}")
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
                log("HyperIsland: pauseAll uri=$uri rows=$rows")
                if (rows > 0) return
            } catch (e: Exception) {
                log("HyperIsland: pauseAll uri=$uri err=${e.message}")
            }
        }
    }

    private fun resumeAll(context: Context) {
        try {
            val values = ContentValues().apply {
                put("status",  STATUS_RUNNING)
                put("control", CONTROL_RUN)
            }
            val rows = context.contentResolver.update(
                DOWNLOADS_URI, values, "status = ?", arrayOf(STATUS_PAUSED_BY_APP.toString())
            )
            log("HyperIsland: resumeAll rows=$rows")
        } catch (e: Exception) {
            log("HyperIsland: resumeAll failed: ${e.message}")
        }
    }

    private fun cancelAll(context: Context) {
        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            val cursor = context.contentResolver.query(
                DOWNLOADS_URI, arrayOf("_id"),
                "status = ? OR status = ?",
                arrayOf(STATUS_RUNNING.toString(), STATUS_PENDING.toString()), null
            )
            val ids = mutableListOf<Long>()
            cursor?.use { while (it.moveToNext()) ids.add(it.getLong(0)) }
            if (ids.isNotEmpty()) {
                val removed = dm?.remove(*ids.toLongArray()) ?: 0
                log("HyperIsland: cancelAll removed=$removed ids=$ids")
            }
        } catch (e: Exception) {
            log("HyperIsland: cancelAll failed: ${e.message}")
        }
    }

    // ── 暂停覆盖通知 ──────────────────────────────────────────────────────────

    private fun postPausedOverlay(context: Context, isAll: Boolean) {
        if (!resumeNotificationEnabled) {
            log("HyperIsland: postPausedOverlay — disabled by setting")
            return
        }
        val snap = lastDownloadSnapshot ?: run {
            log("HyperIsland: postPausedOverlay — no snapshot")
            return
        }
        val overlaySnap = snap.copy(
            notifId    = PAUSED_OVERLAY_ID,
            notifTag   = null,
            isMultiFile = isAll || snap.isMultiFile
        )
        repostAsPaused(context, overlaySnap)
    }

    private fun repostAsPaused(context: Context, snapshot: DownloadNotifSnapshot) {
        try {
            val extrasField = DownloadHook.extrasField
            val builder = Notification.Builder(context, snapshot.channelId)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(if (snapshot.isMultiFile) "${snapshot.fileName} 已暂停" else "已暂停")
                .setContentText(snapshot.fileName)
                .setOngoing(false)
                .setAutoCancel(false)
            val notif = builder.build()
            val extras = extrasField?.get(notif) as? Bundle ?: return

            val pausedTitle = if (snapshot.isMultiFile) "${snapshot.fileName} 已暂停" else "已暂停"
            val appIcon = context.packageManager.getAppIcon(snapshot.packageName)
            DownloadIslandNotification.inject(
                context, extras, pausedTitle, snapshot.fileName,
                snapshot.progress, "", snapshot.fileName,
                snapshot.downloadId, snapshot.packageName,
                isPaused = true, appIcon = appIcon
            )
            extras.putBoolean("hyperisland_processed", true)

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
            log("HyperIsland: repostAsPaused id=${snapshot.notifId}")
        } catch (e: Exception) {
            log("HyperIsland: repostAsPaused failed: ${e.message}")
        }
    }

    private fun cancelPausedOverlay(context: Context) {
        try {
            context.getSystemService(NotificationManager::class.java)?.cancel(PAUSED_OVERLAY_ID)
            log("HyperIsland: cancelPausedOverlay")
        } catch (e: Exception) {
            log("HyperIsland: cancelPausedOverlay failed: ${e.message}")
        }
    }

    @Suppress("unused")
    private fun callDmMethod(context: Context, methodName: String, downloadId: Long): Boolean {
        return try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) ?: return false
            val method = dm.javaClass.getMethod(methodName, LongArray::class.java)
            method.isAccessible = true
            method.invoke(dm, longArrayOf(downloadId))
            log("HyperIsland: $methodName($downloadId) OK [${dm.javaClass.name}]")
            true
        } catch (e: Exception) {
            log("HyperIsland: $methodName reflection failed: ${e.message}")
            false
        }
    }
}
