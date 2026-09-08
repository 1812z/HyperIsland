package io.github.hyperisland.compose.page.settings.appearance

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.hyperisland.R
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import top.yukonga.miuix.kmp.basic.Card

@Composable
internal fun AppearanceSizePage(prefs: FlutterPrefsRepository, onBack: () -> Unit) {
    AppearanceDetailPage(title = stringResource(R.string.appearance_size), onBack = onBack) {
        item {
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
    }
}
