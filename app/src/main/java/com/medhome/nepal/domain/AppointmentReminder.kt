package com.medhome.nepal.domain

/**
 * An upcoming booking this phone reminds the patient of. A local copy of what the reminders
 * need (kept in step with the bookings in Firestore), so they still fire after a reboot.
 */
data class AppointmentReminder(
    val bookingId: String,
    val startAtMillis: Long,
    /** Shown in the reminder; empty until the bookings listener has filled it in. */
    val doctorName: String,
)

/** The two reminders of an appointment. [key] is part of the alarm's identity. */
enum class AppointmentAlert(val key: String) {
    /** The evening before, at [AppointmentReminders.EVENING_HOUR]. */
    EVENING_BEFORE("evening"),

    /** [AppointmentReminders.HOUR_BEFORE_MINUTES] before the start. */
    HOUR_BEFORE("hour"),
    ;

    companion object {
        fun fromKey(key: String?): AppointmentAlert? = entries.firstOrNull { it.key == key }
    }
}

/** What changes to make so the local reminders match the patient's upcoming bookings. */
data class AppointmentSyncPlan(
    /** New, moved or renamed: (re)schedule. */
    val upsert: List<AppointmentReminder>,
    /** No longer upcoming (cancelled, by anyone, or started): cancel and forget. */
    val remove: List<String>,
)

object AppointmentReminders {
    /** The evening reminder fires at this hour (Nepal time) the day before. */
    const val EVENING_HOUR = 18
    const val HOUR_BEFORE_MINUTES = 60

    /** When [alert] for an appointment at [startAtMillis] fires. */
    fun alertAt(startAtMillis: Long, alert: AppointmentAlert): Long = when (alert) {
        AppointmentAlert.EVENING_BEFORE -> NepalTime.epochMillis(
            NepalTime.dateOf(startAtMillis).plusDays(-1),
            TimeOfDay(EVENING_HOUR * TimeOfDay.MINUTES_PER_HOUR),
        )
        AppointmentAlert.HOUR_BEFORE -> startAtMillis - HOUR_BEFORE_MINUTES * NepalTime.MILLIS_PER_MINUTE
    }

    /**
     * The alerts still to come at [nowMillis], with their times; one whose time has passed is
     * skipped (booked after 18:00 for tomorrow: only the hour-before one). The evening alert
     * always comes first: the hour-before one is 23:00 the day before at the earliest.
     */
    fun pendingAlerts(startAtMillis: Long, nowMillis: Long): Map<AppointmentAlert, Long> =
        AppointmentAlert.entries
            .associateWith { alertAt(startAtMillis, it) }
            .filterValues { it > nowMillis }

    /**
     * Compares the stored reminders with the patient's bookings (the listener's full list).
     * Only booked, not yet started bookings are kept; a row stored at booking time without the
     * doctor's name is filled in here.
     */
    fun plan(stored: List<AppointmentReminder>, bookings: List<Booking>, nowMillis: Long): AppointmentSyncPlan {
        val storedById = stored.associateBy { it.bookingId }
        val wanted = bookings
            .filter { it.isUpcoming(nowMillis) }
            .associate { it.id to AppointmentReminder(it.id, it.startAtMillis, it.doctor.name) }
        val upsert = wanted.values.filter { it != storedById[it.bookingId] }
        val remove = storedById.keys.filter { it !in wanted }
        return AppointmentSyncPlan(upsert = upsert, remove = remove)
    }
}
