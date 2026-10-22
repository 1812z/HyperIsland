package io.github.hyperisland.xposed.hook

import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

/**
 * 超级岛左侧 / 右侧文本位置。
 *
 * 大岛分 `area_left` / `area_right` 两个 FrameLayout，文本模块以 `layout_gravity`
 * 贴在外侧：左文默认 start、右文默认 end。这里只改模块根视图的 `layout_gravity`，
 * 保留原有的垂直 gravity，不改动布局层级，也不做视图树遍历。
 */
object IslandTextGravityHook : BaseHook() {

    private const val TAG = "HyperIsland[IslandGravity]"

    private const val KEY_LEFT_GRAVITY = "pref_island_left_text_gravity"
    private const val KEY_RIGHT_GRAVITY = "pref_island_right_text_gravity"

    private const val MODE_DEFAULT = "default"
    private const val MODE_CENTER = "center"
    private const val MODE_LEFT = "left"
    private const val MODE_RIGHT = "right"

    private const val LEFT_HOLDER_CLASS =
        "miui.systemui.dynamicisland.module.IslandImageTextViewHolder"

    private val RIGHT_HOLDER_CLASSES = arrayOf(
        "miui.systemui.dynamicisland.module.IslandRightTextViewHolder",
        "miui.systemui.dynamicisland.module.IslandImageTextView2Holder",
        "miui.systemui.dynamicisland.module.IslandImageTextView3Holder",
        "miui.systemui.dynamicisland.module.IslandSameWidthDigitViewHolder",
    )

    private val hookedClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    )
    private val trackedHolders = Collections.synchronizedMap(
        WeakHashMap<Any, Boolean>()
    )
    private val viewMethods = Collections.synchronizedMap(
        WeakHashMap<Class<*>, Method>()
    )

    @Volatile private var leftMode = MODE_DEFAULT
    @Volatile private var rightMode = MODE_DEFAULT

    private val mainHandler: Handler by lazy { Handler(Looper.getMainLooper()) }

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        refreshModes()
        hookClasses(module, param.defaultClassLoader)
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { classLoader ->
            hookClasses(module, classLoader)
        }
    }

    override fun onConfigChanged() {
        val nextLeft = ConfigManager.getString(KEY_LEFT_GRAVITY, MODE_DEFAULT)
        val nextRight = ConfigManager.getString(KEY_RIGHT_GRAVITY, MODE_DEFAULT)
        if (nextLeft == leftMode && nextRight == rightMode) return
        leftMode = nextLeft
        rightMode = nextRight
        val holders = synchronized(trackedHolders) { trackedHolders.toMap() }
        runOnMain {
            holders.forEach { (holder, isLeft) ->
                applyGravity(holder, isLeft)
            }
        }
    }

    private fun refreshModes() {
        leftMode = ConfigManager.getString(KEY_LEFT_GRAVITY, MODE_DEFAULT)
        rightMode = ConfigManager.getString(KEY_RIGHT_GRAVITY, MODE_DEFAULT)
    }

    private fun hookClasses(module: XposedModule, classLoader: ClassLoader) {
        hookClass(module, classLoader, LEFT_HOLDER_CLASS, isLeft = true)
        RIGHT_HOLDER_CLASSES.forEach { className ->
            hookClass(module, classLoader, className, isLeft = false)
        }
    }

    private fun hookClass(
        module: XposedModule,
        classLoader: ClassLoader,
        className: String,
        isLeft: Boolean,
    ) {
        runCatching {
            val clazz = classLoader.loadClass(className)
            if (hookedClasses.add(clazz)) hookBindings(module, clazz, isLeft)
        }.onFailure { error ->
            if (error !is ClassNotFoundException) {
                logError(module, "failed to hook $className: ${error.message}")
            }
        }
    }

    private fun hookBindings(module: XposedModule, holderClass: Class<*>, isLeft: Boolean) {
        holderClass.declaredMethods
            .filter { method ->
                (method.name == "bind" || method.name == "updatePartial") &&
                    method.parameterTypes.size == 2
            }
            .forEach { method ->
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    runCatching {
                        val holder = chain.thisObject ?: return@runCatching
                        trackedHolders[holder] = isLeft
                        applyGravity(holder, isLeft)
                    }.onFailure { error ->
                        logError(module, "failed to apply text gravity: ${error.message}")
                    }
                    result
                }
                log(module) { "hooked ${holderClass.name}#${method.name}" }
            }
    }

    private fun applyGravity(holder: Any, isLeft: Boolean) {
        val root = holderView(holder) ?: return
        val layoutParams = root.layoutParams as? FrameLayout.LayoutParams ?: return
        val mode = if (isLeft) leftMode else rightMode
        val horizontal = when (mode) {
            MODE_CENTER -> Gravity.CENTER_HORIZONTAL
            MODE_LEFT -> Gravity.START
            MODE_RIGHT -> Gravity.END
            else -> if (isLeft) Gravity.START else Gravity.END
        }
        val gravity = (layoutParams.gravity and Gravity.VERTICAL_GRAVITY_MASK) or horizontal
        if (layoutParams.gravity != gravity) {
            layoutParams.gravity = gravity
            root.layoutParams = layoutParams
        }
    }

    private fun holderView(holder: Any): View? = runCatching {
        val type = holder.javaClass
        val method = synchronized(viewMethods) {
            viewMethods[type] ?: type.getMethod("getView").also { viewMethods[type] = it }
        }
        method.invoke(holder) as? View
    }.getOrNull()

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post { runCatching(block) }
        }
    }
}
