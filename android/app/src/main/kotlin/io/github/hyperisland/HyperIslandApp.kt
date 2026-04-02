package io.github.hyperisland

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

/**
 * 自定义 Application，负责将 Flutter 端写入的 SharedPreferences
 * 镜像同步到 LSPosed 的 RemotePreferences，使 Hook 进程能通过
 * [XposedModule.getRemotePreferences] 读到最新配置。
 *
 * 架构参考 example/App.kt + example/MainActivity.kt：
 *   - App 端通过 [XposedServiceHelper] 获取 [XposedService]
 *   - 写入用 [XposedService.getRemotePreferences].edit()
 *   - Hook 端用 module.getRemotePreferences() 读取
 */
class HyperIslandApp : Application(), XposedServiceHelper.OnServiceListener {

    private val flutterPrefs: SharedPreferences by lazy {
        getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
    }

    @Volatile private var xposedService: XposedService? = null
        set(value) { field = value; serviceReady = value != null }

    private val flutterPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        syncKeyToRemote(prefs, key)
    }

    override fun onCreate() {
        super.onCreate()
        XposedServiceHelper.registerListener(this)
        flutterPrefs.registerOnSharedPreferenceChangeListener(flutterPrefsListener)
    }

    // ── XposedService 回调 ────────────────────────────────────────────────────

    override fun onServiceBind(service: XposedService) {
        xposedService = service
        apiVersion = service.apiVersion
        Log.d(TAG, "XposedService bound, API version: $apiVersion, syncing all prefs")
        syncAllToRemote(service)
        synchronized(serviceReadyLock) { serviceReadyLock.notifyAll() }
    }

    override fun onServiceDied(service: XposedService) {
        xposedService = null
        Log.d(TAG, "XposedService died")
    }

    // ── 同步实现 ──────────────────────────────────────────────────────────────

    /** 单个 key 变更时，增量同步到 RemotePreferences。 */
    private fun syncKeyToRemote(prefs: SharedPreferences, key: String?) {
        val service = xposedService ?: return
        try {
            val remote = service.getRemotePreferences(REMOTE_PREFS_NAME)
            val editor = remote.edit() ?: return
            if (key == null) {
                writeAll(prefs, editor)
            } else {
                writeValue(editor, key, prefs.all[key])
            }
            editor.apply()
            Log.d(TAG, "synced key=$key to remote prefs")
        } catch (e: Exception) {
            Log.w(TAG, "syncKeyToRemote failed: ${e.message}")
        }
    }

    /** Service 刚绑定时，将所有 FlutterSharedPreferences 条目全量同步。 */
    private fun syncAllToRemote(service: XposedService) {
        try {
            val remote = service.getRemotePreferences(REMOTE_PREFS_NAME)
            val editor = remote.edit() ?: return
            writeAll(flutterPrefs, editor)
            editor.apply()
            Log.d(TAG, "full sync done: ${flutterPrefs.all.size} keys")
        } catch (e: Exception) {
            Log.w(TAG, "syncAllToRemote failed: ${e.message}")
        }
    }

    private fun writeAll(src: SharedPreferences, editor: SharedPreferences.Editor) {
        for ((key, value) in src.all) {
            writeValue(editor, key, value)
        }
    }

    private fun writeValue(editor: SharedPreferences.Editor, key: String, value: Any?) {
        when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is Int     -> editor.putInt(key, value)
            is Long    -> editor.putLong(key, value)
            is Float   -> editor.putFloat(key, value)
            is String  -> editor.putString(key, value)
            null       -> editor.remove(key)
        }
    }

    companion object {
        private const val TAG = "HyperIsland[App]"
        const val REMOTE_PREFS_NAME = "FlutterSharedPreferences"

        @Volatile private var serviceReady = false
        @Volatile private var apiVersion: Int = 0
        private val serviceReadyLock = Object()

        fun isReady(): Boolean = serviceReady

        fun getApiVersion(): Int = apiVersion

        fun awaitReady(timeoutMs: Long = 1500): Boolean {
            if (isReady()) return true
            synchronized(serviceReadyLock) {
                if (!isReady()) {
                    try { serviceReadyLock.wait(timeoutMs) }
                    catch (_: InterruptedException) { }
                }
            }
            return isReady()
        }
    }
}
