package io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.transition

import android.view.View
import io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.config.IslandMaterialConfigStore
import io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.model.BlurConfig
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
        val material = configStore.materialFor(type)
        if (!material.isCustom || !view.isAttachedToWindow) {
            release(view)
            return false
        }
        if (material.type == MaterialType.SOFT) {
            releaseNative(view)
            val applied = SoftGlassController.apply(view, material.softGlass)
            if (applied) ensureDetachCleanup(view)
            if (applied) return true
            // Keep the same host/lifecycle as Gaussian blur when native Bionics is unavailable.
            return applyNative(view, type, material.softFallback())
        }
        SoftGlassController.release(view, restoreBackground = false)
        return applyNative(view, type, configStore.blurFor(type))
    }

    private fun applyNative(view: View, type: IslandType, config: BlurConfig): Boolean {
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
