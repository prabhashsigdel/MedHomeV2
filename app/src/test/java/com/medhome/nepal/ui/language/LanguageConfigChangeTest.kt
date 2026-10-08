package com.medhome.nepal.ui.language

import android.app.Application
import android.content.ComponentName
import android.content.pm.ActivityInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.medhome.nepal.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * MainActivity must handle theme and language changes itself: without these flags a theme switch
 * restarts the activity, and a language switch on Android 13+ freezes while it is recreated.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class LanguageConfigChangeTest {

    @Test
    fun `MainActivity handles uiMode, locale and layout direction changes`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val info = context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        val required = ActivityInfo.CONFIG_UI_MODE or ActivityInfo.CONFIG_LOCALE or ActivityInfo.CONFIG_LAYOUT_DIRECTION
        val missing = required and info.configChanges.inv()
        assertTrue(
            "configChanges is 0x${info.configChanges.toString(16)}, missing 0x${missing.toString(16)}",
            missing == 0,
        )
    }
}
