package com.medhome.nepal

import android.app.UiModeManager
import android.content.res.Configuration
import android.graphics.Color
import androidx.core.graphics.drawable.toDrawable
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
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.medhome.nepal.data.ThemeMode
import com.medhome.nepal.ui.MedHomeNavHost
import com.medhome.nepal.ui.common.LocalCredentialClient
import com.medhome.nepal.ui.theme.LocalThemeController
import com.medhome.nepal.ui.theme.MedHomeTheme
import com.medhome.nepal.ui.theme.ThemeController
import com.medhome.nepal.ui.theme.darkGlassColors
import com.medhome.nepal.ui.theme.lightGlassColors
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * AppCompatActivity so per-app language (AppCompatDelegate) also works on Android 12 and lower.
 * Handles uiMode changes itself (manifest), so a theme switch crossfades instead of restarting.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as MedHomeApplication).container
        // One small read before the first frame, so that frame is already in the chosen theme.
        val initialMode = runBlocking { container.themeSettings.current() }
        applyBarStyle(dark = initialMode.isDark(systemIsDark = isNightConfiguration()))
        setContent {
            val mode by container.themeSettings.themeMode.collectAsStateWithLifecycle(initialMode)
            val dark = mode.isDark(systemIsDark = isSystemInDarkTheme())
            ApplySystemAppearance(mode = mode, dark = dark)
            val themeController = ThemeController(mode) { newMode ->
                lifecycleScope.launch { container.themeSettings.setThemeMode(newMode) }
            }
            MedHomeTheme(darkTheme = dark) {
                CompositionLocalProvider(
                    LocalCredentialClient provides container.credentialClient,
                    LocalThemeController provides themeController,
                ) {
                    MedHomeNavHost(passwordSaveOffers = container.passwordSaveOffers)
                }
            }
        }
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
     * Keeps everything outside Compose in step with the theme: system bar icons, the window
     * background (seen briefly on rotation) and, on Android 12+, the night mode the system
     * remembers for the app, so the next launch's starting window matches too.
     */
    @Composable
    private fun ApplySystemAppearance(mode: ThemeMode, dark: Boolean) {
        DisposableEffect(dark) {
            applyBarStyle(dark)
            val background = if (dark) darkGlassColors().backgroundBase else lightGlassColors().backgroundBase
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
}
