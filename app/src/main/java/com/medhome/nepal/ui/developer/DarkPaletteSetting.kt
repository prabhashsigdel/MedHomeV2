package com.medhome.nepal.ui.developer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import com.medhome.nepal.R
import com.medhome.nepal.data.DarkPalette
import com.medhome.nepal.ui.components.ChoiceOption
import com.medhome.nepal.ui.components.GlassChoiceSheet
import com.medhome.nepal.ui.components.SettingsValueRow

/** The dark theme's palette and how to change it. Provided by MainActivity. */
@Immutable
class DarkPaletteController(
    val palette: DarkPalette,
    val setPalette: (DarkPalette) -> Unit,
)

val LocalDarkPaletteController = staticCompositionLocalOf { DarkPaletteController(DarkPalette.DEFAULT) {} }

/**
 * Developer builds only (BuildConfig.DEVELOPER_OPTIONS): "Dark palette  Warm dusk  >", opening
 * Warm dusk / Midnight aurora, to compare the dark backgrounds on a device. Applies on tap, so
 * the background crossfades while the sheet slides away.
 */
@Composable
fun DarkPaletteSetting(enabled: Boolean = true) {
    val controller = LocalDarkPaletteController.current
    var showSheet by rememberSaveable { mutableStateOf(false) }
    val options = listOf(
        ChoiceOption(DarkPalette.WARM_DUSK, stringResource(R.string.dark_palette_warm_dusk)),
        ChoiceOption(DarkPalette.MIDNIGHT_AURORA, stringResource(R.string.dark_palette_midnight_aurora)),
    )
    SettingsValueRow(
        label = R.string.settings_dark_palette,
        value = options.first { it.value == controller.palette }.label,
        onClick = { showSheet = true },
        enabled = enabled,
    )
    if (showSheet) {
        GlassChoiceSheet(
            title = R.string.settings_dark_palette,
            options = options,
            selected = controller.palette,
            onSelect = controller.setPalette,
            onDismissRequest = { showSheet = false },
        )
    }
}
