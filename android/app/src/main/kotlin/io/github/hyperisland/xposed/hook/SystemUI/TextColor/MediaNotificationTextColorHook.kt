package io.github.hyperisland.xposed.hook

import android.app.Notification
import android.app.PendingIntent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.log
import io.github.hyperisland.xposed.logError
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Overrides expanded media-island notification title and artist colors. */
object MediaNotificationTextColorHook : BaseHook() {

    private const val TAG = "HyperIsland[MediaNotificationTextColor]"
    private const val KEY_TEXT_COLOR_MODE = "pref_media_notification_text_color_mode"

    private val hookedClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    )
    private val titleColors = Collections.synchronizedMap(
        WeakHashMap<TextView, ColorStateList>()
    )
    private val subtitleColors = Collections.synchronizedMap(
        WeakHashMap<TextView, ColorStateList>()
    )
    private val attachRefreshViews = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<TextView, Boolean>())
    )
    private val pendingPostViews = Collections.synchronizedMap(
        WeakHashMap<TextView, Runnable>()
    )
    private val expandedViewData = Collections.synchronizedMap(
        // Island data holds getView()/getFakeView(); a strong value would keep its weak key alive.
        WeakHashMap<View, WeakReference<Any>>()
    )
    private val internalTitleViewIds: List<Int> by lazy {
        resolveInternalViewIds("title")
    }
    private val internalSubtitleViewIds: List<Int> by lazy {
        resolveInternalViewIds("text", "text2")
    }
    private val expandedProbeCount = AtomicInteger()

    @Volatile private var islandAnimationRunning = false
    @Volatile private var pendingTintRefresh = false
    @Volatile private var tintRefreshScheduled = false

    private val statusBarTintListener: (Int) -> Unit = {
        val mode = getConfiguredMode()
        if (mode == TextColorPolicy.MODE_FOLLOW_STATUS_BAR ||
            mode == TextColorPolicy.MODE_INVERT_STATUS_BAR
        ) {
            if (islandAnimationRunning || tintRefreshScheduled) {
                pendingTintRefresh = true
            } else {
                refreshTrackedColors()
            }
        }
    }

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        StatusBarTextColorHook.addReadableTintListener(statusBarTintListener)
        log { "$TAG [MediaTitleDiag] init, defaultClassLoader=${param.defaultClassLoader}" }
        hookClasses(module, param.defaultClassLoader)
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { classLoader ->
            hookClasses(module, classLoader)
        }
    }

    override fun onConfigChanged() {
        reapplyTrackedColors()
    }

    private fun hookClasses(module: XposedModule, classLoader: ClassLoader) {
        runCatching {
            val contentViewClass = classLoader.loadClass(DYNAMIC_ISLAND_CONTENT_VIEW_CLASS)
            if (hookedClasses.add(contentViewClass)) {
                hookExpandedMediaData(module, contentViewClass)
            }
        }.onFailure { error ->
            if (error !is ClassNotFoundException) {
                logError(module, "failed to hook DynamicIslandContentView: ${error.message}")
            }
        }

        runCatching {
            val expandedViewClass = classLoader.loadClass(
                "miui.systemui.dynamicisland.view.DynamicIslandExpandedView"
            )
            if (hookedClasses.add(expandedViewClass)) {
                hookExpandedContentInstall(module, expandedViewClass)
            }
        }.onFailure { error ->
            if (error !is ClassNotFoundException) {
                logError(module, "failed to hook DynamicIslandExpandedView: ${error.message}")
            }
        }

        runCatching {
            val windowViewClass = classLoader.loadClass(DYNAMIC_ISLAND_WINDOW_VIEW_CLASS)
            if (hookedClasses.add(windowViewClass)) {
                hookExpandedMediaUpdate(module, windowViewClass)
            }
        }.onFailure { error ->
            if (error !is ClassNotFoundException) {
                logError(module, "failed to hook DynamicIslandWindowView: ${error.message}")
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

    }

    private fun hookAnimationLifecycle(module: XposedModule, coordinatorClass: Class<*>) {
        coordinatorClass.declaredMethods
            .filter { method -> method.name == "onAnimationStart" }
            .forEach { method ->
                module.hook(method).intercept { chain ->
                    islandAnimationRunning = true
                    chain.proceed()
                }
                log(module) { "hooked media DynamicIslandEventCoordinator#onAnimationStart" }
            }
        coordinatorClass.declaredMethods
            .filter { method ->
                method.name == "onAnimationFinished" || method.name == "onAnimationCancel"
            }
            .forEach { method ->
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    islandAnimationRunning = false
                    // Flush after collapse too; no further tint event is guaranteed.
                    pendingTintRefresh = true
                    schedulePendingTintRefresh()
                    result
                }
                log(module) { "hooked media DynamicIslandEventCoordinator#${method.name}" }
            }
    }

    private fun schedulePendingTintRefresh() {
        if (!pendingTintRefresh || tintRefreshScheduled) return
        tintRefreshScheduled = true
        Choreographer.getInstance().postFrameCallback {
            tintRefreshScheduled = false
            if (islandAnimationRunning || !pendingTintRefresh) return@postFrameCallback
            pendingTintRefresh = false
            refreshTrackedColors()
        }
    }

    private fun hookExpandedMediaData(module: XposedModule, contentViewClass: Class<*>) {
        val methods = contentViewClass.declaredMethods.filter { method ->
            method.name == "updateExpandedView" && method.parameterTypes.size == 3
        }
        log { "$TAG [MediaTitleDiag] expanded install hook methods=${methods.size}" }
        methods.forEach { method ->
            module.hook(method).intercept { chain ->
                val data = chain.args.firstOrNull()
                val root = data?.let { invokeNoArg(it, "getView") as? View }
                val reference = data?.let { WeakReference(it) }
                if (root != null && reference != null) {
                    synchronized(expandedViewData) {
                        if (expandedViewData.size < MAX_TRACKED_VIEWS) {
                            expandedViewData[root] = reference
                        }
                    }
                }
                try {
                    chain.proceed()
                } finally {
                    // Reused content and failed updates may never call setContentView.
                    if (root != null && reference != null) {
                        synchronized(expandedViewData) {
                            if (expandedViewData[root] === reference) expandedViewData.remove(root)
                        }
                    }
                }
            }
            log(module) { "hooked DynamicIslandContentView#updateExpandedView" }
        }
    }

    private fun hookExpandedContentInstall(module: XposedModule, expandedViewClass: Class<*>) {
        val methods = expandedViewClass.declaredMethods.filter { method ->
            method.name.startsWith("setContentView") &&
                method.parameterTypes.contentEquals(arrayOf(View::class.java))
        }
        log { "$TAG [MediaTitleDiag] physical install hook methods=${methods.size}" }
        methods.forEach { method ->
            module.hook(method).intercept { chain ->
                val root = chain.args.firstOrNull() as? View
                val result = chain.proceed()
                if (root != null) {
                    val data = expandedViewData.remove(root)?.get()
                        ?: findCurrentIslandData(chain.thisObject as? View)
                    if (data != null) {
                        handleExpandedIslandData(data)
                    } else if (expandedProbeCount.getAndIncrement() < MAX_EXPANDED_PROBES) {
                        log { "$TAG [MediaTitleDiag] physical install without data " +
                                "root=${root.javaClass.name}, " +
                                "textViewIds=${collectTextViewResourceNames(root).distinct()}" }
                    }
                }
                result
            }
            log(module) { "hooked DynamicIslandExpandedView#${method.name}" }
        }
    }

    /**
     * HyperOS 4 reuses pendingMediaView when the media notification changes, so setContentView is
     * not called again. This callback runs after both the real and fake content views finish their
     * update and therefore avoids a draw/layout listener while still applying the color late enough.
     */
    private fun hookExpandedMediaUpdate(module: XposedModule, windowViewClass: Class<*>) {
        val methods = windowViewClass.declaredMethods.filter { method ->
            method.name == "handleContentViewUpdateOperate" && method.parameterTypes.size == 2
        }
        methods.forEach { method ->
            module.hook(method).intercept { chain ->
                val contentView = chain.args.firstOrNull() as? View
                val params = chain.args.getOrNull(1)
                val data = params?.let { invokeNoArg(it, "getIslandData") }
                val result = chain.proceed()
                if (data != null) {
                    val fakeView = contentView?.let { invokeNoArg(it, "getFakeView") as? View }
                    val expandedView = contentView
                        ?.let { invokeNoArg(it, "getExpandedView") as? View }
                        ?: contentView
                    val fakeExpandedView = fakeView
                        ?.let { invokeNoArg(it, "getFakeExpandedView") as? View }
                        ?: fakeView
                    handleExpandedIslandData(
                        data,
                        listOfNotNull(expandedView, fakeExpandedView),
                    )
                }
                result
            }
            log(module) { "hooked DynamicIslandWindowView#${method.name}" }
        }
    }

    private fun findCurrentIslandData(start: View?): Any? {
        var current: Any? = start
        while (current is View) {
            if (current.javaClass.name == DYNAMIC_ISLAND_CONTENT_VIEW_CLASS) {
                return invokeNoArg(current, "getCurrentIslandData")
            }
            current = current.parent
        }
        return null
    }

    private fun handleExpandedIslandData(data: Any, installedRoots: List<View> = emptyList()) {
        runCatching {
            val extras = invokeNoArg(data, "getExtras") as? Bundle
            val realRoot = invokeNoArg(data, "getView") as? View
            val fakeRoot = invokeNoArg(data, "getFakeView") as? View
            val hasMediaPendingIntent = extras
                ?.getParcelable<PendingIntent>("miui.pending.intent") != null
            val sbn = extras?.getParcelable<StatusBarNotification>("miui.sbn")
            val sbnMedia = sbn?.notification?.let(::isMediaNotification) == true
            if (!hasMediaPendingIntent && !sbnMedia) return

            val notification = sbn?.notification
            val roots = (listOfNotNull(realRoot, fakeRoot) + installedRoots).distinct()
            if (roots.isEmpty()) return
            val shouldLogProbe = expandedProbeCount.getAndIncrement() < MAX_EXPANDED_PROBES
            if (shouldLogProbe) {
                val titleProbe = roots.firstNotNullOfOrNull { root ->
                    findExpandedMediaTitle(root, notification)
                }
                log { "$TAG [MediaTitleDiag] expanded install probe " +
                        "data=${data.javaClass.name}, " +
                        "pkg=${sbn?.packageName ?: extras?.getString("miui.pkg.name")}, " +
                        "pendingIntent=$hasMediaPendingIntent, sbnMedia=$sbnMedia, " +
                        "root=${realRoot?.javaClass?.name}, titleId=${titleProbe?.let(::resourceEntryName)}, " +
                        "textViewIds=${roots.flatMap(::collectTextViewResourceNames).distinct()}" }
            }

            var matched = 0
            var subtitleMatched = 0
            val handledTitles = mutableSetOf<TextView>()
            val handledSubtitles = mutableSetOf<TextView>()
            roots.forEach { root ->
                findExpandedMediaTitle(root, notification)?.let { title ->
                    if (handledTitles.add(title)) {
                        matched++
                        applyExpandedMediaTextColor(title, false)
                    }
                }
                findExpandedMediaSubtitle(root, notification)?.let { subtitle ->
                    if (handledSubtitles.add(subtitle)) {
                        subtitleMatched++
                        applyExpandedMediaTextColor(subtitle, true)
                    }
                }
            }
            if (shouldLogProbe) {
                log { "$TAG [MediaTitleDiag] expanded media color " +
                        "pkg=${sbn?.packageName ?: extras?.getString("miui.pkg.name")}, " +
                        "pendingIntent=$hasMediaPendingIntent, sbnMedia=$sbnMedia, " +
                        "realRoot=${realRoot?.javaClass?.name}, roots=${roots.size}, " +
                        "titles=$matched, subtitles=$subtitleMatched" }
            }
            if (matched == 0 && shouldLogProbe) {
                val ids = roots.flatMap(::collectTextViewResourceNames).distinct()
                log { "$TAG [MediaTitleDiag] expanded title not found, textViewIds=$ids" }
            }
        }.onFailure { error ->
            logError(
                "$TAG [MediaTitleDiag] expanded data error=${error.javaClass.name}: " +
                    error.message
            )
        }
    }

private fun applyExpandedMediaTextColor(textView: TextView, subtitle: Boolean) {
        val originals = if (subtitle) subtitleColors else titleColors
        var source = 0
        synchronized(originals) {
            val existing = originals[textView]
            if (existing == null) {
                if (originals.size >= MAX_TRACKED_VIEWS) return
                val current = textView.textColors
                originals[textView] = current
                source = current.defaultColor
                ensureAttachRefresh(textView, subtitle)
            } else {
                source = existing.defaultColor
            }
        }
        val target = mapFromOriginal(source) ?: return
        TextColorPolicy.apply(textView, target)
        synchronized(pendingPostViews) {
            if (pendingPostViews.containsKey(textView)) return
            val targetReference = WeakReference(textView)
            val refresh = Runnable {
                val view = targetReference.get() ?: return@Runnable
                pendingPostViews.remove(view)
                mapFromOriginal(source)?.let { TextColorPolicy.apply(view, it) }
            }
            pendingPostViews[textView] = refresh
            if (!textView.post(refresh)) pendingPostViews.remove(textView)
        }
    }

private fun ensureAttachRefresh(textView: TextView, subtitle: Boolean) {
        if (!attachRefreshViews.add(textView)) return
        val originals = if (subtitle) subtitleColors else titleColors
        textView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                val target = view as? TextView ?: return
                val source = synchronized(originals) { originals[target]?.defaultColor } ?: return
                mapFromOriginal(source)?.let { TextColorPolicy.apply(target, it) }
            }

            override fun onViewDetachedFromWindow(view: View) {
                val target = view as? TextView ?: return
                pendingPostViews.remove(target)?.let { target.removeCallbacks(it) }
                // Keep the bounded weak color snapshot for SystemUI's detach/reattach reuse.
            }
        })
    }

    private fun refreshTrackedColors() {
        if (!TextColorPolicy.isConfigured(getConfiguredMode())) return
        refreshTracked(titleColors)
        refreshTracked(subtitleColors)
    }

    private fun refreshTracked(originals: MutableMap<TextView, ColorStateList>) {
        trackedViews(originals).filter { it.isAttachedToWindow }.forEach { textView ->
            val source = synchronized(originals) { originals[textView]?.defaultColor } ?: return@forEach
            mapFromOriginal(source)?.let { TextColorPolicy.apply(textView, it) }
        }
    }

    private fun reapplyTrackedColors() {
        val mode = getConfiguredMode()
        reapplyTrackedColors(titleColors, mode)
        reapplyTrackedColors(subtitleColors, mode)
    }

    private fun reapplyTrackedColors(
        originals: MutableMap<TextView, ColorStateList>,
        mode: String,
    ) {
        trackedViews(originals).forEach { textView ->
            val colors = synchronized(originals) { originals[textView] }
            if (mode == TextColorPolicy.MODE_DEFAULT || colors == null) {
                colors?.let {
                    TextColorPolicy.apply(textView, it.defaultColor)
                    textView.setTextColor(it)
                }
            } else {
                mapFromOriginal(colors.defaultColor)?.let { TextColorPolicy.apply(textView, it) }
            }
        }
    }

    private fun mapFromOriginal(color: Int): Int? = TextColorPolicy.map(
        color = color,
        mode = getConfiguredMode(),
        regionDark = false,
        statusBarTintBlack = statusBarTintBlack(),
    )

    private fun statusBarTintBlack(): Boolean =
        StatusBarTextColorHook.getReadableTint() == Color.BLACK

    private fun trackedViews(colors: MutableMap<TextView, ColorStateList>): List<TextView> {
        return synchronized(colors) { colors.keys.toList() }
    }

    private fun findExpandedMediaTitle(root: View, notification: Notification?): TextView? {
        internalTitleViewIds.firstNotNullOfOrNull { id ->
            root.findViewById(id) as? TextView
        }?.let { return it }
        MEDIA_TITLE_IDS.firstNotNullOfOrNull { id ->
            findTextViewByResourceName(root, id)
        }?.let { return it }
        return findTextViewByExpectedText(root, mediaTextCandidates(notification, true))
    }

    private fun findExpandedMediaSubtitle(root: View, notification: Notification?): TextView? {
        internalSubtitleViewIds.firstNotNullOfOrNull { id ->
            root.findViewById(id) as? TextView
        }?.let { return it }
        MEDIA_SUBTITLE_IDS.firstNotNullOfOrNull { id ->
            findTextViewByResourceName(root, id)
        }?.let { return it }
        return findTextViewByExpectedText(root, mediaTextCandidates(notification, false))
    }

    private fun resolveInternalViewIds(vararg fieldNames: String): List<Int> {
        return runCatching { Class.forName("com.android.internal.R\$id") }
            .getOrNull()
            ?.let { resourceClass ->
                fieldNames.mapNotNull { fieldName ->
                    runCatching { resourceClass.getField(fieldName).getInt(null) }
                        .getOrNull()
                        ?.takeUnless { it == View.NO_ID }
                }
            }
            .orEmpty()
    }

    private fun mediaTextCandidates(notification: Notification?, title: Boolean): Set<String> {
        val extras = notification?.extras ?: return emptySet()
        val keys = if (title) {
            arrayOf(Notification.EXTRA_TITLE_BIG, Notification.EXTRA_TITLE)
        } else {
            arrayOf(Notification.EXTRA_TEXT, Notification.EXTRA_SUB_TEXT, Notification.EXTRA_INFO_TEXT)
        }
        return keys.mapNotNull { key -> normalizeText(extras.getCharSequence(key)) }.toSet()
    }

    private fun findTextViewByExpectedText(view: View, expected: Set<String>): TextView? {
        if (expected.isEmpty()) return null
        if (view is TextView && normalizeText(view.text) in expected) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findTextViewByExpectedText(view.getChildAt(index), expected)?.let { return it }
            }
        }
        return null
    }

    private fun normalizeText(text: CharSequence?): String? {
        return text?.toString()?.trim()?.takeIf(String::isNotEmpty)
    }

    private fun collectTextViewResourceNames(root: View): List<String> {
        val names = mutableListOf<String>()
        collectTextViewResourceNames(root, names)
        return names
    }

    private fun collectTextViewResourceNames(view: View, names: MutableList<String>) {
        if (view is TextView) names.add(resourceEntryName(view) ?: "<no-id>")
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                collectTextViewResourceNames(view.getChildAt(index), names)
            }
        }
    }

    private fun findTextViewByResourceName(view: View, target: String): TextView? {
        if (view is TextView && resourceEntryName(view) == target) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findTextViewByResourceName(view.getChildAt(index), target)?.let { return it }
            }
        }
        return null
    }

    private fun resourceEntryName(view: View): String? {
        if (view.id == View.NO_ID) return null
        return runCatching { view.resources.getResourceEntryName(view.id) }.getOrNull()
    }

    private fun invokeNoArg(target: Any, methodName: String): Any? {
        return target.javaClass.methods.firstOrNull { method ->
            method.name == methodName && method.parameterTypes.isEmpty()
        }?.invoke(target)
    }

    private fun isMediaNotification(notification: Notification): Boolean {
        val extras = notification.extras
        if (extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return true
        if (extras.getString(Notification.EXTRA_TEMPLATE)
                ?.contains("MediaStyle", ignoreCase = true) == true
        ) {
            return true
        }
        return runCatching {
            val mediaMethod = notification.javaClass.methods.firstOrNull { method ->
                method.name == "isMediaNotification" && method.parameterTypes.isEmpty()
            } ?: return@runCatching false
            mediaMethod.invoke(notification) == true
        }.getOrDefault(false)
    }

    private fun getConfiguredMode(): String =
        when (val mode = ConfigManager.getString(KEY_TEXT_COLOR_MODE, TextColorPolicy.MODE_DEFAULT)) {
            TextColorPolicy.MODE_BLACK,
            TextColorPolicy.MODE_FOLLOW_STATUS_BAR,
            TextColorPolicy.MODE_INVERT_STATUS_BAR -> mode
            else -> TextColorPolicy.MODE_DEFAULT
        }

    private val MEDIA_TITLE_IDS = listOf("header_title", "title", "audio_title", "media_title")
    private val MEDIA_SUBTITLE_IDS = listOf(
        "header_artist",
        "artist",
        "media_artist",
        "subtitle",
        "sub_title",
        "text",
        "text2",
    )
    private const val DYNAMIC_ISLAND_CONTENT_VIEW_CLASS =
        "miui.systemui.dynamicisland.window.content.DynamicIslandContentView"
    private const val DYNAMIC_ISLAND_WINDOW_VIEW_CLASS =
        "miui.systemui.dynamicisland.window.DynamicIslandWindowView"
    private const val MAX_EXPANDED_PROBES = 8
    private const val MAX_TRACKED_VIEWS = 128
}
