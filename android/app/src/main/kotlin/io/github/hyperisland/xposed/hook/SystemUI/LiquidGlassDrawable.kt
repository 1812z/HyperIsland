package io.github.hyperisland.xposed.hook.SystemUI

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
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
) {
    companion object {
        fun disabled() = LiquidGlassConfig(
            false, 0.16f, 0.16f, 0.42f, 0.14f, 243, 0.18f, false, false,
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
    private val glassRect = RectF()
    private val rimShader = runCatching { RuntimeShader(RIM_SHADER) }.getOrNull()
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = rimShader }
    private val refractionShader = runCatching { RuntimeShader(REFRACTION_SHADER) }.getOrNull()
    private val refractionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = refractionShader }
    private val screenCapture = RefractiveScreenCapture(host, ::onScreenCaptured)
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
        updateCaptureState()
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
        if (config.enabled && config.trueRefraction && refractionShader != null) {
            screenCapture.updateScreenPosition()
            screenCapture.start()
        } else {
            screenCapture.stop()
        }
    }

    private fun onScreenCaptured(bitmap: Bitmap, scaleX: Float, scaleY: Float) {
        val runtimeShader = refractionShader ?: return
        runtimeShader.setInputShader(
            "uContent",
            BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP),
        )
        runtimeShader.setFloatUniform("uCaptureScale", scaleX, scaleY)
        hostView.get()?.postInvalidateOnAnimation()
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
        screenCapture.updateScreenPosition()
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
        if (config.enabled) {
            drawTrueRefraction(canvas, width, height, radius)
            drawDirectionalRim(canvas, width, height, radius)
        }
    }

    private fun drawTrueRefraction(canvas: Canvas, width: Float, height: Float, radius: Float) {
        if (!config.trueRefraction || !screenCapture.hasFrame) return
        val runtimeShader = refractionShader ?: return
        runtimeShader.setFloatUniform("uOrigin", glassRect.left, glassRect.top)
        runtimeShader.setFloatUniform("uScreenOrigin", screenCapture.screenX, screenCapture.screenY)
        runtimeShader.setFloatUniform("uSize", width, height)
        runtimeShader.setFloatUniform("uCornerRadius", radius)
        runtimeShader.setFloatUniform("uRefractionHeight", (height * config.edgeWidth * 2f).coerceAtLeast(1f))
        runtimeShader.setFloatUniform("uRefractionAmount", height * config.refraction)
        runtimeShader.setFloatUniform("uDispersion", config.dispersion * 4f)
        canvas.drawRect(glassRect, refractionPaint)
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
        screenCapture.release()
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

        val REFRACTION_SHADER = """
            uniform shader uContent;
            uniform float2 uOrigin;
            uniform float2 uScreenOrigin;
            uniform float2 uCaptureScale;
            uniform float2 uSize;
            uniform float uCornerRadius;
            uniform float uRefractionHeight;
            uniform float uRefractionAmount;
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

                float depth = clamp(-distance / uRefractionHeight, 0.0, 1.0);
                float fade = 1.0 - depth * depth * (3.0 - 2.0 * depth);
                float displacement = sqrt(max(1.0 - depth * depth, 0.0))
                    * uRefractionAmount * fade;
                float2 screenCoord = fragCoord + uScreenOrigin;
                float2 refracted = screenCoord + normal * displacement;
                float2 dispersion = normal * uDispersion * fade;
                half red = uContent.eval((refracted + dispersion) * uCaptureScale).r;
                half4 center = uContent.eval(refracted * uCaptureScale);
                half blue = uContent.eval((refracted - dispersion) * uCaptureScale).b;
                half alpha = half(fade);
                return half4(half3(red, center.g, blue) * alpha, alpha);
            }
        """.trimIndent()
    }
}

private class RefractiveScreenCapture(
    host: View,
    private val onFrame: (Bitmap, Float, Float) -> Unit,
) {
    companion object {
        private const val TAG = "HyperIsland[TrueRefraction]"
        private const val CAPTURE_INTERVAL_MS = 50L
        private const val CAPTURE_SCALE = 0.3f
    }

    private val host = WeakReference(host)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var thread: HandlerThread? = null
    private var worker: Handler? = null
    private var generation = 0
    private var captureAccess: CaptureAccess? = null
    private val location = IntArray(2)

    @Volatile
    var hasFrame = false
        private set

    @Volatile
    var screenX = 0f
        private set

    @Volatile
    var screenY = 0f
        private set

    fun start() {
        if (worker != null) return
        val view = host.get() ?: return
        val access = runCatching { CaptureAccess.create(view) }
            .onFailure { logWarn("$TAG unavailable: ${it.message}") }
            .getOrNull() ?: return
        captureAccess = access
        val captureThread = HandlerThread("HyperIslandTrueRefraction").apply { start() }
        thread = captureThread
        worker = Handler(captureThread.looper)
        generation++
        scheduleCapture(generation, 0L)
        logWarn("$TAG started path=${access.path} with excluded island surface")
    }

    fun stop() {
        generation++
        worker?.removeCallbacksAndMessages(null)
        thread?.quitSafely()
        worker = null
        thread = null
        captureAccess = null
        hasFrame = false
    }

    fun release() {
        stop()
        host.clear()
    }

    fun updateScreenPosition() {
        val view = host.get() ?: return
        runCatching {
            view.getLocationOnScreen(location)
            screenX = location[0].toFloat()
            screenY = location[1].toFloat()
        }
    }

    private fun scheduleCapture(token: Int, delay: Long) {
        worker?.postDelayed({ capture(token) }, delay)
    }

    private fun capture(token: Int) {
        if (token != generation) return
        val access = captureAccess ?: return
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
        if (token == generation) {
            mainHandler.post {
                if (token != generation) return@post
                hasFrame = true
                onFrame(frame.bitmap, frame.scaleX, frame.scaleY)
            }
        }
        scheduleCapture(token, CAPTURE_INTERVAL_MS)
    }

    private data class CapturedFrame(
        val bitmap: Bitmap,
        val scaleX: Float,
        val scaleY: Float,
    )

    private class CaptureAccess(
        val path: String,
        private val captureBuffer: () -> Any?,
        private val sourceWidth: Int,
        private val sourceHeight: Int,
    ) {
        fun capture(): CapturedFrame? {
            val buffer = captureBuffer() ?: return null
            val bitmap = findMethod(buffer.javaClass, "asBitmap")?.invoke(buffer) as? Bitmap
                ?: return null
            return CapturedFrame(
                bitmap,
                bitmap.width.toFloat() / sourceWidth,
                bitmap.height.toFloat() / sourceHeight,
            )
        }

        companion object {
            fun create(view: View): CaptureAccess {
                val viewRoot = findMethod(view.javaClass, "getViewRootImpl")?.invoke(view)
                    ?: error("ViewRootImpl unavailable")
                val surface = findMethod(viewRoot.javaClass, "getSurfaceControl")
                    ?.invoke(viewRoot) ?: error("island SurfaceControl unavailable")
                val surfaceClass = Class.forName("android.view.SurfaceControl")
                if (!surfaceClass.isInstance(surface)) error("invalid island SurfaceControl")
                val screenCaptureClass = Class.forName("android.window.ScreenCapture")
                val metrics = view.resources.displayMetrics
                val width = metrics.widthPixels
                val height = metrics.heightPixels
                val excludeArray = java.lang.reflect.Array.newInstance(surfaceClass, 1)
                java.lang.reflect.Array.set(excludeArray, 0, surface)

                return createWindowManagerAccess(
                    view,
                    screenCaptureClass,
                    excludeArray,
                    width,
                    height,
                )
            }

            private fun createWindowManagerAccess(
                view: View,
                screenCaptureClass: Class<*>,
                excludeArray: Any,
                width: Int,
                height: Int,
            ): CaptureAccess {
                val builderClass = Class.forName(
                    "android.window.ScreenCapture\$CaptureArgs\$Builder",
                )
                val constructor = builderClass.declaredConstructors.firstOrNull {
                    it.parameterCount == 0
                }?.apply { isAccessible = true } ?: error("CaptureArgs.Builder unavailable")
                val builder = constructor.newInstance()
                configureCaptureBuilder(builderClass, builder, excludeArray, width, height)
                val args = findMethod(builderClass, "build")?.invoke(builder)
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
                        method.parameterTypes[1].isInstance(args)
                }?.apply { isAccessible = true } ?: service.javaClass.declaredMethods.firstOrNull { method ->
                    method.name == "captureDisplay" &&
                        method.parameterCount == 3 &&
                        method.parameterTypes[0] == Int::class.javaPrimitiveType &&
                        method.parameterTypes[1].isInstance(args)
                }?.apply { isAccessible = true } ?: error("IWindowManager.captureDisplay unavailable")
                val displayId = view.display?.displayId ?: 0
                return CaptureAccess(
                    path = "iwm",
                    captureBuffer = {
                        val listener = createListener.invoke(null)
                            ?: error("sync capture listener creation failed")
                        captureMethod.invoke(service, displayId, args, listener)
                        findMethod(listener.javaClass, "getBuffer")?.invoke(listener)
                            ?: error("sync capture returned no buffer")
                    },
                    sourceWidth = width,
                    sourceHeight = height,
                )
            }

            private fun configureCaptureBuilder(
                builderClass: Class<*>,
                builder: Any,
                excludeArray: Any,
                width: Int,
                height: Int,
            ) {
                val setSize = findMethod(
                    builderClass,
                    "setSize",
                    Int::class.javaPrimitiveType!!,
                    Int::class.javaPrimitiveType!!,
                )
                if (setSize != null) {
                    setSize.invoke(
                        builder,
                        (width * CAPTURE_SCALE).toInt().coerceAtLeast(1),
                        (height * CAPTURE_SCALE).toInt().coerceAtLeast(1),
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
                        oneScale != null -> oneScale.invoke(builder, CAPTURE_SCALE)
                        twoScale != null -> twoScale.invoke(builder, CAPTURE_SCALE, CAPTURE_SCALE)
                        else -> error("capture scale unsupported")
                    }
                }
                val setExcludeLayers = builderClass.methods.firstOrNull {
                    it.name == "setExcludeLayers" && it.parameterCount == 1
                } ?: builderClass.declaredMethods.firstOrNull {
                    it.name == "setExcludeLayers" && it.parameterCount == 1
                }?.apply { isAccessible = true } ?: error("excludeLayers unsupported")
                setExcludeLayers.invoke(builder, excludeArray)
            }

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
