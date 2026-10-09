package com.medhome.nepal.ui.booking

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medhome.nepal.R
import com.medhome.nepal.domain.CalendarDate
import com.medhome.nepal.domain.DayPeriod
import com.medhome.nepal.domain.DaySlots
import com.medhome.nepal.domain.Doctor
import com.medhome.nepal.domain.Slot
import com.medhome.nepal.domain.Slots
import com.medhome.nepal.ui.common.DateStyle
import com.medhome.nepal.ui.common.LocaleFormat
import com.medhome.nepal.ui.common.currentLocale
import com.medhome.nepal.ui.common.feeText
import com.medhome.nepal.ui.components.GlassBottomSheet
import com.medhome.nepal.ui.components.GlassButton
import com.medhome.nepal.ui.components.GlassButtonStyle
import com.medhome.nepal.ui.components.GlassCard
import com.medhome.nepal.ui.components.GlassScreen
import com.medhome.nepal.ui.components.MessageKind
import com.medhome.nepal.ui.components.ScreenTitle
import com.medhome.nepal.ui.components.SectionTitle
import com.medhome.nepal.ui.components.SettingsDivider
import com.medhome.nepal.ui.components.StatusMessage
import com.medhome.nepal.ui.components.controlBorder
import com.medhome.nepal.ui.components.glassControl
import com.medhome.nepal.ui.doctors.FactRow
import com.medhome.nepal.ui.doctors.LoadingCard
import com.medhome.nepal.ui.doctors.MessageCard
import com.medhome.nepal.ui.doctors.label
import com.medhome.nepal.ui.motion.entrance
import com.medhome.nepal.ui.motion.pressScale
import com.medhome.nepal.ui.theme.GlassDimens
import com.medhome.nepal.ui.theme.GlassShapes
import com.medhome.nepal.ui.theme.GlassTheme

/** Test tags, for navigation tests. */
const val DATE_CHIP_TAG = "book_date_chip"
const val SLOT_CHIP_TAG = "book_slot_chip"

/**
 * Booking with one doctor (pushed from their details): a 14-day date strip, the chosen day's
 * free times grouped by morning, afternoon and evening, then a confirm sheet. After booking the
 * sheet shows success, and [onBooked] takes the user to the Bookings tab (also on Back or
 * when the sheet is swiped away, since the booking is done).
 */
@Composable
fun BookAppointmentScreen(viewModel: BookAppointmentViewModel, onBooked: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(enabled = state.booked != null, onBack = onBooked)

    GlassScreen(showBack = true, drawBackground = false) {
        ScreenTitle(title = R.string.book_title, modifier = Modifier.entrance(0))
        when (val slots = state.slots) {
            SlotsState.Loading -> LoadingCard()
            SlotsState.NeedsVerification -> MessageCard(R.string.book_verify_title, R.string.book_verify_body) {
                GlassButton(text = R.string.book_verify_action, onClick = viewModel::verifyEmail)
            }
            is SlotsState.DoctorUnavailable -> MessageCard(
                title = R.string.doctor_unavailable_title,
                body = if (slots.offline) R.string.doctor_unavailable_offline else R.string.doctor_unavailable_body,
            )
            SlotsState.Failed -> StatusMessage(
                message = R.string.book_slots_load_failed,
                kind = MessageKind.Error,
                onRetry = viewModel::retry,
            )
            is SlotsState.Ready -> SlotPicker(
                slots = slots,
                state = state,
                onSelectDate = viewModel::selectDate,
                onSelectSlot = viewModel::selectSlot,
                onDismissError = viewModel::dismissError,
            )
        }
    }

    val sheetSlot = state.booked ?: state.pendingSlot
    // After booking, the doctor booked with: the slots may stop being ready meanwhile.
    val doctor = state.bookedDoctor ?: (state.slots as? SlotsState.Ready)?.doctor
    if (sheetSlot != null && doctor != null) {
        BookingSheet(
            doctor = doctor,
            slot = sheetSlot,
            state = state,
            onConfirm = viewModel::confirm,
            onDismiss = if (state.booked != null) onBooked else viewModel::dismissConfirm,
            onBooked = onBooked,
        )
    }
}

@Composable
private fun SlotPicker(
    slots: SlotsState.Ready,
    state: BookAppointmentUiState,
    onSelectDate: (CalendarDate) -> Unit,
    onSelectSlot: (Slot) -> Unit,
    onDismissError: () -> Unit,
) {
    val colors = GlassTheme.colors
    DoctorLine(slots.doctor, modifier = Modifier.entrance(1))
    // Errors from a closed sheet (the slot was taken) show here; open-sheet errors show in it.
    if (state.pendingSlot == null) {
        state.error?.let { StatusMessage(text = bookingErrorText(it), kind = MessageKind.Error, onDismiss = onDismissError) }
    }
    if (!slots.hasAnySlot) {
        GlassCard(modifier = Modifier.entrance(2)) {
            Text(
                text = stringResource(R.string.book_no_slots, LocaleFormat.number(Slots.BOOKING_DAYS, currentLocale())),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
            )
        }
        return
    }
    SectionTitle(text = R.string.book_pick_date, modifier = Modifier.entrance(2))
    DateStrip(days = slots.days, selected = state.selectedDate, onSelect = onSelectDate, modifier = Modifier.entrance(2))
    SectionTitle(text = R.string.book_pick_time, modifier = Modifier.entrance(3))
    val day = slots.days.firstOrNull { it.date == state.selectedDate }
    SlotGrid(slots = day?.slots.orEmpty(), onSelect = onSelectSlot, modifier = Modifier.entrance(3))
    Text(
        text = stringResource(R.string.book_times_note),
        style = MaterialTheme.typography.bodySmall,
        color = colors.textSecondary,
        modifier = Modifier.entrance(4),
    )
}

@Composable
private fun DoctorLine(doctor: Doctor, modifier: Modifier = Modifier) {
    val colors = GlassTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = doctor.name, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        Text(
            text = "${stringResource(doctor.specialty.label)} · ${doctor.hospital}",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
    }
}

/** The 14 days as a sideways-scrolling row of chips. Days with no free time are dimmed. */
@Composable
private fun DateStrip(
    days: List<DaySlots>,
    selected: CalendarDate?,
    onSelect: (CalendarDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        days.forEach { day ->
            key(day.date) {
                DateChip(day = day, selected = day.date == selected, onClick = { onSelect(day.date) })
            }
        }
    }
}

@Composable
private fun DateChip(day: DaySlots, selected: Boolean, onClick: () -> Unit) {
    val colors = GlassTheme.colors
    val locale = currentLocale()
    val interactionSource = remember { MutableInteractionSource() }
    val available = day.slots.isNotEmpty()
    val textColor = when {
        selected -> colors.onAccent
        available -> colors.textPrimary
        else -> colors.textSecondary
    }
    val freeTimes = if (available) {
        pluralStringResource(R.plurals.book_day_free, day.slots.size, LocaleFormat.number(day.slots.size, locale))
    } else {
        stringResource(R.string.book_day_full)
    }
    val description = "${LocaleFormat.date(day.date, DateStyle.FULL, locale)}, $freeTimes"
    Column(
        modifier = Modifier
            .testTag(DATE_CHIP_TAG)
            .width(DateChipWidth)
            .pressScale(interactionSource)
            .clip(GlassShapes.Chip)
            .then(if (selected) Modifier.background(colors.accent) else Modifier.glassControl(GlassShapes.Chip))
            .controlBorder(GlassShapes.Chip)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = ripple(),
                role = Role.RadioButton,
                onClick = onClick,
            )
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.RadioButton
                this.selected = selected
            }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = LocaleFormat.date(day.date, DateStyle.WEEKDAY, locale), style = MaterialTheme.typography.labelMedium, color = textColor)
        Text(text = LocaleFormat.date(day.date, DateStyle.DAY, locale), style = MaterialTheme.typography.titleLarge, color = textColor)
        Text(text = LocaleFormat.date(day.date, DateStyle.MONTH, locale), style = MaterialTheme.typography.labelSmall, color = textColor)
    }
}

/** The day's free times grouped by morning, afternoon and evening, three per row. */
@Composable
private fun SlotGrid(slots: List<Slot>, onSelect: (Slot) -> Unit, modifier: Modifier = Modifier) {
    val colors = GlassTheme.colors
    GlassCard(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (slots.isEmpty()) {
            Text(text = stringResource(R.string.book_no_slots_day), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            return@GlassCard
        }
        slots.groupBy { DayPeriod.of(it.start) }.forEach { (period, inPeriod) ->
            Text(text = stringResource(period.label), style = MaterialTheme.typography.titleSmall, color = colors.textSecondary)
            inPeriod.chunked(SLOTS_PER_ROW).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { slot ->
                        key(slot.id) { SlotChip(slot = slot, onClick = { onSelect(slot) }, modifier = Modifier.weight(1f)) }
                    }
                    repeat(SLOTS_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun SlotChip(slot: Slot, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .testTag(SLOT_CHIP_TAG)
            .heightIn(min = GlassDimens.MinTouchTarget)
            .pressScale(interactionSource)
            .glassControl(GlassShapes.Chip)
            .selectable(
                selected = false,
                interactionSource = interactionSource,
                indication = ripple(),
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 6.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = LocaleFormat.timeOfDay(slot.start, currentLocale()),
            style = MaterialTheme.typography.labelLarge,
            color = GlassTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** Confirm the chosen slot, then (same sheet) the success message. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookingSheet(
    doctor: Doctor,
    slot: Slot,
    state: BookAppointmentUiState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onBooked: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val booked = state.booked != null
    GlassBottomSheet(
        onDismissRequest = { if (!state.submitting) onDismiss() },
        // Static while the booking is sent: no drag, no Back, no scrim tap.
        locked = state.submitting,
        sheetState = sheetState,
        title = if (booked) R.string.book_success_title else R.string.book_confirm_title,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = GlassDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(GlassDimens.ItemSpacing),
        ) {
            if (booked) {
                Text(
                    text = stringResource(R.string.book_success_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = GlassTheme.colors.textPrimary,
                )
            }
            BookingSummary(doctor = doctor, slot = slot)
            if (booked) {
                GlassButton(text = R.string.book_success_action, onClick = onBooked)
            } else {
                StatusMessage(message = R.string.book_pay_at_clinic, kind = MessageKind.Info)
                state.error?.let { StatusMessage(text = bookingErrorText(it), kind = MessageKind.Error) }
                GlassButton(
                    text = R.string.book_confirm_action,
                    onClick = onConfirm,
                    loading = state.submitting,
                    enabled = !state.submitting,
                )
                GlassButton(
                    text = R.string.action_cancel,
                    onClick = onDismiss,
                    style = GlassButtonStyle.Secondary,
                    enabled = !state.submitting,
                )
            }
        }
    }
}

@Composable
private fun BookingSummary(doctor: Doctor, slot: Slot) {
    val locale = currentLocale()
    GlassCard(contentPadding = PaddingValues(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        FactRow(label = R.string.label_doctor, value = doctor.name)
        SettingsDivider()
        FactRow(label = R.string.label_date, value = LocaleFormat.date(slot.date, DateStyle.FULL, locale))
        SettingsDivider()
        FactRow(label = R.string.label_time, value = LocaleFormat.timeOfDay(slot.start, locale))
        SettingsDivider()
        FactRow(label = R.string.doctor_hospital, value = doctor.hospital)
        SettingsDivider()
        FactRow(label = R.string.doctor_fee, value = feeText(doctor.feeNpr))
    }
}

private const val SLOTS_PER_ROW = 3
private val DateChipWidth = 64.dp
