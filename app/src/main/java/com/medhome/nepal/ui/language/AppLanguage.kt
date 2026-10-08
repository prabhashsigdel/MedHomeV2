package com.medhome.nepal.ui.language

import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** The languages the app ships strings for. [tag] is the BCP 47 language tag. */
enum class AppLanguage(val tag: String) {
    ENGLISH("en"),
    NEPALI("ne"),
    ;

    companion object {
        /**
         * The language the picker should show as selected: the per-app choice if one was made,
         * otherwise the phone's language when the app supports it, otherwise English.
         */
        fun resolve(appLanguageTag: String?, systemLanguageTag: String?): AppLanguage =
            fromTag(appLanguageTag) ?: fromTag(systemLanguageTag) ?: ENGLISH

        private fun fromTag(tag: String?): AppLanguage? {
            val language = tag?.substringBefore('-')?.lowercase() ?: return null
            return entries.firstOrNull { it.tag == language }
        }
    }
}

/**
 * Per-app language preference via AppCompatDelegate: stored by the system on Android 13+ and by
 * AppCompat's AppLocalesMetadataHolderService below that, so it survives restarts everywhere.
 */
object LanguageSettings {

    fun current(configuration: Configuration): AppLanguage {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        val appTag = if (appLocales.isEmpty) null else appLocales[0]?.language
        val systemTag = configuration.locales.takeIf { !it.isEmpty }?.get(0)?.language
        return AppLanguage.resolve(appTag, systemTag)
    }

    /**
     * Switches the app language. On Android 13+ the activity handles the change in place
     * (manifest configChanges); below that MainActivity restarts itself. Call it through
     * [LocalLanguageController] so the switch is covered by a fade.
     */
    fun apply(language: AppLanguage) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
    }
}
