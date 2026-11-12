package io.github.hyperisland.xposed.hook.SystemUI.extensions.spectrum

import android.content.Context
import android.media.session.MediaSession
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.Collections
import java.util.WeakHashMap

/** One capture for the real island and its transition copies. State is owned by the main looper. */
internal object MusicSpectrum {
    private const val MAX_CLIENTS = 32
    private val main = Handler(Looper.getMainLooper())
    private val views = Collections.newSetFromMap(WeakHashMap<SpectrumView, Boolean>())
    private val silence = FloatArray(SpectrumDsp.BARS)
    private val reported = mutableSetOf<String>()
    private val updateDemand = Runnable { demand() }
    private var engine: SpectrumCapture? = null
    private var mediaKey: String? = null
    private var mediaPackage: String? = null
    private var mediaUser = -1
    private var sourcePlaying: Boolean? = null
    private var local = true
    private var clientCount = 0
    private var levels = silence
    private var lastFrame = 0L
    private var silenced = false
    var wired = false
        private set

    var reporter: SpectrumCapture.Reporter = SpectrumCapture.Reporter { _, _ -> }

    fun reportOnce(key: String, failure: Throwable) {
        if (reported.add(key)) reporter.report(key, failure)
    }

    fun media(
        context: Context,
        key: String?,
        packageName: String,
        user: Int,
        playing: Boolean?,
        route: String,
        token: MediaSession.Token?,
    ) {
        val resolved = MediaRoute.resolve(context, route, token)
        wired = resolved == "wired"
        local = MediaRoute.isLocal(resolved)
        sourcePlaying = playing
        mediaKey = key
        if (mediaPackage != packageName || mediaUser != user) {
            mediaPackage = packageName
            mediaUser = user
            engine?.media(packageName, user)
        }
        views.toList().forEach { it.refreshPlayback() }
        scheduleDemand()
    }

    fun removed(key: String) {
        if (key != mediaKey) return
        mediaKey = null
        sourcePlaying = false
        views.toList().forEach { it.refreshPlayback() }
        scheduleDemand()
    }

    fun isPlaying(wave: Boolean, wiredIcon: Boolean): Boolean =
        local && if (wiredIcon) wired && sourcePlaying == true else wave && sourcePlaying != false

    fun attach(view: SpectrumView) {
        if (views.size >= MAX_CLIENTS) return
        views.add(view)
        scheduleDemand()
    }

    fun detach(view: SpectrumView) {
        views.remove(view)
        scheduleDemand()
    }

    fun scheduleDemand() {
        main.removeCallbacks(updateDemand)
        main.post(updateDemand)
    }

    fun seed(view: SpectrumView) {
        view.silenced(silenced)
        if (lastFrame > 0 && SystemClock.uptimeMillis() - lastFrame < 350) view.frame(levels)
    }

    private fun demand() {
        val visible = views.filter { it.wantsAudio }
        if (visible.size == clientCount) return
        clientCount = visible.size
        if (visible.isNotEmpty() && engine == null) {
            val context = visible.first().context
            engine = SpectrumCapture.create(reporter, main, object : SpectrumCapture.Sink {
                override fun frame(data: FloatArray, time: Long, healthy: Boolean) {
                    if (!healthy || clientCount == 0 || silenced) return
                    lastFrame = time
                    levels = data
                    views.toList().forEach { if (it.wantsAudio) it.frame(data) }
                }

                override fun silenced(value: Boolean) {
                    silenced = value
                    if (value) lastFrame = 0L
                    views.toList().forEach { if (it.wantsAudio) it.silenced(value) }
                }
            }).also { capture ->
                capture.audio(context.applicationContext ?: context)
                mediaPackage?.let { capture.media(it, mediaUser) }
            }
        }
        if (clientCount == 0) {
            levels = silence
            lastFrame = 0L
        }
        engine?.demand(clientCount > 0, clientCount)
    }
}
