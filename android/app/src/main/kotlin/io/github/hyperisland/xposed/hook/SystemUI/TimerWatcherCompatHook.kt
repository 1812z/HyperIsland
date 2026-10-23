package io.github.hyperisland.xposed.hook

import android.os.Handler
import android.os.Looper
import android.text.Editable
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap

/** OS3 等宽计时器不能在 TextView 分发监听器时同步重建岛。 */
object TimerWatcherCompatHook : BaseHook() {
    private const val HOLDER_CLASS =
        "miui.systemui.dynamicisland.module.IslandSameWidthDigitViewHolder"
    private const val MAX_PENDING = 128
    private val mainHandler = Handler(Looper.getMainLooper())
    private val hookedClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    )
    private val pending = WeakHashMap<Any, Runnable>()

    override fun getTag() = "HyperIsland[TimerWatcherCompat]"

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        hookClasses(module, param.defaultClassLoader)
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) {
            hookClasses(module, it)
        }
    }

    private fun hookClasses(module: XposedModule, loader: ClassLoader) {
        runCatching {
            val holderClass = loader.loadClass(HOLDER_CLASS)
            val watcherField = holderClass.getDeclaredField("textWatcher").apply {
                isAccessible = true
            }
            // 探测 OS3 的具体 watcher 结构，不按 Android SDK 号区分 OS3/OS4。
            val watcherClass = watcherField.type
            val holderField = watcherClass.getDeclaredField("this\$0").apply {
                isAccessible = true
            }
            val initializedField = holderClass.getDeclaredField("timerInitialized").apply {
                isAccessible = true
            }
            val lengthField = holderClass.getDeclaredField("lastLength").apply {
                isAccessible = true
            }
            val dataField = holderClass.getDeclaredField("lastData").apply {
                isAccessible = true
            }
            val getEmitEvent = holderClass.methods.first {
                it.name == "getEmitEvent" && it.parameterTypes.isEmpty()
            }
            val invokeEvent = getEmitEvent.returnType.getMethod(
                "invoke", Any::class.java, Any::class.java
            )
            val event = loader.loadClass(
                "miui.systemui.dynamicisland.event.DynamicIslandEvent\$UpdateDynamicIsland"
            ).getField("INSTANCE").get(null)
            val afterChanged = watcherClass.getDeclaredMethod("afterTextChanged", Editable::class.java)
            val onDetach = holderClass.getDeclaredMethod("onDetach")
            if (!hookedClasses.add(watcherClass)) return

            module.hook(afterChanged).intercept { chain ->
                val watcher = chain.thisObject ?: return@intercept chain.proceed()
                val holder = holderField.get(watcher) ?: return@intercept chain.proceed()
                val length = (chain.args.firstOrNull() as? Editable)?.length ?: 0
                if (!initializedField.getBoolean(holder) || lengthField.getInt(holder) == length) {
                    return@intercept chain.proceed()
                }
                // 原回调在此分支仅 emit UpdateDynamicIsland。先退出 sendAfterTextChanged，
                // 再读取 holder 的最新数据发事件；不持有 Editable、通知数据或 holder 强引用。
                synchronized(pending) {
                    if (!pending.containsKey(holder)) {
                        if (pending.size >= MAX_PENDING) {
                            val iterator = pending.entries.iterator()
                            val entry = iterator.next()
                            mainHandler.removeCallbacks(entry.value)
                            iterator.remove()
                        }
                        val holderRef = WeakReference(holder)
                        val task = Runnable {
                            val activeHolder = holderRef.get() ?: return@Runnable
                            synchronized(pending) { pending.remove(activeHolder) }
                            runCatching {
                                val emit = getEmitEvent.invoke(activeHolder) ?: return@runCatching
                                invokeEvent.invoke(emit, dataField.get(activeHolder), event)
                            }.onFailure {
                                logFailureOnce(module, "dispatch:${watcherClass.name}") {
                                    "deferred timer update failed: $it"
                                }
                            }
                        }
                        pending[holder] = task
                        mainHandler.post(task)
                    }
                }
                null
            }
            module.hook(onDetach).intercept { chain ->
                synchronized(pending) { pending.remove(chain.thisObject) }
                    ?.let(mainHandler::removeCallbacks)
                chain.proceed()
            }
            log(module) { "deferred ${watcherClass.name}#afterTextChanged island updates" }
        }.onFailure {
            if (it !is ClassNotFoundException) {
                logFailureOnce(module, "install:${it.javaClass.name}") {
                    "timer watcher compatibility unavailable: $it"
                }
            }
        }
    }
}
