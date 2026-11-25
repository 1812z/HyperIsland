package io.github.hyperisland.xposed.hook

import android.view.View
import android.view.ViewTreeObserver
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Coalesces notification binds and runs once after layout, before the changed view is drawn. */
internal class NotificationColorPreDraw(
    private val apply: (View) -> Unit,
) {
    private val pending = WeakHashMap<View, Callback>()

    fun schedule(view: View) {
        if (pending.containsKey(view)) return
        if (pending.size >= 256) {
            apply(view)
            return
        }
        val callback = Callback(view)
        pending[view] = callback
        view.addOnAttachStateChangeListener(callback)
        if (view.isAttachedToWindow) callback.onViewAttachedToWindow(view)
    }

    private inner class Callback(view: View) :
        ViewTreeObserver.OnPreDrawListener, View.OnAttachStateChangeListener {
        private val target = WeakReference(view)
        private var observer: WeakReference<ViewTreeObserver>? = null

        override fun onViewAttachedToWindow(view: View) {
            val current = view.viewTreeObserver
            current.addOnPreDrawListener(this)
            observer = WeakReference(current)
        }

        override fun onViewDetachedFromWindow(view: View) {
            unregister()
        }

        override fun onPreDraw(): Boolean {
            val view = target.get()
            if (view != null && !view.isAttachedToWindow) return true
            unregister()
            if (view == null) return true
            pending.remove(view)
            view.removeOnAttachStateChangeListener(this)
            apply(view)
            return true
        }

        private fun unregister() {
            observer?.get()?.takeIf { it.isAlive }?.removeOnPreDrawListener(this)
            observer = null
        }
    }
}
