package com.medhome.nepal.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.medhome.nepal.data.ThemeMode

/** The current appearance choice and how to change it. Provided by MainActivity. */
@Immutable
class ThemeController(
    val mode: ThemeMode,
    val setMode: (ThemeMode) -> Unit,
)

val LocalThemeController = staticCompositionLocalOf { ThemeController(ThemeMode.DEFAULT) {} }
