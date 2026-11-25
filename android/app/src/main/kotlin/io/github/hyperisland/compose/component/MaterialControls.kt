package io.github.hyperisland.compose.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import io.github.hyperisland.compose.theme.LocalCardGlassSettings
import io.github.hyperisland.compose.theme.LocalUiGlassSettings
import io.github.hyperisland.compose.theme.isGlassEnabled
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonColors
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextButtonColors
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldColors
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.glass.GlassCardDefaults
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonDefaults
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.glass.GlassTextField
import top.yukonga.miuix.kmp.glass.GlassTextFieldDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

// Null denotes page content; a solid modal explicitly provides false.
internal val LocalModalGlassEnabled = staticCompositionLocalOf<Boolean?> { null }

private const val ModalControlTransparencyRatio = 0.90f
private const val ModalControlMinAlpha = 1f - ModalControlTransparencyRatio

@Composable
private fun modalControlTintStrength(): Float {
    if (LocalModalGlassEnabled.current != true) return 1f
    val panelTransparency = 1f - LocalUiGlassSettings.current.tintStrength
    return 1f - panelTransparency * ModalControlTransparencyRatio
}

@Composable
internal fun modalCardFill(): Color {
    val fill = GlassCardDefaults.color()
    return fill.copy(alpha = fill.alpha * modalControlTintStrength())
}

@Composable
private fun modalInteractiveFill(color: Color): Color {
    if (LocalModalGlassEnabled.current != true) return color
    val tintStrength = modalControlTintStrength()
    // Keep the actual fill visible even when the library's default is already translucent.
    return color.copy(alpha = ModalControlMinAlpha + (tintStrength - ModalControlMinAlpha) * color.alpha)
}

@Composable
private fun modalButtonFill(
    color: Color,
    role: GlassDialogButtonRole = GlassDialogButtonRole.Normal,
): Color {
    if (LocalModalGlassEnabled.current != true) return color
    return if (role == GlassDialogButtonRole.Primary) {
        modalInteractiveFill(color)
    } else {
        modalCardFill()
    }
}

@Composable
private fun controlGlassEnabled(): Boolean =
    LocalModalGlassEnabled.current ?: isGlassEnabled(LocalCardGlassSettings.current)

@Composable
internal fun AppModalIconButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val backgroundColor = if (LocalModalGlassEnabled.current == true) {
        modalCardFill()
    } else {
        Color.Unspecified
    }
    IconButton(onClick = onClick, enabled = enabled, backgroundColor = backgroundColor, content = content)
}

@Composable
internal fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    colors: TextFieldColors? = null,
    label: String = "",
    useLabelAsPlaceholder: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    trailingIcon: (@Composable () -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    if (controlGlassEnabled()) {
        val glassColors = GlassTextFieldDefaults.textFieldColors()
        val fill = modalInteractiveFill(glassColors.backgroundColor)
        GlassTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            colors = (colors ?: glassColors).copy(backgroundColor = fill),
            label = label,
            useLabelAsPlaceholder = useLabelAsPlaceholder,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            trailingIcon = trailingIcon,
            visualTransformation = visualTransformation,
        )
    } else {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            colors = colors ?: TextFieldDefaults.textFieldColors(),
            label = label,
            useLabelAsPlaceholder = useLabelAsPlaceholder,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            trailingIcon = trailingIcon,
            visualTransformation = visualTransformation,
        )
    }
}

@Composable
internal fun AppActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors? = null,
    cornerRadius: Dp? = null,
    minWidth: Dp? = null,
    minHeight: Dp? = null,
    insideMargin: PaddingValues? = null,
    glassEnabled: Boolean = controlGlassEnabled(),
    content: @Composable RowScope.() -> Unit,
) {
    if (glassEnabled) {
        val scheme = MiuixTheme.colorScheme
        val role = when {
            colors?.contentColor == scheme.error -> GlassDialogButtonRole.Destructive
            colors?.color == scheme.primary -> GlassDialogButtonRole.Primary
            else -> GlassDialogButtonRole.Normal
        }
        val glassColors = GlassDialogButtonDefaults.buttonColors(role)
        GlassDialogButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            role = role,
            colors = glassColors.copy(
                color = modalButtonFill(glassColors.color, role),
                disabledColor = modalButtonFill(glassColors.disabledColor, role),
            ),
            cornerRadius = cornerRadius ?: GlassDialogButtonDefaults.CornerRadius,
            minWidth = minWidth ?: GlassDialogButtonDefaults.MinWidth,
            minHeight = minHeight ?: GlassDialogButtonDefaults.MinHeight,
            insideMargin = insideMargin ?: GlassDialogButtonDefaults.InsideMargin,
            content = content,
        )
    } else {
        Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = colors ?: ButtonDefaults.buttonColors(),
            cornerRadius = cornerRadius ?: ButtonDefaults.CornerRadius,
            minWidth = minWidth ?: ButtonDefaults.MinWidth,
            minHeight = minHeight ?: ButtonDefaults.MinHeight,
            insideMargin = insideMargin ?: ButtonDefaults.InsideMargin,
            content = content,
        )
    }
}

@Composable
internal fun AppActionTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: TextButtonColors? = null,
    glassEnabled: Boolean = controlGlassEnabled(),
) {
    if (glassEnabled) {
        AppActionButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = colors?.let { ButtonColors(it.color, it.disabledColor, it.textColor, it.disabledTextColor) },
            glassEnabled = true,
        ) {
            Text(text, style = MiuixTheme.textStyles.button)
        }
    } else {
        TextButton(
            text = text,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = colors ?: ButtonDefaults.textButtonColors(),
        )
    }
}
