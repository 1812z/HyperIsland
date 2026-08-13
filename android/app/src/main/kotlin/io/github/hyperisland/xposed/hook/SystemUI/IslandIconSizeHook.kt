package io.github.hyperisland.xposed.hook.SystemUI

import android.content.res.Resources
import android.view.ViewGroup
import android.widget.ImageView
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.hyperisland.xposed.utils.ResourceDimenHook
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.Collections
import java.util.WeakHashMap

/** Scales icons while real and fake island module holders bind their content. */
object IslandIconSizeHook : BaseHook() {
    private const val TAG = "HyperIsland[IslandIconSize]"
    private const val KEY_ICON_SIZE = "pref_island_icon_size"
    private const val KEY_ROUND_ICON_RADIUS = "pref_round_icon_radius"
    private const val KEY_ICON_PADDING = "pref_island_icon_padding"
    private const val ICON_RADIUS_RESOURCE = "island_icon_radius"
    private const val ICON_SIZE_RESOURCE = "island_fix_icon_size"
    private const val ICON_HOLDER_CLASS =
        "miui.systemui.dynamicisland.module.IslandIconViewHolder"
    private const val SMALL_ISLAND_ICON_MODULE = "modulePicSmallIsland"

    private data class OriginalSize(val width: Int, val height: Int)

    private val originalSizes = Collections.synchronizedMap(
        WeakHashMap<ImageView, OriginalSize>(),
    )
    private val iconHolders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Any, Boolean>()),
    )
    private val originalHolderWidths = Collections.synchronizedMap(
        WeakHashMap<Any, Int>(),
    )
    private val originalContainerTranslations = Collections.synchronizedMap(
        WeakHashMap<ViewGroup, Float>(),
    )
    private val hookedClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>()),
    )
    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        ResourceDimenHook.register(module, ICON_RADIUS_RESOURCE, ::iconRadiusPx)
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { classLoader ->
            hookIconHolder(module, classLoader)
        }
    }

    private fun iconRadiusPx(resources: Resources): Float {
        val radiusPercent = ConfigManager.getInt(KEY_ROUND_ICON_RADIUS, 30).coerceIn(0, 100)
        val iconSizePx = findDimension(resources, ICON_SIZE_RESOURCE)
            ?: (30f * resources.displayMetrics.density)
        return iconSizePx * iconSizePercent() * radiusPercent / 20_000f
    }

    private fun findDimension(
        resources: Resources,
        name: String,
    ): Float? = runCatching {
        val packageNames = sequenceOf(
            "miui.systemui.dynamicisland",
            "com.android.systemui",
            "miui.systemui.plugin",
        )
        val resourceId = packageNames
            .map { packageName -> resources.getIdentifier(name, "dimen", packageName) }
            .firstOrNull { it != 0 }
            ?: return@runCatching null
        resources.getDimension(resourceId)
    }.getOrNull()

    override fun onConfigChanged() {
        val snapshot = synchronized(iconHolders) { iconHolders.toList() }
        snapshot.forEach { holder ->
            iconContainer(holder)?.post { applyToHolder(holder) }
        }
    }

    private fun hookIconHolder(module: XposedModule, classLoader: ClassLoader) {
        try {
            val clazz = classLoader.loadClass(ICON_HOLDER_CLASS)
            if (!hookedClasses.add(clazz)) return

            val bindMethods = clazz.declaredMethods.filter { method ->
                method.name == "bind" && method.parameterCount == 2
            }
            for (method in bindMethods) {
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    chain.thisObject?.let { holder ->
                        iconHolders += holder
                        originalHolderWidths.remove(holder)
                        applyToHolder(holder)
                    }
                    result
                }
            }

            val getWidth = clazz.declaredMethods.firstOrNull { method ->
                method.name == "getWidth" && method.parameterCount == 0
            }
            if (getWidth != null) {
                module.hook(getWidth).intercept { chain ->
                    val holder = chain.thisObject ?: return@intercept chain.proceed()
                    val originalWidth = synchronized(originalHolderWidths) {
                        originalHolderWidths[holder]
                    } ?: run {
                        restoreHolder(holder)
                        val width = chain.proceed() as? Int ?: 0
                        originalHolderWidths[holder] = width
                        applyToHolder(holder)
                        width
                    }
                    (originalWidth * iconSizePercent() / 100f).toInt().coerceAtLeast(0)
                }
            }

            log(
                module,
                "hooked icon holder bind=${bindMethods.size}, width=${getWidth != null}",
            )
        } catch (_: ClassNotFoundException) {
        } catch (error: Throwable) {
            hookedClasses.remove(runCatching { classLoader.loadClass(ICON_HOLDER_CLASS) }.getOrNull())
            logError(module, "failed to hook $ICON_HOLDER_CLASS: ${error.message}")
        }
    }

    private fun applyToHolder(holder: Any) {
        val iconContainer = iconContainer(holder) ?: return
        applyToChildren(iconContainer, iconSizePercent())
        if (isSmallIslandIconHolder(holder)) {
            restoreIconOffset(iconContainer)
        } else {
            applyIconOffset(iconContainer)
        }
        iconContainer.requestLayout()
    }

    private fun restoreHolder(holder: Any) {
        val iconContainer = iconContainer(holder) ?: return
        restoreChildren(iconContainer)
        restoreIconOffset(iconContainer)
        iconContainer.requestLayout()
    }

    private fun applyIconOffset(iconContainer: ViewGroup) {
        val originalTranslation = synchronized(originalContainerTranslations) {
            originalContainerTranslations[iconContainer] ?: iconContainer.translationX.also {
                originalContainerTranslations[iconContainer] = it
            }
        }
        val offsetDp = ConfigManager.getDouble(KEY_ICON_PADDING, 8.0).coerceIn(0.0, 10.0) - 8.0
        iconContainer.translationX = originalTranslation +
            (offsetDp * iconContainer.resources.displayMetrics.density).toFloat()
    }

    private fun restoreIconOffset(iconContainer: ViewGroup) {
        val originalTranslation = synchronized(originalContainerTranslations) {
            originalContainerTranslations[iconContainer]
        } ?: return
        iconContainer.translationX = originalTranslation
    }

    private fun isSmallIslandIconHolder(holder: Any): Boolean = runCatching {
        holder.javaClass.getMethod("getModule").invoke(holder) == SMALL_ISLAND_ICON_MODULE
    }.getOrDefault(false)

    private fun iconContainer(holder: Any): ViewGroup? = runCatching {
        holder.javaClass.getDeclaredField("iconContainer").apply {
            isAccessible = true
        }.get(holder) as? ViewGroup
    }.getOrNull()

    private fun applyToChildren(parent: ViewGroup, percent: Int) {
        for (index in 0 until parent.childCount) {
            when (val child = parent.getChildAt(index)) {
                is ImageView -> scaleImageView(child, percent)
                is ViewGroup -> applyToChildren(child, percent)
            }
        }
    }

    private fun restoreChildren(parent: ViewGroup) {
        for (index in 0 until parent.childCount) {
            when (val child = parent.getChildAt(index)) {
                is ImageView -> restoreImageView(child)
                is ViewGroup -> restoreChildren(child)
            }
        }
    }

    private fun scaleImageView(imageView: ImageView, percent: Int) {
        val params = imageView.layoutParams ?: return
        val width = params.width
        val height = params.height
        if (width <= 0 || height <= 0 || kotlin.math.abs(width - height) > 2) return

        val original = synchronized(originalSizes) {
            originalSizes[imageView] ?: OriginalSize(width, height).also {
                originalSizes[imageView] = it
            }
        }
        val targetWidth = (original.width * percent / 100f).toInt().coerceAtLeast(1)
        val targetHeight = (original.height * percent / 100f).toInt().coerceAtLeast(1)
        if (params.width == targetWidth && params.height == targetHeight) return
        params.width = targetWidth
        params.height = targetHeight
        imageView.layoutParams = params
    }

    private fun restoreImageView(imageView: ImageView) {
        val original = synchronized(originalSizes) { originalSizes[imageView] } ?: return
        val params = imageView.layoutParams ?: return
        if (params.width == original.width && params.height == original.height) return
        params.width = original.width
        params.height = original.height
        imageView.layoutParams = params
    }

    private fun iconSizePercent(): Int =
        ConfigManager.getInt(KEY_ICON_SIZE, 100).coerceIn(50, 150)
}
