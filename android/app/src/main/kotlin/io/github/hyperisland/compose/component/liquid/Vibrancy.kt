// Adapted from the compose-miuix-ui IosLiquidGlassNavigationBar example (Apache-2.0).
package io.github.hyperisland.compose.component.liquid

import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.colorControls

internal fun BackdropEffectScope.vibrancy() {
    colorControls(brightness = 0f, contrast = 1f, saturation = 1.5f)
}
