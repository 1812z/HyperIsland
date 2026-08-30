package io.github.hyperisland.xposed.utils

import android.content.res.Resources
import android.util.TypedValue
import io.github.hyperisland.xposed.ConfigManager
import io.github.libxposed.api.XposedModule
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/** Replaces dimension resources by entry name in the current hooked process. */
object ResourceDimenHook {
    private val replacements = ConcurrentHashMap<String, (Resources) -> Float?>()
    private val offsets = ConcurrentHashMap<String, (Resources) -> Float>()
    @Volatile private var installed = false

    fun register(
        module: XposedModule,
        resourceName: String,
        valuePx: (Resources) -> Float,
    ) {
        replacements[resourceName] = { resources -> valuePx(resources) }
        install(module)
    }

    /** Replaces a dp resource unless the preference value means "follow system". */
    fun registerDpOrSystem(
        module: XposedModule,
        resourceName: String,
        preferenceKey: String,
        followSystemValue: Int,
        range: IntRange,
    ) {
        replacements[resourceName] = { resources ->
            val configuredDp = ConfigManager.getInt(preferenceKey, followSystemValue)
            if (configuredDp == followSystemValue) {
                null
            } else {
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    configuredDp.coerceIn(range).toFloat(),
                    resources.displayMetrics,
                )
            }
        }
        install(module)
    }

    fun registerDp(
        module: XposedModule,
        resourceName: String,
        preferenceKey: String,
        defaultDp: Int,
        range: IntRange,
    ) = register(module, resourceName) { resources ->
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            ConfigManager.getInt(preferenceKey, defaultDp).coerceIn(range).toFloat(),
            resources.displayMetrics,
        )
    }

    fun registerDpOffset(
        module: XposedModule,
        resourceName: String,
        preferenceKey: String,
        defaultDp: Int,
        range: IntRange,
    ) {
        offsets[resourceName] = { resources ->
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                ConfigManager.getInt(preferenceKey, defaultDp).coerceIn(range).toFloat(),
                resources.displayMetrics,
            )
        }
        install(module)
    }

    private fun install(module: XposedModule) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            hook(module, "getDimension") { it }
            hook(module, "getDimensionPixelSize") { it.roundToInt() }
            hook(module, "getDimensionPixelOffset") { it.toInt() }
            installed = true
        }
    }

    private fun hook(
        module: XposedModule,
        methodName: String,
        convert: (Float) -> Any,
    ) {
        val method = Resources::class.java.getDeclaredMethod(
            methodName,
            Int::class.javaPrimitiveType,
        )
        module.hook(method).intercept { chain ->
            val resources = chain.thisObject as? Resources ?: return@intercept chain.proceed()
            val resourceId = chain.args.getOrNull(0) as? Int ?: return@intercept chain.proceed()
            val resourceName = runCatching {
                if (resources.getResourceTypeName(resourceId) != "dimen") return@runCatching null
                resources.getResourceEntryName(resourceId)
            }.getOrNull() ?: return@intercept chain.proceed()
            val replacement = replacements[resourceName]
            if (replacement != null) {
                val replacementPx = replacement(resources)
                if (replacementPx != null) {
                    return@intercept convert(replacementPx)
                }
                return@intercept chain.proceed()
            }
            val offset = offsets[resourceName] ?: return@intercept chain.proceed()
            val original = chain.proceed()
            val originalPx = (original as? Number)?.toFloat() ?: return@intercept original
            convert((originalPx + offset(resources)).coerceAtLeast(0f))
        }
    }
}
