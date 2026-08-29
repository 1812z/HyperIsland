package io.github.hyperisland.compose.component.keepisland

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.hyperisland.compose.component.SectionTitle
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.window.WindowBottomSheet

internal data class PlaceholderGroup(
    val title: String,
    val items: List<PlaceholderItem>,
)

internal data class PlaceholderItem(
    val label: String,
    val value: String,
)

@Composable
internal fun KeepIslandPlaceholderSheet(
    show: Boolean,
    title: String,
    groups: List<PlaceholderGroup>,
    onDismiss: () -> Unit,
    onSelect: (PlaceholderItem) -> Unit,
) {
    WindowBottomSheet(
        show = show,
        title = title,
        onDismissRequest = onDismiss,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            groups.forEach { group ->
                item { SectionTitle(group.title) }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        group.items.forEach { placeholder ->
                            BasicComponent(
                                title = placeholder.label,
                                onClick = { onSelect(placeholder) },
                            )
                        }
                    }
                }
            }
        }
    }
}
