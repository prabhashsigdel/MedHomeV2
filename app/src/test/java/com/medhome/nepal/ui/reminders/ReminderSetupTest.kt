package com.medhome.nepal.ui.reminders

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.medhome.nepal.reminders.ReminderSetupItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Which setup items count as missing, from what the phone allows. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class ReminderSetupTest {

    private val allOn = ReminderAccessState(
        notifications = true,
        medicineChannel = true,
        appointmentChannel = true,
        exactAlarms = true,
        batteryUnrestricted = true,
    )

    @Test @Config(sdk = [35])
    fun `nothing is missing when everything is allowed`() {
        assertTrue(allOn.missing().isEmpty())
    }

    @Test @Config(sdk = [35])
    fun `each switch that is off is missing, and only those`() {
        assertEquals(setOf(ReminderSetupItem.NOTIFICATIONS), allOn.copy(notifications = false).missing())
        // The medicine channel turned off counts as notifications off; the appointment one doesn't.
        assertEquals(setOf(ReminderSetupItem.NOTIFICATIONS), allOn.copy(medicineChannel = false).missing())
        assertTrue(allOn.copy(appointmentChannel = false).missing().isEmpty())
        assertEquals(setOf(ReminderSetupItem.EXACT_ALARMS), allOn.copy(exactAlarms = false).missing())
        assertEquals(setOf(ReminderSetupItem.BACKGROUND), allOn.copy(batteryUnrestricted = false).missing())
    }

    @Test @Config(sdk = [30])
    fun `below Android 12 there is no Alarms and reminders item`() {
        assertFalse(ReminderSetupItem.EXACT_ALARMS.applies)
        assertTrue(allOn.copy(exactAlarms = false).missing().isEmpty())
    }

    @Test
    fun `stored keys read back as items`() {
        ReminderSetupItem.entries.forEach { assertEquals(it, ReminderSetupItem.fromKey(it.key)) }
        assertEquals(null, ReminderSetupItem.fromKey("battery_guide"))
    }
}
