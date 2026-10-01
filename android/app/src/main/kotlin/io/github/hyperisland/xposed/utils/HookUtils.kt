package io.github.hyperisland.xposed.utils

import io.github.libxposed.api.XposedModule
import android.content.Context
import android.os.Process
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.logDebug
import java.util.WeakHashMap
import java.util.IdentityHashMap
import java.lang.ref.WeakReference

/**
 * 公共工具类，提供Context获取和类加载器Hook等通用功能
 */
object HookUtils {

    private class LoaderCallback(val callback: (ClassLoader) -> Unit) {
        val attempts = ClassLoaderAttemptGate()
    }
    private val dynamicClassLoaderCallbacks = mutableListOf<LoaderCallback>()
    private val readyLoaders = mutableListOf<WeakReference<ClassLoader>>()
    private var dynamicClassLoaderHooksInstalled = false
    private val dispatchedLoaders = ClassLoaderAttemptGate()
    private val constructorDepth = ThreadLocal<IdentityHashMap<ClassLoader, Int>>()
    private val diagnosticEvents = WeakHashMap<ClassLoader, MutableSet<String>>()
    private var diagnosticCount = 0
    private const val DIAGNOSTIC_TAG = "HyperIsland[LoaderDiscovery]"
    private var pluginContextObserverAttempted = false

    /** Missing classes do not consume an attempt: a later plugin-ready event may retry. */
    internal fun isIslandLoaderReady(loader: ClassLoader): Boolean = runCatching {
        val base = Class.forName(
            "miui.systemui.dynamicisland.window.content.DynamicIslandBaseContentView", false, loader,
        )
        val background = Class.forName(
            "miui.systemui.dynamicisland.DynamicIslandBackgroundView", false, loader,
        )
        // Resolve signatures too: finding a class does not prove its dependencies are available.
        base.declaredMethods
        background.declaredMethods
        true
    }.getOrDefault(false)

    fun discoverPluginLoader(module: XposedModule, loader: ClassLoader) {
        runCatching {
            if (!isIslandLoaderReady(loader)) return
            val callbacks = synchronized(dynamicClassLoaderCallbacks) {
                readyLoaders.removeAll { it.get() == null }
                if (readyLoaders.none { it.get() === loader } && readyLoaders.size < 256) {
                    readyLoaders.add(WeakReference(loader))
                }
                dynamicClassLoaderCallbacks.toList()
            }
            runCatching { traceLoader(module, loader, "island-ready") }
            callbacks.forEach { entry ->
                if (entry.attempts.enter(loader)) runCatching { entry.callback(loader) }
            }
        }
    }

    fun initializeLoaderDiagnostics(module: XposedModule, loader: ClassLoader) {
        synchronized(diagnosticEvents) {
            if (pluginContextObserverAttempted) return
            pluginContextObserverAttempted = true
        }
        runCatching { traceLoader(module, loader, "package-loaded") }
        observePluginContext(module, loader)
        discoverPluginLoader(module, loader)
    }

    private fun describe(loader: ClassLoader?): String = loader?.let {
        "${it.javaClass.name}@${Integer.toHexString(System.identityHashCode(it))}"
    } ?: "bootstrap"

    private fun traceLoader(module: XposedModule, loader: ClassLoader, event: String, detail: String = "") {
        // 前置短路：关闭调试日志时直接跳过下方昂贵的 Class.forName / toString 探测
        if (!ConfigManager.isDebugLogEnabled()) return
        synchronized(diagnosticEvents) {
            if (diagnosticCount >= 120 || diagnosticEvents.size >= 256) return
            val events = diagnosticEvents.getOrPut(loader) { mutableSetOf() }
            if (!events.add(event)) return
            diagnosticCount++
        }
        val probe = runCatching {
            val target = Class.forName("miui.systemui.dynamicisland.DynamicIslandBackgroundView", false, loader)
            "background=found defining=${describe(target.classLoader)}"
        }.getOrElse { "background=missing error=${it.javaClass.simpleName}" }
        val path = runCatching { loader.toString().take(1500) }.getOrDefault("unavailable")
        module.logDebug(DIAGNOSTIC_TAG) { "pid=${Process.myPid()} event=$event loader=${describe(loader)} parent=${describe(loader.parent)} " +
                "$probe $detail path=$path" }
    }

    /** Context creation is an additional readiness event, not a per-frame retry. */
    private fun observePluginContext(module: XposedModule, loader: ClassLoader) {
        runCatching {
            val factory = Class.forName(
                "com.android.systemui.shared.plugins.PluginInstance\$PluginFactory", false, loader,
            )
            val methods = factory.declaredMethods.filter { it.name == "createPluginContext" }
            methods.forEach { method ->
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    runCatching {
                        (result as? Context)?.let { context ->
                            if (context.packageName != "miui.systemui.plugin") return@let
                            runCatching {
                                traceLoader(module, context.classLoader, "plugin-context-ready", "package=${context.packageName}")
                            }
                            discoverPluginLoader(module, context.classLoader)
                        }
                    }
                    result
                }
            }
            module.logDebug(DIAGNOSTIC_TAG) { "pid=${Process.myPid()} plugin-context-observer methods=${methods.size}" }
        }.onFailure {
            module.logDebug(DIAGNOSTIC_TAG) { "pid=${Process.myPid()} plugin-context-observer unavailable=${it.javaClass.simpleName}" }
        }
    }

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
     * 在完整的类加载器构造结束后分发一次，不在每次 loadClass 时回调。
     */
    fun hookDynamicClassLoaders(
        module: XposedModule,
        classLoader: ClassLoader,
        onClassLoaded: (ClassLoader) -> Unit
    ) {
        val entry = LoaderCallback(onClassLoaded)
        val knownLoaders: List<ClassLoader>
        val installHooks: Boolean
        synchronized(dynamicClassLoaderCallbacks) {
            dynamicClassLoaderCallbacks.add(entry)
            knownLoaders = readyLoaders.mapNotNull { it.get() }
            installHooks = !dynamicClassLoaderHooksInstalled
            if (installHooks) dynamicClassLoaderHooksInstalled = true
        }
        // A plugin may have been discovered while the other Hook installers were registering.
        knownLoaders.forEach { loader ->
            if (entry.attempts.enter(loader)) runCatching { entry.callback(loader) }
        }
        if (!installHooks) return

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
                            val cl = chain.thisObject as? ClassLoader
                                ?: return@intercept chain.proceed()
                            val depths = constructorDepth.get()
                                ?: IdentityHashMap<ClassLoader, Int>().also { constructorDepth.set(it) }
                            val depth = depths[cl] ?: 0
                            depths[cl] = depth + 1
                            var completed = false
                            try {
                                val result = chain.proceed()
                                completed = true
                                result
                            } finally {
                                if (depth == 0) depths.remove(cl) else depths[cl] = depth
                                if (depths.isEmpty()) constructorDepth.remove()
                                // BaseDex/Path/DelegateLast constructors can nest for the same
                                // object. Only probe after the outermost constructor succeeds.
                                if (completed && depth == 0 && dispatchedLoaders.enter(cl)) {
                                    discoverPluginLoader(module, cl)
                                }
                            }
                        }
                    } catch (error: Exception) {
                        module.logDebug(DIAGNOSTIC_TAG) { "constructor-hook unavailable class=$clName error=${error.javaClass.simpleName}" }
                    }
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
