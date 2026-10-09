package com.medhome.nepal.data

import com.medhome.nepal.domain.Doctor
import com.medhome.nepal.domain.Specialty
import com.medhome.nepal.domain.TimeOfDay
import com.medhome.nepal.domain.TimeRange
import com.medhome.nepal.domain.Weekday

/**
 * Turns a doctors/{id} document into a [Doctor], or null when it must not be shown: inactive,
 * or missing (or mistyping) a field the app can't do without. Firestore data is never trusted:
 * text is cleaned of control and invisible formatting characters (no bidi-override spoofing)
 * and length-capped, numbers are range-checked, and bad schedule entries are dropped one by
 * one rather than hiding the doctor. Never throws.
 */
object DoctorMapper {

    const val FIELD_ACTIVE = "active"
    const val FIELD_NAME = "name"
    const val FIELD_SPECIALTY = "specialty"
    const val FIELD_HOSPITAL = "hospital"
    const val FIELD_FEE = "feeNpr"
    const val FIELD_EXPERIENCE = "experienceYears"
    const val FIELD_BIO = "bio"
    const val FIELD_SLOT_MINUTES = "slotMinutes"
    const val FIELD_SCHEDULE = "weeklySchedule"
    const val FIELD_START = "start"
    const val FIELD_END = "end"

    const val MAX_NAME_LENGTH = 100
    const val MAX_HOSPITAL_LENGTH = 120
    const val MAX_BIO_LENGTH = 2000
    const val MAX_FEE_NPR = 100_000
    const val MAX_EXPERIENCE_YEARS = 70
    val SlotMinutesRange = 5..240

    /** Used when slotMinutes is missing or out of range, so the doctor can still be shown. */
    const val DEFAULT_SLOT_MINUTES = 15

    fun parse(id: String, data: Map<String, Any?>?): Doctor? {
        if (data == null || !Doctor.isValidId(id)) return null
        if (data[FIELD_ACTIVE] != true) return null
        val name = cleanLine(data[FIELD_NAME], MAX_NAME_LENGTH) ?: return null
        val specialty = Specialty.fromKey(data[FIELD_SPECIALTY] as? String) ?: return null
        val hospital = cleanLine(data[FIELD_HOSPITAL], MAX_HOSPITAL_LENGTH) ?: return null
        val fee = wholeNumber(data[FIELD_FEE])?.takeIf { it in 0..MAX_FEE_NPR } ?: return null
        return Doctor(
            id = id,
            name = name,
            specialty = specialty,
            hospital = hospital,
            feeNpr = fee,
            experienceYears = wholeNumber(data[FIELD_EXPERIENCE])?.takeIf { it in 0..MAX_EXPERIENCE_YEARS },
            bio = cleanText(data[FIELD_BIO], MAX_BIO_LENGTH).orEmpty(),
            slotMinutes = wholeNumber(data[FIELD_SLOT_MINUTES])?.takeIf { it in SlotMinutesRange } ?: DEFAULT_SLOT_MINUTES,
            weeklySchedule = parseSchedule(data[FIELD_SCHEDULE]),
        )
    }

    /** weekday key -> list of {start, end}. Unknown days and malformed ranges are skipped. */
    fun parseSchedule(value: Any?): Map<Weekday, List<TimeRange>> {
        val days = value as? Map<*, *> ?: return emptyMap()
        val schedule = mutableMapOf<Weekday, List<TimeRange>>()
        for ((key, ranges) in days) {
            val day = Weekday.fromKey(key as? String) ?: continue
            val parsed = (ranges as? List<*>).orEmpty().mapNotNull(::parseRange)
            val ordered = withoutOverlaps(parsed.sortedBy { it.start })
            if (ordered.isNotEmpty()) schedule[day] = ordered
        }
        return Weekday.entries.filter { it in schedule }.associateWith { schedule.getValue(it) }
    }

    private fun parseRange(value: Any?): TimeRange? {
        val range = value as? Map<*, *> ?: return null
        val start = TimeOfDay.parse(range[FIELD_START] as? String) ?: return null
        val end = TimeOfDay.parse(range[FIELD_END] as? String) ?: return null
        return if (start < end) TimeRange(start, end) else null
    }

    /** Keeps each range that starts at or after the previous one's end; later overlaps are dropped. */
    private fun withoutOverlaps(sorted: List<TimeRange>): List<TimeRange> =
        sorted.fold(emptyList()) { kept, range ->
            if (kept.isEmpty() || range.start >= kept.last().end) kept + range else kept
        }

    /** Firestore integers arrive as Long; a whole Double is accepted too, nothing else. */
    private fun wholeNumber(value: Any?): Int? = when (value) {
        is Long -> value.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
        is Int -> value
        is Double -> value.takeIf { it % 1.0 == 0.0 && it in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble() }?.toInt()
        else -> null
    }

    /** One line of text: invisible characters removed, whitespace collapsed; null when empty. */
    private fun cleanLine(value: Any?, maxLength: Int): String? =
        (value as? String)
            ?.let { stripInvisible(it, keepNewlines = false) }
            ?.replace(WHITESPACE, " ")
            ?.trim()
            ?.truncate(maxLength)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    /** Paragraph text: like [cleanLine], but line breaks are kept (at most one blank line). */
    private fun cleanText(value: Any?, maxLength: Int): String? =
        (value as? String)
            ?.let { stripInvisible(it, keepNewlines = true) }
            ?.lines()
            ?.joinToString("\n") { it.replace(WHITESPACE, " ").trim() }
            ?.replace(BLANK_LINES, "\n\n")
            ?.trim()
            ?.truncate(maxLength)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    /** Removes control and format characters (Cc, Cf: bidi overrides, zero-width marks, ...). */
    private fun stripInvisible(text: String, keepNewlines: Boolean): String = buildString {
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            index += Character.charCount(codePoint)
            val type = Character.getType(codePoint)
            val invisible = type == Character.CONTROL.toInt() || type == Character.FORMAT.toInt()
            when {
                codePoint == '\n'.code && keepNewlines -> append('\n')
                // Devanagari needs the zero-width joiners to render some conjuncts correctly.
                codePoint == ZWJ || codePoint == ZWNJ -> appendCodePoint(codePoint)
                invisible -> if (codePoint == '\t'.code || codePoint == '\n'.code) append(' ')
                else -> appendCodePoint(codePoint)
            }
        }
    }

    /** At most [max] chars, never cutting a surrogate pair (emoji, rare scripts) in half. */
    private fun String.truncate(max: Int): String {
        if (length <= max) return this
        val end = if (Character.isHighSurrogate(this[max - 1])) max - 1 else max
        return substring(0, end)
    }

    private const val ZWJ = 0x200D
    private const val ZWNJ = 0x200C
    private val WHITESPACE = Regex("[ \\t\\u00A0\\u2000-\\u200B\\u2028\\u2029\\u3000]+")
    private val BLANK_LINES = Regex("\n{3,}")
}
