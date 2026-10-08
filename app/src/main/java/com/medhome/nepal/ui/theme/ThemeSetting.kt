package com.medhome.nepal.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.medhome.nepal.R
import com.medhome.nepal.data.ThemeMode
import com.medhome.nepal.ui.components.ChoiceOption
import com.medhome.nepal.ui.components.GlassChoiceSheet
import com.medhome.nepal.ui.components.SettingsValueRow

/**
 * "Theme  Dark  >" row; tapping it opens a sheet with System / Light / Dark. The choice applies
 * on tap, so the whole app crossfades while the sheet slides away (no restart).
 */
@Composable
fun ThemeSetting(enabled: Boolean = true) {
    val controller = LocalThemeController.current
    var showSheet by rememberSaveable { mutableStateOf(false) }
    val options = listOf(
        ChoiceOption(ThemeMode.SYSTEM, stringResource(R.string.theme_system)),
        ChoiceOption(ThemeMode.LIGHT, stringResource(R.string.theme_light)),
        ChoiceOption(ThemeMode.DARK, stringResource(R.string.theme_dark)),
    )
    SettingsValueRow(
        label = R.string.settings_theme,
        value = options.first { it.value == controller.mode }.label,
        onClick = { showSheet = true },
        enabled = enabled,
    )
    if (showSheet) {
        GlassChoiceSheet(
            title = R.string.settings_theme,
            options = options,
            selected = controller.mode,
            onSelect = controller.setMode,
            onDismissRequest = { showSheet = false },
        )
    }
}
