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
import kotlin.math.max

class MarqueeHook : IXposedHookLoadPackage {
    
    companion object {
        private const val FIELD_HOOKED_FACTORY = "HOOKED_FACTORY_HYPERISLAND_MARQUEE"
        private val scrollerMap = WeakHashMap<TextView, MarqueeController>()
        
        fun startMarquee(textView: TextView) {
            val fullText = textView.text?.toString() ?: ""
            if (fullText.isEmpty()) {
                stopMarquee(textView)
                return
            }
            
            val measuredW = textView.paint.measureText(fullText)
            val actualW = textView.width

            var visibleW = if (actualW > 0) actualW else Int.MAX_VALUE
            var p = textView.parent
            while (p is ViewGroup) {
                if (p.width > 0 && p.width < visibleW) {
                    visibleW = p.width
                }
                p = p.parent
            }
            if (visibleW == Int.MAX_VALUE) visibleW = 0

            val availableW = visibleW - textView.paddingLeft - textView.paddingRight
            val needMarquee = measuredW > availableW

            if (needMarquee && visibleW > 0) {
                var controller = scrollerMap[textView]
                if (controller == null) {
                    controller = MarqueeController(textView)
                    scrollerMap[textView] = controller
                }
                controller.start()
            } else {
                stopMarquee(textView)
            }
        }

        fun stopMarquee(textView: TextView) {
            scrollerMap.remove(textView)?.stop()
            textView.scrollTo(0, 0)
        }

        private fun isMarqueeEnabled(context: android.content.Context): Boolean {
            return try {
                val uri = android.net.Uri.parse("content://io.github.hyperisland.settings/pref_marquee_feature")
                context.contentResolver.query(uri, null, null, null, null)
                    ?.use { if (it.moveToFirst()) it.getInt(0) != 0 else false } ?: false
            } catch (_: Exception) {
                false
            }
        }

        private val observedViews = WeakHashMap<TextView, Boolean>()

        private fun traverseAndApplyMarquee(view: View) {
            if (view is TextView) {
                if (observedViews.containsKey(view)) return
                observedViews[view] = true
                XposedBridge.log("HyperIsland[MarqueeHook]: new TextView found, hooking listeners.")

                view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                    startMarquee(v as TextView)
                }

                view.addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: android.text.Editable?) {
                        startMarquee(view)
                    }
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
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.android.systemui") return
        XposedBridge.log("HyperIsland[MarqueeHook]: initializing for ${lpparam.packageName}")

        try {
            // First attempt: hook direct load
            val factoryClass = lpparam.classLoader.loadClass("miui.systemui.dynamicisland.template.IslandTemplateFactory")
            doHookFactory(factoryClass)
        } catch (_: ClassNotFoundException) {
            // Register ClassLoader hooks if not found
            val classLoaders = arrayOf(
                "dalvik.system.BaseDexClassLoader",
                "dalvik.system.PathClassLoader",
                "dalvik.system.DexClassLoader",
                "dalvik.system.DelegateLastClassLoader"
            )

            for (clName in classLoaders) {
                try {
                    val clazz = Class.forName(clName)
                    for (c in clazz.declaredConstructors) {
                        try {
                            XposedBridge.hookMethod(c, object : XC_MethodHook() {
                                override fun afterHookedMethod(param: MethodHookParam) {
                                    val cl = param.thisObject as? ClassLoader ?: return
                                    try {
                                        val factoryClass = cl.loadClass("miui.systemui.dynamicisland.template.IslandTemplateFactory")
                                        doHookFactory(factoryClass)
                                    } catch (_: ClassNotFoundException) {
                                    } catch (_: Exception) {}
                                }
                            })
                        } catch (e: Exception) { }
                    }
                } catch (e: Exception) { }
            }
        } catch (e: Exception) {
            XposedBridge.log("HyperIsland[MarqueeHook]: failed to inject: ${e.message}")
        }
    }

    @Synchronized
    private fun doHookFactory(factoryClass: Class<*>) {
        try {
            val hookedField = try {
                factoryClass.classLoader?.javaClass?.getDeclaredField(FIELD_HOOKED_FACTORY)?.apply { isAccessible = true }
            } catch (_: NoSuchFieldException) { null }

            if (hookedField != null && hookedField.get(factoryClass.classLoader) == true) {
                return
            }
            hookedField?.set(factoryClass.classLoader, true)

            val targetMethod = factoryClass.declaredMethods.firstOrNull { it.name == "createBigIslandTemplateView" }
            if (targetMethod != null) {
                XposedBridge.hookMethod(targetMethod, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val bigIslandView = param.result as? ViewGroup ?: return
                        if (isMarqueeEnabled(bigIslandView.context)) {
                            traverseAndApplyMarquee(bigIslandView)
                        }
                    }
                })
                XposedBridge.log("HyperIsland[MarqueeHook]: IslandTemplateFactory hook accomplished on ${targetMethod.name}")
            } else {
                XposedBridge.log("HyperIsland[MarqueeHook]: no createBigIslandTemplateView method found.")
            }
        } catch (e: Exception) {
            XposedBridge.log("HyperIsland[MarqueeHook]: doHookFactory error: ${e.message}")
        }
    }

    class MarqueeController(
        private val view: TextView,
        private val speedPxPerSec: Int = 100,
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
            val cachedTextWidth = view.paint.measureText(currentText)
            var visibleW = if (view.width > 0) view.width else Int.MAX_VALUE
            var p = view.parent
            while (p is ViewGroup) {
                if (p.width > 0 && p.width < visibleW) {
                    visibleW = p.width
                }
                p = p.parent
            }
            if (visibleW == Int.MAX_VALUE) visibleW = 0
            val availableW = visibleW - view.paddingLeft - view.paddingRight
            return kotlin.math.max(0f, cachedTextWidth - availableW.toFloat())
        }

        override fun doFrame(frameTimeNanos: Long) {
            if (!isRunning) return

            if (startTimeNanos == 0L) {
                startTimeNanos = frameTimeNanos
                lastFrameTimeNanos = frameTimeNanos
            }

            val maxScroll = getRealMaxScroll()

            if (maxScroll <= 0) {
                stop()
                return
            }

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
