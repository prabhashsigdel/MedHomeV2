package com.medhome.nepal.domain

import java.util.Locale
import kotlin.random.Random

/** Medical specialties. [key] is what Firestore stores; the label comes from string resources. */
enum class Specialty(val key: String) {
    GENERAL_PHYSICIAN("general_physician"),
    CARDIOLOGY("cardiology"),
    DERMATOLOGY("dermatology"),
    PEDIATRICS("pediatrics"),
    GYNECOLOGY("gynecology"),
    ORTHOPEDICS("orthopedics"),
    ENT("ent"),
    NEUROLOGY("neurology"),
    PSYCHIATRY("psychiatry"),
    OPHTHALMOLOGY("ophthalmology"),
    DENTISTRY("dentistry"),
    GASTROENTEROLOGY("gastroenterology"),
    ;

    companion object {
        fun fromKey(key: String?): Specialty? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Days of the week, Sunday first (the Nepali week). [key] is the weeklySchedule map key in
 * Firestore. Its own type because java.time needs API 26 (minSdk is 24).
 */
enum class Weekday(val key: String) {
    SUNDAY("sun"),
    MONDAY("mon"),
    TUESDAY("tue"),
    WEDNESDAY("wed"),
    THURSDAY("thu"),
    FRIDAY("fri"),
    SATURDAY("sat"),
    ;

    companion object {
        fun fromKey(key: String?): Weekday? = entries.firstOrNull { it.key == key }
    }
}

/** A time of day in Asia/Kathmandu, as minutes after midnight (0 until 1440). */
@JvmInline
value class TimeOfDay(val minutes: Int) : Comparable<TimeOfDay> {
    init {
        require(minutes in 0 until MINUTES_PER_DAY) { "Not a time of day: $minutes" }
    }

    val hour: Int get() = minutes / MINUTES_PER_HOUR
    val minute: Int get() = minutes % MINUTES_PER_HOUR

    override fun compareTo(other: TimeOfDay): Int = minutes.compareTo(other.minutes)

    /** "HH:mm" (24-hour, Western digits), as Firestore stores it. Never shown to users. */
    fun toStorage(): String = String.format(Locale.ROOT, "%02d:%02d", hour, minute)

    companion object {
        const val MINUTES_PER_HOUR = 60
        const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR

        private val HH_MM = Regex("([01][0-9]|2[0-3]):([0-5][0-9])")

        /** "HH:mm" (24-hour), or null when malformed. */
        fun parse(text: String?): TimeOfDay? {
            val match = text?.let { HH_MM.matchEntire(it) } ?: return null
            val (hours, minutes) = match.destructured
            return TimeOfDay(hours.toInt() * MINUTES_PER_HOUR + minutes.toInt())
        }
    }
}

/** Opening hours on one day, [start] before [end]. Schedules never run past midnight. */
data class TimeRange(val start: TimeOfDay, val end: TimeOfDay) {
    init {
        require(start < end) { "Empty range" }
    }
}

/** A doctor as the app shows them. Only active, well-formed doctors ever reach this type. */
data class Doctor(
    val id: String,
    val name: String,
    val specialty: Specialty,
    val hospital: String,
    val feeNpr: Int,
    /** Null when the stored value is missing or unusable: the line is then left out. */
    val experienceYears: Int?,
    val bio: String,
    val slotMinutes: Int,
    /** Days without hours are absent. Ranges are sorted and don't overlap. */
    val weeklySchedule: Map<Weekday, List<TimeRange>>,
) {
    companion object {
        /**
         * Doctor document IDs: letters, digits and hyphens ("doc-001"). No underscore, so a
         * booking ID "{doctorId}_{date}_{time}" always splits unambiguously, and no "/", so an
         * ID can never be read as a Firestore path. The seed script enforces the same pattern.
         */
        private val ID = Regex("[A-Za-z0-9-]{1,64}")

        fun isValidId(id: String): Boolean = ID.matches(id)

        private const val NEW_ID_PREFIX = "doc-"
        private const val NEW_ID_LENGTH = 12
        private const val NEW_ID_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

        /**
         * A new doctor's ID: "doc-" and 12 random lowercase letters and digits ("doc-k3f9x2ab7qpz").
         * A valid ID that can't clash with the seed's numbered ones ("doc-001").
         */
        fun newId(random: Random = Random.Default): String =
            NEW_ID_PREFIX + String(CharArray(NEW_ID_LENGTH) { NEW_ID_ALPHABET[random.nextInt(NEW_ID_ALPHABET.length)] })
    }
}
