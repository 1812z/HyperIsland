package io.github.hyperisland.xposed.hook

import android.util.TypedValue
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.utils.ResourceDimenHook
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/** Replaces SystemUI's shared island height dimension. */
object IslandDimenHook : BaseHook() {
    private const val TAG = "HyperIsland[islandDimen]"
    private const val KEY_HEIGHT = "pref_island_height"
    private const val HEIGHT_RESOURCE = "island_height"
    private const val DEFAULT_HEIGHT_DP = 34.0

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        ResourceDimenHook.register(module, HEIGHT_RESOURCE) { resources ->
            val configuredHeight = ConfigManager.getDouble(KEY_HEIGHT, 0.0)
            val heightDp = configuredHeight.takeIf { it > 0.0 } ?: DEFAULT_HEIGHT_DP
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                heightDp.coerceAtMost(100.0).toFloat(),
                resources.displayMetrics,
            )
        }
    }
}
