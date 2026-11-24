package io.github.hyperisland.compose.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import io.github.hyperisland.compose.theme.LocalCardGlassSettings
import io.github.hyperisland.compose.theme.isGlassEnabled
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonColors
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextButtonColors
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldColors
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonDefaults
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.glass.GlassTextField
import top.yukonga.miuix.kmp.glass.GlassTextFieldDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

// Null denotes page content; a solid modal explicitly provides false.
internal val LocalModalGlassEnabled = staticCompositionLocalOf<Boolean?> { null }

@Composable
private fun controlGlassEnabled(): Boolean =
    LocalModalGlassEnabled.current ?: isGlassEnabled(LocalCardGlassSettings.current)

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
        GlassTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            colors = colors?.copy(backgroundColor = glassColors.backgroundColor) ?: glassColors,
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
        GlassDialogButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            role = role,
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
