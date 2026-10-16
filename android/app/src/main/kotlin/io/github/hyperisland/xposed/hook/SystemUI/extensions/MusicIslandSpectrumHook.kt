package io.github.hyperisland.xposed.hook.SystemUI.extensions

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.media.audiofx.Visualizer
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.hyperisland.xposed.logWarn as xlogWarn
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// ── 顶层诊断（有界：每个 key 只打首次）───────────────────────────────────────
private const val TAG = "HyperIsland[MusicSpectrum]"
private val diagSeen = Collections.synchronizedSet(mutableSetOf<String>())
private fun diag(key: String, message: String) {
    if (diagSeen.add(key)) xlogWarn(TAG, message)
}

// ── 共享常量 ─────────────────────────────────────────────────────────────────
private const val DEFAULT_BAR_COUNT = 5
private const val MAX_BAR_COUNT = 8
private const val SYSTEM_AUDIO_SESSION = 0
private const val CAPTURE_SIZE = 512
private const val DECAY_STEP = 0.08f
private const val MAX_BIN_FRACTION = 0.5f
private const val MAX_AMPLITUDE_FRACTION = 0.9f
private const val MIN_BAR_HALF_FRACTION = 0.16f
private const val BARS_WIDTH_FRACTION = 0.72f
private const val TOP_COLOR = 0xFF57F2FF.toInt()
private const val BOTTOM_COLOR = 0xFFF5E6FF.toInt()
private const val GAIN_TARGET = 1f
private const val GAIN_ATTACK = 0.02f
private const val GAIN_RELEASE = 0.004f
private const val MIN_GAIN = 1e-4f
private const val MAX_GAIN = 1e3f
private const val SILENCE_NANOS = 350_000_000L

// ── 配置键 ─────────────────────────────────────────────────────────────────
private const val PREF_COLOR_MODE = "pref_music_island_spectrum_color_mode"
private const val PREF_GRADIENT_START = "pref_music_island_spectrum_gradient_start"
private const val PREF_GRADIENT_END = "pref_music_island_spectrum_gradient_end"
private const val PREF_BAR_COUNT = "pref_music_island_spectrum_bar_count"
private const val PREF_SMOOTHING = "pref_music_island_spectrum_smoothing"
private const val COLOR_MODE_FOLLOW_COVER = "follow_cover"

// ── 波动程度（平滑等级）─────────────────────────────────────────────────────
private const val SMOOTHING_LOW = "low"
private const val SMOOTHING_MEDIUM = "medium"
private const val SMOOTHING_HIGH = "high"

/** 解析 #RRGGBB / #AARRGGBB；失败返回 null。 */
private fun parseHexColor(value: String): Int? = runCatching {
    val trimmed = value.trim()
    if (!trimmed.startsWith("#")) return@runCatching null
    val hex = trimmed.substring(1)
    if (hex.length != 6 && hex.length != 8) return@runCatching null
    (hex.toLong(16) or if (hex.length == 6) 0xFF000000L else 0L).toInt()
}.getOrNull()

/**
 * 读取官方 IslandIconViewHolder 的封面动态渐变色（gradientTopColor / gradientBottomColor）。
 *
 * 官方 musicWave 的 Lottie「Gradient Fill 1」通过 registerLottieCallback 用这两个静态字段
 * 动态上色；字段由 getLottieColor(封面 MiPalette 主色) 在 IO 线程异步写入，初始值为 -1
 * （-1 表示封面色尚未算出，此时官方显示 Lottie 资源默认渐变色）。因此这里低频轮询读取，
 * 取得 -1 时回退到资源默认色（TOP_COLOR / BOTTOM_COLOR），实现「默认跟随封面、失败回退」。
 */
private object OfficialColors {
    private const val READ_INTERVAL_NANOS = 500_000_000L
    private var topField: java.lang.reflect.Field? = null
    private var bottomField: java.lang.reflect.Field? = null
    private var lastRead = 0L
    private var cachedTop = -1
    private var cachedBottom = -1

    fun attach(holderClass: Class<*>) {
        if (topField != null) return
        topField = runCatching {
            holderClass.getDeclaredField("gradientTopColor").apply { isAccessible = true }
        }.getOrNull()
        bottomField = runCatching {
            holderClass.getDeclaredField("gradientBottomColor").apply { isAccessible = true }
        }.getOrNull()
    }

    /** 低频读取（内部节流），返回 (top, bottom)；官方未取得时回退默认色。 */
    fun read(): Pair<Int, Int> {
        val now = System.nanoTime()
        if (now - lastRead < READ_INTERVAL_NANOS && cachedTop != -1) {
            return cachedTop to cachedBottom
        }
        lastRead = now
        val top = topField?.let { runCatching { it.getInt(null) }.getOrDefault(-1) } ?: -1
        val bottom = bottomField?.let { runCatching { it.getInt(null) }.getOrDefault(-1) } ?: -1
        cachedTop = if (top != -1) top else TOP_COLOR
        cachedBottom = if (bottom != -1) bottom else BOTTOM_COLOR
        return cachedTop to cachedBottom
    }
}

/**
 * 把音乐岛右侧的官方 Lottie 频谱（musicWave / musicPause，纯循环动画，与音频无关）
 * 替换为基于系统混音输出 FFT 的实时频谱。
 *
 * 关键约束：岛的 fake 过渡层与 stable 稳定层各有一个 iconContainer，会各自生成一个
 * SpectrumView。`Visualizer(0)`（系统混音输出）是进程内全局唯一资源，不能每个 View
 * 各建一个，否则第二个创建失败（IllegalStateException: state 2）并干扰第一个的采样
 * 句柄。因此这里使用全局单例采样源 + 引用计数，所有 SpectrumView 共享同一份 FFT 数据。
 */
object MusicIslandSpectrumHook : BaseHook() {

    private const val ISLAND_ICON_HOLDER =
        "miui.systemui.dynamicisland.module.IslandIconViewHolder"
    private const val PIC_MUSIC_WAVE = "musicWave"
    private const val PIC_MUSIC_PAUSE = "musicPause"

    private val hookedClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>()),
    )

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        hookClasses(module, param.defaultClassLoader)
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { classLoader ->
            hookClasses(module, classLoader)
        }
    }

    private fun hookClasses(module: XposedModule, classLoader: ClassLoader) {
        runCatching {
            val holderClass = classLoader.loadClass(ISLAND_ICON_HOLDER)
            if (!hookedClasses.add(holderClass)) return
            hookBind(module, holderClass)
        }.onFailure { error ->
            if (error !is ClassNotFoundException) {
                logError(module, "failed to hook IslandIconViewHolder: ${error.message}")
            }
        }
    }

    private fun hookBind(module: XposedModule, holderClass: Class<*>) {
        OfficialColors.attach(holderClass)
        val bindMethods = holderClass.declaredMethods.filter { method ->
            method.name == "bind" && method.parameterCount == 2
        }
        if (bindMethods.isEmpty()) {
            logWarn(module, "IslandIconViewHolder.bind not found")
            return
        }
        val getPicInfo = runCatching {
            holderClass.getMethod("getPicInfo")
        }.getOrNull()
        val getContextMethod = runCatching {
            holderClass.superclass?.getMethod("getContext")
        }.getOrNull()
        val iconContainerField = runCatching {
            holderClass.getDeclaredField("iconContainer").apply { isAccessible = true }
        }.getOrNull()
        val lottieViewField = runCatching {
            holderClass.getDeclaredField("lottieView").apply { isAccessible = true }
        }.getOrNull()

        bindMethods.forEach { method ->
            module.hook(method).intercept { chain ->
                val result = chain.proceed()
                runCatching {
                    applySpectrum(
                        holder = chain.thisObject,
                        getPicInfo = getPicInfo,
                        getContext = getContextMethod,
                        iconContainerField = iconContainerField,
                        lottieViewField = lottieViewField,
                    )
                }
                result
            }
            log(module) { "hooked IslandIconViewHolder#bind" }
        }
    }

    private fun applySpectrum(
        holder: Any,
        getPicInfo: java.lang.reflect.Method?,
        getContext: java.lang.reflect.Method?,
        iconContainerField: java.lang.reflect.Field?,
        lottieViewField: java.lang.reflect.Field?,
    ) {
        val picInfo = getPicInfo?.invoke(holder)
        val pic = picInfo?.let { info ->
            runCatching { info.javaClass.getMethod("getPic").invoke(info) as? String }.getOrNull()
        }
        val type = picInfo?.let { info ->
            runCatching { info.javaClass.getMethod("getType").invoke(info) as? Number }
                .getOrNull()?.toInt()
        }
        val container = iconContainerField?.get(holder) as? ViewGroup
            ?: return
        val lottieView = lottieViewField?.get(holder) as? View

        val realtime = pic == PIC_MUSIC_WAVE
        val isSpectrumPic = pic == PIC_MUSIC_WAVE || pic == PIC_MUSIC_PAUSE

        val existing = findSpectrum(container)
        if (!isSpectrumPic) {
            existing?.let { container.removeView(it) }
            if (type == 2) lottieView?.visibility = View.VISIBLE
            return
        }

        diag("bind.spectrum", "spectrum bind pic=$pic type=$type container=${container.width}x${container.height} lottie=${lottieView?.width}x${lottieView?.height}")

        val spectrum = existing ?: createAndAttach(
            container,
            getContext?.invoke(holder) as? Context,
            lottieView,
        )
        if (spectrum != null) {
            lottieView?.visibility = View.GONE
            spectrum.setRealtime(realtime)
        } else {
            lottieView?.visibility = View.VISIBLE
        }
    }

    private fun findSpectrum(container: ViewGroup): SpectrumView? {
        for (index in 0 until container.childCount) {
            val child = container.getChildAt(index)
            if (child is SpectrumView) return child
        }
        return null
    }

    /** 当前条数：读配置，clamp 到 [4, MAX_BAR_COUNT]。 */
    internal fun currentBarCount(): Int =
        ConfigManager.getInt(PREF_BAR_COUNT, DEFAULT_BAR_COUNT).coerceIn(4, MAX_BAR_COUNT)

    /** 当前渐变色：渐变模式读自定义色，否则跟随封面。返回 (top, bottom)。 */
    internal fun currentColors(): Pair<Int, Int> {
        val mode = ConfigManager.getString(PREF_COLOR_MODE, COLOR_MODE_FOLLOW_COVER)
        if (mode == COLOR_MODE_FOLLOW_COVER) return OfficialColors.read()
        val start = ConfigManager.getString(PREF_GRADIENT_START, "").let(::parseHexColor) ?: TOP_COLOR
        val end = ConfigManager.getString(PREF_GRADIENT_END, "").let(::parseHexColor) ?: BOTTOM_COLOR
        return start to end
    }

    /**
     * 波动程度（平滑）：低=强抑制（最平滑），高=完全不抑制（最实时，保持原始采样节奏）。
     * [Smoothing.attack] 是上升时的混合系数（1f 为瞬时跟随），[Smoothing.releaseStep] 是
     * 下降时每帧衰减的绝对步长。高挡使用与旧实现完全一致的参数，保证默认体验不变。
     */
    internal fun currentSmoothing(): Smoothing = when (
        ConfigManager.getString(PREF_SMOOTHING, SMOOTHING_HIGH)
    ) {
        SMOOTHING_LOW -> Smoothing(attack = 0.2f, releaseStep = 0.02f)
        SMOOTHING_MEDIUM -> Smoothing(attack = 0.5f, releaseStep = 0.04f)
        else -> Smoothing(attack = 1f, releaseStep = DECAY_STEP)
    }

    internal data class Smoothing(val attack: Float, val releaseStep: Float)

    private fun createAndAttach(
        container: ViewGroup,
        context: Context?,
        lottieView: View?,
    ): SpectrumView? {
        val ctx = context ?: container.context ?: return null
        val view = SpectrumView(ctx)
        val side = resolveIconSide(container, lottieView)
        val lp = FrameLayout.LayoutParams(side, side).apply {
            gravity = android.view.Gravity.CENTER
        }
        container.addView(view, lp)
        return view
    }

    private fun resolveIconSide(container: ViewGroup, lottieView: View?): Int {
        lottieView?.let { lv ->
            val w = lv.width
            val h = lv.height
            if (w > 0 && h > 0) return min(w, h)
            val lp = lv.layoutParams
            if (lp != null && lp.width > 0 && lp.height > 0) return min(lp.width, lp.height)
        }
        if (container.height > 0) return container.height
        val density = container.resources.displayMetrics.density
        return (30f * density).toInt().coerceAtLeast(1)
    }

    /**
     * 全局唯一的 Visualizer 采样源。
     *
     * `Visualizer(SYSTEM_AUDIO_SESSION)` 对系统混音输出是进程内单例资源；fake 层与 stable
     * 层各有一个 SpectrumView，必须共享这一个实例。用客户端引用计数管理 enable/release。
     */
    private object VisualizerSource {
        private val lock = Any()
        private var visualizer: Visualizer? = null
        private var clientCount = 0

        val targetLevels = FloatArray(MAX_BAR_COUNT)
        @Volatile var lastFftAt = 0L
        private val fftScratch = FloatArray(MAX_BAR_COUNT)
        private var gain = MIN_GAIN

        fun acquire(): Boolean {
            synchronized(lock) {
                clientCount++
                if (visualizer != null) return true
                visualizer = try {
                    Visualizer(SYSTEM_AUDIO_SESSION).also { instance ->
                        instance.captureSize = CAPTURE_SIZE
                        instance.setDataCaptureListener(
                            captureListener,
                            Visualizer.getMaxCaptureRate(),
                            false,
                            true,
                        )
                        instance.enabled = true
                    }
                } catch (t: Throwable) {
                    diag("visualizer.create.fail", "Visualizer create failed: ${t.javaClass.simpleName}: ${t.message}")
                    null
                }
                if (visualizer == null) {
                    // 创建失败：回滚计数，避免引用计数泄漏。
                    if (clientCount > 0) clientCount--
                    return false
                }
                diag("visualizer.created", "Visualizer created clients=$clientCount captureSize=$CAPTURE_SIZE rate=${Visualizer.getMaxCaptureRate()}")
                return true
            }
        }

        fun release() {
            synchronized(lock) {
                if (clientCount > 0) clientCount--
                if (clientCount == 0) {
                    val v = visualizer
                    visualizer = null
                    v?.let {
                        runCatching { it.enabled = false }
                        runCatching { it.release() }
                    }
                    diag("visualizer.released", "Visualizer released")
                }
            }
        }

        private val captureListener = object : Visualizer.OnDataCaptureListener {
            override fun onWaveFormDataCapture(
                visualizer: Visualizer,
                waveform: ByteArray,
                samplingRate: Int,
            ) {
            }

            override fun onFftDataCapture(
                visualizer: Visualizer,
                fft: ByteArray,
                samplingRate: Int,
            ) {
                val barCount = MusicIslandSpectrumHook.currentBarCount()
                val binCount = fft.size / 2
                if (binCount < barCount + 1) return
                val maxBin = (binCount * MAX_BIN_FRACTION).toInt()
                    .coerceIn(barCount + 1, binCount - 1)
                val scratch = fftScratch
                var frameMax = 0f
                for (bar in 0 until barCount) {
                    val start = freqBin(bar, maxBin, barCount)
                    val end = max(freqBin(bar + 1, maxBin, barCount), start + 1)
                    var peak = 0f
                    for (bin in start until end) {
                        val re = fft[2 * bin].toInt()
                        val im = fft[2 * bin + 1].toInt()
                        val magnitude = sqrt((re * re + im * im).toFloat())
                        if (magnitude > peak) peak = magnitude
                    }
                    scratch[bar] = peak
                    if (peak > frameMax) frameMax = peak
                }
                synchronized(lock) {
                    lastFftAt = System.nanoTime()
                    if (frameMax > 0f) {
                        val implied = GAIN_TARGET / frameMax
                        gain = (if (implied > gain) {
                            min(gain + GAIN_ATTACK, implied)
                        } else {
                            max(gain - GAIN_RELEASE, implied)
                        }).coerceIn(MIN_GAIN, MAX_GAIN)
                    }
                    for (bar in 0 until barCount) {
                        targetLevels[bar] = min(1f, scratch[bar] * gain)
                    }
                }
                diag("fft.capture", "fft captured size=${fft.size} sr=$samplingRate barCount=$barCount frameMax=$frameMax gain=$gain")
            }
        }

        /** 低频更密集：bar 0 覆盖最窄的低频段，最后一个 bar 覆盖最宽的中频段。 */
        private fun freqBin(index: Int, maxBin: Int, barCount: Int): Int {
            val fraction = index.toFloat() / barCount
            return (maxBin * fraction * fraction).toInt().coerceIn(1, maxBin - 1)
        }
    }

    /**
     * 对称竖条的实时频谱。每个实例从全局 [VisualizerSource] 读取数据并做本地平滑。
     */
    private class SpectrumView(context: Context) : View(context) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val barRect = RectF()

        // 每根条独立的渐变缓存：因为渐变要沿每根竖条的 top→bottom 铺，而每根条的高度
        // 逐帧变化，所以渐变只能在 draw 时按当前条高动态构造；用缓存避免每帧重复 new。
        private var barGradient: LinearGradient? = null
        private var barGradientBar = -1
        private var barGradientTop = -1f
        private var barGradientBottom = -1f
        private var barGradientColorTop = -1
        private var barGradientColorBottom = -1

        private val levels = FloatArray(MAX_BAR_COUNT)
        private val drawSnapshot = FloatArray(MAX_BAR_COUNT)
        private val levelsLock = Any()

        @Volatile private var realtime = false
        @Volatile private var attached = false
        private var acquired = false

        private val frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!attached) return
                stepLevels()
                invalidate()
                Choreographer.getInstance().postFrameCallback(this)
            }
        }

        fun setRealtime(value: Boolean) {
            realtime = value
            diag("setRealtime.$value", "setRealtime=$value attached=$attached")
            if (!value) {
                synchronized(levelsLock) { levels.fill(0f) }
            }
            syncSource()
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            attached = true
            diag("view.attach", "spectrum view attached size=${width}x${height}")
            syncSource()
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }

        override fun onDetachedFromWindow() {
            attached = false
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            syncSource()
            super.onDetachedFromWindow()
        }

        private fun syncSource() {
            val shouldRun = realtime && attached
            if (shouldRun && !acquired) {
                acquired = VisualizerSource.acquire()
                if (!acquired) {
                    diag("source.acquire.fail", "VisualizerSource acquire failed")
                }
            } else if (!shouldRun && acquired) {
                acquired = false
                VisualizerSource.release()
            }
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            // 渐变不再铺满整 View，改在 draw 里按每根条的高度动态构造（见 drawBar）。
            barGradient = null
            barGradientBar = -1
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val width = width.toFloat()
            val height = height.toFloat()
            if (width <= 0f || height <= 0f) {
                diag("draw.empty", "onDraw skipped width=$width height=$height")
                return
            }

            val barCount = MusicIslandSpectrumHook.currentBarCount()
            val centerY = height / 2f
            val maxHalfHeight = height / 2f * MAX_AMPLITUDE_FRACTION
            val minHalfHeight = height * MIN_BAR_HALF_FRACTION

            val totalBarsWidth = width * BARS_WIDTH_FRACTION
            val gap = totalBarsWidth / (2f * barCount + (barCount - 1))
            val barWidth = gap * 2f
            val startX = (width - totalBarsWidth) / 2f

            val snapshot = drawSnapshot
            synchronized(levelsLock) { System.arraycopy(levels, 0, snapshot, 0, barCount) }

            // 跟随官方封面动态色；或渐变模式使用自定义色。
            val (topColor, bottomColor) = MusicIslandSpectrumHook.currentColors()

            for (bar in 0 until barCount) {
                val level = snapshot[bar].coerceIn(0f, 1f)
                val half = minHalfHeight + (maxHalfHeight - minHalfHeight) * level
                val left = startX + bar * (barWidth + gap)
                val top = centerY - half
                val bottom = centerY + half
                barRect.set(left, top, left + barWidth, bottom)
                paint.shader = barShader(bar, top, bottom, topColor, bottomColor)
                canvas.drawRoundRect(barRect, barWidth / 2f, barWidth / 2f, paint)
            }
            diag("draw.ok", "onDraw width=$width height=$height barCount=$barCount barWidth=$barWidth topColor=#${Integer.toHexString(topColor)} bottomColor=#${Integer.toHexString(bottomColor)} levels=[${snapshot.take(barCount).joinToString { "%.2f".format(it) }}]")
        }

        /** 每根条独立渐变：沿该条当前 top→bottom 铺 TOP_COLOR→BOTTOM_COLOR，缓存复用。 */
        private fun barShader(bar: Int, top: Float, bottom: Float, topColor: Int, bottomColor: Int): LinearGradient? {
            val g = barGradient
            if (g != null && barGradientBar == bar &&
                barGradientTop == top && barGradientBottom == bottom &&
                barGradientColorTop == topColor && barGradientColorBottom == bottomColor
            ) {
                return g
            }
            val created = if (bottom > top) {
                LinearGradient(0f, top, 0f, bottom, topColor, bottomColor, Shader.TileMode.CLAMP)
            } else {
                null
            }
            barGradient = created
            barGradientBar = bar
            barGradientTop = top
            barGradientBottom = bottom
            barGradientColorTop = topColor
            barGradientColorBottom = bottomColor
            return created
        }

        private fun stepLevels() {
            synchronized(levelsLock) {
                val barCount = MusicIslandSpectrumHook.currentBarCount()
                val smoothing = MusicIslandSpectrumHook.currentSmoothing()
                val active = realtime &&
                    VisualizerSource.lastFftAt != 0L &&
                    (System.nanoTime() - VisualizerSource.lastFftAt) < SILENCE_NANOS
                for (bar in 0 until barCount) {
                    val target = if (active) VisualizerSource.targetLevels[bar] else 0f
                    if (target >= levels[bar]) {
                        // 上升：按 attack 系数向 target 插值（attack=1f 时瞬时跟随，等价旧实现）。
                        levels[bar] += (target - levels[bar]) * smoothing.attack
                    } else {
                        // 下降：按 releaseStep 绝对步长衰减（attack 越平滑，下降也相应变缓）。
                        levels[bar] = max(0f, levels[bar] - smoothing.releaseStep)
                    }
                }
                // 多余条的残留高度清零，避免条数减少时尾部残留。
                for (bar in barCount until MAX_BAR_COUNT) levels[bar] = 0f
                if (levels.take(barCount).any { it > 0.01f }) {
                    diag("levels.moving", "levels moving active=$active realtime=$realtime barCount=$barCount [${levels.take(barCount).joinToString { "%.2f".format(it) }}]")
                } else if (realtime && active) {
                    diag("levels.flat", "levels flat active=$active targets=[${VisualizerSource.targetLevels.take(barCount).joinToString { "%.2f".format(it) }}]")
                }
            }
        }
    }
}
