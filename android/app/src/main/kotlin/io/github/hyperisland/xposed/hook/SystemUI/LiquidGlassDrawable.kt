package io.github.hyperisland.xposed.hook.SystemUI

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.drawable.Drawable
import kotlin.math.hypot

/** Applies edge refraction and directional rim lighting to a native backdrop blur. */
internal class LiquidGlassDrawable(
    private val child: Drawable,
    private val inset: Int,
) : Drawable(), Drawable.Callback {

    private val glassRect = RectF()
    private val shader = runCatching { RuntimeShader(GLASS_SHADER) }.getOrNull()
    private val rimShader = runCatching { RuntimeShader(RIM_SHADER) }.getOrNull()
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = rimShader }
    private val renderEffect = shader?.let { runtimeShader ->
        runCatching {
            RenderEffect.createRuntimeShaderEffect(runtimeShader, "uContent")
        }.getOrNull()
    }
    private val renderNode = renderEffect?.let { effect ->
        RenderNode("HyperIslandLiquidGlass").apply {
            setRenderEffect(effect)
            setClipToBounds(true)
        }
    }
    private var cornerRadius = 0f
    private var lightX = DEFAULT_LIGHT_X
    private var lightY = DEFAULT_LIGHT_Y
    private var drawableAlpha = 1f

    init {
        child.callback = this
        setLightDirection(DEFAULT_LIGHT_X, DEFAULT_LIGHT_Y)
    }

    fun setCornerRadius(radius: Float) {
        cornerRadius = radius.coerceAtLeast(0f)
        invalidateSelf()
    }

    /** Reserved for a future accelerometer/gyroscope-driven highlight. */
    fun setLightDirection(x: Float, y: Float) {
        val length = hypot(x, y).coerceAtLeast(0.0001f)
        lightX = x / length
        lightY = y / length
        invalidateSelf()
    }

    override fun onBoundsChange(bounds: Rect) {
        child.bounds = bounds
        updateGlassRect(bounds)
    }

    override fun draw(canvas: Canvas) {
        val runtimeShader = shader
        if (!updateGlassRect(bounds)) {
            child.draw(canvas)
            return
        }

        val width = glassRect.width()
        val height = glassRect.height()
        val radius = cornerRadius.coerceAtMost(minOf(width, height) / 2f)
        if (canvas.isHardwareAccelerated && runtimeShader != null && renderNode != null) {
            runtimeShader.setFloatUniform("uSize", width, height)
            runtimeShader.setFloatUniform("uCornerRadius", radius)
            runtimeShader.setFloatUniform("uEdgeWidth", (height * EDGE_WIDTH_RATIO).coerceAtLeast(1f))
            runtimeShader.setFloatUniform(
                "uRefractionAmount",
                (height * REFRACTION_AMOUNT_RATIO).coerceAtLeast(1f),
            )

            // Record only the island fill. The outer outline remains outside this effect node.
            val left = glassRect.left.toInt()
            val top = glassRect.top.toInt()
            val nodeWidth = width.toInt()
            val nodeHeight = height.toInt()
            renderNode.setPosition(left, top, left + nodeWidth, top + nodeHeight)
            val recordingCanvas = renderNode.beginRecording(nodeWidth, nodeHeight)
            recordingCanvas.translate(-glassRect.left, -glassRect.top)
            child.draw(recordingCanvas)
            renderNode.endRecording()
            canvas.drawRenderNode(renderNode)
        } else {
            child.draw(canvas)
        }

        drawDirectionalRim(canvas, width, height, radius)
    }

    private fun drawDirectionalRim(canvas: Canvas, width: Float, height: Float, radius: Float) {
        val runtimeShader = rimShader ?: return
        runtimeShader.setFloatUniform("uOrigin", glassRect.left, glassRect.top)
        runtimeShader.setFloatUniform("uSize", width, height)
        runtimeShader.setFloatUniform("uCornerRadius", radius)
        runtimeShader.setFloatUniform("uLightDir", lightX, lightY)
        runtimeShader.setFloatUniform("uEdgeWidth", (height * EDGE_WIDTH_RATIO).coerceAtLeast(1f))
        runtimeShader.setFloatUniform("uEdgeAlpha", EDGE_ALPHA * drawableAlpha)
        runtimeShader.setFloatUniform("uEdgeShadow", EDGE_SHADOW * drawableAlpha)
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
        child.callback = null
        callback = null
    }

    private companion object {
        // Fixed visual parameters until user-facing controls are added.
        const val EDGE_WIDTH_RATIO = 0.16f
        const val REFRACTION_AMOUNT_RATIO = 0.16f
        const val EDGE_ALPHA = 0.42f
        const val EDGE_SHADOW = 0.14f
        const val DEFAULT_LIGHT_X = -0.45f
        const val DEFAULT_LIGHT_Y = -0.89f

        val GLASS_SHADER = """
            uniform float2 uSize;
            uniform float uCornerRadius;
            uniform float uEdgeWidth;
            uniform float uRefractionAmount;
            uniform shader uContent;

            float sdRoundedBox(float2 p, float2 halfSize, float radius) {
                float2 q = abs(p) - halfSize + float2(radius);
                return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius;
            }

            half4 main(float2 fragCoord) {
                float2 halfSize = uSize * 0.5;
                float2 p = fragCoord - halfSize;
                float distance = sdRoundedBox(p, halfSize, uCornerRadius);
                if (distance > 0.0) return half4(0.0);

                // Finite differences produce the rounded-rectangle edge normal instead
                // of a center radial normal, preventing a triangular center highlight.
                float2 dx = float2(1.0, 0.0);
                float2 dy = float2(0.0, 1.0);
                float2 normal = normalize(float2(
                    sdRoundedBox(p + dx, halfSize, uCornerRadius)
                        - sdRoundedBox(p - dx, halfSize, uCornerRadius),
                    sdRoundedBox(p + dy, halfSize, uCornerRadius)
                        - sdRoundedBox(p - dy, halfSize, uCornerRadius)
                ) + float2(0.0001));

                float edge = smoothstep(-uEdgeWidth, 0.0, distance);
                float lens = edge * edge * (3.0 - 2.0 * edge);
                float2 refractedCoord = fragCoord - normal * uRefractionAmount * lens;

                // Slightly different channel offsets make the bent edge readable while
                // keeping the center and most of the blurred backdrop unchanged.
                half4 center = uContent.eval(refractedCoord);
                half red = uContent.eval(
                    fragCoord - normal * uRefractionAmount * 1.08 * lens
                ).r;
                half blue = uContent.eval(
                    fragCoord - normal * uRefractionAmount * 0.92 * lens
                ).b;
                half3 color = half3(red, center.g, blue);

                return half4(color, center.a);
            }
        """.trimIndent()

        val RIM_SHADER = """
            uniform float2 uOrigin;
            uniform float2 uSize;
            uniform float uCornerRadius;
            uniform float2 uLightDir;
            uniform float uEdgeWidth;
            uniform float uEdgeAlpha;
            uniform float uEdgeShadow;

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
                float alpha = clamp(bright + shadow, 0.0, 1.0);
                float3 color = float3(1.0, 0.99, 0.96) * bright;
                return half4(half3(color), half(alpha));
            }
        """.trimIndent()
    }
}
