package com.medhome.nepal.ui.common

import androidx.annotation.StringRes
import com.medhome.nepal.R
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Dates of birth are calendar dates, stored as "YYYY-MM-DD" text so no time zone can shift the
 * day. Material's date picker works in UTC midnight milliseconds, so conversions use UTC too.
 * Uses java.text/java.util (java.time needs Android 8, minSdk is 7).
 */
object BirthDate {
    private const val MAX_AGE_YEARS = 120
    private val UTC: TimeZone = TimeZone.getTimeZone("UTC")

    private fun isoFormat() = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = UTC
        isLenient = false
    }

    fun toIso(utcMillis: Long): String = isoFormat().format(utcMillis)

    fun toUtcMillis(iso: String): Long? = try {
        isoFormat().parse(iso)?.time
    } catch (_: ParseException) {
        null
    }

    /** Not in the future and not more than [MAX_AGE_YEARS] years ago. */
    @StringRes
    fun error(utcMillis: Long, todayUtcMillis: Long): Int? {
        val earliest = Calendar.getInstance(UTC).apply {
            timeInMillis = todayUtcMillis
            add(Calendar.YEAR, -MAX_AGE_YEARS)
        }.timeInMillis
        return if (utcMillis > todayUtcMillis || utcMillis < earliest) R.string.validation_date_of_birth_invalid else null
    }

    /** Shown in the app's language (e.g. Devanagari month names and digits in Nepali). */
    fun display(iso: String, locale: Locale): String? {
        val millis = toUtcMillis(iso) ?: return null
        return DateFormat.getDateInstance(DateFormat.MEDIUM, locale).apply { timeZone = UTC }.format(millis)
    }
}
