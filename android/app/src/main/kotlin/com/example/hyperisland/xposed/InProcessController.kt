package com.example.hyperisland.xposed

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
import com.example.hyperisland.xposed.templates.DownloadIslandNotification
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 进程内下载控制器。
 * 不硬编码类名，从 getSystemService 的运行时类直接反射。
 * pause/resume 完全复刻 MiuiDownloadManager 的 ContentProvider 逻辑。
 */
object InProcessController {

    private const val ACTION          = "com.example.hyperisland.INTERNAL_CTRL"
    private const val EXTRA_CMD       = "cmd"
    private const val EXTRA_ID        = "dlId"
    private const val EXTRA_NOTIF_ID  = "notifId"
    private const val EXTRA_NOTIF_TAG = "notifTag"

    const val CMD_PAUSE   = "pause"
    const val CMD_RESUME  = "resume"
    const val CMD_CANCEL  = "cancel"
    const val CMD_DISMISS = "dismiss"

    // 来自 JADX：MiuiDownloads.Impl 状态常量
    private const val STATUS_PENDING       = 190
    private const val STATUS_RUNNING       = 192
    private const val STATUS_PAUSED_BY_APP = 193
    private const val CONTROL_RUN          = 0
    private const val CONTROL_PAUSED       = 1

    // my_downloads 按调用者 UID 过滤，all_downloads 有全部记录（系统进程可用）
    private val DOWNLOADS_URI     = Uri.parse("content://downloads/my_downloads")
    private val DOWNLOADS_URI_ALL = Uri.parse("content://downloads/all_downloads")

    @Volatile private var registered = false

    /** 从 ContentProvider 加载的设置，在 ensureRegistered 时初始化 */
    @Volatile private var resumeNotificationEnabled = true
    /** 仅用于 DownloadHook：是否将下载管理器通知图标替换为应用图标。 */
    @Volatile var useHookAppIconEnabled = true

    /** 下载通知快照，供暂停后重建覆盖通知 */
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

    /** 最近一次快照 */
    @Volatile var lastDownloadSnapshot: DownloadNotifSnapshot? = null

    /** 固定 ID 的暂停覆盖通知，避免和 DM 原通知 ID 冲突 */
    private const val PAUSED_OVERLAY_ID = 0x48594F01

    // ── 初始化：注册进程内 Receiver ────────────────────────────────────────────

    /** 从 ContentProvider 加载所有设置，进程首次初始化时调用一次 */
    private fun loadSettings(context: Context) {
        resumeNotificationEnabled = readBooleanSetting(context, "pref_resume_notification", true)
        useHookAppIconEnabled     = readBooleanSetting(context, "pref_use_hook_app_icon", true)
        XposedBridge.log("HyperIsland: settings loaded — resumeNotification=$resumeNotificationEnabled useHookAppIcon=$useHookAppIconEnabled")
    }

    fun ensureRegistered(context: Context) {
        if (registered) return
        val appCtx = context.applicationContext ?: context

        // 读取设置（进程首次初始化时）
        loadSettings(appCtx)

        // 打印当前进程的 DownloadManager 实际类型，方便调试
        runCatching {
            val dm = appCtx.getSystemService(Context.DOWNLOAD_SERVICE)
            XposedBridge.log("HyperIsland: DownloadManager runtime class = ${dm?.javaClass?.name}")
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(EXTRA_ID, -1L)
                val cmd = intent.getStringExtra(EXTRA_CMD)
                XposedBridge.log("HyperIsland: onReceive cmd=$cmd id=$id")
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
                            XposedBridge.log("HyperIsland: dismiss notifId=$notifId tag=$notifTag")
                        }
                    }
                }
            }
        }

        val filter = IntentFilter(ACTION)
        // 必须用 EXPORTED：MIUI 超级岛按钮点击时，PendingIntent 由 system_server
        // 代发，UID ≠ 目标进程，RECEIVER_NOT_EXPORTED 会将广播过滤掉。
        if (Build.VERSION.SDK_INT >= 33) {
            appCtx.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            appCtx.registerReceiver(receiver, filter)
        }
        registered = true
        XposedBridge.log("HyperIsland: InProcessController registered in pid=${android.os.Process.myPid()}")
    }

    /**
     * 在 handleLoadPackage 阶段 Hook MiuiDownloadManager 的方法，
     * 确保在目标包进程里正确调用。
     * 仅在 com.xiaomi.android.app.downloadmanager 进程中尝试。
     */
    fun hookMiuiDownloadManager(lpparam: XC_LoadPackage.LoadPackageParam) {
        val candidates = listOf(
            "com.xiaomi.android.app.downloadmanager.MiuiDownloadManager",
            "com.android.providers.downloads.MiuiDownloadManager",
            "miui.app.MiuiDownloadManager"
        )
        for (className in candidates) {
            try {
                val clazz = lpparam.classLoader.loadClass(className)
                XposedBridge.log("HyperIsland: Found MiuiDownloadManager: $className")

                // Hook pauseDownload 验证方法存在
                XposedHelpers.findAndHookMethod(clazz, "pauseDownload", LongArray::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val ids = param.args[0] as? LongArray
                            XposedBridge.log("HyperIsland: pauseDownload called ids=${ids?.toList()}")
                        }
                    })
                XposedBridge.log("HyperIsland: Hooked pauseDownload in $className")
                break
            } catch (_: Throwable) {}
        }
    }

    // ── PendingIntent 工厂 ────────────────────────────────────────────────────

    fun pauseIntent(context: Context, downloadId: Long)  = makeIntent(context, CMD_PAUSE,  downloadId, reqCode(downloadId, 0))
    fun resumeIntent(context: Context, downloadId: Long) = makeIntent(context, CMD_RESUME, downloadId, reqCode(downloadId, 1))
    fun cancelIntent(context: Context, downloadId: Long) = makeIntent(context, CMD_CANCEL, downloadId, reqCode(downloadId, 2))

    /** 暂停/恢复/取消所有下载（id=-1 触发 xxxAll） */
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
        // 先尝试 MiuiDownloadManager 反射（传入真实 ID 和通过查询得到的真实 IDs）
        val realIds = queryActiveIds(context)
        XposedBridge.log("HyperIsland: pause notifId=$downloadId realIds=$realIds")

        // 用 DownloadManager 公开 API 无法 pause，但可以借助查到的真实 ID 更新 ContentProvider
        val idsToTry = (listOf(downloadId) + realIds).distinct()
        val values = ContentValues().apply {
            put("status",  STATUS_PAUSED_BY_APP)
            put("control", CONTROL_PAUSED)
        }
        for (id in idsToTry) {
            for (uri in listOf(DOWNLOADS_URI_ALL, DOWNLOADS_URI)) {
                try {
                    val rows = context.contentResolver.update(
                        uri, values, "_id = ?", arrayOf(id.toString())
                    )
                    XposedBridge.log("HyperIsland: pause id=$id uri=$uri rows=$rows")
                    if (rows > 0) return
                } catch (e: Exception) {
                    XposedBridge.log("HyperIsland: pause id=$id uri=$uri err=${e.message}")
                }
            }
        }
        // 最后降级：不限 ID，直接更新所有活跃下载
        pauseAll(context)
    }

    private fun resume(context: Context, downloadId: Long) {
        val realIds = queryPausedIds(context)
        XposedBridge.log("HyperIsland: resume notifId=$downloadId realIds=$realIds")

        val idsToTry = (listOf(downloadId) + realIds).distinct()
        val values = ContentValues().apply {
            put("status",  STATUS_RUNNING)
            put("control", CONTROL_RUN)
        }
        for (id in idsToTry) {
            for (uri in listOf(DOWNLOADS_URI_ALL, DOWNLOADS_URI)) {
                try {
                    val rows = context.contentResolver.update(
                        uri, values, "_id = ?", arrayOf(id.toString())
                    )
                    XposedBridge.log("HyperIsland: resume id=$id uri=$uri rows=$rows")
                    if (rows > 0) return
                } catch (e: Exception) {
                    XposedBridge.log("HyperIsland: resume id=$id uri=$uri err=${e.message}")
                }
            }
        }
        resumeAll(context)
    }

    /** 查询正在运行/等待的下载的真实 ID（和 cancelAll 同一策略）*/
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
            XposedBridge.log("HyperIsland: queryActiveIds err=${e.message}")
            emptyList()
        }
    }

    /** 查询已暂停的下载的真实 ID */
    private fun queryPausedIds(context: Context): List<Long> {
        return try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            val cursor = dm?.query(
                DownloadManager.Query().setFilterByStatus(DownloadManager.STATUS_PAUSED)
            )
            val ids = mutableListOf<Long>()
            cursor?.use {
                val col = it.getColumnIndex(DownloadManager.COLUMN_ID)
                while (it.moveToNext()) if (col >= 0) ids.add(it.getLong(col))
            }
            ids
        } catch (e: Exception) {
            XposedBridge.log("HyperIsland: queryPausedIds err=${e.message}")
            emptyList()
        }
    }

    private fun cancel(context: Context, downloadId: Long) {
        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            val n = dm?.remove(downloadId) ?: 0
            XposedBridge.log("HyperIsland: cancel dm.remove($downloadId)=$n")
            if (n == 0) cancelAll(context)  // ID 未匹配，降级取消所有活跃
        } catch (e: Exception) {
            XposedBridge.log("HyperIsland: cancel failed: ${e.message}")
            cancelAll(context)
        }
    }

    // ── id=-1 降级：操作所有匹配状态的下载 ───────────────────────────────────

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
                XposedBridge.log("HyperIsland: pauseAll uri=$uri rows=$rows")
                if (rows > 0) return
            } catch (e: Exception) {
                XposedBridge.log("HyperIsland: pauseAll uri=$uri err=${e.message}")
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
                DOWNLOADS_URI, values,
                "status = ?",
                arrayOf(STATUS_PAUSED_BY_APP.toString())
            )
            XposedBridge.log("HyperIsland: resumeAll rows=$rows")
        } catch (e: Exception) {
            XposedBridge.log("HyperIsland: resumeAll failed: ${e.message}")
        }
    }

    private fun cancelAll(context: Context) {
        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            // 只取消正在进行（RUNNING/PENDING）的下载，不碰已暂停的
            val cursor = context.contentResolver.query(
                DOWNLOADS_URI, arrayOf("_id"),
                "status = ? OR status = ?",
                arrayOf(STATUS_RUNNING.toString(), STATUS_PENDING.toString()),
                null
            )
            val ids = mutableListOf<Long>()
            cursor?.use { while (it.moveToNext()) ids.add(it.getLong(0)) }
            if (ids.isNotEmpty()) {
                val removed = dm?.remove(*ids.toLongArray()) ?: 0
                XposedBridge.log("HyperIsland: cancelAll removed=$removed ids=$ids")
            }
        } catch (e: Exception) {
            XposedBridge.log("HyperIsland: cancelAll failed: ${e.message}")
        }
    }

    // ── 暂停覆盖通知 ──────────────────────────────────────────────────────────

    private fun postPausedOverlay(context: Context, isAll: Boolean) {
        if (!resumeNotificationEnabled) {
            XposedBridge.log("HyperIsland: postPausedOverlay — disabled by setting")
            return
        }
        val snap = lastDownloadSnapshot ?: run {
            XposedBridge.log("HyperIsland: postPausedOverlay — no snapshot")
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
            val appIcon = getAppIcon(context, snapshot.packageName)
            DownloadIslandNotification.inject(
                context, extras, pausedTitle, snapshot.fileName,
                snapshot.progress, "", snapshot.fileName,
                snapshot.downloadId, snapshot.packageName,
                isPaused = true, appIcon = appIcon
            )
            extras.putBoolean("hyperisland_processed", true)

            val resumeIntent = if (snapshot.isMultiFile) resumeAllIntent(context)
                               else                      resumeIntent(context, snapshot.downloadId)
            val cancelIntent = if (snapshot.isMultiFile) cancelAllIntent(context)
                               else                      cancelIntent(context, snapshot.downloadId)
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
            XposedBridge.log("HyperIsland: repostAsPaused id=${snapshot.notifId}")
        } catch (e: Exception) {
            XposedBridge.log("HyperIsland: repostAsPaused failed: ${e.message}")
        }
    }

    private fun cancelPausedOverlay(context: Context) {
        try {
            context.getSystemService(NotificationManager::class.java)?.cancel(PAUSED_OVERLAY_ID)
            XposedBridge.log("HyperIsland: cancelPausedOverlay")
        } catch (e: Exception) {
            XposedBridge.log("HyperIsland: cancelPausedOverlay failed: ${e.message}")
        }
    }

    /**
     * 获取指定包名的应用启动图标，转换为 [Icon]。
     * 失败时返回 null，由调用方降级到默认图标。
     */
    fun getAppIcon(context: Context, packageName: String): android.graphics.drawable.Icon? {
        return try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val w = drawable.intrinsicWidth.coerceAtLeast(1)
            val h = drawable.intrinsicHeight.coerceAtLeast(1)
            val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, w, h)
            drawable.draw(canvas)
            android.graphics.drawable.Icon.createWithBitmap(bitmap)
        } catch (e: Exception) {
            XposedBridge.log("HyperIsland: getAppIcon($packageName) failed: ${e.message}")
            null
        }
    }

    /** 通过模块 ContentProvider 查询布尔设置，默认值 [default] */
    private fun readBooleanSetting(context: Context, key: String, default: Boolean): Boolean {
        return try {
            val uri = android.net.Uri.parse("content://com.example.hyperisland.settings/$key")
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use { if (it.moveToFirst()) it.getInt(0) != 0 else default } ?: default
        } catch (e: Exception) {
            XposedBridge.log("HyperIsland: readBooleanSetting($key) failed: ${e.message}")
            default
        }
    }

    /**
     * 用 getSystemService 的运行时类做反射，不猜类名。
     * pauseDownload/resumeDownload 签名都是 (long...) = LongArray
     */
    private fun callDmMethod(context: Context, methodName: String, downloadId: Long): Boolean {
        return try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) ?: return false
            val method = dm.javaClass.getMethod(methodName, LongArray::class.java)
            method.isAccessible = true
            method.invoke(dm, longArrayOf(downloadId))
            XposedBridge.log("HyperIsland: $methodName($downloadId) OK [${dm.javaClass.name}]")
            true
        } catch (e: Exception) {
            XposedBridge.log("HyperIsland: $methodName reflection failed: ${e.message}")
            false
        }
    }
}
