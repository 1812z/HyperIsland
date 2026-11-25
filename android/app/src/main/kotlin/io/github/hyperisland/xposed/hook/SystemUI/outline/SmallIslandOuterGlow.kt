package io.github.hyperisland.xposed.hook.SystemUI.outline

import android.content.Context
import android.content.res.Resources
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroupOverlay
import android.view.ViewTreeObserver
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.diagnostics.profiledHook
import io.github.hyperisland.xposed.hook.SystemUI.corner.IslandCornerHook
import io.github.libxposed.api.XposedInterface.HookHandle
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.WeakHashMap
import kotlin.math.ceil

internal enum class SmallIslandGlowSlot { REAL, FAKE }

internal fun selectSmallIslandGlowSlot(realAlpha: Float, fakeAlpha: Float): SmallIslandGlowSlot? = when {
    fakeAlpha > 0f && fakeAlpha >= realAlpha -> SmallIslandGlowSlot.FAKE
    realAlpha > 0f -> SmallIslandGlowSlot.REAL
    else -> null
}

internal data class SmallIslandGlowStyle(
    val color: Int?,
    val range: Int,
    val singleColor: Boolean,
    val baseColor: Int?,
)

internal class SmallIslandGlowBackgroundView(context: Context) : View(context) {
    var hasApertureMask = false
        private set

    init {
        setBackgroundColor(0)
    }

    fun maskAperture(source: String): String {
        val alpha = "fragColor *= uAlpha;"
        if ("half sdf = box;" !in source || alpha !in source) return source

        // Convert the expanded outline distance back to the island edge.
        hasApertureMask = true
        return rectangularGlowCutout.replaceFirst(source, "").replaceFirst(
            alpha,
            "fragColor *= uAlpha * smoothstep(-0.5, 0.5, sdf * uResolution.y + uGlareWidth);",
        )
    }
}

private val rectangularGlowCutout = Regex(
    """(?m)^[ \t]*(?:half|float) xInRange\s*=\s*step\(0\.25,\s*vUv\.x\)\s*\*\s*step\(vUv\.x,\s*0\.75\);\s*""" +
        """(?:half|float) yInRange\s*=\s*step\(0\.25,\s*vUv\.y\)\s*\*\s*step\(vUv\.y,\s*0\.75\);\s*""" +
        """if\s*\(\s*xInRange\s*\*\s*yInRange\s*!=\s*0\.0?\s*\)\s*\{\s*return\s+""" +
        """(?:half4|vec4)\(0\.0?,\s*0\.0?,\s*0\.0?,\s*0\.0?\);\s*\}\r?\n""",
)

/** An independent instance of the stock two-layer glow, driven by the visible small slot. */
internal object SmallIslandOuterGlow {
    private const val TAG = "SmallIslandOuterGlow"
    private const val ROOT = "miui.systemui.dynamicisland."
    private const val FAKE_START_GRACE_MS = 400L
    private const val MAX_OWNERS = 128
    private val installed = WeakHashMap<Class<*>, Unit>()
    private val activeBindings = WeakHashMap<View, Binding>()
    private val attachedOwners = WeakHashMap<View, Runtime>()
    @Volatile var enabled = false
        private set
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val ownerLifecycle = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) {}

        override fun onViewDetachedFromWindow(view: View) {
            view.removeOnAttachStateChangeListener(this)
            attachedOwners.remove(view)
            activeBindings[view]?.dispose()
        }
    }
    private val refreshActiveBindings = Runnable {
        enabled = ConfigManager.getBoolean("pref_small_island_glow", false)
        if (enabled) {
            attachedOwners.forEach { (owner, runtime) -> runtime.refresh(owner) }
        } else {
            activeBindings.values.toList().forEach(Binding::dispose)
        }
    }

    fun onConfigChanged() {
        mainHandler.removeCallbacks(refreshActiveBindings)
        mainHandler.post(refreshActiveBindings)
    }

    fun install(
        module: XposedModule,
        loader: ClassLoader,
        resolveStyle: (View) -> SmallIslandGlowStyle?,
        configure: (View, SmallIslandGlowStyle, Float) -> Float?,
        reportFailure: (Throwable) -> Unit,
    ) {
        val content = loader.loadClass(ROOT + "window.content.DynamicIslandContentView")
        synchronized(installed) {
            if (content in installed || installed.size >= 32) return
            val access = Access(loader, content)
            val runtime = Runtime(access, resolveStyle, configure, reportFailure)
            val handles = mutableListOf<HookHandle>()
            try {
                handles += module.profiledHook(TAG, access.stateChanged).intercept { chain ->
                    val result = chain.proceed()
                    if (enabled) (chain.args[0] as? View)?.let(runtime::refresh)
                    result
                }
                handles += module.profiledHook(TAG, access.setData).intercept { chain ->
                    val result = chain.proceed()
                    if (enabled) (chain.thisObject as? View)?.takeIf(content::isInstance)?.let(runtime::refresh)
                    result
                }
                handles += module.profiledHook(TAG, access.attach).intercept { chain ->
                    val result = chain.proceed()
                    (chain.thisObject as? View)?.let(runtime::onAttach)
                    result
                }
                access.fakeToSmall?.let { method ->
                    handles += module.profiledHook(TAG, method).intercept { chain ->
                        val result = chain.proceed()
                        if (enabled) (chain.args[0] as? View)?.let { runtime.refresh(it, expectFake = true) }
                        result
                    }
                }
                installed[content] = Unit
            } catch (error: Throwable) {
                handles.forEach { runCatching { it.unhook() } }
                throw error
            }
        }
    }

    private class Access(loader: ClassLoader, content: Class<*>) {
        private val glow = loader.loadClass("com.mi.widget.view.LightBgView")
        private val controller = loader.loadClass(ROOT + "anim.DynamicIslandAnimationController")
        val stateChanged: Method = controller.getDeclaredMethod("onStateChange", content)
        val setData: Method = content.getMethod("setCurrentIslandData", content.getMethod("getCurrentIslandData").returnType)
        val attach: Method = content.getDeclaredMethod("onAttachedToWindow")
        val fakeToSmall: Method? = runCatching {
            val delegate = content.getMethod("getAnimatorDelegate").returnType
            val animator = delegate.getDeclaredMethod("getFakeViewAnimator").returnType
            animator.getMethod("fakeViewToSmallIsland", content, Boolean::class.javaPrimitiveType)
        }.getOrNull()
        val state: Method = content.getMethod("getState")
        val lastState: Method = content.getMethod("getLastState")
        val isAnimating: Method = content.getMethod("isAnimating")
        val smallState: Class<*> = loader.loadClass(state.returnType.name + "\$SmallIsland")
        val small: Method = content.getMethod("getSmallIslandView")
        val fake: Method = content.getMethod("getFakeView")
        val fakeSmall: Method = fake.returnType.getMethod("getFakeSmallIsland")
        val background: Method = content.getMethod("getBackgroundView")
        val left: Method = background.returnType.getMethod("getActualLeft")
        val top: Method = background.returnType.getMethod("getActualTop")
        val right: Method = background.returnType.getMethod("getActualWidth")
        val bottom: Method = background.returnType.getMethod("getActualHeight")
        val createGlow = glow.getConstructor(Context::class.java)
        val bindEffect: Method = glow.getMethod("setEffectView", View::class.java, View::class.java)
        val autoAperture: Method = glow.getMethod("setAutoApertureSize", Boolean::class.javaPrimitiveType)
        val distance: Method = glow.getMethod("setSizeOfGlowArea", Float::class.javaPrimitiveType)
        val radius: Method = glow.getMethod("setSizeOfGlowRadius", Float::class.javaPrimitiveType)
        val sizeTop: Method = glow.getMethod("setUpperApertureSize", SizeF::class.java)
        val sizeBottom: Method = glow.getMethod("setBottomApertureSize", SizeF::class.java)
        val start: Method = glow.getMethod("start", Boolean::class.javaPrimitiveType)
        val stop: Method = glow.getMethod("stop", Boolean::class.javaPrimitiveType)
    }

    private class Runtime(
        private val access: Access,
        private val resolveStyle: (View) -> SmallIslandGlowStyle?,
        private val configure: (View, SmallIslandGlowStyle, Float) -> Float?,
        private val reportFailure: (Throwable) -> Unit,
    ) {
        fun onAttach(owner: View) {
            if (Looper.myLooper() !== Looper.getMainLooper() || !owner.isAttachedToWindow) return
            if (enabled) refresh(owner) else trackAttachedOwner(owner)
        }

        fun refresh(owner: View, expectFake: Boolean = false) {
            if (Looper.myLooper() !== Looper.getMainLooper() || !owner.isAttachedToWindow) return
            runCatching {
                if (!trackAttachedOwner(owner)) return
                val existing = activeBindings[owner]
                val small = access.smallState.isInstance(access.state.invoke(owner))
                val wasSmall = access.smallState.isInstance(access.lastState.invoke(owner))
                if (!small && !expectFake && existing == null &&
                    (!wasSmall || access.isAnimating.invoke(owner) != true)
                ) return
                val style = resolveStyle(owner)
                if (style == null) {
                    existing?.dispose()
                    return
                }
                val binding = existing ?: Binding(owner, access, configure, reportFailure)
                    .also { activeBindings[owner] = it }
                binding.refresh(owner, small, wasSmall, expectFake, style)
            }.onFailure {
                activeBindings[owner]?.dispose()
                reportFailure(it)
            }
        }

        private fun trackAttachedOwner(owner: View): Boolean {
            if (owner in attachedOwners) return true
            if (attachedOwners.size >= MAX_OWNERS) return false
            attachedOwners[owner] = this
            owner.addOnAttachStateChangeListener(ownerLifecycle)
            return true
        }
    }

    private class Binding(
        owner: View,
        private val access: Access,
        private val configure: (View, SmallIslandGlowStyle, Float) -> Float?,
        private val reportFailure: (Throwable) -> Unit,
    ) : ViewTreeObserver.OnPreDrawListener, View.OnAttachStateChangeListener {
        private val owner = WeakReference(owner)
        private var small = WeakReference<View>(null)
        private var background = WeakReference<View>(null)
        private var fake = WeakReference<View>(null)
        private var fakeSmall = WeakReference<View>(null)
        private var observers = emptyList<WeakReference<ViewTreeObserver>>()
        private var inSmallState = false
        private var wasSmall = false
        private var pendingFakeUntil = 0L
        private val expireFakeStart = Runnable {
            pendingFakeUntil = 0L
            this.owner.get()?.invalidate()
            fake.get()?.invalidate()
        }
        private lateinit var style: SmallIslandGlowStyle
        private var radius = Float.MAX_VALUE
        private var stockRadius = Float.MAX_VALUE
        private var stockRange = 0f
        private var density = 0f
        private var renderer: Renderer? = null
        private var renderedSlot: SmallIslandGlowSlot? = null
        private val area = RectF()
        private val clip = Rect()
        private val matrix = Matrix()

        fun refresh(owner: View, inSmallState: Boolean, wasSmall: Boolean, expectFake: Boolean, style: SmallIslandGlowStyle) {
            this.inSmallState = inSmallState
            this.wasSmall = wasSmall
            if (expectFake && !inSmallState) {
                pendingFakeUntil = SystemClock.uptimeMillis() + FAKE_START_GRACE_MS
                mainHandler.removeCallbacks(expireFakeStart)
                mainHandler.postAtTime(expireFakeStart, pendingFakeUntil)
            }
            if (::style.isInitialized && this.style != style) releaseRenderer()
            this.style = style
            val smallView = access.small.invoke(owner) as? View
            if (smallView !== small.get()) small = WeakReference<View>(smallView)
            val backgroundView = access.background.invoke(owner) as? View
            if (backgroundView !== background.get()) background = WeakReference<View>(backgroundView)
            val fakeView = access.fake.invoke(owner) as? View
            if (fakeView !== fake.get()) {
                if (renderedSlot == SmallIslandGlowSlot.FAKE) releaseRenderer()
                fake.get()?.removeOnAttachStateChangeListener(this)
                fake = WeakReference<View>(fakeView)
                fakeView?.addOnAttachStateChangeListener(this)
            }
            val fakeSmallView = fakeView?.let { access.fakeSmall.invoke(it) as? View }
            if (fakeSmallView !== fakeSmall.get()) fakeSmall = WeakReference<View>(fakeSmallView)
            val currentDensity = owner.resources.displayMetrics.density
            if (density != currentDensity) {
                releaseRenderer()
                density = currentDensity
                stockRadius = stockDimension(owner, "island_radius", Float.MAX_VALUE)
                stockRange = stockDimension(owner, "island_glow_effect_area_size", 20f * density)
            }
            radius = IslandCornerHook.configuredPx(owner, false) ?: stockRadius
            observe()
            owner.invalidate()
            fakeView?.invalidate()
        }

        override fun onViewAttachedToWindow(view: View) = observe()

        override fun onViewDetachedFromWindow(view: View) {
            if (renderedSlot == SmallIslandGlowSlot.FAKE) releaseRenderer()
            observe(exclude = view)
            owner.get()?.invalidate()
        }

        private fun observe(exclude: View? = null) {
            val current = listOfNotNull(owner.get(), fake.get())
                .filter { it !== exclude && it.isAttachedToWindow }
                .map { it.viewTreeObserver }.filter { it.isAlive }.distinct()
            val previous = observers.mapNotNull { it.get() }.filter { it.isAlive }
            if (current == previous) return
            previous.filterNot(current::contains).forEach { it.removeOnPreDrawListener(this) }
            current.filterNot(previous::contains).forEach { it.addOnPreDrawListener(this) }
            observers = current.map { WeakReference(it) }
        }

        override fun onPreDraw(): Boolean {
            runCatching { draw() }.onFailure {
                dispose()
                reportFailure(it)
            }
            return true
        }

        private fun draw() {
            val owner = owner.get() ?: return dispose()
            val bg = background.get()
            val fakeView = fake.get()
            val realHost = bg?.rootView as? ViewGroup
            val fakeHost = fakeView?.rootView as? ViewGroup
            val leavingSmall = wasSmall && !inSmallState && access.isAnimating.invoke(owner) == true
            val realAlpha = if (inSmallState || leavingSmall) opacity(small.get(), realHost) else 0f
            val fakeAlpha = opacity(fakeSmall.get(), fakeHost)
            if (fakeAlpha > 0f && pendingFakeUntil != 0L) {
                pendingFakeUntil = 0L
                mainHandler.removeCallbacks(expireFakeStart)
            }
            val source = selectSmallIslandGlowSlot(realAlpha, fakeAlpha)
            if (source == null) {
                if (inSmallState || leavingSmall || pendingFakeUntil > SystemClock.uptimeMillis()) {
                    releaseRenderer()
                } else dispose()
                return
            }
            val host: ViewGroup
            val coordinates: View
            val alpha: Float
            when (source) {
                SmallIslandGlowSlot.REAL -> {
                    host = realHost ?: return
                    coordinates = bg
                    alpha = realAlpha
                    area.set(
                        (access.left.invoke(bg) as Int).toFloat(),
                        (access.top.invoke(bg) as Int).toFloat(),
                        (access.right.invoke(bg) as Int).toFloat(),
                        (access.bottom.invoke(bg) as Int).toFloat(),
                    )
                }
                SmallIslandGlowSlot.FAKE -> {
                    host = fakeHost ?: return
                    coordinates = fakeSmall.get() ?: return
                    alpha = fakeAlpha
                    area.set(0f, 0f, coordinates.width.toFloat(), coordinates.height.toFloat())
                }
            }
            val width = area.width()
            val height = area.height()
            if (width <= 0f || height <= 0f) return releaseRenderer()
            matrix.reset()
            coordinates.transformMatrixToGlobal(matrix)
            host.transformMatrixToLocal(matrix)
            matrix.mapRect(area)
            if (area.isEmpty || area.right <= 0 || area.left >= host.width ||
                area.bottom <= 0 || area.top >= host.height
            ) {
                releaseRenderer()
                return
            }
            if (renderer?.host?.get() !== host) {
                releaseRenderer()
                renderer = Renderer.create(access, host, owner, stockRange, style, configure)
            }
            renderer?.update(area, width, height, minOf(radius, width / 2f, height / 2f), alpha)
            renderedSlot = source
        }

        private fun opacity(slot: View?, host: ViewGroup?): Float {
            if (slot == null || host == null || !slot.isAttachedToWindow || !host.isShown ||
                slot.windowVisibility != View.VISIBLE || slot.width <= 0 || slot.height <= 0
            ) return 0f
            var opacity = 1f
            var view: View = slot
            while (view !== host) {
                if (view.visibility != View.VISIBLE || view.scaleX == 0f || view.scaleY == 0f ||
                    (view.getClipBounds(clip) && clip.isEmpty)
                ) return 0f
                opacity *= view.alpha
                if (opacity <= 0f) return 0f
                view = view.parent as? View ?: return 0f
            }
            return opacity
        }

        private fun stockDimension(owner: View, name: String, fallback: Float): Float {
            val resources = owner.resources
            return try {
                val id = resources.getIdentifier(name, "dimen", resources.getResourcePackageName(owner.id))
                if (id != 0) resources.getDimension(id) else fallback
            } catch (_: Resources.NotFoundException) {
                fallback
            }
        }

        fun dispose() {
            mainHandler.removeCallbacks(expireFakeStart)
            owner.get()?.let { view ->
                if (activeBindings[view] === this) activeBindings.remove(view)
            }
            fake.get()?.removeOnAttachStateChangeListener(this)
            observers.forEach { it.get()?.takeIf(ViewTreeObserver::isAlive)?.removeOnPreDrawListener(this) }
            observers = emptyList()
            releaseRenderer()
        }

        private fun releaseRenderer() {
            renderedSlot = null
            val active = renderer ?: return
            renderer = null
            active.dispose()
        }
    }

    private class Renderer(
        private val access: Access,
        host: ViewGroup,
        overlay: ViewGroupOverlay,
        effectTop: View,
        effectBottom: SmallIslandGlowBackgroundView,
        shaderHost: View,
        private val padding: Float,
    ) {
        val host = WeakReference(host)
        private val overlay = WeakReference(overlay)
        private val effectTop = WeakReference(effectTop)
        private val effectBottom = WeakReference(effectBottom)
        private val shaderHost = WeakReference(shaderHost)
        private var lastSize = SizeF(0f, 0f)
        private var lastRadius = -1f
        private var running = false

        fun update(area: RectF, width: Float, height: Float, radius: Float, alpha: Float) {
            val topEffect = effectTop.get() ?: return
            val bottomEffect = effectBottom.get() ?: return
            val shader = shaderHost.get() ?: return
            if (width != lastSize.width || height != lastSize.height) {
                val renderWidth = ceil(width + 2f * padding).toInt()
                val renderHeight = ceil(height + 2f * padding).toInt()
                sizeEffect(topEffect, renderWidth, renderHeight)
                sizeEffect(bottomEffect, renderWidth, renderHeight)
                lastSize = SizeF(width, height)
                access.sizeTop.invoke(shader, lastSize)
                access.sizeBottom.invoke(shader, lastSize)
            }
            if (radius != lastRadius) {
                access.radius.invoke(shader, radius)
                lastRadius = radius
            }
            val scaleX = area.width() / width
            val scaleY = area.height() / height
            place(topEffect, area.centerX(), area.centerY(), scaleX, scaleY, alpha)
            val haloAlpha = if (bottomEffect.hasApertureMask) alpha else 0f
            place(bottomEffect, area.centerX(), area.centerY(), scaleX, scaleY, haloAlpha)
            if (!running) {
                // Overlay children are attached to the renderer, but isShown() stops at the overlay.
                access.start.invoke(shader, true)
                running = true
            }
        }

        private fun place(effect: View, x: Float, y: Float, scaleX: Float, scaleY: Float, alpha: Float) {
            effect.translationX = x - effect.left - effect.width / 2f
            effect.translationY = y - effect.top - effect.height / 2f
            effect.scaleX = scaleX
            effect.scaleY = scaleY
            effect.alpha = alpha
        }

        private fun sizeEffect(effect: View, width: Int, height: Int) {
            if (width == effect.width && height == effect.height) return
            effect.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
            )
            effect.layout(effect.left, effect.top, effect.left + width, effect.top + height)
        }

        fun dispose() {
            effectTop.get()?.alpha = 0f
            effectBottom.get()?.alpha = 0f
            shaderHost.get()?.let { runCatching { access.stop.invoke(it, true) } }
            // The window can already be detaching its overlay views.
            mainHandler.post {
                val target = overlay.get() ?: return@post
                shaderHost.get()?.let(target::remove)
                effectTop.get()?.let(target::remove)
                effectBottom.get()?.let(target::remove)
            }
        }

        companion object {
            fun create(
                access: Access,
                host: ViewGroup,
                owner: View,
                stockRange: Float,
                style: SmallIslandGlowStyle,
                configure: (View, SmallIslandGlowStyle, Float) -> Float?,
            ): Renderer {
                val shader = access.createGlow.newInstance(owner.context) as View
                val topEffect = View(owner.context).apply { setBackgroundColor(0) }
                val bottomEffect = SmallIslandGlowBackgroundView(owner.context)
                val overlay = host.overlay
                try {
                    access.autoAperture.invoke(shader, false)
                    access.bindEffect.invoke(shader, topEffect, bottomEffect)
                    access.distance.invoke(shader, stockRange)
                    val padding = (configure(shader, style, stockRange) ?: stockRange) + 2f
                    overlay.add(shader)
                    overlay.add(bottomEffect)
                    overlay.add(topEffect)
                    return Renderer(access, host, overlay, topEffect, bottomEffect, shader, padding)
                } catch (error: Throwable) {
                    runCatching { access.stop.invoke(shader, true) }
                    overlay.remove(shader)
                    overlay.remove(topEffect)
                    overlay.remove(bottomEffect)
                    throw error
                }
            }
        }
    }
}
