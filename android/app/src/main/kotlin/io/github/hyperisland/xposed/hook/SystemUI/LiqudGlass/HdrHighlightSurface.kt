package io.github.hyperisland.xposed.hook.SystemUI.LiqudGlass

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.RuntimeShader
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import io.github.hyperisland.xposed.logWarn
import java.lang.ref.WeakReference
import kotlin.math.ceil

/** Owns the transparent F16 Surface used only for luminance above the SDR white point. */
internal class HdrHighlightSurface(
    context: Context,
    host: View,
) : SurfaceHolder.Callback {
    private val host = WeakReference(host)
    private val shader = runCatching { RuntimeShader(HDR_HIGHLIGHT_SHADER) }.getOrNull()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = this@HdrHighlightSurface.shader
    }
    private val hostLocation = IntArray(2)
    private val rootLocation = IntArray(2)
    private var surfaceView: SurfaceView? = null
    private var surfaceReady = false
    private var rendererConfigured = false
    private var rendererSetupLogged = false
    private var metadataSetupLogged = false
    private var attachPosted = false
    private var released = false
    private var width = 0
    private var height = 0
    private var radius = 0f
    private var lightX = 0f
    private var lightY = 0f
    private var edgeWidth = 1f
    private var intensity = 0f

    private val view = SurfaceView(context).apply {
        setZOrderOnTop(true)
        holder.setFormat(PixelFormat.RGBA_F16)
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        visibility = View.INVISIBLE
        holder.addCallback(this@HdrHighlightSurface)
        runCatching { javaClass.getMethod("setUseAlpha").invoke(this) }
        runCatching {
            javaClass.getMethod(
                "setDesiredHdrHeadroom",
                Float::class.javaPrimitiveType!!,
            ).invoke(this, HDR_HEADROOM)
        }.onFailure { error ->
            logWarn("HyperIsland[LiquidGlassHDR] SurfaceView HDR unavailable: ${error.message}")
        }
    }

    fun update(state: LiquidGlassEdgeState) {
        if (released || shader == null) return
        val hostView = host.get() ?: return
        ensureAttached(hostView)
        val overlay = surfaceView ?: return
        val root = overlay.parent as? View ?: return
        val bounds = state.bounds
        hostView.getLocationOnScreen(hostLocation)
        root.getLocationOnScreen(rootLocation)
        val nextWidth = ceil(bounds.width()).toInt().coerceAtLeast(1)
        val nextHeight = ceil(bounds.height()).toInt().coerceAtLeast(1)
        if (width != nextWidth || height != nextHeight) {
            width = nextWidth
            height = nextHeight
            overlay.layoutParams = overlay.layoutParams.apply {
                this.width = nextWidth
                this.height = nextHeight
            }
        }
        overlay.x = hostLocation[0] - rootLocation[0] + bounds.left
        overlay.y = hostLocation[1] - rootLocation[1] + bounds.top
        radius = state.cornerRadius
        lightX = state.lightX
        lightY = state.lightY
        edgeWidth = state.edgeWidth
        intensity = state.highlight
        overlay.visibility = if (intensity > 0f) View.VISIBLE else View.INVISIBLE
        drawFrame()
    }

    fun hide() {
        surfaceView?.visibility = View.INVISIBLE
    }

    fun release() {
        released = true
        surfaceReady = false
        view.holder.removeCallback(this)
        val parent = view.parent as? ViewGroup
        if (parent != null) {
            parent.post { if (view.parent === parent) parent.removeView(view) }
        }
        surfaceView = null
        host.clear()
    }

    private fun ensureAttached(hostView: View) {
        if (surfaceView != null || attachPosted || released) return
        val root = hostView.rootView as? ViewGroup ?: return
        attachPosted = true
        root.post {
            attachPosted = false
            if (released || view.parent != null) return@post
            runCatching {
                root.addView(view, ViewGroup.LayoutParams(1, 1))
                surfaceView = view
                hostView.postInvalidateOnAnimation()
            }.onFailure { error ->
                logWarn("HyperIsland[LiquidGlassHDR] overlay attach failed: ${error.message}")
            }
        }
    }

    private fun drawFrame() {
        if (!surfaceReady || width <= 0 || height <= 0 || intensity <= 0f) return
        val runtimeShader = shader ?: return
        val surface = view.holder.surface
        if (!surface.isValid) return
        val canvas = runCatching {
            surface.javaClass.getDeclaredMethod("lockHardwareWideColorGamutCanvas").apply {
                isAccessible = true
            }.invoke(surface) as Canvas
        }.getOrElse {
            view.holder.lockHardwareCanvas()
        }
        try {
            if (!rendererConfigured) rendererConfigured = configureHdrRenderer(surface)
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            runtimeShader.setFloatUniform("uSize", width.toFloat(), height.toFloat())
            runtimeShader.setFloatUniform("uCornerRadius", radius)
            runtimeShader.setFloatUniform("uLightDir", lightX, lightY)
            runtimeShader.setFloatUniform("uEdgeWidth", edgeWidth)
            runtimeShader.setFloatUniform("uIntensity", intensity)
            runtimeShader.setFloatUniform("uHdrHeadroom", HDR_HEADROOM)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        } finally {
            runCatching { view.holder.unlockCanvasAndPost(canvas) }
        }
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
                logWarn("HyperIsland[LiquidGlassHDR] F16 renderer HDR ratio=$HDR_HEADROOM")
            }
            true
        }.onFailure { error ->
            logWarn("HyperIsland[LiquidGlassHDR] renderer setup failed: ${error.message}")
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
                    logWarn("HyperIsland[LiquidGlassHDR] BLAST metadata ratio=$HDR_HEADROOM")
                }
            } finally {
                runCatching { transactionClass.getMethod("close").invoke(transaction) }
            }
        }.onFailure { error ->
            logWarn("HyperIsland[LiquidGlassHDR] metadata failed: ${error.message}")
        }
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

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        rendererConfigured = false
        applyHdrMetadata()
        logWarn("HyperIsland[LiquidGlassHDR] highlight surface ready headroom=$HDR_HEADROOM")
        drawFrame()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        surfaceReady = true
        rendererConfigured = false
        applyHdrMetadata()
        drawFrame()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        rendererConfigured = false
    }

    private companion object {
        const val HDR_HEADROOM = 4.99f
        const val HDR_COLOR_MODE = 2

        val HDR_HIGHLIGHT_SHADER = """
            uniform float2 uSize;
            uniform float uCornerRadius;
            uniform float2 uLightDir;
            uniform float uEdgeWidth;
            uniform float uIntensity;
            uniform float uHdrHeadroom;

            $LIQUID_GLASS_EDGE_GEOMETRY

            half4 main(float2 fragCoord) {
                float2 halfSize = uSize * 0.5;
                float2 p = fragCoord - halfSize;
                float3 geometry = edgeGeometry(
                    p, halfSize, uCornerRadius, uEdgeWidth, uLightDir
                );
                if (geometry.x > 0.0 || uIntensity <= 0.0) return half4(0.0);

                float facing = max(geometry.y, 0.0);
                float edge = geometry.z;
                float coverage = pow(facing * edge, 2.0);
                float strength = clamp(uIntensity, 0.0, 1.0);
                float peakLuminance = mix(1.0, uHdrHeadroom, strength);
                float luminance = mix(1.0, peakLuminance, coverage);
                return half4(half3(luminance * coverage), half(coverage));
            }
        """.trimIndent()
    }
}
