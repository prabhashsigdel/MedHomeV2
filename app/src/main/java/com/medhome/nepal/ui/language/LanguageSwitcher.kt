package com.medhome.nepal.ui.language

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.medhome.nepal.R
import com.medhome.nepal.ui.components.ChoiceOption
import com.medhome.nepal.ui.components.SegmentedChoice

/**
 * English / नेपाली choice. Each option is labelled in its own language so it's recognisable
 * whichever language is active. Choosing one recreates the activity in that language.
 */
@Composable
fun LanguageSwitcher(modifier: Modifier = Modifier) {
    val configuration = LocalConfiguration.current
    val selected = remember(configuration) { LanguageSettings.current(configuration) }
    SegmentedChoice(
        options = listOf(
            ChoiceOption(AppLanguage.ENGLISH, stringResource(R.string.language_english)),
            ChoiceOption(AppLanguage.NEPALI, stringResource(R.string.language_nepali)),
        ),
        selected = selected,
        onSelect = LanguageSettings::apply,
        modifier = modifier,
    )
}
