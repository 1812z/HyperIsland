package io.github.hyperisland.xposed.hook

import android.util.Log
import io.github.hyperisland.xposed.ConfigManager
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * Hook基类，提供统一的配置管理和生命周期
 */
abstract class BaseHook {

    private val configChangeListener: () -> Unit = {
        onConfigChanged()
    }

    private var configListenerRegistered = false

    /**
     * 获取Hook的标签，用于日志记录
     */
    abstract fun getTag(): String

    /**
     * Hook初始化方法
     */
    abstract fun onInit(module: XposedModule, param: PackageLoadedParam)

    /**
     * 配置变化回调
     */
    open fun onConfigChanged() {}

    /**
     * 确保ConfigManager已初始化并注册监听器
     */
    protected fun ensureConfigManager(module: XposedModule) {
        ConfigManager.init(module)
        if (!configListenerRegistered) {
            ConfigManager.addChangeListener(configChangeListener)
            configListenerRegistered = true
        }
    }

    /**
     * 获取日志实例
     */
    protected fun log(module: XposedModule, message: String) {
        if (ConfigManager.isDebugLogEnabled())
            module.log(Log.DEBUG, getTag(), message)
    }

    /**
     * 获取警告日志实例
     */
    protected fun logWarn(module: XposedModule, message: String) {
        module.log(Log.WARN, getTag(), message)
    }

    /**
     * 获取错误日志实例
     */
    protected fun logError(module: XposedModule, message: String) {
        module.log(Log.ERROR, getTag(), message)
    }

    /**
     * Hook入口点，确保配置管理器初始化后调用onInit
     */
    fun init(module: XposedModule, param: PackageLoadedParam) {
        ensureConfigManager(module)
        log(module, "initializing for ${param.packageName}")
        try {
            onInit(module, param)
        } catch (e: Throwable) {
            logError(module, "init failed: ${e.message}")
        }
    }
}
