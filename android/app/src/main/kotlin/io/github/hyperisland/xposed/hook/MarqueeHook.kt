package io.github.hyperisland.xposed.hook

import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.log
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModule
import java.util.WeakHashMap

/**
 * Hook SystemUI 中超级岛大岛视图的 TextView，实现自定义跑马灯（文字横向滚动）效果。
 */
object MarqueeHook {

    private const val TAG = "HyperIsland[MarqueeHook]"

    private val hookedFactories = mutableSetOf<String>()
    private val scrollerMap = WeakHashMap<TextView, MarqueeController>()
    private val observedViews = WeakHashMap<TextView, Boolean>()

    @Volatile var pendingMarqueeEnabled: Boolean = false
    @Volatile private var cachedSpeed: Int? = null
    @Volatile private var observerRegistered = false

    fun ensureObserver(context: android.content.Context, module: XposedModule) {
        if (observerRegistered) return
        ConfigManager.init(module)
        ConfigManager.addChangeListener {
            cachedSpeed = null
            stopAllMarquees()
            module.log("$TAG: settings changed via Observer, cache cleared")
        }
        observerRegistered = true
        module.log("$TAG: ConfigManager Observer registered in SystemUI")
    }

    private fun getMarqueeSpeed(): Int {
        cachedSpeed?.let { return it }
        return ConfigManager.getInt("pref_marquee_speed", 100).coerceIn(20, 500)
            .also { cachedSpeed = it }
    }

    private fun stopAllMarquees() {
        scrollerMap.values.forEach { it.stop() }
        scrollerMap.clear()
    }

    fun startMarquee(textView: TextView) {
        val fullText = textView.text?.toString() ?: ""
        if (fullText.isEmpty()) {
            stopMarquee(textView)
            return
        }
        val measuredW = textView.paint.measureText(fullText)
        val visibleW = resolveVisibleWidth(textView)
        val availableW = visibleW - textView.paddingLeft - textView.paddingRight
        val needMarquee = measuredW > availableW

        if (needMarquee && visibleW > 0) {
            val speed = getMarqueeSpeed()
            val controller = scrollerMap.getOrPut(textView) { MarqueeController(textView, speed) }
            controller.speedPxPerSec = speed
            controller.start()
        } else {
            stopMarquee(textView)
        }
    }

    fun stopMarquee(textView: TextView) {
        scrollerMap.remove(textView)?.stop()
        textView.scrollTo(0, 0)
    }

    private fun resolveVisibleWidth(view: View): Int {
        var visibleW = if (view.width > 0) view.width else Int.MAX_VALUE
        var p = view.parent
        while (p is ViewGroup) {
            if (p.width > 0 && p.width < visibleW) visibleW = p.width
            p = p.parent
        }
        return if (visibleW == Int.MAX_VALUE) 0 else visibleW
    }

    fun traverseAndApplyMarquee(view: View) {
        if (view is TextView) {
            if (observedViews.containsKey(view)) return
            observedViews[view] = true

            view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                startMarquee(v as TextView)
            }
            view.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) { startMarquee(view) }
            })
            startMarquee(view)
        } else if (view is ViewGroup) {
            view.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
                override fun onChildViewAdded(parent: View?, child: View?) {
                    child?.let { traverseAndApplyMarquee(it) }
                }
                override fun onChildViewRemoved(parent: View?, child: View?) {
                    if (child is TextView) stopMarquee(child)
                }
            })
            for (i in 0 until view.childCount) {
                traverseAndApplyMarquee(view.getChildAt(i))
            }
        }
    }

    // ─── IXposedHookLoadPackage → init ────────────────────────────────────────

    fun init(module: XposedModule, param: PackageLoadedParam) {
        module.log("$TAG: initializing for ${param.packageName}")
        try {
            val factoryClass = param.defaultClassLoader
                .loadClass("miui.systemui.dynamicisland.template.IslandTemplateFactory")
            doHookFactory(module, factoryClass)
        } catch (_: ClassNotFoundException) {
            hookDynamicClassLoaders(module, param)
        } catch (e: Exception) {
            module.log("$TAG: failed to inject: ${e.message}")
        }
    }

    private fun hookDynamicClassLoaders(module: XposedModule, param: PackageLoadedParam) {
        val classLoaders = arrayOf(
            "dalvik.system.BaseDexClassLoader",
            "dalvik.system.PathClassLoader",
            "dalvik.system.DexClassLoader",
            "dalvik.system.DelegateLastClassLoader"
        )
        for (clName in classLoaders) {
            try {
                val clazz = Class.forName(clName)
                for (ctor in clazz.declaredConstructors) {
                    try {
                        module.hook(ctor).intercept { chain ->
                            val result = chain.proceed()
                            val cl = chain.thisObject as? ClassLoader
                            if (cl != null) {
                                try {
                                    val factoryClass = cl.loadClass(
                                        "miui.systemui.dynamicisland.template.IslandTemplateFactory"
                                    )
                                    doHookFactory(module, factoryClass)
                                } catch (_: ClassNotFoundException) {
                                } catch (_: Exception) {}
                            }
                            result
                        }
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
    }

    @Synchronized
    private fun doHookFactory(module: XposedModule, factoryClass: Class<*>) {
        try {
            val key = "${factoryClass.name}@${System.identityHashCode(factoryClass.classLoader)}"
            if (!hookedFactories.add(key)) return

            val targetMethod = factoryClass.declaredMethods
                .firstOrNull { it.name == "createBigIslandTemplateView" }

            if (targetMethod != null) {
                module.hook(targetMethod).intercept { chain ->
                    val result = chain.proceed()
                    val bigIslandView = result as? ViewGroup
                    if (bigIslandView != null) {
                        ensureObserver(bigIslandView.context, module)
                        if (pendingMarqueeEnabled) {
                            traverseAndApplyMarquee(bigIslandView)
                        }
                    }
                    result
                }
                module.log("$TAG: hooked ${targetMethod.name} on ${factoryClass.name}")
            } else {
                module.log("$TAG: createBigIslandTemplateView not found in ${factoryClass.name}")
            }
        } catch (e: Exception) {
            module.log("$TAG: doHookFactory error: ${e.message}")
        }
    }

    // ─── MarqueeController ────────────────────────────────────────────────────

    class MarqueeController(
        private val view: TextView,
        var speedPxPerSec: Int = 100,
        private val delayMs: Int = 1500
    ) : Choreographer.FrameCallback {

        private companion object {
            const val PAUSE_AT_END_MS = 1000
        }

        private var currentScrollX = 0f
        private var isRunning = false
        private var startTimeNanos = 0L
        private var lastFrameTimeNanos = 0L
        private val choreographer = Choreographer.getInstance()
        private var state = 0
        private var currentText = ""

        fun start() {
            val textNow = view.text.toString()
            if (isRunning && currentText == textNow) return
            currentText = textNow
            isRunning = true
            currentScrollX = 0f
            state = 0
            startTimeNanos = 0
            choreographer.removeFrameCallback(this)
            choreographer.postFrameCallback(this)
        }

        fun stop() {
            if (!isRunning) return
            isRunning = false
            choreographer.removeFrameCallback(this)
            view.scrollTo(0, 0)
        }

        private fun getRealMaxScroll(): Float {
            val textWidth = view.paint.measureText(currentText)
            var visibleW = if (view.width > 0) view.width else Int.MAX_VALUE
            var p = view.parent
            while (p is ViewGroup) {
                if (p.width > 0 && p.width < visibleW) visibleW = p.width
                p = p.parent
            }
            if (visibleW == Int.MAX_VALUE) visibleW = 0
            val availableW = visibleW - view.paddingLeft - view.paddingRight
            return kotlin.math.max(0f, textWidth - availableW.toFloat())
        }

        override fun doFrame(frameTimeNanos: Long) {
            if (!isRunning) return
            if (startTimeNanos == 0L) {
                startTimeNanos = frameTimeNanos
                lastFrameTimeNanos = frameTimeNanos
            }
            val maxScroll = getRealMaxScroll()
            if (maxScroll <= 0) { stop(); return }

            val elapsedMs = (frameTimeNanos - startTimeNanos) / 1_000_000
            when (state) {
                0 -> if (elapsedMs >= delayMs) {
                    state = 1
                    lastFrameTimeNanos = frameTimeNanos
                }
                1 -> {
                    currentScrollX += speedPxPerSec * ((frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f)
                    if (currentScrollX >= maxScroll) {
                        currentScrollX = maxScroll
                        state = 2
                        startTimeNanos = frameTimeNanos
                    }
                    view.scrollTo(currentScrollX.toInt(), 0)
                }
                2 -> if (elapsedMs > PAUSE_AT_END_MS) {
                    currentScrollX = 0f
                    view.scrollTo(0, 0)
                    state = 0
                    startTimeNanos = frameTimeNanos
                }
            }
            lastFrameTimeNanos = frameTimeNanos
            choreographer.postFrameCallback(this)
        }
    }
}