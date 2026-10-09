package com.medhome.nepal.ui.admin

import com.medhome.nepal.data.DoctorMapper
import com.medhome.nepal.domain.TimeOfDay
import com.medhome.nepal.domain.TimeRange
import com.medhome.nepal.domain.Weekday

/** Opening hours as being edited: unlike [TimeRange], it may be empty or backwards until fixed. */
data class RangeInput(val start: TimeOfDay, val end: TimeOfDay)

/** What is wrong with one range of a day. */
enum class RangeProblem {
    /** The end is not after the start. */
    END_NOT_AFTER_START,

    /** It shares time with another range of the same day (touching is fine: 09:00-13:00, 13:00-17:00). */
    OVERLAPS,
}

/** A week of opening hours being edited. Days without hours are absent or empty. */
typealias ScheduleInput = Map<Weekday, List<RangeInput>>

/**
 * The weekly schedule editor's rules, as pure functions over an immutable [ScheduleInput]: up
 * to [MAX_RANGES] ranges a day (what firestore.rules and [DoctorMapper] accept), each ending
 * after it starts, none overlapping. Times are whole minutes, so always "on the minute".
 */
object ScheduleEditor {
    const val MAX_RANGES = DoctorMapper.MAX_RANGES_PER_DAY

    /** The first range of a day: a morning. */
    private val DEFAULT_START = TimeOfDay(9 * TimeOfDay.MINUTES_PER_HOUR)
    private val DEFAULT_END = TimeOfDay(13 * TimeOfDay.MINUTES_PER_HOUR)

    /** A later range starts where the day's last one ends and lasts an hour (to 23:59 at most). */
    private const val NEW_RANGE_MINUTES = 60
    private val LAST_MINUTE = TimeOfDay(TimeOfDay.MINUTES_PER_DAY - 1)

    fun from(schedule: Map<Weekday, List<TimeRange>>): ScheduleInput =
        schedule.mapValues { (_, ranges) -> ranges.map { RangeInput(it.start, it.end) } }

    fun ranges(schedule: ScheduleInput, day: Weekday): List<RangeInput> = schedule[day].orEmpty()

    fun canAdd(schedule: ScheduleInput, day: Weekday): Boolean = ranges(schedule, day).size < MAX_RANGES

    /** Adds a range to [day] (unchanged when it already has [MAX_RANGES]). */
    fun add(schedule: ScheduleInput, day: Weekday): ScheduleInput {
        val current = ranges(schedule, day)
        if (current.size >= MAX_RANGES) return schedule
        val added = if (current.isEmpty()) {
            RangeInput(DEFAULT_START, DEFAULT_END)
        } else {
            val start = current.maxOf { it.end }
            RangeInput(start, TimeOfDay(minOf(start.minutes + NEW_RANGE_MINUTES, LAST_MINUTE.minutes)))
        }
        return schedule + (day to current + added)
    }

    fun remove(schedule: ScheduleInput, day: Weekday, index: Int): ScheduleInput {
        val current = ranges(schedule, day)
        if (index !in current.indices) return schedule
        val left = current.filterIndexed { i, _ -> i != index }
        return if (left.isEmpty()) schedule - day else schedule + (day to left)
    }

    fun setStart(schedule: ScheduleInput, day: Weekday, index: Int, time: TimeOfDay): ScheduleInput =
        replace(schedule, day, index) { it.copy(start = time) }

    fun setEnd(schedule: ScheduleInput, day: Weekday, index: Int, time: TimeOfDay): ScheduleInput =
        replace(schedule, day, index) { it.copy(end = time) }

    /** The problem of each range of a day, in order (null: fine). */
    fun problems(ranges: List<RangeInput>): List<RangeProblem?> = ranges.mapIndexed { i, range ->
        when {
            range.end <= range.start -> RangeProblem.END_NOT_AFTER_START
            ranges.withIndex().any { (j, other) -> j != i && other.start < other.end && overlap(range, other) } ->
                RangeProblem.OVERLAPS
            else -> null
        }
    }

    fun hasProblems(schedule: ScheduleInput): Boolean =
        schedule.values.any { ranges -> ranges.size > MAX_RANGES || problems(ranges).any { it != null } }

    /** The schedule to save: each day's ranges sorted, empty days left out. Null while any range has a problem. */
    fun toSchedule(schedule: ScheduleInput): Map<Weekday, List<TimeRange>>? {
        if (hasProblems(schedule)) return null
        return Weekday.entries
            .filter { ranges(schedule, it).isNotEmpty() }
            .associateWith { day -> ranges(schedule, day).sortedBy { it.start }.map { TimeRange(it.start, it.end) } }
    }

    private fun overlap(a: RangeInput, b: RangeInput): Boolean = a.start < b.end && b.start < a.end

    private fun replace(
        schedule: ScheduleInput,
        day: Weekday,
        index: Int,
        change: (RangeInput) -> RangeInput,
    ): ScheduleInput {
        val current = ranges(schedule, day)
        if (index !in current.indices) return schedule
        return schedule + (day to current.mapIndexed { i, range -> if (i == index) change(range) else range })
    }
}
