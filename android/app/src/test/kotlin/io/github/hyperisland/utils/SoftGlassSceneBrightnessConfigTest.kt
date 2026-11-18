package io.github.hyperisland.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SoftGlassSceneBrightnessConfigTest {
    @Test fun optInIsRequiredEvenOnAWhiteScreen() {
        val scene = SoftGlassSceneBrightnessConfig()
        assertFalse(scene.enabled)
        assertEquals(-28L, scene.whiteSceneBrightness)
        assertNull(scene.reflectionLightenOverride(blackStatusBarText = true))
    }

    @Test fun leavingTheLightSceneAndDisablingBothRestoreTheManualMaterialValue() {
        val scene = SoftGlassSceneBrightnessConfig(enabled = true)
        val manualValues = listOf(-35.0, 0.0, 14.0)
        for (manual in manualValues) {
            fun effective(settings: SoftGlassSceneBrightnessConfig, black: Boolean) =
                settings.reflectionLightenOverride(black) ?: manual
            assertEquals(manual, effective(scene, false), 0.0)
            assertEquals(-28.0, effective(scene, true), 0.0)
            assertEquals(manual, effective(scene, false), 0.0)
            assertEquals(manual, effective(scene.copy(enabled = false), true), 0.0)
        }
    }

    @Test fun changedBrightnessTakesEffectOnlyForBlackStatusBarText() {
        val scene = SoftGlassSceneBrightnessConfig(enabled = true, whiteSceneBrightness = -32L)
        assertEquals(-32.0, scene.reflectionLightenOverride(true)!!, 0.0)
        assertNull(scene.reflectionLightenOverride(false))
        assertEquals(-50.0, scene.copy(whiteSceneBrightness = -500L).reflectionLightenOverride(true)!!, 0.0)
        assertEquals(50.0, scene.copy(whiteSceneBrightness = 500L).reflectionLightenOverride(true)!!, 0.0)
    }
}
