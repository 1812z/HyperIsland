package io.github.hyperisland.compose.page.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.AppCard
import io.github.hyperisland.compose.component.DetailPage
import io.github.hyperisland.compose.component.PreferenceSlider
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.theme.AppWallpaperImage
import io.github.hyperisland.compose.theme.AppWallpaperState
import io.github.hyperisland.compose.theme.LocalAppWallpaper
import io.github.hyperisland.compose.theme.MAX_WALLPAPER_BLUR_RADIUS
import io.github.hyperisland.compose.theme.MAX_WALLPAPER_BRIGHTNESS
import io.github.hyperisland.compose.theme.PREF_WALLPAPER_BLUR_RADIUS
import io.github.hyperisland.compose.theme.PREF_WALLPAPER_BRIGHTNESS
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Image

@Composable
internal fun WallpaperSettingsPage(prefs: FlutterPrefsRepository, onBack: () -> Unit) {
    val wallpaper = LocalAppWallpaper.current ?: return
    val context = LocalContext.current.applicationContext
    val onFailure = {
        Toast.makeText(context, R.string.wallpaper_update_failed, Toast.LENGTH_SHORT).show()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) wallpaper.replace(uri, onFailure)
    }

    DetailPage(title = stringResource(R.string.wallpaper), onBack = onBack) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WallpaperPickerCard(
                    wallpaper = wallpaper,
                    onClick = { picker.launch(arrayOf("image/*")) },
                )
                if (wallpaper.image != null) {
                    TextButton(
                        text = stringResource(R.string.wallpaper_remove),
                        onClick = { wallpaper.replace(null, onFailure) },
                        enabled = !wallpaper.busy,
                    )
                }
            }
        }
        item {
            val blurRadius = wallpaper.blurRadius.coerceIn(0, MAX_WALLPAPER_BLUR_RADIUS)
            val brightness = wallpaper.brightness.coerceIn(0, MAX_WALLPAPER_BRIGHTNESS)
            AppCard(modifier = Modifier.fillMaxWidth()) {
                PreferenceSlider(
                    title = stringResource(R.string.wallpaper_blur),
                    icon = null,
                    value = blurRadius.toFloat(),
                    valueText = "${blurRadius} dp",
                    valueRange = 0f..MAX_WALLPAPER_BLUR_RADIUS.toFloat(),
                    steps = MAX_WALLPAPER_BLUR_RADIUS.toInt() - 1,
                    onValueChange = { wallpaper.blurRadius = it.roundToInt().toLong() },
                    onValueChangeFinished = { prefs.putLong(PREF_WALLPAPER_BLUR_RADIUS, wallpaper.blurRadius) },
                )
                PreferenceSlider(
                    title = stringResource(R.string.wallpaper_brightness),
                    icon = null,
                    value = brightness.toFloat(),
                    valueText = "${brightness}%",
                    valueRange = 0f..MAX_WALLPAPER_BRIGHTNESS.toFloat(),
                    steps = MAX_WALLPAPER_BRIGHTNESS.toInt() - 1,
                    onValueChange = { wallpaper.brightness = it.roundToInt().toLong() },
                    onValueChangeFinished = { prefs.putLong(PREF_WALLPAPER_BRIGHTNESS, wallpaper.brightness) },
                )
            }
        }
    }
}

@Composable
private fun WallpaperPickerCard(wallpaper: AppWallpaperState, onClick: () -> Unit) {
    val pickerLabel = stringResource(R.string.wallpaper_choose_image)
    val image = wallpaper.image
    val imageRatio = image?.let { it.width.toFloat() / it.height } ?: 1f
    Card(
        modifier = Modifier
            .widthIn(max = 320.dp)
            .heightIn(max = 320.dp)
            .aspectRatio(imageRatio)
            .semantics { if (image != null) contentDescription = pickerLabel },
        insideMargin = PaddingValues(0.dp),
        showIndication = true,
        onClick = onClick.takeUnless { wallpaper.busy },
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (image == null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(MiuixIcons.Image, contentDescription = null, modifier = Modifier.size(32.dp))
                    Text(pickerLabel)
                }
            } else {
                AppWallpaperImage(wallpaper, Modifier.matchParentSize())
            }
        }
    }
}
