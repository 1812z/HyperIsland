package io.github.hyperisland.homeassistant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Icon

/**
 * Home Assistant 的实体图标使用 `mdi:<name>`。模块不依赖 HA 前端资源包，因此这里保留
 * MDI 名称并将常用图标绘制成同语义的单色图标；未知名称使用通用实体图标。
 */
object HomeAssistantIconResolver {
    fun resolve(iconName: String, context: Context): Icon {
        val name = iconName.substringAfter(':', iconName).lowercase()
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 8f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        when {
            name.contains("battery") || name.contains("power-plug") -> {
                canvas.drawRoundRect(20f, 28f, 76f, 72f, 8f, 8f, paint)
                canvas.drawLine(76f, 42f, 84f, 42f, paint)
                canvas.drawLine(76f, 58f, 84f, 58f, paint)
                paint.style = Paint.Style.FILL
                canvas.drawRect(28f, 36f, 48f, 64f, paint)
            }
            name.contains("light") || name.contains("bulb") -> {
                canvas.drawCircle(48f, 40f, 22f, paint)
                canvas.drawLine(34f, 60f, 62f, 60f, paint)
                canvas.drawLine(38f, 70f, 58f, 70f, paint)
            }
            name.contains("thermometer") || name.contains("temperature") -> {
                canvas.drawRoundRect(40f, 16f, 56f, 68f, 8f, 8f, paint)
                canvas.drawCircle(48f, 72f, 14f, paint)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(48f, 72f, 7f, paint)
            }
            name.contains("flash") || name.contains("lightning") -> {
                paint.style = Paint.Style.FILL
                val path = Path().apply {
                    moveTo(54f, 8f); lineTo(22f, 52f); lineTo(46f, 52f)
                    lineTo(40f, 88f); lineTo(74f, 40f); lineTo(50f, 40f); close()
                }
                canvas.drawPath(path, paint)
            }
            else -> {
                canvas.drawCircle(48f, 48f, 30f, paint)
                canvas.drawCircle(48f, 38f, 7f, paint)
                canvas.drawArc(30f, 42f, 66f, 76f, 20f, 140f, false, paint)
            }
        }
        return Icon.createWithBitmap(bitmap)
    }
}
