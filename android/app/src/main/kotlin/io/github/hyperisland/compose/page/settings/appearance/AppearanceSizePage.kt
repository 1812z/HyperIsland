package io.github.hyperisland.compose.page.settings.appearance

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.hyperisland.R
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.data.IslandCornerPreferences
import io.github.hyperisland.data.ExpandedCollapsePreferences as Keys
import top.yukonga.miuix.kmp.basic.Card

@Composable
internal fun AppearanceSizePage(prefs: FlutterPrefsRepository, onBack: () -> Unit) {
    AppearanceDetailPage(title = stringResource(R.string.appearance_size), onBack = onBack) {
        item {
            SectionTitle(stringResource(R.string.island_position_size))
            Card(modifier = Modifier.fillMaxWidth()) {
                DoublePreferenceSlider(prefs, KEY_ISLAND_HEIGHT, R.string.island_height, 0.0, 100.0, 0.0)
                DoublePreferenceSlider(prefs, KEY_ISLAND_TOP_OFFSET, R.string.vertical_position, -40.0, 50.0, 0.0)
                LongPreferenceSlider(prefs, KEY_BIG_MAX_WIDTH, R.string.big_max_width, 0, 500, 0, 5)
                LongPreferenceSlider(prefs, KEY_BIG_MIN_WIDTH, R.string.big_min_width, 0, 500, 0, 5)
                LongPreferenceSlider(
                    prefs,
                    KEY_SMALL_WIDTH,
                    R.string.small_island_width,
                    1,
                    100,
                    34,
                    followSystemAtDefault = true,
                )
                LongPreferenceSlider(prefs, KEY_SMALL_OFFSET, R.string.small_island_offset, -10, 50, 0)
            }
        }
        item {
            SectionTitle(stringResource(R.string.expand_layout_section))
            Card(modifier = Modifier.fillMaxWidth()) {
                LongPreferenceSlider(prefs, Keys.TOP_GAP, R.string.expand_layout_top_gap,
                    -1, 30, Keys.DEFAULT_TOP_GAP, followSystemAtDefault = true)
                LongPreferenceSlider(prefs, Keys.CONTENT_TOP_GAP, R.string.expand_layout_content_top_gap,
                    0, 20, Keys.DEFAULT_CONTENT_TOP_GAP, showDefaultAsSystem = false)
            }
        }
        item {
            SectionTitle(stringResource(R.string.island_corner_section))
            Card(modifier = Modifier.fillMaxWidth()) {
                LongPreferenceSlider(prefs, IslandCornerPreferences.ISLAND,
                    R.string.island_corner_radius, -1, 50, IslandCornerPreferences.SYSTEM,
                    followSystemAtDefault = true)
                LongPreferenceSlider(prefs, IslandCornerPreferences.EXPAND,
                    R.string.expand_corner_radius, -1, 50, IslandCornerPreferences.SYSTEM,
                    followSystemAtDefault = true)
            }
        }
    }
}
