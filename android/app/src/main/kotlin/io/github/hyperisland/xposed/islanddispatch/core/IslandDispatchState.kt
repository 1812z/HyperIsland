package io.github.hyperisland.xposed.islanddispatch.core

import androidx.collection.ArraySet
import io.github.libxposed.api.XposedModule

internal object IslandDispatchState {
    @Volatile
    var registered: Boolean = false

    @Volatile
    var module: XposedModule? = null

    val postedIds: ArraySet<Int> = ArraySet()
}
