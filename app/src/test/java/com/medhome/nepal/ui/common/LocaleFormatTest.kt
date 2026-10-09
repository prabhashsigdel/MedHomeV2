package com.medhome.nepal.ui.common

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.medhome.nepal.domain.TimeOfDay
import com.medhome.nepal.domain.Weekday
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Locale

/** Robolectric: the formatting is ICU's. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class LocaleFormatTest {

    private val english = Locale.ENGLISH
    private val nepali = Locale.forLanguageTag("ne")

    @Test
    fun `numbers use the language's digits and grouping`() {
        assertEquals("800", LocaleFormat.number(800, english))
        assertEquals("1,500", LocaleFormat.number(1500, english))
        assertEquals("८००", LocaleFormat.number(800, nepali))
        assertEquals("१,५००", LocaleFormat.number(1500, nepali))
    }

    @Test
    fun `times are the wall-clock time in the language's style and digits`() {
        val halfPastTen = TimeOfDay(10 * 60 + 30)
        assertTrue(LocaleFormat.timeOfDay(halfPastTen, english), LocaleFormat.timeOfDay(halfPastTen, english).startsWith("10:30"))
        assertTrue(LocaleFormat.timeOfDay(halfPastTen, nepali), "१०:३०" in LocaleFormat.timeOfDay(halfPastTen, nepali))
        assertTrue(LocaleFormat.timeOfDay(TimeOfDay(15 * 60), english).startsWith("3:00"))
    }

    @Test
    fun `weekday names follow the language, Sunday first`() {
        assertEquals("Sun", LocaleFormat.weekdayShort(Weekday.SUNDAY, english))
        assertEquals("Sat", LocaleFormat.weekdayShort(Weekday.SATURDAY, english))
        assertTrue(LocaleFormat.weekdayShort(Weekday.SUNDAY, nepali).first().code in DEVANAGARI)
    }

    private companion object {
        val DEVANAGARI = 0x0900..0x097F
    }
}
