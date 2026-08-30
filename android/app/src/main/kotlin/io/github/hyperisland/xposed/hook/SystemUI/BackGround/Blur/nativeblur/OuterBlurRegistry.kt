package io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.nativeblur

import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.SystemUiReflection.findMethod
import io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.config.IslandMaterialConfigStore
import io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.model.BlurConfig
import io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.model.IslandType
import io.github.hyperisland.xposed.hook.SystemUI.IslandOutlineHook
import io.github.hyperisland.xposed.log
import io.github.hyperisland.xposed.logWarn
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.util.Collections
import java.util.WeakHashMap

/** Owns stable DynamicIslandBackgroundView blur instances and their recovery queues. */
internal class OuterBlurRegistry(
    private val configStore: IslandMaterialConfigStore,
    private val renderer: NativeBlurRenderer,
) {
    private val active = Collections.synchronizedMap(WeakHashMap<View, OuterBlur>())
    private val pending = Collections.synchronizedMap(WeakHashMap<View, PendingBlur>())
    private val recovering = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    )
    private val detachListeners = Collections.synchronizedMap(
        WeakHashMap<View, View.OnAttachStateChangeListener>()
    )

    fun onConfigChanged() {
        val snapshot = synchronized(active) {
            active.entries.map { (view, outer) -> view to outer }
        }
        snapshot.forEach { (view, outer) ->
            val config = configStore.blurFor(outer.owned.type)
            if (!config.isActive) {
                deactivate(view, outer.drawableField, "config-disabled")
                return@forEach
            }
            val shapeView = outer.shapeView.get() ?: return@forEach
            if (!view.isAttachedToWindow || !shapeView.isAttachedToWindow) return@forEach
            runCatching {
                // Material-only changes (for example the soft-glass highlight switch)
                // do not necessarily produce a SystemUI state/content callback. Refresh the
                // live drawable here so its config revision changes without waiting for the
                // next notification update. Refraction remains owned by the same drawable.
                renderer.update(view, outer.owned, config, shapeView)
                show(outer)
                outer.drawableField.set(view, outer.renderDrawable)
                ensureCallback(view, outer)
                view.invalidate()
            }.onFailure { error ->
                logWarn("$TAG live config refresh failed: ${error.message}")
            }
        }
        synchronized(pending) {
            pending.entries.removeAll { (view, item) ->
                val remove = !configStore.blurFor(item.type).isActive
                if (remove) recovering.remove(view)
                remove
            }
        }
    }

    fun isEmpty(): Boolean = synchronized(active) { active.isEmpty() }

    fun hasActiveVisual(view: View): Boolean = active[view]?.active == true

    fun hasDrawableBounds(view: View): Boolean = currentBounds(view) != null

    fun prepareDraw(view: View, drawableField: Field, tempHidden: Boolean) {
        realizePending(view, drawableField, tempHidden)
        val outer = active[view] ?: return
        if (!outer.active) return
        runCatching {
            if (drawableField.get(view) !== outer.renderDrawable) {
                drawableField.set(view, outer.renderDrawable)
            }
            // BackgroundBlurDrawable can remain visually active when SystemUI leaves the
            // wrapper alpha at zero, while LiquidGlassDrawable correctly fades its rim to
            // zero. Reassert the owned drawable alpha immediately before the same onDraw.
            outer.renderDrawable.alpha = 255
        }
    }

    fun apply(
        backgroundView: View,
        shapeView: View,
        type: IslandType,
        config: BlurConfig,
        drawableField: Field,
        tempHidden: Boolean,
    ): Boolean = runCatching {
        if (!backgroundView.isAttachedToWindow) return@runCatching false
        if (tempHidden || recovering.contains(backgroundView)) {
            pending[backgroundView] = PendingBlur(
                WeakReference(shapeView),
                type,
                requireVisibleGeometry = true,
            )
            return@runCatching false
        }
        val outer = active[backgroundView]
        if (outer == null) {
            pending[backgroundView] = PendingBlur(WeakReference(shapeView), type)
            backgroundView.invalidate()
            return@runCatching true
        }
        pending.remove(backgroundView)
        val typeChanged = outer.owned.type != type
        if (typeChanged) {
            val currentDrawable = drawableField.get(backgroundView) as? Drawable
            if (currentDrawable !== outer.renderDrawable) outer.stockDrawable = currentDrawable
            log("$TAG reuse ${outer.owned.type} -> $type")
            outer.owned.type = type
        }
        outer.shapeView = WeakReference(shapeView)
        ensureDetachCleanup(backgroundView)
        renderer.update(backgroundView, outer.owned, config, shapeView)
        show(outer)
        rebuildOutlineIfNeeded(backgroundView, outer, type, typeChanged)
        drawableField.set(backgroundView, outer.renderDrawable)
        ensureCallback(backgroundView, outer)
        outer.owned.active = true
        outer.active = true
        backgroundView.invalidate()
        true
    }.onFailure {
        recovering.remove(backgroundView)
        val failed = active.remove(backgroundView)
        failed?.release()
        if (failed?.stockDrawable != null) {
            runCatching { drawableField.set(backgroundView, failed.stockDrawable) }
            backgroundView.invalidate()
        }
    }.getOrDefault(false)

    fun deactivate(
        backgroundView: View,
        drawableField: Field,
        reason: String = "state-disabled",
    ) {
        pending.remove(backgroundView)
        recovering.remove(backgroundView)
        val outer = active.remove(backgroundView) ?: return
        if (runCatching { drawableField.get(backgroundView) }.getOrNull() === outer.renderDrawable) {
            runCatching { drawableField.set(backgroundView, outer.stockDrawable) }
        }
        log("$TAG release ${outer.owned.type}, reason=$reason")
        outer.release()
        backgroundView.invalidate()
    }

    fun shouldKeepOpaque(backgroundView: View): Boolean {
        val pendingItem = pending[backgroundView]
        val outer = active[backgroundView]
        val shapeView = pendingItem?.shapeView?.get() ?: outer?.shapeView?.get() ?: return false
        val type = pendingItem?.type ?: outer?.owned?.type ?: return false
        return configStore.blurFor(type).isActive && hasVisibleGeometry(backgroundView, shapeView)
    }

    fun enterTempHidden() {
        synchronized(pending) {
            pending.entries.removeAll { (view, item) ->
                !view.isAttachedToWindow || item.shapeView.get() == null
            }
        }
        val entries = synchronized(active) { active.entries.map { it.key to it.value } }
        entries.forEach { (view, outer) ->
            outer.shapeView.get()?.let { shapeView ->
                pending[view] = PendingBlur(
                    WeakReference(shapeView),
                    outer.owned.type,
                    requireVisibleGeometry = true,
                )
            }
        }
    }

    fun rebuildRecoveryQueue(): List<View> {
        val reusable = synchronized(active) {
            active.entries.mapNotNull { (view, outer) ->
                val shapeView = outer.shapeView.get() ?: return@mapNotNull null
                if (!view.isAttachedToWindow || !shapeView.isAttachedToWindow) {
                    return@mapNotNull null
                }
                Triple(view, shapeView, outer.owned.type)
            }
        }
        reusable.forEach { (view, shapeView, type) ->
            recovering.add(view)
            pending[view] = PendingBlur(
                WeakReference(shapeView),
                type,
                requireVisibleGeometry = true,
            )
        }
        return reusable.map { it.first }
    }

    fun hideEdgeHighlight(backgroundView: View) {
        active[backgroundView]?.owned?.liquidDrawable?.hideEdgeHighlight()
    }

    fun updateStockOutline(
        backgroundView: Any?,
        stockDrawable: Drawable?,
        typeName: String?,
    ): Boolean {
        val view = backgroundView as? View ?: return false
        val stock = stockDrawable ?: return false
        val outer = active[view] ?: return false
        typeName ?: return false
        outer.stockDrawable = stock
        if (outer.owned.type.name != typeName || !outer.active) return true
        IslandOutlineHook.releaseOutline(outer.renderDrawable)
        outer.renderDrawable.callback = null
        outer.renderDrawable = IslandOutlineHook.withOutline(
            outer.owned.drawable,
            stock,
            outer.owned.type == IslandType.EXPAND,
            outer.owned.type.name,
        )
        runCatching { outer.drawableField.set(view, outer.renderDrawable) }
        ensureCallback(view, outer)
        view.invalidate()
        return true
    }

    private fun realizePending(backgroundView: View, drawableField: Field, tempHidden: Boolean) {
        val item = pending[backgroundView] ?: return
        if (!backgroundView.isAttachedToWindow || backgroundView.visibility != View.VISIBLE) return
        val config = configStore.blurFor(item.type)
        if (!config.isActive) {
            removePending(backgroundView)
            return
        }
        val shapeView = item.shapeView.get() ?: run {
            removePending(backgroundView)
            return
        }
        if (tempHidden || recovering.contains(backgroundView) || item.requireVisibleGeometry) {
            if (!hasVisibleGeometry(backgroundView, shapeView)) return
        }
        if (currentBounds(backgroundView) == null) return

        var candidate: OwnedBlur? = null
        runCatching {
            val current = active[backgroundView]
            if (current != null) {
                val typeChanged = current.owned.type != item.type
                if (typeChanged) {
                    val currentDrawable = drawableField.get(backgroundView) as? Drawable
                    if (currentDrawable !== current.renderDrawable) {
                        current.stockDrawable = currentDrawable
                    }
                    current.owned.type = item.type
                }
                renderer.update(backgroundView, current.owned, config, shapeView)
                show(current)
                rebuildOutlineIfNeeded(backgroundView, current, item.type, typeChanged)
                drawableField.set(backgroundView, current.renderDrawable)
                ensureCallback(backgroundView, current)
                current.owned.active = true
                current.active = true
                removePending(backgroundView)
                ensureDetachCleanup(backgroundView)
                return@runCatching
            }
            val stock = drawableField.get(backgroundView) as? Drawable
            val owned = renderer.create(backgroundView, item.type) ?: run {
                removePending(backgroundView)
                log("$TAG native blur unavailable for ${item.type}")
                return@runCatching
            }
            candidate = owned
            if (!setCurrentBounds(backgroundView, owned.drawable)) {
                owned.release()
                candidate = null
                return@runCatching
            }
            val outer = OuterBlur(owned, stock, drawableField, WeakReference(shapeView))
            renderer.update(backgroundView, owned, config, shapeView)
            outer.renderDrawable = IslandOutlineHook.withOutline(
                owned.drawable,
                stock,
                item.type == IslandType.EXPAND,
                item.type.name,
            )
            outer.renderDrawable.alpha = 255
            ensureCallback(backgroundView, outer)
            drawableField.set(backgroundView, outer.renderDrawable)
            active[backgroundView] = outer
            candidate = null
            owned.active = true
            outer.active = true
            removePending(backgroundView)
            ensureDetachCleanup(backgroundView)
        }.onFailure { error ->
            logWarn("$TAG realization failed for ${item.type}: ${error.message}")
            candidate?.let { owned ->
                runCatching { owned.updateEffectRadius(0) }
                owned.release()
            }
            removePending(backgroundView)
            val failed = active.remove(backgroundView)
            failed?.release()
            if (failed != null &&
                runCatching { drawableField.get(backgroundView) }.getOrNull() === failed.renderDrawable
            ) {
                runCatching { drawableField.set(backgroundView, failed.stockDrawable) }
            }
        }
    }

    private fun rebuildOutlineIfNeeded(
        view: View,
        outer: OuterBlur,
        type: IslandType,
        typeChanged: Boolean,
    ) {
        val outlineEnabled = IslandOutlineHook.isOutlineEnabled(type == IslandType.EXPAND)
        if (!typeChanged && IslandOutlineHook.hasOutline(outer.renderDrawable) == outlineEnabled) return
        IslandOutlineHook.releaseOutline(outer.renderDrawable)
        outer.renderDrawable.callback = null
        outer.renderDrawable = IslandOutlineHook.withOutline(
            outer.owned.drawable,
            outer.stockDrawable,
            type == IslandType.EXPAND,
            type.name,
        )
        ensureCallback(view, outer)
    }

    private fun show(outer: OuterBlur) {
        outer.owned.liquidDrawable.setVisible(true, false)
        outer.renderDrawable.setVisible(true, false)
        outer.renderDrawable.alpha = 255
    }

    private fun ensureCallback(view: View, outer: OuterBlur) {
        if (outer.renderDrawable.callback == null) {
            outer.renderDrawable.callback = WeakViewDrawableCallback(view)
        }
    }

    private fun removePending(view: View) {
        pending.remove(view)
        recovering.remove(view)
    }

    private fun currentBounds(view: View): Rect? {
        val left = readInt(view, "getActualLeft") ?: return null
        val top = readInt(view, "getActualTop") ?: return null
        val right = readInt(view, "getActualWidth") ?: return null
        val bottom = readInt(view, "getActualHeight") ?: return null
        return Rect(left, top, right, bottom).takeIf {
            it.width() > 0 && it.height() > 0
        }
    }

    private fun setCurrentBounds(view: View, drawable: Drawable): Boolean {
        val bounds = currentBounds(view) ?: return false
        val stroke = renderer.strokeWidth(view)
        drawable.setBounds(
            bounds.left - stroke,
            bounds.top - stroke,
            bounds.right + stroke,
            bounds.bottom + stroke,
        )
        return true
    }

    private fun readInt(view: View, method: String): Int? = runCatching {
        findMethod(view.javaClass, method)?.invoke(view) as? Int
    }.getOrNull()

    private fun hasVisibleGeometry(backgroundView: View, shapeView: View): Boolean {
        if (!backgroundView.isShown || !shapeView.isShown ||
            backgroundView.windowVisibility != View.VISIBLE ||
            shapeView.windowVisibility != View.VISIBLE
        ) return false
        val visibleRect = Rect()
        if (!shapeView.getGlobalVisibleRect(visibleRect) ||
            visibleRect.width() <= 0 || visibleRect.height() <= 0
        ) return false
        var current: View? = shapeView
        while (current != null) {
            if (current.visibility != View.VISIBLE || current.alpha <= 0.01f) return false
            current = current.parent as? View
        }
        return true
    }

    private fun ensureDetachCleanup(backgroundView: View) {
        synchronized(detachListeners) {
            if (detachListeners.containsKey(backgroundView)) return
            val listener = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) = Unit

                override fun onViewDetachedFromWindow(view: View) {
                    removePending(view)
                    active.remove(view)?.release()
                    detachListeners.remove(view)
                    view.removeOnAttachStateChangeListener(this)
                }
            }
            detachListeners[backgroundView] = listener
            backgroundView.addOnAttachStateChangeListener(listener)
        }
    }

    private class OuterBlur(
        val owned: OwnedBlur,
        var stockDrawable: Drawable?,
        val drawableField: Field,
        var shapeView: WeakReference<View>,
        var active: Boolean = false,
    ) {
        var renderDrawable: Drawable = owned.drawable

        fun release() {
            runCatching { owned.updateEffectRadius(0) }
            IslandOutlineHook.releaseOutline(renderDrawable)
            renderDrawable.callback = null
            owned.release()
            owned.active = false
            active = false
        }
    }

    private data class PendingBlur(
        val shapeView: WeakReference<View>,
        val type: IslandType,
        val requireVisibleGeometry: Boolean = false,
    )

    private companion object {
        const val TAG = "HyperIsland[IslandBlur]"
    }
}
