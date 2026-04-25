package io.github.hyperisland.xposed.hook

import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
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
    private const val KEY_CORNER_RADIUS = "pref_island_bg_corner_radius"

    /** 背景图片默认目录 */
    private const val DEFAULT_BG_DIR = "/sdcard/Download"

    /** 默认 PNG 文件名 */
    private val DEFAULT_FILE_NAMES = mapOf(
        IslandType.SMALL to "hyperisland_bg_small.png",
        IslandType.BIG to "hyperisland_bg_big.png",
        IslandType.EXPAND to "hyperisland_bg_expand.png"
    )

    /** island 类型枚举 */
    private enum class IslandType { SMALL, BIG, EXPAND }

    /** 自定义圆角半径（px） */
    @Volatile
    private var customCornerRadius: Float = 0f

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

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        customCornerRadius = ConfigManager.getFloat(KEY_CORNER_RADIUS, 0f)
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

            log(module, "Detected DynamicIsland in ClassLoader: ${classLoader.javaClass.name}")

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
            // 静默忽略，不要影响其他功能
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

                // [DEBUG] 详细日志
                val originalDrawable = chain.args[0] as? Drawable
                log(module, "[DEBUG] setDrawable called: type=$type, " +
                    "originalDrawable=${originalDrawable?.javaClass?.simpleName}, " +
                    "bgView=${bgView?.javaClass?.simpleName}, " +
                    "thread=${Thread.currentThread().name}")

                if (type != null) {
                    val context = try {
                        bgView?.context
                    } catch (_: Exception) { null }

                    val customDrawable = loadCustomDrawable(type, context, module)

                    if (customDrawable != null) {
                        try {
                            // 通过反射替换 drawable 字段
                            val drawableField = bgViewClass.getDeclaredField("drawable")
                            drawableField.isAccessible = true
                            drawableField.set(chain.thisObject, customDrawable)

                            log(module, "Replaced drawable for $type via reflection")
                        } catch (e: Exception) {
                            logError(module, "Reflection set drawable failed: ${e.message}")
                        }

                        // 清除 contentView 及其子 View 的暗色/模糊背景
                        // 这些背景会遮挡 backgroundView.onDraw() 绘制的自定义背景
                        try {
                            val bgViewGroup = bgView as? ViewGroup
                            if (bgViewGroup != null && bgViewGroup.childCount > 0) {
                                clearChildBackgrounds(bgViewGroup, module)
                            }
                        } catch (e: Exception) {
                            logError(module, "Failed to clear child backgrounds: ${e.message}")
                        }
                    }
                }

                null
            }
            log(module, "Hooked DynamicIslandBackgroundView.setDrawable")
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

                // [DEBUG] 记录所有参数
                val arg1 = chain.args.getOrNull(1) as? String
                val arg2 = chain.args.getOrNull(2)
                val arg3 = chain.args.getOrNull(3)
                log(module, "[DEBUG] updateDarkLightMode: stateClass=$stateFullName, " +
                    "stateSimple=$stateName → type=$type, " +
                    "arg1(str)=$arg1, arg2=$arg2, arg3=$arg3, " +
                    "thread=${Thread.currentThread().name}")

                if (type != null) {
                    islandTypeHolder.set(type)
                }

                // 执行原方法（内部会调用 backgroundView.setDrawable）
                chain.proceed()

                // [DEBUG] 确认 ThreadLocal 在 setDrawable 后是否已被清理
                val typeAfter = islandTypeHolder.get()
                log(module, "[DEBUG] updateDarkLightMode proceed done, ThreadLocal after=$typeAfter")

                // 清理 ThreadLocal
                islandTypeHolder.remove()

                null
            }
            log(module, "Hooked updateDarkLightMode for type detection")
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
                    val originalAlpha = chain.args[0] as Float
                    try {
                        val alphaField = bgViewClass.getDeclaredField("backgroundAlpha")
                        alphaField.isAccessible = true
                        alphaField.setFloat(bgView, 1.0f)

                        // 调用 scheduleUpdate() 让 drawable.setAlpha + invalidate 生效
                        val scheduleMethod = bgViewClass.getDeclaredMethod("scheduleUpdate")
                        scheduleMethod.isAccessible = true
                        scheduleMethod.invoke(bgView)

                        log(module, "[DEBUG] alphaAnimation: type=$type, originalAlpha=$originalAlpha → 1.0 (skipped animation)")
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
            log(module, "Hooked DynamicIslandBackgroundView.alphaAnimation")
        } catch (e: Throwable) {
            logError(module, "Failed to hook alphaAnimation: ${e.message}")
        }
    }

    /**
     * 清除 contentView 及其 container 子 View 的暗色/模糊背景。
     *
     * 系统的遮挡层来自多个来源：
     * 1. updateBackgroundBg() 给 bigIslandView/smallIslandView/expandedView 设置 dynamic_island_background
     * 2. containerScheduleUpdate() 给 container (R.id.container) 设置暗色背景
     * 3. MiBlurCompat 的 blend colors 模糊效果
     *
     * 这些遮挡层都会覆盖 backgroundView.onDraw() 绘制的自定义背景图片，需要全部清除。
     */
    private fun clearChildBackgrounds(viewGroup: ViewGroup, module: XposedModule) {
        // 遍历直接子 View（主要是 contentView）
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i) as? ViewGroup ?: continue
            clearViewAndContainerBg(child, module)
        }
    }

    /**
     * 清除指定 View 及其 container 子 View 的背景和模糊效果。
     */
    private fun clearViewAndContainerBg(view: View, module: XposedModule) {
        // 清除 View 自身的背景
        if (view.background != null) {
            log(module, "[DEBUG] clearViewBg: clearing bg of ${view.javaClass.simpleName}@${System.identityHashCode(view)}")
            view.background = null
        }
        // 清除模糊效果
        clearBlurEffect(view)

        // 如果是 ViewGroup，遍历子 View 寻找 container 和岛内容 View
        val viewGroup = view as? ViewGroup ?: return
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            val childName = child.javaClass.simpleName

            // 清除已知的遮挡层 View：container、bigIslandView、smallIslandView、expandedView
            if (childName == "FrameLayout" ||  // container 是 FrameLayout
                childName.contains("BigIsland") ||
                childName.contains("SmallIsland") ||
                childName.contains("Expanded") ||
                child.background != null
            ) {
                if (child.background != null) {
                    log(module, "[DEBUG] clearChildBg: clearing bg of $childName@${System.identityHashCode(child)}")
                    child.background = null
                }
                clearBlurEffect(child)
            }

            // 递归进入子 ViewGroup
            if (child is ViewGroup) {
                clearViewAndContainerBg(child, module)
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

                // 当有自定义背景文件时，清除 container 的暗色背景
                if (hasAnyCustomBgFile()) {
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
            log(module, "Hooked containerScheduleUpdate")
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
                // 直接检查自定义背景文件是否存在（不依赖全局标志，避免时序问题）
                if (hasAnyCustomBgFile()) {
                    // 有自定义背景，清除内容视图的暗色/模糊背景
                    val view = chain.args[0] as? View
                    val isPromoted = chain.args[1] as? Boolean ?: false
                    log(module, "[DEBUG] updateBackgroundBg: hasCustomBgFile=true, view=${view?.javaClass?.simpleName}, isPromoted=$isPromoted")

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

                        log(module, "[DEBUG] updateBackgroundBg: cleared view background and blur")
                    }
                    return@intercept null  // 跳过原方法
                }

                // 无自定义背景，执行原方法
                chain.proceed()
                null
            }
            log(module, "Hooked updateBackgroundBg")
        } catch (e: Throwable) {
            logError(module, "Failed to hook updateBackgroundBg: ${e.message}")
        }
    }

    /**
     * 检查是否存在任何自定义背景文件。
     * 直接检查文件系统，不依赖缓存标志，避免时序问题。
     */
    private fun hasAnyCustomBgFile(): Boolean {
        for (type in IslandType.entries) {
            val configPath = when (type) {
                IslandType.SMALL -> ConfigManager.getString(KEY_SMALL_BG)
                IslandType.BIG -> ConfigManager.getString(KEY_BIG_BG)
                IslandType.EXPAND -> ConfigManager.getString(KEY_EXPAND_BG)
            }
            val fileName = DEFAULT_FILE_NAMES[type] ?: continue
            val file = if (!configPath.isNullOrBlank()) {
                File(configPath)
            } else {
                File(DEFAULT_BG_DIR, fileName)
            }
            if (file.exists() && file.canRead()) return true
        }
        return false
    }

    /**
     * 加载指定类型的自定义背景 BitmapDrawable。
     */
    private fun loadCustomDrawable(
        type: IslandType,
        context: android.content.Context?,
        module: XposedModule
    ): Drawable? {
        val configPath = when (type) {
            IslandType.SMALL -> ConfigManager.getString(KEY_SMALL_BG)
            IslandType.BIG -> ConfigManager.getString(KEY_BIG_BG)
            IslandType.EXPAND -> ConfigManager.getString(KEY_EXPAND_BG)
        }

        val fileName = DEFAULT_FILE_NAMES[type] ?: return null
        val file = if (!configPath.isNullOrBlank()) {
            File(configPath)
        } else {
            File(DEFAULT_BG_DIR, fileName)
        }

        if (!file.exists() || !file.canRead()) return null

        val currentModified = file.lastModified()
        val cachedModified = lastFileModified[type] ?: 0L
        val cachedPath = lastConfigPath[type] ?: ""

        if (cachedDrawables[type] == null || currentModified != cachedModified || cachedPath != configPath) {
            synchronized(this) {
                if (cachedDrawables[type] == null || currentModified != (lastFileModified[type] ?: 0L) || lastConfigPath[type] != configPath) {
                    val old = cachedDrawables[type]
                    val drawable = decodeFile(file, context, module)
                    if (drawable != null) {
                        cachedDrawables[type] = drawable
                        lastFileModified[type] = currentModified
                        lastConfigPath[type] = configPath
                        hasAnyCustomBg = true  // 通知 updateBackgroundBg hook 清除暗色覆盖
                        log(module, "Loaded $type background from ${file.absolutePath} (${file.length()} bytes)")
                    }
                    // 回收旧 drawable 的 bitmap
                    if (old is RoundedClippingDrawable) {
                        old.innerDrawable?.bitmap?.recycle()
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
        module: XposedModule
    ): Drawable? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            val srcW = options.outWidth
            val srcH = options.outHeight
            if (srcW <= 0 || srcH <= 0) return null

            // 采样压缩
            val maxTargetSize = 512
            val sampleSize = calculateInSampleSize(srcW, srcH, maxTargetSize, maxTargetSize)
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return null

            // 获取圆角半径
            val cornerRadius = getCornerRadius(context)

            val bitmapDrawable = if (context != null) {
                BitmapDrawable(context.resources, bitmap)
            } else {
                BitmapDrawable(null, bitmap)
            }

            // 用圆角裁剪包装器包裹，draw 时 canvas.clipPath 实时裁剪
            RoundedClippingDrawable(bitmapDrawable, cornerRadius)
        } catch (e: Exception) {
            logError(module, "Failed to decode background: ${e.message}")
            null
        }
    }

    /**
     * 获取圆角半径（px）。
     */
    private fun getCornerRadius(context: android.content.Context?): Float {
        if (customCornerRadius > 0f) return customCornerRadius

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
            customCornerRadius = ConfigManager.getFloat(KEY_CORNER_RADIUS, 0f)

            cachedDrawables.values.forEach { drawable ->
                if (drawable is RoundedClippingDrawable) {
                    drawable.innerDrawable?.bitmap?.recycle()
                } else if (drawable is BitmapDrawable) {
                    drawable.bitmap?.recycle()
                }
            }
            cachedDrawables.clear()
            lastFileModified.clear()
            lastConfigPath.clear()
            hasAnyCustomBg = false  // 重置，下次 loadCustomDrawable 时重新判断
        }
    }

    /**
     * 圆角裁剪 Drawable 包装器。
     *
     * 系统的 DynamicIslandBackgroundView.onDraw() 会 setBounds + draw，
     * 原始 GradientDrawable 自身就是圆角形状，但 BitmapDrawable 是矩形。
     * 此包装器在 draw 时用 canvas.clipPath 裁剪成圆角，确保不溢出岛外形。
     */
    private class RoundedClippingDrawable(
        val innerDrawable: BitmapDrawable,
        private val cornerRadius: Float
    ) : Drawable() {

        private val clipPath = Path()
        private val rect = RectF()

        override fun draw(canvas: Canvas) {
            val bounds = getBounds()
            if (bounds.isEmpty) return

            // 构建 clip path
            rect.set(bounds)
            clipPath.reset()
            clipPath.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)

            // 保存 canvas 状态，clip 后绘制，再恢复
            val save = canvas.save()
            canvas.clipPath(clipPath)
            innerDrawable.bounds = bounds
            innerDrawable.draw(canvas)
            canvas.restoreToCount(save)
        }

        override fun setAlpha(alpha: Int) {
            innerDrawable.alpha = alpha
        }

        override fun getOpacity(): Int = innerDrawable.opacity

        override fun setColorFilter(colorFilter: ColorFilter?) {
            innerDrawable.colorFilter = colorFilter
        }

        override fun getIntrinsicWidth(): Int = innerDrawable.intrinsicWidth

        override fun getIntrinsicHeight(): Int = innerDrawable.intrinsicHeight
    }
}
