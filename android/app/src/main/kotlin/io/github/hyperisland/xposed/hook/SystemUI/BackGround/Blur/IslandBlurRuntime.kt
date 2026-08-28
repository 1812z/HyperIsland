package io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur

import io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.config.IslandMaterialConfigStore
import io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.nativeblur.NativeBlurRenderer
import io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.nativeblur.OuterBlurRegistry
import io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.transition.TransitionBlurController

/** Shared renderer graph used by the hook entry and transition visual hook. */
internal object IslandBlurRuntime {
    val configStore = IslandMaterialConfigStore()
    val nativeBlurRenderer = NativeBlurRenderer(configStore)
    val outerBlurRegistry = OuterBlurRegistry(configStore, nativeBlurRenderer)
    val transitionBlurController = TransitionBlurController(configStore, nativeBlurRenderer)
}
