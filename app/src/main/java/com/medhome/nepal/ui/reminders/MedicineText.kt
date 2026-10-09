package com.medhome.nepal.ui.reminders

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.medhome.nepal.R
import com.medhome.nepal.domain.CalendarDate
import com.medhome.nepal.domain.DoseState
import com.medhome.nepal.domain.Medicine
import com.medhome.nepal.domain.MedicineDays
import com.medhome.nepal.domain.TimeOfDay
import com.medhome.nepal.domain.Weekday
import com.medhome.nepal.ui.common.DateStyle
import com.medhome.nepal.ui.common.LocaleFormat
import com.medhome.nepal.ui.common.currentLocale

/** "8:00 AM, 8:00 PM" in the app's language. */
@Composable
@ReadOnlyComposable
fun timesText(times: List<TimeOfDay>): String {
    val locale = currentLocale()
    return times.joinToString(stringResource(R.string.list_separator)) { LocaleFormat.timeOfDay(it, locale) }
}

/** "Every day", or the chosen days Sunday first: "Mon, Wed, Fri". */
@Composable
@ReadOnlyComposable
fun daysText(days: MedicineDays): String = when (days) {
    MedicineDays.EveryDay -> stringResource(R.string.medicine_every_day)
    is MedicineDays.Chosen -> {
        val locale = currentLocale()
        Weekday.entries.filter { it in days.days }
            .joinToString(stringResource(R.string.list_separator)) { LocaleFormat.weekdayShort(it, locale) }
    }
}

/** "From Thu, 9 Oct", or "Thu, 9 Oct – Mon, 20 Oct" with an end date. */
@Composable
@ReadOnlyComposable
fun periodText(start: CalendarDate, end: CalendarDate?): String {
    val locale = currentLocale()
    val from = LocaleFormat.date(start, DateStyle.SHORT, locale)
    return if (end == null) {
        stringResource(R.string.medicine_period_from, from)
    } else {
        stringResource(R.string.medicine_period_range, from, LocaleFormat.date(end, DateStyle.SHORT, locale))
    }
}

/** Everything about a medicine's schedule on one line, for lists. */
@Composable
@ReadOnlyComposable
fun scheduleText(medicine: Medicine): String =
    stringResource(R.string.medicine_schedule_line, timesText(medicine.times), daysText(medicine.days))

@get:StringRes
val DoseState.label: Int
    get() = when (this) {
        DoseState.TAKEN -> R.string.dose_taken
        DoseState.UPCOMING -> R.string.dose_upcoming
        DoseState.MISSED -> R.string.dose_missed
    }
