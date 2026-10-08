package com.medhome.nepal.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.medhome.nepal.R
import com.medhome.nepal.data.ThemeMode
import com.medhome.nepal.ui.components.ChoiceOption
import com.medhome.nepal.ui.components.SegmentedChoice

/** System / Light / Dark. Changing it crossfades the whole app (no restart). */
@Composable
fun ThemeSwitcher(modifier: Modifier = Modifier) {
    val controller = LocalThemeController.current
    SegmentedChoice(
        options = listOf(
            ChoiceOption(ThemeMode.SYSTEM, stringResource(R.string.theme_system)),
            ChoiceOption(ThemeMode.LIGHT, stringResource(R.string.theme_light)),
            ChoiceOption(ThemeMode.DARK, stringResource(R.string.theme_dark)),
        ),
        selected = controller.mode,
        onSelect = controller.setMode,
        modifier = modifier,
    )
}
