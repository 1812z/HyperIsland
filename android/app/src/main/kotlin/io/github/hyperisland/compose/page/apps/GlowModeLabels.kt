package io.github.hyperisland.compose.page.apps

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.hyperisland.R

@Composable
internal fun defaultGlowLabel(defaultMode: String): String = when (defaultMode) {
    "on" -> stringResource(R.string.default_on)
    "follow_dynamic" -> stringResource(
        R.string.default_with_value,
        stringResource(R.string.follow_dynamic_color),
    )
    else -> stringResource(R.string.default_off)
}
