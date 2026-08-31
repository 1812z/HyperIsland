package io.github.hyperisland

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList

internal object AppLocaleController {
    fun currentLanguageTag(context: Context): String {
        val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
        return locales.takeUnless { it.isEmpty }?.get(0)?.toLanguageTag().orEmpty()
    }

    fun apply(context: Context, languageTag: String) {
        val localeManager = context.getSystemService(LocaleManager::class.java)
        val locales = languageTag.takeIf(String::isNotBlank)
            ?.let(LocaleList::forLanguageTags)
            ?: LocaleList.getEmptyLocaleList()
        if (localeManager.applicationLocales.toLanguageTags() != locales.toLanguageTags()) {
            localeManager.applicationLocales = locales
        }
    }
}
