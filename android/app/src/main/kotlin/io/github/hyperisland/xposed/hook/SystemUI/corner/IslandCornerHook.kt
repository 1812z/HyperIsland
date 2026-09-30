package io.github.hyperisland.xposed.hook.SystemUI.corner

import android.graphics.Outline
import android.graphics.Rect
import android.view.View
import android.view.ViewOutlineProvider
import io.github.hyperisland.data.IslandCornerPreferences
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.SystemUiReflection.findMethod
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/** Preserves the system provider's geometry and side effects; changes only round-rect radius. */
object IslandCornerHook : BaseHook() {
    override fun getTag() = "HyperIsland[IslandCorner]"

    fun configuredPx(view: View, expanded: Boolean): Float? {
        val key = if (expanded) IslandCornerPreferences.EXPAND else IslandCornerPreferences.ISLAND
        val dp = ConfigManager.getInt(key, -1)
        return if (dp < 0) null else dp.coerceIn(0, 50) * view.resources.displayMetrics.density
    }

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        if (param.packageName != "com.android.systemui") return
        val setter = View::class.java.getDeclaredMethod("setOutlineProvider", ViewOutlineProvider::class.java)
        module.hook(setter).intercept { chain ->
            val view = chain.thisObject as? View ?: return@intercept chain.proceed()
            val provider = chain.args.firstOrNull() as? ViewOutlineProvider
                ?: return@intercept chain.proceed()
            if (provider is CornerProvider) return@intercept chain.proceed()
            val island = runCatching {
                view.javaClass.name.startsWith("miui.systemui.dynamicisland.") ||
                    view.resources.getResourceEntryName(view.id) in setOf(
                        "fake_small_island_view", "fake_big_island_view", "fake_expanded_view")
            }.getOrDefault(false)
            if (!island) return@intercept chain.proceed()
            chain.proceed(arrayOf(CornerProvider(provider)))
        }
    }

    private class CornerProvider(private val original: ViewOutlineProvider) : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            original.getOutline(view, outline)
            runCatching {
                val rect = Rect()
                // Do not replace smooth/custom Path outlines or change alpha/geometry.
                if (!outline.getRect(rect)) return@runCatching
                val id = runCatching { view.resources.getResourceEntryName(view.id) }.getOrDefault("")
                val expanded = if (id == "fake_expanded_view" || id == "expanded_view") true
                    else findMethod(view.javaClass, "getState")?.invoke(view)?.javaClass?.simpleName
                        ?.let { it.contains("Expanded") || it == "Expanded" } == true
                val radius = configuredPx(view, expanded) ?: return@runCatching
                outline.setRoundRect(rect, radius.coerceAtMost(minOf(rect.width(), rect.height()) / 2f))
            }
        }
    }
}
