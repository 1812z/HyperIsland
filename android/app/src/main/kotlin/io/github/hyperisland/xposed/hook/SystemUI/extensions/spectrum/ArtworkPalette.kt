package io.github.hyperisland.xposed.hook.SystemUI.extensions.spectrum

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import java.lang.ref.WeakReference

/** Decode the current media cover once for both the real island and its transition copies. */
internal object ArtworkPalette {
    const val HYPEROS = "hyperos"
    const val IOS = "ios"

    private const val SAMPLE_SIZE = 48
    private val main = Handler(Looper.getMainLooper())
    private val worker by lazy {
        val thread = HandlerThread("HyperIslandArtworkPalette").apply { start() }
        Handler(thread.looper)
    }
    private val fallback by lazy { fieldBitmap(CoverColors.fallback()) }

    private var track: String? = null
    private var icon: WeakReference<Icon>? = null
    private var generation = 0L
    private var hyperOs: Bitmap? = null
    private var ios: Bitmap? = null

    fun update(context: Context, track: String?, artwork: Icon?) {
        val artworkTrack = if (artwork != null) track else null
        if (this.track == artworkTrack && icon?.get() === artwork) return
        this.track = artworkTrack
        icon = artwork?.let(::WeakReference)
        hyperOs = null
        ios = null
        val ticket = ++generation
        worker.removeCallbacksAndMessages(null)
        if (artwork == null) return

        val appContext = context.applicationContext ?: context
        worker.post {
            val pixels = runCatching { readPixels(appContext, artwork) }.getOrNull() ?: return@post
            val hyperOsField = fieldBitmap(CoverColors.hyperOsField(pixels))
            val iosField = fieldBitmap(CoverColors.field(pixels, SAMPLE_SIZE, SAMPLE_SIZE))
            main.post {
                if (ticket == generation) {
                    hyperOs = hyperOsField
                    ios = iosField
                }
            }
        }
    }

    fun bitmap(mode: String): Bitmap? = when (mode) {
        HYPEROS -> hyperOs ?: fallback
        IOS -> ios ?: fallback
        else -> null
    }

    private fun fieldBitmap(colors: IntArray): Bitmap =
        Bitmap.createBitmap(colors, CoverColors.SIZE, CoverColors.SIZE, Bitmap.Config.ARGB_8888)

    private fun readPixels(context: Context, icon: Icon): IntArray? {
        val drawable = icon.loadDrawable(context) ?: return null
        val source = (drawable as? BitmapDrawable)?.bitmap
        val pixels = IntArray(SAMPLE_SIZE * SAMPLE_SIZE)
        if (source?.config == Bitmap.Config.HARDWARE) {
            val scaled = Bitmap.createScaledBitmap(source, SAMPLE_SIZE, SAMPLE_SIZE, true)
            try {
                val readable = if (scaled.config == Bitmap.Config.HARDWARE) {
                    scaled.copy(Bitmap.Config.ARGB_8888, false) ?: return null
                } else {
                    scaled
                }
                try {
                    readable.getPixels(pixels, 0, SAMPLE_SIZE, 0, 0, SAMPLE_SIZE, SAMPLE_SIZE)
                } finally {
                    if (readable !== scaled) readable.recycle()
                }
            } finally {
                if (scaled !== source) scaled.recycle()
            }
        } else {
            val sample = Bitmap.createBitmap(SAMPLE_SIZE, SAMPLE_SIZE, Bitmap.Config.ARGB_8888)
            val bounds = Rect(drawable.bounds)
            try {
                drawable.setBounds(0, 0, SAMPLE_SIZE, SAMPLE_SIZE)
                drawable.draw(Canvas(sample))
                sample.getPixels(pixels, 0, SAMPLE_SIZE, 0, 0, SAMPLE_SIZE, SAMPLE_SIZE)
            } finally {
                drawable.bounds = bounds
                sample.recycle()
            }
        }
        return pixels
    }
}
