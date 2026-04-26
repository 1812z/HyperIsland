package io.github.hyperisland.xposed.hook

import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Hook 超级岛背景视图，替换默认背景 Drawable 为自定义 PNG。
 *
 * 架构分析（来自 JADX 反编译）：
 *   - DynamicIslandBackgroundView：岛外形背景视图
 *     - setDrawable(Drawable)：设置背景 drawable
 *     - onDraw(Canvas)：使用 actualLeft/Top/Width/Height 设置 bounds 并绘制 drawable
 *   - DynamicIslandBaseContentView：岛内容视图
 *     - updateDarkLightMode(DynamicIslandState, String, boolean, boolean)：明暗模式切换时调用
 *       内部会调用 backgroundView.setDrawable(drawable) 设置背景
 *     - updateBackgroundBg(View, boolean)：设置内容子 View 的模糊/纯色背景（不是岛外形！）
 *   - DynamicIslandState：状态类
 *     - SmallIsland / BigIsland / Expanded / AppExpanded / MiniWindowExpanded 等子类
 *
 * Hook 策略：
 *   1. Hook updateDarkLightMode → 通过 DynamicIslandState 子类名判断岛类型，存入 ThreadLocal
 *   2. Hook setDrawable → 读取 ThreadLocal 中的类型，替换 drawable 为自定义背景
 *   3. 系统的 onDraw() 自动绘制新 drawable，位置由 actualLeft/Top/Width/Height 控制，不会错位
 */
object IslandBackgroundHook : BaseHook() {

    private const val TAG = "HyperIsland[IslandBg]"

    /** 配置 Key */
    private const val KEY_SMALL_BG = "pref_island_bg_small_path"
    private const val KEY_BIG_BG = "pref_island_bg_big_path"
    private const val KEY_EXPAND_BG = "pref_island_bg_expand_path"

    /** island 类型枚举 */
    private enum class IslandType { SMALL, BIG, EXPAND }

    /** 按类型缓存 drawable */
    private val cachedDrawables = ConcurrentHashMap<IslandType, Drawable>()

    /** 按类型记录上次文件修改时间 */
    private val lastFileModified = ConcurrentHashMap<IslandType, Long>()

    /** 按类型记录上次配置的路径字符串 */
    private val lastConfigPath = ConcurrentHashMap<IslandType, String>()

    /** 已 Hook 的 ClassLoader 集合（用于去重） */
    private val hookedClassLoaders = ConcurrentHashMap.newKeySet<Int>()

    /** 在 updateDarkLightMode → setDrawable 调用链中传递岛类型 */
    private val islandTypeHolder = ThreadLocal<IslandType>()

    /** 是否有任何自定义背景文件存在（全局标志，供 updateBackgroundBg 判断） */
    @Volatile
    private var hasAnyCustomBg = false

    /** hasAnyCustomBgFile 的短期缓存（避免高频方法中反复做文件 I/O） */
    @Volatile
    private var hasAnyCustomBgCached = false
    private var hasAnyCustomBgCheckTime = 0L
    private const val CUSTOM_BG_CHECK_TTL = 5000L // 5 秒缓存

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        hookDynamicClassLoaders(module)
    }

    /**
     * Hook 所有 ClassLoader 构造方法，在加载时尝试识别并 Hook DynamicIsland 相关类。
     */
    private fun hookDynamicClassLoaders(module: XposedModule) {
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { cl ->
            onClassLoaderLoaded(module, cl)
        }
    }

    /**
     * 当新的 ClassLoader 加载时，尝试识别并 Hook DynamicIsland 相关类。
     */
    private fun onClassLoaderLoaded(module: XposedModule, classLoader: ClassLoader) {
        val clId = System.identityHashCode(classLoader)
        if (!hookedClassLoaders.add(clId)) return

        try {
            // 尝试加载关键类
            val bgViewClass = try {
                classLoader.loadClass("miui.systemui.dynamicisland.DynamicIslandBackgroundView")
            } catch (_: ClassNotFoundException) {
                // 不是包含 DynamicIsland 类的 ClassLoader
                hookedClassLoaders.remove(clId)
                return
            }

            

            // Hook setDrawable - 替换岛外形背景
            hookSetDrawable(module, bgViewClass)

            // Hook alphaAnimation - 提高背景透明度（系统默认 0.22 太低，自定义图片不可见）
            hookAlphaAnimation(module, bgViewClass)

            // Hook updateDarkLightMode - 获取岛类型
            try {
                val contentViewClass = classLoader.loadClass(
                    "miui.systemui.dynamicisland.window.content.DynamicIslandBaseContentView"
                )
                val stateClass = classLoader.loadClass(
                    "miui.systemui.dynamicisland.event.DynamicIslandState"
                )
                hookUpdateDarkLightMode(module, contentViewClass, stateClass)

                // Hook updateBackgroundBg - 清除内容视图的暗色/模糊背景，防止遮挡自定义背景
                hookUpdateBackgroundBg(module, contentViewClass)

                // Hook containerScheduleUpdate - 清除动画过程中 container 的暗色背景
                try {
                    val animDelegateClass = classLoader.loadClass(
                        "miui.systemui.dynamicisland.anim.DynamicIslandAnimationDelegate"
                    )
                    hookContainerScheduleUpdate(module, animDelegateClass)
                } catch (e: Throwable) {
                    logError(module, "Failed to hook containerScheduleUpdate: ${e.message}")
                }
            } catch (e: Throwable) {
                logError(module, "Failed to hook updateDarkLightMode/updateBackgroundBg: ${e.message}")
            }

        } catch (e: Throwable) {
            logError(module, "Hook setup failed for CL: ${e.message}")
            hookedClassLoaders.remove(clId) // 允许后续重试
        }
    }

    /**
     * Hook DynamicIslandBackgroundView.setDrawable(Drawable)。
     *
     * 这是岛外形背景的核心方法。系统调用链：
     *   updateDarkLightMode() → backgroundView.setDrawable(drawable) → onDraw() 中绘制
     *
     * 替换 drawable 后，系统的 onDraw() 会自动使用新 drawable 绘制，
     * 位置由 actualLeft/Top/Width/Height 控制，不会出现错位问题。
     */
    private fun hookSetDrawable(module: XposedModule, bgViewClass: Class<*>) {
        try {
            val setDrawableMethod = bgViewClass.getDeclaredMethod("setDrawable", Drawable::class.java)

            module.hook(setDrawableMethod).intercept { chain ->
                // 先执行原方法（只是 this.drawable = drawable，无副作用）
                chain.proceed()

                // 读取 ThreadLocal 中的岛类型
                val type = islandTypeHolder.get()
                val bgView = chain.thisObject as? View

                if (type != null) {
                    val context = try {
                        bgView?.context
                    } catch (_: Exception) { null }

                    // 获取 stokeWidth，直接传给 loadCustomDrawable
                    var stokeWidth = 0
                    if (bgView != null) {
                        try {
                            val stokeField = bgViewClass.getDeclaredField("stokeWidth")
                            stokeField.isAccessible = true
                            stokeWidth = stokeField.getInt(bgView)
                        } catch (_: Exception) {}
                    }

                    val customDrawable = loadCustomDrawable(type, context, module, stokeWidth)

                    if (customDrawable != null) {
                        try {
                            // 通过反射替换 drawable 字段
                            val drawableField = bgViewClass.getDeclaredField("drawable")
                            drawableField.isAccessible = true
                            drawableField.set(chain.thisObject, customDrawable)

                            
                        } catch (e: Exception) {
                            logError(module, "Reflection set drawable failed: ${e.message}")
                        }

                        // 清除 contentView 及其子 View 的暗色/模糊背景
                        // 这些背景会遮挡 backgroundView.onDraw() 绘制的自定义背景
                        try {
                            val bgViewGroup = bgView as? ViewGroup
                            if (bgViewGroup != null && bgViewGroup.childCount > 0) {
                                clearChildBackgrounds(bgViewGroup)
                            }
                        } catch (e: Exception) {
                            logError(module, "Failed to clear child backgrounds: ${e.message}")
                        }
                    }
                }

                null
            }
            
        } catch (e: Throwable) {
            logError(module, "Failed to hook setDrawable: ${e.message}")
        }
    }

    /**
     * Hook DynamicIslandBaseContentView.updateDarkLightMode(DynamicIslandState, String, boolean, boolean)。
     *
     * 通过 DynamicIslandState 的子类名判断当前岛的类型：
     *   - SmallIsland → 小岛
     *   - BigIsland / ShowOnceBigIsland → 大岛
     *   - Expanded / AppExpanded / MiniWindowExpanded / SubAppExpanded / SubMiniWindowExpanded → 展开态
     *
     * 类型存入 ThreadLocal，供 setDrawable hook 读取。
     */
    private fun hookUpdateDarkLightMode(
        module: XposedModule,
        contentViewClass: Class<*>,
        stateClass: Class<*>
    ) {
        try {
            val method = contentViewClass.getDeclaredMethod(
                "updateDarkLightMode",
                stateClass,
                String::class.java,
                Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType
            )

            module.hook(method).intercept { chain ->
                // 通过 DynamicIslandState 子类名判断岛类型
                val state = chain.args[0]
                val stateName = state?.javaClass?.simpleName ?: ""
                val stateFullName = state?.javaClass?.name ?: ""

                val type = when (stateName) {
                    "SmallIsland" -> IslandType.SMALL
                    "BigIsland", "ShowOnceBigIsland" -> IslandType.BIG
                    "Expanded", "AppExpanded", "MiniWindowExpanded",
                    "SubAppExpanded", "SubMiniWindowExpanded" -> IslandType.EXPAND
                    else -> null // Unknown state, don't override
                }

                if (type != null) {
                    islandTypeHolder.set(type)
                }

                // 执行原方法（内部会调用 backgroundView.setDrawable）
                chain.proceed()

                // 清理 ThreadLocal
                islandTypeHolder.remove()

                null
            }
            
        } catch (e: Throwable) {
            logError(module, "Failed to hook updateDarkLightMode: ${e.message}")
        }
    }

    /**
     * Hook DynamicIslandBackgroundView.alphaAnimation(float)。
     *
     * 系统默认 alpha 目标值很低（如 0.22），导致自定义背景图片几乎不可见。
     * 当有自定义背景时，直接设置 backgroundAlpha=1.0 并调用 scheduleUpdate()，
     * 跳过原 Folme 动画（因为 chain.args 是不可修改 List，不能改参数值）。
     */
    private fun hookAlphaAnimation(module: XposedModule, bgViewClass: Class<*>) {
        try {
            val alphaMethod = bgViewClass.getDeclaredMethod("alphaAnimation", Float::class.javaPrimitiveType)

            module.hook(alphaMethod).intercept { chain ->
                val type = islandTypeHolder.get()

                if (type != null) {
                    // 有自定义背景，直接设置 backgroundAlpha=1.0，跳过 Folme 动画
                    val bgView = chain.thisObject
                    try {
                        val alphaField = bgViewClass.getDeclaredField("backgroundAlpha")
                        alphaField.isAccessible = true
                        alphaField.setFloat(bgView, 1.0f)

                        // 调用 scheduleUpdate() 让 drawable.setAlpha + invalidate 生效
                        val scheduleMethod = bgViewClass.getDeclaredMethod("scheduleUpdate")
                        scheduleMethod.isAccessible = true
                        scheduleMethod.invoke(bgView)
                    } catch (e: Exception) {
                        logError(module, "alphaAnimation override failed: ${e.message}, falling back to original")
                        chain.proceed()
                    }
                    return@intercept null  // 跳过原方法
                }

                // 无自定义背景，执行原方法
                chain.proceed()
                null
            }
            
        } catch (e: Throwable) {
            logError(module, "Failed to hook alphaAnimation: ${e.message}")
        }
    }

    /**
     * 清除 contentView 及其直接子 View 的背景和模糊效果。
     *
     * 遮挡层来源：updateBackgroundBg() 设置在 bigIslandView/smallIslandView/expandedView 上，
     * 以及 containerScheduleUpdate() 设置在 R.id.container 上。
     * 只清除背景非 null 的 View，不暴力递归整棵 View 树，避免误杀。
     */
    private fun clearChildBackgrounds(viewGroup: ViewGroup) {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child.background != null) {
                child.background = null
            }
            clearBlurEffect(child)
            // 递归一层子 View（container 在 contentView 的子 View 下）
            if (child is ViewGroup) {
                for (j in 0 until child.childCount) {
                    val grandChild = child.getChildAt(j)
                    if (grandChild.background != null) {
                        grandChild.background = null
                    }
                    clearBlurEffect(grandChild)
                }
            }
        }
    }

    /**
     * 清除 View 的模糊效果。
     */
    private fun clearBlurEffect(view: View) {
        try {
            val cl = view.javaClass.classLoader
            if (cl != null) {
                val miBlurCompatClass = cl.loadClass("miui.util.MiBlurCompat")
                val clearBlendMethod = miBlurCompatClass.getDeclaredMethod(
                    "clearMiBackgroundBlendColorCompat", View::class.java
                )
                clearBlendMethod.invoke(null, view)

                val setBlurModeMethod = miBlurCompatClass.getDeclaredMethod(
                    "setMiViewBlurModeCompat", View::class.java, Int::class.javaPrimitiveType
                )
                setBlurModeMethod.invoke(null, view, 0)
            }
        } catch (_: Exception) {
            // MiBlurCompat 不可用，忽略
        }
    }

    /**
     * Hook DynamicIslandAnimationDelegate.containerScheduleUpdate()。
     *
     * 系统动画代理在每帧更新时会给 container（contentView 的子 View）设置暗色背景：
     *   - container.setBackgroundDrawable(dynamic_island_background)
     *   - container.setBackgroundColor(containerBackgroundColor)
     * 这些暗色层会遮挡 backgroundView.onDraw() 绘制的自定义背景。
     *
     * 当有自定义背景时，在 containerScheduleUpdate 执行后清除 container 的背景。
     */
    private fun hookContainerScheduleUpdate(module: XposedModule, animDelegateClass: Class<*>) {
        try {
            val method = animDelegateClass.getDeclaredMethod("containerScheduleUpdate")

            module.hook(method).intercept { chain ->
                chain.proceed()

                // 检查当前类型是否有自定义背景配置
                val currentType = islandTypeHolder.get()
                val hasBgForType = currentType != null && hasBgFileForType(currentType)

                // 只有当前类型有配置时才清除背景
                if (hasBgForType) {
                    try {
                        // 从 animDelegate 获取 view 字段（DynamicIslandBaseContentView）
                        val viewField = animDelegateClass.getDeclaredField("view")
                        viewField.isAccessible = true
                        val contentView = viewField.get(chain.thisObject) as? View ?: return@intercept null

                        // 获取 container 子 View（R.id.container）
                        val containerResId = contentView.resources.getIdentifier("container", "id", "com.android.systemui")
                        if (containerResId > 0) {
                            val container = contentView.findViewById<View>(containerResId)
                            if (container != null && container.background != null) {
                                container.background = null
                                clearBlurEffect(container)
                            }
                        }
                    } catch (e: Exception) {
                        logError(module, "containerScheduleUpdate clear bg failed: ${e.message}")
                    }
                }

                null
            }
            
        } catch (e: Throwable) {
            logError(module, "Failed to hook containerScheduleUpdate: ${e.message}")
        }
    }

    /**
     * Hook DynamicIslandBaseContentView.updateBackgroundBg(View, boolean)。
     *
     * 系统会给内容视图设置暗色/模糊背景（dynamic_island_background 或 blur blend colors），
     * 这个暗色层遮挡了自定义背景图片。当有自定义背景文件时，清除内容视图的背景和模糊效果。
     *
     * 注意：不依赖 hasAnyCustomBg 全局标志，因为 updateBackgroundBg 可能在 setDrawable 之前调用，
     * 此时 hasAnyCustomBg 还未设为 true。改为直接检查自定义背景文件是否存在。
     */
    private fun hookUpdateBackgroundBg(module: XposedModule, contentViewClass: Class<*>) {
        try {
            val method = contentViewClass.getDeclaredMethod(
                "updateBackgroundBg",
                View::class.java,
                Boolean::class.javaPrimitiveType
            )

            module.hook(method).intercept { chain ->
                // 检查当前类型是否有自定义背景配置
                val currentType = islandTypeHolder.get()
                val hasBgForType = currentType != null && hasBgFileForType(currentType)

                if (hasBgForType) {
                    // 当前类型有自定义背景，清除内容视图的暗色/模糊背景
                    val view = chain.args[0] as? View

                    if (view != null) {
                        view.background = null

                        // 尝试清除模糊效果（如果存在 MiBlurCompat）
                        try {
                            val cl = contentViewClass.classLoader
                            if (cl != null) {
                                val miBlurCompatClass = cl.loadClass("miui.util.MiBlurCompat")
                                val clearBlendMethod = miBlurCompatClass.getDeclaredMethod(
                                    "clearMiBackgroundBlendColorCompat", View::class.java
                                )
                                clearBlendMethod.invoke(null, view)

                                val setBlurModeMethod = miBlurCompatClass.getDeclaredMethod(
                                    "setMiViewBlurModeCompat", View::class.java, Int::class.javaPrimitiveType
                                )
                                setBlurModeMethod.invoke(null, view, 0)
                            }
                        } catch (_: Exception) {
                            // MiBlurCompat 不可用，忽略
                        }
                    }
                    return@intercept null  // 跳过原方法
                }

                // 无自定义背景，执行原方法
                chain.proceed()
                null
            }
            
        } catch (e: Throwable) {
            logError(module, "Failed to hook updateBackgroundBg: ${e.message}")
        }
    }

    /**
     * 检查是否存在任何自定义背景文件。
     * 带短期 TTL 缓存（5 秒），避免在 updateBackgroundBg 等高频方法中反复做文件 I/O。
     */
    private fun hasAnyCustomBgFile(): Boolean {
        val now = System.currentTimeMillis()
        if (now - hasAnyCustomBgCheckTime < CUSTOM_BG_CHECK_TTL) return hasAnyCustomBgCached
        hasAnyCustomBgCached = checkCustomBgFilesExist()
        hasAnyCustomBgCheckTime = now
        return hasAnyCustomBgCached
    }

    /**
     * 检查指定类型是否有配置路径且文件存在。
     */
    private fun hasBgFileForType(type: IslandType): Boolean {
        val configPath = when (type) {
            IslandType.SMALL -> ConfigManager.getString(KEY_SMALL_BG)
            IslandType.BIG -> ConfigManager.getString(KEY_BIG_BG)
            IslandType.EXPAND -> ConfigManager.getString(KEY_EXPAND_BG)
        }
        if (configPath.isNullOrBlank()) return false
        val file = File(configPath)
        return file.exists() && file.canRead()
    }

    /**
     * 实际检查自定义背景文件是否存在。
     * 只有配置路径非空且文件存在时才返回 true，不再回退到默认路径。
     */
    private fun checkCustomBgFilesExist(): Boolean {
        for (type in IslandType.entries) {
            val configPath = when (type) {
                IslandType.SMALL -> ConfigManager.getString(KEY_SMALL_BG)
                IslandType.BIG -> ConfigManager.getString(KEY_BIG_BG)
                IslandType.EXPAND -> ConfigManager.getString(KEY_EXPAND_BG)
            }
            // 配置路径为空时，不使用任何背景
            if (configPath.isNullOrBlank()) continue
            val file = File(configPath)
            if (file.exists() && file.canRead()) return true
        }
        return false
    }

    /**
     * 加载指定类型的自定义背景 BitmapDrawable。
     * 只有配置路径非空时才加载，不再回退到默认路径。
     */
    private fun loadCustomDrawable(
        type: IslandType,
        context: android.content.Context?,
        module: XposedModule,
        stokeWidth: Int = 0
    ): Drawable? {
        val configPath = when (type) {
            IslandType.SMALL -> ConfigManager.getString(KEY_SMALL_BG)
            IslandType.BIG -> ConfigManager.getString(KEY_BIG_BG)
            IslandType.EXPAND -> ConfigManager.getString(KEY_EXPAND_BG)
        }

        // 配置路径为空时，不加载任何背景
        if (configPath.isNullOrBlank()) return null

        val file = File(configPath)
        if (!file.exists() || !file.canRead()) return null

        val currentModified = file.lastModified()
        val cachedModified = lastFileModified[type] ?: 0L
        val cachedPath = lastConfigPath[type] ?: ""

        if (cachedDrawables[type] == null || currentModified != cachedModified || cachedPath != configPath) {
            synchronized(this) {
                if (cachedDrawables[type] == null || currentModified != (lastFileModified[type] ?: 0L) || lastConfigPath[type] != configPath) {
                    val old = cachedDrawables[type]
                    val drawable = decodeFile(file, context, module, stokeWidth)
                    if (drawable != null) {
                        cachedDrawables[type] = drawable
                        lastFileModified[type] = currentModified
                        lastConfigPath[type] = configPath
                        hasAnyCustomBg = true  // 通知 updateBackgroundBg hook 清除暗色覆盖
                    }
                    // 回收旧 drawable
                    if (old is RoundedClippingDrawable) {
                        if (!old.bitmap.isRecycled) {
                            old.bitmap.recycle()
                        }
                    } else if (old is BitmapDrawable) {
                        old.bitmap?.recycle()
                    }
                }
            }
        }
        return cachedDrawables[type]
    }

    /**
     * 解码背景文件为圆角裁剪 Drawable。
     *
     * 不再使用 createRoundedBitmap 预裁切（bounds 拉伸后比例不对），
     * 改为 RoundedClippingDrawable 在 draw 时用 canvas.clipPath 实时裁剪。
     */
    private fun decodeFile(
        file: File,
        context: android.content.Context?,
        module: XposedModule,
        stokeWidth: Int = 0
    ): Drawable? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            val srcW = options.outWidth
            val srcH = options.outHeight
            if (srcW <= 0 || srcH <= 0) return null

            // 采样压缩：动态计算目标尺寸，避免展开态模糊
            val displayMetrics = android.content.res.Resources.getSystem().displayMetrics
            val maxTargetSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 200f, displayMetrics
            ).toInt().coerceAtLeast(512)

            val sampleSize = calculateInSampleSize(srcW, srcH, maxTargetSize, maxTargetSize)
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return null

            // 获取圆角半径
            val cornerRadius = getCornerRadius(context)

            // 用圆角裁剪包装器包裹 bitmap，直接 canvas.drawBitmap 绘制确保完全填充
            RoundedClippingDrawable(bitmap, cornerRadius, stokeWidth)
        } catch (e: Exception) {
            logError(module, "Failed to decode background: ${e.message}")
            null
        }
    }

    /**
     * 获取岛圆角半径（px），直接读取系统资源 island_radius。
     */
    private fun getCornerRadius(context: android.content.Context?): Float {
        if (context != null) {
            try {
                val res = context.resources
                val dimenId = res.getIdentifier("island_radius", "dimen", "com.android.systemui")
                if (dimenId > 0) {
                    val radius = res.getDimension(dimenId)
                    if (radius > 0f) return radius
                }
            } catch (_: Exception) {}
        }

        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            30f,
            android.content.res.Resources.getSystem().displayMetrics
        )
    }

    private fun calculateInSampleSize(srcW: Int, srcH: Int, dstW: Int, dstH: Int): Int {
        var inSampleSize = 1
        if (srcW > dstW || srcH > dstH) {
            val halfW = srcW / 2
            val halfH = srcH / 2
            while (halfW / inSampleSize >= dstW && halfH / inSampleSize >= dstH) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    override fun onConfigChanged() {
        synchronized(this) {
            cachedDrawables.values.forEach { drawable ->
                if (drawable is RoundedClippingDrawable) {
                    if (!drawable.bitmap.isRecycled) {
                        drawable.bitmap.recycle()
                    }
                } else if (drawable is BitmapDrawable) {
                    drawable.bitmap?.recycle()
                }
            }
            cachedDrawables.clear()
            lastFileModified.clear()
            lastConfigPath.clear()
            hasAnyCustomBg = false  // 重置，下次 loadCustomDrawable 时重新判断
            hasAnyCustomBgCached = false
            hasAnyCustomBgCheckTime = 0L
        }
    }

    /**
     * 圆角裁剪 Drawable 包装器。
     *
     * 系统的 DynamicIslandBackgroundView.onDraw() 会 setBounds + draw，
     * 原始 GradientDrawable 自身就是圆角形状，但 BitmapDrawable 是矩形。
     * 此包装器在 draw 时用 canvas.clipPath 裁剪成圆角，确保不溢出岛外形。
     *
     * 使用 canvas.drawBitmap 直接绘制 bitmap，确保图片填满到内容区域
     *（不含 stokeWidth 边框扩展），不覆盖边框外的光效。
     *
     * @param bitmap 背景图片
     * @param cornerRadius 圆角半径（px）
     * @param stokeWidth 背景视图的边框宽度（px），用于计算内容区域
     */
    private class RoundedClippingDrawable(
        val bitmap: Bitmap,
        private val cornerRadius: Float,
        private val stokeWidth: Int = 0
    ) : Drawable() {

        private val clipPath = Path()
        private val rect = RectF()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true  // 启用双线性过滤，抗锯齿
        }

        // 预计算默认 stokeWidth 回退值（4dp）
        private val defaultStoke by lazy {
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 4f,
                android.content.res.Resources.getSystem().displayMetrics
            ).toInt()
        }

        override fun draw(canvas: Canvas) {
            val bounds = getBounds()
            if (bounds.isEmpty || bitmap.isRecycled) return

            // bounds 是系统 onDraw 设置的：(actualLeft - stoke, actualTop - stoke, actualLeft + actualWidth + stoke, actualTop + actualHeight + stoke)
            // 需要去除 stokeWidth 计算内容区域
            val s = if (stokeWidth > 0) stokeWidth.toFloat() else defaultStoke.toFloat()
            val contentLeft = (bounds.left + s).coerceAtLeast(bounds.left.toFloat())
            val contentTop = (bounds.top + s).coerceAtLeast(bounds.top.toFloat())
            val contentRight = (bounds.right - s).coerceAtMost(bounds.right.toFloat())
            val contentBottom = (bounds.bottom - s).coerceAtMost(bounds.bottom.toFloat())

            // 确保内容区域有效
            if (contentRight > contentLeft && contentBottom > contentTop) {
                rect.set(contentLeft, contentTop, contentRight, contentBottom)
            } else {
                rect.set(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat())
            }

            // 构建 clip path（圆角矩形）
            clipPath.reset()
            clipPath.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)

            // 保存 canvas 状态，clip 后绘制 bitmap，再恢复
            val save = canvas.save()
            canvas.clipPath(clipPath)

            // 绘制 bitmap 到内容区域（而非整个 bounds）
            canvas.drawBitmap(bitmap, null, rect, paint)
            canvas.restoreToCount(save)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        override fun getIntrinsicWidth(): Int = bitmap.width

        override fun getIntrinsicHeight(): Int = bitmap.height
    }
}
