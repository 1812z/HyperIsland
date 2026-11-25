package io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur

import android.view.View
import java.lang.reflect.Method

/** Resolve through the owning plugin, never through a framework FrameLayout's loader. */
internal class MaskBlurCleanup private constructor(
    private val setViewMode: Method?,
    private val clearBlend: Method?,
    private val setBackgroundMode: Method?,
    private val clearMaterial: Method?,
    private val clearCommon: Method?,
    private val commonInstance: Any?,
) {
    fun clear(view: View) {
        // Keep all native cleanup channels: a null Drawable does not prove Bionics/blend is off.
        runCatching { clearMaterial?.invoke(null, view) }
        runCatching { setBackgroundMode?.invoke(null, view, 0) }
        val viewModeCleared = setViewMode?.let { method ->
            runCatching { method.invoke(null, view, 0) != false }.getOrDefault(false)
        } ?: false
        val blendCleared = clearBlend?.let { method ->
            runCatching { method.invoke(null, view) != false }.getOrDefault(false)
        } ?: false
        // CommonUtils resets the same view blur mode and background blend via the combined API.
        if (!viewModeCleared || !blendCleared) {
            runCatching { clearCommon?.invoke(commonInstance, view) }
        }
    }

    companion object {
        fun resolve(owner: Class<*>): MaskBlurCleanup {
            val loader = owner.classLoader
            fun load(name: String) = runCatching { Class.forName(name, false, loader) }.getOrNull()
            fun method(clazz: Class<*>?, name: String, vararg types: Class<*>): Method? =
                clazz?.let { SystemUiReflection.findMethod(it, name, *types) }
            val compat = load("miui.systemui.util.MiBlurCompat") ?: load("miui.util.MiBlurCompat")
            val style = load("miui.systemui.util.MiBackgroundStyle")
            val common = load("miui.systemui.util.CommonUtils")
            val instance = common?.let { runCatching { it.getField("INSTANCE").get(null) }.getOrNull() }
            return MaskBlurCleanup(
                method(compat, "setMiViewBlurModeCompat", View::class.java, Int::class.javaPrimitiveType!!),
                method(compat, "clearMiBackgroundBlendColorCompat", View::class.java),
                method(compat, "setMiBackgroundBlurModeCompat", View::class.java, Int::class.javaPrimitiveType!!),
                method(style, "clearBionicsMaterial", View::class.java),
                method(common, "clearMiBlurBlendEffect", View::class.java),
                instance,
            )
        }
    }
}
