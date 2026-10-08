package com.medhome.nepal

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import com.medhome.nepal.ui.MedHomeNavHost
import com.medhome.nepal.ui.common.LocalCredentialClient
import com.medhome.nepal.ui.theme.MedHomeTheme

/** AppCompatActivity so per-app language (AppCompatDelegate) also works on Android 12 and lower. */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app always uses the light glass theme, so system bar icons must stay dark
        // even when the phone is in dark mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        val container = (application as MedHomeApplication).container
        setContent {
            MedHomeTheme {
                CompositionLocalProvider(LocalCredentialClient provides container.credentialClient) {
                    MedHomeNavHost(passwordSaveOffers = container.passwordSaveOffers)
                }
            }
        }
    }
}
