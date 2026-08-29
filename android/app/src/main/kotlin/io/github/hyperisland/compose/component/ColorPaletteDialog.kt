package io.github.hyperisland.compose.component

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hyperisland.R
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun ColorPaletteDialog(
    show: Boolean,
    title: String,
    initialColor: Color,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onSave: (Color) -> Unit,
) {
    var selectedColor by remember(show, initialColor) { mutableStateOf(initialColor) }
    WindowDialog(show = show, title = title, onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ColorPalette(
                color = selectedColor,
                onColorChanged = { newColor -> selectedColor = newColor },
                modifier = Modifier.fillMaxWidth(),
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
                if (onDelete != null) {
                    TextButton(
                        text = stringResource(R.string.delete),
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                    )
                }
                Button(
                    onClick = { onSave(selectedColor) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}

internal fun parseHexColor(value: String, fallback: Color = Color.Red): Color = runCatching {
    if (!HEX_COLOR.matches(value)) return@runCatching fallback
    Color(AndroidColor.parseColor(value))
}.getOrDefault(fallback)

internal fun Color.toArgbHex(): String = "#%08X".format(toArgb())

private val HEX_COLOR = Regex("^#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")
