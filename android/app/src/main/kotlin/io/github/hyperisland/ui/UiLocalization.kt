package io.github.hyperisland.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration

enum class UiLanguage {
    Chinese,
    English,
}

val LocalUiLanguage = compositionLocalOf { UiLanguage.Chinese }

@Composable
fun rememberUiLanguage(localeTag: String?): UiLanguage {
    val configuration = LocalConfiguration.current
    val systemLanguage = configuration.locales[0]?.language.orEmpty()
    return remember(localeTag, systemLanguage) {
        when (localeTag ?: systemLanguage) {
            "en" -> UiLanguage.English
            else -> UiLanguage.Chinese
        }
    }
}

@Composable
fun textOf(chinese: String, english: String): String {
    return textOf(LocalUiLanguage.current, chinese, english)
}

fun textOf(language: UiLanguage, chinese: String, english: String): String {
    return when (language) {
        UiLanguage.English -> english
        UiLanguage.Chinese -> chinese
    }
}
