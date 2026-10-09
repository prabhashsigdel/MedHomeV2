package com.medhome.nepal.ui.components

import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.medhome.nepal.R
import com.medhome.nepal.domain.CalendarDate
import com.medhome.nepal.domain.NepalTime
import com.medhome.nepal.domain.TimeOfDay

/** Material's time input (solid surfaces), in the phone's 12 / 24-hour setting. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    @StringRes title: Int,
    initial: TimeOfDay,
    onConfirm: (TimeOfDay) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = DateFormat.is24HourFormat(context),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { TimeInput(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(TimeOfDay(state.hour * TimeOfDay.MINUTES_PER_HOUR + state.minute)) }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * Material's date picker (solid surfaces). It works in UTC midnights, which is exactly a
 * [CalendarDate]'s epoch day, so no time zone can move the date. Days before [earliest] can't
 * be picked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarDatePickerDialog(
    initial: CalendarDate,
    onConfirm: (CalendarDate) -> Unit,
    onDismiss: () -> Unit,
    earliest: CalendarDate? = null,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.epochDay * NepalTime.MILLIS_PER_DAY,
        yearRange = CalendarDate.MIN_YEAR..CalendarDate.MAX_YEAR,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                earliest == null || utcTimeMillis >= earliest.epochDay * NepalTime.MILLIS_PER_DAY
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) onConfirm(CalendarDate.ofEpochDay(millis.floorDiv(NepalTime.MILLIS_PER_DAY))) else onDismiss()
                },
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    ) {
        DatePicker(state = state)
    }
}
