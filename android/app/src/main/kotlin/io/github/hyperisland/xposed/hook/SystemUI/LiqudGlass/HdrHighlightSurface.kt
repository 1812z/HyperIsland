package io.github.hyperisland.xposed.hook.SystemUI.LiqudGlass

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.RuntimeShader
import android.view.Choreographer
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import io.github.hyperisland.xposed.log
import io.github.hyperisland.xposed.logError
import io.github.hyperisland.xposed.logWarn
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.IdentityHashMap
import java.util.WeakHashMap
import kotlin.math.ceil

/** A per-drawable slot in the fixed, window-level F16 HDR highlight surface. */
internal class HdrHighlightSurface(
    context: Context,
    host: View,
) {
    private val context = context
    private val host = WeakReference(host)
    private var compositor: HdrHighlightCompositor? = null
    private var released = false

    fun update(state: LiquidGlassEdgeState) {
        if (released) return
        val hostView = host.get() ?: run {
            release()
            return
        }
        if (!hostView.isAttachedToWindow || (state.highlight <= 0f && state.shadow <= 0f)) {
            hide()
            return
        }
        val root = hostView.rootView as? ViewGroup ?: return
        var target = compositor
        if (target == null || !target.owns(root)) {
            target?.remove(this)
            target = HdrHighlightCompositor.obtain(context, root)
            compositor = target
        }
        target.update(this, hostView, state)
    }

    fun hide() {
        compositor?.hide(this)
    }

    fun release() {
        if (released) return
        released = true
        compositor?.remove(this)
        compositor = null
        host.clear()
    }
}

/**
 * Keeps one non-moving HDR Surface per island window. All drawable slots are
 * cleared and redrawn into one F16 buffer at the frame commit boundary.
 */
private class HdrHighlightCompositor(
    context: Context,
    root: ViewGroup,
) : SurfaceHolder.Callback {
    private val root = WeakReference(root)
    private val shader = runCatching { RuntimeShader(HDR_HIGHLIGHT_SHADER) }.getOrNull()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = this@HdrHighlightCompositor.shader
    }
    private val states = IdentityHashMap<HdrHighlightSurface, HighlightState>()
    private val hostLocation = IntArray(2)
    private val rootLocation = IntArray(2)
    private var surfaceReady = false
    private var rendererConfigurationAttempted = false
    private var rendererSetupLogged = false
    private var metadataSetupLogged = false
    private var requestLogged = false
    private var attachPosted = false
    private var released = false
    private var frameScheduled = false
    private var bufferWidth = 0
    private var bufferHeight = 0
    private var wideColorCanvasMethod: Method? = null
    private var wideColorCanvasMethodResolved = false
    private var postCommitMethod: Method? = null
    private var removeCommitMethod: Method? = null
    private var commitMethodsResolved = false
    private val drawFrame = Runnable {
        frameScheduled = false
        drawLatestStates()
    }
    private val fallbackFrame = Choreographer.FrameCallback { drawFrame.run() }
    private val idleRelease = Runnable { if (states.isEmpty()) release() }
    private val rootAttachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = Unit

        override fun onViewDetachedFromWindow(v: View) {
            release()
        }
    }

    private val view = SurfaceView(context).apply {
        setZOrderOnTop(true)
        holder.setFormat(PixelFormat.RGBA_F16)
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        visibility = View.INVISIBLE
        holder.addCallback(this@HdrHighlightCompositor)
        runCatching {
            javaClass.getMethod("setUseAlpha").invoke(this)
        }.onFailure { error ->
            logWarn("HyperIsland[LiquidGlassHDR] Surface alpha unavailable: ${error.message}")
        }
        runCatching {
            javaClass.getMethod(
                "setDesiredHdrHeadroom",
                Float::class.javaPrimitiveType!!,
            ).invoke(this, HDR_HEADROOM)
        }.onFailure { error ->
            logWarn("HyperIsland[LiquidGlassHDR] SurfaceView HDR unavailable: ${error.message}")
        }
    }

    init {
        root.addOnAttachStateChangeListener(rootAttachListener)
        if (shader == null) {
            logError("HyperIsland[LiquidGlassHDR] RuntimeShader creation failed")
        }
    }

    fun owns(candidate: ViewGroup): Boolean = !released && root.get() === candidate

    fun update(slot: HdrHighlightSurface, host: View, state: LiquidGlassEdgeState) {
        if (released || shader == null) return
        view.removeCallbacks(idleRelease)
        val rootView = root.get() ?: run {
            release()
            return
        }
        ensureAttached(rootView)
        host.getLocationOnScreen(hostLocation)
        if (view.parent != null) {
            view.getLocationOnScreen(rootLocation)
        } else {
            rootView.getLocationOnScreen(rootLocation)
        }
        val offsetX = (hostLocation[0] - rootLocation[0]).toFloat()
        val offsetY = (hostLocation[1] - rootLocation[1]).toFloat()
        val bounds = state.bounds
        states[slot] = HighlightState(
            left = bounds.left + offsetX,
            top = bounds.top + offsetY,
            width = ceil(bounds.width()).coerceAtLeast(1f),
            height = ceil(bounds.height()).coerceAtLeast(1f),
            radius = state.cornerRadius,
            lightX = state.lightX,
            lightY = state.lightY,
            edgeWidth = state.edgeWidth,
            intensity = state.highlight,
            oppositeIntensity = state.shadow,
        )
        ensureBufferSize(rootView)
        if (!requestLogged) {
            requestLogged = true
            log("HyperIsland[LiquidGlassHDR] fixed highlight compositor requested")
        }
        scheduleFrame()
    }

    fun hide(slot: HdrHighlightSurface) {
        if (states.remove(slot) == null) return
        if (view.parent == null || !surfaceReady) {
            release()
        } else {
            scheduleFrame()
            view.removeCallbacks(idleRelease)
            view.postDelayed(idleRelease, IDLE_RELEASE_DELAY_MS)
        }
    }

    fun remove(slot: HdrHighlightSurface) {
        hide(slot)
    }

    private fun ensureAttached(rootView: ViewGroup) {
        if (view.parent != null || attachPosted || released) return
        attachPosted = true
        rootView.post {
            attachPosted = false
            if (released || view.parent != null || !rootView.isAttachedToWindow) return@post
            runCatching {
                rootView.addView(view, ViewGroup.LayoutParams(1, 1))
                log("HyperIsland[LiquidGlassHDR] fixed overlay attached to window root")
                ensureBufferSize(rootView)
                view.visibility = View.VISIBLE
                scheduleFrame()
                rootView.postInvalidateOnAnimation()
            }.onFailure { error ->
                logError("HyperIsland[LiquidGlassHDR] overlay attach failed: ${error.message}")
            }
        }
    }

    private fun ensureBufferSize(rootView: ViewGroup) {
        val contentRight = states.values.maxOfOrNull { ceil(it.left + it.width).toInt() } ?: 1
        val contentBottom = states.values.maxOfOrNull { ceil(it.top + it.height).toInt() } ?: 1
        val nextWidth = roundBufferDimension(maxOf(rootView.width, contentRight, 1))
        val nextHeight = roundBufferDimension(maxOf(contentBottom, 1))
        bufferWidth = maxOf(bufferWidth, nextWidth)
        bufferHeight = maxOf(bufferHeight, nextHeight)
        if (view.parent != null &&
            (view.layoutParams.width != bufferWidth || view.layoutParams.height != bufferHeight)
        ) {
            view.layoutParams = view.layoutParams.apply {
                width = bufferWidth
                height = bufferHeight
            }
        }
    }

    private fun scheduleFrame() {
        if (frameScheduled || released || view.parent == null) return
        frameScheduled = true
        val choreographer = Choreographer.getInstance()
        if (!postCommitCallback(choreographer)) {
            choreographer.postFrameCallback(fallbackFrame)
        }
    }

    private fun postCommitCallback(choreographer: Choreographer): Boolean {
        resolveCommitMethods(choreographer)
        return runCatching {
            val method = postCommitMethod ?: return false
            method.invoke(choreographer, CALLBACK_COMMIT, drawFrame, this)
            true
        }.getOrDefault(false)
    }

    private fun resolveCommitMethods(choreographer: Choreographer) {
        if (commitMethodsResolved) return
        commitMethodsResolved = true
        postCommitMethod = runCatching {
            choreographer.javaClass.getMethod(
                "postCallback",
                Int::class.javaPrimitiveType!!,
                Runnable::class.java,
                Any::class.java,
            )
        }.getOrNull()
        removeCommitMethod = runCatching {
            choreographer.javaClass.getMethod(
                "removeCallbacks",
                Int::class.javaPrimitiveType!!,
                Runnable::class.java,
                Any::class.java,
            )
        }.getOrNull()
    }

    private fun drawLatestStates() {
        if (!surfaceReady || released) return
        val runtimeShader = shader ?: return
        val surface = view.holder.surface
        if (!surface.isValid) return
        val canvas = lockCanvas(surface) ?: return
        try {
            if (!rendererConfigurationAttempted) {
                rendererConfigurationAttempted = true
                configureHdrRenderer(surface)
            }
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            states.values.forEach { state ->
                runtimeShader.setFloatUniform("uSize", state.width, state.height)
                runtimeShader.setFloatUniform("uOrigin", state.left, state.top)
                runtimeShader.setFloatUniform("uCornerRadius", state.radius)
                runtimeShader.setFloatUniform("uLightDir", state.lightX, state.lightY)
                runtimeShader.setFloatUniform("uEdgeWidth", state.edgeWidth)
                runtimeShader.setFloatUniform("uIntensity", state.intensity)
                runtimeShader.setFloatUniform("uOppositeIntensity", state.oppositeIntensity)
                runtimeShader.setFloatUniform("uHdrHeadroom", HDR_HEADROOM)
                canvas.drawRect(
                    state.left,
                    state.top,
                    state.left + state.width,
                    state.top + state.height,
                    paint,
                )
            }
        } finally {
            runCatching { view.holder.unlockCanvasAndPost(canvas) }
        }
    }

    private fun lockCanvas(surface: Any): Canvas? {
        if (!wideColorCanvasMethodResolved) {
            wideColorCanvasMethodResolved = true
            wideColorCanvasMethod = runCatching {
                surface.javaClass.getDeclaredMethod("lockHardwareWideColorGamutCanvas").apply {
                    isAccessible = true
                }
            }.getOrNull()
        }
        return runCatching {
            wideColorCanvasMethod?.invoke(surface) as? Canvas
                ?: view.holder.lockHardwareCanvas()
        }.onFailure { error ->
            logError("HyperIsland[LiquidGlassHDR] canvas lock failed: ${error.message}")
        }.getOrNull()
    }

    private fun configureHdrRenderer(surface: Any): Boolean {
        return runCatching {
            val hwuiContext = findField(surface.javaClass, "mHwuiContext")
                ?.get(surface) ?: error("Surface HWUI context unavailable")
            val renderer = findField(hwuiContext.javaClass, "mHardwareRenderer")
                ?.get(hwuiContext) ?: error("Surface HardwareRenderer unavailable")
            val setColorMode = renderer.javaClass.methods.firstOrNull {
                it.name == "setColorMode" && it.parameterCount == 1
            } ?: error("renderer HDR color mode unavailable")
            setColorMode.invoke(renderer, HDR_COLOR_MODE)
            val setTargetRatio = renderer.javaClass.methods.firstOrNull {
                it.name == "setTargetHdrSdrRatio" && it.parameterCount == 1
            } ?: error("renderer HDR target ratio unavailable")
            setTargetRatio.invoke(renderer, HDR_HEADROOM)
            if (!rendererSetupLogged) {
                rendererSetupLogged = true
                log("HyperIsland[LiquidGlassHDR] F16 renderer HDR ratio=$HDR_HEADROOM")
            }
            true
        }.onFailure { error ->
            logError("HyperIsland[LiquidGlassHDR] renderer setup failed: ${error.message}")
        }.getOrDefault(false)
    }

    private fun applyHdrMetadata() {
        runCatching {
            val surfaceControl = findField(view.javaClass, "mBlastSurfaceControl")
                ?.get(view) ?: error("BLAST SurfaceControl unavailable")
            val isValid = surfaceControl.javaClass.getMethod("isValid")
                .invoke(surfaceControl) as? Boolean ?: false
            if (!isValid) return@runCatching
            val transactionClass = Class.forName("android.view.SurfaceControl\$Transaction")
            val transaction = transactionClass.getConstructor().newInstance()
            try {
                val setExtendedRange = transactionClass.methods.firstOrNull {
                    it.name == "setExtendedRangeBrightness" && it.parameterCount == 3
                } ?: error("extended-range brightness unavailable")
                setExtendedRange.invoke(
                    transaction,
                    surfaceControl,
                    HDR_HEADROOM,
                    HDR_HEADROOM,
                )
                transactionClass.getMethod("apply").invoke(transaction)
                if (!metadataSetupLogged) {
                    metadataSetupLogged = true
                    log("HyperIsland[LiquidGlassHDR] BLAST metadata ratio=$HDR_HEADROOM")
                }
            } finally {
                runCatching { transactionClass.getMethod("close").invoke(transaction) }
            }
        }.onFailure { error ->
            logError("HyperIsland[LiquidGlassHDR] metadata failed: ${error.message}")
        }
    }

    private fun release() {
        if (released) return
        released = true
        frameScheduled = false
        view.removeCallbacks(idleRelease)
        val choreographer = Choreographer.getInstance()
        runCatching {
            removeCommitMethod?.invoke(choreographer, CALLBACK_COMMIT, drawFrame, this)
        }
        choreographer.removeFrameCallback(fallbackFrame)
        states.clear()
        surfaceReady = false
        view.holder.removeCallback(this)
        val rootView = root.get()
        rootView?.removeOnAttachStateChangeListener(rootAttachListener)
        unregister(rootView, this)
        val parent = view.parent as? ViewGroup
        if (parent != null) {
            // Never mutate DynamicIslandWindowView's child array during dispatchDraw.
            parent.post { if (view.parent === parent) parent.removeView(view) }
        }
        paint.shader = null
        root.clear()
    }

    private fun findField(clazz: Class<*>, name: String): java.lang.reflect.Field? {
        var current: Class<*>? = clazz
        while (current != null) {
            runCatching {
                return current.getDeclaredField(name).apply { isAccessible = true }
            }
            current = current.superclass
        }
        return null
    }

    private fun roundBufferDimension(value: Int): Int {
        return ((value + BUFFER_ALLOCATION_STEP - 1) / BUFFER_ALLOCATION_STEP) *
            BUFFER_ALLOCATION_STEP
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        rendererConfigurationAttempted = false
        applyHdrMetadata()
        log("HyperIsland[LiquidGlassHDR] fixed highlight surface ready headroom=$HDR_HEADROOM")
        scheduleFrame()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        surfaceReady = true
        rendererConfigurationAttempted = false
        applyHdrMetadata()
        scheduleFrame()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        rendererConfigurationAttempted = false
    }

    private data class HighlightState(
        val left: Float,
        val top: Float,
        val width: Float,
        val height: Float,
        val radius: Float,
        val lightX: Float,
        val lightY: Float,
        val edgeWidth: Float,
        val intensity: Float,
        val oppositeIntensity: Float,
    )

    companion object {
        const val HDR_HEADROOM = 4.99f
        const val HDR_COLOR_MODE = 2
        const val CALLBACK_COMMIT = 4
        const val BUFFER_ALLOCATION_STEP = 64
        const val IDLE_RELEASE_DELAY_MS = 500L
        val compositors = WeakHashMap<ViewGroup, HdrHighlightCompositor>()

        fun obtain(context: Context, root: ViewGroup): HdrHighlightCompositor {
            return compositors[root]?.takeIf { it.owns(root) }
                ?: HdrHighlightCompositor(context, root).also { compositors[root] = it }
        }

        fun unregister(root: ViewGroup?, compositor: HdrHighlightCompositor) {
            if (root != null && compositors[root] === compositor) compositors.remove(root)
        }

        val HDR_HIGHLIGHT_SHADER = """
            uniform float2 uSize;
            uniform float2 uOrigin;
            uniform float uCornerRadius;
            uniform float2 uLightDir;
            uniform float uEdgeWidth;
            uniform float uIntensity;
            uniform float uOppositeIntensity;
            uniform float uHdrHeadroom;

            $LIQUID_GLASS_EDGE_GEOMETRY

            half4 main(float2 fragCoord) {
                float2 halfSize = uSize * 0.5;
                float2 p = fragCoord - uOrigin - halfSize;
                float3 geometry = edgeGeometry(
                    p, halfSize, uCornerRadius, uEdgeWidth, uLightDir
                );
                if (geometry.x > 0.0 ||
                    (uIntensity <= 0.0 && uOppositeIntensity <= 0.0)) {
                    return half4(0.0);
                }

                float edge = geometry.z;
                float primaryFacing = max(geometry.y, 0.0);
                float oppositeFacing = max(-geometry.y, 0.0);
                float primaryStrength = clamp(uIntensity, 0.0, 1.0);
                float oppositeStrength = clamp(uOppositeIntensity, 0.0, 1.0);
                float primaryCoverage = pow(primaryFacing * edge, 2.0) * primaryStrength;
                float oppositeCoverage = pow(oppositeFacing * edge, 2.0) * oppositeStrength;
                float highlightCoverage = primaryCoverage + oppositeCoverage;
                float peakStrength = max(primaryStrength, oppositeStrength);
                float peakLuminance = mix(1.0, uHdrHeadroom, peakStrength);
                return half4(
                    half3(peakLuminance * highlightCoverage),
                    half(highlightCoverage)
                );
            }
        """.trimIndent()
    }
}
