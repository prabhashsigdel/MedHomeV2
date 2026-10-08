package com.medhome.nepal.ui.home

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Avatar initials and the Home date line. Robolectric for the date: it formats with ICU. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class HomeFormatTest {

    private val thursday8October2026: Date = Calendar.getInstance().apply {
        clear()
        set(2026, Calendar.OCTOBER, 8, 12, 0)
    }.time

    @Test
    fun `initials are the first letters of the first and last words`() {
        assertEquals("PS", initialsOf("Prabhash Sigdel"))
        assertEquals("PS", initialsOf("  prabhash   kumar  sigdel "))
    }

    @Test
    fun `a single word gives one initial`() {
        assertEquals("A", initialsOf("Asha"))
    }

    @Test
    fun `Devanagari names give one letter, without vowel signs or viramas`() {
        assertEquals("प", initialsOf("प्रभाष सिग्देल"))
        assertEquals("क", initialsOf("किरण"))
    }

    @Test
    fun `leading punctuation is skipped`() {
        assertEquals("AR", initialsOf("(Asha) Rai"))
    }

    @Test
    fun `a name without letters has no initials`() {
        assertNull(initialsOf(""))
        assertNull(initialsOf("   "))
        assertNull(initialsOf("- ."))
    }

    @Test
    fun `the English date is day first`() {
        assertEquals("Thursday, 8 October", homeDateText(thursday8October2026, Locale.ENGLISH))
        assertEquals("Thursday, 8 October", homeDateText(thursday8October2026, Locale.US))
    }

    @Test
    fun `the Nepali date is day first with Devanagari digits`() {
        val text = homeDateText(thursday8October2026, Locale.forLanguageTag("ne"))
        assertTrue("Expected Devanagari 8 in \"$text\"", text.contains(", ८ "))
        assertTrue("Expected the Nepali month name in \"$text\"", text.endsWith("अक्टोबर"))
        assertFalse("Expected no Western digits in \"$text\"", text.any { it in '0'..'9' })
    }
}
