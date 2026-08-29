package io.github.hyperisland.compose.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hyperisland.R
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun KeywordListDialog(
    show: Boolean,
    title: String,
    keywords: List<String>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    var draft by remember(show, keywords) { mutableStateOf(keywords.distinct()) }
    var input by remember(show) { mutableStateOf("") }

    fun addKeyword() {
        val keyword = input.trim()
        if (keyword.isNotEmpty() && keyword !in draft) draft = draft + keyword
        input = ""
    }

    WindowDialog(show = show, title = title, onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.compose_keyword_hint),
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                )
                Button(
                    onClick = ::addKeyword,
                    enabled = input.trim().isNotEmpty(),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(stringResource(R.string.compose_add_keyword))
                }
            }
            if (draft.isNotEmpty()) {
                Card {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                        items(draft, key = { it }) { keyword ->
                            BasicComponent(
                                title = keyword,
                                endActions = {
                                    IconButton(onClick = { draft = draft - keyword }) {
                                        Icon(
                                            MiuixIcons.Close,
                                            stringResource(R.string.compose_remove_keyword),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.compose_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { onSave(draft) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(stringResource(R.string.compose_save))
                }
            }
        }
    }
}
