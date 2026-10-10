package com.medhome.nepal.reminders

/**
 * What a phone must allow for reminders to arrive on time, each offered once in the setup
 * dialog after a medicine is saved (and always in Settings > Reminder setup). [key] is what
 * ReminderSettings keeps for the ones already offered.
 */
enum class ReminderSetupItem(val key: String) {
    /** Notifications allowed, for the app and its medicine channel. */
    NOTIFICATIONS("notifications"),

    /** "Alarms & reminders" (exact alarms), Android 12+. */
    EXACT_ALARMS("exact_alarms"),

    /** Not battery optimized: the phone lets the app run in the background. */
    BACKGROUND("background"),
    ;

    companion object {
        fun fromKey(key: String): ReminderSetupItem? = entries.firstOrNull { it.key == key }
    }
}
