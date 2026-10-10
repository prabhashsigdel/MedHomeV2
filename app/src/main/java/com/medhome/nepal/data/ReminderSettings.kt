package com.medhome.nepal.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** The reminder switches in Settings, and which one-time explanations were shown. */
data class ReminderPrefs(
    val medicineReminders: Boolean = true,
    val appointmentReminders: Boolean = true,
    /** The battery guide opens by itself once, after the first reminder is set. */
    val batteryGuideShown: Boolean = false,
)

interface ReminderSettings {
    val prefs: Flow<ReminderPrefs>

    suspend fun current(): ReminderPrefs = prefs.first()

    suspend fun setMedicineReminders(enabled: Boolean)

    suspend fun setAppointmentReminders(enabled: Boolean)

    suspend fun setBatteryGuideShown()

    /** Back to the defaults, on sign-out (the next account on this phone starts fresh). Keeps [owner]. */
    suspend fun clear()

    /**
     * The uid of the account the reminders on this phone belong to. Null when nobody's are here
     * yet: wiped, or saved before the owner was kept. Only [setOwner] changes it.
     */
    val owner: Flow<String?>

    suspend fun currentOwner(): String? = owner.first()

    suspend fun setOwner(uid: String?)
}

private val Context.reminderDataStore: DataStore<Preferences> by preferencesDataStore(name = "reminder_settings")

/** Stored with DataStore. Excluded from backup and device transfer by data_extraction_rules. */
class DataStoreReminderSettings(context: Context) : ReminderSettings {
    private val dataStore = context.applicationContext.reminderDataStore

    override val prefs: Flow<ReminderPrefs> = dataStore.data.map { stored ->
        ReminderPrefs(
            medicineReminders = stored[KEY_MEDICINE] ?: true,
            appointmentReminders = stored[KEY_APPOINTMENT] ?: true,
            batteryGuideShown = stored[KEY_BATTERY_GUIDE] ?: false,
        )
    }

    override suspend fun setMedicineReminders(enabled: Boolean) {
        dataStore.edit { it[KEY_MEDICINE] = enabled }
    }

    override suspend fun setAppointmentReminders(enabled: Boolean) {
        dataStore.edit { it[KEY_APPOINTMENT] = enabled }
    }

    override suspend fun setBatteryGuideShown() {
        dataStore.edit { it[KEY_BATTERY_GUIDE] = true }
    }

    override suspend fun clear() {
        dataStore.edit { stored ->
            stored.remove(KEY_MEDICINE)
            stored.remove(KEY_APPOINTMENT)
            stored.remove(KEY_BATTERY_GUIDE)
        }
    }

    override val owner: Flow<String?> = dataStore.data.map { it[KEY_OWNER] }

    override suspend fun setOwner(uid: String?) {
        dataStore.edit { stored -> if (uid == null) stored.remove(KEY_OWNER) else stored[KEY_OWNER] = uid }
    }

    private companion object {
        val KEY_MEDICINE = booleanPreferencesKey("medicine_reminders")
        val KEY_APPOINTMENT = booleanPreferencesKey("appointment_reminders")
        val KEY_BATTERY_GUIDE = booleanPreferencesKey("battery_guide_shown")
        val KEY_OWNER = stringPreferencesKey("owner_uid")
    }
}
