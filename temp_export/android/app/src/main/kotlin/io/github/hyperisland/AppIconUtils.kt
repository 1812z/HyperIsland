package io.github.hyperisland

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon

/**
 * 将 Drawable 渲染为指定尺寸的 Bitmap。
 * AdaptiveIconDrawable.intrinsicWidth/Height 返回 -1，必须用固定尺寸。
 */
fun Drawable.toBitmap(size: Int): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    setBounds(0, 0, size, size)
    draw(Canvas(bmp))
    return bmp
}

/**
 * 从 PackageManager 获取应用图标并渲染为 Icon。
 * 失败时返回 null。
 */
fun android.content.pm.PackageManager.getAppIcon(packageName: String, size: Int = 192): Icon? {
    return try {
        Icon.createWithBitmap(getApplicationIcon(packageName).toBitmap(size))
    } catch (_: Exception) {
        null
    }
}

