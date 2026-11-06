package io.github.hyperisland.xposed.hook.SystemUI

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.os.SystemClock
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.LinearInterpolator
import android.view.animation.Transformation
import io.github.hyperisland.utils.IslandBehaviorConfig
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedInterface.HookHandle
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.roundToInt
import kotlin.math.sin

/** Opt-in single island. The stock small slot remains a queue entry, never a visible island. */
object SingleIslandDispatchHook : BaseHook() {
    private const val ROOT = "miui.systemui.dynamicisland."
    private const val MAX_VIEWS = 128
    private val installed = Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    private val rings = WeakHashMap<View, QueueRing>()
    private val pendingEntries = WeakHashMap<Any, QueueEntry>()
    private class OriginalClip(val bounds: Rect?)
    private val hiddenFakeClips = WeakHashMap<View, OriginalClip>()
    @Volatile private var active = false
    @Volatile private var indicatorColor = IslandBehaviorConfig.FOLLOW_ISLAND

    private class QueueEntry(target: View) {
        val target = WeakReference(target)
        val createdAt = SystemClock.uptimeMillis()
    }

    override fun getTag() = "HyperIsland[SingleIslandDispatch]"

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        // A style change requires a scope restart: default installs no hooks or loader callbacks.
        if (IslandBehaviorConfig.style(ConfigManager.getString(
                IslandBehaviorConfig.STYLE_KEY, IslandBehaviorConfig.DEFAULT,
            )) != IslandBehaviorConfig.SINGLE) return
        if (active) return
        active = true
        loadIndicatorColor()
        bind(module, param.defaultClassLoader)
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) {
            bind(module, it)
        }
    }

    override fun onConfigChanged() {
        if (!active) return
        loadIndicatorColor()
        invalidateRings()
    }

    private fun loadIndicatorColor() {
        indicatorColor = IslandBehaviorConfig.color(ConfigManager.getString(
            IslandBehaviorConfig.COLOR_KEY, IslandBehaviorConfig.FOLLOW_ISLAND,
        ))
    }

    private fun invalidateRings() {
        val views = synchronized(rings) { rings.keys.map { WeakReference(it) } }
        views.forEach { it.get()?.postInvalidate() }
    }

    /** Resolve the complete modern animation contract before installing anything. No View is cached here. */
    private class Access(loader: ClassLoader) {
        val base = loader.loadClass(ROOT + "window.content.DynamicIslandBaseContentView")
        val real = loader.loadClass(ROOT + "window.content.DynamicIslandContentView")
        val fake = loader.loadClass(ROOT + "window.content.DynamicIslandContentFakeView")
        val coordinator = loader.loadClass(ROOT + "event.DynamicIslandEventCoordinator")
        val controller = loader.loadClass(ROOT + "window.DynamicIslandWindowViewController")
        val delegate = loader.loadClass(ROOT + "anim.DynamicIslandAnimationDelegate")
        val touch = loader.loadClass(ROOT + "touch.domain.interactor.DynamicIslandTouchInteractor")
        private val stateClass = loader.loadClass(ROOT + "event.DynamicIslandState")
        val small = loader.loadClass(stateClass.name + "\$SmallIsland")
        val hidden = loader.loadClass(stateClass.name + "\$Hidden")
        val empty = loader.loadClass(stateClass.name + "\$Empty")
        val big = loader.loadClass(stateClass.name + "\$BigIsland")
        val showOnce = loader.loadClass(stateClass.name + "\$ShowOnceBigIsland")
        val state = base.getMethod("getState")
        private val lastState = base.getMethod("getLastState")
        val setState = base.getMethod("setState", stateClass)
        val background = base.getMethod("getBackgroundView")
        val bigView = base.getMethod("getBigIslandView")
        val smallView = base.getMethod("getSmallIslandView")
        val template = base.getMethod("getTemplate")
        val bigRect = base.getMethod("getBigIslandRect", Boolean::class.javaObjectType)
        val fakeView = base.getMethod("getFakeView")
        val realView = base.getMethod("getRealView")
        val fakeSmall = base.getMethod("getFakeSmallIsland")
        val fakeBig = base.getMethod("getFakeBigIsland")
        val fakeExpanded = base.getMethod("getFakeExpandedView")
        val eventCoordinator = base.getMethod("getDynamicIslandEventCoordinator")
        val hasSmall = base.getMethod("hasSmallIsland")
        val currentWidth = base.getMethod("getCurrentBigIslandWidth", Boolean::class.javaObjectType)
        val currentX = base.getMethod("getCurrentBigIslandX", Boolean::class.javaObjectType)
        val smallRect = base.getMethod("getSmallIslandRect", Int::class.javaPrimitiveType)
        private val smallWidth = base.getMethod("getSmallIslandViewWidth")
        private val islandHeight = base.getMethod("getIslandViewHeight")
        val setAlpha = real.getDeclaredMethod("setAlpha", Float::class.javaPrimitiveType)
        val setBackground = base.getMethod("setBackgroundView", background.returnType)
        val attached = real.getDeclaredMethod("onAttachedToWindow")
        val darkLight = base.declaredMethods.single { it.name == "updateDarkLightMode" }
        val onlyBigRegion = coordinator.getDeclaredMethod("computeBigIslandOnlyRegion").accessible()
        val dualRegion = coordinator.declaredMethods.single { it.name == "computeBigWithSmallRegion" }
        val dispatch = coordinator.declaredMethods.single { it.name == "dispatchEvent" && it.parameterCount == 2 }
        private val hiddenList = coordinator.getDeclaredField("hiddenList").apply { isAccessible = true }
        private val bigHandler = coordinator.getMethod("getBigIslandStateHandler")
        private val smallHandler = coordinator.getMethod("getSmallIslandStateHandler")
        private val current = bigHandler.returnType.getMethod("getCurrent")
        private val smallCurrent = smallHandler.returnType.getMethod("getCurrent")
        private val tempCurrent = bigHandler.returnType.getMethod("getCurrentTempShow")
        private val isTempHidden = coordinator.getMethod("isTempHidden", real)
        private val coordinatorContext = coordinator.getDeclaredField("ctx").apply { isAccessible = true }
        private val backgroundClass = background.returnType
        val left = backgroundClass.getMethod("getActualLeft")
        val top = backgroundClass.getMethod("getActualTop")
        // These are right/bottom coordinates, despite the stock method names.
        val right = backgroundClass.getMethod("getActualWidth")
        val bottom = backgroundClass.getMethod("getActualHeight")
        val touchIntercept = touch.declaredMethods.single { it.name == "onInterceptTouchEvent" }
        val controllerSmallRect = controller.declaredMethods.single { it.name == "getSmallIslandRect" }
        val fakeCurrentSmallRect = fake.getMethod("getCurrentSmallRect", real)
        val fakeVisibility = fake.getDeclaredMethod("setVisibility", Int::class.javaPrimitiveType)
        val closeEnd = fake.declaredMethods.single { it.name == "updateViewStateWhenCloseEnd" }
        val fakeAnimatorClass = delegate.getDeclaredMethod("getFakeViewAnimator").returnType
        val fakeToBig = fakeAnimatorClass.getMethod("fakeViewToBigIsland", real, Boolean::class.javaPrimitiveType)
        val fakeToSmall = fakeAnimatorClass.getMethod("fakeViewToSmallIsland", real, Boolean::class.javaPrimitiveType)
        val fakeToHidden = fakeAnimatorClass.getMethod("fakeViewToHidden", real, Boolean::class.javaPrimitiveType)
        val fakeUpdate = fakeAnimatorClass.getMethod("updateFakeViewAnimState")
        private val animatorView = fakeAnimatorClass.getDeclaredField("view").apply { isAccessible = true }
        private val updaterClass = delegate.getDeclaredField("propertyUpdater").type
        val updateSmall = updaterClass.declaredMethods.single { it.name == "updateSmallIsland" }
        private val updaterView = updaterClass.getDeclaredField("view").apply { isAccessible = true }
        val appGeometry = fakeAnimatorClass.declaredMethods.single { it.name == "updateFakeViewStateForAppAnim" }
        val fakeGeometry = fakeAnimatorClass.declaredMethods.single { it.name == "updateFakeViewState" }
        private val glowUpper = runCatching {
            bigView.returnType.superclass.getDeclaredField("mGlowEffectUpperView").apply { isAccessible = true }
        }.getOrNull()
        private val glowBottom = runCatching {
            bigView.returnType.superclass.getDeclaredField("mGlowEffectBottomView").apply { isAccessible = true }
        }.getOrNull()
        val hitTesting = ThreadLocal<Boolean>()

        fun isSmall(view: Any) = small.isInstance(state.invoke(view))
        fun isQueued(view: Any) = state.invoke(view).let { small.isInstance(it) || hidden.isInstance(it) }
        fun hideRoot(view: Any): Boolean {
            val value = state.invoke(view)
            return small.isInstance(value) || (hidden.isInstance(value) && small.isInstance(lastState.invoke(view)))
        }
        fun isBig(view: Any) = state.invoke(view).let { big.isInstance(it) || showOnce.isInstance(it) }
        fun owner(fakeView: Any): Any? = realView.invoke(fakeView)
        fun phoneCoordinator(value: Any) =
            (coordinatorContext.get(value) as Context).resources.configuration.smallestScreenWidthDp < 600
        fun animatorOwner(animator: Any): Any? = animatorView.get(animator)
        fun updaterOwner(updater: Any): Any? = updaterView.get(updater)
        fun displayedBig(view: Any): View? {
            val c = eventCoordinator.invoke(view) ?: return null
            val handler = bigHandler.invoke(c)
            val target = tempCurrent.invoke(handler) ?: current.invoke(handler)
            return (target as? View)?.takeIf {
                it !== view && isBig(it) && isTempHidden.invoke(c, it) != true
            }
        }
        fun queueEntryRect(view: Any): Rect {
            val destination = displayedBig(view) ?: view
            val bounds = bigRect.invoke(destination, false) as Rect
            val height = minOf(
                (islandHeight.invoke(view) as Number).toInt(), bounds.height(),
            ).coerceAtLeast(1)
            // Shrink inside the receiving island; never show the queued item's big template.
            val size = (height * 0.6f).roundToInt().coerceAtLeast(1)
            val width = minOf((smallWidth.invoke(view) as Number).toInt().coerceAtLeast(1), size)
            val left = bounds.centerX() - width / 2
            val top = bounds.centerY() - size / 2
            return Rect(left, top, left + width, top + size)
        }
        fun bounceTargets(target: View): List<View> {
            val content = bigView.invoke(target)
            val bg = background.invoke(target) as? View ?: return emptyList()
            val external = listOfNotNull(
                content?.let { glowUpper?.get(it) as? View },
                content?.let { glowBottom?.get(it) as? View },
            ).filter { effect ->
                var parent = effect.parent
                var inside = false
                while (parent is View) {
                    if (parent === bg) { inside = true; break }
                    parent = parent.parent
                }
                !inside
            }
            return (listOf(bg) + external).distinct()
        }
        fun tempHidden(view: Any): Boolean {
            val c = eventCoordinator.invoke(view) ?: return true
            return isTempHidden.invoke(c, view) == true
        }
        fun isDisplayed(view: Any, c: Any): Boolean {
            val handler = bigHandler.invoke(c)
            return current.invoke(handler) === view || tempCurrent.invoke(handler) === view
        }

        fun availability(c: Any): Pair<Boolean, Boolean> {
            val next = smallCurrent.invoke(smallHandler.invoke(c))
            var right = next != null && isSmall(next) && isTempHidden.invoke(c, next) != true
            var left = false
            var passedEmpty = false
            (hiddenList.get(c) as? List<*>)?.forEach { entry ->
                if (entry != null) {
                    val value = state.invoke(entry)
                    if (empty.isInstance(value)) passedEmpty = true
                    else if (hidden.isInstance(value) && isTempHidden.invoke(c, entry) != true) {
                        if (passedEmpty) left = true else right = true
                    }
                }
            }
            return left to right
        }
    }

    private fun Method.accessible() = apply { isAccessible = true }
    private fun phone(view: Any?) = (view as? View)?.context?.resources?.configuration
        ?.smallestScreenWidthDp?.let { it < 600 } == true

    private fun bind(module: XposedModule, loader: ClassLoader) {
        if (!HookUtils.isIslandLoaderReady(loader)) return
        synchronized(installed) {
            val base = runCatching { loader.loadClass(ROOT + "window.content.DynamicIslandBaseContentView") }
                .getOrNull() ?: return
            if (base in installed || installed.size >= 32) return
            val handles = mutableListOf<HookHandle>()
            runCatching {
                val a = Access(loader)
                handles += module.hook(a.hasSmall).intercept { chain ->
                    if (phone(chain.thisObject)) false else chain.proceed()
                }
                listOf(a.currentWidth, a.currentX).forEach { method ->
                    handles += module.hook(method).intercept { chain ->
                        if (phone(chain.thisObject)) chain.proceed(arrayOf(false)) else chain.proceed()
                    }
                }
                // The stock setter also updates the background alpha. Never change fake root visibility.
                handles += module.hook(a.setAlpha).intercept { chain ->
                    val hide = runCatching { phone(chain.thisObject) && a.hideRoot(chain.thisObject!!) }
                        .getOrDefault(false)
                    val result = if (hide) chain.proceed(arrayOf(0f)) else chain.proceed()
                    guarded(module, "small-alpha") { chain.thisObject?.let { suppressRealSmall(it, a) } }
                    result
                }
                handles += module.hook(a.setState).intercept { chain ->
                    val result = chain.proceed()
                    guarded(module, "state") {
                        val view = chain.thisObject as? View ?: return@guarded
                        if (phone(view) && a.real.isInstance(view)) {
                            suppressRealSmall(view, a)
                            if (a.hideRoot(view)) view.alpha = 0f
                            suppressFakeSlot(a.fakeView.invoke(view), view, a)
                            if (!a.isQueued(view) && !a.isBig(view)) synchronized(pendingEntries) {
                                pendingEntries.remove(view)
                            }
                            attachRing(view, a)
                        }
                    }
                    result
                }
                handles += module.hook(a.updateSmall).intercept { chain ->
                    val result = chain.proceed()
                    guarded(module, "small-frame") {
                        a.updaterOwner(chain.thisObject!!)?.let { suppressRealSmall(it, a) }
                    }
                    result
                }
                handles += module.hook(a.darkLight).intercept { chain ->
                    val result = chain.proceed()
                    guarded(module, "ring-outline") {
                        chain.thisObject?.let { owner ->
                            suppressRealSmall(owner, a)
                            val destination = chain.args[0]
                            if (phone(owner) && a.real.isInstance(owner) &&
                                (a.big.isInstance(destination) || a.showOnce.isInstance(destination)) &&
                                a.template.invoke(owner) != null
                            ) {
                                attachRing(owner, a)
                                val bg = a.background.invoke(owner) as? View ?: return@let
                                // On phones, the false branch is the one that adds the island stroke.
                                // Preserve the native decision independently of any replacement material;
                                // highlightColor only controls its color, which the indicator deliberately ignores.
                                synchronized(rings) { rings[bg] }?.systemOutlineVisible = chain.args[2] == false
                                bg.invalidate()
                            }
                        }
                    }
                    result
                }
                listOf(a.setBackground, a.attached).forEach { method ->
                    handles += module.hook(method).intercept { chain ->
                        val result = chain.proceed()
                        guarded(module, "attach") { chain.thisObject?.let { attachRing(it, a) } }
                        result
                    }
                }
                handles += module.hook(a.dualRegion).intercept { chain ->
                    if (a.phoneCoordinator(chain.thisObject!!)) a.onlyBigRegion.invoke(chain.thisObject)
                    else chain.proceed()
                }
                // Small rectangles are still used by stock hit testing. Keep that invisible slot inert.
                handles += module.hook(a.touchIntercept).intercept { chain ->
                    val previous = a.hitTesting.get()
                    a.hitTesting.set(true)
                    try { chain.proceed() } finally {
                        if (previous == null) a.hitTesting.remove() else a.hitTesting.set(previous)
                    }
                }
                handles += module.hook(a.smallRect).intercept { chain ->
                    if (!phone(chain.thisObject)) chain.proceed()
                    else if (a.hitTesting.get() == true) Rect()
                    else if (a.tempHidden(chain.thisObject!!)) chain.proceed()
                    else a.queueEntryRect(chain.thisObject!!)
                }
                handles += module.hook(a.controllerSmallRect).intercept { chain ->
                    val view = chain.args[0]
                    // Called for the predicted Small/Hidden destination, while view may still be AppExpanded.
                    if (phone(view) && !a.tempHidden(view!!)) {
                        beginQueueEntry(view, a)
                        a.queueEntryRect(view)
                    }
                    else chain.proceed()
                }
                handles += module.hook(a.fakeCurrentSmallRect).intercept { chain ->
                    val view = chain.args[0]
                    if (phone(view) && !a.tempHidden(view!!)) a.queueEntryRect(view) else chain.proceed()
                }
                // A queued item has no visible island. Reuse native Hidden cleanup, never the big fake path.
                listOf(a.fakeToSmall, a.fakeToBig).forEach { method ->
                    handles += module.hook(method).intercept { chain ->
                        val view = chain.args[0]
                        val redirect = phone(view) && a.isQueued(view!!) && !a.tempHidden(view)
                        if (!redirect) return@intercept chain.proceed()
                        a.fakeToHidden.invoke(chain.thisObject, view, chain.args[1])
                        guarded(module, "fake-target") { suppressFakeSlot(a.fakeView.invoke(view), view, a) }
                        null
                    }
                }
                handles += module.hook(a.fakeToHidden).intercept { chain ->
                    val result = chain.proceed()
                    guarded(module, "hidden-target") {
                        val view = chain.args[0]
                        if (phone(view)) suppressFakeSlot(a.fakeView.invoke(view), view, a)
                    }
                    result
                }
                handles += module.hook(a.appGeometry).intercept { chain ->
                    val view = chain.args[4]
                    if (!phone(view) || !a.isQueued(view!!) || a.tempHidden(view)) {
                        return@intercept chain.proceed()
                    }
                    beginQueueEntry(view, a)
                    val target = a.queueEntryRect(view)
                    val args = chain.args.toTypedArray()
                    args[0] = target.left
                    args[1] = target.top
                    args[2] = target.width()
                    args[3] = target.height()
                    // Preserve native reset, surface scaling, outline setup and completion lifecycle.
                    chain.proceed(args)
                }
                handles += module.hook(a.fakeGeometry).intercept { chain ->
                    val view = chain.args[6]
                    if (!phone(view) || !a.isQueued(view!!) || a.tempHidden(view)) {
                        return@intercept chain.proceed()
                    }
                    val target = a.queueEntryRect(view)
                    val args = chain.args.toTypedArray()
                    args[0] = 0
                    args[1] = 0
                    args[2] = target.left
                    args[3] = target.width()
                    args[4] = target.top
                    args[5] = target.height()
                    chain.proceed(args)
                }
                handles += module.hook(a.fakeUpdate).intercept { chain ->
                    val result = chain.proceed()
                    guarded(module, "fake-frame") {
                        val view = a.animatorOwner(chain.thisObject!!)
                        if (phone(view)) suppressFakeSlot(a.fakeView.invoke(view), view, a)
                    }
                    result
                }
                handles += module.hook(a.fakeVisibility).intercept { chain ->
                    val wasVisible = (chain.thisObject as? View)?.visibility == View.VISIBLE
                    val result = chain.proceed() // Includes alreadyCloseAppEnd and lifetime cleanup.
                    guarded(module, "handoff") {
                        val fake = chain.thisObject ?: return@guarded
                        val owner = a.owner(fake) as? View ?: return@guarded
                        if (phone(owner)) {
                            suppressFakeSlot(fake, owner, a)
                            if (a.hideRoot(owner)) owner.alpha = 0f
                            attachRing(owner, a)
                            if (wasVisible && chain.args[0] == View.INVISIBLE) finishQueueEntry(owner, a)
                        }
                    }
                    result
                }
                handles += module.hook(a.closeEnd).intercept { chain ->
                    val result = chain.proceed()
                    guarded(module, "queue-arrived") {
                        a.owner(chain.thisObject!!)?.let { finishQueueEntry(it, a) }
                    }
                    result
                }
                handles += module.hook(a.dispatch).intercept { chain ->
                    val result = chain.proceed()
                    guarded(module, "queue") {
                        val c = chain.thisObject ?: return@guarded
                        val backgrounds = synchronized(rings) { rings.keys.map { WeakReference(it) } }
                        backgrounds.forEach { it.get()?.invalidate() }
                        if (a.phoneCoordinator(c)) {
                            val sides = a.availability(c)
                            log(module) { "queue event=${chain.args[0]?.javaClass?.simpleName} left=${sides.first} right=${sides.second}" }
                        }
                    }
                    result
                }
                installed.add(base)
                log(module) { "enabled: single visible big island, stock queue and window lifecycle preserved" }
            }.onFailure { error ->
                handles.asReversed().forEach { runCatching { it.unhook() } }
                logFailureOnce(module, "runtime-${System.identityHashCode(base)}") {
                    "single-island runtime unsupported; hooks rolled back: ${error.message}"
                }
            }
        }
    }

    private inline fun guarded(module: XposedModule, key: String, block: () -> Unit) {
        runCatching(block).onFailure { error ->
            logFailureOnce(module, key) { "$key failed: ${error.message}" }
        }
    }

    private fun suppressRealSmall(owner: Any, a: Access) {
        if (!phone(owner)) return
        (a.smallView.invoke(owner) as? View)?.alpha = 0f
    }

    private fun beginQueueEntry(owner: Any, a: Access) {
        val target = a.displayedBig(owner) ?: return
        val now = SystemClock.uptimeMillis()
        synchronized(pendingEntries) {
            pendingEntries.entries.removeAll { now - it.value.createdAt > 10_000L || it.value.target.get() == null }
            if (pendingEntries.size < MAX_VIEWS && !pendingEntries.containsKey(owner)) {
                pendingEntries[owner] = QueueEntry(target)
            }
        }
    }

    private fun finishQueueEntry(owner: Any, a: Access) {
        val entry = synchronized(pendingEntries) { pendingEntries.remove(owner) } ?: return
        if (!a.isQueued(owner) || SystemClock.uptimeMillis() - entry.createdAt > 10_000L) return
        val target = entry.target.get() ?: return
        if (a.displayedBig(owner) !== target || !target.isShown) return
        val targets = a.bounceTargets(target).filter { it.isShown && it.alpha > 0f }
        val bg = a.background.invoke(target) as? View ?: return
        // Do not replace an animation owned by the system or another feature.
        if (targets.any { it.animation?.let { animation -> !animation.hasEnded() && animation !is QueueBounce } == true }) return
        val start = AnimationUtils.currentAnimationTimeMillis()
        val distance = 3f * bg.resources.displayMetrics.density
        targets.forEach { view ->
            // A classic View animation is additive: native translation/scale and burn-in values stay intact.
            val bounce = QueueBounce(distance)
            view.startAnimation(bounce)
            bounce.startTime = start
        }
    }

    private class QueueBounce(private val distance: Float) : Animation() {
        init {
            duration = 320L
            interpolator = LinearInterpolator()
        }

        override fun applyTransformation(interpolatedTime: Float, transformation: Transformation) {
            val remaining = 1f - interpolatedTime
            val offset = -distance * sin(interpolatedTime * (Math.PI * 2).toFloat()) * remaining * remaining
            transformation.matrix.setTranslate(0f, offset)
        }
    }

    private val fakeDetachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = Unit
        override fun onViewDetachedFromWindow(v: View) = restoreFakeClip(v)
    }

    private fun restoreFakeClip(view: View) {
        val original = synchronized(hiddenFakeClips) { hiddenFakeClips.remove(view) } ?: return
        val current = view.clipBounds
        if (current != null && current.left == 0 && current.top == 0 && current.right == 0 && current.bottom == 0) {
            view.clipBounds = original.bounds
        }
        view.removeOnAttachStateChangeListener(fakeDetachListener)
    }

    private fun hideQueuedFake(view: View, hidden: Boolean) {
        if (!hidden) { restoreFakeClip(view); return }
        synchronized(hiddenFakeClips) {
            if (hiddenFakeClips.containsKey(view) || hiddenFakeClips.size >= MAX_VIEWS) return
            hiddenFakeClips[view] = OriginalClip(view.clipBounds?.let(::Rect))
            view.addOnAttachStateChangeListener(fakeDetachListener)
        }
        // Hide root material/mask too. Alpha alone can be rewritten during the swipe to Hidden.
        view.clipBounds = Rect(0, 0, 0, 0)
    }

    private fun suppressFakeSlot(fake: Any?, owner: Any?, a: Access) {
        if (fake == null || owner == null) return
        (a.fakeSmall.invoke(fake) as? View)?.alpha = 0f
        val queued = a.isQueued(owner)
        (fake as? View)?.let { hideQueuedFake(it, queued) }
        if (queued) {
            (a.fakeBig.invoke(fake) as? View)?.alpha = 0f
            (a.fakeExpanded.invoke(fake) as? View)?.alpha = 0f
        }
    }

    private val detachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = Unit
        override fun onViewDetachedFromWindow(v: View) {
            val ring = synchronized(rings) { rings.remove(v) } ?: return
            ring.restoreAccessibility()
            if (v.animation is QueueBounce) v.clearAnimation()
            if (v.foreground === ring.installedForeground.get()) v.foreground = ring.originalForeground
            v.removeOnAttachStateChangeListener(this)
        }
    }

    private fun attachRing(owner: Any, a: Access) {
        if (!phone(owner) || !a.real.isInstance(owner)) return
        suppressRealSmall(owner, a)
        val bg = a.background.invoke(owner) as? View ?: return
        val ring = synchronized(rings) {
            rings[bg] ?: run {
                if (rings.size >= MAX_VIEWS) return
                QueueRing(bg, owner, a).also {
                    rings[bg] = it
                    bg.addOnAttachStateChangeListener(detachListener)
                }
            }
        }
        ring.updateAccessibility()
        if (ring.installedForeground.get() == null || bg.foreground !== ring.installedForeground.get()) {
            ring.originalForeground = bg.foreground
            val foreground = bg.foreground?.let { LayerDrawable(arrayOf(it, ring)) } ?: ring
            bg.foreground = foreground
            ring.installedForeground = WeakReference(foreground)
        }
        bg.invalidate()
    }

    /** Foreground draws after stock onDraw clipping, in the native background's own animated bounds. */
    private class QueueRing(bg: View, owner: Any, private val a: Access) : Drawable() {
        private val background = WeakReference(bg)
        private val owner = WeakReference(owner)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF000000.toInt()
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        private val arc = RectF()
        var originalForeground: Drawable? = null
        var installedForeground = WeakReference<Drawable>(null)
        private var drawableAlpha = 255
        private var originalAccessibility: Int? = null
        @Volatile var systemOutlineVisible = false

        fun updateAccessibility() {
            val view = owner.get() as? View ?: return
            if (a.isSmall(view)) {
                if (originalAccessibility == null) originalAccessibility = view.importantForAccessibility
                view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            } else restoreAccessibility()
        }

        fun restoreAccessibility() {
            originalAccessibility?.let { saved ->
                (owner.get() as? View)?.importantForAccessibility = saved
            }
            originalAccessibility = null
        }

        override fun draw(canvas: Canvas) {
            // Reflection handles are pre-resolved. No class lookup, View allocation or scheduling per frame.
            runCatching {
                val bg = background.get() ?: return
                val view = owner.get() ?: return
                if (!phone(view) || !a.isBig(view)) return
                val c = a.eventCoordinator.invoke(view) ?: return
                if (!a.isDisplayed(view, c)) return
                val content = a.bigView.invoke(view) as? View ?: return
                if (content.visibility != View.VISIBLE || content.alpha <= 0f) return
                var (leftQueue, rightQueue) = a.availability(c)
                if (bg.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                    val previous = leftQueue
                    leftQueue = rightQueue
                    rightQueue = previous
                }
                if (!leftQueue && !rightQueue) return
                val left = (a.left.invoke(bg) as Number).toFloat()
                val top = (a.top.invoke(bg) as Number).toFloat()
                val right = (a.right.invoke(bg) as Number).toFloat()
                val bottom = (a.bottom.invoke(bg) as Number).toFloat()
                val height = bottom - top
                if (height <= 0 || right - left < height) return
                val sweep = 100f
                val radius = height * 0.55f
                val cy = (top + bottom) / 2f
                paint.strokeWidth = minOf(2f * bg.resources.displayMetrics.density, height * 0.07f)
                val gap = paint.strokeWidth * 2f
                // Move the arcs outward while retaining their slightly shorter height.
                // At each side's midpoint, the clear gap excludes the stroke's inner half.
                val outset = (gap + paint.strokeWidth / 2f - (radius - height / 2f)).coerceAtLeast(0f)
                val mode = indicatorColor
                val islandOutlined = IslandOutlineHook.resolveOutlineVisibility(false, systemOutlineVisible)
                val gray = 0xFF2F2F2F.toInt()
                // Reuse the background decision to color the entire arc; no separate border.
                paint.color = when (mode) {
                    IslandBehaviorConfig.WHITE -> Color.WHITE
                    IslandBehaviorConfig.BLACK -> Color.BLACK
                    IslandBehaviorConfig.INVERT_ISLAND -> if (islandOutlined) Color.BLACK else gray
                    else -> if (islandOutlined) gray else Color.BLACK
                }
                val alpha = drawableAlpha * content.alpha
                paint.alpha = alpha.roundToInt().coerceIn(0, 255)
                if (leftQueue) {
                    val cx = left + height / 2f - outset
                    arc.set(cx - radius, cy - radius, cx + radius, cy + radius)
                    canvas.drawArc(arc, 180f - sweep / 2f, sweep, false, paint)
                }
                if (rightQueue) {
                    val cx = right - height / 2f + outset
                    arc.set(cx - radius, cy - radius, cx + radius, cy + radius)
                    canvas.drawArc(arc, -sweep / 2f, sweep, false, paint)
                }
            }
        }

        override fun setAlpha(alpha: Int) { drawableAlpha = alpha; invalidateSelf() }
        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
            invalidateSelf()
        }
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
