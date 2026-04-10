package io.github.hyperisland.xposed.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.Icon
import io.github.hyperisland.xposed.ConfigManager

/**
 * 在 Xposed 进程（如 SystemUI）中，[Context] 属于宿主进程，不包含本模块的资源。
 * 此函数通过 [Context.createPackageContext] 创建一个包含模块资源的上下文，
 * 使 [Context.getString] 能正确按设备语言加载模块的字符串资源。
 * 在普通 App 进程中 context 已经是模块自身，无需特殊处理，异常时直接返回 this。
 */
internal fun Context.moduleContext(): Context = try {
    createPackageContext("io.github.hyperisland", Context.CONTEXT_IGNORE_SECURITY)
} catch (_: Exception) {
    this
}

/**
 * 将 Icon 转为圆角版本。失败时原样返回。
 * @param radiusFraction 圆角半径占图标尺寸的比例，默认 0.25（25%）
 */
fun Icon.toRounded(context: Context, radiusFraction: Float = 0.25f): Icon {
    if (!isRoundIconEnabled()) return this
    return try {
        val drawable = loadDrawable(context) ?: return this
        val size = 192
        val src = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(Canvas(src))

        val dst = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dst)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = size * radiusFraction
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), r, r, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(src, 0f, 0f, paint)
        src.recycle()

        Icon.createWithBitmap(dst)
    } catch (_: Exception) {
        this
    }
}

private fun isRoundIconEnabled(): Boolean =
    ConfigManager.getBoolean("pref_round_icon", true)
