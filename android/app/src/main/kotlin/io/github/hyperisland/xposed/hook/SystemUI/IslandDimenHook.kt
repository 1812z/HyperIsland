package io.github.hyperisland.xposed.hook

import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Field

/**
 * 修改超级岛尺寸（高度、垂直位置）
 *
 * 原理：
 *   DynamicIslandBaseContentView 在构造函数和 reset() 中读取 dimen 资源，
 *   然后缓存到字段（islandViewHeight、islandViewMarginTop 等），
 *   后续直接使用字段值，不再调用 Resources.getDimensionPixelSize()。
 *
 * 做法：
 *   1. 用 HookUtils.hookDynamicClassLoaders 拿到 SystemUI 的 ClassLoader
 *   2. 加载 DynamicIslandBaseContentView，Hook 其 reset() 和构造函数
 *   3. 在原始方法执行完后，用自定义 dp 值覆盖字段
 */
object IslandDimenHook : BaseHook() {

    private const val TAG = "HyperIsland[islandDimen]"

    /** 配置 Key */
    private const val KEY_HEIGHT = "pref_island_height"
    private const val KEY_MINI_Y = "pref_island_mini_y"
    private const val KEY_RADIUS = "pref_island_radius"
    private const val KEY_TITLE_SIZE = "pref_island_title_size"

    private const val CLAZZ = "miui.systemui.dynamicisland.window.content.DynamicIslandBaseContentView"

    /** 自定义值（dp，0 = 不生效） */
    @Volatile private var customHeightDp = 0.0
    @Volatile private var customMiniYDp = 0.0
    @Volatile private var customRadiusDp = 0.0
    @Volatile private var customTitleSizeDp = 0.0

    /** 当前 module 实例（用于 log 调用）*/
    @Volatile private var currentModule: XposedModule? = null

    /** 反射字段缓存 */
    private var cachedClazz: Class<*>? = null
    private var fIslandViewHeight: Field? = null
    private var fIslandViewMarginTop: Field? = null
    private var fSmallIslandViewWidth: Field? = null
    private var fBigIslandView: Field? = null

    private var hookedReset = false
    private var hookedCtor = false

    override fun getTag() = TAG

    override fun onConfigChanged() {
        loadConfig()
    }

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        if (param.packageName != "com.android.systemui") return
        loadConfig()
        // 诊断：打印 ConfigManager 读取到的值
        log(module, "onInit loadConfig: heightDp=$customHeightDp, miniYDp=$customMiniYDp, radiusDp=$customRadiusDp, titleSizeDp=$customTitleSizeDp")
        log(module, "onInit contains: height=${ConfigManager.contains(KEY_HEIGHT)}, miniY=${ConfigManager.contains(KEY_MINI_Y)}, radius=${ConfigManager.contains(KEY_RADIUS)}, titleSize=${ConfigManager.contains(KEY_TITLE_SIZE)}")
        log(module, "onInit raw string: height='${ConfigManager.getString(KEY_HEIGHT)}', miniY='${ConfigManager.getString(KEY_MINI_Y)}', radius='${ConfigManager.getString(KEY_RADIUS)}', titleSize='${ConfigManager.getString(KEY_TITLE_SIZE)}'")
        hookDynamicClassLoaders(module)
    }

    private fun hookDynamicClassLoaders(module: XposedModule) {
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { cl ->
            try {
                val clazz = cl.loadClass(CLAZZ)
                if (!hookedReset || !hookedCtor) {
                    doHook(module, clazz)
                }
            } catch (_: ClassNotFoundException) {
            } catch (e: Exception) {
                logError(module, "hookDynamicClassLoaders failed: ${e.message}")
            }
        }
    }

    private fun doHook(module: XposedModule, clazz: Class<*>) {
        // 保存 module 引用，供 cacheFields 等内部方法使用
        this.currentModule = module
        // 缓存字段（含 fBigIslandView）
        cacheFields(clazz)
        this.cachedClazz = clazz

        // Hook reset()
        if (!hookedReset) {
            try {
                val resetMethod = clazz.getDeclaredMethod("reset")
                module.hook(resetMethod).intercept { chain ->
                    chain.proceed()
                    val obj = chain.thisObject ?: return@intercept null
                    apply(obj, module)
                    null
                }
                hookedReset = true
                log(module, "hooked reset()")
            } catch (e: Exception) {
                logError(module, "hook reset() failed: ${e.message}")
            }
        }

        // Hook 构造函数
        if (!hookedCtor) {
            try {
                for (ctor in clazz.constructors) {
                    module.hook(ctor).intercept { chain ->
                        chain.proceed()
                        val obj = chain.thisObject ?: return@intercept null
                        // 构造函数执行完后，等 View 初始化完成再 apply
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            apply(obj, module)
                        }
                        null
                    }
                }
                hookedCtor = true
                log(module, "hooked constructors")
            } catch (e: Exception) {
                logError(module, "hook constructors failed: ${e.message}")
            }
        }
    }

    private fun cacheFields(clazz: Class<*>) {
        val m = currentModule ?: return
        try {
            fIslandViewHeight = clazz.getDeclaredField("islandViewHeight").apply { isAccessible = true }
            fIslandViewMarginTop = clazz.getDeclaredField("islandViewMarginTop").apply { isAccessible = true }
            fSmallIslandViewWidth = clazz.getDeclaredField("smallIslandViewWidth").apply { isAccessible = true }
            fBigIslandView = clazz.getDeclaredField("bigIslandView").apply { isAccessible = true }
        } catch (e: Exception) {
            logError(m, "cacheFields partial fail: ${e.message}")
        }
    }

    /**
     * 将自定义 dp 值写入 obj 的字段，并同步更新 View 的 LayoutParams
     */
    private fun apply(obj: Any, module: XposedModule) {
        if (customHeightDp <= 0.0 && customMiniYDp <= 0.0 && customTitleSizeDp <= 0.0) return

        try {
            val view = obj as View
            val dm = view.resources.displayMetrics

            // 自定义高度 → 覆盖 islandViewHeight 字段 + View LayoutParams
            if (customHeightDp > 0.0) {
                val px = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, customHeightDp.toFloat(), dm
                ).toInt()

                fIslandViewHeight?.setInt(obj, px)
                updateViewHeight(obj, px)
                log(module, "apply: islandViewHeight = ${customHeightDp}dp → ${px}px")
            }

            // 自定义垂直位置 → 覆盖 islandViewMarginTop 字段
            if (customMiniYDp > 0.0) {
                val px = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, customMiniYDp.toFloat(), dm
                ).toInt()

                fIslandViewMarginTop?.setInt(obj, px)
                log(module, "apply: islandViewMarginTop = ${customMiniYDp}dp → ${px}px")
            }

            // 自定义字体大小 → 等 bind() 完成后遍历 View 树改 textSize
            if (customTitleSizeDp > 0.0) {
                applyTitleSize(view, module)
            }
        } catch (e: Exception) {
            logError(module, "apply failed: ${e.message}")
        }
    }

    /**
     * 通过 ViewTreeObserver 在布局完成后遍历所有 TextView 子视图，修改字体大小。
     * 因为 bind() 在 reset() 之后执行，ViewStub 在 bind() 里才 inflate，
     * 所以必须等 onGlobalLayout 才能拿到所有子 View。
     */
    private fun applyTitleSize(rootView: View, module: XposedModule) {
        try {
            val dm = rootView.resources.displayMetrics
            val textSizePx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, customTitleSizeDp.toFloat(), dm
            )

            rootView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    try {
                        rootView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    } catch (_: Exception) {}

                    var count = 0
                    traverseTextViews(rootView) { tv ->
                        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
                        count++
                    }
                    if (count > 0) {
                        log(module, "applyTitleSize: set $count TextViews to ${customTitleSizeDp}sp (${textSizePx.toInt()}px)")
                    }
                }
            })
        } catch (e: Exception) {
            logError(module, "applyTitleSize failed: ${e.message}")
        }
    }

    /**
     * 递归遍历 View 树，对 id 资源名包含 "island_title" 或 "island_content" 的 TextView 执行 action。
     */
    private fun traverseTextViews(view: View, action: (TextView) -> Unit) {
        if (view is TextView && view.id != View.NO_ID) {
            try {
                val entryName = view.resources.getResourceEntryName(view.id)
                if (entryName.contains("island_title") || entryName.contains("island_content") || entryName.contains("island_front_title")) {
                    action(view)
                    return
                }
            } catch (_: Exception) {}
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                traverseTextViews(view.getChildAt(i), action)
            }
        }
    }

    /**
     * 更新 bigIslandView 的 LayoutParams.height
     * （使用缓存的 fBigIslandView 字段）
     */
    private fun updateViewHeight(obj: Any, heightPx: Int) {
        try {
            val view = fBigIslandView?.get(obj) as? View ?: return
            val lp = view.layoutParams ?: return
            lp.height = heightPx
            view.layoutParams = lp
        } catch (_: Exception) {
        }
    }

    private fun loadConfig() {
        customHeightDp = ConfigManager.getDouble(KEY_HEIGHT, 0.0)
        customMiniYDp = ConfigManager.getDouble(KEY_MINI_Y, 0.0)
        customRadiusDp = ConfigManager.getDouble(KEY_RADIUS, 0.0)
        customTitleSizeDp = ConfigManager.getDouble(KEY_TITLE_SIZE, 0.0)
    }
}
