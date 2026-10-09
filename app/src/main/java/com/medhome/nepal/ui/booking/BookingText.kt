package com.medhome.nepal.ui.booking

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.medhome.nepal.R
import com.medhome.nepal.domain.Booking
import com.medhome.nepal.domain.BookingError
import com.medhome.nepal.domain.DayPeriod
import com.medhome.nepal.ui.common.LocaleFormat
import com.medhome.nepal.ui.common.currentLocale

/** Why booking failed, in this screen's words (never a message written for another context). */
@Composable
@ReadOnlyComposable
fun bookingErrorText(error: BookingError): String = when (error) {
    BookingError.SLOT_TAKEN -> stringResource(R.string.book_error_slot_taken)
    BookingError.SLOT_UNAVAILABLE -> stringResource(R.string.book_error_slot_unavailable)
    BookingError.LIMIT_REACHED ->
        stringResource(R.string.book_error_limit, LocaleFormat.number(Booking.MAX_UPCOMING, currentLocale()))
    BookingError.EMAIL_NOT_VERIFIED -> stringResource(R.string.book_verify_title)
    BookingError.DOCTOR_UNAVAILABLE -> stringResource(R.string.doctor_unavailable_body)
    BookingError.NETWORK -> stringResource(R.string.error_network)
    BookingError.ALREADY_STARTED,
    BookingError.NOT_FOUND,
    BookingError.UNKNOWN -> stringResource(R.string.book_error_unknown)
}

/** Why cancelling failed. */
@StringRes
fun cancelErrorText(error: BookingError): Int = when (error) {
    BookingError.ALREADY_STARTED -> R.string.booking_cancel_error_started
    BookingError.NETWORK -> R.string.error_network
    BookingError.NOT_FOUND -> R.string.booking_not_found_body
    BookingError.SLOT_TAKEN,
    BookingError.SLOT_UNAVAILABLE,
    BookingError.LIMIT_REACHED,
    BookingError.EMAIL_NOT_VERIFIED,
    BookingError.DOCTOR_UNAVAILABLE,
    BookingError.UNKNOWN -> R.string.booking_cancel_error_unknown
}

@get:StringRes
val DayPeriod.label: Int
    get() = when (this) {
        DayPeriod.MORNING -> R.string.book_morning
        DayPeriod.AFTERNOON -> R.string.book_afternoon
        DayPeriod.EVENING -> R.string.book_evening
    }
