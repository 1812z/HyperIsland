package io.github.hyperisland.compose.page.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.DetailPage
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.component.SettingsAction
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class ReferenceProject(
    val name: String,
    val url: String,
)

@Composable
internal fun ReferencesPage(onBack: () -> Unit) {
    val context = LocalContext.current

    DetailPage(
        title = stringResource(R.string.compose_references),
        onBack = onBack,
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.compose_references_description),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        item {
            SectionTitle(stringResource(R.string.compose_reference_projects))
            Card(modifier = Modifier.fillMaxWidth()) {
                referenceProjects.forEach { project ->
                    SettingsAction(
                        title = project.name,
                        summary = project.url,
                        endIcon = MiuixIcons.Link,
                    ) {
                        context.openUrl(project.url)
                    }
                }
            }
        }
    }
}

private fun Context.openUrl(url: String) {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

private val referenceProjects = listOf(
    ReferenceProject(
        name = "HyperIsland ToolKit",
        url = "https://github.com/D4vidDf/HyperIsland-ToolKit",
    ),
    ReferenceProject(
        name = "libxposed API",
        url = "https://github.com/libxposed/api",
    ),
    ReferenceProject(
        name = "MIUISmoothIsland",
        url = "https://github.com/Leaf-lsgtky/MIUISmoothIsland",
    ),
    ReferenceProject(
        name = "HyperLight",
        url = "https://github.com/KiminonawaResa/HyperLight",
    ),
    ReferenceProject(
        name = "HyperCeiler",
        url = "https://github.com/ReChronoRain/HyperCeiler",
    ),
    ReferenceProject(
        name = "AndroidX Graphics Shapes",
        url = "https://github.com/androidx/androidx/tree/androidx-main/graphics/graphics-shapes",
    ),
    ReferenceProject(
        name = "Flutter",
        url = "https://github.com/flutter/flutter",
    ),
)
