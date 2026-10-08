package com.medhome.nepal.ui.language

import android.app.Application
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.medhome.nepal.ui.motion.LocalReducedMotion
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * The language switch cover: when the locale is applied relative to the cover, and that the
 * cover always goes away. The locale change and the frame capture are replaced through the
 * internal [LanguageSwitchHost], and the configuration is provided by the test.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE) // The fake captured frame is a real bitmap.
class LanguageSwitchHostTest {

    @get:Rule
    val compose = createComposeRule()

    private val applied = mutableListOf<AppLanguage>()
    private lateinit var controller: LanguageController
    private lateinit var baseConfiguration: Configuration
    private var configuration by mutableStateOf<Configuration?>(null)

    private fun show(restartsActivity: Boolean, reducedMotion: Boolean = false) {
        compose.setContent {
            baseConfiguration = LocalConfiguration.current
            CompositionLocalProvider(
                LocalConfiguration provides (configuration ?: baseConfiguration),
                LocalReducedMotion provides reducedMotion,
            ) {
                LanguageSwitchHost(
                    restartsActivity = restartsActivity,
                    fadeInOnStart = false,
                    applyLanguage = { applied += it },
                    captureFrame = { ImageBitmap(1, 1) },
                ) {
                    controller = LocalLanguageController.current
                    Box(Modifier.fillMaxSize())
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private fun switchTo(language: AppLanguage) = compose.runOnUiThread { controller.apply(language) }

    /** What the system does on Android 13+: a new configuration with the new locale. */
    private fun deliverLocale(tag: String) = compose.runOnUiThread {
        configuration = Configuration(baseConfiguration).apply { setLocale(Locale.forLanguageTag(tag)) }
        // The clock is paused, so nothing else would tell the composition about the write.
        Snapshot.sendApplyNotifications()
    }

    private fun advance(ms: Long) = compose.mainClock.advanceTimeBy(ms)

    private val cover get() = compose.onNodeWithTag(LANGUAGE_COVER_TAG)

    @Test
    fun `in place, the locale is applied only after the cover is on screen`() {
        show(restartsActivity = false)
        switchTo(AppLanguage.NEPALI)
        assertEquals(emptyList<AppLanguage>(), applied)

        advance(FRAME_MS * 4)
        cover.assertExists()
        assertEquals(listOf(AppLanguage.NEPALI), applied)
    }

    @Test
    fun `in place, the cover fades out once the new locale arrives`() {
        show(restartsActivity = false)
        switchTo(AppLanguage.NEPALI)
        advance(FRAME_MS * 4)
        cover.assertExists()

        deliverLocale("ne")
        advance(SETTLE_MS)
        cover.assertDoesNotExist()
    }

    @Test
    fun `in place, the cover goes away even if the locale never arrives`() {
        show(restartsActivity = false)
        switchTo(AppLanguage.NEPALI)
        advance(FRAME_MS * 4)

        advance(MAX_WAIT_MS / 2)
        cover.assertExists()
        advance(MAX_WAIT_MS + SETTLE_MS)
        cover.assertDoesNotExist()
    }

    @Test
    fun `with a restart, the content fades out before the locale is applied`() {
        show(restartsActivity = true)
        switchTo(AppLanguage.NEPALI)
        advance(FRAME_MS * 2)
        assertEquals("Applied before the fade-out finished", emptyList<AppLanguage>(), applied)

        advance(SETTLE_MS)
        assertEquals(listOf(AppLanguage.NEPALI), applied)
        cover.assertDoesNotExist()
    }

    @Test
    fun `with animations off, the locale is applied at once`() {
        show(restartsActivity = false, reducedMotion = true)
        switchTo(AppLanguage.NEPALI)
        assertEquals(listOf(AppLanguage.NEPALI), applied)
        cover.assertDoesNotExist()
    }

    private companion object {
        const val FRAME_MS = 16L
        const val SETTLE_MS = 1_000L
        const val MAX_WAIT_MS = 1_500L
    }
}
