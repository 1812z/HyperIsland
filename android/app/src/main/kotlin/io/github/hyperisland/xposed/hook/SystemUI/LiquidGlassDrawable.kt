package io.github.hyperisland.xposed.hook.SystemUI

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.drawable.Drawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import io.github.hyperisland.xposed.logWarn
import java.lang.ref.WeakReference
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
) {
    companion object {
        fun disabled() = LiquidGlassConfig(false, 0.16f, 0.16f, 0.42f, 0.14f, 243, 0.18f, false)
    }
}

/** Draws directional rim lighting over the native backdrop blur. */
internal class LiquidGlassDrawable(
    context: Context,
    hostView: View,
    private val child: Drawable,
    private val inset: Int,
    initialConfig: LiquidGlassConfig,
) : Drawable(), Drawable.Callback {

    private val context = context
    private val hostView = WeakReference(hostView)
    private val glassRect = RectF()
    private val rimShader = runCatching { RuntimeShader(RIM_SHADER) }.getOrNull()
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = rimShader }
    private var cornerRadius = 0f
    private var lightX = DEFAULT_LIGHT_X
    private var lightY = DEFAULT_LIGHT_Y
    private var drawableAlpha = 1f
    private var loggedFirstDraw = false
    private var config = initialConfig
    private var tiltAttached = false

    init {
        child.callback = this
        applyFixedLightDirection()
        updateTiltRegistration()
    }

    fun setCornerRadius(radius: Float) {
        cornerRadius = radius.coerceAtLeast(0f)
        invalidateSelf()
    }

    fun updateConfig(value: LiquidGlassConfig) {
        if (config == value) return
        config = value
        applyFixedLightDirection()
        updateTiltRegistration()
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
        updateGlassRect(bounds)
    }

    override fun draw(canvas: Canvas) {
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

        val width = glassRect.width()
        val height = glassRect.height()
        val radius = cornerRadius.coerceAtMost(minOf(width, height) / 2f)
        // BackgroundBlurDrawable is a RenderThread special drawable and must be
        // submitted directly to the destination canvas. Recording it into another
        // RenderNode produces an empty layer on HyperOS.
        child.draw(canvas)
        if (config.enabled) drawDirectionalRim(canvas, width, height, radius)
    }

    private fun drawDirectionalRim(canvas: Canvas, width: Float, height: Float, radius: Float) {
        val runtimeShader = rimShader ?: return
        runtimeShader.setFloatUniform("uOrigin", glassRect.left, glassRect.top)
        runtimeShader.setFloatUniform("uSize", width, height)
        runtimeShader.setFloatUniform("uCornerRadius", radius)
        runtimeShader.setFloatUniform("uLightDir", lightX, lightY)
        runtimeShader.setFloatUniform("uEdgeWidth", (height * config.edgeWidth).coerceAtLeast(1f))
        runtimeShader.setFloatUniform("uRefraction", config.refraction)
        runtimeShader.setFloatUniform("uEdgeAlpha", config.highlight * drawableAlpha)
        runtimeShader.setFloatUniform("uEdgeShadow", config.shadow * drawableAlpha)
        runtimeShader.setFloatUniform("uDispersion", config.dispersion)
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
        invalidateSelf()
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
        hostView.clear()
        child.callback = null
        callback = null
    }

    private companion object {
        const val DEFAULT_LIGHT_X = -0.45f
        const val DEFAULT_LIGHT_Y = -0.89f
        const val TILT_STRENGTH = 2.2f

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
                float3 tint = float3(1.0 + max(dispersion, 0.0), 0.99,
                    0.96 + max(-dispersion, 0.0));
                float3 color = tint * (bright + lensBand * 0.45);
                return half4(half3(color), half(alpha));
            }
        """.trimIndent()
    }
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
