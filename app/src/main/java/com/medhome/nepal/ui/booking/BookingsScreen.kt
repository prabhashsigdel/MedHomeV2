package com.medhome.nepal.ui.booking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medhome.nepal.R
import com.medhome.nepal.domain.Booking
import com.medhome.nepal.domain.BookingStatus
import com.medhome.nepal.ui.common.dateTimeText
import com.medhome.nepal.ui.components.ChoiceOption
import com.medhome.nepal.ui.components.GlassButton
import com.medhome.nepal.ui.components.GlassButtonStyle
import com.medhome.nepal.ui.components.GlassCard
import com.medhome.nepal.ui.components.GlassScreen
import com.medhome.nepal.ui.components.InitialsAvatar
import com.medhome.nepal.ui.components.MessageKind
import com.medhome.nepal.ui.components.ScreenTitle
import com.medhome.nepal.ui.components.SegmentedChoice
import com.medhome.nepal.ui.components.StatusMessage
import com.medhome.nepal.ui.doctors.LoadingCard
import com.medhome.nepal.ui.doctors.MessageCard
import com.medhome.nepal.ui.doctors.label
import com.medhome.nepal.ui.motion.entrance
import com.medhome.nepal.ui.theme.GlassTheme

/** Test tags, for navigation tests. */
const val BOOKING_CARD_TAG = "booking_card"
const val BOOK_APPOINTMENT_BUTTON_TAG = "book_appointment_button"

/**
 * The Bookings tab: Book appointment (opens Find a doctor), then Upcoming (soonest first) and
 * Past (latest first), each a list of cards. With nothing upcoming, the empty card offers
 * booking too. A button, not a floating action button: that would clash with the floating tab bar.
 */
@Composable
fun BookingsScreen(viewModel: BookingsViewModel, onOpenBooking: (String) -> Unit, onBookAppointment: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val segment by viewModel.segment.collectAsStateWithLifecycle()
    GlassScreen(drawBackground = false) {
        ScreenTitle(title = R.string.bookings_title, modifier = Modifier.entrance(0))
        GlassButton(
            text = R.string.bookings_book_action,
            onClick = onBookAppointment,
            modifier = Modifier.entrance(1).testTag(BOOK_APPOINTMENT_BUTTON_TAG),
        )
        SegmentedChoice(
            options = listOf(
                ChoiceOption(BookingsSegment.UPCOMING, stringResource(R.string.bookings_upcoming)),
                ChoiceOption(BookingsSegment.PAST, stringResource(R.string.bookings_past)),
            ),
            selected = segment,
            onSelect = viewModel::selectSegment,
            modifier = Modifier.entrance(2),
        )
        when (val current = state) {
            BookingsUiState.Loading -> LoadingCard()
            BookingsUiState.Failed -> StatusMessage(
                message = R.string.bookings_load_failed,
                kind = MessageKind.Error,
                onRetry = viewModel::retry,
            )
            is BookingsUiState.Ready -> {
                if (current.showingSaved) StatusMessage(message = R.string.bookings_saved_notice, kind = MessageKind.Info)
                val shown = if (segment == BookingsSegment.UPCOMING) current.upcoming else current.past
                when {
                    shown.isNotEmpty() -> shown.forEach { booking ->
                        key(booking.id) { BookingCard(booking = booking, onClick = { onOpenBooking(booking.id) }) }
                    }
                    segment == BookingsSegment.UPCOMING ->
                        MessageCard(R.string.home_no_appointments, R.string.bookings_none_upcoming_body) {
                            GlassButton(
                                text = R.string.bookings_book_action,
                                onClick = onBookAppointment,
                                style = GlassButtonStyle.Secondary,
                                modifier = Modifier.testTag(BOOK_APPOINTMENT_BUTTON_TAG),
                            )
                        }
                    else -> MessageCard(R.string.bookings_none_past_title, R.string.bookings_none_past_body)
                }
            }
        }
    }
}

/** One booking: doctor, specialty, date and time, and who cancelled it when it was. One button for TalkBack. */
@Composable
private fun BookingCard(booking: Booking, onClick: () -> Unit) {
    val colors = GlassTheme.colors
    GlassCard(
        onClick = onClick,
        contentPadding = PaddingValues(16.dp),
        modifier = Modifier
            .testTag(BOOKING_CARD_TAG)
            .semantics(mergeDescendants = true) {},
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InitialsAvatar(name = booking.doctor.name)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = booking.doctor.name, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                Text(
                    text = stringResource(booking.doctor.specialty.label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                Text(
                    text = dateTimeText(booking.date, booking.start),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                )
                if (booking.status == BookingStatus.CANCELLED) {
                    Text(
                        text = stringResource(cancelledStatusText(booking.cancelledBy)),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.error,
                    )
                }
            }
        }
    }
}
