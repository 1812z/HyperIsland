package io.github.hyperisland.xposed.hook.SystemUI

import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.View
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.hyperisland.xposed.hook.IslandBackgroundHook
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

/** Keeps custom island visuals visible while SystemUI animates its fake transition views. */
object IslandTransitionVisualHook : BaseHook() {
    private const val TAG = "HyperIsland[TransitionVisual]"
    private const val FAKE_VIEW_CLASS =
        "miui.systemui.dynamicisland.window.content.DynamicIslandContentFakeView"
    private const val ANIMATION_DELEGATE_CLASS =
        "miui.systemui.dynamicisland.anim.DynamicIslandAnimationDelegate"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val hookedFakeClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    )
    private val fakeViews = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
    )
    private val targets = Collections.synchronizedMap(
        WeakHashMap<View, TransitionTarget>()
    )
    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        if (param.packageName != "com.android.systemui") return
        hookPlugin(module, param.defaultClassLoader)
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { classLoader ->
            hookPlugin(module, classLoader)
        }
    }

    override fun onConfigChanged() {
        mainHandler.post {
            synchronized(fakeViews) { fakeViews.toList() }.forEach(::refreshFakeView)
        }
    }

    private fun hookPlugin(module: XposedModule, classLoader: ClassLoader) {
        val fakeClass = runCatching {
            Class.forName(FAKE_VIEW_CLASS, false, classLoader)
        }.getOrNull() ?: return
        if (!hookedFakeClasses.add(fakeClass)) return

        val access = FakeViewAccess(
            small = findMethod(fakeClass, "getFakeSmallIsland"),
            big = findMethod(fakeClass, "getFakeBigIsland"),
            expand = findMethod(fakeClass, "getFakeExpandedView"),
        )
        hookRefreshAfter(module, fakeClass.getDeclaredMethod("onFinishInflate")) { owner ->
            fakeViews.add(owner)
            refreshFakeView(owner, access)
        }
        hookRefreshAfter(module, fakeClass.getDeclaredMethod("setVisibility", Int::class.javaPrimitiveType)) {
            refreshFakeView(it, access)
        }

        val delegateClass = runCatching {
            Class.forName(ANIMATION_DELEGATE_CLASS, false, classLoader)
        }.getOrNull() ?: return
        val getFakeView = findMethod(delegateClass, "getFakeView") ?: return
        sequenceOf("updateFakeViewAnimState", "containerScheduleUpdate").forEach { name ->
            val method = delegateClass.declaredMethods.firstOrNull {
                it.name == name && it.parameterCount == 0
            } ?: return@forEach
            hookRefreshAfter(module, method) { delegate ->
                val fakeView = runCatching { getFakeView.invoke(delegate) }.getOrNull() ?: return@hookRefreshAfter
                fakeViews.add(fakeView)
                refreshFakeView(fakeView, access)
            }
        }
        log(module, "fake island transition visuals hooked")
    }

    private fun hookRefreshAfter(
        module: XposedModule,
        method: Method,
        refresh: (Any) -> Unit,
    ) {
        method.isAccessible = true
        module.hook(method).intercept { chain ->
            val result = chain.proceed()
            chain.thisObject?.let(refresh)
            result
        }
    }

    private fun refreshFakeView(fakeView: Any) {
        val clazz = fakeView.javaClass
        refreshFakeView(
            fakeView,
            FakeViewAccess(
                small = findMethod(clazz, "getFakeSmallIsland"),
                big = findMethod(clazz, "getFakeBigIsland"),
                expand = findMethod(clazz, "getFakeExpandedView"),
            ),
        )
    }

    private fun refreshFakeView(fakeView: Any, access: FakeViewAccess) {
        access.forEach(fakeView) { typeName, view ->
            val target = synchronized(targets) {
                targets.getOrPut(view) { TransitionTarget(view.background) }
            }
            if (IslandBlurHook.isTransitionBlurEnabled(typeName)) {
                restoreStockBackground(view, target)
                IslandBlurHook.applyTransitionBlur(view, typeName)
                return@forEach
            }

            IslandBlurHook.releaseTransitionBlur(view)
            val drawable = IslandBackgroundHook.createTransitionBackground(view, typeName)
            if (drawable != null) {
                if (view.background !== target.customDrawable) {
                    target.customDrawable?.callback = null
                    target.customDrawable = drawable
                    view.background = drawable
                }
            } else {
                restoreStockBackground(view, target)
            }
        }
    }

    private fun restoreStockBackground(view: View, target: TransitionTarget) {
        target.customDrawable?.callback = null
        target.customDrawable = null
        val stock = target.stockDrawable.get()
        if (view.background !== stock) view.background = stock
    }

    private fun findMethod(clazz: Class<*>, name: String): Method? {
        var current: Class<*>? = clazz
        while (current != null) {
            runCatching {
                return current.getDeclaredMethod(name).apply { isAccessible = true }
            }
            current = current.superclass
        }
        return null
    }

    private class FakeViewAccess(
        private val small: Method?,
        private val big: Method?,
        private val expand: Method?,
    ) {
        fun forEach(owner: Any, action: (String, View) -> Unit) {
            apply(owner, "SMALL", small, action)
            apply(owner, "BIG", big, action)
            apply(owner, "EXPAND", expand, action)
        }

        private fun apply(
            owner: Any,
            typeName: String,
            getter: Method?,
            action: (String, View) -> Unit,
        ) {
            val view = runCatching { getter?.invoke(owner) as? View }.getOrNull() ?: return
            action(typeName, view)
        }
    }

    private class TransitionTarget(stockDrawable: Drawable?) {
        val stockDrawable = WeakReference(stockDrawable)
        var customDrawable: Drawable? = null
    }
}
