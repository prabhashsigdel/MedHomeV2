package com.medhome.nepal.ui.admin

import com.medhome.nepal.domain.TimeOfDay
import com.medhome.nepal.domain.TimeRange
import com.medhome.nepal.domain.Weekday
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleEditorTest {

    private fun t(text: String) = requireNotNull(TimeOfDay.parse(text))
    private fun r(start: String, end: String) = RangeInput(t(start), t(end))

    @Test
    fun `the first range of a day is a morning, later ones start where the last one ends`() {
        val one = ScheduleEditor.add(emptyMap(), Weekday.MONDAY)
        assertEquals(listOf(r("09:00", "13:00")), one[Weekday.MONDAY])

        val two = ScheduleEditor.add(one, Weekday.MONDAY)
        assertEquals(r("13:00", "14:00"), two.getValue(Weekday.MONDAY)[1])
    }

    @Test
    fun `a day takes at most 3 ranges`() {
        var schedule: ScheduleInput = emptyMap()
        repeat(3) { schedule = ScheduleEditor.add(schedule, Weekday.SUNDAY) }
        assertFalse(ScheduleEditor.canAdd(schedule, Weekday.SUNDAY))
        assertSame(schedule, ScheduleEditor.add(schedule, Weekday.SUNDAY))
        assertEquals(3, ScheduleEditor.ranges(schedule, Weekday.SUNDAY).size)
        assertTrue(ScheduleEditor.canAdd(schedule, Weekday.MONDAY))
    }

    @Test
    fun `a range added late in the day stops at 23 59`() {
        val schedule = mapOf(Weekday.FRIDAY to listOf(r("20:00", "23:30")))
        assertEquals(r("23:30", "23:59"), ScheduleEditor.add(schedule, Weekday.FRIDAY).getValue(Weekday.FRIDAY)[1])
    }

    @Test
    fun `removing the last range closes the day`() {
        val schedule = mapOf(Weekday.MONDAY to listOf(r("09:00", "13:00"), r("14:00", "17:00")))
        val one = ScheduleEditor.remove(schedule, Weekday.MONDAY, 0)
        assertEquals(listOf(r("14:00", "17:00")), one[Weekday.MONDAY])
        assertFalse(Weekday.MONDAY in ScheduleEditor.remove(one, Weekday.MONDAY, 0))
        assertSame(one, ScheduleEditor.remove(one, Weekday.MONDAY, 5))
    }

    @Test
    fun `start and end change one range only and never the original`() {
        val schedule = mapOf(Weekday.MONDAY to listOf(r("09:00", "13:00"), r("14:00", "17:00")))
        val changed = ScheduleEditor.setEnd(ScheduleEditor.setStart(schedule, Weekday.MONDAY, 1, t("15:00")), Weekday.MONDAY, 1, t("18:30"))
        assertEquals(listOf(r("09:00", "13:00"), r("15:00", "18:30")), changed[Weekday.MONDAY])
        assertEquals(listOf(r("09:00", "13:00"), r("14:00", "17:00")), schedule[Weekday.MONDAY])
    }

    @Test
    fun `a range must end after it starts`() {
        assertEquals(listOf(RangeProblem.END_NOT_AFTER_START), ScheduleEditor.problems(listOf(r("13:00", "09:00"))))
        assertEquals(listOf(RangeProblem.END_NOT_AFTER_START), ScheduleEditor.problems(listOf(r("09:00", "09:00"))))
        assertEquals(listOf<RangeProblem?>(null), ScheduleEditor.problems(listOf(r("09:00", "09:01"))))
    }

    @Test
    fun `overlapping ranges are both marked, touching ones are fine`() {
        assertEquals(
            listOf(RangeProblem.OVERLAPS, RangeProblem.OVERLAPS, null),
            ScheduleEditor.problems(listOf(r("09:00", "13:00"), r("12:59", "15:00"), r("15:00", "17:00"))),
        )
        assertEquals(listOf<RangeProblem?>(null, null), ScheduleEditor.problems(listOf(r("13:00", "17:00"), r("09:00", "13:00"))))
    }

    @Test
    fun `a backwards range doesn't make the others overlap`() {
        assertEquals(
            listOf(null, RangeProblem.END_NOT_AFTER_START),
            ScheduleEditor.problems(listOf(r("09:00", "13:00"), r("12:00", "10:00"))),
        )
    }

    @Test
    fun `the schedule to save is sorted, drops closed days and is null while anything is wrong`() {
        val input = mapOf(
            Weekday.TUESDAY to listOf(r("14:00", "17:00"), r("09:00", "13:00")),
            Weekday.MONDAY to emptyList(),
        )
        assertEquals(
            mapOf(Weekday.TUESDAY to listOf(TimeRange(t("09:00"), t("13:00")), TimeRange(t("14:00"), t("17:00")))),
            ScheduleEditor.toSchedule(input),
        )
        assertNull(ScheduleEditor.toSchedule(mapOf(Weekday.SUNDAY to listOf(r("10:00", "09:00")))))
        assertTrue(ScheduleEditor.hasProblems(mapOf(Weekday.SUNDAY to List(4) { r("0$it:00", "0$it:30") })))
    }

    @Test
    fun `a saved schedule loads back unchanged`() {
        val saved = mapOf(Weekday.SATURDAY to listOf(TimeRange(t("00:00"), t("23:59"))))
        assertEquals(saved, ScheduleEditor.toSchedule(ScheduleEditor.from(saved)))
    }
}
