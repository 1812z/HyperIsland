package io.github.hyperisland.homeassistant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Icon
import androidx.core.content.res.ResourcesCompat
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial

/**
 * Resolves Home Assistant `mdi:<name>` icons through the bundled MDI typeface.
 *
 * IslandDispatcher accepts Android [Icon]. The MDI glyph is rasterized from
 * the bundled Community Material font before crossing the SystemUI boundary.
 * This deliberately avoids IconicsDrawable: in SystemUI, its ResourcesCompat
 * lookup can resolve a host animated-vector resource instead of the module's
 * font resource and produce a malformed-font XML error.
 *
 * RemoteViews（焦点通知的自定义面板）不能塞 [Icon]，只有 [renderBitmap] 一条路。
 */
object HomeAssistantIconResolver {
    private const val ICON_SIZE_PX = 96
    private val iconIndex by lazy {
        buildMap<String, IIcon> {
            fun add(values: Array<out IIcon>) {
                values.forEach { value ->
                    val enumName = (value as Enum<*>).name
                    val normalized = enumName.lowercase()
                        .removePrefix("cmd_")
                        .removePrefix("community_material_")
                        .replace('_', '-')
                    put(normalized, value)
                }
            }
            // CommunityMaterial splits the MDI catalogue across three enums.
            // The previous implementation indexed only Icon, which excluded
            // common entries such as cmd_power and cmd_power_plug in Icon3.
            add(CommunityMaterial.Icon.values())
            add(CommunityMaterial.Icon2.values())
            add(CommunityMaterial.Icon3.values())
        }
    }

    data class Resolution(
        val icon: Icon?,
        val input: String,
        val normalized: String,
        val matchedEnum: String?,
        val renderedPixels: Int,
        val reason: String,
    )

    fun resolve(iconName: String, context: Context): Icon? =
        resolveWithDiagnostics(iconName, context).icon

    /**
     * 按指定颜色把 MDI 字形渲染成位图，供 RemoteViews（焦点通知面板）使用。
     * 找不到字形 / 字体资源时返回 null，调用方自行隐藏图标位。
     */
    fun renderBitmap(
        iconName: String,
        context: Context,
        color: Int,
        sizePx: Int = ICON_SIZE_PX,
    ): Bitmap? = lookup(iconName)?.let { renderGlyph(it, context, color, sizePx) }

    fun resolveWithDiagnostics(iconName: String, context: Context): Resolution {
        val normalized = normalize(iconName)
        if (normalized.isBlank()) return Resolution(null, iconName, "", null, 0, "empty-icon-name")
        val icon = lookup(iconName)
            ?: return Resolution(
                null,
                iconName,
                normalized,
                null,
                0,
                "enum-not-found candidates=${iconIndex.keys.filter { it.contains("power") || it.contains("plug") }.take(12)}",
            )
        val bitmap = renderGlyph(icon, context, Color.WHITE, ICON_SIZE_PX)
            ?: return Resolution(null, iconName, normalized, icon.name, 0, "font-load-failed")
        var pixels = 0
        for (y in 0 until ICON_SIZE_PX) {
            for (x in 0 until ICON_SIZE_PX) {
                if ((bitmap.getPixel(x, y) ushr 24) != 0) pixels++
            }
        }
        return Resolution(
            icon = Icon.createWithBitmap(bitmap),
            input = iconName,
            normalized = normalized,
            matchedEnum = icon.name,
            renderedPixels = pixels,
            reason = if (pixels == 0) "blank-render" else "ok",
        )
    }

    /** `mdi:power-plug` / `power_plug` / `cmd_power_plug` 都归一化成 `power-plug`。 */
    private fun normalize(iconName: String): String =
        iconName.substringAfter(':', iconName).trim().lowercase().replace('_', '-')

    private fun lookup(iconName: String): IIcon? {
        val normalized = normalize(iconName)
        if (normalized.isBlank()) return null
        return iconIndex[normalized]
            ?: iconIndex.entries.firstOrNull { (key, _) -> key.endsWith(normalized) }?.value
            ?: iconIndex.entries.firstOrNull { (key, _) -> key.contains(normalized) }?.value
    }

    private fun renderGlyph(icon: IIcon, context: Context, color: Int, sizePx: Int): Bitmap? {
        val moduleContext = runCatching {
            context.createPackageContext("io.github.hyperisland", Context.CONTEXT_IGNORE_SECURITY)
        }.getOrElse { context }
        val typeface = runCatching {
            ResourcesCompat.getFont(moduleContext, CommunityMaterial.fontRes)
        }.getOrNull() ?: return null
        return runCatching {
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                this.typeface = typeface
                textSize = sizePx * 0.82f
                textAlign = Paint.Align.CENTER
            }
            val metrics = paint.fontMetrics
            val baseline = sizePx / 2f - (metrics.ascent + metrics.descent) / 2f
            canvas.drawText(icon.character.toString(), sizePx / 2f, baseline, paint)
            bitmap
        }.getOrNull()
    }
}
