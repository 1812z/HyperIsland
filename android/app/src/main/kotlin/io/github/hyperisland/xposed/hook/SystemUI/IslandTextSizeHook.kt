package io.github.hyperisland.xposed.hook.SystemUI

import android.widget.TextView
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.Collections
import java.util.WeakHashMap

/** Applies the configured size after SystemUI binds its island text views. */
object IslandTextSizeHook : BaseHook() {
    private const val TAG = "HyperIsland[IslandTextSize]"
    private const val KEY_TEXT_SCALE = "pref_island_text_scale"
    private const val DEFAULT_TEXT_SCALE = 100
    private const val TITLE_TEXT_SIZE_DP = 14f
    private const val CONTENT_TEXT_SIZE_DP = 11f

    private val holderClassNames = arrayOf(
        "miui.systemui.dynamicisland.module.IslandTextViewHolder",
        "miui.systemui.dynamicisland.module.IslandRightTextViewHolder",
    )
    private val textFieldNames = arrayOf("title", "frontTitle", "content")
    private val hookedClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>()),
    )
    private val trackedHolders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Any, Boolean>()),
    )

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { classLoader ->
            hookHolderClasses(module, classLoader)
        }
    }

    override fun onConfigChanged() {
        val holders = synchronized(trackedHolders) { trackedHolders.toList() }
        holders.forEach(::applyTextSize)
    }

    private fun hookHolderClasses(module: XposedModule, classLoader: ClassLoader) {
        for (className in holderClassNames) {
            try {
                val clazz = classLoader.loadClass(className)
                if (!hookedClasses.add(clazz)) continue
                clazz.declaredMethods
                    .filter { it.name in setOf("bind", "updatePartial") }
                    .forEach { method ->
                        module.hook(method).intercept { chain ->
                            val result = chain.proceed()
                            chain.thisObject?.let { holder ->
                                trackedHolders += holder
                                applyTextSize(holder)
                            }
                            result
                        }
                    }
                log(module, "hooked island text size on $className")
            } catch (_: ClassNotFoundException) {
            } catch (e: Throwable) {
                logError(module, "failed to hook $className: ${e.message}")
            }
        }
    }

    private fun applyTextSize(holder: Any) {
        val textScale = ConfigManager.getInt(KEY_TEXT_SCALE, DEFAULT_TEXT_SCALE)
            .coerceIn(10, 200) / 100f
        textFieldNames.forEach { fieldName ->
            val textView = runCatching {
                holder.javaClass.getDeclaredField(fieldName).apply { isAccessible = true }
                    .get(holder) as? TextView
            }.getOrNull() ?: return@forEach
            textView.post {
                val baseSizeDp = if (fieldName == "title") TITLE_TEXT_SIZE_DP else CONTENT_TEXT_SIZE_DP
                val targetPx = baseSizeDp * textScale * textView.resources.displayMetrics.density
                if (kotlin.math.abs(textView.textSize - targetPx) < 0.5f) return@post
                textView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, targetPx)
                textView.requestLayout()
            }
        }
    }
}
