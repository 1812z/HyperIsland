package io.github.hyperisland.xposed.hook.SystemUI.extensions.spectrum

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.os.PowerManager
import android.os.SystemClock
import android.view.Choreographer
import android.view.View
import android.widget.FrameLayout
import io.github.hyperisland.BuildConfig
import io.github.hyperisland.xposed.ConfigManager
import java.lang.ref.WeakReference
import java.lang.reflect.Method

internal class SpectrumView(
    context: Context,
    private val coverColors: () -> CoverColors,
) : View(context) {
    data class CoverColors(val top: Int, val bottom: Int)

    private val motion = SpectrumMotion()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val visibleRect = Rect()
    private val power = context.getSystemService(PowerManager::class.java)
    private var original: WeakReference<View>? = null
    private var originalVisibility: Int? = null
    private var cancelOriginal: Method? = null
    private var playOriginal: Method? = null
    private var allowed = true
    private var wave = false
    private var wiredIcon = false
    private var barCount = 5
    private var colors: CoverColors? = null
    private var artwork: Bitmap? = null
    private val artworkMatrix = Matrix()
    private var framePending = false
    private var lastStep = 0L
    private var silenced = false
    private var fallback = false
    private var hostVisible = false

    var wantsAudio = false
        private set

    private val stale = Runnable {
        if (wantsAudio && !silenced) {
            if (BuildConfig.NATIVE_TAP_BUILD.isEmpty()) {
                fallback = true
                restoreOriginal(animate = true)
                invalidate()
            }
            motion.frame(SILENCE, SystemClock.uptimeMillis())
            postFrame()
        }
    }

    // Alpha animation on the host can change without a View visibility callback.
    private val checkHost = object : Runnable {
        override fun run() {
            updateStyle()
            updateDemand()
            if (shouldCheckHost()) postDelayed(this, 500)
        }
    }

    private val animate = Choreographer.FrameCallback { time ->
        framePending = false
        val elapsed = if (lastStep == 0L) 16f else ((time - lastStep) / 1_000_000f).coerceIn(0f, 100f)
        lastStep = time
        val moving = motion.advance(elapsed)
        invalidate()
        if (moving) postFrame() else lastStep = 0L
    }

    fun bind(view: View, realtime: Boolean, wired: Boolean) {
        if (original?.get() !== view) {
            restoreOriginal()
            original = WeakReference(view)
            cancelOriginal = view.animationMethod("cancelAnimation")
            playOriginal = view.animationMethod("playAnimation")
            if (parent != null) layoutParams = FrameLayout.LayoutParams(view.layoutParams as FrameLayout.LayoutParams)
        }
        wave = realtime
        wiredIcon = wired
        refreshPlayback()
        suppressOriginal()
        updateStyle()
    }

    fun allow(value: Boolean) {
        allowed = value
        if (value) suppressOriginal()
        refreshHost()
    }

    fun refreshPlayback() {
        val eligible = !wiredIcon || MusicSpectrum.wired
        if (eligible) suppressOriginal() else restoreOriginal()
        visibility = if (eligible) VISIBLE else INVISIBLE
        if (motion.playing(MusicSpectrum.isPlaying(wave, wiredIcon))) postFrame()
        updateDemand()
    }

    fun suppressOriginal() {
        if (!hostVisible || fallback || (wiredIcon && !MusicSpectrum.wired)) return
        val host = original?.get() ?: return
        if (host.visibility != INVISIBLE) {
            runCatching { cancelOriginal?.invoke(host) }
            originalVisibility = host.visibility
            host.visibility = INVISIBLE
        }
    }

    private fun restoreOriginal(animate: Boolean = false) {
        val host = original?.get()
        val visibility = originalVisibility
        if (host?.visibility == INVISIBLE && visibility != null) {
            host.visibility = visibility
            if (animate && visibility == VISIBLE) runCatching { playOriginal?.invoke(host) }
        }
        originalVisibility = null
    }

    private fun View.animationMethod(name: String): Method? =
        runCatching { javaClass.getMethod(name) }.getOrNull()

    fun dispose() {
        restoreOriginal()
        (parent as? FrameLayout)?.removeView(this)
    }

    fun frame(levels: FloatArray) {
        if (silenced) return
        if (fallback) {
            fallback = false
            suppressOriginal()
            invalidate()
        }
        scheduleStale()
        motion.frame(levels, SystemClock.uptimeMillis())
        postFrame()
    }

    fun silenced(value: Boolean) {
        silenced = value
        if (value) {
            removeCallbacks(stale)
            fallback = false
            suppressOriginal()
            motion.frame(SILENCE, SystemClock.uptimeMillis())
            postFrame()
        } else {
            scheduleStale()
        }
    }

    private fun scheduleStale() {
        removeCallbacks(stale)
        if (wantsAudio && !silenced) postDelayed(stale, 800)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        MusicSpectrum.attach(this)
        refreshHost()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(checkHost)
        removeCallbacks(stale)
        cancelFrame()
        hostVisible = false
        wantsAudio = false
        restoreOriginal()
        MusicSpectrum.detach(this)
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isAttachedToWindow) refreshHost()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateArtworkBounds()
        if (isAttachedToWindow) updateDemand()
    }

    private fun refreshHost() {
        removeCallbacks(checkHost)
        if (shouldCheckHost()) {
            checkHost.run()
        } else {
            updateDemand()
        }
    }

    private fun shouldCheckHost() = isAttachedToWindow && allowed && isShown && windowVisibility == VISIBLE

    private fun renderable(): Boolean {
        if (!shouldCheckHost() || power?.isInteractive == false || !getGlobalVisibleRect(visibleRect)) return false
        var node: View? = this
        while (node != null) {
            if (node.alpha <= 0f) return false
            node = node.parent as? View
        }
        return true
    }

    private fun updateDemand() {
        val visible = renderable()
        val visibilityChanged = visible != hostVisible
        hostVisible = visible
        if (visibilityChanged) invalidate()
        val next = visible && motion.isPlaying
        if (next != wantsAudio) {
            wantsAudio = next
            fallback = false
            removeCallbacks(stale)
            MusicSpectrum.scheduleDemand()
            if (next) MusicSpectrum.seed(this)
            else if (motion.isPlaying) motion.frame(SILENCE, SystemClock.uptimeMillis())
            invalidate()
        }
        if (!visible) {
            cancelFrame()
            motion.hidden()
            restoreOriginal()
        } else {
            if (visibilityChanged || !next) suppressOriginal()
            // A pause can arrive during the fake/real visibility handoff.
            if (visibilityChanged && !motion.isPlaying) postFrame()
        }
    }

    private fun postFrame() {
        if (framePending || !hostVisible) return
        framePending = true
        Choreographer.getInstance().postFrameCallback(animate)
    }

    private fun cancelFrame() {
        if (framePending) Choreographer.getInstance().removeFrameCallback(animate)
        framePending = false
        lastStep = 0
    }

    private fun updateStyle() {
        val style = Style.read()
        val field = ArtworkPalette.bitmap(style.mode)
        if (field != null) {
            if (barCount == style.count && artwork === field) return
            barCount = style.count
            if (artwork !== field) {
                artwork = field
                colors = null
                paint.shader = BitmapShader(field, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                    setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
                }
                updateArtworkBounds()
            }
            invalidate()
            return
        }
        val followsCover = style.mode != "gradient"
        val cover = if (followsCover) coverColors() else style.gradient
        val next = CoverColors(
            if (followsCover && cover.top == -1) TOP else cover.top,
            if (followsCover && cover.bottom == -1) BOTTOM else cover.bottom,
        )
        if (barCount == style.count && colors == next) return
        barCount = style.count
        artwork = null
        if (colors != next) {
            colors = next
            paint.shader = LinearGradient(0f, 0f, 0f, 1f, next.top, next.bottom, Shader.TileMode.CLAMP)
        }
        invalidate()
    }

    private fun updateArtworkBounds() {
        val field = artwork ?: return
        if (width == 0 || height == 0) return
        val stripWidth = width * STRIP_WIDTH_FRACTION
        artworkMatrix.setScale(stripWidth / field.width, height.toFloat() / field.height)
        artworkMatrix.postTranslate((width - stripWidth) / 2f, 0f)
        paint.shader?.setLocalMatrix(artworkMatrix)
    }

    override fun onDraw(canvas: Canvas) {
        if (!hostVisible || fallback || width == 0 || height == 0) return
        val center = height / 2f
        val stripWidth = width * STRIP_WIDTH_FRACTION
        val gap = stripWidth / (3 * barCount - 1)
        val barWidth = 2f * gap
        val left = (width - stripWidth) / 2f
        for (bar in 0 until barCount) {
            val level = displayLevel(bar)
            val half = height * (0.16f + 0.29f * level)
            val fullHeight = 2 * half
            val x = left + bar * (barWidth + gap)
            val y = center - half
            if (artwork != null) {
                // The bars reveal a shared cover field without stretching it as their heights change.
                canvas.drawRoundRect(x, y, x + barWidth, y + fullHeight, gap, gap, paint)
                continue
            }
            val save = canvas.save()
            canvas.translate(x, y)
            canvas.scale(1f, fullHeight)
            canvas.drawRoundRect(0f, 0f, barWidth, 1f, gap, gap / fullHeight, paint)
            canvas.restoreToCount(save)
        }
    }

    private fun displayLevel(bar: Int): Float {
        val levels = motion.heights
        if (barCount <= levels.size) {
            val start = bar * levels.size / barCount
            val end = (bar + 1) * levels.size / barCount
            var peak = levels[start]
            for (index in start + 1 until end) peak = maxOf(peak, levels[index])
            return peak
        }
        // Keep bass and treble at the ends. The upstream DSP always receives and processes six bands.
        val position = bar.toFloat() * levels.lastIndex / (barCount - 1)
        val low = position.toInt()
        val high = (low + 1).coerceAtMost(levels.lastIndex)
        return levels[low] + (levels[high] - levels[low]) * (position - low)
    }

    private data class Style(val count: Int, val mode: String, val gradient: CoverColors) {
        companion object {
            private var cached = Style(5, "follow_cover", CoverColors(TOP, BOTTOM))
            private var expiresAt = 0L

            fun read(): Style {
                val now = SystemClock.uptimeMillis()
                if (now < expiresAt) return cached
                val mode = ConfigManager.getString("pref_music_island_spectrum_color_mode", "follow_cover")
                cached = Style(
                    ConfigManager.getInt("pref_music_island_spectrum_bar_count", 5).coerceIn(4, 8),
                    mode,
                    if (mode != "gradient") cached.gradient else CoverColors(
                        readColor("pref_music_island_spectrum_gradient_start", TOP),
                        readColor("pref_music_island_spectrum_gradient_end", BOTTOM),
                    ),
                )
                expiresAt = now + 500
                return cached
            }

            private fun readColor(key: String, default: Int): Int = runCatching {
                Color.parseColor(ConfigManager.getString(key, "").trim())
            }.getOrDefault(default)
        }
    }

    companion object {
        private const val STRIP_WIDTH_FRACTION = 0.72f
        private const val TOP = 0xFF57F2FF.toInt()
        private const val BOTTOM = 0xFFF5E6FF.toInt()
        private val SILENCE = FloatArray(SpectrumDsp.BARS)
    }
}
