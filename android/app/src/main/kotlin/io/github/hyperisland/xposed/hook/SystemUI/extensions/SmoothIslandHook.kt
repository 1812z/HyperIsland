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
        // HyperOS 3 SystemUI / media island.
        "com.android.systemui.statusbar.notification.DynamicIslandWindowAnimController\$updateFakeViewOutline\$1",
        "com.android.systemui.statusbar.notification.mediaisland.MiuiIslandMediaViewHolder\$Companion\$create\$1\$1",
        // HyperOS 4 dynamic-island plugin. The main container provider is R8-inlined,
        // so it is still covered by the scoped Outline hook below.
        "miui.systemui.dynamicisland.window.content.DynamicIslandContentFakeView\$updateExpandViewBlur\$1\$1",
        "miui.systemui.dynamicisland.window.content.DynamicIslandContentView\$updateExpandViewBlur\$1\$1",
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
    private val installingOs4OutlineProvider = ThreadLocal<Boolean>()
    private val fillPaint = ThreadLocal.withInitial {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    }
    private val strokePaint = ThreadLocal.withInitial {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    }
    private val hookedProviderClasses = java.util.Collections.synchronizedSet(
        java.util.Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>()),
    )
    private val os4OfficialSmoothDisabledViews = java.util.Collections.synchronizedSet(
        java.util.Collections.newSetFromMap(WeakHashMap<View, Boolean>()),
    )

    @Volatile private var enabled = false
    @Volatile private var smoothing = DEFAULT_SMOOTHING
    @Volatile private var outlineHooked = false
    @Volatile private var outlineLifecycleHooked = false
    @Volatile private var pluginHooksInstalled = false
    @Volatile private var os4OfficialSmoothMode = false
    @Volatile private var drawFallbackLogged = false
    @Volatile private var getDrawableMethod: java.lang.reflect.Method? = null
    @Volatile private var getStokeWidthMethod: java.lang.reflect.Method? = null
    @Volatile private var getActualLeftMethod: java.lang.reflect.Method? = null
    @Volatile private var getActualTopMethod: java.lang.reflect.Method? = null
    @Volatile private var getActualWidthMethod: java.lang.reflect.Method? = null
    @Volatile private var getActualHeightMethod: java.lang.reflect.Method? = null
    @Volatile private var drawableStrokePaintField: java.lang.reflect.Field? = null
    @Volatile private var setViewSmoothCornerEnabledMethod: java.lang.reflect.Method? = null

    override fun getTag() = TAG

    override fun onConfigChanged() {
        val wasEnabled = enabled
        loadConfig()
        synchronized(pathCache) { pathCache.clear() }
        if (wasEnabled && !enabled) restoreOfficialSmoothCorners()
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
                    if (os4OfficialSmoothMode) disableOfficialSmoothCorner(target)
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
                val view = chain.thisObject as? View
                val provider = chain.args.getOrNull(0) as? ViewOutlineProvider
                val result = withActiveOutlineTarget(view) { chain.proceed() }
                if (
                    enabled &&
                    os4OfficialSmoothMode &&
                    installingOs4OutlineProvider.get() != true &&
                    view != null &&
                    provider != null &&
                    provider !is Os4SmoothOutlineProvider &&
                    isDynamicIslandOutlineCall()
                ) {
                    installOs4OutlineProvider(view, provider)
                }
                result
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
        targetProviderClasses.forEach { className ->
            try {
                val clazz = Class.forName(className, false, classLoader)
                if (!hookedProviderClasses.add(clazz)) return@forEach
                val method = clazz.getDeclaredMethod("getOutline", View::class.java, Outline::class.java)
                module.hook(method).intercept { chain ->
                    val target = chain.args.getOrNull(0) as? View
                    withActiveOutlineTarget(target) {
                        val result = chain.proceed()
                        if (enabled) overrideOutlineIfCapsule(target, chain.args.getOrNull(1) as? Outline)
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
            os4OfficialSmoothMode = detectOs4OfficialSmooth(classLoader)
            getDrawableMethod = backgroundViewClass.getMethod("getDrawable")
            getStokeWidthMethod = backgroundViewClass.getMethod("getStokeWidth")
            getActualLeftMethod = backgroundViewClass.getMethod("getActualLeft")
            getActualTopMethod = backgroundViewClass.getMethod("getActualTop")
            getActualWidthMethod = backgroundViewClass.getMethod("getActualWidth")
            getActualHeightMethod = backgroundViewClass.getMethod("getActualHeight")
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
            log(module, "hooked plugin smooth island; os4OfficialSmooth=$os4OfficialSmoothMode")
        } catch (_: Throwable) {
        }
    }

    private fun overrideOutlineIfCapsule(target: View?, outline: Outline?): Boolean {
        if (outline == null) return false
        val bounds = Rect()
        if (outline.getRect(bounds) && bounds.height() > 10) {
            val height = bounds.height()
            if (abs(outline.radius - (height / 2f)) <= 1.5f) {
                if (os4OfficialSmoothMode) disableOfficialSmoothCorner(target)
                outline.setPath(
                    createSmoothPath(
                        bounds.left.toFloat(),
                        bounds.top.toFloat(),
                        bounds.right.toFloat(),
                        bounds.bottom.toFloat(),
                        min(outline.radius, height / 2f),
                    ),
                )
                return true
            }
        }
        return false
    }

    /**
     * HyperOS 4 enables framework smooth corners on the Bionics material View. Its
     * curve is fixed in the renderer and wins over a custom round-rect Outline.
     * Wrap SystemUI's provider so its geometry/RenderNode side effects stay intact,
     * then disable only that View's official smooth flag and replace capsule outlines.
     */
    private class Os4SmoothOutlineProvider(
        private val delegate: ViewOutlineProvider,
    ) : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            delegate.getOutline(view, outline)
            if (enabled) overrideOutlineIfCapsule(view, outline)
        }
    }

    private fun installOs4OutlineProvider(view: View, provider: ViewOutlineProvider) {
        installingOs4OutlineProvider.set(true)
        try {
            view.outlineProvider = Os4SmoothOutlineProvider(provider)
            view.invalidateOutline()
        } finally {
            installingOs4OutlineProvider.remove()
        }
    }

    private fun disableOfficialSmoothCorner(view: View?) {
        if (view == null) return
        if (!os4OfficialSmoothDisabledViews.add(view)) return
        try {
            setViewSmoothCornerEnabledMethod?.invoke(view, false)
        } catch (_: Throwable) {
            os4OfficialSmoothDisabledViews.remove(view)
        }
    }

    private fun restoreOfficialSmoothCorners() {
        val views = synchronized(os4OfficialSmoothDisabledViews) {
            os4OfficialSmoothDisabledViews.toList().also { os4OfficialSmoothDisabledViews.clear() }
        }
        views.forEach { view ->
            try {
                setViewSmoothCornerEnabledMethod?.invoke(view, true)
                view.invalidateOutline()
            } catch (_: Throwable) {
            }
        }
    }

    private fun detectOs4OfficialSmooth(classLoader: ClassLoader): Boolean {
        return try {
            val styleClass = Class.forName("miui.systemui.util.MiBackgroundStyle", false, classLoader)
            styleClass.getDeclaredMethod("isBionicsActive", android.content.Context::class.java)
            val tokenClass = Class.forName("miui.systemui.util.BionicsToken", false, classLoader)
            tokenClass.getDeclaredMethod("getToBionicsParams")
            Class.forName(
                "miui.systemui.dynamicisland.window.content.DynamicIslandBaseContentView",
                false,
                classLoader,
            ).getDeclaredField("EXPANDED_GLASS_TOKEN")
            setViewSmoothCornerEnabledMethod = View::class.java.getDeclaredMethod(
                "setSmoothCornerEnabled",
                Boolean::class.javaPrimitiveType!!,
            ).apply { isAccessible = true }
            true
        } catch (_: Throwable) {
            setViewSmoothCornerEnabledMethod = null
            false
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
        // OS3 and OS4 both update these four values from the active container outline.
        // Prefer them because OS4 inlines its main ViewOutlineProvider and therefore has
        // no stable provider class name to hook. Despite the names, actualWidth/Height
        // are the right/bottom coordinates used directly by SystemUI's onDraw().
        if (os4OfficialSmoothMode) try {
            val left = getActualLeftMethod?.invoke(view) as? Int
            val top = getActualTopMethod?.invoke(view) as? Int
            val right = getActualWidthMethod?.invoke(view) as? Int
            val bottom = getActualHeightMethod?.invoke(view) as? Int
            if (left != null && top != null && right != null && bottom != null &&
                right > left && bottom > top
            ) {
                return Rect(left, top, right, bottom)
            }
        } catch (_: Throwable) {
        }

        // HyperOS 3 and early plugin builds may update their outline before actual*
        // becomes valid. Keep the captured child-outline path as a compatibility fallback.
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
