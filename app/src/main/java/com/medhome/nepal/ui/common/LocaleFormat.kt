package com.medhome.nepal.ui.common

import android.icu.text.DateFormat
import android.icu.text.DateFormatSymbols
import android.icu.text.NumberFormat
import android.icu.util.TimeZone
import android.icu.util.ULocale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.medhome.nepal.R
import com.medhome.nepal.domain.TimeOfDay
import com.medhome.nepal.domain.Weekday
import java.util.Date
import java.util.Locale

/**
 * Numbers, times and weekday names shown to the user, in the app's language: Devanagari digits
 * in Nepali, Western in English (dates use the Gregorian calendar either way). ICU, because it
 * formats Nepali correctly on every supported Android version. Never use these for IDs or
 * stored values, and never for phone numbers (always Western digits).
 */
object LocaleFormat {

    /** A whole number with the locale's digits and grouping: "1,500", "१,५००". */
    fun number(value: Int, locale: Locale): String =
        NumberFormat.getIntegerInstance(ULocale.forLocale(locale)).format(value.toLong())

    /** A clock time in the locale's short style: "10:30 AM" in English. */
    fun timeOfDay(time: TimeOfDay, locale: Locale): String {
        val format = DateFormat.getTimeInstance(DateFormat.SHORT, ULocale.forLocale(locale))
        // The minutes are a wall-clock time, not an instant: format them in UTC so no zone shifts them.
        format.timeZone = TimeZone.GMT_ZONE
        return format.format(Date(time.minutes * MILLIS_PER_MINUTE))
    }

    /** The weekday's short name in [locale]: "Sun", "आइत". */
    fun weekdayShort(day: Weekday, locale: Locale): String =
        DateFormatSymbols.getInstance(ULocale.forLocale(locale))
            .getWeekdays(DateFormatSymbols.FORMAT, DateFormatSymbols.ABBREVIATED)[day.ordinal + FIRST_ICU_WEEKDAY]

    private const val MILLIS_PER_MINUTE = 60_000L

    /** ICU's weekday arrays are indexed by Calendar.SUNDAY (1) to SATURDAY (7). */
    private const val FIRST_ICU_WEEKDAY = 1
}

/** The app's current language, for [LocaleFormat]. */
@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/** A fee in Nepali rupees: "Rs. 800", "रु. ८००". */
@Composable
@ReadOnlyComposable
fun feeText(npr: Int): String = stringResource(R.string.fee_npr, LocaleFormat.number(npr, currentLocale()))
