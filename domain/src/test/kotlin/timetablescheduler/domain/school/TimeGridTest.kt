package timetablescheduler.domain.school

import timetablescheduler.domain.validation.ValidationError
import timetablescheduler.domain.validation.ValidationResult
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TimeGridTest {

    private val mondayToFriday = DayOfWeek.entries.filter { it <= FRIDAY }.toSet()

    private fun time(text: String) = LocalTime.parse(text)

    private fun TimeGrid.addValid(label: String, start: String, end: String, kind: SlotKind = SlotKind.TEACHING): TimeGrid =
        assertIs<ValidationResult.Valid<TimeGrid>>(withSlotAdded(label, time(start), time(end), kind)).value

    private fun gridWithMorning(): TimeGrid =
        TimeGrid.empty().withTeachingDays(mondayToFriday)
            .addValid("1. Stunde", "08:00", "08:45")
            .addValid("2. Stunde", "08:50", "09:35")
            .addValid("Große Pause", "09:35", "09:55", SlotKind.BREAK)
            .addValid("3. Stunde", "09:55", "10:40")

    @Test
    fun `slots are ordered chronologically regardless of insertion order`() {
        val grid = TimeGrid.empty()
            .addValid("3. Stunde", "09:55", "10:40")
            .addValid("1. Stunde", "08:00", "08:45")

        assertEquals(listOf("1. Stunde", "3. Stunde"), grid.slots.map { it.label })
    }

    @Test
    fun `end time must be after start time`() {
        val sameTime = TimeGrid.empty().withSlotAdded("1. Stunde", time("08:00"), time("08:00"), SlotKind.TEACHING)
        val reversed = TimeGrid.empty().withSlotAdded("1. Stunde", time("09:00"), time("08:00"), SlotKind.TEACHING)

        assertTrue(ValidationError.EndNotAfterStart in assertIs<ValidationResult.Invalid>(sameTime).errors)
        assertTrue(ValidationError.EndNotAfterStart in assertIs<ValidationResult.Invalid>(reversed).errors)
    }

    @Test
    fun `overlapping slot is rejected`() {
        val result = gridWithMorning().withSlotAdded("Extra", time("08:30"), time("09:15"), SlotKind.TEACHING)

        assertTrue(ValidationError.Overlap in assertIs<ValidationResult.Invalid>(result).errors)
    }

    @Test
    fun `touching slots are allowed`() {
        assertEquals(4, gridWithMorning().slots.size)
    }

    @Test
    fun `slot label must not be blank or longer than 40 characters`() {
        val blank = TimeGrid.empty().withSlotAdded(" ", time("08:00"), time("08:45"), SlotKind.TEACHING)
        val tooLong = TimeGrid.empty().withSlotAdded("x".repeat(41), time("08:00"), time("08:45"), SlotKind.TEACHING)

        assertTrue(ValidationError.BlankValue(TimeSlot.FIELD_LABEL) in assertIs<ValidationResult.Invalid>(blank).errors)
        assertTrue(ValidationError.TooLong(TimeSlot.FIELD_LABEL, 40) in assertIs<ValidationResult.Invalid>(tooLong).errors)
    }

    @Test
    fun `updating a slot may keep its own time range but must not overlap others`() {
        val grid = gridWithMorning()
        val first = grid.slots.first()

        val relabelled = grid.withSlotUpdated(first.id, "Erste", first.startTime, first.endTime, SlotKind.TEACHING)
        val overlapping = grid.withSlotUpdated(first.id, "Erste", time("08:00"), time("09:00"), SlotKind.TEACHING)

        assertEquals("Erste", assertIs<ValidationResult.Valid<TimeGrid>>(relabelled).value.slots.first().label)
        assertTrue(ValidationError.Overlap in assertIs<ValidationResult.Invalid>(overlapping).errors)
    }

    @Test
    fun `teaching slots per week count only teaching slots on teaching days without exclusions`() {
        val grid = gridWithMorning()
        val thirdPeriod = grid.slots.last()
        val shortFriday = assertIs<ValidationResult.Valid<TimeGrid>>(grid.withExclusions(thirdPeriod.id, setOf(FRIDAY))).value

        assertEquals(15, grid.teachingSlotsPerWeek())
        assertEquals(14, shortFriday.teachingSlotsPerWeek())
        assertEquals(2, shortFriday.teachingSlotsOn(FRIDAY).size)
    }

    @Test
    fun `exclusion is only allowed for teaching days`() {
        val grid = gridWithMorning()

        val result = grid.withExclusions(grid.slots.first().id, setOf(SATURDAY))

        assertTrue(ValidationError.InvalidReference(TimeGrid.FIELD_DAY_OF_WEEK) in assertIs<ValidationResult.Invalid>(result).errors)
    }

    @Test
    fun `removing a teaching day keeps its exclusions but ignores them`() {
        val grid = gridWithMorning()
        val thirdPeriod = grid.slots.last()
        val shortFriday = (grid.withExclusions(thirdPeriod.id, setOf(FRIDAY)) as ValidationResult.Valid).value

        val withoutFriday = shortFriday.withTeachingDays(mondayToFriday - FRIDAY)

        assertTrue(SlotDayExclusion(thirdPeriod.id, FRIDAY) in withoutFriday.exclusions)
        assertEquals(12, withoutFriday.teachingSlotsPerWeek())
        assertEquals(14, withoutFriday.withTeachingDays(mondayToFriday).teachingSlotsPerWeek())
    }

    @Test
    fun `removing a slot removes its exclusions`() {
        val grid = gridWithMorning()
        val thirdPeriod = grid.slots.last()
        val shortFriday = (grid.withExclusions(thirdPeriod.id, setOf(FRIDAY)) as ValidationResult.Valid).value

        val withoutThird = shortFriday.withSlotRemoved(thirdPeriod.id)

        assertEquals(3, withoutThird.slots.size)
        assertTrue(withoutThird.exclusions.isEmpty())
    }

    @Test
    fun `slots on a day are the slots without exclusion for that day`() {
        val grid = gridWithMorning()
        val first = grid.slots.first()
        val withExclusion = (grid.withExclusions(first.id, setOf(MONDAY)) as ValidationResult.Valid).value

        assertEquals(3, withExclusion.slotsOn(MONDAY).size)
        assertEquals(4, withExclusion.slotsOn(FRIDAY).size)
    }
}
