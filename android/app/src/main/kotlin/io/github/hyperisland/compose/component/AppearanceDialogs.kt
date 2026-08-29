package io.github.hyperisland.compose.component

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hyperisland.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import java.io.File

@Composable
internal fun BackgroundPickerDialog(
    show: Boolean,
    title: String,
    currentPath: String,
    selectedUri: Uri?,
    onChoose: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val context = LocalContext.current
    var preview by remember(show, currentPath, selectedUri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(show, currentPath, selectedUri) {
        preview = if (show) loadPreview(context, selectedUri, currentPath) else null
    }
    WindowDialog(show = show, title = title, onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 190.dp)
                    .aspectRatio(16f / 8.5f)
                    .clickable(onClick = onChoose),
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (preview != null) {
                        Image(
                            bitmap = preview!!,
                            contentDescription = title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.click_select_file),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.delete),
                    enabled = currentPath.isNotBlank(),
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = onSave,
                    enabled = selectedUri != null,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}

@Composable
internal fun GlassSamplingDialog(
    show: Boolean,
    initialFps: Int,
    initialQuality: Int,
    onDismiss: () -> Unit,
    onSave: (fps: Int, quality: Int) -> Unit,
) {
    var fps by remember(show, initialFps) { mutableIntStateOf(initialFps) }
    var quality by remember(show, initialQuality) { mutableIntStateOf(initialQuality) }
    WindowDialog(
        show = show,
        title = stringResource(R.string.glass_sampling_settings),
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            SamplingSlider(
                title = stringResource(R.string.glass_sampling_fps),
                valueText = stringResource(R.string.fps_value, fps),
                value = fps.toFloat(),
                defaultValue = DEFAULT_SAMPLING_FPS,
                range = 1f..90f,
                steps = 88,
                onValueChange = { fps = it.toInt() },
                onReset = { fps = DEFAULT_SAMPLING_FPS },
            )
            SamplingSlider(
                title = stringResource(R.string.glass_sampling_quality),
                valueText = stringResource(R.string.percent_value, quality),
                value = quality.toFloat(),
                defaultValue = DEFAULT_SAMPLING_QUALITY,
                range = 10f..100f,
                steps = 17,
                onValueChange = { quality = it.toInt() },
                onReset = { quality = DEFAULT_SAMPLING_QUALITY },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { onSave(fps, quality) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}

@Composable
private fun SamplingSlider(
    title: String,
    valueText: String,
    value: Float,
    defaultValue: Int,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    onReset: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = title, modifier = Modifier.weight(1f))
            Text(text = valueText, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            SliderResetAction(
                visible = value.toInt() != defaultValue,
                alignToSliderEnd = false,
                onClick = onReset,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private const val DEFAULT_SAMPLING_FPS = 20
private const val DEFAULT_SAMPLING_QUALITY = 30

private suspend fun loadPreview(context: Context, uri: Uri?, path: String): ImageBitmap? =
    withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = if (uri != null) {
                context.contentResolver.openInputStream(uri).use(BitmapFactory::decodeStream)
            } else {
                BitmapFactory.decodeFile(File(path).path)
            }
            bitmap?.asImageBitmap()
        }.getOrNull()
    }
