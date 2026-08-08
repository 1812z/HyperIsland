package io.github.hyperisland.xposed.hook.SystemUI.LiqudGlass

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.view.View

internal const val LIQUID_GLASS_EDGE_GEOMETRY = """
    float sdRoundedBox(float2 p, float2 halfSize, float radius) {
        float2 q = abs(p) - halfSize + float2(radius);
        return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius;
    }

    float3 edgeGeometry(
        float2 p,
        float2 halfSize,
        float radius,
        float edgeWidth,
        float2 lightDir
    ) {
        float distance = sdRoundedBox(p, halfSize, radius);
        float2 dx = float2(1.0, 0.0);
        float2 dy = float2(0.0, 1.0);
        float2 normal = normalize(float2(
            sdRoundedBox(p + dx, halfSize, radius)
                - sdRoundedBox(p - dx, halfSize, radius),
            sdRoundedBox(p + dy, halfSize, radius)
                - sdRoundedBox(p - dy, halfSize, radius)
        ) + float2(0.0001));
        float facing = dot(normal, normalize(lightDir));
        float edge = pow(smoothstep(-edgeWidth, 0.0, distance), 2.2);
        return float3(distance, facing, edge);
    }
"""

internal data class LiquidGlassEdgeState(
    val bounds: RectF,
    val cornerRadius: Float,
    val lightX: Float,
    val lightY: Float,
    val edgeWidth: Float,
    val highlight: Float,
    val shadow: Float,
    val refraction: Float,
    val dispersion: Float,
    val hdrEnabled: Boolean,
)

/** Coordinates the SDR rim details and the HDR-only highlight from one edge state. */
internal class LiquidGlassEdgeHighlight(
    context: Context,
    host: View,
) {
    private val sdrShader = runCatching { RuntimeShader(SDR_EDGE_SHADER) }.getOrNull()
    private val sdrPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = sdrShader }
    private val hdrSurface = HdrHighlightSurface(context, host)

    val isAvailable: Boolean
        get() = sdrShader != null

    fun draw(canvas: Canvas, state: LiquidGlassEdgeState) {
        drawSdr(canvas, state)
        if (state.hdrEnabled) {
            hdrSurface.update(state)
        } else {
            hdrSurface.hide()
        }
    }

    fun hide() = hdrSurface.hide()

    fun release() = hdrSurface.release()

    private fun drawSdr(canvas: Canvas, state: LiquidGlassEdgeState) {
        val shader = sdrShader ?: return
        val bounds = state.bounds
        shader.setFloatUniform("uOrigin", bounds.left, bounds.top)
        shader.setFloatUniform("uSize", bounds.width(), bounds.height())
        shader.setFloatUniform("uCornerRadius", state.cornerRadius)
        shader.setFloatUniform("uLightDir", state.lightX, state.lightY)
        shader.setFloatUniform("uEdgeWidth", state.edgeWidth)
        shader.setFloatUniform("uRefraction", state.refraction)
        shader.setFloatUniform("uEdgeAlpha", state.highlight * SDR_HIGHLIGHT_SCALE)
        shader.setFloatUniform("uEdgeShadow", state.shadow * SDR_SHADOW_SCALE)
        shader.setFloatUniform("uDispersion", state.dispersion * SDR_DISPERSION_SCALE)
        canvas.drawRect(bounds, sdrPaint)
    }

    private companion object {
        const val SDR_HIGHLIGHT_SCALE = 0.56f
        const val SDR_SHADOW_SCALE = 0.34f
        const val SDR_DISPERSION_SCALE = 0.16f

        val SDR_EDGE_SHADER = """
            uniform float2 uOrigin;
            uniform float2 uSize;
            uniform float uCornerRadius;
            uniform float2 uLightDir;
            uniform float uEdgeWidth;
            uniform float uRefraction;
            uniform float uEdgeAlpha;
            uniform float uEdgeShadow;
            uniform float uDispersion;

            $LIQUID_GLASS_EDGE_GEOMETRY

            half4 main(float2 fragCoord) {
                float2 halfSize = uSize * 0.5;
                float2 p = fragCoord - uOrigin - halfSize;
                float3 geometry = edgeGeometry(
                    p, halfSize, uCornerRadius, uEdgeWidth, uLightDir
                );
                float distance = geometry.x;
                if (distance > 0.0) return half4(0.0);

                float facing = geometry.y;
                float edge = geometry.z;
                float bright = max(facing, 0.0) * edge * uEdgeAlpha;
                float opposite = max(-facing, 0.0) * edge * uEdgeShadow;
                float lensBand = pow(smoothstep(-uEdgeWidth * 2.2, -uEdgeWidth * 0.25, distance), 2.0)
                    * (1.0 - edge) * uRefraction;
                float dispersion = facing * edge * uDispersion * 0.22;
                float alpha = clamp(bright + opposite + lensBand, 0.0, 1.0);
                float3 primary = float3(1.0 + max(dispersion, 0.0), 0.99,
                    0.96 + max(-dispersion, 0.0)) * (bright + lensBand * 0.45);
                float3 secondary = float3(0.92, 0.96, 1.0) * opposite;
                return half4(half3(primary + secondary), half(alpha));
            }
        """.trimIndent()
    }
}
