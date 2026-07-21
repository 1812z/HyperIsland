package io.github.hyperisland.xposed.hook

import android.content.res.ColorStateList
import android.graphics.Color
import android.text.Spanned
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

/** Overrides only the fallback colors of V3 focus-notification text fields. */
object FocusNotificationTextColorHook : BaseHook() {

    private const val TAG = "HyperIsland[FocusNotificationTextColor]"
    private const val KEY_TEXT_COLOR_MODE = "pref_focus_notification_text_color_mode"

    private const val MODE_DEFAULT = "default"
    private const val MODE_BLACK = "black"
    private const val MODE_FOLLOW_STATUS_BAR = "follow_status_bar"
    private const val MODE_INVERT_STATUS_BAR = "invert_status_bar"

    private val hookedClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    )
    private val trackedHolders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
    )
    private val injectedFields = Collections.synchronizedMap(
        WeakHashMap<Any, Set<String>>()
    )
    private val originalFieldColors = Collections.synchronizedMap(
        WeakHashMap<Any, Map<String, Any?>>()
    )
    private val originalTextColors = Collections.synchronizedMap(
        WeakHashMap<Any, List<OriginalTextColor>>()
    )
    private val colorSetters = Collections.synchronizedMap(
        WeakHashMap<Any, Map<String, Method>>()
    )
    private val attachRefreshViews = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<TextView, Boolean>())
    )

    @Volatile private var islandAnimationRunning = false
    @Volatile private var collapseAnimationRunning = false
    @Volatile private var pendingTintRefresh = false
    @Volatile private var tintRefreshScheduled = false

    private val statusBarTintListener: (Int) -> Unit = {
        if (islandAnimationRunning || tintRefreshScheduled) {
            pendingTintRefresh = true
        } else {
            refreshInjectedTextColors()
        }
    }

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        IslandTextColorHook.addStatusBarTintListener(statusBarTintListener)
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { classLoader ->
            hookClasses(module, classLoader)
        }
    }

    override fun onConfigChanged() {
        val holders = synchronized(trackedHolders) { trackedHolders.toList() }
        holders.forEach(::reapplyHolderColors)
    }

    private fun hookClasses(module: XposedModule, classLoader: ClassLoader) {
        runCatching {
            val holderClass = classLoader.loadClass(
                "miui.systemui.notification.focus.moduleV3.ModuleViewHolder"
            )
            if (hookedClasses.add(holderClass)) {
                hookTextColorResolver(module, holderClass)
            }
        }.onFailure { error ->
            if (error !is ClassNotFoundException) {
                logError(module, "failed to hook ModuleViewHolder: ${error.message}")
            }
        }

        runCatching {
            val coordinatorClass = classLoader.loadClass(
                "miui.systemui.dynamicisland.event.DynamicIslandEventCoordinator"
            )
            if (hookedClasses.add(coordinatorClass)) {
                hookAnimationLifecycle(module, coordinatorClass)
            }
        }.onFailure { error ->
            if (error !is ClassNotFoundException) {
                logError(module, "failed to hook DynamicIslandEventCoordinator: ${error.message}")
            }
        }

        runCatching {
            val collapseCoordinatorClass = classLoader.loadClass(
                "miui.systemui.dynamicisland.event.CollapseEventCoordinator"
            )
            if (hookedClasses.add(collapseCoordinatorClass)) {
                hookCollapseDirection(module, collapseCoordinatorClass)
            }
        }.onFailure { error ->
            if (error !is ClassNotFoundException) {
                logError(module, "failed to hook CollapseEventCoordinator: ${error.message}")
            }
        }
    }

    private fun hookCollapseDirection(module: XposedModule, coordinatorClass: Class<*>) {
        coordinatorClass.declaredMethods
            .filter { method -> method.name == "handleAppEvent" && method.parameterTypes.size == 3 }
            .forEach { method ->
                module.hook(method).intercept { chain ->
                    if (chain.args.firstOrNull()?.javaClass?.name == COLLAPSE_EVENT_CLASS) {
                        collapseAnimationRunning = true
                    }
                    chain.proceed()
                }
                log(module, "hooked CollapseEventCoordinator#handleAppEvent")
            }
    }

    private fun hookAnimationLifecycle(module: XposedModule, coordinatorClass: Class<*>) {
        coordinatorClass.declaredMethods
            .filter { method -> method.name == "onAnimationStart" }
            .forEach { method ->
                module.hook(method).intercept { chain ->
                    islandAnimationRunning = true
                    chain.proceed()
                }
                log(module, "hooked DynamicIslandEventCoordinator#onAnimationStart")
            }
        coordinatorClass.declaredMethods
            .filter { method ->
                method.name == "onAnimationFinished" || method.name == "onAnimationCancel"
            }
            .forEach { method ->
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    islandAnimationRunning = false
                    val collapsed = collapseAnimationRunning
                    collapseAnimationRunning = false
                    if (!collapsed || method.name == "onAnimationCancel") {
                        schedulePendingTintRefresh()
                    }
                    result
                }
                log(module, "hooked DynamicIslandEventCoordinator#${method.name}")
            }
    }

    private fun schedulePendingTintRefresh() {
        if (!pendingTintRefresh || tintRefreshScheduled) return
        tintRefreshScheduled = true
        Choreographer.getInstance().postFrameCallback {
            tintRefreshScheduled = false
            if (islandAnimationRunning || !pendingTintRefresh) return@postFrameCallback
            pendingTintRefresh = false
            refreshInjectedTextColors()
        }
    }

    private fun hookTextColorResolver(module: XposedModule, holderClass: Class<*>) {
        holderClass.declaredMethods
            .filter { method ->
                method.name == "initTextAndColor" && method.parameterTypes.size == 1
            }
            .forEach { method ->
                module.hook(method).intercept { chain ->
                    val holder = chain.thisObject
                    if (holder != null) clearInjectedColors(holder)
                    val result = chain.proceed()
                    if (holder != null && isDynamicIslandHolder(holder)) {
                        trackedHolders.add(holder)
                        applyOverrideColors(holder)
                    }
                    result
                }
                log(module, "hooked ModuleViewHolder#initTextAndColor")
            }
    }

    private fun applyOverrideColors(holder: Any) {
        val mode = getConfiguredMode()
        if (mode == MODE_DEFAULT) return

        val color = resolveTextColor(mode)
        val injected = mutableSetOf<String>()
        val originals = mutableMapOf<String, Any?>()
        val setters = mutableMapOf<String, Method>()

        captureOriginalTextColors(holder)

        COLOR_FIELDS.forEach { field ->
            val getter = holder.javaClass.methods.firstOrNull { method ->
                method.name == "get$field" && method.parameterTypes.isEmpty()
            } ?: return@forEach
            val setter = holder.javaClass.methods.firstOrNull { method ->
                method.name == "set$field" && method.parameterTypes.size == 1
            } ?: return@forEach
            originals[field] = runCatching { getter.invoke(holder) }.getOrNull()
            if (runCatching { setter.invoke(holder, color) }.isSuccess) {
                injected.add(field)
                setters[field] = setter
            }
        }

        if (injected.isNotEmpty()) {
            injectedFields[holder] = injected
            originalFieldColors[holder] = originals
            colorSetters[holder] = setters
        }
    }

    private fun isDynamicIslandHolder(holder: Any): Boolean {
        return ISLAND_MARKER_METHODS.any { methodName ->
            runCatching {
                holder.javaClass.methods.firstOrNull { method ->
                    method.name == methodName && method.parameterTypes.isEmpty()
                }?.invoke(holder) as? Boolean
            }.getOrNull() == true
        }
    }

    private fun clearInjectedColors(holder: Any) {
        val fields = injectedFields.remove(holder) ?: return
        originalTextColors.remove(holder)?.forEach { original ->
            original.view.get()?.setTextColor(original.colors)
        }
        val originals = originalFieldColors.remove(holder).orEmpty()
        val setters = colorSetters.remove(holder).orEmpty()
        fields.forEach { field ->
            runCatching {
                setters[field]?.invoke(holder, originals[field])
            }
        }
    }

    private fun captureOriginalTextColors(holder: Any) {
        if (originalTextColors.containsKey(holder)) return
        val root = runCatching {
            holder.javaClass.methods.firstOrNull { method ->
                method.name == "getView" && method.parameterTypes.isEmpty()
            }?.invoke(holder) as? View
        }.getOrNull() ?: return
        val colors = mutableListOf<OriginalTextColor>()
        collectTextColors(root, holder, colors)
        if (colors.isNotEmpty()) originalTextColors[holder] = colors
    }

    private fun collectTextColors(view: View, holder: Any, colors: MutableList<OriginalTextColor>) {
        if (view is TextView) {
            val field = targetColorField(view)
            if (field != null) {
                colors.add(OriginalTextColor(WeakReference(view), view.textColors, field))
                ensureAttachRefresh(view, holder)
            }
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                collectTextColors(view.getChildAt(index), holder, colors)
            }
        }
    }

    private fun ensureAttachRefresh(textView: TextView, holder: Any) {
        if (!attachRefreshViews.add(textView)) return
        val holderRef = WeakReference(holder)
        textView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                val target = view as? TextView ?: return
                val mode = getConfiguredMode()
                if (mode != MODE_FOLLOW_STATUS_BAR && mode != MODE_INVERT_STATUS_BAR) return
                if (islandAnimationRunning) {
                    pendingTintRefresh = true
                } else {
                    val trackedHolder = holderRef.get()
                    val color = resolveTextColor(mode)
                    if (trackedHolder != null) applyHolderFieldColors(trackedHolder, color)
                    applyTextColor(target, color)
                }
            }

            override fun onViewDetachedFromWindow(view: View) = Unit
        })
    }

    private fun reapplyHolderColors(holder: Any) {
        runCatching {
            val view = holder.javaClass.methods.firstOrNull { method ->
                method.name == "getView" && method.parameterTypes.isEmpty()
            }?.invoke(holder) as? View ?: return
            view.post {
                clearInjectedColors(holder)
                applyOverrideColors(holder)
                val fields = injectedFields[holder] ?: return@post
                val mode = getConfiguredMode()
                val color = resolveTextColor(mode)
                applyVisibleTextColor(view, fields, color)
            }
        }
    }

    private fun refreshInjectedTextColors() {
        val mode = getConfiguredMode()
        if (mode != MODE_FOLLOW_STATUS_BAR && mode != MODE_INVERT_STATUS_BAR) return
        val holders = synchronized(trackedHolders) { trackedHolders.toList() }
        holders.forEach { holder ->
            val fields = injectedFields[holder] ?: return@forEach
            val views = originalTextColors[holder].orEmpty().mapNotNull { original ->
                original.view.get()?.takeIf { it.isAttachedToWindow }?.let { original to it }
            }
            if (views.isEmpty()) return@forEach
            val color = resolveTextColor(mode)
            applyHolderFieldColors(holder, color)
            views.forEach { (original, textView) ->
                if (original.field in fields) applyTextColor(textView, color)
            }
        }
    }

    private fun applyHolderFieldColors(holder: Any, color: Int) {
        val fields = injectedFields[holder] ?: return
        val setters = colorSetters[holder].orEmpty()
        fields.forEach { field ->
            runCatching { setters[field]?.invoke(holder, color) }
        }
    }

    private fun getHolderView(holder: Any): View? {
        return runCatching {
            holder.javaClass.methods.firstOrNull { method ->
                method.name == "getView" && method.parameterTypes.isEmpty()
            }?.invoke(holder) as? View
        }.getOrNull()
    }

    private fun applyVisibleTextColor(view: View, fields: Set<String>, color: Int) {
        if (view is TextView) {
            val field = targetColorField(view)
            if (field != null && field in fields) applyTextColor(view, color)
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                applyVisibleTextColor(view.getChildAt(index), fields, color)
            }
        }
    }

    private fun applyTextColor(textView: TextView, color: Int) {
        val updateMethod = textView.javaClass.methods.firstOrNull { method ->
            method.name == "updateTextWithNewAppearance" && method.parameterTypes.size == 2
        }
        if (updateMethod == null) {
            if (textView.currentTextColor == color) return
            textView.setTextColor(color)
            return
        }

        val text = textView.text as? Spanned
        val spanClass = runCatching {
            textView.javaClass.classLoader.loadClass(
                "miuix.colorful.texteffect.TimerTextEffectSpan"
            )
        }.getOrNull()
        val appearanceMethod = spanClass?.methods?.firstOrNull { method ->
            method.name == "setOldTextAppearance" && method.parameterTypes.size == 2
        }
        val spans = if (text != null && spanClass != null) {
            text.getSpans(0, text.length, spanClass)
        } else {
            emptyArray()
        }
        if (appearanceMethod == null || spans.isEmpty()) {
            textView.setTextColor(color)
            return
        }
        if (runCatching { appearanceMethod.invoke(spans[0], text, color) }.isFailure) {
            textView.setTextColor(color)
            return
        }
        textView.setTextColor(color)
        textView.invalidate()
    }

    private fun resolveTextColor(mode: String): Int {
        return when (mode) {
            MODE_BLACK -> Color.BLACK
            MODE_FOLLOW_STATUS_BAR -> IslandTextColorHook.getStatusBarTint()
            MODE_INVERT_STATUS_BAR -> {
                if (isLightColor(IslandTextColorHook.getStatusBarTint())) Color.BLACK else Color.WHITE
            }
            else -> Color.WHITE
        }
    }

    private fun getConfiguredMode(): String {
        return when (val mode = ConfigManager.getString(KEY_TEXT_COLOR_MODE, MODE_DEFAULT)) {
            MODE_BLACK, MODE_FOLLOW_STATUS_BAR, MODE_INVERT_STATUS_BAR -> mode
            else -> MODE_DEFAULT
        }
    }

    private fun isLightColor(color: Int): Boolean {
        return Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114 >= 128000
    }

    private fun targetColorField(view: TextView): String? {
        if (view.id == View.NO_ID) return null
        val resourceName = runCatching {
            view.resources.getResourceEntryName(view.id)
        }.getOrNull() ?: return null
        return TEXT_ID_TO_FIELD[resourceName]
    }

    private val COLOR_FIELDS = listOf(
        "TitleColor",
        "SubTitleColor",
        "ExtraTitleColor",
        "SpecialTitleColor",
        "ContentColor",
        "SubContentColor",
    )

    private val ISLAND_MARKER_METHODS = listOf(
        "getIsland",
        "getDynamicIsland",
        "isDynamicIsland",
    )

    private val TEXT_ID_TO_FIELD = mapOf(
        "focus_title" to "TitleColor",
        "focus_subtitle" to "SubTitleColor",
        "focus_subtitle_divider" to "SubTitleColor",
        "focus_extra_title" to "ExtraTitleColor",
        "focus_extra_title_divider" to "ExtraTitleColor",
        "focus_special_title" to "SpecialTitleColor",
        "focus_content" to "ContentColor",
        "focus_sub_content" to "SubContentColor",
        "focus_sub_content_divider" to "SubContentColor",
        "focus_function_icon_divider" to "SubContentColor",
    )

    private data class OriginalTextColor(
        val view: WeakReference<TextView>,
        val colors: ColorStateList,
        val field: String,
    )

    private const val COLLAPSE_EVENT_CLASS =
        "miui.systemui.dynamicisland.event.DynamicIslandEvent\$Collapse"

}
