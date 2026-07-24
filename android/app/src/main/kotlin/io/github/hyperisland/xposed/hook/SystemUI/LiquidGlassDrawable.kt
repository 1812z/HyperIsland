package io.github.hyperisland.xposed.hook.SystemUI

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.HardwareRenderer
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.view.View
import io.github.hyperisland.xposed.logWarn
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.ArrayDeque
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

internal data class LiquidGlassConfig(
    val enabled: Boolean,
    val edgeWidth: Float,
    val refraction: Float,
    val highlight: Float,
    val shadow: Float,
    val lightDirection: Int,
    val dispersion: Float,
    val gyroscope: Boolean,
    val trueRefraction: Boolean,
    val captureFps: Int,
    val captureScale: Float,
) {
    companion object {
        fun disabled() = LiquidGlassConfig(
            false, 0.16f, 0.16f, 0.42f, 0.14f, 243, 0.18f, false, false, 20, 0.3f,
        )
    }
}

/** Draws directional rim lighting over the native backdrop blur. */
internal class LiquidGlassDrawable(
    context: Context,
    host: View,
    private val child: Drawable,
    private val inset: Int,
    initialConfig: LiquidGlassConfig,
) : Drawable(), Drawable.Callback {

    private val context = context
    private val hostView = WeakReference(host)
    private var contentView: WeakReference<View>? = null
    private val glassRect = RectF()
    private val rimShader = runCatching { RuntimeShader(RIM_SHADER) }.getOrNull()
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = rimShader }
    private val refractionShader = runCatching { RuntimeShader(REFRACTION_SHADER) }.getOrNull()
    private val refractionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = refractionShader }
    private val screenCapture = RefractiveScreenCapture(
        host,
        ::applySystemBlur,
        ::onScreenCaptured,
    )
    private var cornerRadius = 0f
    private var lightX = DEFAULT_LIGHT_X
    private var lightY = DEFAULT_LIGHT_Y
    private var drawableAlpha = 1f
    private var currentFrame: Bitmap? = null
    private val retiredFrames = ArrayDeque<Bitmap>()
    private val frameReleaseHandler = Handler(Looper.getMainLooper())
    private val blurResourceLock = Any()
    private val blurNode = RenderNode("HyperIslandSystemBlur")
    private var blurEffectRadius = -1f
    private var blurEffect: RenderEffect? = null
    @Volatile
    private var backgroundBlurRadius = 0f
    private var blendColor = Color.TRANSPARENT
    private var loggedFirstDraw = false
    private var config = initialConfig
    private var tiltAttached = false

    init {
        child.callback = this
        screenCapture.updateSettings(config.captureFps, config.captureScale)
        applyFixedLightDirection()
        updateTiltRegistration()
        updateCaptureState()
    }

    fun setCornerRadius(radius: Float) {
        cornerRadius = radius.coerceAtLeast(0f)
        updateCaptureState()
        invalidateSelf()
    }

    fun setContentView(view: View) {
        if (contentView?.get() !== view) contentView = WeakReference(view)
        updateCaptureState()
    }

    fun setBackgroundBlurRadius(radius: Float) {
        val value = radius.coerceAtLeast(0f)
        if (backgroundBlurRadius == value) return
        backgroundBlurRadius = value
        invalidateSelf()
        hostView.get()?.postInvalidateOnAnimation()
    }

    fun setBlendColor(color: Int) {
        if (blendColor == color) return
        blendColor = color
        invalidateSelf()
        hostView.get()?.postInvalidateOnAnimation()
    }

    fun updateConfig(value: LiquidGlassConfig) {
        if (config == value) return
        config = value
        screenCapture.updateSettings(config.captureFps, config.captureScale)
        applyFixedLightDirection()
        updateTiltRegistration()
        updateCaptureState()
        invalidateSelf()
        hostView.get()?.postInvalidateOnAnimation()
    }

    private fun applyFixedLightDirection() {
        val angle = config.lightDirection * PI.toFloat() / 180f
        setLightDirection(cos(angle), sin(angle))
    }

    private fun updateTiltRegistration() {
        val shouldAttach = config.enabled && config.gyroscope
        if (shouldAttach == tiltAttached) return
        tiltAttached = shouldAttach
        if (shouldAttach) {
            runCatching { LiquidGlassTiltController.attach(context, this) }
        } else {
            runCatching { LiquidGlassTiltController.detach(this) }
        }
    }

    private fun updateCaptureState() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            hostView.get()?.post { updateCaptureState() }
            return
        }
        val canCapture = shouldCaptureNow()
        screenCapture.updateViewSnapshot(canCapture)
        if (canCapture) {
            screenCapture.start()
        } else {
            stopCapture()
        }
    }

    private fun stopCapture() {
        screenCapture.stop()
        currentFrame?.let(::retireFrameLater)
        currentFrame = null
        while (retiredFrames.isNotEmpty()) retireFrameLater(retiredFrames.removeFirst())
    }

    private fun shouldCaptureNow(): Boolean {
        if (!config.enabled || !config.trueRefraction || refractionShader == null) return false
        if (!isVisible || drawableAlpha <= 0f || bounds.isEmpty) return false
        val host = hostView.get() ?: return false
        val content = contentView?.get() ?: return false
        return isActuallyVisible(host) && isActuallyVisible(content)
    }

    private fun isActuallyVisible(view: View): Boolean {
        if (!view.isAttachedToWindow || !view.isShown || view.windowVisibility != View.VISIBLE ||
            view.width <= 0 || view.height <= 0 || !view.getGlobalVisibleRect(Rect())
        ) return false
        var current: View? = view
        while (current != null) {
            if (current.alpha <= 0.01f) return false
            current = current.parent as? View
        }
        return true
    }

    private fun onScreenCaptured(
        bitmap: Bitmap,
        scaleX: Float,
        scaleY: Float,
        cropX: Float,
        cropY: Float,
    ) {
        val runtimeShader = refractionShader ?: return
        val previousFrame = currentFrame
        currentFrame = bitmap
        runtimeShader.setInputShader(
            "uContent",
            BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP),
        )
        runtimeShader.setFloatUniform("uCaptureScale", scaleX, scaleY)
        runtimeShader.setFloatUniform("uCaptureOrigin", cropX, cropY)
        if (previousFrame !== bitmap && previousFrame?.isRecycled == false) {
            retiredFrames.addLast(previousFrame)
            if (retiredFrames.size > RETAINED_FRAME_COUNT) {
                retiredFrames.removeFirst().recycle()
            }
        }
        hostView.get()?.postInvalidateOnAnimation()
    }

    private fun retireFrameLater(bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        frameReleaseHandler.postDelayed(
            { if (!bitmap.isRecycled) bitmap.recycle() },
            STOPPED_FRAME_RELEASE_DELAY_MS,
        )
    }

    @SuppressLint("BlockedPrivateApi")
    private fun applySystemBlur(bitmap: Bitmap): Bitmap {
        val radius = trueRefractionBlurRadius()
        if (radius <= 0f || bitmap.isRecycled) return bitmap
        val blurred = runCatching {
            synchronized(blurResourceLock) {
                blurNode.setPosition(0, 0, bitmap.width, bitmap.height)
                val recordingCanvas = blurNode.beginRecording(bitmap.width, bitmap.height)
                recordingCanvas.drawBitmap(bitmap, 0f, 0f, null)
                blurNode.endRecording()
                if (blurEffect == null || blurEffectRadius != radius) {
                    blurEffectRadius = radius
                    blurEffect = RenderEffect.createBlurEffect(
                        radius,
                        radius,
                        Shader.TileMode.CLAMP,
                    )
                    blurNode.setRenderEffect(blurEffect)
                }
                try {
                    createHardwareBitmapMethod.invoke(
                        null,
                        blurNode,
                        bitmap.width,
                        bitmap.height,
                    ) as? Bitmap
                        ?: error("HardwareRenderer.createHardwareBitmap returned no bitmap")
                } finally {
                    blurNode.discardDisplayList()
                }
            }
        }.onFailure { error ->
            logWarn("HyperIsland[TrueRefraction] system blur failed: ${error.message}")
        }.getOrNull() ?: return bitmap
        if (blurred !== bitmap && !bitmap.isRecycled) bitmap.recycle()
        return blurred
    }

    /** Updated by the shared pose sensor listener while this drawable is active. */
    fun setLightDirection(x: Float, y: Float) {
        val length = hypot(x, y).coerceAtLeast(0.0001f)
        lightX = x / length
        lightY = y / length
        invalidateSelf()
        hostView.get()?.postInvalidateOnAnimation()
    }

    fun applyTilt(x: Float, y: Float) {
        val angle = config.lightDirection * PI.toFloat() / 180f
        setLightDirection(
            cos(angle) - x * TILT_STRENGTH,
            sin(angle) + y * TILT_STRENGTH,
        )
    }

    override fun onBoundsChange(bounds: Rect) {
        child.bounds = bounds
        if (updateGlassRect(bounds)) updateCaptureRegion()
        updateCaptureState()
    }

    override fun draw(canvas: Canvas) {
        updateCaptureState()
        if (!loggedFirstDraw) {
            loggedFirstDraw = true
            logWarn(
                "HyperIsland[LiquidGlassTilt] first draw shader=${rimShader != null} " +
                    "bounds=$bounds hardware=${canvas.isHardwareAccelerated}",
            )
        }
        if (!updateGlassRect(bounds)) {
            child.draw(canvas)
            return
        }
        updateCaptureRegion()

        val width = glassRect.width()
        val height = glassRect.height()
        val radius = cornerRadius.coerceAtMost(minOf(width, height) / 2f)
        val hasTrueRefraction = config.enabled && config.trueRefraction && screenCapture.hasFrame &&
            canvas.isHardwareAccelerated && refractionShader != null
        val drewTrueRefraction = hasTrueRefraction && runCatching {
            drawTrueRefraction(canvas, width, height, radius)
        }.onFailure { error ->
            logWarn("HyperIsland[TrueRefraction] render failed: ${error.message}")
        }.getOrDefault(false)
        if (!drewTrueRefraction) {
            // Native backdrop blur is only needed while capture is unavailable or
            // true-refraction rendering fails; otherwise it would be fully covered.
            child.draw(canvas)
        }
        if (config.enabled) {
            drawDirectionalRim(canvas, width, height, radius)
        }
    }

    private fun drawTrueRefraction(
        canvas: Canvas,
        width: Float,
        height: Float,
        radius: Float,
    ): Boolean {
        if (!config.trueRefraction || !screenCapture.hasFrame || !canvas.isHardwareAccelerated) {
            return false
        }
        val runtimeShader = refractionShader ?: return false
        currentFrame?.takeIf { !it.isRecycled } ?: return false
        runtimeShader.setFloatUniform("uOrigin", glassRect.left, glassRect.top)
        runtimeShader.setFloatUniform("uScreenOrigin", screenCapture.screenX, screenCapture.screenY)
        runtimeShader.setFloatUniform("uSize", width, height)
        runtimeShader.setFloatUniform("uCornerRadius", radius)
        runtimeShader.setFloatUniform(
            "uRefractionHeight",
            (minOf(width, height) * config.edgeWidth * 1.25f).coerceAtLeast(1f),
        )
        runtimeShader.setFloatUniform(
            "uRefractionAmount",
            -minOf(width, height) * config.refraction * 2f,
        )
        runtimeShader.setFloatUniform("uDepthEffect", 1f)
        runtimeShader.setFloatUniform("uDispersion", config.dispersion * 0.12f)
        runtimeShader.setFloatUniform(
            "uBlendColor",
            Color.red(blendColor) / 255f,
            Color.green(blendColor) / 255f,
            Color.blue(blendColor) / 255f,
            Color.alpha(blendColor) / 255f,
        )
        refractionPaint.alpha = (drawableAlpha * 255f).toInt().coerceIn(0, 255)
        canvas.drawRect(glassRect, refractionPaint)
        return true
    }

    private fun updateCaptureRegion() {
        screenCapture.updateCaptureRegion(
            glassRect,
            trueRefractionBlurRadius() / config.captureScale.coerceAtLeast(0.1f),
            minOf(glassRect.width(), glassRect.height()) * config.refraction * 2f,
        )
    }

    private fun trueRefractionBlurRadius(): Float {
        // True refraction exposes a dedicated 0..20 range in the UI. Keep this
        // value in capture-texture pixels so capture quality does not weaken it.
        return backgroundBlurRadius.coerceIn(0f, 20f)
    }

    private fun drawDirectionalRim(canvas: Canvas, width: Float, height: Float, radius: Float) {
        val runtimeShader = rimShader ?: return
        runtimeShader.setFloatUniform("uOrigin", glassRect.left, glassRect.top)
        runtimeShader.setFloatUniform("uSize", width, height)
        runtimeShader.setFloatUniform("uCornerRadius", radius)
        runtimeShader.setFloatUniform("uLightDir", lightX, lightY)
        runtimeShader.setFloatUniform("uEdgeWidth", (height * config.edgeWidth).coerceAtLeast(1f))
        runtimeShader.setFloatUniform(
            "uRefraction",
            if (config.trueRefraction && screenCapture.hasFrame) 0f else config.refraction,
        )
        runtimeShader.setFloatUniform("uEdgeAlpha", config.highlight * drawableAlpha * 0.56f)
        runtimeShader.setFloatUniform("uEdgeShadow", config.shadow * drawableAlpha * 0.34f)
        runtimeShader.setFloatUniform("uDispersion", config.dispersion * 0.16f)
        canvas.drawRect(glassRect, rimPaint)
    }

    private fun updateGlassRect(bounds: Rect): Boolean {
        val safeInset = inset.coerceAtMost(minOf(bounds.width(), bounds.height()) / 2)
        glassRect.set(
            (bounds.left + safeInset).toFloat(),
            (bounds.top + safeInset).toFloat(),
            (bounds.right - safeInset).toFloat(),
            (bounds.bottom - safeInset).toFloat(),
        )
        return !glassRect.isEmpty
    }

    override fun setAlpha(alpha: Int) {
        child.alpha = alpha
        drawableAlpha = alpha.coerceIn(0, 255) / 255f
        updateCaptureState()
        invalidateSelf()
    }

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        child.setVisible(visible, restart)
        val changed = super.setVisible(visible, restart)
        updateCaptureState()
        return changed
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        child.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun invalidateDrawable(who: Drawable) = invalidateSelf()

    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
        scheduleSelf(what, `when`)
    }

    override fun unscheduleDrawable(who: Drawable, what: Runnable) {
        unscheduleSelf(what)
    }

    fun release() {
        if (tiltAttached) runCatching { LiquidGlassTiltController.detach(this) }
        tiltAttached = false
        screenCapture.release()
        stopCapture()
        contentView = null
        hostView.clear()
        child.callback = null
        callback = null
        synchronized(blurResourceLock) {
            blurNode.discardDisplayList()
            blurNode.setRenderEffect(null)
            blurEffect = null
        }
    }

    private companion object {
        const val DEFAULT_LIGHT_X = -0.45f
        const val DEFAULT_LIGHT_Y = -0.89f
        const val TILT_STRENGTH = 2.2f
        const val RETAINED_FRAME_COUNT = 2
        const val STOPPED_FRAME_RELEASE_DELAY_MS = 150L

        val createHardwareBitmapMethod: Method by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            resolveCreateHardwareBitmapMethod()
        }

        @SuppressLint("BlockedPrivateApi")
        fun resolveCreateHardwareBitmapMethod(): Method {
            return HardwareRenderer::class.java.getDeclaredMethod(
                "createHardwareBitmap",
                RenderNode::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            ).apply { isAccessible = true }
        }

        val RIM_SHADER = """
            uniform float2 uOrigin;
            uniform float2 uSize;
            uniform float uCornerRadius;
            uniform float2 uLightDir;
            uniform float uEdgeWidth;
            uniform float uRefraction;
            uniform float uEdgeAlpha;
            uniform float uEdgeShadow;
            uniform float uDispersion;

            float sdRoundedBox(float2 p, float2 halfSize, float radius) {
                float2 q = abs(p) - halfSize + float2(radius);
                return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius;
            }

            half4 main(float2 fragCoord) {
                float2 halfSize = uSize * 0.5;
                float2 p = fragCoord - uOrigin - halfSize;
                float distance = sdRoundedBox(p, halfSize, uCornerRadius);
                if (distance > 0.0) return half4(0.0);

                float2 dx = float2(1.0, 0.0);
                float2 dy = float2(0.0, 1.0);
                float2 normal = normalize(float2(
                    sdRoundedBox(p + dx, halfSize, uCornerRadius)
                        - sdRoundedBox(p - dx, halfSize, uCornerRadius),
                    sdRoundedBox(p + dy, halfSize, uCornerRadius)
                        - sdRoundedBox(p - dy, halfSize, uCornerRadius)
                ) + float2(0.0001));

                float edge = pow(smoothstep(-uEdgeWidth, 0.0, distance), 2.2);
                float facing = dot(normal, normalize(uLightDir));
                float bright = max(facing, 0.0) * edge * uEdgeAlpha;
                float shadow = max(-facing, 0.0) * edge * uEdgeShadow;
                float lensBand = pow(smoothstep(-uEdgeWidth * 2.2, -uEdgeWidth * 0.25, distance), 2.0)
                    * (1.0 - edge) * uRefraction;
                float dispersion = facing * edge * uDispersion * 0.22;
                float alpha = clamp(bright + shadow + lensBand, 0.0, 1.0);
                float3 primary = float3(1.0 + max(dispersion, 0.0), 0.99,
                    0.96 + max(-dispersion, 0.0)) * (bright + lensBand * 0.45);
                float3 secondary = float3(0.92, 0.96, 1.0) * shadow;
                float3 color = primary + secondary;
                return half4(half3(color), half(alpha));
            }
        """.trimIndent()

        val REFRACTION_SHADER = """
            uniform shader uContent;
            uniform float2 uOrigin;
            uniform float2 uScreenOrigin;
            uniform float2 uCaptureScale;
            uniform float2 uCaptureOrigin;
            uniform float2 uSize;
            uniform float uCornerRadius;
            uniform float uRefractionHeight;
            uniform float uRefractionAmount;
            uniform float uDepthEffect;
            uniform float uDispersion;
            uniform float4 uBlendColor;

            float sdRoundedBox(float2 p, float2 halfSize, float radius) {
                float2 q = abs(p) - halfSize + float2(radius);
                return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius;
            }

            float2 gradRoundedBox(float2 p, float2 halfSize, float radius) {
                float2 q = abs(p) - halfSize + float2(radius);
                float2 s = sign(p);
                s.x = s.x == 0.0 ? 1.0 : s.x;
                s.y = s.y == 0.0 ? 1.0 : s.y;
                if (q.x >= 0.0 || q.y >= 0.0) {
                    return s * normalize(max(q, 0.0) + float2(0.0001));
                }
                float gx = step(q.y, q.x);
                return s * float2(gx, 1.0 - gx);
            }

            float circleMap(float x) {
                return 1.0 - sqrt(max(1.0 - x * x, 0.0));
            }

            half4 sampleContent(float2 screenCoord) {
                half4 content = uContent.eval(
                    (screenCoord - uCaptureOrigin) * uCaptureScale
                );
                return half4(
                    mix(content.rgb, half3(uBlendColor.rgb), half(uBlendColor.a)),
                    content.a
                );
            }

            half4 main(float2 fragCoord) {
                float2 halfSize = uSize * 0.5;
                float2 p = fragCoord - uOrigin - halfSize;
                float distance = sdRoundedBox(p, halfSize, uCornerRadius);
                if (distance > 0.0) return half4(0.0);

                float2 screenCoord = fragCoord + uScreenOrigin;
                if (-distance >= uRefractionHeight) {
                    half4 content = sampleContent(screenCoord);
                    return half4(content.rgb, 1.0);
                }

                float depth = clamp(-min(distance, 0.0) / uRefractionHeight, 0.0, 1.0);
                float fade = 1.0 - depth * depth * (3.0 - 2.0 * depth);
                float displacement = circleMap(1.0 - depth) * uRefractionAmount * fade;
                float2 centerDirection = normalize(p + float2(0.0001));
                float2 normal = normalize(
                    gradRoundedBox(p, halfSize, uCornerRadius) +
                        uDepthEffect * fade * centerDirection
                );
                float2 refracted = screenCoord + normal * displacement;
                float positionFactor = abs(p.x * p.y) /
                    max(halfSize.x * halfSize.y, 1.0);
                float2 dispersion = displacement * normal * uDispersion *
                    (0.4 + 0.6 * positionFactor);
                half red = sampleContent(refracted + dispersion).r;
                half4 center = sampleContent(refracted);
                half blue = sampleContent(refracted - dispersion).b;
                return half4(half3(red, center.g, blue), 1.0);
            }
        """.trimIndent()
    }
}

private class RefractiveScreenCapture(
    host: View,
    private val prepareFrame: (Bitmap) -> Bitmap,
    private val onFrame: (Bitmap, Float, Float, Float, Float) -> Unit,
) {
    companion object {
        private const val TAG = "HyperIsland[TrueRefraction]"
    }

    private val host = WeakReference(host)
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var worker: Handler? = null
    @Volatile
    private var generation = 0
    @Volatile
    private var captureAccess: CaptureAccess? = null
    private var captureFps = 20
    private var captureScale = 0.3f
    private val location = IntArray(2)
    @Volatile
    private var viewSnapshot = ViewSnapshot.EMPTY
    @Volatile
    private var captureRegion: Rect? = null
    @Volatile
    var hasFrame = false
        private set

    @Volatile
    var screenX = 0f
        private set

    @Volatile
    var screenY = 0f
        private set

    fun updateSettings(fps: Int, scale: Float) {
        val nextFps = fps.coerceIn(10, 60)
        val nextScale = scale.coerceIn(0.1f, 1f)
        if (captureFps == nextFps && captureScale == nextScale) return
        if (worker != null) stop()
        captureFps = nextFps
        captureScale = nextScale
    }

    fun start() {
        if (worker != null) return
        val snapshot = viewSnapshot
        if (!snapshot.canCapture) return
        val captureWorker = GlassCaptureManager.acquire()
        worker = captureWorker
        generation++
        val token = generation
        captureWorker.post { initializeCapture(token, snapshot.displayId) }
    }

    private fun initializeCapture(token: Int, displayId: Int) {
        if (token != generation) return
        if (!viewSnapshot.canCapture) {
            mainHandler.post { if (token == generation) stop() }
            return
        }
        val access = runCatching {
            CaptureAccess.create(
                displayId,
                captureScale,
                { captureRegion },
                { viewSnapshot.excludeLayers },
            )
        }
            .onFailure { logWarn("$TAG unavailable: ${it.message}") }
            .getOrNull()
        if (access == null) {
            mainHandler.post { if (token == generation) stop() }
            return
        }
        captureAccess = access
        logWarn(
            "$TAG started path=${access.path} fps=$captureFps scale=$captureScale " +
                "with excluded island layers",
        )
        capture(token)
    }

    fun stop() {
        if (worker == null && captureAccess == null && !hasFrame) return
        generation++
        worker = null
        captureAccess = null
        hasFrame = false
        host.get()?.postInvalidateOnAnimation()
    }

    fun release() {
        stop()
        host.clear()
    }

    fun updateViewSnapshot(canCapture: Boolean) {
        val view = host.get() ?: return
        runCatching {
            view.getLocationOnScreen(location)
            screenX = location[0].toFloat()
            screenY = location[1].toFloat()
            viewSnapshot = ViewSnapshot(
                canCapture = canCapture,
                displayId = view.display?.displayId ?: 0,
                excludeLayers = CaptureAccess.currentExcludeLayers(view),
            )
        }.onFailure {
            viewSnapshot = ViewSnapshot.EMPTY
        }
    }

    fun updateCaptureRegion(
        localBounds: RectF,
        blurRadius: Float,
        refractionDistance: Float,
    ) {
        val view = host.get() ?: return
        if (localBounds.isEmpty) return
        runCatching {
            view.getLocationOnScreen(location)
            screenX = location[0].toFloat()
            screenY = location[1].toFloat()
            val metrics = view.resources.displayMetrics
            val padding = kotlin.math.ceil(
                maxOf(blurRadius * 2f, refractionDistance * 1.25f, 1f),
            ).toInt()
            val left = kotlin.math.floor(screenX + localBounds.left - padding).toInt()
                .coerceIn(0, metrics.widthPixels)
            val top = kotlin.math.floor(screenY + localBounds.top - padding).toInt()
                .coerceIn(0, metrics.heightPixels)
            val right = kotlin.math.ceil(screenX + localBounds.right + padding).toInt()
                .coerceIn(0, metrics.widthPixels)
            val bottom = kotlin.math.ceil(screenY + localBounds.bottom + padding).toInt()
                .coerceIn(0, metrics.heightPixels)
            captureRegion = Rect(left, top, right, bottom).takeIf {
                it.width() > 0 && it.height() > 0
            }
        }
    }

    private fun scheduleCapture(token: Int, delay: Long) {
        worker?.postDelayed({ capture(token) }, delay)
    }

    private fun capture(token: Int) {
        if (token != generation) return
        if (!viewSnapshot.canCapture) {
            mainHandler.post { if (token == generation) stop() }
            return
        }
        val access = captureAccess ?: return
        val startedAt = SystemClock.uptimeMillis()
        val result = runCatching { access.capture() }
        val frame = result.getOrNull()
        if (result.isFailure || frame == null) {
            logWarn(
                "$TAG capture failed, falling back to native blur: " +
                    (result.exceptionOrNull()?.message ?: "empty frame"),
            )
            mainHandler.post { if (token == generation) stop() }
            return
        }
        if (token != generation) {
            if (!frame.bitmap.isRecycled) frame.bitmap.recycle()
            return
        }
        val preparedBitmap = prepareFrame(frame.bitmap)
        mainHandler.post {
            if (token != generation) {
                if (!preparedBitmap.isRecycled) preparedBitmap.recycle()
                return@post
            }
            hasFrame = true
            onFrame(
                preparedBitmap,
                frame.scaleX,
                frame.scaleY,
                frame.cropX,
                frame.cropY,
            )
        }
        val elapsed = SystemClock.uptimeMillis() - startedAt
        scheduleCapture(token, (captureIntervalMs - elapsed).coerceAtLeast(0L))
    }

    private val captureIntervalMs: Long
        get() = (1000L / captureFps.coerceAtLeast(1)).coerceAtLeast(1L)

    private data class CapturedFrame(
        val bitmap: Bitmap,
        val scaleX: Float,
        val scaleY: Float,
        val cropX: Float,
        val cropY: Float,
    )

    private data class ViewSnapshot(
        val canCapture: Boolean,
        val displayId: Int,
        val excludeLayers: Any?,
    ) {
        companion object {
            val EMPTY = ViewSnapshot(false, 0, null)
        }
    }

    private class CaptureAccess(
        val path: String,
        private val captureFrame: () -> CapturedFrame?,
    ) {
        fun capture(): CapturedFrame? = captureFrame()

        companion object {
            fun create(
                displayId: Int,
                captureScale: Float,
                getCaptureRegion: () -> Rect?,
                getExcludeLayers: () -> Any?,
            ): CaptureAccess {
                val screenCaptureClass = Class.forName("android.window.ScreenCapture")

                return createWindowManagerAccess(
                    displayId,
                    screenCaptureClass,
                    captureScale,
                    getCaptureRegion,
                    getExcludeLayers,
                )
            }

            private fun createWindowManagerAccess(
                displayId: Int,
                screenCaptureClass: Class<*>,
                captureScale: Float,
                getCaptureRegion: () -> Rect?,
                getExcludeLayers: () -> Any?,
            ): CaptureAccess {
                val builderClass = Class.forName(
                    "android.window.ScreenCapture\$CaptureArgs\$Builder",
                )
                val constructor = builderClass.declaredConstructors.firstOrNull {
                    it.parameterCount == 0
                }?.apply { isAccessible = true } ?: error("CaptureArgs.Builder unavailable")
                val captureArgsClass = Class.forName("android.window.ScreenCapture\$CaptureArgs")
                val buildMethod = findMethod(builderClass, "build")
                    ?: error("CaptureArgs unavailable")
                val windowManagerGlobal = Class.forName("android.view.WindowManagerGlobal")
                val service = findMethod(windowManagerGlobal, "getWindowManagerService")
                    ?.invoke(null) ?: error("IWindowManager unavailable")
                val createListener = findMethod(screenCaptureClass, "createSyncCaptureListener")
                    ?: error("sync capture listener unavailable")
                val captureMethod = service.javaClass.methods.firstOrNull { method ->
                        method.name == "captureDisplay" &&
                            method.parameterCount == 3 &&
                            method.parameterTypes[0] == Int::class.javaPrimitiveType &&
                            method.parameterTypes[1].isAssignableFrom(captureArgsClass)
                    }?.apply { isAccessible = true } ?: service.javaClass.declaredMethods.firstOrNull { method ->
                        method.name == "captureDisplay" &&
                            method.parameterCount == 3 &&
                            method.parameterTypes[0] == Int::class.javaPrimitiveType &&
                            method.parameterTypes[1].isAssignableFrom(captureArgsClass)
                    }?.apply { isAccessible = true } ?: error("IWindowManager.captureDisplay unavailable")
                return CaptureAccess(
                    path = "iwm-local",
                    captureFrame = {
                        val region = getCaptureRegion()
                            ?: return@CaptureAccess null
                        val builder = constructor.newInstance()
                        configureCaptureBuilder(
                            builderClass,
                            builder,
                            getExcludeLayers(),
                            region,
                            captureScale,
                        )
                        val args = buildMethod.invoke(builder)
                            ?: error("CaptureArgs unavailable")
                        val listener = createListener.invoke(null)
                            ?: error("sync capture listener creation failed")
                        captureMethod.invoke(service, displayId, args, listener)
                        val buffer = findMethod(listener.javaClass, "getBuffer")?.invoke(listener)
                            ?: error("sync capture returned no buffer")
                        val bitmap = findMethod(buffer.javaClass, "asBitmap")?.invoke(buffer) as? Bitmap
                            ?: return@CaptureAccess null
                        CapturedFrame(
                            bitmap = bitmap,
                            scaleX = bitmap.width.toFloat() / region.width(),
                            scaleY = bitmap.height.toFloat() / region.height(),
                            cropX = region.left.toFloat(),
                            cropY = region.top.toFloat(),
                        )
                    },
                )
            }

            fun currentExcludeLayers(view: View): Any? {
                val surfaceClass = runCatching {
                    Class.forName("android.view.SurfaceControl")
                }.getOrNull() ?: return null
                val rootView = view.rootView ?: view
                val viewRoot = findMethod(rootView.javaClass, "getViewRootImpl")
                    ?.invoke(rootView) ?: return null
                val surface = findMethod(viewRoot.javaClass, "getSurfaceControl")
                    ?.invoke(viewRoot) ?: return null
                if (!surfaceClass.isInstance(surface)) return null
                val isValid = findMethod(surfaceClass, "isValid")?.invoke(surface) as? Boolean
                if (isValid == false) return null
                return java.lang.reflect.Array.newInstance(surfaceClass, 1).also {
                    java.lang.reflect.Array.set(it, 0, surface)
                }
            }

            private fun configureCaptureBuilder(
                builderClass: Class<*>,
                builder: Any,
                excludeArray: Any?,
                region: Rect,
                captureScale: Float,
            ) {
                val setSourceCrop = findMethod(builderClass, "setSourceCrop", Rect::class.java)
                    ?: error("local capture crop unsupported")
                setSourceCrop.invoke(builder, region)
                val setSize = findMethod(
                    builderClass,
                    "setSize",
                    Int::class.javaPrimitiveType!!,
                    Int::class.javaPrimitiveType!!,
                )
                if (setSize != null) {
                    setSize.invoke(
                        builder,
                        (region.width() * captureScale).toInt().coerceAtLeast(1),
                        (region.height() * captureScale).toInt().coerceAtLeast(1),
                    )
                } else {
                    val oneScale = findMethod(
                        builderClass,
                        "setFrameScale",
                        Float::class.javaPrimitiveType!!,
                    )
                    val twoScale = findMethod(
                        builderClass,
                        "setFrameScale",
                        Float::class.javaPrimitiveType!!,
                        Float::class.javaPrimitiveType!!,
                    )
                    when {
                        oneScale != null -> oneScale.invoke(builder, captureScale)
                        twoScale != null -> twoScale.invoke(builder, captureScale, captureScale)
                        else -> error("capture scale unsupported")
                    }
                }
                findMethod(
                    builderClass,
                    "setCaptureMode",
                    Int::class.javaPrimitiveType!!,
                )?.invoke(builder, 1)
                val setLayerNames = findMethod(
                    builderClass,
                    "setExcludeOrIncludeLayerNames",
                    Array<String>::class.java,
                )
                setLayerNames?.invoke(builder, EXCLUDED_LAYER_NAMES)
                val setExcludeLayers = builderClass.methods.firstOrNull {
                    it.name == "setExcludeLayers" && it.parameterCount == 1
                } ?: builderClass.declaredMethods.firstOrNull {
                    it.name == "setExcludeLayers" && it.parameterCount == 1
                }?.apply { isAccessible = true }
                if (excludeArray != null && setExcludeLayers != null) {
                    setExcludeLayers.invoke(builder, excludeArray)
                } else if (setLayerNames == null) {
                    error("no supported island exclusion mechanism")
                }
            }

            private val EXCLUDED_LAYER_NAMES = arrayOf(
                "NotificationShade#",
                "StatusBar#",
                "StatusBar1#",
                "NavigationBar0#",
                "DynamicIslandWindow#",
                "VolumePanelDialogController#",
                "ShellDropTarget#",
                "MiuiShellDropTarget#",
                "NotificationModalWindowManager#",
                "SecondaryHomeHandle0#",
            )

            private fun findMethod(clazz: Class<*>, name: String, vararg types: Class<*>): java.lang.reflect.Method? {
                runCatching {
                    return clazz.getMethod(name, *types).apply { isAccessible = true }
                }
                var current: Class<*>? = clazz
                while (current != null) {
                    runCatching {
                        return current.getDeclaredMethod(name, *types).apply { isAccessible = true }
                    }
                    current = current.superclass
                }
                return null
            }
        }
    }
}

/** Serializes all true-refraction work on one process-wide capture thread. */
private object GlassCaptureManager {
    private val thread = HandlerThread("HyperIslandTrueRefraction").apply { start() }
    private val handler = Handler(thread.looper)

    fun acquire(): Handler = handler
}

/** Shares one pose sensor listener between all currently active glass drawables. */
private object LiquidGlassTiltController : SensorEventListener {
    private const val TAG = "HyperIsland[LiquidGlassTilt]"
    private const val FILTER_ALPHA = 0.16f
    private const val DIAGNOSTIC_INTERVAL_MS = 2_000L

    private val targets = Collections.newSetFromMap(
        WeakHashMap<LiquidGlassDrawable, Boolean>(),
    )
    private var sensorManager: SensorManager? = null
    private var activeSensor: Sensor? = null
    private var filteredTiltX = 0f
    private var filteredTiltY = 0f
    private val rotationMatrix = FloatArray(9)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var receivedFirstEvent = false
    private var lastDiagnosticAt = 0L

    fun attach(context: Context, drawable: LiquidGlassDrawable) {
        synchronized(targets) {
            targets.add(drawable)
            if (activeSensor != null) return

            // A SystemUI plugin Context can have no applicationContext. Sensor setup
            // must never be allowed to abort creation of the blur drawable.
            val sensorContext = context.applicationContext ?: context
            val manager = runCatching {
                sensorContext.getSystemService(SensorManager::class.java)
            }.onFailure { error ->
                logWarn("$TAG SensorManager unavailable: ${error.message}")
            }.getOrNull() ?: run {
                logWarn("$TAG SensorManager is null")
                return
            }
            val sensor = manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
                ?: manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
                ?: manager.getDefaultSensor(Sensor.TYPE_GRAVITY)
                ?: run {
                    logWarn("$TAG no rotation-vector or gravity sensor")
                    return
                }
            val registered = runCatching {
                manager.registerListener(
                    this,
                    sensor,
                    SensorManager.SENSOR_DELAY_GAME,
                    mainHandler,
                )
            }.onFailure { error ->
                logWarn("$TAG registration threw: ${error.message}")
            }.getOrDefault(false)
            if (registered) {
                sensorManager = manager
                activeSensor = sensor
                receivedFirstEvent = false
                logWarn(
                    "$TAG registered type=${sensor.type} name=${sensor.name} " +
                        "vendor=${sensor.vendor} targets=${targets.size}",
                )
            } else {
                logWarn("$TAG registration returned false for type=${sensor.type} name=${sensor.name}")
            }
        }
    }

    fun detach(drawable: LiquidGlassDrawable) {
        synchronized(targets) {
            targets.remove(drawable)
            if (targets.isNotEmpty()) return
            runCatching { sensorManager?.unregisterListener(this) }
            sensorManager = null
            activeSensor = null
            filteredTiltX = 0f
            filteredTiltY = 0f
            receivedFirstEvent = false
            logWarn("$TAG unregistered: no active glass targets")
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val tilt = when (event.sensor.type) {
            Sensor.TYPE_GAME_ROTATION_VECTOR,
            Sensor.TYPE_ROTATION_VECTOR
            -> rotationVectorTilt(event.values)
            Sensor.TYPE_GRAVITY -> {
                val scale = SensorManager.GRAVITY_EARTH.coerceAtLeast(0.0001f)
                event.values[0] / scale to event.values[1] / scale
            }
            else -> return
        }

        filteredTiltX += (tilt.first.coerceIn(-1f, 1f) - filteredTiltX) * FILTER_ALPHA
        filteredTiltY += (tilt.second.coerceIn(-1f, 1f) - filteredTiltY) * FILTER_ALPHA
        val snapshot = synchronized(targets) { targets.toList() }
        snapshot.forEach { it.applyTilt(filteredTiltX, filteredTiltY) }

        val now = SystemClock.uptimeMillis()
        if (!receivedFirstEvent || now - lastDiagnosticAt >= DIAGNOSTIC_INTERVAL_MS) {
            receivedFirstEvent = true
            lastDiagnosticAt = now
        }
    }

    private fun rotationVectorTilt(values: FloatArray): Pair<Float, Float> {
        SensorManager.getRotationMatrixFromVector(rotationMatrix, values)
        // R transforms device coordinates into world coordinates. Its third row is
        // the world vertical projected back onto the device X/Y axes.
        return rotationMatrix[6] to rotationMatrix[7]
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
