package io.github.hyperisland.xposed.hook

import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

/**
 * 超级岛左右文本排列（靠左 / 居中 / 靠右）。**只移动文字，图标原位。**
 *
 * 结构（`dynamic_big_island_template_standard.xml`）：
 *
 * ```
 * big_container(LinearLayout)
 * ├── area_left (FrameLayout, weight=1)
 * ├── area_cutout (30dp, 摄像头挖空)
 * └── area_right (FrameLayout, weight=1)
 * ```
 * 每个区域挂一个模块根视图，根视图里可能是：
 * - 纯文本：`right_text` / `text` → [FrameLayout → FadeLinearLayout → TextView…]
 * - 图标+文字：`image_text_1..4` → [FrameLayout →(icon, 文字容器 → FadeLinearLayout → TextView…)]
 *
 * 做法（参考 HyperLyric 的 `setFillParentWidthForInjection` + `MaxWidthFrameLayout`）：
 * 1. 模块根视图 `layout_width = MATCH_PARENT`，让根视图拿到整个区域宽度。
 *    岛宽预测量走 `calculateBigIslandWidth()` 的 `measure(AT_MOST(screenWidth/2))`，
 *    而 `MATCH_PARENT` 在 `AT_MOST` 父级下仍按内容宽测量，只有最终布局（区域宽度被设成
 *    具体像素 → `EXACTLY`）才铺满 —— 所以岛宽、摄像头挖空、另一侧位置都不变。
 * 2. 文字容器（根视图里含 TextView 的那个直接子视图）用 `translationX` 排列：
 *    以「图标与该侧内边缘之间」的空档为范围，取左/中/右。
 * 3. 图标是文字容器的**兄弟节点**，从不平移、也不改它的任何参数 → 图标原位。
 * 4. 位移每次布局按当前宽度重算，区域宽度是 Folme 动画出来的也能跟随。
 */
object IslandTextAlignHook : BaseHook() {

    private const val TAG = "HyperIsland[IslandAlign]"

    private const val KEY_LEFT_ALIGN = "pref_island_left_text_gravity"
    private const val KEY_RIGHT_ALIGN = "pref_island_right_text_gravity"

    private const val ALIGN_DEFAULT = "default"
    private const val ALIGN_CENTER = "center"
    private const val ALIGN_LEFT = "left"
    private const val ALIGN_RIGHT = "right"

    private const val SIDE_LEFT = 1
    private const val SIDE_RIGHT = 2

    /** 大岛所有模块 Holder：只从中取「根视图在 area_* 下」的那些。 */
    private val HOLDER_CLASSES = arrayOf(
        "miui.systemui.dynamicisland.module.IslandIconViewHolder",
        "miui.systemui.dynamicisland.module.IslandImageTextViewHolder",
        "miui.systemui.dynamicisland.module.IslandImageTextView2Holder",
        "miui.systemui.dynamicisland.module.IslandImageTextView3Holder",
        "miui.systemui.dynamicisland.module.IslandImageTextView4Holder",
        "miui.systemui.dynamicisland.module.IslandProgressTextViewHolder",
        "miui.systemui.dynamicisland.module.IslandRightTextViewHolder",
        "miui.systemui.dynamicisland.module.IslandFixedWidthDigitViewHolder",
        "miui.systemui.dynamicisland.module.IslandSameWidthDigitViewHolder",
        "miui.systemui.dynamicisland.module.IslandCombineImageViewHolder",
        "miui.systemui.dynamicisland.module.IslandIconFixedWidthTextHolder",
        "miui.systemui.dynamicisland.module.IslandTextOverIconHolder",
        "miui.systemui.dynamicisland.module.IslandTextViewHolder",
    )

    private class TextSlot(
        val root: View,
        val textHost: View,
        val area: View,
        val side: Int,
        val listener: View.OnLayoutChangeListener,
    )

    private val hookedClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    )
    private val viewMethods = Collections.synchronizedMap(
        WeakHashMap<Class<*>, Method>()
    )

    /** 根视图 -> 槽位（弱 key；值不反向持有 key，避免 WeakHashMap 泄漏）。 */
    private val slots = Collections.synchronizedMap(
        WeakHashMap<View, TextSlot>()
    )
    private val originalWidths = Collections.synchronizedMap(
        WeakHashMap<View, Int>()
    )
    private val watchedRoots = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    )

    @Volatile private var leftAlign = ALIGN_DEFAULT
    @Volatile private var rightAlign = ALIGN_DEFAULT

    @Volatile private var logModule: XposedModule? = null

    private val mainHandler: Handler by lazy { Handler(Looper.getMainLooper()) }

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        logModule = module
        refreshAlign()
        HOLDER_CLASSES.forEach { className ->
            hookClass(module, param.defaultClassLoader, className)
        }
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { loader ->
            HOLDER_CLASSES.forEach { className -> hookClass(module, loader, className) }
        }
    }

    override fun onConfigChanged() {
        val nextLeft = ConfigManager.getString(KEY_LEFT_ALIGN, ALIGN_DEFAULT)
        val nextRight = ConfigManager.getString(KEY_RIGHT_ALIGN, ALIGN_DEFAULT)
        if (nextLeft == leftAlign && nextRight == rightAlign) return
        leftAlign = nextLeft
        rightAlign = nextRight

        val snapshot = synchronized(slots) { slots.values.toList() }
        if (snapshot.isEmpty()) return
        runOnMain {
            snapshot.forEach { slot ->
                runCatching {
                    applySlot(slot)
                    slot.root.requestLayout()
                }
            }
        }
    }

    private fun refreshAlign() {
        leftAlign = ConfigManager.getString(KEY_LEFT_ALIGN, ALIGN_DEFAULT)
        rightAlign = ConfigManager.getString(KEY_RIGHT_ALIGN, ALIGN_DEFAULT)
    }

    private fun hookClass(module: XposedModule, loader: ClassLoader, className: String) {
        runCatching {
            val clazz = loader.loadClass(className)
            if (hookedClasses.add(clazz)) hookMethods(module, clazz)
        }.onFailure { error ->
            if (error !is ClassNotFoundException) {
                logError(module, "failed to hook $className: ${error.message}")
            }
        }
    }

    private fun hookMethods(module: XposedModule, clazz: Class<*>) {
        clazz.declaredMethods
            .filter { method ->
                when (method.name) {
                    "bind", "updatePartial" -> method.parameterTypes.size == 2
                    "updateWidth" -> method.parameterTypes.size == 1 &&
                        method.parameterTypes[0] == Int::class.javaPrimitiveType
                    else -> false
                }
            }
            .forEach { method ->
                method.isAccessible = true
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    runCatching {
                        val holder = chain.thisObject ?: return@runCatching
                        onHolderUpdated(holder)
                    }.onFailure { error ->
                        logError(module, "failed to align island text: ${error.message}")
                    }
                    result
                }
                log(module) { "hooked ${clazz.name}#${method.name}" }
            }
    }

    private fun onHolderUpdated(holder: Any) {
        val root = holderView(holder) ?: return
        val side = islandAreaSide(root) ?: return
        val area = root.parent as? View ?: return
        val slot = synchronized(slots) { slots[root] }
        if (slot != null && slot.textHost.parent === root) {
            applySlot(slot)
            return
        }
        val textHost = findTextChild(root)
        if (textHost == null) {
            logModule?.let { module ->
                logFailureOnce(module, "align-no-text") {
                    "align skipped: no text child, view=${root.javaClass.simpleName} " +
                        "children=${(root as? ViewGroup)?.childCount}"
                }
            }
            return
        }
        val newSlot = TextSlot(root, textHost, area, side, newListener(root, textHost, area, side))
        synchronized(slots) { slots[root] = newSlot }
        watchedRoots.add(root)
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit

            override fun onViewDetachedFromWindow(view: View) {
                synchronized(slots) { slots.remove(view) }?.let { bound ->
                    bound.area.removeOnLayoutChangeListener(bound.listener)
                }
                view.translationX = 0f
            }
        })
        applySlot(newSlot)
        logModule?.let { module ->
            log(module) {
                "align attached: side=$side " +
                    "root=${root.javaClass.simpleName} host=${textHost.javaClass.simpleName}"
            }
        }
    }

    /**
     * 区域宽度是 Folme 动画出来的，监听区域布局，用当前宽度重算文字位移。
     * 这里只写 translationX，不触发布局，因此没有递归风险。
     */
    private fun newListener(
        root: View,
        textHost: View,
        area: View,
        side: Int,
    ): View.OnLayoutChangeListener {
        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            runCatching { applyOffset(root, textHost, area, side) }
        }
        area.addOnLayoutChangeListener(listener)
        return listener
    }

    private fun applySlot(slot: TextSlot) {
        val mode = modeOf(slot.side)
        if (mode == ALIGN_DEFAULT) {
            restoreFill(slot.root)
            if (slot.textHost.translationX != 0f) slot.textHost.translationX = 0f
            return
        }
        if (!ensureFill(slot.root)) return
        applyOffset(slot.root, slot.textHost, slot.area, slot.side)
    }

    private fun applyOffset(root: View, textHost: View, area: View, side: Int) {
        if (root.width <= 0 || textHost.width <= 0) return
        val mode = modeOf(side)

        // 文字可用的横向范围：根视图内容区，扣掉同一层里的其它视图（图标）。
        var spanLeft = root.paddingLeft.toFloat()
        var spanRight = (root.width - root.paddingRight).toFloat()
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                val sibling = root.getChildAt(index)
                if (sibling === textHost) continue
                if (sibling.visibility != View.VISIBLE || sibling.width <= 0) continue
                if (sibling.right <= textHost.left) {
                    if (sibling.right > spanLeft) spanLeft = sibling.right.toFloat()
                } else if (sibling.left >= textHost.right) {
                    if (sibling.left < spanRight) spanRight = sibling.left.toFloat()
                }
            }
        }

        val room = spanRight - spanLeft
        val hostWidth = textHost.width.toFloat()
        val target: Float
        if (mode == ALIGN_DEFAULT || room <= hostWidth) {
            target = textHost.left.toFloat()
        } else {
            target = when (mode) {
                ALIGN_LEFT -> spanLeft
                ALIGN_RIGHT -> spanRight - hostWidth
                else -> spanLeft + (room - hostWidth) / 2f
            }
        }
        val offset = target - textHost.left
        if (textHost.translationX != offset) {
            textHost.translationX = offset
            logModule?.let { module ->
                log(module) {
                    "align offset=${offset.toInt()} mode=$mode room=${room.toInt()} " +
                        "host=${textHost.javaClass.simpleName} root=${root.javaClass.simpleName}"
                }
            }
        }
    }

    private fun modeOf(side: Int): String = if (side == SIDE_LEFT) leftAlign else rightAlign

    /** 根视图在区域里的水平 gravity 决定“铺满后图标是否还在原位”。 */
    private fun ensureFill(root: View): Boolean {
        val params = root.layoutParams as? FrameLayout.LayoutParams ?: return false
        val absolute = Gravity.getAbsoluteGravity(params.gravity, root.layoutDirection) and
            Gravity.HORIZONTAL_GRAVITY_MASK
        // 居中摆放的模块铺满后位置会变，直接跳过；start/end/未指定都等于“贴外侧”。
        if (absolute == Gravity.CENTER) {
            logModule?.let { module ->
                logFailureOnce(module, "align-root-gravity") {
                    "align skipped: centered root gravity=0x${params.gravity.toString(16)} " +
                        "view=${root.javaClass.simpleName}"
                }
            }
            return false
        }
        synchronized(originalWidths) { originalWidths.getOrPut(root) { params.width } }
        if (params.width != ViewGroup.LayoutParams.MATCH_PARENT) {
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            root.layoutParams = params
        }
        return true
    }

    private fun restoreFill(root: View) {
        val original = synchronized(originalWidths) { originalWidths.remove(root) } ?: return
        val params = root.layoutParams ?: return
        if (params.width == original) return
        params.width = original
        root.layoutParams = params
    }

    /**
     * 找根视图里「含 TextView」的直接子视图 —— 就是文字容器。图标是另一个子视图，不会被选中。
     * 纯文本模块（只有 FadeLinearLayout）也能命中。
     */
    private fun findTextChild(root: View): View? {
        val group = root as? ViewGroup ?: return null
        var best: View? = null
        var bestCount = 0
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index)
            val count = countTextViews(child, 0)
            if (count > bestCount) {
                bestCount = count
                best = child
            }
        }
        return best
    }

    private fun countTextViews(view: View, depth: Int): Int {
        if (depth > 4) return 0
        if (view is TextView) return 1
        if (view !is ViewGroup) return 0
        var count = 0
        for (index in 0 until view.childCount) {
            count += countTextViews(view.getChildAt(index), depth + 1)
        }
        return count
    }

    /**
     * 只认大岛区域，纯结构判断（系统插件做了资源名混淆，不能依赖 `area_left` 这类名字）：
     * 父级是 `big_container` / `fake_big_container` 的第 0 或第 2 个子视图，
     * 中间那个是空的 FrameLayout（摄像头挖空）。
     */
    private fun islandAreaSide(root: View): Int? {
        val parent = root.parent as? FrameLayout ?: return null
        val container = parent.parent as? LinearLayout ?: return null
        if (container.childCount != 3) return null
        val cutout = container.getChildAt(1)
        if (cutout !is FrameLayout || cutout.childCount != 0) return null
        return when (container.indexOfChild(parent)) {
            0 -> SIDE_LEFT
            2 -> SIDE_RIGHT
            else -> null
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
