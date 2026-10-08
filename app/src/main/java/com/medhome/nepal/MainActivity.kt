package com.medhome.nepal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.medhome.nepal.ui.MedHomeNavHost
import com.medhome.nepal.ui.theme.MedHomeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MedHomeTheme {
                MedHomeNavHost()
            }
        }
    }
}
