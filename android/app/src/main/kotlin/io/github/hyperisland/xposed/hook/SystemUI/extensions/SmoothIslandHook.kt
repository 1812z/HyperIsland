package io.github.hyperisland.xposed.hook.SystemUI.extensions

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.LinkedHashMap
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.min

/**
 * 将超级岛普通圆角胶囊替换为连续曲率的平滑胶囊。
 *
 * Hook 会在 SystemUI 启动时按开关决定是否注册；已注册后如果用户关闭开关，
 * 拦截入口也会完全旁路，不再修改 Outline 或 Drawable。
 */
object SmoothIslandHook : BaseHook() {

    private const val TAG = "HyperIsland[SmoothIsland]"
    private const val KEY_ENABLED = "pref_smooth_island"
    private const val KEY_SMOOTHING = "pref_smooth_island_smoothing"
    private const val DEFAULT_SMOOTHING = 0.8f
    private const val MIN_SMOOTHING = 0.0f
    private const val MAX_SMOOTHING = 1.0f
    private const val MAX_PATH_CACHE_SIZE = 32
    private const val MIN_SMOOTH_RADIUS = 4f
    private const val CAPSULE_RADIUS_TOLERANCE = 1.5f

    private const val BACKGROUND_VIEW_CLASS =
        "miui.systemui.dynamicisland.DynamicIslandBackgroundView"

    private val targetProviderClasses = listOf(
        "com.android.systemui.statusbar.notification.DynamicIslandWindowAnimController\$updateFakeViewOutline\$1",
        "com.android.systemui.statusbar.notification.mediaisland.MiuiIslandMediaViewHolder\$Companion\$create\$1\$1",
    )
    private val dynamicIslandCallers = listOf("dynamicisland", "mediaisland")
    private val excludedOutlineCallers = listOf(
        "footerview",
        "footerviewbutton",
        "notificationstackscrolllayout",
        "notif_footer",
    )

    private val pathCache = object : LinkedHashMap<ShapeKey, Path>(MAX_PATH_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ShapeKey, Path>?): Boolean {
            return size > MAX_PATH_CACHE_SIZE
        }
    }
    private val capturedOutlineRects = java.util.Collections.synchronizedMap(WeakHashMap<View, Rect>())
    private val activeOutlineTarget = ThreadLocal<View?>()
    private val fillPaint = ThreadLocal.withInitial {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    }
    private val strokePaint = ThreadLocal.withInitial {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    }
    private val hookedProviderClassLoaders = mutableSetOf<Int>()

    @Volatile private var enabled = false
    @Volatile private var smoothing = DEFAULT_SMOOTHING
    @Volatile private var outlineHooked = false
    @Volatile private var outlineLifecycleHooked = false
    @Volatile private var pluginHooksInstalled = false
    @Volatile private var drawFallbackLogged = false
    @Volatile private var getDrawableMethod: java.lang.reflect.Method? = null
    @Volatile private var getStokeWidthMethod: java.lang.reflect.Method? = null
    @Volatile private var drawableStrokePaintField: java.lang.reflect.Field? = null

    override fun getTag() = TAG

    override fun onConfigChanged() {
        loadConfig()
        synchronized(pathCache) { pathCache.clear() }
    }

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        if (param.packageName != "com.android.systemui") return
        loadConfig()
        if (!enabled) return
        hookOutlineRoundRect(module)
        hookViewOutlineLifecycle(module)
        hookTargetOutlineProviders(module, param.defaultClassLoader)
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { classLoader ->
            hookTargetOutlineProviders(module, classLoader)
            hookPlugin(module, classLoader)
        }
        hookPlugin(module, param.defaultClassLoader)
    }

    private fun hookOutlineRoundRect(module: XposedModule) {
        if (outlineHooked) return
        try {
            val method = Outline::class.java.getDeclaredMethod(
                "setRoundRect",
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                Float::class.javaPrimitiveType!!,
            )
            module.hook(method).intercept { chain ->
                if (!enabled) return@intercept chain.proceed()
                val left = chain.args.getOrNull(0) as? Int ?: return@intercept chain.proceed()
                val top = chain.args.getOrNull(1) as? Int ?: return@intercept chain.proceed()
                val right = chain.args.getOrNull(2) as? Int ?: return@intercept chain.proceed()
                val bottom = chain.args.getOrNull(3) as? Int ?: return@intercept chain.proceed()
                val radius = chain.args.getOrNull(4) as? Float ?: return@intercept chain.proceed()
                val height = (bottom - top).toFloat()
                val clampedRadius = min(radius, height / 2f)
                val dynamicIslandCall = isDynamicIslandOutlineCall()
                val target = activeOutlineTarget.get()
                if (dynamicIslandCall && target != null && right > left && bottom > top) {
                    capturedOutlineRects[target] = Rect(left, top, right, bottom)
                }
                if (
                    height > 10f &&
                    right > left &&
                    clampedRadius >= MIN_SMOOTH_RADIUS &&
                    clampedRadius >= (height / 2f) - CAPSULE_RADIUS_TOLERANCE &&
                    dynamicIslandCall
                ) {
                    (chain.thisObject as? Outline)?.setPath(
                        createSmoothPath(
                            left.toFloat(),
                            top.toFloat(),
                            right.toFloat(),
                            bottom.toFloat(),
                            clampedRadius,
                        ),
                    )
                    null
                } else {
                    chain.proceed()
                }
            }
            outlineHooked = true
            log(module, "hooked Outline.setRoundRect")
        } catch (e: Throwable) {
            logError(module, "hookOutlineRoundRect failed: ${e.message}")
        }
    }

    private fun hookViewOutlineLifecycle(module: XposedModule) {
        if (outlineLifecycleHooked) return
        try {
            val invalidateOutline = View::class.java.getDeclaredMethod("invalidateOutline")
            module.hook(invalidateOutline).intercept { chain ->
                withActiveOutlineTarget(chain.thisObject as? View) { chain.proceed() }
            }
        } catch (_: Throwable) {
        }
        try {
            val setOutlineProvider = View::class.java.getDeclaredMethod(
                "setOutlineProvider",
                ViewOutlineProvider::class.java,
            )
            module.hook(setOutlineProvider).intercept { chain ->
                withActiveOutlineTarget(chain.thisObject as? View) { chain.proceed() }
            }
        } catch (_: Throwable) {
        }
        outlineLifecycleHooked = true
    }

    private inline fun <T> withActiveOutlineTarget(target: View?, block: () -> T): T {
        val previousTarget = activeOutlineTarget.get()
        activeOutlineTarget.set(target)
        return try {
            block()
        } finally {
            if (previousTarget == null) activeOutlineTarget.remove() else activeOutlineTarget.set(previousTarget)
        }
    }

    private fun hookTargetOutlineProviders(module: XposedModule, classLoader: ClassLoader) {
        val clId = System.identityHashCode(classLoader)
        if (!hookedProviderClassLoaders.add(clId)) return
        targetProviderClasses.forEach { className ->
            try {
                val clazz = Class.forName(className, false, classLoader)
                val method = clazz.getDeclaredMethod("getOutline", View::class.java, Outline::class.java)
                module.hook(method).intercept { chain ->
                    withActiveOutlineTarget(chain.args.getOrNull(0) as? View) {
                        val result = chain.proceed()
                        if (enabled) overrideOutlineIfCapsule(chain.args.getOrNull(1) as? Outline)
                        result
                    }
                }
                log(module, "hooked $className.getOutline")
            } catch (_: Throwable) {
            }
        }
    }

    private fun hookPlugin(module: XposedModule, classLoader: ClassLoader) {
        if (pluginHooksInstalled) return
        try {
            val backgroundViewClass = Class.forName(BACKGROUND_VIEW_CLASS, false, classLoader)
            getDrawableMethod = backgroundViewClass.getMethod("getDrawable")
            getStokeWidthMethod = backgroundViewClass.getMethod("getStokeWidth")
            drawableStrokePaintField = GradientDrawable::class.java.getDeclaredField("mStrokePaint").apply {
                isAccessible = true
            }
            val onDraw = backgroundViewClass.getDeclaredMethod("onDraw", Canvas::class.java)
            module.hook(onDraw).intercept { chain ->
                if (!enabled) return@intercept chain.proceed()
                val view = chain.thisObject
                val canvas = chain.args.getOrNull(0) as? Canvas
                if (view != null && canvas != null && drawSmoothIsland(view, canvas)) null else chain.proceed()
            }
            pluginHooksInstalled = true
            log(module, "hooked plugin smooth island")
        } catch (_: Throwable) {
        }
    }

    private fun overrideOutlineIfCapsule(outline: Outline?) {
        if (outline == null) return
        val bounds = Rect()
        if (outline.getRect(bounds) && bounds.height() > 10) {
            val height = bounds.height()
            if (abs(outline.radius - (height / 2f)) <= 1.5f) {
                outline.setPath(
                    createSmoothPath(
                        bounds.left.toFloat(),
                        bounds.top.toFloat(),
                        bounds.right.toFloat(),
                        bounds.bottom.toFloat(),
                        min(outline.radius, height / 2f),
                    ),
                )
            }
        }
    }

    private fun createSmoothPath(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        radius: Float,
    ): Path {
        val width = right - left
        val height = bottom - top
        if (width <= 0 || height <= 0) return Path()
        val path = Path(getBasePath(width, height, radius))
        path.offset(left + width / 2f, top + height / 2f)
        return path
    }

    private fun getBasePath(width: Float, height: Float, radius: Float): Path {
        val key = ShapeKey(width, height, radius, smoothing)
        synchronized(pathCache) {
            pathCache[key]?.let { return it }
        }
        val halfWidth = width / 2f
        val halfHeight = height / 2f
        val generated = RoundedPolygon(
            vertices = floatArrayOf(
                -halfWidth, -halfHeight,
                halfWidth, -halfHeight,
                halfWidth, halfHeight,
                -halfWidth, halfHeight,
            ),
            rounding = CornerRounding(
                radius = radius.coerceIn(0f, min(halfWidth, halfHeight)),
                smoothing = smoothing,
            ),
            centerX = 0f,
            centerY = 0f,
        ).toPath()
        synchronized(pathCache) { pathCache[key] = generated }
        return generated
    }

    private fun isDynamicIslandOutlineCall(): Boolean {
        var matched = false
        Thread.currentThread().stackTrace.forEach { frame ->
            val name = frame.className.lowercase()
            if (excludedOutlineCallers.any { name.contains(it) }) return false
            if (dynamicIslandCallers.any { name.contains(it) }) matched = true
        }
        return matched
    }

    private fun drawSmoothIsland(view: Any, canvas: Canvas): Boolean {
        try {
            val drawable = getDrawableMethod?.invoke(view) as? GradientDrawable ?: return false
            if (drawable.shape != GradientDrawable.RECTANGLE) return false
            if (drawable.cornerRadii != null || drawable.colors != null) return false
            val fillColors = drawable.color ?: return false
            val boundsOutset = getStokeWidthMethod?.invoke(view) as? Int ?: return false
            val liveRect = liveIslandRect(view) ?: return false
            val left = (liveRect.left - boundsOutset).toFloat()
            val top = (liveRect.top - boundsOutset).toFloat()
            val right = (liveRect.right + boundsOutset).toFloat()
            val bottom = (liveRect.bottom + boundsOutset).toFloat()
            val sourceStrokePaint = drawableStrokePaintField?.get(drawable) as? Paint
            val strokeWidth = sourceStrokePaint?.strokeWidth?.coerceAtLeast(0f) ?: 0f
            val strokeColor = sourceStrokePaint?.color
            val halfStroke = strokeWidth / 2f
            val width = (right - left) - strokeWidth
            val height = (bottom - top) - strokeWidth
            if (width <= 0f || height <= 10f) return false

            val bodyHeight = (bottom - top) - (2f * boundsOutset)
            val bodyRadius = min(drawable.cornerRadius, bodyHeight / 2f)
            if (bodyRadius < MIN_SMOOTH_RADIUS) return false
            if (bodyRadius < (bodyHeight / 2f) - CAPSULE_RADIUS_TOLERANCE) return false
            val radius = min(bodyRadius + (boundsOutset - halfStroke), height / 2f)
            val path = getBasePath(width, height, radius)
            val drawableAlpha = drawable.alpha
            val fillColor = fillColors.getColorForState(drawable.state, fillColors.defaultColor)

            canvas.save()
            canvas.translate(left + halfStroke + width / 2f, top + halfStroke + height / 2f)
            if (Color.alpha(fillColor) != 0) {
                val paint = fillPaint.get()!!
                paint.color = modulateAlpha(fillColor, drawableAlpha)
                canvas.drawPath(path, paint)
            }
            if (strokeWidth > 0f && strokeColor != null && Color.alpha(strokeColor) != 0) {
                val paint = strokePaint.get()!!
                paint.color = modulateAlpha(strokeColor, drawableAlpha)
                paint.strokeWidth = strokeWidth
                canvas.drawPath(path, paint)
            }
            canvas.restore()
            return true
        } catch (t: Throwable) {
            if (!drawFallbackLogged) {
                drawFallbackLogged = true
                android.util.Log.w(TAG, "drawSmoothIsland fell back to MIUI drawing", t)
            }
            return false
        }
    }

    private fun liveIslandRect(view: Any): Rect? {
        val background = view as? ViewGroup ?: return null
        for (index in 0 until background.childCount) {
            val child = background.getChildAt(index)
            val captured = capturedOutlineRects[child] ?: continue
            val offsetX = child.left + child.translationX.toInt()
            val offsetY = child.top + child.translationY.toInt()
            return Rect(
                captured.left + offsetX,
                captured.top + offsetY,
                captured.right + offsetX,
                captured.bottom + offsetY,
            )
        }
        return null
    }

    private fun modulateAlpha(color: Int, alpha: Int): Int {
        if (alpha >= 255) return color
        val scaledAlpha = Color.alpha(color) * alpha / 255
        return (color and 0x00FFFFFF) or (scaledAlpha shl 24)
    }

    private fun loadConfig() {
        enabled = ConfigManager.getBoolean(KEY_ENABLED, false)
        smoothing = ConfigManager.getFloat(KEY_SMOOTHING, DEFAULT_SMOOTHING)
            .coerceIn(MIN_SMOOTHING, MAX_SMOOTHING)
    }

    private data class ShapeKey(
        val width: Float,
        val height: Float,
        val radius: Float,
        val smoothing: Float,
    )
}
