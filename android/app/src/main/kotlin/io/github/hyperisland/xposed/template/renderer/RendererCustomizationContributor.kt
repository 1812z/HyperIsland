package io.github.hyperisland.xposed.renderer

import io.github.hyperisland.xposed.template.core.contracts.IslandTemplate
import io.github.hyperisland.xposed.template.core.customization.FocusCustomizationApplyEnv
import io.github.hyperisland.xposed.template.core.customization.FocusCustomizationFieldSpec
import org.json.JSONObject

interface RendererCustomizationContributor {
    val fields: List<FocusCustomizationFieldSpec>

    fun buildPayload(
        config: JSONObject,
        template: IslandTemplate?,
        env: FocusCustomizationApplyEnv,
    ): RendererPayload
}
