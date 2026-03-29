package io.github.hyperisland.xposed

import android.content.SharedPreferences
import android.util.Log
import io.github.libxposed.api.XposedModule

/**
 * API 101 的 [XposedModule.log] 需要 (priority, tag, message) 三参数。
 * 此扩展函数提供单参简写，统一使用 DEBUG 级别和 "HyperIsland" 标签。
 */
fun XposedModule.log(message: String) =
    log(Log.DEBUG, "HyperIsland", message)

/**
 * 基于 XposedService.getRemotePreferences 的配置管理器（API 101 版本）。
 *
 * 架构：
 *   - Flutter 的 shared_preferences 插件将全量配置以 "flutter." 前缀写入模块 App 进程的
 *     FlutterSharedPreferences.xml。
 *   - Hook 进程（SystemUI / XMSF / 下载管理器）通过 XposedService.getRemotePreferences()
 *     跨进程读取该文件，并注册 OnSharedPreferenceChangeListener 实现热重载。
 *
 * 好处：
 *   - 直接使用 API 101 原生远程 Prefs
 *   - Prefs 变更时回调自动触发
 *   - 与 Flutter 端配置修改行为完全兼容
 */
object ConfigManager {

    private const val FLUTTER_KEY_PREFIX = "flutter."
    private const val PREFS_GROUP = "FlutterSharedPreferences"

    @Volatile private var prefs: SharedPreferences? = null
    @Volatile private var initialized = false

    private val changeListeners = mutableListOf<() -> Unit>()

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        Log.d("HyperIsland[ConfigManager]", "prefs changed: key=$key")
        notifyListeners()
    }

    /**
     * 初始化：直接通过 [XposedModule.getRemotePreferences] 同步获取远程 SharedPreferences。
     * 幂等，多次调用只执行一次。
     * 
     * 注意：在 API 101 中，getRemotePreferences 在被 hook 的应用中是只读的。
     */
    @Synchronized
    fun init(module: XposedModule) {
        if (initialized) {
            Log.d("HyperIsland[ConfigManager]", "init: already initialized, skip")
            return
        }

        Log.d("HyperIsland[ConfigManager]", "init: starting, pid=${android.os.Process.myPid()}")
        Log.d("HyperIsland[ConfigManager]", "init: module class=${module.javaClass.name}")
        Log.d("HyperIsland[ConfigManager]", "init: trying to get remote prefs '$PREFS_GROUP'...")
        
        try {
            val p = module.getRemotePreferences(PREFS_GROUP)
            Log.d("HyperIsland[ConfigManager]", "init: getRemotePreferences returned successfully, prefs=$p")
            
            // 打印所有 key 以便调试
            try {
                val allKeys = p.all
                Log.d("HyperIsland[ConfigManager]", "init: prefs has ${allKeys.size} keys: ${allKeys.keys}")
                
                // 打印每个 key 的值
                allKeys.forEach { (k, v) ->
                    Log.d("HyperIsland[ConfigManager]", "init:   - $k = $v (${v?.javaClass?.simpleName})")
                }
                
                // 检查目标 key 是否存在
                val targetKey = "$FLUTTER_KEY_PREFIX$USE_HOOK_APP_ICON_KEY"
                val containsTarget = p.contains(targetKey)
                val targetValue = if (containsTarget) p.getBoolean(targetKey, true) else "NOT_FOUND"
                Log.d("HyperIsland[ConfigManager]", "init: target key '$targetKey' exists=$containsTarget, value=$targetValue")
            } catch (e: Exception) {
                Log.e("HyperIsland[ConfigManager]", "init: failed to read prefs: ${e.message}", e)
            }
            
            p.registerOnSharedPreferenceChangeListener(prefsListener)
            prefs = p
            initialized = true
            module.log("HyperIsland[ConfigManager]: remote prefs '$PREFS_GROUP' loaded")
            notifyListeners()
        } catch (e: UnsupportedOperationException) {
            Log.e("HyperIsland[ConfigManager]", "init: UnsupportedOperationException - framework may be embedded", e)
            // 框架是嵌入式的，无法使用远程 prefs
            initialized = true
        } catch (e: Exception) {
            Log.e("HyperIsland[ConfigManager]", "init failed: ${e.message}", e)
            // 即使失败也标记为已初始化，避免重复尝试
            initialized = true
        }
    }
    
    private const val USE_HOOK_APP_ICON_KEY = "pref_use_hook_app_icon"

    /** 注册配置变化回调，Prefs 每次变更后触发（调用方负责只注册一次）。 */
    @Synchronized
    fun addChangeListener(listener: () -> Unit) {
        changeListeners += listener
    }

    // ── 类型化读取 ──────────────────────────────────────────────────────────────

    fun getBoolean(key: String, default: Boolean): Boolean {
        val fullKey = fk(key)
        Log.d("HyperIsland[ConfigManager]", "getBoolean: key='$key', fullKey='$fullKey', default=$default")
        
        if (prefs == null) {
            Log.w("HyperIsland[ConfigManager]", "getBoolean: prefs is null, returning default=$default")
            return default
        }
        
        val contains = prefs!!.contains(fullKey)
        Log.d("HyperIsland[ConfigManager]", "getBoolean: prefs contains '$fullKey'=$contains")
        
        if (!contains) {
            Log.d("HyperIsland[ConfigManager]", "getBoolean: key not found, returning default=$default")
            return default
        }
        
        return try {
            val value = prefs!!.getBoolean(fullKey, default)
            Log.d("HyperIsland[ConfigManager]", "getBoolean: '$fullKey'=$value")
            value
        } catch (e: ClassCastException) {
            Log.e("HyperIsland[ConfigManager]", "getBoolean: ClassCastException for '$fullKey', returning default=$default", e)
            default
        }
    }

    fun getString(key: String, default: String = ""): String =
        try { prefs?.getString(fk(key), default) ?: default }
        catch (_: ClassCastException) { default }

    /**
     * Flutter 的 int 在 Android SharedPreferences 中以 Long 存储，
     * 优先用 getLong 读取再转换，若类型不符再尝试 getInt。
     */
    fun getInt(key: String, default: Int): Int =
        try { prefs?.getLong(fk(key), default.toLong())?.toInt() ?: default }
        catch (_: ClassCastException) {
            try { prefs?.getInt(fk(key), default) ?: default }
            catch (_: ClassCastException) { default }
        }

    fun contains(key: String): Boolean =
        prefs?.contains(fk(key)) ?: false

    /**
     * 获取所有配置项（用于调试）
     */
    fun getAll(): Map<String, *> {
        val allPrefs = prefs?.all ?: emptyMap<String, Any?>()
        Log.d("HyperIsland[ConfigManager]", "getAll: ${allPrefs.size} keys")
        allPrefs.forEach { (k, v) ->
            Log.d("HyperIsland[ConfigManager]", "  - $k = $v (${v?.javaClass?.simpleName})")
        }
        return allPrefs
    }

    /**
     * 强制刷新并打印当前状态（用于调试）
     */
    fun debugDump() {
        Log.d("HyperIsland[ConfigManager]", "=== ConfigManager Debug Dump ===")
        Log.d("HyperIsland[ConfigManager]", "initialized=$initialized")
        Log.d("HyperIsland[ConfigManager]", "prefs=$prefs")
        Log.d("HyperIsland[ConfigManager]", "listenerCount=${changeListeners.size}")
        getAll()
        Log.d("HyperIsland[ConfigManager]", "=== End Debug Dump ===")
    }

    // ── 内部实现 ────────────────────────────────────────────────────────────────

    private fun fk(key: String) = "$FLUTTER_KEY_PREFIX$key"

    private fun notifyListeners() {
        val ls = synchronized(this) { changeListeners.toList() }
        ls.forEach { runCatching { it() } }
    }
}
