package io.github.hyperisland.xposed.hook

import android.service.notification.StatusBarNotification
import android.os.SystemClock
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.log
import io.github.hyperisland.xposed.logError
import io.github.hyperisland.xposed.islanddispatch.definition.IslandDispatchContract
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.WeakHashMap

/**
 * Hook SystemUI 中超级岛大岛视图的 TextView，实现自定义跑马灯（文字横向滚动）效果。
 */
object MarqueeHook : BaseHook() {

    private const val TAG = "HyperIsland[Marquee]"
    private const val SELF_PKG = "io.github.hyperisland"
    private const val SYSTEM_UI_PKG = "com.android.systemui"
    private const val DIRECT_PROXY_CHANNEL = IslandDispatchContract.CHANNEL_ID

    override fun getTag() = TAG

    override fun onConfigChanged() {
        cachedSpeed = null
        hookedContentView = false
        cachedWhitelist = null
        stopAllMarquees()
    }

    private val scrollerMap = WeakHashMap<TextView, MarqueeController>()
    private val observedViews = WeakHashMap<TextView, TextViewListeners>()
    private val islandMarqueeState = WeakHashMap<ViewGroup, Boolean>()
    private val forcedSingleLineMaxLines = WeakHashMap<TextView, Int>()

    @Volatile private var cachedSpeed: Int? = null

    private data class TextViewListeners(
        val layoutListeners: ArrayList<View.OnLayoutChangeListener>,
        val textWatcher: android.text.TextWatcher
    )

    private fun normalizeText(text: String): String {
        return text
            .replace(Regex("[\\n\\r\\t\\u00A0\\u200B\\uFEFF]+"), " ")
            .replace(Regex(" +"), " ")
            .trim()
    }

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

    private fun getMarqueeSpeed(): Int {
        cachedSpeed?.let { return it }
        return ConfigManager.getInt("pref_marquee_speed", 100).coerceIn(20, 500)
            .also { cachedSpeed = it }
    }

    private fun stopAllMarquees() {
        val textViews = observedViews.keys.toList()
        textViews.forEach { unobserveTextView(it) }
        scrollerMap.values.forEach { it.stop() }
        scrollerMap.clear()
        forcedSingleLineMaxLines.clear()
        islandMarqueeState.clear()
    }

    fun startMarquee(textView: TextView) {
        val fullText = textView.text?.toString() ?: ""
        val cleanText = normalizeText(fullText)
        if (cleanText.isEmpty()) {
            stopMarquee(textView)
            return
        }
        if (textView.maxLines != 1) {
            if (!forcedSingleLineMaxLines.containsKey(textView)) {
                forcedSingleLineMaxLines[textView] = textView.maxLines
            }
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
        val originalMaxLines = forcedSingleLineMaxLines.remove(textView)
        if (originalMaxLines != null) {
            textView.setSingleLine(false)
            textView.maxLines = originalMaxLines
        }
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
        //log("Marquee ${if (enabled) "enabled" else "disabled"} for island view")
        islandMarqueeState[bigIslandView] = enabled
        traverseInternal(bigIslandView, enabled)
    }

    private fun isInExpandedView(view: View): Boolean {
        var p: View? = view
        while (p != null) {
            val className = p.javaClass.simpleName
            if (className.contains("DynamicIslandExpandedView") || 
                className.contains("ExpandedView")) {
                return true
            }
            p = if (p.parent is View) p.parent as View else null
        }
        return false
    }

    private fun traverseInternal(view: View, enabled: Boolean) {
        if (view is TextView) {
            if (isInExpandedView(view)) return
            if (enabled) {
                observeTextView(view)
                startMarquee(view)
            } else {
                unobserveTextView(view)
                stopMarquee(view)
            }
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                traverseInternal(view.getChildAt(i), enabled)
            }
        }
    }

    private fun observeTextView(view: TextView) {
        if (observedViews.containsKey(view)) return

        val listeners = ArrayList<View.OnLayoutChangeListener>()
        val layoutListener = View.OnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val tv = v as TextView
            if (isInExpandedView(tv)) return@OnLayoutChangeListener
            tv.post {
                if (isMarqueeEnabledFor(tv)) startMarquee(tv)
                else stopMarquee(tv)
            }
        }
        listeners.add(layoutListener)
        view.addOnLayoutChangeListener(layoutListener)

        val textWatcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (isInExpandedView(view)) return
                view.post {
                    if (isMarqueeEnabledFor(view)) startMarquee(view)
                    else stopMarquee(view)
                }
            }
        }
        view.addTextChangedListener(textWatcher)
        observedViews[view] = TextViewListeners(listeners, textWatcher)
    }

    private fun unobserveTextView(view: TextView) {
        val listeners = observedViews.remove(view) ?: return
        listeners.layoutListeners.forEach { view.removeOnLayoutChangeListener(it) }
        view.removeTextChangedListener(listeners.textWatcher)
    }

    private var hookedContentView = false
    private val targetPkg = java.util.Collections.synchronizedMap(WeakHashMap<View, String>())
    private val targetChannel = java.util.Collections.synchronizedMap(WeakHashMap<View, String>())
    @Volatile private var cachedWhitelist: Map<String, Set<String>>? = null
    private val directProxyExpireAt =
        java.util.Collections.synchronizedMap(mutableMapOf<String, Long>())

    fun markDirectProxyPosted(pkg: String, channelId: String) {
        if (pkg.isBlank()) return
        val safeChannel = channelId.ifBlank { "toast" }
        val key = "$pkg#$safeChannel"
        directProxyExpireAt[key] = SystemClock.elapsedRealtime() + 10_000L
    }

    private fun takeRecentDirectProxySource(): Pair<String, String>? {
        val now = SystemClock.elapsedRealtime()
        val iterator = directProxyExpireAt.entries.iterator()
        var candidate: Pair<String, String>? = null
        var bestExpire = 0L
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value <= now) {
                iterator.remove()
                continue
            }
            if (entry.value > bestExpire) {
                val parts = entry.key.split('#', limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank()) {
                    candidate = parts[0] to parts[1]
                    bestExpire = entry.value
                }
            }
        }
        return candidate
    }

    private fun loadWhitelist(): Map<String, Set<String>> {
        cachedWhitelist?.let { return it }
        val csv = ConfigManager.getString("pref_generic_whitelist")
        val map = csv.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .associate { pkg ->
                val channelCsv = ConfigManager.getString("pref_channels_$pkg")
                val channels = if (channelCsv.isBlank()) emptySet()
                else channelCsv.split(",").filter { it.isNotBlank() }.toSet()
                pkg to channels
            }
        if (map.isNotEmpty()) cachedWhitelist = map
        return map
    }

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        hookDynamicClassLoaders(module)
    }

    private fun hookContentViewClasses(module: XposedModule, classLoader: ClassLoader) {
        if (hookedContentView) return
        val classNames = arrayOf(
            "miui.systemui.dynamicisland.window.content.DynamicIslandContentView"
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
                            var channelId = ""
                            var isOngoing = false
                            try {
                                if (islandData != null) {
                                    val getExtrasMethod = islandData.javaClass.getMethod("getExtras")
                                    val extras = getExtrasMethod.invoke(islandData) as? android.os.Bundle
                                    val sbn = if (android.os.Build.VERSION.SDK_INT >= 33) {
                                        extras?.getParcelable("miui.sbn", StatusBarNotification::class.java)
                                    } else {
                                        @Suppress("DEPRECATION")
                                        extras?.getParcelable("miui.sbn") as? StatusBarNotification
                                    }
                                    pkgName = sbn?.packageName ?: ""
                                    channelId = sbn?.notification?.channelId ?: ""
                                    val sourcePkg = sbn?.notification?.extras
                                        ?.getString("hyperisland_source_pkg")
                                        ?.trim()
                                        .orEmpty()
                                    val sourceChannel = sbn?.notification?.extras
                                        ?.getString("hyperisland_source_channel")
                                        ?.trim()
                                        .orEmpty()
                                    if (sourcePkg.isNotEmpty()) {
                                        pkgName = sourcePkg
                                        channelId = sourceChannel.ifEmpty {
                                            if (channelId.isNotEmpty()) channelId else "toast"
                                        }
                                    } else if (
                                        (pkgName == SELF_PKG || pkgName == SYSTEM_UI_PKG) &&
                                            channelId == DIRECT_PROXY_CHANNEL
                                    ) {
                                        val recent = takeRecentDirectProxySource()
                                        if (recent != null) {
                                            pkgName = recent.first
                                            channelId = recent.second
                                        }
                                    }
                                    isOngoing = (sbn?.notification?.flags ?: 0) and android.app.Notification.FLAG_ONGOING_EVENT != 0
                                    targetPkg[islandView] = pkgName
                                    targetChannel[islandView] = channelId
                                }
                            } catch (_: Exception) {}
                            if (pkgName.isEmpty()) {
                                pkgName = targetPkg[islandView] ?: ""
                            }
                            if (channelId.isEmpty()) {
                                channelId = targetChannel[islandView] ?: ""
                            }
                            if (pkgName.isEmpty()) return@intercept result
                            
                            val isToastSource = channelId == "toast"
                            if (!isToastSource) {
                                val whitelist = loadWhitelist()
                                val allowedChannels = whitelist[pkgName]
                                if (allowedChannels == null) {
                                    traverseAndApplyMarquee(islandView, false)
                                    return@intercept result
                                }

                                if (allowedChannels.isNotEmpty() && channelId.isEmpty()) {
                                    traverseAndApplyMarquee(islandView, false)
                                    return@intercept result
                                }

                                if (allowedChannels.isNotEmpty() && channelId.isNotEmpty() && channelId !in allowedChannels) {
                                    traverseAndApplyMarquee(islandView, false)
                                    return@intercept result
                                }
                            }

                            val marqueeRaw = if (isToastSource) {
                                ConfigManager.getString("pref_toast_marquee_$pkgName", "default")
                            } else {
                                val marqueeKey = "pref_channel_marquee_${pkgName}_${channelId}"
                                ConfigManager.getString(marqueeKey, "default")
                            }
                            val defaultMarquee = ConfigManager.getBoolean("pref_default_marquee", false)
                            val enabled = when (marqueeRaw) {
                                "on" -> true
                                "off" -> false
                                else -> defaultMarquee
                            }
                            
                            if (!enabled || isOngoing) {
                                traverseAndApplyMarquee(islandView, false)
                                return@intercept result
                            }
                            
                            log("Marquee triggered for package: $pkgName")
                            traverseAndApplyMarquee(islandView, true)
                        } catch (e: Exception) {
                            logError("Error in updateBigIslandView hook: ${e.message}")
                        }
                        result
                    }
                    hookedContentView = true
                    module.log("Hooked updateBigIslandView on $className")
                }
            } catch (_: Exception) {}
        }
    }

    private fun hookDynamicClassLoaders(module: XposedModule) {
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { cl ->
            if (!hookedContentView) {
                hookContentViewClasses(module, cl)
            }
        }
    }

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
