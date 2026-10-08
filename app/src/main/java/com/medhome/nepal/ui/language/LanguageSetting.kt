package com.medhome.nepal.ui.language

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.medhome.nepal.R
import com.medhome.nepal.ui.components.ChoiceOption
import com.medhome.nepal.ui.components.GlassChoiceSheet
import com.medhome.nepal.ui.components.SettingsValueRow

/**
 * "Language  नेपाली  >" row; tapping it opens a sheet with English / नेपाली, each written in its
 * own language so it's recognisable whichever is active. The switch starts once the sheet has
 * closed, through [LocalLanguageController] (see [LanguageSwitchHost]).
 */
@Composable
fun LanguageSetting(enabled: Boolean = true) {
    val configuration = LocalConfiguration.current
    val selected = remember(configuration) { LanguageSettings.current(configuration) }
    val controller = LocalLanguageController.current
    var showSheet by rememberSaveable { mutableStateOf(false) }
    val options = listOf(
        ChoiceOption(AppLanguage.ENGLISH, stringResource(R.string.language_english)),
        ChoiceOption(AppLanguage.NEPALI, stringResource(R.string.language_nepali)),
    )
    SettingsValueRow(
        label = R.string.profile_language,
        value = options.first { it.value == selected }.label,
        onClick = { showSheet = true },
        enabled = enabled,
    )
    if (showSheet) {
        GlassChoiceSheet(
            title = R.string.profile_language,
            options = options,
            selected = selected,
            onSelect = controller.apply,
            onDismissRequest = { showSheet = false },
            applyAfterClose = true,
        )
    }
}
