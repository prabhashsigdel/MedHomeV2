package com.medhome.nepal.ui.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medhome.nepal.R
import com.medhome.nepal.domain.Medicine
import com.medhome.nepal.ui.components.GlassButton
import com.medhome.nepal.ui.components.GlassCard
import com.medhome.nepal.ui.components.GlassScreen
import com.medhome.nepal.ui.components.MessageKind
import com.medhome.nepal.ui.components.ScreenTitle
import com.medhome.nepal.ui.components.StatusMessage
import com.medhome.nepal.ui.doctors.LoadingCard
import com.medhome.nepal.ui.motion.entrance
import com.medhome.nepal.ui.theme.GlassTheme

/** Test tag on each medicine's card. */
const val MEDICINE_CARD_TAG = "medicine_card"

/**
 * Medicine reminders, from Home's shortcut: what may stop reminders arriving (each with its fix),
 * Add medicine, and every medicine (tap to edit).
 */
@Composable
fun MedicinesScreen(
    viewModel: MedicinesViewModel,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val access = rememberReminderAccess()
    GlassScreen(showBack = true, drawBackground = false) {
        ScreenTitle(
            title = R.string.medicines_title,
            subtitle = R.string.medicines_subtitle,
            modifier = Modifier.entrance(0),
        )
        when (val current = state) {
            MedicinesUiState.Loading -> LoadingCard()
            is MedicinesUiState.Ready -> {
                if (!current.prefs.medicineReminders) {
                    StatusMessage(message = R.string.medicines_reminders_off, kind = MessageKind.Warning)
                } else if (current.medicines.isNotEmpty()) {
                    ReminderAccessNotices(access = access, channelAllowed = access.medicineChannel, needsExact = true)
                }
                GlassButton(text = R.string.medicines_add, onClick = onAdd, modifier = Modifier.entrance(1))
                if (current.medicines.isEmpty()) {
                    GlassCard(modifier = Modifier.entrance(2), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.medicines_empty), style = MaterialTheme.typography.titleMedium, color = GlassTheme.colors.textPrimary)
                        Text(stringResource(R.string.medicines_empty_body), style = MaterialTheme.typography.bodyMedium, color = GlassTheme.colors.textSecondary)
                    }
                } else {
                    current.medicines.forEachIndexed { index, medicine ->
                        MedicineCard(
                            medicine = medicine,
                            onClick = { onOpen(medicine.id) },
                            // Only the first few animate in (Column, so nothing re-animates on scroll).
                            modifier = if (index < ENTRANCE_ITEMS) Modifier.entrance(2 + index) else Modifier,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MedicineCard(medicine: Medicine, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = GlassTheme.colors
    GlassCard(
        onClick = onClick,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .testTag(MEDICINE_CARD_TAG)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(text = medicine.name, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        Text(text = medicine.dose, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        Text(text = scheduleText(medicine), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        Text(text = periodText(medicine.startDate, medicine.endDate), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
    }
}

private const val ENTRANCE_ITEMS = 4
