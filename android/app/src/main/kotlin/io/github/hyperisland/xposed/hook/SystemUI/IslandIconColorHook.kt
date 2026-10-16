package io.github.hyperisland.xposed.hook.SystemUI

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Animatable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.hyperisland.xposed.hook.StatusBarTextColorHook
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Makes near-white icons in the left/right small-island slots readable on a light island.
 *
 * This deliberately hooks the common SystemUI IslandIconViewHolder instead of looking at
 * HyperIsland notification data, so icons supplied by other applications are covered too.
 * The holder's module name is used to exclude focus/expanded content and the center/small
 * island icon slot.
 */
object IslandIconColorHook : BaseHook() {
    private const val TAG = "HyperIsland[IslandIconColor]"
    private const val HOLDER_CLASS =
        "miui.systemui.dynamicisland.module.IslandIconViewHolder"
    private const val ICON_CONTAINER_FIELD = "iconContainer"
    private const val WHITE_THRESHOLD = 245
    private const val MIN_VISIBLE_PIXELS = 8
    private const val MIN_WHITE_RATIO = 0.98f
    private const val ICON_SAMPLE_SIZE = 64
    private const val KEY_ICON_COLOR_MODE = "pref_island_icon_color_mode"
    private const val MODE_DEFAULT = "default"
    private const val MODE_BLACK = "black"
    private const val MODE_FOLLOW_STATUS_BAR = "follow_status_bar"
    private const val MODE_INVERT_STATUS_BAR = "invert_status_bar"
    private val SIDE_MODULES = setOf(
        "modulepicleftisland",
        "modulepicrightisland",
        // Official small-island icon slot.
        "modulepicsmallisland",
    )

    private val hookedClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>()),
    )
    private val trackedIcons = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ImageView, Boolean>()),
    )
    private val originalFilters = Collections.synchronizedMap(
        WeakHashMap<ImageView, android.graphics.ColorFilter?>(),
    )
    private val pendingContainers = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<View, Boolean>()),
    )
    private val lastDrawables = Collections.synchronizedMap(
        WeakHashMap<ImageView, Drawable>(),
    )
    private val whiteResults = Collections.synchronizedMap(
        WeakHashMap<Drawable.ConstantState, Boolean>(),
    )
    private val blackFilter = PorterDuffColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN)
    private val diagnosticCount = AtomicInteger(0)
    @Volatile private var lastStatusBarFilterState: Boolean? = null
    private val statusBarTintListener: (Int) -> Unit = listener@{
        if (currentMode() == MODE_BLACK || currentMode() == MODE_DEFAULT) return@listener
        val shouldUseFilter = shouldUseBlackFilter()
        if (lastStatusBarFilterState != shouldUseFilter) {
            lastStatusBarFilterState = shouldUseFilter
            reapplyTrackedIcons()
        }
    }

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        StatusBarTextColorHook.addReadableTintListener(statusBarTintListener)
        hookHolder(module, param.defaultClassLoader)
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { loader ->
            hookHolder(module, loader)
        }
    }

    override fun onConfigChanged() {
        lastStatusBarFilterState = null
        reapplyTrackedIcons()
    }

    private fun hookHolder(module: XposedModule, classLoader: ClassLoader) {
        runCatching {
            val holderClass = classLoader.loadClass(HOLDER_CLASS)
            if (!hookedClasses.add(holderClass)) return

            val containerField = holderClass.getDeclaredField(ICON_CONTAINER_FIELD).apply {
                isAccessible = true
            }
            val getModule = holderClass.getMethod("getModule")
            val bindMethods = holderClass.declaredMethods.filter {
                it.name == "bind" && it.parameterCount == 2
            }
            bindMethods.forEach { method ->
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    val holder = chain.thisObject
                    if (holder != null) {
                        val container = containerField.get(holder) as? View
                        val moduleValue = runCatching { getModule.invoke(holder) }.getOrNull()
                        logDiagnostic(module, moduleValue, container)
                        if (isSideModule(moduleValue)) {
                            // SystemUI may replace/reset the ImageView drawable a moment after
                            // bind() returns. Recheck on the next frame and once after the
                            // notification update settles, without polling continuously.
                            scheduleApply(container)
                            container?.let { target ->
                                target.postDelayed({ trackAndApply(target) }, 16L)
                                target.postDelayed({ trackAndApply(target) }, 64L)
                                target.postDelayed({ trackAndApply(target) }, 160L)
                            }
                        }
                    }
                    result
                }
            }
            log(module) { "hooked global island side-icon color bind=${bindMethods.size}" }
        }.onFailure { error ->
            hookedClasses.removeIf { it.name == HOLDER_CLASS }
            if (error !is ClassNotFoundException) {
                logError(module, "failed to hook $HOLDER_CLASS: ${error.message}")
            }
        }
    }

    private fun scheduleApply(container: View?) {
        if (container == null) return
        synchronized(pendingContainers) {
            if (!pendingContainers.add(container)) return
        }
        container.post {
            pendingContainers.remove(container)
            trackAndApply(container)
        }
    }

    private fun trackAndApply(container: View) {
        if (container !is ViewGroup) return
        visit(container) { imageView ->
            trackedIcons.add(imageView)
            applyToIcon(imageView)
        }
    }

    private fun reapplyTrackedIcons() {
        val icons = synchronized(trackedIcons) { trackedIcons.toList() }
        icons.forEach { imageView -> imageView.post { applyToIcon(imageView) } }
    }

    private fun visit(view: View, action: (ImageView) -> Unit) {
        when (view) {
            is ImageView -> action(view)
            is ViewGroup -> for (index in 0 until view.childCount) {
                visit(view.getChildAt(index), action)
            }
        }
    }

    private fun applyToIcon(imageView: ImageView) {
        val mode = currentMode()
        if (mode == MODE_DEFAULT) {
            restoreIfNeeded(imageView)
            return
        }
        // Do not inspect or draw the Drawable when the current status-bar mode says
        // the icon should remain untouched. This is important for animated Drawables:
        // an off-screen sampling draw can disturb their bounds/animation state.
        if (!shouldUseBlackFilter(mode)) {
            restoreIfNeeded(imageView)
            return
        }
        val drawable = imageView.drawable
        if (drawable == null) {
            restoreIfNeeded(imageView)
            synchronized(lastDrawables) { lastDrawables.remove(imageView) }
            return
        }

        val drawableChanged = synchronized(lastDrawables) {
            val previous = lastDrawables.put(imageView, drawable)
            previous !== drawable
        }
        if (drawableChanged) {
            // A reused ImageView can still carry the previous filter while SystemUI is
            // installing the new notification icon. Restore the old source filter first,
            // then capture the new source filter before applying our own one.
            restoreIfNeeded(imageView)
            synchronized(originalFilters) {
                originalFilters[imageView] = imageView.colorFilter
            }
        }

        val shouldInvert = isNearWhite(drawable)
        if (shouldInvert) {
            synchronized(originalFilters) {
                if (!originalFilters.containsKey(imageView) && imageView.colorFilter !== blackFilter) {
                    originalFilters[imageView] = imageView.colorFilter
                }
            }
            if (imageView.colorFilter !== blackFilter) imageView.colorFilter = blackFilter
        } else {
            restoreIfNeeded(imageView)
        }
    }

    private fun shouldUseBlackFilter(mode: String = currentMode()): Boolean = when (mode) {
        MODE_DEFAULT -> false
        MODE_FOLLOW_STATUS_BAR -> statusBarTintIsBlack()
        MODE_INVERT_STATUS_BAR -> !statusBarTintIsBlack()
        else -> true
    }

    private fun currentMode(): String = when (val mode =
        ConfigManager.getString(KEY_ICON_COLOR_MODE, MODE_DEFAULT)
    ) {
        MODE_DEFAULT, MODE_FOLLOW_STATUS_BAR, MODE_INVERT_STATUS_BAR ->
            mode
        else -> MODE_BLACK
    }

    private fun statusBarTintIsBlack(): Boolean =
        StatusBarTextColorHook.getReadableTint() == Color.BLACK

    private fun restoreIfNeeded(imageView: ImageView) {
        if (imageView.colorFilter !== blackFilter) return
        val original = synchronized(originalFilters) { originalFilters[imageView] }
        imageView.colorFilter = original
    }

    private fun isNearWhite(source: Drawable): Boolean {
        // BitmapDrawable is the common notification-icon path. Inspect its pixels directly;
        // never change bounds or draw it into an off-screen Canvas.
        if (source is BitmapDrawable) {
            return isNearWhite(source.bitmap)
        }
        // Animated/vector drawables are deliberately left alone unless SystemUI exposes a
        // stable ConstantState clone. Sampling the live instance can disturb its animation
        // bounds and cause the icon to render at the top-left after a tint update.
        if (source is Animatable && source.constantState == null) return false
        val sourceState = source.constantState
        if (sourceState != null) {
            synchronized(whiteResults) {
                whiteResults[sourceState]?.let { return it }
            }
        }
        val drawable = source.constantState?.newDrawable()?.mutate() ?: source
        val bitmap = runCatching {
            Bitmap.createBitmap(ICON_SAMPLE_SIZE, ICON_SAMPLE_SIZE, Bitmap.Config.ARGB_8888)
        }.getOrNull() ?: return false

        val oldBounds = drawable.bounds
        val result = try {
            drawable.setBounds(0, 0, ICON_SAMPLE_SIZE, ICON_SAMPLE_SIZE)
            drawable.draw(Canvas(bitmap))
            var visible = 0
            var white = 0
            for (y in 0 until ICON_SAMPLE_SIZE) {
                for (x in 0 until ICON_SAMPLE_SIZE) {
                    val pixel = bitmap.getPixel(x, y)
                    if (Color.alpha(pixel) < 24) continue
                    visible++
                    if (Color.red(pixel) >= WHITE_THRESHOLD &&
                        Color.green(pixel) >= WHITE_THRESHOLD &&
                        Color.blue(pixel) >= WHITE_THRESHOLD
                    ) {
                        white++
                    }
                }
            }
            visible >= MIN_VISIBLE_PIXELS && white.toFloat() / visible >= MIN_WHITE_RATIO
        } catch (_: Throwable) {
            false
        } finally {
            drawable.bounds = oldBounds
            bitmap.recycle()
        }
        if (sourceState != null) {
            synchronized(whiteResults) {
                whiteResults[sourceState] = result
            }
        }
        return result
    }

    private fun isNearWhite(bitmap: Bitmap): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return false
        val stepX = (width / ICON_SAMPLE_SIZE).coerceAtLeast(1)
        val stepY = (height / ICON_SAMPLE_SIZE).coerceAtLeast(1)
        var visible = 0
        var white = 0
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.alpha(pixel) >= 24) {
                    visible++
                    if (Color.red(pixel) >= WHITE_THRESHOLD &&
                        Color.green(pixel) >= WHITE_THRESHOLD &&
                        Color.blue(pixel) >= WHITE_THRESHOLD
                    ) {
                        white++
                    }
                }
                x += stepX
            }
            y += stepY
        }
        return visible >= MIN_VISIBLE_PIXELS && white.toFloat() / visible >= MIN_WHITE_RATIO
    }

    private fun isSideModule(module: Any?): Boolean {
        val name = module?.toString()?.lowercase() ?: return false
        if (name in SIDE_MODULES) return true
        // Image/text templates use numbered module names for the same small-island
        // picture slot, for example moduleImageText_1.
        if (name.startsWith("moduleimagetext_")) return true
        return (name.contains("left") || name.contains("right")) &&
            !name.contains("small") &&
            !name.contains("center") &&
            !name.contains("expand") &&
            !name.contains("focus")
    }

    private fun logDiagnostic(module: XposedModule, moduleValue: Any?, container: View?) {
        if (!io.github.hyperisland.xposed.ConfigManager.isDebugLogEnabled()) return
        val index = diagnosticCount.incrementAndGet()
        if (index > 24) return
        val children = buildString {
            describeViews(container, this, 0)
        }
        log(module) {
            "bind#$index module=${moduleValue?.javaClass?.name}:${moduleValue} " +
                "container=${container?.javaClass?.name} children=$children"
        }
    }

    private fun describeViews(view: View?, output: StringBuilder, depth: Int) {
        if (view == null || depth > 3) return
        repeat(depth) { output.append("  ") }
        if (view is ImageView) {
            val drawable = view.drawable
            output.append("ImageView(drawable=${drawable?.javaClass?.name},")
            output.append("bounds=${drawable?.bounds},alpha=${drawable?.alpha}")
            if (drawable != null) {
                output.append(",sample=${sampleDescription(drawable)}")
            }
            output.append(")")
        } else {
            output.append(view.javaClass.name)
        }
        output.append(';')
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                describeViews(view.getChildAt(index), output, depth + 1)
            }
        }
    }

    private fun sampleDescription(source: Drawable): String {
        val state = source.constantState
        val sampled = state?.let { synchronized(whiteResults) { whiteResults[it] } }
        return sampled?.let { if (it) "nearWhite" else "colored" } ?: "uncached"
    }
}
