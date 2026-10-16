package io.github.hyperisland.xposed.hook.SystemUI.extensions

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.media.audiofx.Visualizer
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.widget.FrameLayout
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.hyperisland.xposed.logWarn as xlogWarn
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

// ── 顶层诊断（有界：每个 key 只打首次）───────────────────────────────────────
// 注意：message 必须是惰性 lambda。这些 diag 都位于帧回调 / FFT 回调的热路径上，
// 若用普通字符串参数，Kotlin 会在每次调用前就构造好字符串（含 joinToString + "%.2f".format），
// 即使 key 早已记录、日志根本不会输出，也会白白产生大量分配。
private const val TAG = "HyperIsland[MusicSpectrum]"
private val diagSeen = Collections.synchronizedSet(mutableSetOf<String>())
private inline fun diag(key: String, message: () -> String) {
    if (diagSeen.add(key)) xlogWarn(TAG, message())
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

/** 电平低于该值即视为静止：用于决定帧循环是否可以停下来（静止后不再 invalidate）。 */
private const val LEVEL_EPSILON = 0.01f

/**
 * 电平变化小于该值就不重绘。
 *
 * 这是播放态最关键的一条：FFT 采样只有 20Hz，而 vsync 有 120Hz，中间那些帧算出来的
 * 目标值根本没变。旧实现每帧无条件 invalidate()，等于把「同一份数据」在一个被模糊 /
 * 发光 / 圆角裁剪包裹的岛图层里重画 6 遍。按阈值过滤后，重绘次数直接对齐采样率。
 * 阈值换算成像素远小于 1px（条高量程约 9px，0.005 × 9 ≈ 0.05px），肉眼不可见。
 */
private const val REDRAW_DELTA_EPSILON = 0.005f

/**
 * 120Hz 基准帧间隔。平滑参数（attack / releaseStep）是在 120Hz 下调出来的，
 * 其它帧率或经过节流后必须按实际 dt 等比换算，否则 60Hz 设备衰减速度只有一半。
 */
private const val REFERENCE_FRAME_NANOS = 8_333_333L

/**
 * 目标动画帧率。FFT 采样本身只有 20Hz（见 `Visualizer.getMaxCaptureRate()`，单位是
 * milliHertz），60fps 已经远超数据更新率，再高只是在重画同一份数据。
 *
 * 实现方式：仍然接收每个 vsync（动画必须对齐 vsync，否则会撕裂），但被跳过的 vsync
 * 只做一次时间比较就返回，成本可忽略。
 */
private const val TARGET_FPS = 60

/** 留 1ms 余量，避免 vsync 周期与阈值正好相等时因取整而掉到 30fps。 */
private const val MIN_STEP_INTERVAL_NANOS = 1_000_000_000L / TARGET_FPS - 1_000_000L

/** 单帧 dt 上限，避免主线程卡顿后一次补跳过大。 */
private const val MAX_STEP_DELTA_NANOS = 100_000_000L

/**
 * 样式快照刷新间隔。配置读取会走 RemotePreferences 并伴随字段表排序分配，
 * 必须远离「每帧 / 每回调」路径，所以统一节流到 500ms，视觉上无感知。
 */
private const val STYLE_TTL_NANOS = 500_000_000L

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
 *
 * 资源占用约束（帧循环与采样都必须可停）：
 * - 帧循环只在「实时模式且已附着」时运行；暂停态（pic=musicPause，realtime=false）完全不排帧。
 * - 即使处于实时模式，只要超过 [SILENCE_NANOS] 没有采到非静音 FFT，电平衰减归零后也会停帧，
 *   等采样线程重新采到声音再唤醒（[VisualizerSource.wakeClients]）。
 * - 每帧 / 每回调都不得直读 ConfigManager，统一走 [StyleCache]。
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

        diag("bind.spectrum") { "spectrum bind pic=$pic type=$type container=${container.width}x${container.height} lottie=${lottieView?.width}x${lottieView?.height}" }

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

    /** 一帧绘制 / 采样所需的全部样式参数，低频刷新一次。 */
    internal data class Style(
        val barCount: Int,
        val smoothing: Smoothing,
        val topColor: Int,
        val bottomColor: Int,
    )

    /**
     * 样式快照缓存。
     *
     * 条数 / 平滑 / 渐变色原本在「每帧 × 每个 View」和「每次 FFT 回调」里直接读 ConfigManager：
     * 每次 getInt/getString 都要遍历 TOAST_FIELDS / NOTIFICATION_FIELDS / CHANNEL_FIELDS 并做
     * `sortedByDescending` 分配，再走 RemotePreferences。而帧回调与 Visualizer 回调都跑在
     * SystemUI 主线程上，两个 SpectrumView 叠加后每秒上千次这种读取，正是暂停态 50% CPU 的主因。
     * 这里统一按 [STYLE_TTL_NANOS] 节流，配置变更最多 500ms 后生效。
     */
    private object StyleCache {
        private val lock = Any()

        @Volatile private var cached = Style(
            barCount = DEFAULT_BAR_COUNT,
            smoothing = Smoothing(attack = 1f, releaseStep = DECAY_STEP),
            topColor = TOP_COLOR,
            bottomColor = BOTTOM_COLOR,
        )

        @Volatile private var lastRead = 0L

        fun get(): Style {
            val snapshot = cached
            if (System.nanoTime() - lastRead < STYLE_TTL_NANOS) return snapshot
            synchronized(lock) {
                val now = System.nanoTime()
                if (now - lastRead < STYLE_TTL_NANOS) return cached
                val (top, bottom) = MusicIslandSpectrumHook.currentColors()
                val fresh = Style(
                    barCount = MusicIslandSpectrumHook.currentBarCount(),
                    smoothing = MusicIslandSpectrumHook.currentSmoothing(),
                    topColor = top,
                    bottomColor = bottom,
                )
                cached = fresh
                lastRead = now
                return fresh
            }
        }
    }

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
     * 层各有一个 SpectrumView，必须共享这一个实例。用客户端集合（弱引用）管理 enable/release，
     * 集合为空时立刻关闭并释放 Visualizer —— 这是暂停态「不再采样」的关键。
     */
    private object VisualizerSource {
        private val lock = Any()
        private val clients = Collections.newSetFromMap(WeakHashMap<SpectrumView, Boolean>())
        private var visualizer: Visualizer? = null
        private var gain = MIN_GAIN
        private val fftScratch = FloatArray(MAX_BAR_COUNT)

        /**
         * 最近一次「非静音」FFT 的时间戳。
         *
         * 只在采到真实声音时更新：全零帧不会刷新它，于是 [SILENCE_NANOS] 之后自然判定为无声，
         * 帧循环得以停下来。旧实现每个回调都刷新，导致播放态静音段也永远在跑帧。
         */
        @Volatile private var lastAudioAt = 0L

        /** 目标电平快照。整体替换（而不是原地改写）发布，读方无锁也能拿到一致快照。 */
        @Volatile private var levelSnapshot = FloatArray(MAX_BAR_COUNT)

        fun levels(): FloatArray = levelSnapshot

        fun isAudioActive(): Boolean {
            val at = lastAudioAt
            return at != 0L && System.nanoTime() - at < SILENCE_NANOS
        }

        fun acquire(view: SpectrumView): Boolean {
            synchronized(lock) {
                if (visualizer != null) {
                    clients.add(view)
                    return true
                }
                val created = try {
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
                    diag("visualizer.create.fail") { "Visualizer create failed: ${t.javaClass.simpleName}: ${t.message}" }
                    null
                }
                if (created == null) return false
                visualizer = created
                clients.add(view)
                diag("visualizer.created") { "Visualizer created clients=${clients.size} captureSize=$CAPTURE_SIZE rate=${Visualizer.getMaxCaptureRate()}" }
                return true
            }
        }

        fun release(view: SpectrumView) {
            synchronized(lock) {
                clients.remove(view)
                if (clients.isNotEmpty()) return
                val v = visualizer
                visualizer = null
                lastAudioAt = 0L
                v?.let {
                    runCatching { it.enabled = false }
                    runCatching { it.release() }
                }
                diag("visualizer.released") { "Visualizer released" }
            }
        }

        /** 重新采到声音时唤醒处于休眠（已停帧）的客户端。 */
        private fun wakeClients() {
            synchronized(lock) {
                if (clients.isEmpty()) return
                for (client in clients) client.wakeFromAudio()
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
                // 静音快速路径：全零帧直接返回。既省掉逐 bin 开方，也不刷新 lastAudioAt，
                // 让 SILENCE_NANOS 之后自动判定无声并停掉帧循环。
                var silent = true
                for (index in fft.indices) {
                    if (fft[index].toInt() != 0) {
                        silent = false
                        break
                    }
                }
                if (silent) return

                val style = StyleCache.get()
                val barCount = style.barCount
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
                if (frameMax <= 0f) return

                val snapshot = FloatArray(MAX_BAR_COUNT)
                synchronized(lock) {
                    val implied = GAIN_TARGET / frameMax
                    gain = (if (implied > gain) {
                        min(gain + GAIN_ATTACK, implied)
                    } else {
                        max(gain - GAIN_RELEASE, implied)
                    }).coerceIn(MIN_GAIN, MAX_GAIN)
                    for (bar in 0 until barCount) {
                        snapshot[bar] = min(1f, scratch[bar] * gain)
                    }
                    lastAudioAt = System.nanoTime()
                    levelSnapshot = snapshot
                }
                diag("fft.capture") { "fft captured size=${fft.size} sr=$samplingRate barCount=$barCount frameMax=$frameMax gain=$gain" }
                wakeClients()
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
     *
     * 帧循环遵循「按需运行」：只有 realtime 且已附着才排帧；一旦电平静止（或非实时态）就
     * 停止排帧并只画最后一帧，避免暂停 / 静音时仍然 120Hz 空转。
     */
    private class SpectrumView(context: Context) : View(context) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val barRect = RectF()

        // 每根条独立的渐变缓存：因为渐变要沿每根竖条的 top→bottom 铺，而每根条的高度
        // 逐帧变化，所以渐变只能在 draw 时按当前条高动态构造。按 bar 分别缓存几何 + 颜色，
        // 避免旧实现只缓存一根条、5 根条每帧反复 new LinearGradient。
        private val gradCache = arrayOfNulls<LinearGradient>(MAX_BAR_COUNT)
        private val gradTop = FloatArray(MAX_BAR_COUNT)
        private val gradBottom = FloatArray(MAX_BAR_COUNT)
        private val gradColorTop = IntArray(MAX_BAR_COUNT)
        private val gradColorBottom = IntArray(MAX_BAR_COUNT)

        private val levels = FloatArray(MAX_BAR_COUNT)
        private val drawSnapshot = FloatArray(MAX_BAR_COUNT)
        private val levelsLock = Any()

        /** 复用同一个 Rect，避免每帧做可见性判断时分配对象。 */
        private val visibleRect = Rect()

        @Volatile private var realtime = false
        @Volatile private var attached = false
        private var acquired = false

        /** 帧回调是否已入队；只在 realtime && attached 时为 true。 */
        @Volatile private var framePending = false

        /** 上一次推进电平的时间（vsync 时间戳），用于节流与 dt 换算。 */
        private var lastStepNanos = 0L

        /** 上一次 stepLevels 是否产生了值得重绘的变化。 */
        private var levelsDirty = false

        private val frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                framePending = false
                if (!attached || !realtime) return
                // 节流判断放在最前面：被跳过的 vsync 只做一次时间比较，不做可见性遍历。
                val sinceLast = if (lastStepNanos == 0L) {
                    Long.MAX_VALUE
                } else {
                    frameTimeNanos - lastStepNanos
                }
                if (sinceLast < MIN_STEP_INTERVAL_NANOS) {
                    postFrame()
                    return
                }
                lastStepNanos = frameTimeNanos
                // 不可见时（fake 过渡层已隐藏、整岛淡出到 alpha=0、或被祖先裁剪掉）
                // 不推进也不绘制，只保留循环等待重新可见；否则隐藏的 SpectrumView 会一直空烧。
                // 保留循环而不是停掉，是为了让「重新可见」能自愈（alpha 变化没有回调可监听）。
                if (!isRenderable()) {
                    postFrame()
                    return
                }
                val dtNanos = if (sinceLast == Long.MAX_VALUE) REFERENCE_FRAME_NANOS else sinceLast
                val active = VisualizerSource.isAudioActive()
                val keepRunning = stepLevels(
                    active = active,
                    dtNanos = dtNanos.coerceAtMost(MAX_STEP_DELTA_NANOS),
                )
                // 只有电平真的变了才重绘：避免为同一份采样数据反复刷整个岛图层。
                if (levelsDirty) invalidate()
                if (keepRunning) {
                    postFrame()
                } else {
                    // 停帧：清掉计时，下次被唤醒时按基准 dt 立刻推进一帧。
                    lastStepNanos = 0L
                }
            }
        }

        /** 是否真的会显示在屏幕上：不可见 / 祖先透明 / 被祖先裁剪掉时没必要推进与绘制。 */
        private fun isRenderable(): Boolean {
            if (!isShown || alpha == 0f) return false
            // getGlobalVisibleRect 会连带计算与各级祖先裁剪区的交集，
            // 能识别「可见但被裁到 0 面积」的隐藏层（isShown 判断不出来）。
            if (!getGlobalVisibleRect(visibleRect) ||
                visibleRect.width() <= 0 || visibleRect.height() <= 0
            ) {
                return false
            }
            var node: ViewParent? = parent
            var depth = 0
            while (depth < 8) {
                val view = node as? View ?: return true
                if (view.alpha == 0f) return false
                node = view.parent
                depth++
            }
            return true
        }

        /** 排帧：非实时态 / 未附着 / 已排队时不重复排。 */
        private fun postFrame() {
            if (framePending || !attached || !realtime) return
            framePending = true
            val posted = runCatching {
                Choreographer.getInstance().postFrameCallback(frameCallback)
            }.isSuccess
            if (!posted) framePending = false
        }

        private fun cancelFrame() {
            framePending = false
            runCatching { Choreographer.getInstance().removeFrameCallback(frameCallback) }
        }

        /** 采样线程重新采到声音时调用：若当前处于休眠（已停帧）则重启帧循环。 */
        fun wakeFromAudio() {
            if (attached && realtime) postFrame()
        }

        fun setRealtime(value: Boolean) {
            val changed = realtime != value
            realtime = value
            if (changed) {
                diag("setRealtime.$value") { "setRealtime=$value attached=$attached" }
            }
            if (changed) lastStepNanos = 0L
            if (!value && changed) {
                synchronized(levelsLock) { levels.fill(0f) }
            }
            syncSource()
            if (value) {
                postFrame()
            } else if (changed) {
                // 暂停态：只补画一帧静态频谱，之后不再排帧。
                invalidate()
            }
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            attached = true
            lastStepNanos = 0L
            diag("view.attach") { "spectrum view attached size=${width}x${height}" }
            syncSource()
            postFrame()
        }

        override fun onDetachedFromWindow() {
            attached = false
            cancelFrame()
            syncSource()
            super.onDetachedFromWindow()
        }

        private fun syncSource() {
            val shouldRun = realtime && attached
            if (shouldRun && !acquired) {
                acquired = VisualizerSource.acquire(this)
                if (!acquired) {
                    diag("source.acquire.fail") { "VisualizerSource acquire failed" }
                }
            } else if (!shouldRun && acquired) {
                acquired = false
                VisualizerSource.release(this)
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val width = width.toFloat()
            val height = height.toFloat()
            if (width <= 0f || height <= 0f) {
                diag("draw.empty") { "onDraw skipped width=$width height=$height" }
                return
            }

            val style = StyleCache.get()
            val barCount = style.barCount
            val centerY = height / 2f
            val maxHalfHeight = height / 2f * MAX_AMPLITUDE_FRACTION
            val minHalfHeight = height * MIN_BAR_HALF_FRACTION

            val totalBarsWidth = width * BARS_WIDTH_FRACTION
            val gap = totalBarsWidth / (2f * barCount + (barCount - 1))
            val barWidth = gap * 2f
            val startX = (width - totalBarsWidth) / 2f

            val snapshot = drawSnapshot
            synchronized(levelsLock) { System.arraycopy(levels, 0, snapshot, 0, barCount) }

            val topColor = style.topColor
            val bottomColor = style.bottomColor

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
            diag("draw.ok") { "onDraw width=$width height=$height barCount=$barCount barWidth=$barWidth topColor=#${Integer.toHexString(topColor)} bottomColor=#${Integer.toHexString(bottomColor)} levels=[${snapshot.take(barCount).joinToString { "%.2f".format(it) }}]" }
        }

        /** 每根条独立渐变：沿该条当前 top→bottom 铺 TOP_COLOR→BOTTOM_COLOR，按 bar 缓存复用。 */
        private fun barShader(bar: Int, top: Float, bottom: Float, topColor: Int, bottomColor: Int): LinearGradient? {
            if (gradColorTop[bar] == topColor && gradColorBottom[bar] == bottomColor &&
                gradTop[bar] == top && gradBottom[bar] == bottom
            ) {
                return gradCache[bar]
            }
            val created = if (bottom > top) {
                LinearGradient(0f, top, 0f, bottom, topColor, bottomColor, Shader.TileMode.CLAMP)
            } else {
                null
            }
            gradCache[bar] = created
            gradTop[bar] = top
            gradBottom[bar] = bottom
            gradColorTop[bar] = topColor
            gradColorBottom[bar] = bottomColor
            return created
        }

        /**
         * 推进一帧电平平滑。
         *
         * @param active 采样是否活跃（决定目标值是真实电平还是 0）
         * @param dtNanos 距上次推进的时长，用于把平滑参数从 [REFERENCE_FRAME_NANOS] 基准
         *                换算到当前帧率，保证不同刷新率 / 节流后手感一致。
         * @return 是否还需要下一帧（采样仍活跃，或电平尚未完全归零）。
         */
        private fun stepLevels(active: Boolean, dtNanos: Long): Boolean {
            val style = StyleCache.get()
            val barCount = style.barCount
            val smoothing = style.smoothing
            val scale = dtNanos.toFloat() / REFERENCE_FRAME_NANOS
            // attack 是混合系数而非线性步长，按 (1-a)^scale 换算才能保证「每秒收敛量」一致。
            val attack = if (smoothing.attack >= 1f) {
                1f
            } else {
                1f - (1f - smoothing.attack).pow(scale)
            }
            val releaseStep = smoothing.releaseStep * scale
            // 目标数组恒定非空（整体替换发布）；非活跃时直接按 0 处理，避免逐条装箱。
            val targets = VisualizerSource.levels()
            var changed = false
            var settled = true
            synchronized(levelsLock) {
                for (bar in 0 until barCount) {
                    val target = if (active) targets[bar] else 0f
                    val current = levels[bar]
                    val next = if (target >= current) {
                        // 上升：按 attack 系数向 target 插值（attack=1f 时瞬时跟随，等价旧实现）。
                        current + (target - current) * attack
                    } else {
                        // 下降：按 releaseStep 绝对步长衰减（attack 越平滑，下降也相应变缓）。
                        max(0f, current - releaseStep)
                    }
                    if (abs(next - current) > REDRAW_DELTA_EPSILON) changed = true
                    if (next > LEVEL_EPSILON) settled = false
                    levels[bar] = next
                }
                // 多余条的残留高度清零，避免条数减少时尾部残留。
                for (bar in barCount until MAX_BAR_COUNT) {
                    if (levels[bar] != 0f) {
                        levels[bar] = 0f
                        changed = true
                    }
                }
            }
            levelsDirty = changed
            return active || !settled
        }
    }
}
