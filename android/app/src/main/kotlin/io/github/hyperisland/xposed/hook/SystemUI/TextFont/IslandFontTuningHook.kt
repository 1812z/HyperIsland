package io.github.hyperisland.xposed.hook

import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.widget.Chronometer
import android.widget.TextView
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.Collections
import java.util.WeakHashMap

/**
 * 超级岛文本字体微调：
 * - 字体粗细（%）：基于当前字体派生等比字重，100% 保持系统原样；
 * - 超级岛文本宽度（%）：textScaleX 水平收窄，只处理文本组件（title / frontTitle / content）；
 * - 时间文本宽度（%）：textScaleX 水平收窄，只处理计时 / 倒计时（HyperChronometer）。
 *
 * 只挂超级岛自己的两条绑定路径 [BaseIslandModuleViewHolder#setFont] 与
 * [BaseIslandModuleViewHolder#setFixedFont]，不触碰焦点通知 / 通知中心里由
 * moduleV3 holder 渲染的同一套通知视图。
 */
object IslandFontTuningHook : BaseHook() {

    private const val TAG = "HyperIsland[IslandFont]"

    private const val KEY_TEXT_WEIGHT = "pref_island_text_weight_percent"
    private const val KEY_TEXT_WIDTH = "pref_island_text_width_percent"
    private const val KEY_TIME_WIDTH = "pref_island_time_width_percent"

    private const val DEFAULT_PERCENT = 100
    private const val MIN_WIDTH_PERCENT = 30
    private const val MIN_WEIGHT_PERCENT = 50
    private const val MAX_WEIGHT_PERCENT = 200

    private const val BASE_ISLAND_HOLDER_CLASS =
        "miui.systemui.dynamicisland.module.BaseIslandModuleViewHolder"

    /** 会承载文本组件（title / frontTitle / content）的岛 Holder。 */
    private val TEXT_HOLDER_CLASSES = arrayOf(
        "miui.systemui.dynamicisland.module.IslandTextViewHolder",
        "miui.systemui.dynamicisland.module.IslandRightTextViewHolder",
        "miui.systemui.dynamicisland.module.IslandTextOverIconHolder",
        "miui.systemui.dynamicisland.module.IslandImageTextView3Holder",
    )

    /** 文本 Holder 里的文本字段名，安装 Hook 时一次性解析。 */
    private val TEXT_FIELD_NAMES = arrayOf("title", "frontTitle", "content", "titleView")

    private val hookedClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    )
    private val trackedTexts = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<TextView, Boolean>())
    )
    private val trackedTimers = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<TextView, Boolean>())
    )

    /** 第一次收窄前的 textScaleX，恢复 100% 时还原。 */
    private val originalScales = Collections.synchronizedMap(
        WeakHashMap<TextView, Float>()
    )

    /** 字重调整前的原始字体，以及我们实际应用过的字体，用于识别系统重设。 */
    private val weightSnapshots = Collections.synchronizedMap(
        WeakHashMap<TextView, WeightSnapshot>()
    )

    @Volatile private var textWeightPercent = DEFAULT_PERCENT
    @Volatile private var textWidthPercent = DEFAULT_PERCENT
    @Volatile private var timeWidthPercent = DEFAULT_PERCENT

    private val mainHandler: Handler by lazy { Handler(Looper.getMainLooper()) }

    private data class WeightSnapshot(val original: Typeface?, val applied: Typeface, val percent: Int)

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        refreshConfig()
        hookClass(module, param.defaultClassLoader)
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { classLoader ->
            hookClass(module, classLoader)
        }
    }

    override fun onConfigChanged() {
        refreshConfig()
        val texts = synchronized(trackedTexts) { trackedTexts.toList() }
        val timers = synchronized(trackedTimers) { trackedTimers.toList() }
        runOnMain {
            texts.forEach { textView ->
                applyTextWidth(textView)
                applyWeight(textView)
            }
            timers.forEach { textView ->
                applyTimeWidth(textView)
                applyWeight(textView)
            }
        }
    }

    private fun refreshConfig() {
        textWeightPercent = ConfigManager.getInt(KEY_TEXT_WEIGHT, DEFAULT_PERCENT)
            .coerceIn(MIN_WEIGHT_PERCENT, MAX_WEIGHT_PERCENT)
        textWidthPercent = ConfigManager.getInt(KEY_TEXT_WIDTH, DEFAULT_PERCENT)
            .coerceIn(MIN_WIDTH_PERCENT, DEFAULT_PERCENT)
        timeWidthPercent = ConfigManager.getInt(KEY_TIME_WIDTH, DEFAULT_PERCENT)
            .coerceIn(MIN_WIDTH_PERCENT, DEFAULT_PERCENT)
    }

    private fun hookClass(module: XposedModule, classLoader: ClassLoader) {
        runCatching {
            val clazz = classLoader.loadClass(BASE_ISLAND_HOLDER_CLASS)
            if (hookedClasses.add(clazz)) {
                hookSetFont(module, clazz)
                hookSetFixedFont(module, clazz)
            }
        }.onFailure { error ->
            if (error !is ClassNotFoundException) {
                logError(module, "failed to hook island font: ${error.message}")
            }
        }
        TEXT_HOLDER_CLASSES.forEach { className ->
            runCatching {
                val clazz = classLoader.loadClass(className)
                if (hookedClasses.add(clazz)) hookTextBind(module, clazz)
            }.onFailure { error ->
                if (error !is ClassNotFoundException) {
                    logError(module, "failed to hook $className: ${error.message}")
                }
            }
        }
    }

    /**
     * 文本组件是 ViewStub 懒加载的：`bind` 开头调用 setFont 时 title 可能还是 null，
     * 真正创建发生在 bind 内部。所以安装时先解析 Holder 的文本字段，bind / updatePartial
     * 结束后直接按字段取 TextView，不做任何视图树遍历。
     */
    private fun hookTextBind(module: XposedModule, holderClass: Class<*>) {
        val textFields = TEXT_FIELD_NAMES.mapNotNull { name ->
            runCatching {
                holderClass.getDeclaredField(name).apply { isAccessible = true }
            }.getOrNull()
        }
        if (textFields.isEmpty()) return
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
                        textFields.forEach { field ->
                            (field.get(holder) as? TextView)?.let { textView ->
                                if (textView !is Chronometer) onTextBound(textView)
                            }
                        }
                    }.onFailure { error ->
                        logError(module, "failed to tune island text: ${error.message}")
                    }
                    result
                }
                log(module) { "hooked ${holderClass.name}#${method.name}" }
            }
    }

    /** 文本组件：title / frontTitle / content 在 bind 时都会走 setFont。 */
    private fun hookSetFont(module: XposedModule, holderClass: Class<*>) {
        holderClass.declaredMethods
            .filter { method ->
                method.name == "setFont" &&
                    method.parameterTypes.size == 2 &&
                    TextView::class.java.isAssignableFrom(method.parameterTypes[1])
            }
            .forEach { method ->
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    runCatching {
                        (chain.args.getOrNull(1) as? TextView)?.let(::onTextBound)
                    }.onFailure { error ->
                        logError(module, "failed to tune island text: ${error.message}")
                    }
                    result
                }
                log(module) { "hooked ${holderClass.name}#${method.name}" }
            }
    }

    /** 计时 / 倒计时：等宽数字会走 setFixedFont。 */
    private fun hookSetFixedFont(module: XposedModule, holderClass: Class<*>) {
        holderClass.declaredMethods
            .filter { method ->
                method.name == "setFixedFont" &&
                    method.parameterTypes.size == 1 &&
                    TextView::class.java.isAssignableFrom(method.parameterTypes[0])
            }
            .forEach { method ->
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    runCatching {
                        (chain.args.getOrNull(0) as? TextView)?.let(::onTimerBound)
                    }.onFailure { error ->
                        logError(module, "failed to tune island timer: ${error.message}")
                    }
                    result
                }
                log(module) { "hooked ${holderClass.name}#${method.name}" }
            }
    }

    private fun onTextBound(textView: TextView) {
        trackedTexts.add(textView)
        applyTextWidth(textView)
        applyWeight(textView)
    }

    private fun onTimerBound(textView: TextView) {
        if (textView !is Chronometer) return
        trackedTimers.add(textView)
        applyTimeWidth(textView)
        applyWeight(textView)
    }

    private fun applyTextWidth(textView: TextView) = applyScale(textView, textWidthPercent)

    private fun applyTimeWidth(textView: TextView) = applyScale(textView, timeWidthPercent)

    private fun applyScale(textView: TextView, percent: Int) {
        if (percent == DEFAULT_PERCENT) {
            val original = synchronized(originalScales) { originalScales.remove(textView) }
            if (original != null && textView.textScaleX != original) {
                textView.textScaleX = original
            }
            return
        }
        val target = percent / 100f
        if (textView.textScaleX == target) return
        synchronized(originalScales) {
            if (!originalScales.containsKey(textView)) {
                originalScales[textView] = textView.textScaleX
            }
        }
        textView.textScaleX = target
    }

    private fun applyWeight(textView: TextView) {
        val percent = textWeightPercent
        val current = textView.typeface
        val snapshot = synchronized(weightSnapshots) { weightSnapshots[textView] }

        if (percent == DEFAULT_PERCENT) {
            if (snapshot == null) return
            synchronized(weightSnapshots) { weightSnapshots.remove(textView) }
            if (current === snapshot.applied) textView.typeface = snapshot.original
            return
        }
        if (snapshot != null && current === snapshot.applied && snapshot.percent == percent) return

        // 系统重新设过字体（例如 narrowFont 切换）时，以当前字体作为新的基准。
        val base = if (snapshot != null && current === snapshot.applied) snapshot.original else current
        val baseWeight = (base ?: Typeface.DEFAULT).weight.coerceAtLeast(1)
        val weight = (baseWeight * percent / 100f).toInt().coerceIn(100, 900)
        val target = Typeface.create(base ?: Typeface.DEFAULT, weight, false)
        synchronized(weightSnapshots) {
            weightSnapshots[textView] = WeightSnapshot(base, target, percent)
        }
        if (current !== target) textView.typeface = target
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post { runCatching(block) }
        }
    }
}
