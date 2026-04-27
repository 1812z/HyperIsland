package io.github.hyperisland.xposed.hook

import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import android.os.Handler
import android.os.Looper
import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * Hook 超级岛背景视图，替换默认背景 Drawable 为自定义 PNG。
 *
 * 核心原则：**只对配置了自定义背景的岛类型替换 drawable，绝不影响其他类型的岛外形。**
 * 但遮罩处理采用"全有或全无"策略——只要有一个类型配了自定义背景（anyCustomBgConfigured），
 * 所有类型的遮罩都由本 Hook 管理：
 *   - 有自定义背景的类型 → 清除遮罩（透明，让自定义背景透出）
 *   - 无自定义背景的类型 → loadCustomDrawable 返回纯黑图片（替代系统遮罩）
 *   - 无任何自定义背景时 → 完全跟随系统，不做任何修改
 *
 * 原因：container 是共享视图，清除 container 遮罩后，非自定义类型无法依赖系统遮罩，
 * 必须通过 loadCustomDrawable 返回纯黑图片，走相同渲染管线。
 *
 * 架构分析（来自 JADX 反编译）：
 *   View 层级（来自 DynamicIslandViewBinding）：
 *     DynamicIslandBackgroundView (rootView, 绘制岛外形)
 *       └── DynamicIslandContentView (id=island_content)
 *            ├── smallIslandView (FrameLayout, id=small_island_view)
 *            ├── bigIslandView (DynamicIslandBigIslandView, id=big_island_view)
 *            ├── expandedView (DynamicIslandExpandedView, from ViewStub)
 *            └── container (FrameLayout, id=container)
 *
 *   遮罩来源（来自 JADX updateBackgroundBg）：
 *     - blur 开启时：setMiViewBlurModeCompat(view, 1) + setMiBackgroundBlendColors → 暗色叠加
 *     - blur 关闭时：view.setBackgroundDrawable(dynamic_island_background) → 黑色遮罩
 *     - tablet 路径：view.setBackground(null) + clearMiBlurBlendEffect
 *
 * Hook 策略（anyCustomBgConfigured=true 时）：
 *   1. hookUpdateDarkLightMode → 识别岛类型，存入 ThreadLocal + lastIslandType
 *   2. hookSetDrawable → 替换 drawable（自定义背景 or 纯黑图片）
 *   3. hookAlphaAnimation → 设 alpha=1.0
 *   4. hookUpdateBackgroundBg → 拦截所有类型的遮罩设置（清除遮罩）
 *   5. hookContainerScheduleUpdate → 清除 container 遮罩
 *
 * ★ 关键：当任意类型有自定义背景时，所有遮罩都由本 Hook 控制。
 *   非自定义类型用真正的纯黑图片替代系统遮罩，避免 container 清空后变透明。
 *   当没有任何自定义背景时，完全跟随系统，不做任何修改。
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

    /** 上一次确定的岛类型（在 islandTypeHolder 被清除后供其他 hook 使用） */
    @Volatile
    private var lastIslandType: IslandType? = null

    /** 缓存的纯黑 Bitmap（512x512），供无自定义背景的岛类型使用 */
    @Volatile
    private var cachedBlackBitmap: Bitmap? = null

    /** 缓存 anyCustomBgConfigured 结果，避免热路径每帧做 3 次 I/O */
    @Volatile
    private var cachedAnyCustomBg: Boolean? = null

    /** 缓存圆角半径，运行时不会变 */
    @Volatile
    private var cachedCornerRadius: Float? = null

    /** 缓存 MiBlurCompat 反射对象，避免每次调用 clearMaskForView 都做类加载+方法查找 */
    @Volatile
    private var miBlurCompatClass: Class<*>? = null
    @Volatile
    private var setBlurModeMethod: Method? = null
    @Volatile
    private var clearBlendMethod: Method? = null

    /** 缓存 bgViewClass 的 Field/Method，避免热路径反复反射查找 */
    private val stokeWidthFieldCache = ConcurrentHashMap<Class<*>, Field?>()
    private val drawableFieldCache = ConcurrentHashMap<Class<*>, Field?>()
    private val backgroundAlphaFieldCache = ConcurrentHashMap<Class<*>, Field?>()
    private val scheduleUpdateMethodCache = ConcurrentHashMap<Class<*>, Method?>()

    /** 延迟重试 Handler（用于 ConfigManager 时序问题的延迟重试） */
    private val bgRetryHandler = Handler(Looper.getMainLooper())

    /** 当前挂起的延迟重试 Runnable，避免堆积 */
    @Volatile
    private var pendingRetryRunnable: Runnable? = null

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
            val bgViewClass = try {
                classLoader.loadClass("miui.systemui.dynamicisland.DynamicIslandBackgroundView")
            } catch (_: ClassNotFoundException) {
                hookedClassLoaders.remove(clId)
                return
            }

            hookSetDrawable(module, bgViewClass)
            hookAlphaAnimation(module, bgViewClass)

            try {
                val contentViewClass = classLoader.loadClass(
                    "miui.systemui.dynamicisland.window.content.DynamicIslandBaseContentView"
                )
                val stateClass = classLoader.loadClass(
                    "miui.systemui.dynamicisland.event.DynamicIslandState"
                )
                hookUpdateDarkLightMode(module, contentViewClass, stateClass)
                hookUpdateBackgroundBg(module, contentViewClass)

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
            hookedClassLoaders.remove(clId)
        }
    }

    /**
     * 获取当前岛类型（优先 ThreadLocal，回退到 lastIslandType）。
     */
    private fun getCurrentIslandType(): IslandType? {
        return islandTypeHolder.get() ?: lastIslandType
    }

    /**
     * Hook DynamicIslandBackgroundView.setDrawable(Drawable)。
     *
     * ★ 仅当当前岛类型有自定义背景时替换 drawable，不影响其他类型。
     * 替换后，精准清除当前类型主视图的遮罩（不遍历所有子 View，避免跨类型干扰）。
     * 额外的遮罩清除由 hookUpdateBackgroundBg 和 hookContainerScheduleUpdate 处理。
     */
    private fun hookSetDrawable(module: XposedModule, bgViewClass: Class<*>) {
        try {
            val setDrawableMethod = bgViewClass.getDeclaredMethod("setDrawable", Drawable::class.java)

            module.hook(setDrawableMethod).intercept { chain ->
                chain.proceed()

                val type = getCurrentIslandType()
                val bgView = chain.thisObject as? View

                if (type != null && anyCustomBgConfigured()) {
                    val context = try { bgView?.context } catch (_: Exception) { null }

                    val stokeWidth = if (bgView != null) getStokeWidth(bgView, bgViewClass) else 0

                    val customDrawable = loadCustomDrawable(type, context, module, stokeWidth)

                    if (customDrawable != null) {
                        try {
                            val drawableField = getCachedField(drawableFieldCache, bgViewClass, "drawable")
                            drawableField?.set(chain.thisObject, customDrawable)
                        } catch (e: Exception) {
                            logError(module, "Reflection set drawable failed: ${e.message}")
                        }

                        // ★ 精准清除当前类型主视图的遮罩
                        if (bgView is ViewGroup) {
                            clearMaskForCurrentType(bgView, type)
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
     * 精准清除当前岛类型主视图的暗色遮罩。
     *
     * ★ 与旧版 clearChildBackgrounds 不同：只清除匹配当前类型的那一个主视图，
     *   绝不触碰其他类型的视图。container 由 hookContainerScheduleUpdate 单独处理。
     *
     * View 层级：
     *   DynamicIslandBackgroundView (bgView)
     *     └── DynamicIslandContentView (contentView)
     *          ├── smallIslandView  → 只在 type=SMALL 时清除
     *          ├── bigIslandView    → 只在 type=BIG 时清除
     *          ├── expandedView     → 只在 type=EXPAND 时清除
     *          └── container        → 不在此处处理，由 hookContainerScheduleUpdate 处理
     */
    private fun clearMaskForCurrentType(bgView: ViewGroup, currentType: IslandType) {
        // 遍历到 DynamicIslandContentView
        for (i in 0 until bgView.childCount) {
            val contentView = bgView.getChildAt(i)
            if (contentView !is ViewGroup) continue

            // 遍历 contentView 的子 View，找匹配当前类型的那个
            for (j in 0 until contentView.childCount) {
                val child = contentView.getChildAt(j)
                val childType = getIslandTypeForView(child)

                // ★ 只清除类型完全匹配的主视图，跳过 container 和不匹配的类型
                if (childType == currentType) {
                    clearMaskForView(child)
                }
            }
        }
    }

    /**
     * Hook DynamicIslandBaseContentView.updateDarkLightMode。
     *
     * 通过 DynamicIslandState 子类名判断岛类型，存入 ThreadLocal + lastIslandType。
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
                val state = chain.args[0]
                val stateName = state?.javaClass?.simpleName ?: ""
                val stateFullName = state?.javaClass?.name ?: ""

                val type = resolveIslandType(stateName, stateFullName)

                if (type != null) {
                    islandTypeHolder.set(type)
                    lastIslandType = type
                }

                chain.proceed()

                islandTypeHolder.remove()

                null
            }

        } catch (e: Throwable) {
            logError(module, "Failed to hook updateDarkLightMode: ${e.message}")
        }
    }

    private fun resolveIslandType(simpleName: String, fullName: String): IslandType? {
        return when (simpleName) {
            "SmallIsland" -> IslandType.SMALL
            "BigIsland", "ShowOnceBigIsland" -> IslandType.BIG
            "Expanded", "AppExpanded", "MiniWindowExpanded",
            "SubAppExpanded", "SubMiniWindowExpanded" -> IslandType.EXPAND
            else -> when {
                fullName.contains("SmallIsland") -> IslandType.SMALL
                fullName.contains("BigIsland") -> IslandType.BIG
                fullName.contains("Expanded") -> IslandType.EXPAND
                else -> null
            }
        }
    }

    /**
     * Hook DynamicIslandBackgroundView.alphaAnimation(float)。
     *
     * ★ 仅当当前岛类型有自定义背景时设 alpha=1.0 并跳过 Folme 动画。
     */
    private fun hookAlphaAnimation(module: XposedModule, bgViewClass: Class<*>) {
        try {
            val alphaMethod = bgViewClass.getDeclaredMethod("alphaAnimation", Float::class.javaPrimitiveType)

            module.hook(alphaMethod).intercept { chain ->
                val type = getCurrentIslandType()

                if (type != null && anyCustomBgConfigured()) {
                    val bgView = chain.thisObject
                    try {
                        val alphaField = getCachedField(backgroundAlphaFieldCache, bgViewClass, "backgroundAlpha")
                        alphaField?.setFloat(bgView, 1.0f)

                        val scheduleMethod = getCachedMethod(scheduleUpdateMethodCache, bgViewClass, "scheduleUpdate")
                        scheduleMethod?.invoke(bgView)
                    } catch (e: Exception) {
                        logError(module, "alphaAnimation override failed: ${e.message}, falling back")
                        chain.proceed()
                    }
                    return@intercept null
                }

                chain.proceed()
                null
            }

        } catch (e: Throwable) {
            logError(module, "Failed to hook alphaAnimation: ${e.message}")
        }
    }

    /**
     * 通过 View 的类名/资源名判断它属于哪个岛类型。
     *
     * 精确映射（来自 JADX DynamicIslandViewBinding）：
     *   - DynamicIslandBigIslandView → BIG
     *   - DynamicIslandExpandedView → EXPAND
     *   - small_island_view (FrameLayout) → SMALL
     *   - container → 跟随当前岛类型（lastIslandType）
     *   - island_content → 无法确定，不处理
     *   - 其他 → 无法确定，不处理
     */
    private fun getIslandTypeForView(view: View): IslandType? {
        val className = view.javaClass.name
        return when {
            className.contains("BigIslandView") -> IslandType.BIG
            className.contains("ExpandedView") -> IslandType.EXPAND
            else -> {
                val resName = try {
                    view.resources?.getResourceEntryName(view.id) ?: ""
                } catch (_: Exception) { "" }
                when {
                    resName.contains("small_island") -> IslandType.SMALL
                    resName.contains("big_island") -> IslandType.BIG
                    resName.contains("expanded") -> IslandType.EXPAND
                    resName.contains("container") -> lastIslandType
                    else -> null
                }
            }
        }
    }

    /**
     * 清除 View 的暗色遮罩：设置 background=null + blur mode=0 + 清除 blend colors。
     *
     * ★ 必须同时设 blur mode=0，否则残留的 blur mode 会导致系统继续施加模糊效果，
     *   在某些设备上表现为半透明暗色叠加覆盖自定义背景。
     */
    private fun clearMaskForView(view: View) {
        view.background = null
        disableBlurAndClearBlend(view)
    }

    /**
     * 禁用 blur 并清除 blend colors。
     * blur mode 设为 0 + 清除 blend colors，确保无暗色叠加残留。
     * ★ 反射对象缓存，避免每次调用都做类加载+方法查找。
     */
    private fun disableBlurAndClearBlend(view: View) {
        try {
            val cl = view.javaClass.classLoader ?: return

            // 确保 MiBlurCompat 反射对象已缓存
            if (miBlurCompatClass == null || miBlurCompatClass?.classLoader != cl) {
                val blurClass = cl.loadClass("miui.util.MiBlurCompat")
                miBlurCompatClass = blurClass
                setBlurModeMethod = blurClass.getDeclaredMethod(
                    "setMiViewBlurModeCompat", View::class.java, Int::class.javaPrimitiveType
                )
                clearBlendMethod = blurClass.getDeclaredMethod(
                    "clearMiBackgroundBlendColorCompat", View::class.java
                )
            }

            // 1. 设 blur mode = 0（禁用模糊）
            try {
                setBlurModeMethod?.invoke(null, view, 0)
            } catch (_: Exception) {}

            // 2. 清除 blend colors
            try {
                clearBlendMethod?.invoke(null, view)
            } catch (_: Exception) {}
        } catch (_: Exception) {}
    }

    /**
     * 统一辅助方法：将自定义 drawable 应用到 backgroundView 并安排重绘。
     *
     * 1. 通过反射设置 drawable 字段
     * 2. 设置 backgroundAlpha = 1.0
     * 3. 取消 Folme 动画（设 backgroundAlpha 直接覆盖）
     * 4. 调用 scheduleUpdate() 触发重绘
     * 5. 精准清除当前类型主视图的遮罩
     */
    private fun applyDrawableToBgView(
        bgView: View,
        bgViewClass: Class<*>,
        type: IslandType,
        module: XposedModule,
        customDrawable: Drawable
    ) {
        try {
            val drawableField = getCachedField(drawableFieldCache, bgViewClass, "drawable")
            drawableField?.set(bgView, customDrawable)
        } catch (e: Exception) {
            logError(module, "applyDrawable failed: ${e.message}")
        }

        try {
            val alphaField = getCachedField(backgroundAlphaFieldCache, bgViewClass, "backgroundAlpha")
            alphaField?.setFloat(bgView, 1.0f)
        } catch (_: Exception) {}

        try {
            val scheduleMethod = getCachedMethod(scheduleUpdateMethodCache, bgViewClass, "scheduleUpdate")
            scheduleMethod?.invoke(bgView)
        } catch (_: Exception) {}

        if (bgView is ViewGroup) {
            clearMaskForCurrentType(bgView, type)
        }
    }

    /**
     * 延迟重试应用自定义背景（解决 ConfigManager 时序问题）。
     *
     * 当 hasBgFileForType() 在首次调用时因 remote prefs 未加载完成而返回 false，
     * 延迟 2 秒后重试。如果此时 prefs 已加载且有配置，则应用自定义背景。
     */
    private fun scheduleBgRetry(
        bgView: View,
        bgViewClass: Class<*>,
        type: IslandType,
        module: XposedModule
    ) {
        // ★ 取消之前挂起的重试，避免堆积
        pendingRetryRunnable?.let { bgRetryHandler.removeCallbacks(it) }

        val runnable = Runnable {
            try {
                if (!anyCustomBgConfigured()) return@Runnable

                val context = try { bgView.context } catch (_: Exception) { null }
                val stokeWidth = getStokeWidth(bgView, bgViewClass)

                val customDrawable = loadCustomDrawable(type, context, module, stokeWidth)
                if (customDrawable != null) {
                    if (bgView.isAttachedToWindow) {
                        applyDrawableToBgView(bgView, bgViewClass, type, module, customDrawable)
                    }
                }
            } catch (e: Exception) {
                logError(module, "scheduleBgRetry failed: ${e.message}")
            } finally {
                pendingRetryRunnable = null
            }
        }
        pendingRetryRunnable = runnable
        bgRetryHandler.postDelayed(runnable, 2000L)
    }

    /**
     * Hook DynamicIslandBaseContentView.updateBackgroundBg(View, boolean)。
     *
     * ★ 根据 View 自身类型判断是否需要跳过原方法：
     *   - View 属于有自定义背景的类型 → 跳过原方法，清除遮罩（background + blur + blend）
     *   - View 属于无自定义背景的类型 → 执行原方法，完全跟随系统
     *
     * 这样只配 BIG 背景时，updateBackgroundBg(smallIslandView) 仍走原方法，
     * smallIslandView 的遮罩正常设置，不会变透明。
     */
    private fun hookUpdateBackgroundBg(module: XposedModule, contentViewClass: Class<*>) {
        try {
            val method = contentViewClass.getDeclaredMethod(
                "updateBackgroundBg",
                View::class.java,
                Boolean::class.javaPrimitiveType
            )

            module.hook(method).intercept { chain ->
                val view = chain.args[0] as? View
                val viewType = if (view != null) getIslandTypeForView(view) else null

                if (viewType != null && anyCustomBgConfigured()) {
                    // ★ 该 View 所属的岛类型有自定义背景 → 跳过原方法，清除遮罩
                    if (view != null) {
                        clearMaskForView(view)
                    }
                    return@intercept null
                }

                // 无自定义背景 → 执行原方法
                chain.proceed()
                null
            }

        } catch (e: Throwable) {
            logError(module, "Failed to hook updateBackgroundBg: ${e.message}")
        }
    }

    /**
     * Hook DynamicIslandAnimationDelegate.containerScheduleUpdate()。
     *
     * ★ 仅当当前岛类型有自定义背景时，清除 container 的遮罩。
     * 无自定义背景时完全跟随系统。
     */
    private fun hookContainerScheduleUpdate(module: XposedModule, animDelegateClass: Class<*>) {
        try {
            val method = animDelegateClass.getDeclaredMethod("containerScheduleUpdate")

            module.hook(method).intercept { chain ->
                chain.proceed()

                val type = lastIslandType

                if (type != null && anyCustomBgConfigured()) {
                    try {
                        val viewField = animDelegateClass.getDeclaredField("view")
                        viewField.isAccessible = true
                        val contentView = viewField.get(chain.thisObject) as? View ?: return@intercept null

                        val containerResId = contentView.resources.getIdentifier("container", "id", "com.android.systemui")
                        if (containerResId > 0) {
                            val container = contentView.findViewById<View>(containerResId)
                            if (container != null) {
                                clearMaskForView(container)
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
     * 生成 View 描述字符串（用于错误日志）。
     */
    private fun describeView(view: View?): String {
        if (view == null) return "null"
        val className = view.javaClass.simpleName
        val resName = try {
            view.resources?.getResourceEntryName(view.id) ?: "?"
        } catch (_: Exception) { "no-id" }
        return "$className($resName)"
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
     * 从缓存获取或创建 Field 对象，避免热路径反复反射查找。
     * 反射失败返回 null，由调用方安全处理。
     */
    private fun getCachedField(
        cache: ConcurrentHashMap<Class<*>, Field?>,
        clazz: Class<*>,
        fieldName: String
    ): Field? {
        cache[clazz]?.let { return it }
        return try {
            val field = clazz.getDeclaredField(fieldName).apply { isAccessible = true }
            cache[clazz] = field
            field
        } catch (_: Exception) { null }
    }

    /**
     * 从缓存获取或创建 Method 对象，避免热路径反复反射查找。
     * 反射失败返回 null，由调用方安全处理。
     */
    private fun getCachedMethod(
        cache: ConcurrentHashMap<Class<*>, Method?>,
        clazz: Class<*>,
        methodName: String,
        vararg parameterTypes: Class<*>
    ): Method? {
        cache[clazz]?.let { return it }
        return try {
            val method = clazz.getDeclaredMethod(methodName, *parameterTypes).apply { isAccessible = true }
            cache[clazz] = method
            method
        } catch (_: Exception) { null }
    }

    /**
     * 读取 bgView 的 stokeWidth 字段值，Field 对象缓存复用。
     */
    private fun getStokeWidth(bgView: View, bgViewClass: Class<*>): Int {
        return try {
            val field = stokeWidthFieldCache.getOrPut(bgViewClass) {
                try {
                    bgViewClass.getDeclaredField("stokeWidth").apply { isAccessible = true }
                } catch (_: Exception) { null }
            }
            field?.getInt(bgView) ?: 0
        } catch (_: Exception) { 0 }
    }

    /**
     * 加载指定类型的自定义背景 BitmapDrawable。
     * ★ 只加载该类型的背景，不回退到其他类型。
     * ★ 当该类型没有自定义背景但其他类型有时，返回纯黑图片（避免 container 清空后变透明）。
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

        if (configPath.isNullOrBlank()) {
            if (anyCustomBgConfigured()) {
                return loadBlackDrawable(context, module, stokeWidth)
            }
            return null
        }

        val file = File(configPath)
        if (!file.exists() || !file.canRead()) {
            if (anyCustomBgConfigured()) {
                return loadBlackDrawable(context, module, stokeWidth)
            }
            return null
        }

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
                    }
                    if (old is RoundedClippingDrawable) {
                        if (!old.bitmap.isRecycled) old.bitmap.recycle()
                    } else if (old is BitmapDrawable) {
                        old.bitmap?.recycle()
                    }
                }
            }
        }
        return cachedDrawables[type]
    }

    /**
     * 检查是否至少有一个岛类型配置了自定义背景。
     * ★ 结果缓存，避免热路径每帧做 3 次跨进程读 + 磁盘 I/O。
     *   配置变更时由 onConfigChanged() 清除缓存。
     */
    private fun anyCustomBgConfigured(): Boolean {
        cachedAnyCustomBg?.let { return it }
        val result = hasBgFileForType(IslandType.SMALL)
                || hasBgFileForType(IslandType.BIG)
                || hasBgFileForType(IslandType.EXPAND)
        cachedAnyCustomBg = result
        return result
    }

    /**
     * 加载纯黑背景 Drawable（512x512 Bitmap + RoundedClippingDrawable）。
     * ★ 用于没有自定义背景的岛类型，避免 container 清空后变透明。
     * ★ Bitmap 缓存复用，每次创建新的 Drawable 实例（Drawable 不可跨 View 共享）。
     */
    private fun loadBlackDrawable(
        context: android.content.Context?,
        module: XposedModule,
        stokeWidth: Int = 0
    ): Drawable? {
        val bitmap = getOrCreateBlackBitmap(module) ?: return null
        val cornerRadius = getCornerRadius(context)
        return RoundedClippingDrawable(bitmap, cornerRadius, stokeWidth)
    }

    /**
     * 获取或创建缓存的纯黑 Bitmap（512x512 ARGB_8888）。
     */
    private fun getOrCreateBlackBitmap(module: XposedModule): Bitmap? {
        cachedBlackBitmap?.let { if (!it.isRecycled) return it }
        synchronized(this) {
            cachedBlackBitmap?.let { if (!it.isRecycled) return it }
            return try {
                val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.BLACK)
                cachedBlackBitmap = bitmap
                bitmap
            } catch (e: Exception) {
                logError(module, "Failed to create black bitmap: ${e.message}")
                null
            }
        }
    }

    /**
     * 解码背景文件为圆角裁剪 Drawable。
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

            val displayMetrics = android.content.res.Resources.getSystem().displayMetrics
            val maxTargetSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 200f, displayMetrics
            ).toInt().coerceAtLeast(512)

            val sampleSize = calculateInSampleSize(srcW, srcH, maxTargetSize, maxTargetSize)
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return null

            val cornerRadius = getCornerRadius(context)
            RoundedClippingDrawable(bitmap, cornerRadius, stokeWidth)
        } catch (e: Exception) {
            logError(module, "Failed to decode background: ${e.message}")
            null
        }
    }

    /**
     * 获取圆角半径。
     * ★ 结果缓存，运行时圆角不会变，避免每次都做资源查找。
     */
    private fun getCornerRadius(context: android.content.Context?): Float {
        cachedCornerRadius?.let { return it }
        val radius = computeCornerRadius(context)
        cachedCornerRadius = radius
        return radius
    }

    private fun computeCornerRadius(context: android.content.Context?): Float {
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
            TypedValue.COMPLEX_UNIT_DIP, 30f,
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
                    if (!drawable.bitmap.isRecycled) drawable.bitmap.recycle()
                } else if (drawable is BitmapDrawable) {
                    drawable.bitmap?.recycle()
                }
            }
            cachedDrawables.clear()
            lastFileModified.clear()
            lastConfigPath.clear()
            cachedBlackBitmap = null
        }
        // ★ 清除性能缓存，下次调用时重新计算
        cachedAnyCustomBg = null
        cachedCornerRadius = null
        stokeWidthFieldCache.clear()
        drawableFieldCache.clear()
        backgroundAlphaFieldCache.clear()
        scheduleUpdateMethodCache.clear()
    }

    /**
     * 圆角裁剪 Drawable 包装器。
     */
    private class RoundedClippingDrawable(
        val bitmap: Bitmap,
        private val cornerRadius: Float,
        private val stokeWidth: Int = 0
    ) : Drawable() {

        private val clipPath = Path()
        private val rect = RectF()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
        }

        private val defaultStoke by lazy {
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 4f,
                android.content.res.Resources.getSystem().displayMetrics
            ).toInt()
        }

        override fun draw(canvas: Canvas) {
            val bounds = getBounds()
            if (bounds.isEmpty || bitmap.isRecycled) return

            val s = if (stokeWidth > 0) stokeWidth.toFloat() else defaultStoke.toFloat()
            val contentLeft = (bounds.left + s).coerceAtLeast(bounds.left.toFloat())
            val contentTop = (bounds.top + s).coerceAtLeast(bounds.top.toFloat())
            val contentRight = (bounds.right - s).coerceAtMost(bounds.right.toFloat())
            val contentBottom = (bounds.bottom - s).coerceAtMost(bounds.bottom.toFloat())

            if (contentRight > contentLeft && contentBottom > contentTop) {
                rect.set(contentLeft, contentTop, contentRight, contentBottom)
            } else {
                rect.set(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat())
            }

            clipPath.reset()
            clipPath.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)

            val save = canvas.save()
            canvas.clipPath(clipPath)
            canvas.drawBitmap(bitmap, null, rect, paint)
            canvas.restoreToCount(save)
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
        override fun getIntrinsicWidth(): Int = bitmap.width
        override fun getIntrinsicHeight(): Int = bitmap.height
    }
}
