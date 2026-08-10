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

/** Overrides V3 focus-notification text colors. */
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
    private val originalButtonColors = Collections.synchronizedMap(
        WeakHashMap<Any, List<OriginalButtonColor>>()
    )
    private val trackedButtonHolders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
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
        hookClasses(module, param.defaultClassLoader)
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { classLoader ->
            hookClasses(module, classLoader)
        }
    }

    override fun onConfigChanged() {
        val holders = synchronized(trackedHolders) { trackedHolders.toList() }
        holders.forEach(::reapplyHolderColors)
        val buttonHolders = synchronized(trackedButtonHolders) {
            trackedButtonHolders.toList()
        }
        buttonHolders.forEach(::reapplyButtonColors)
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

        holderClass.declaredMethods
            .filter { method -> method.name == "bind" && method.parameterTypes.size == 2 }
            .forEach { method ->
                module.hook(method).intercept { chain ->
                    val holder = chain.thisObject
                    val result = chain.proceed()
                    if (holder != null) {
                        getHolderView(holder)?.post {
                            captureAndApplyButtonColors(holder)
                        }
                    }
                    result
                }
                log(module, "hooked ModuleViewHolder#bind button text colors")
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
        val buttonHolders = synchronized(trackedButtonHolders) {
            trackedButtonHolders.toList()
        }
        buttonHolders.forEach(::applyButtonColors)
    }

    private fun captureAndApplyButtonColors(holder: Any) {
        val root = getHolderView(holder) ?: return
        val buttons = mutableListOf<TextView>()
        collectButtonTextViews(root, buttons)
        if (buttons.isEmpty()) return

        trackedButtonHolders.add(holder)
        originalButtonColors[holder] = buttons.map { button ->
            OriginalButtonColor(WeakReference(button), button.textColors)
        }
        applyButtonColors(holder)
    }

    private fun reapplyButtonColors(holder: Any) {
        val mode = getConfiguredMode()
        if (mode == MODE_DEFAULT) {
            originalButtonColors[holder].orEmpty().forEach { original ->
                original.view.get()?.setTextColor(original.colors)
            }
        } else {
            applyButtonColors(holder)
        }
    }

    private fun applyButtonColors(holder: Any) {
        val mode = getConfiguredMode()
        if (mode == MODE_DEFAULT) return
        val primaryColor = resolveTextColor(mode)
        originalButtonColors[holder].orEmpty().forEach { original ->
            val button = original.view.get() ?: return@forEach
            if (isLightColor(primaryColor)) {
                button.setTextColor(original.colors)
            } else {
                applyTextColor(
                    button,
                    resolveDarkButtonTextColor(original.colors.defaultColor)
                )
            }
        }
    }

    private fun collectButtonTextViews(view: View, buttons: MutableList<TextView>) {
        if (view is TextView && resourceEntryName(view) == BUTTON_TITLE_ID) {
            buttons.add(view)
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                collectButtonTextViews(view.getChildAt(index), buttons)
            }
        }
    }

    private fun getHolderView(holder: Any): View? {
        return runCatching {
            holder.javaClass.methods.firstOrNull { method ->
                method.name == "getView" && method.parameterTypes.isEmpty()
            }?.invoke(holder) as? View
        }.getOrNull()
    }

    private fun resourceEntryName(view: View): String? {
        if (view.id == View.NO_ID) return null
        return runCatching { view.resources.getResourceEntryName(view.id) }.getOrNull()
    }

    private fun applyHolderFieldColors(holder: Any, color: Int) {
        val fields = injectedFields[holder] ?: return
        val setters = colorSetters[holder].orEmpty()
        fields.forEach { field ->
            runCatching { setters[field]?.invoke(holder, color) }
        }
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

    private fun resolveDarkButtonTextColor(originalColor: Int): Int {
        val luminance = colorLuminance(originalColor)
        val gray = BUTTON_DARK_MIN +
            ((BUTTON_DARK_MAX - BUTTON_DARK_MIN) * luminance / MAX_COLOR_LUMINANCE)
        return Color.argb(Color.alpha(originalColor), gray, gray, gray)
    }

    private fun getConfiguredMode(): String {
        return when (val mode = ConfigManager.getString(KEY_TEXT_COLOR_MODE, MODE_DEFAULT)) {
            MODE_BLACK, MODE_FOLLOW_STATUS_BAR, MODE_INVERT_STATUS_BAR -> mode
            else -> MODE_DEFAULT
        }
    }

    private fun isLightColor(color: Int): Boolean {
        return colorLuminance(color) >= 128000
    }

    private fun colorLuminance(color: Int): Int {
        return Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114
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

    private data class OriginalButtonColor(
        val view: WeakReference<TextView>,
        val colors: ColorStateList,
    )

    private const val BUTTON_TITLE_ID = "focus_button_title"
    private const val BUTTON_DARK_MIN = 0x33
    private const val BUTTON_DARK_MAX = 0x66
    private const val MAX_COLOR_LUMINANCE = 255000

    private const val COLLAPSE_EVENT_CLASS =
        "miui.systemui.dynamicisland.event.DynamicIslandEvent\$Collapse"

}
