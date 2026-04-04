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

private fun normalizeText(text: String): String {
        return text
            .replace(Regex("[\\n\\r\\t\\u00A0\\u200B\\uFEFF]+"), " ")
            .replace(Regex(" +"), " ")
            .trim()
    }

    private val scrollerMap = WeakHashMap<TextView, MarqueeController>()
    private val observedViews = WeakHashMap<TextView, Boolean>()
    private val islandMarqueeState = WeakHashMap<ViewGroup, Boolean>()

    @Volatile private var cachedSpeed: Int? = null
    @Volatile private var observerRegistered = false

    private fun findBigIslandView(view: View): ViewGroup? {
        var p = view.parent
        while (p is ViewGroup) {
            if (islandMarqueeState.containsKey(p)) return p
            p = p.parent
        }
        return null
    }

    private fun isMarqueeEnabledFor(textView: TextView): Boolean {
        val island = findBigIslandView(textView)
        return island?.let { islandMarqueeState[it] } ?: false
    }

    fun ensureObserver(context: android.content.Context, module: XposedModule) {
        if (observerRegistered) return
        ConfigManager.init(module)
        ConfigManager.addChangeListener {
            cachedSpeed = null
            stopAllMarquees()
            //module.log("$TAG: settings changed via Observer, cache cleared")
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
        val cleanText = normalizeText(fullText)
        if (cleanText.isEmpty()) {
            stopMarquee(textView)
            return
        }
        if (textView.maxLines != 1) {
            textView.setSingleLine(true)
        }
        if (fullText != cleanText) {
            textView.text = cleanText
        }
        val measuredW = textView.paint.measureText(cleanText)
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
        val controller = scrollerMap.remove(textView)
        controller?.stop()
        val fullText = textView.text?.toString() ?: ""
        val cleanText = normalizeText(fullText)
        if (fullText != cleanText) {
            textView.text = cleanText
        }
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

    fun traverseAndApplyMarquee(bigIslandView: ViewGroup, enabled: Boolean) {
        islandMarqueeState[bigIslandView] = enabled
        traverseInternal(bigIslandView, enabled)
    }

    private fun traverseInternal(view: View, enabled: Boolean) {
        if (view is TextView) {
            if (observedViews.containsKey(view)) {
                if (enabled) startMarquee(view)
                else stopMarquee(view)
                return
            }
            observedViews[view] = true

            view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                val tv = v as TextView
                if (isMarqueeEnabledFor(tv)) startMarquee(tv)
                else stopMarquee(tv)
            }
            view.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    if (isMarqueeEnabledFor(view)) startMarquee(view)
                    else stopMarquee(view)
                }
            })
            if (enabled) startMarquee(view)
            else stopMarquee(view)
        } else if (view is ViewGroup) {
            view.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
                override fun onChildViewAdded(parent: View?, child: View?) {
                    if (child is TextView) {
                        traverseInternal(child, isMarqueeEnabledFor(child))
                    } else if (child is ViewGroup) {
                        traverseInternal(child, false)
                    }
                }
                override fun onChildViewRemoved(parent: View?, child: View?) {
                    if (child is TextView) stopMarquee(child)
                }
            })
            for (i in 0 until view.childCount) {
                traverseInternal(view.getChildAt(i), enabled)
            }
        }
    }

    // ─── IXposedHookLoadPackage → init ────────────────────────────────────────

    private var hookedContentView = false
    private val targetPkg = java.util.Collections.synchronizedMap(java.util.WeakHashMap<View, String>())

    fun init(module: XposedModule, param: PackageLoadedParam) {
        module.log("$TAG: initializing for ${param.packageName}")
        hookContentViewClasses(module, param.defaultClassLoader)
        hookDynamicClassLoaders(module)
    }

    private fun hookContentViewClasses(module: XposedModule, classLoader: ClassLoader) {
        if (hookedContentView) return
        val classNames = arrayOf(
            "miui.systemui.dynamicisland.window.content.DynamicIslandContentView",
            "miui.systemui.dynamicisland.window.content.DynamicIslandContentFakeView"
        )
        for (className in classNames) {
            try {
                val clazz = classLoader.loadClass(className)
                val updateMethod = clazz.declaredMethods.firstOrNull { it.name == "updateBigIslandView" }
                if (updateMethod != null) {
                    module.hook(updateMethod).intercept { chain ->
                        val result = chain.proceed()
                        try {
                            val islandView = chain.thisObject as? ViewGroup
                            if (islandView == null) return@intercept result
                            val islandData = chain.args.getOrNull(0)
                            var pkgName = ""
                            try {
                                if (islandData != null) {
                                    val getExtrasMethod = islandData.javaClass.getMethod("getExtras")
                                    val extras = getExtrasMethod.invoke(islandData) as? android.os.Bundle
                                    pkgName = extras?.getString("miui.pkg.name") ?: ""
                                }
                            } catch (_: Exception) {}
                            if (pkgName.isNotEmpty()) {
                                targetPkg[islandView] = pkgName
                            } else {
                                pkgName = targetPkg[islandView] ?: ""
                            }
                            if (pkgName.isEmpty()) return@intercept result
                            ensureObserver(islandView.context, module)
                            val isOngoing = try {
                                islandData?.javaClass?.getMethod("isOngoing")?.invoke(islandData) as? Boolean ?: false
                            } catch (_: Exception) { false }
                            if (isOngoing) {
                                traverseAndApplyMarquee(islandView, false)
                                return@intercept result
                            }
                            val marqueeRaw = ConfigManager.getString("pref_channel_marquee_${pkgName}", "default")
                            val defaultMarquee = ConfigManager.getBoolean("pref_default_marquee", false)
                            val enabled = when (marqueeRaw) {
                                "on" -> true
                                "off" -> false
                                else -> defaultMarquee
                            }
                            traverseAndApplyMarquee(islandView, enabled)
                        } catch (_: Exception) {}
                        result
                    }
                    hookedContentView = true
                    module.log("$TAG: hooked updateBigIslandView on $className")
                }
            } catch (_: Exception) {}
        }
    }

    private fun hookDynamicClassLoaders(module: XposedModule) {
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
                            if (cl != null && !hookedContentView) {
                                hookContentViewClasses(module, cl)
                            }
                            result
                        }
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
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
            val textNow = normalizeText(view.text.toString())
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