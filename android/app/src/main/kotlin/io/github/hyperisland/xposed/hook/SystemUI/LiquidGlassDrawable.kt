package io.github.hyperisland.xposed.hook.SystemUI

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.drawable.Drawable
import kotlin.math.hypot

/** Draws a lightweight glass highlight over a native backdrop blur drawable. */
internal class LiquidGlassDrawable(
    private val child: Drawable,
    private val inset: Int,
) : Drawable(), Drawable.Callback {

    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glassRect = RectF()
    private val shader = runCatching { RuntimeShader(GLASS_SHADER) }.getOrNull()
    private var cornerRadius = 0f
    private var lightX = DEFAULT_LIGHT_X
    private var lightY = DEFAULT_LIGHT_Y

    init {
        child.callback = this
        glassPaint.shader = shader
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
        child.draw(canvas)
        val runtimeShader = shader ?: return
        if (!updateGlassRect(bounds)) return

        val width = glassRect.width()
        val height = glassRect.height()
        val radius = cornerRadius.coerceAtMost(minOf(width, height) / 2f)
        runtimeShader.setFloatUniform("uOrigin", glassRect.left, glassRect.top)
        runtimeShader.setFloatUniform("uSize", width, height)
        runtimeShader.setFloatUniform("uCornerRadius", radius)
        runtimeShader.setFloatUniform("uLightDir", lightX, lightY)
        runtimeShader.setFloatUniform("uEdgeWidth", (height * EDGE_WIDTH_RATIO).coerceAtLeast(1f))
        runtimeShader.setFloatUniform("uEdgeAlpha", EDGE_ALPHA)
        runtimeShader.setFloatUniform("uSpecAlpha", SPECULAR_ALPHA)
        runtimeShader.setFloatUniform("uTopLight", TOP_LIGHT_ALPHA)
        canvas.drawRect(glassRect, glassPaint)
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
        glassPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        child.colorFilter = colorFilter
        glassPaint.colorFilter = colorFilter
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
        const val EDGE_WIDTH_RATIO = 0.18f
        const val EDGE_ALPHA = 0.55f
        const val SPECULAR_ALPHA = 0.35f
        const val TOP_LIGHT_ALPHA = 0.20f
        const val DEFAULT_LIGHT_X = 0.3f
        const val DEFAULT_LIGHT_Y = -0.9f

        val GLASS_SHADER = """
            uniform float2 uOrigin;
            uniform float2 uSize;
            uniform float uCornerRadius;
            uniform float2 uLightDir;
            uniform float uEdgeWidth;
            uniform float uEdgeAlpha;
            uniform float uSpecAlpha;
            uniform float uTopLight;

            float sdRoundedBox(float2 p, float2 halfSize, float radius) {
                float2 q = abs(p) - halfSize + float2(radius);
                return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius;
            }

            half4 main(float2 fragCoord) {
                float2 halfSize = uSize * 0.5;
                float2 p = fragCoord - uOrigin - halfSize;
                float distance = sdRoundedBox(p, halfSize, uCornerRadius);
                if (distance > 0.0) return half4(0.0);

                float edge = smoothstep(-uEdgeWidth, 0.0, distance);
                float edgeHighlight = pow(edge, 2.5) * uEdgeAlpha;
                float2 normal = normalize(p + float2(0.0001));
                float specular = pow(max(dot(normal, uLightDir), 0.0), 10.0) * uSpecAlpha;
                float topGradient = (1.0 - smoothstep(-halfSize.y, halfSize.y, p.y))
                    * uTopLight * 0.5;
                float alpha = clamp(edgeHighlight + specular + topGradient, 0.0, 1.0);

                // A restrained warm/cool split suggests dispersion without sampling backdrop pixels.
                float dispersion = normal.x * edge * 0.018;
                float3 tint = float3(1.0, 0.99 - dispersion * 0.25, 0.97 + dispersion);
                return half4(half3(tint * alpha), half(alpha));
            }
        """.trimIndent()
    }
}
