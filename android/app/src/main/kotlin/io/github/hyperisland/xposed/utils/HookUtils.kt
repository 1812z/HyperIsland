package io.github.hyperisland.xposed.utils

import io.github.libxposed.api.XposedModule

/**
 * 公共工具类，提供Context获取和类加载器Hook等通用功能
 */
object HookUtils {

    private val dynamicClassLoaderCallbacks = mutableListOf<(ClassLoader) -> Unit>()
    private var dynamicClassLoaderHooksInstalled = false

    /**
     * 从类加载器获取Context
     */
    fun getContext(classLoader: ClassLoader): android.content.Context? {
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

    /**
     * Hook所有类加载器，在动态加载类时触发回调
     */
    fun hookDynamicClassLoaders(
        module: XposedModule,
        classLoader: ClassLoader,
        onClassLoaded: (ClassLoader) -> Unit
    ) {
        synchronized(dynamicClassLoaderCallbacks) {
            dynamicClassLoaderCallbacks.add(onClassLoaded)
            if (dynamicClassLoaderHooksInstalled) return
            dynamicClassLoaderHooksInstalled = true
        }

        val classLoaders = arrayOf(
            "dalvik.system.BaseDexClassLoader",
            "dalvik.system.PathClassLoader",
            "dalvik.system.DexClassLoader",
            "dalvik.system.DelegateLastClassLoader"
        )
        for (clName in classLoaders) {
            try {
                val clazz = Class.forName(clName, false, classLoader)
                for (ctor in clazz.declaredConstructors) {
                    try {
                        module.hook(ctor).intercept { chain ->
                            val result = chain.proceed()
                            val cl = chain.thisObject as? ClassLoader
                            if (cl != null) {
                                val callbacks = synchronized(dynamicClassLoaderCallbacks) {
                                    dynamicClassLoaderCallbacks.toList()
                                }
                                callbacks.forEach { callback -> runCatching { callback(cl) } }
                            }
                            result
                        }
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * 在类及其父类层次结构中查找方法
     */
    fun findMethod(clazz: Class<*>, name: String, vararg paramTypes: Class<*>): java.lang.reflect.Method {
        var c: Class<*>? = clazz
        while (c != null) {
            try { return c.getDeclaredMethod(name, *paramTypes) } catch (_: NoSuchMethodException) {}
            c = c.superclass
        }
        throw NoSuchMethodException("$name not found in ${clazz.name} hierarchy")
    }
}
