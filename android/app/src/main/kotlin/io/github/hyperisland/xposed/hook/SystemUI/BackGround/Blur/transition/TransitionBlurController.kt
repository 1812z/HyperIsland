package io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.transition

import android.view.View
import io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.config.IslandMaterialConfigStore
import io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.model.IslandType
import io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.model.MaterialType
import io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.nativeblur.NativeBlurRenderer
import io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.nativeblur.OwnedBlur
import io.github.hyperisland.xposed.hook.SystemUI.SoftGlass.SoftGlassController
import java.util.Collections
import java.util.WeakHashMap

/** Owns fake-island material setup and per-View transition blur resources. */
internal class TransitionBlurController(
    private val configStore: IslandMaterialConfigStore,
    private val nativeBlurRenderer: NativeBlurRenderer,
) {
    private val transitionBlurs = Collections.synchronizedMap(
        WeakHashMap<View, TransitionBlur>()
    )

    fun isEnabled(typeName: String): Boolean {
        val type = typeFromName(typeName) ?: return false
        return configStore.materialFor(type).isCustom
    }

    fun isSoftGlass(typeName: String): Boolean {
        val type = typeFromName(typeName) ?: return false
        return configStore.materialFor(type).type == MaterialType.SOFT
    }

    fun apply(view: View, typeName: String): Boolean {
        val type = typeFromName(typeName) ?: return false
        val config = configStore.blurFor(type)
        val material = configStore.materialFor(type)
        if (!material.isCustom || !view.isAttachedToWindow) {
            release(view)
            return false
        }
        if (material.type == MaterialType.SOFT) {
            val applied = SoftGlassController.apply(view, material.softGlass)
            releaseNative(view)
            if (applied) ensureDetachCleanup(view)
            // Soft glass must stay entirely on HyperOS' Bionics channel. Never
            // substitute the module's LiquidGlassDrawable when native setup fails.
            return applied
        }
        return runCatching {
            var transition = transitionBlurs[view]
            if (transition?.owned?.type != type) {
                release(view)
                val owned = nativeBlurRenderer.create(view, type) ?: return@runCatching false
                transition = TransitionBlur(owned, view.background)
                transitionBlurs[view] = transition
            }
            val active = transition
            nativeBlurRenderer.update(view, active.owned, config, view)
            if (view.background !== active.owned.drawable) {
                view.background = active.owned.drawable
            }
            active.owned.active = true
            ensureDetachCleanup(view)
            view.invalidate()
            true
        }.getOrDefault(false)
    }

    fun isApplied(view: View, typeName: String): Boolean {
        val type = typeFromName(typeName) ?: return false
        if (configStore.materialFor(type).type == MaterialType.SOFT &&
            SoftGlassController.isManaged(view)
        ) return true
        val transition = transitionBlurs[view] ?: return false
        return transition.owned.type == type && view.background === transition.owned.drawable
    }

    fun hasManagedSoftGlass(view: View): Boolean = SoftGlassController.isManaged(view)

    /** Native Bionics state does not reliably survive fake-View reuse or child replacement. */
    fun invalidateSoftGlass(view: View) {
        SoftGlassController.onSystemMaterialReplaced(view)
    }

    fun release(view: View) {
        SoftGlassController.release(view)
        releaseNative(view)
    }

    private fun releaseNative(view: View) {
        val transition = transitionBlurs.remove(view) ?: return
        if (view.background === transition.owned.drawable) {
            view.background = transition.stockDrawable
        }
        runCatching { transition.owned.updateEffectRadius(0) }
        transition.owned.release()
        transition.owned.active = false
        view.invalidate()
    }

    private fun ensureDetachCleanup(view: View) {
        if (view.getTag(TRANSITION_BLUR_LISTENER_TAG) != null) return
        val listener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit

            override fun onViewDetachedFromWindow(view: View) {
                release(view)
                view.setTag(TRANSITION_BLUR_LISTENER_TAG, null)
                view.removeOnAttachStateChangeListener(this)
            }
        }
        view.setTag(TRANSITION_BLUR_LISTENER_TAG, listener)
        view.addOnAttachStateChangeListener(listener)
    }

    private data class TransitionBlur(
        val owned: OwnedBlur,
        val stockDrawable: android.graphics.drawable.Drawable?,
    )

    private companion object {
        const val TRANSITION_BLUR_LISTENER_TAG = 0x4859424c

        fun typeFromName(typeName: String): IslandType? = when (typeName) {
            "SMALL" -> IslandType.SMALL
            "BIG" -> IslandType.BIG
            "EXPAND" -> IslandType.EXPAND
            else -> null
        }
    }
}
