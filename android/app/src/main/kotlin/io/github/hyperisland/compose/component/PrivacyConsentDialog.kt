package io.github.hyperisland.compose.component

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hyperisland.R
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun PrivacyConsentDialog(
    show: Boolean,
    onViewPolicy: () -> Unit,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    WindowDialog(
        show = show,
        title = stringResource(R.string.privacy_consent_title),
        onDismissRequest = onReject,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PrivacyPolicyMessage(onViewPolicy = onViewPolicy)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                top.yukonga.miuix.kmp.basic.TextButton(
                    text = stringResource(R.string.privacy_consent_decline),
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(stringResource(R.string.privacy_consent_accept))
                }
            }
        }
    }
}

@Composable
internal fun PrivacyPolicyMessage(
    onViewPolicy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val policyText = stringResource(R.string.privacy_policy_name)
    val message = stringResource(R.string.privacy_consent_message, policyText)
    val linkColor = MiuixTheme.colorScheme.primary
    val annotatedMessage = buildAnnotatedString {
        val linkStart = message.indexOf(policyText)
        if (linkStart < 0) {
            append(message)
            return@buildAnnotatedString
        }
        append(message.substring(0, linkStart))
        withLink(
            LinkAnnotation.Clickable(
                tag = "privacy_policy",
                styles = TextLinkStyles(style = SpanStyle(color = linkColor)),
                linkInteractionListener = { onViewPolicy() },
            ),
        ) {
            append(policyText)
        }
        append(message.substring(linkStart + policyText.length))
    }
    BasicText(
        text = annotatedMessage,
        modifier = modifier,
        style = MiuixTheme.textStyles.body1.copy(
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        ),
    )
}
