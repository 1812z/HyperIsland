package io.github.hyperisland.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import io.github.hyperisland.R

private val fontAwesomeSolidFamily = FontFamily(Font(R.font.fa_solid_900))
private val fontAwesomeRegularFamily = FontFamily(Font(R.font.fa_regular_400))

enum class FaStyle {
    Solid,
    Regular,
}

enum class FaGlyph(val glyph: String) {
    Times("\uF00D"),
    Check("\uF00C"),
    Heart("\uF004"),
    Tasks("\uF0AE"),
    RedoAlt("\uF2F9"),
}

@Composable
fun FaIcon(
    glyph: FaGlyph,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 20.sp,
    style: FaStyle = FaStyle.Solid,
    tint: Color = LocalContentColor.current,
) {
    val iconModifier = if (contentDescription != null) {
        modifier.semantics { this.contentDescription = contentDescription }
    } else {
        modifier
    }

    Box(
        modifier = iconModifier.clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph.glyph,
            color = tint,
            style = TextStyle(
                fontFamily = when (style) {
                    FaStyle.Solid -> fontAwesomeSolidFamily
                    FaStyle.Regular -> fontAwesomeRegularFamily
                },
                fontSize = fontSize,
                lineHeight = fontSize,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
    }
}
