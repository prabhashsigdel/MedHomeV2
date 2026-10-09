package com.medhome.nepal

import android.app.UiModeManager
import android.content.res.Configuration
import android.graphics.Color
import androidx.core.graphics.drawable.toDrawable
import androidx.core.os.LocaleListCompat
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.medhome.nepal.data.DarkPalette
import com.medhome.nepal.data.ThemeMode
import com.medhome.nepal.ui.MedHomeNavHost
import com.medhome.nepal.ui.common.LocalCredentialClient
import com.medhome.nepal.ui.developer.DarkPaletteController
import com.medhome.nepal.ui.developer.LocalDarkPaletteController
import com.medhome.nepal.ui.language.LanguageSwitchHost
import com.medhome.nepal.ui.theme.LocalThemeController
import com.medhome.nepal.ui.theme.MedHomeTheme
import com.medhome.nepal.ui.theme.ThemeController
import com.medhome.nepal.ui.theme.glassColorsFor
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * AppCompatActivity so per-app language (AppCompatDelegate) also works on Android 12 and lower.
 * Handles uiMode changes itself (manifest), so a theme switch crossfades instead of restarting,
 * and locale changes, so a language switch on Android 13+ recomposes in place.
 */
class MainActivity : AppCompatActivity() {
    /** Set just before a language restart, so the new activity fades in. */
    private var restartingForLanguage = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val fadeInOnStart = savedInstanceState?.getBoolean(KEY_LANGUAGE_RESTART) == true
        val container = (application as MedHomeApplication).container
        // One small read before the first frame, so that frame is already in the chosen theme.
        val initialMode = runBlocking { container.themeSettings.current() }
        applyBarStyle(dark = initialMode.isDark(systemIsDark = isNightConfiguration()))
        val initialPalette = if (BuildConfig.DEVELOPER_OPTIONS) {
            runBlocking { container.developerSettings.currentDarkPalette() }
        } else {
            DarkPalette.DEFAULT
        }
        setContent {
            val mode by container.themeSettings.themeMode.collectAsStateWithLifecycle(initialMode)
            val dark = mode.isDark(systemIsDark = isSystemInDarkTheme())
            val paletteController = rememberDarkPaletteController(initialPalette)
            ApplySystemAppearance(mode = mode, dark = dark, darkPalette = paletteController.palette)
            val themeController = ThemeController(mode) { newMode ->
                lifecycleScope.launch { container.themeSettings.setThemeMode(newMode) }
            }
            MedHomeTheme(darkTheme = dark, darkPalette = paletteController.palette) {
                CompositionLocalProvider(
                    LocalCredentialClient provides container.credentialClient,
                    LocalThemeController provides themeController,
                    LocalDarkPaletteController provides paletteController,
                ) {
                    LanguageSwitchHost(restartsActivity = !languageChangesInPlace, fadeInOnStart = fadeInOnStart) {
                        MedHomeNavHost(passwordSaveOffers = container.passwordSaveOffers)
                    }
                }
            }
        }
    }

    /**
     * Android 12 and lower only (13+ handles the locale through the system). AppCompat has
     * updated the resources in place, but only the activity hears about it, not the Compose
     * views, so restart to redraw in the new language. The content has already faded out.
     */
    override fun onLocalesChanged(locales: LocaleListCompat) {
        super.onLocalesChanged(locales)
        if (!languageChangesInPlace) {
            restartingForLanguage = true
            recreate()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_LANGUAGE_RESTART, restartingForLanguage)
    }

    /** Light icons on dark, dark icons on light. Called before the first frame and on changes. */
    private fun applyBarStyle(dark: Boolean) {
        val bars = if (dark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
    }

    private fun isNightConfiguration(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    /**
     * The developer palette option. Release builds always get the default and never read the
     * stored value; [initial] was read before the first frame, so a stored palette never flips in.
     */
    @Composable
    private fun rememberDarkPaletteController(initial: DarkPalette): DarkPaletteController {
        if (!BuildConfig.DEVELOPER_OPTIONS) return remember { DarkPaletteController(DarkPalette.DEFAULT) {} }
        val settings = (application as MedHomeApplication).container.developerSettings
        val palette by settings.darkPalette.collectAsStateWithLifecycle(initial)
        // Remembered: a new instance in this static local would recompose the whole app.
        return remember(palette) {
            DarkPaletteController(palette) { newPalette -> lifecycleScope.launch { settings.setDarkPalette(newPalette) } }
        }
    }

    /**
     * Keeps everything outside Compose in step with the theme: system bar icons, the window
     * background (seen briefly on rotation) and, on Android 12+, the night mode the system
     * remembers for the app, so the next launch's starting window matches too.
     */
    @Composable
    private fun ApplySystemAppearance(mode: ThemeMode, dark: Boolean, darkPalette: DarkPalette) {
        DisposableEffect(dark, darkPalette) {
            applyBarStyle(dark)
            val background = glassColorsFor(dark, darkPalette).background.base
            window.setBackgroundDrawable(background.toArgb().toDrawable())
            onDispose {}
        }
        LaunchedEffect(mode) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(UiModeManager::class.java)?.setApplicationNightMode(
                    when (mode) {
                        ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
                        ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
                        ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
                    },
                )
            }
        }
    }

    private companion object {
        const val KEY_LANGUAGE_RESTART = "language_restart"

        /** Android 13+ delivers the per-app locale as a normal configuration change. */
        val languageChangesInPlace = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }
}
