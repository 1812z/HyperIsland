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
import java.lang.ref.WeakReference
import java.util.WeakHashMap

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

private data class RoundedIconCacheEntry(
    val radiusPercent: Int,
    val icon: WeakReference<Icon>,
)

private val roundedIconCache = WeakHashMap<Icon, RoundedIconCacheEntry>()
private val roundedIconOutputs = WeakHashMap<Icon, Boolean>()
private val roundedIconLock = Any()

/** Applies the configured notification icon corner radius once per source icon. */
fun Icon.toRounded(context: Context): Icon {
    if (!ConfigManager.getBoolean("pref_round_icon", true)) return this
    val radiusPercent = ConfigManager.getInt("pref_round_icon_radius", 40).coerceIn(0, 100)
    if (radiusPercent == 0) return this

    synchronized(roundedIconLock) {
        if (roundedIconOutputs.containsKey(this)) return this
        roundedIconCache[this]?.takeIf { it.radiusPercent == radiusPercent }?.icon?.get()?.let {
            return it
        }

        return runCatching {
            val drawable = loadDrawable(context) ?: return this
            val size = 192
            val src = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            try {
                drawable.setBounds(0, 0, size, size)
                drawable.draw(Canvas(src))

                val dst = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(dst)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                val radius = size * radiusPercent / 200f
                canvas.drawRoundRect(
                    RectF(0f, 0f, size.toFloat(), size.toFloat()),
                    radius,
                    radius,
                    paint,
                )
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                canvas.drawBitmap(src, 0f, 0f, paint)

                Icon.createWithBitmap(dst).also { rounded ->
                    roundedIconCache[this] = RoundedIconCacheEntry(
                        radiusPercent,
                        WeakReference(rounded),
                    )
                    roundedIconOutputs[rounded] = true
                }
            } finally {
                src.recycle()
            }
        }.getOrElse { this }
    }
}
