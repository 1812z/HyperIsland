package io.github.hyperisland.xposed

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.Icon
import android.os.Bundle
import io.github.hyperisland.xposed.SettingsBridge

/**
 * 在 Xposed 进程（如 SystemUI）中，[Context] 属于宿主进程，不包含本模块的资源。
 * 此函数通过 [Context.createPackageContext] 创建一个包含模块资源的上下文，
 * 使 [Context.getString] 能正确按设备语言加载模块的字符串资源。
 * 在普通 App 进程中 context 已经是模块自身，无需特殊处理，异常时直接返回 this。
 */
internal fun Context.moduleContext(): Context = try {
    createPackageContext("io.github.hyperisland", Context.CONTEXT_IGNORE_SECURITY)
} catch (_: Exception) { this }

/**
 * 将 Icon 转为圆角版本。失败时原样返回。
 * @param radiusFraction 圆角半径占图标尺寸的比例，默认 0.25（25%）
 */
fun Icon.toRounded(context: Context, radiusFraction: Float = 0.25f): Icon {
    if (!isRoundIconEnabled(context)) return this
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

private fun isRoundIconEnabled(context: Context): Boolean =
    SettingsBridge.run {
        init(context)
        getBoolean("pref_round_icon", true)
    }

/**
 * 灵动岛通知模板接口。
 *
 * 新增模板步骤：
 *  1. 创建 object 实现此接口，id 与 Flutter 侧常量对应
 *  2. 在 TemplateRegistry.registry 中添加一行
 */
interface IslandTemplate {
    /** 唯一标识符，与 Flutter 侧 kTemplate* 常量对应。 */
    val id: String

    /** 将通知数据注入 extras，使其触发灵动岛展示。 */
    fun inject(context: Context, extras: Bundle, data: NotifData)
}

/**
 * GenericProgressHook 从通知提取的结构化数据，供各模板统一接收。
 */
data class NotifData(
    val pkg: String,
    val channelId: String,
    val notifId: Int,
    val title: String,
    val subtitle: String,
    val progress: Int,
    val actions: List<Notification.Action>,
    /** 通知小图标（smallIcon）。 */
    val notifIcon: Icon?,
    /** 通知大图标（头像、封面等）。 */
    val largeIcon: Icon?,
    /** 应用图标（来自 PackageManager）。 */
    val appIconRaw: Icon? = null,
    // ── 渠道级覆盖设置 ────────────────────────────────────────────────────────
    /** 超级岛区域图标来源（bigIslandArea / smallIslandArea）："auto" / "notif_small" / "notif_large" / "app_icon" */
    val iconMode: String = "auto",
    /** 焦点图标来源（iconTextInfo）："auto" / "notif_small" / "notif_large" / "app_icon" */
    val focusIconMode: String = "auto",
    /** 焦点通知（island 块）：“default” / “off” */
    val focusNotif: String = "default",
    /** 是否保留状态栏左上角小图标："default" / "on" / "off" */
    val preserveStatusBarSmallIcon: String = "default",
    /** 初次自动展开 islandFirstFloat：“default” / “on” / “off” */
    val firstFloat: String = "default",
    /** 更新时自动展开 enableFloat："default" / "on" / "off" */
    val enableFloatMode: String = "default",
    /** 超级岛自动消失时间，默认 5 */
    val islandTimeout: Int = 5,
    /** 是否为实时（持续）通知，对应 Notification.FLAG_ONGOING_EVENT */
    val isOngoing: Boolean = false,
    /** 原通知的点击动作，用于代发通知时还原点击行为。 */
    val contentIntent: android.app.PendingIntent? = null,
    /** 渲染器（样式）标识符，对应 ImageTextWithButtonsRenderer.RENDERER_ID 等。 */
    val renderer: String = "image_text_with_buttons_4",
)
