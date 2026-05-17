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
class XposedPrefsSyncApp : Application(), XposedServiceHelper.OnServiceListener {

    private val flutterPrefs: SharedPreferences by lazy {
        getSharedPreferences(REMOTE_PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Volatile
    private var xposedService: XposedService? = null

    private val flutterPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        syncKeyToRemote(prefs, key)
    }

    override fun onCreate() {
        super.onCreate()
        XposedServiceHelper.registerListener(this)
        flutterPrefs.registerOnSharedPreferenceChangeListener(flutterPrefsListener)
    }

    override fun onTerminate() {
        flutterPrefs.unregisterOnSharedPreferenceChangeListener(flutterPrefsListener)
        xposedService = null
        ServiceState.markNotReady()
        super.onTerminate()
    }

    // ── XposedService 回调 ────────────────────────────────────────────────────

    override fun onServiceBind(service: XposedService) {
        xposedService = service
        ServiceState.markReady(service.apiVersion, service.frameworkName, service.frameworkVersion)
        Log.d(
            TAG,
            "XposedService bound, API version: ${ServiceState.getApiVersion()}, " +
                "framework: ${ServiceState.getFrameworkName()} ${ServiceState.getFrameworkVersion()}, syncing all prefs"
        )
        syncAllToRemote(service)
        ServiceState.notifyReady()
    }

    override fun onServiceDied(service: XposedService) {
        xposedService = null
        ServiceState.markNotReady()
        Log.d(TAG, "XposedService died")
    }

    // ── 同步实现 ──────────────────────────────────────────────────────────────

    /** 单个 key 变更时，增量同步到 RemotePreferences。 */
    private fun syncKeyToRemote(prefs: SharedPreferences, key: String?) {
        val service = xposedService ?: return
        syncToRemote(service, prefs, key)
    }

    /** Service 刚绑定时，将所有 FlutterSharedPreferences 条目全量同步。 */
    private fun syncAllToRemote(service: XposedService) {
        syncToRemote(service, flutterPrefs, key = null)
    }

    private fun syncToRemote(service: XposedService, sourcePrefs: SharedPreferences, key: String?) {
        try {
            val remote = service.getRemotePreferences(REMOTE_PREFS_NAME)
            val editor = remote.edit() ?: return
            if (key == null) {
                writeAll(sourcePrefs, editor)
            } else {
                writeValue(editor, key, sourcePrefs.all[key])
            }
            editor.apply()
            if (key == null) {
                Log.d(TAG, "full sync done: ${sourcePrefs.all.size} keys")
            } else {
                Log.d(TAG, "synced key=$key to remote prefs")
            }
        } catch (e: Exception) {
            val scope = if (key == null) "all" else key
            Log.w(TAG, "syncToRemote failed (key=$scope): ${e.message}")
        }
    }

    fun requestScope(packages: List<String>) {
        val service = xposedService ?: throw IllegalStateException("XposedService is not ready")
        val currentScope = service.scope.toSet()
        val missingPackages = packages.filterNot { it in currentScope }
        if (missingPackages.isEmpty()) {
            Log.d(TAG, "scope already granted: $packages")
            return
        }

        service.requestScope(missingPackages, object : XposedService.OnScopeEventListener {
            override fun onScopeRequestApproved(scope: List<String>) {
                Log.d(TAG, "scope request approved: $scope")
            }

            override fun onScopeRequestFailed(message: String) {
                Log.w(TAG, "scope request failed: $message")
            }
        })
    }

    fun getCurrentScope(): List<String> {
        val service = xposedService ?: throw IllegalStateException("XposedService is not ready")
        return service.scope
    }

    fun getFrameworkInfo(): Map<String, Any> {
        val service = xposedService ?: throw IllegalStateException("XposedService is not ready")
        return mapOf(
            "apiVersion" to service.apiVersion,
            "frameworkName" to service.frameworkName,
            "frameworkVersion" to service.frameworkVersion,
            "frameworkVersionCode" to service.frameworkVersionCode,
            "scope" to service.scope
        )
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

        private object ServiceState {
            @Volatile private var serviceReady = false
            @Volatile private var apiVersion: Int = 0
            @Volatile private var frameworkName: String = ""
            @Volatile private var frameworkVersion: String = ""
            @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
            private val serviceReadyLock = Object()

            fun isReady(): Boolean = serviceReady

            fun getApiVersion(): Int = apiVersion

            fun getFrameworkName(): String = frameworkName

            fun getFrameworkVersion(): String = frameworkVersion

            fun markReady(newApiVersion: Int, newFrameworkName: String, newFrameworkVersion: String) {
                apiVersion = newApiVersion
                frameworkName = newFrameworkName
                frameworkVersion = newFrameworkVersion
                serviceReady = true
            }

            fun markNotReady() {
                serviceReady = false
                apiVersion = 0
                frameworkName = ""
                frameworkVersion = ""
            }

            fun notifyReady() {
                synchronized(serviceReadyLock) { serviceReadyLock.notifyAll() }
            }

            fun awaitReady(timeoutMs: Long): Boolean {
                if (isReady()) return true
                synchronized(serviceReadyLock) {
                    if (!isReady()) {
                        try {
                            serviceReadyLock.wait(timeoutMs)
                        } catch (_: InterruptedException) {
                        }
                    }
                }
                return isReady()
            }
        }

        fun isReady(): Boolean = ServiceState.isReady()

        fun getApiVersion(): Int = ServiceState.getApiVersion()

        fun getFrameworkName(): String = ServiceState.getFrameworkName()

        fun getFrameworkVersion(): String = ServiceState.getFrameworkVersion()

        fun awaitReady(timeoutMs: Long = 1500): Boolean = ServiceState.awaitReady(timeoutMs)
    }
}
