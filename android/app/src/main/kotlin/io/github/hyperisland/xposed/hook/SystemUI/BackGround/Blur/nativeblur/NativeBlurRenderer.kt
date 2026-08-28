package io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.nativeblur

import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.View
import io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.SystemUiReflection.findMethod
import io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.config.IslandMaterialConfigStore
import io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.model.BlurConfig
import io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.model.IslandType
import io.github.hyperisland.xposed.hook.SystemUI.LiqudGlass.LiquidGlassDrawable
import java.lang.ref.WeakReference
import java.lang.reflect.Method

/** Creates and updates framework BackgroundBlurDrawable instances. */
internal class NativeBlurRenderer(
    private val configStore: IslandMaterialConfigStore,
) {
    fun create(view: View, type: IslandType): OwnedBlur? {
        val viewRoot = runCatching {
            findMethod(view.javaClass, "getViewRootImpl")?.invoke(view)
        }.getOrNull() ?: return null
        val drawable = runCatching {
            val method = findMethod(viewRoot.javaClass, "createBackgroundBlurDrawable")
                ?: return@runCatching null
            method.invoke(viewRoot) as? Drawable
        }.getOrNull() ?: return null

        return runCatching {
            val drawableClass = drawable.javaClass
            val methods = BlurDrawableMethods(
                setRadius = findMethod(
                    drawableClass,
                    "setBlurRadius",
                    Int::class.javaPrimitiveType!!,
                ) ?: return@runCatching null,
                setCornerRadius = findMethod(
                    drawableClass,
                    "setCornerRadius",
                    Float::class.javaPrimitiveType!!,
                    Float::class.javaPrimitiveType!!,
                    Float::class.javaPrimitiveType!!,
                    Float::class.javaPrimitiveType!!,
                ) ?: return@runCatching null,
                setColor = findMethod(
                    drawableClass,
                    "setColor",
                    Int::class.javaPrimitiveType!!,
                ) ?: return@runCatching null,
            )
            val strokeWidth = strokeWidth(view)
            val clippedDrawable = ClippedBlurDrawable(drawable, strokeWidth)
            val liquidDrawable = LiquidGlassDrawable(
                view.context,
                view,
                clippedDrawable,
                strokeWidth,
                configStore.liquidGlassFor(type),
            )
            OwnedBlur(
                drawable = liquidDrawable,
                effectDrawable = drawable,
                clippedDrawable = clippedDrawable,
                liquidDrawable = liquidDrawable,
                type = type,
                methods = methods,
            )
        }.getOrNull()
    }

    fun update(view: View, owned: OwnedBlur, config: BlurConfig, shapeView: View) {
        if (owned.cornerRadius.isNaN()) {
            val radius = resolveCornerRadius(view)
            owned.cornerRadius = radius
            owned.clippedDrawable.setCornerRadius(radius)
            owned.liquidDrawable.setCornerRadius(radius)
            owned.methods.setCornerRadius.invoke(
                owned.effectDrawable,
                radius,
                radius,
                radius,
                radius,
            )
        }
        owned.liquidDrawable.setContentView(shapeView)
        owned.liquidDrawable.setBackgroundBlurRadius(config.radius.toFloat())
        owned.liquidDrawable.setBlendColor(config.blendColor)
        if (owned.glassConfigRevision != configStore.revision ||
            owned.glassConfigType != owned.type
        ) {
            owned.glassConfigRevision = configStore.revision
            owned.glassConfigType = owned.type
            owned.liquidDrawable.updateConfig(configStore.liquidGlassFor(owned.type))
        }
        if (owned.blendColor != config.blendColor) {
            owned.blendColor = config.blendColor
            owned.methods.setColor.invoke(owned.effectDrawable, config.blendColor)
        }
        if (owned.blurRadius != config.radius) {
            owned.blurRadius = config.radius
            // Radius activates RenderThread, so initialize all other properties first.
            owned.methods.setRadius.invoke(owned.effectDrawable, config.radius)
        }
    }

    private fun resolveCornerRadius(view: View): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        32f,
        view.resources.displayMetrics,
    )

    fun strokeWidth(view: View): Int = runCatching {
        (findMethod(view.javaClass, "getStokeWidth")?.invoke(view) as? Int) ?: 0
    }.getOrDefault(0).coerceAtLeast(0)
}

internal class OwnedBlur(
    val drawable: Drawable,
    val effectDrawable: Drawable,
    val clippedDrawable: ClippedBlurDrawable,
    val liquidDrawable: LiquidGlassDrawable,
    var type: IslandType,
    internal val methods: BlurDrawableMethods,
    var active: Boolean = false,
) {
    var cornerRadius = Float.NaN
    var blurRadius = Int.MIN_VALUE
    var blendColor = Int.MIN_VALUE
    var glassConfigRevision = Int.MIN_VALUE
    var glassConfigType: IslandType? = null

    fun updateEffectRadius(radius: Int) {
        methods.setRadius.invoke(effectDrawable, radius)
        blurRadius = radius
    }

    fun release() {
        liquidDrawable.release()
        clippedDrawable.release()
    }
}

internal class WeakViewDrawableCallback(view: View) : Drawable.Callback {
    private val view = WeakReference(view)

    override fun invalidateDrawable(who: Drawable) {
        view.get()?.invalidateDrawable(who)
    }

    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
        view.get()?.scheduleDrawable(who, what, `when`)
    }

    override fun unscheduleDrawable(who: Drawable, what: Runnable) {
        view.get()?.unscheduleDrawable(who, what)
    }
}

internal data class BlurDrawableMethods(
    val setRadius: Method,
    val setCornerRadius: Method,
    val setColor: Method,
)
