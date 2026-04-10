package io.github.hyperisland.xposed.renderer

import io.github.hyperisland.xposed.template.core.models.IslandViewModel

data class RendererContext(
    val vm: IslandViewModel,
    val payload: RendererPayload = EmptyRendererPayload,
)
