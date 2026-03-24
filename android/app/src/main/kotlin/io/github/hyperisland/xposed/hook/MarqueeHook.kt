package io.github.hyperisland.xposed.hook

import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.WeakHashMap

/**
 * Hook SystemUI 中超级岛大岛视图的 TextView，实现自定义跑马灯（文字横向滚动）效果。
 *
 * 工作原理：
 *   1. Hook [IslandTemplateFactory.createBigIslandTemplateView]，在大岛视图创建后注入监听。
 *   2. 递归遍历视图树，对每个 TextView 添加布局变化和文本变化监听。
 *   3. 当文本宽度超出可视区域时，启动 [MarqueeController] 驱动 Choreographer 帧回调实现滚动。
 *
 * 渠道级控制：
 *   由 GenericProgressHook 在处理通知时读取每个渠道的跑马灯开关设置，并通过
 *   [pendingMarqueeEnabled] 传递给 MarqueeHook。通过 ContentObserver 监听设置变化，
 *   变更时立即停止所有正在运行的跑马灯，待下次通知到来时重新评估。
 */
class MarqueeHook : IXposedHookLoadPackage {

    companion object {
        /** 已 hook 过的工厂类标识，防止重复 hook。格式：className@classLoaderIdentityHash */
        private val hookedFactories = mutableSetOf<String>()

        /** 存活的跑马灯控制器，使用 WeakHashMap 避免 TextView 泄漏。 */
        private val scrollerMap = WeakHashMap<TextView, MarqueeController>()

        /** 已注册监听的 TextView，避免重复绑定。 */
        private val observedViews = WeakHashMap<TextView, Boolean>()

        // ─── 热加载相关 ───────────────────────────────────────────────────────

        /**
         * 由 GenericProgressHook 在处理通知时设置，表示当前通知的渠道是否启用了跑马灯。
         * MarqueeHook 在大岛视图创建时读取此值以决定是否启动跑马灯。
         */
        @Volatile var pendingMarqueeEnabled: Boolean = false

        /** 缓存的滚动速度（px/s），null 表示需重新读取。 */
        @Volatile private var cachedSpeed: Int? = null

        /** 确保 ContentObserver 只注册一次。 */
        @Volatile private var observerRegistered = false

        /**
         * 在 SystemUI 进程内注册 ContentObserver，监听 SettingsProvider 的任意变化。
         * 变化时清除缓存，并停止所有正在运行的跑马灯（下次通知到来时会重新评估）。
         * 与 GenericProgressHook.ensureObserver 保持相同模式。
         */
        fun ensureObserver(context: android.content.Context) {
            if (observerRegistered) return
            val settingsUri = android.net.Uri.parse("content://io.github.hyperisland.settings/")
            context.contentResolver.registerContentObserver(
                settingsUri, true,
                object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        // 失效速度缓存，下次读取时重新查询
                        cachedSpeed = null
                        // 设置变更后停止所有跑马灯，待下次通知时重新评估渠道级设置
                        stopAllMarquees()
                        XposedBridge.log("HyperIsland[MarqueeHook]: settings changed, cache cleared")
                    }
                }
            )
            observerRegistered = true
            XposedBridge.log("HyperIsland[MarqueeHook]: ContentObserver registered in SystemUI")
        }

        /**
         * 从缓存或 SettingsProvider 读取跑马灯速度（px/s），限位 20~500。
         * 缓存有效期内（cachedSpeed != null）不再跨进程查询。
         */
        private fun getMarqueeSpeed(context: android.content.Context): Int {
            cachedSpeed?.let { return it }
            val speed = try {
                val uri = android.net.Uri.parse("content://io.github.hyperisland.settings/pref_marquee_speed")
                context.contentResolver.query(uri, null, null, null, null)
                    ?.use { if (it.moveToFirst()) it.getInt(0) else 100 } ?: 100
            } catch (_: Exception) { 100 }
            val clamped = speed.coerceIn(20, 500)
            cachedSpeed = clamped
            return clamped
        }

        /** 停止所有当前存活的跑马灯并清空映射表。 */
        private fun stopAllMarquees() {
            scrollerMap.values.forEach { it.stop() }
            scrollerMap.clear()
        }

        // ─── 跑马灯启停 ──────────────────────────────────────────────────────

        /**
         * 对指定 TextView 启动跑马灯（若文本超出可视宽度）或停止跑马灯（若文本不超出）。
         */
        fun startMarquee(textView: TextView) {
            val fullText = textView.text?.toString() ?: ""
            if (fullText.isEmpty()) {
                stopMarquee(textView)
                return
            }

            // 计算文本宽度与可视宽度
            val measuredW = textView.paint.measureText(fullText)
            val visibleW = resolveVisibleWidth(textView)
            val availableW = visibleW - textView.paddingLeft - textView.paddingRight
            val needMarquee = measuredW > availableW

            if (needMarquee && visibleW > 0) {
                val speed = getMarqueeSpeed(textView.context)
                val controller = scrollerMap.getOrPut(textView) { MarqueeController(textView, speed) }
                controller.speedPxPerSec = speed  // 实时同步最新速度设置
                controller.start()
            } else {
                stopMarquee(textView)
            }
        }

        fun stopMarquee(textView: TextView) {
            scrollerMap.remove(textView)?.stop()
            textView.scrollTo(0, 0)
        }

        /**
         * 向上遍历视图树，取自身宽度与所有父 ViewGroup 宽度中的最小值作为实际可视宽度。
         * 用于准确判断文本是否会被截断。
         */
        private fun resolveVisibleWidth(view: View): Int {
            var visibleW = if (view.width > 0) view.width else Int.MAX_VALUE
            var p = view.parent
            while (p is ViewGroup) {
                if (p.width > 0 && p.width < visibleW) visibleW = p.width
                p = p.parent
            }
            return if (visibleW == Int.MAX_VALUE) 0 else visibleW
        }

        // ─── 视图树遍历 ──────────────────────────────────────────────────────

        /**
         * 递归遍历 [view] 及其子树：
         * - TextView：绑定布局变化、文本变化监听，并立即尝试启动跑马灯。
         * - ViewGroup：注册层级变化监听，子视图加入时递归处理，移除时清理跑马灯。
         */
        fun traverseAndApplyMarquee(view: View) {
            if (view is TextView) {
                if (observedViews.containsKey(view)) return
                observedViews[view] = true

                // 视图布局尺寸变化时重新评估是否需要滚动（如旋转屏幕、窗口缩放）
                view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                    startMarquee(v as TextView)
                }

                // 文本内容变化时重新评估（如通知更新）
                view.addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: android.text.Editable?) {
                        startMarquee(view)
                    }
                })

                startMarquee(view)
            } else if (view is ViewGroup) {
                // 监听子视图增减，动态处理新加入的子视图
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
    }

    // ─── IXposedHookLoadPackage ───────────────────────────────────────────────

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.android.systemui") return
        XposedBridge.log("HyperIsland[MarqueeHook]: initializing for ${lpparam.packageName}")

        try {
            // 优先尝试直接加载 IslandTemplateFactory（主 ClassLoader 已包含时）
            val factoryClass = lpparam.classLoader
                .loadClass("miui.systemui.dynamicisland.template.IslandTemplateFactory")
            doHookFactory(factoryClass)
        } catch (_: ClassNotFoundException) {
            // IslandTemplateFactory 可能由插件 ClassLoader 动态加载，监听各类 ClassLoader 的构造，
            // 在新 ClassLoader 创建时尝试加载并 hook。
            hookDynamicClassLoaders(lpparam)
        } catch (e: Exception) {
            XposedBridge.log("HyperIsland[MarqueeHook]: failed to inject: ${e.message}")
        }
    }

    /**
     * 针对 MIUI/Android 各种 ClassLoader 实现注册构造钩子，
     * 在运行时动态加载的 ClassLoader 创建后立即尝试 hook IslandTemplateFactory。
     */
    private fun hookDynamicClassLoaders(lpparam: XC_LoadPackage.LoadPackageParam) {
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
                        XposedBridge.hookMethod(ctor, object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                val cl = param.thisObject as? ClassLoader ?: return
                                try {
                                    val factoryClass = cl
                                        .loadClass("miui.systemui.dynamicisland.template.IslandTemplateFactory")
                                    doHookFactory(factoryClass)
                                } catch (_: ClassNotFoundException) {
                                } catch (_: Exception) {}
                            }
                        })
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Hook [IslandTemplateFactory.createBigIslandTemplateView]。
     * 使用标记字段防止同一 ClassLoader 重复 hook。
     */
    @Synchronized
    private fun doHookFactory(factoryClass: Class<*>) {
        try {
            // 防止重复 hook：用类名 + ClassLoader 标识唯一区分
            val key = "${factoryClass.name}@${System.identityHashCode(factoryClass.classLoader)}"
            if (!hookedFactories.add(key)) return

            val targetMethod = factoryClass.declaredMethods
                .firstOrNull { it.name == "createBigIslandTemplateView" }

            if (targetMethod != null) {
                XposedBridge.hookMethod(targetMethod, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val bigIslandView = param.result as? ViewGroup ?: return
                        val context = bigIslandView.context
                        // 注册 ContentObserver（只执行一次），保证设置热加载生效
                        ensureObserver(context)
                        if (pendingMarqueeEnabled) {
                            traverseAndApplyMarquee(bigIslandView)
                        }
                    }
                })
                XposedBridge.log("HyperIsland[MarqueeHook]: hooked ${targetMethod.name} on ${factoryClass.name}")
            } else {
                XposedBridge.log("HyperIsland[MarqueeHook]: createBigIslandTemplateView not found in ${factoryClass.name}")
            }
        } catch (e: Exception) {
            XposedBridge.log("HyperIsland[MarqueeHook]: doHookFactory error: ${e.message}")
        }
    }

    // ─── MarqueeController ────────────────────────────────────────────────────

    /**
     * 基于 [Choreographer] 帧回调驱动的跑马灯控制器。
     *
     * 滚动状态机（[state]）：
     *   0 — 初始等待（[delayMs] ms 不移动，让用户先读到标题开头）
     *   1 — 匀速向左滚动（速度 [speedPxPerSec] px/s，直到到达文本末端）
     *   2 — 末端停顿（[PAUSE_AT_END_MS] ms 后重置并回到状态 0）
     *
     * @param view         目标 TextView
     * @param speedPxPerSec 滚动速度（像素/秒）
     * @param delayMs      每轮开始前的初始等待时间（毫秒）
     */
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
            // 文本未变且已在运行，无需重启
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

        /** 计算当前文本在可视区域内可滚动的最大像素距离。 */
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
            if (maxScroll <= 0) {
                // 文本已不超出，停止滚动（如视图变宽或文本变短）
                stop()
                return
            }

            val elapsedMs = (frameTimeNanos - startTimeNanos) / 1_000_000

            when (state) {
                // 状态 0：初始等待，delayMs 后进入滚动
                0 -> if (elapsedMs >= delayMs) {
                    state = 1
                    lastFrameTimeNanos = frameTimeNanos
                }

                // 状态 1：匀速滚动，根据帧间隔时间计算位移
                1 -> {
                    currentScrollX += speedPxPerSec * ((frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f)
                    if (currentScrollX >= maxScroll) {
                        currentScrollX = maxScroll
                        state = 2
                        startTimeNanos = frameTimeNanos
                    }
                    view.scrollTo(currentScrollX.toInt(), 0)
                }

                // 状态 2：末端停顿，PAUSE_AT_END_MS 后重置回状态 0
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
