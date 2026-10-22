package timetablescheduler.domain.teacher

import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.common.SubjectId
import timetablescheduler.domain.school.SlotKind
import timetablescheduler.domain.school.TimeGrid
import timetablescheduler.domain.validation.ValidationError
import timetablescheduler.domain.validation.ValidationResult
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TeacherTest {
    private val schoolId = SchoolId.new()
    private val mathe = SubjectId.new()
    private val chemie = SubjectId.new()

    private fun <T> ValidationResult<T>.valid(): T = assertIs<ValidationResult.Valid<T>>(this).value

    private fun errorsOf(result: ValidationResult<*>): List<ValidationError> = assertIs<ValidationResult.Invalid>(result).errors

    private val grid: TimeGrid =
        TimeGrid
            .empty()
            .withTeachingDays(setOf(MONDAY, FRIDAY))
            .withSlotAdded("1. Stunde", LocalTime.of(8, 0), LocalTime.of(8, 45), SlotKind.TEACHING)
            .valid()
            .withSlotAdded("Pause", LocalTime.of(8, 45), LocalTime.of(9, 0), SlotKind.BREAK)
            .valid()
            .withSlotAdded("6. Stunde", LocalTime.of(12, 30), LocalTime.of(13, 15), SlotKind.TEACHING)
            .valid()
    private val firstPeriod = grid.slots.first()
    private val breakSlot = grid.slots[1]
    private val sixthPeriod = grid.slots.last()

    private val anna = Teacher.create(schoolId, " Anna Schmidt ", " scha ", weeklyLoad = 2, teachingSlotsPerWeek = 4).valid()

    @Test
    fun `teacher keeps trimmed name and an upper case short code`() {
        assertEquals("Anna Schmidt", anna.name)
        assertEquals("SCHA", anna.shortCode)
        assertEquals(2, anna.weeklyLoad)
        assertEquals(emptySet(), anna.qualifiedSubjectIds)
        assertEquals(emptySet(), anna.unavailability)
    }

    @Test
    fun `name must have 1 to 120 characters`() {
        assertEquals(listOf(ValidationError.BlankValue(Teacher.FIELD_NAME)), errorsOf(Teacher.create(schoolId, "", "SCHA", 0, 10)))
        assertEquals(
            listOf(ValidationError.TooLong(Teacher.FIELD_NAME, 120)),
            errorsOf(Teacher.create(schoolId, "x".repeat(121), "SCHA", 0, 10)),
        )
    }

    @Test
    fun `short code has 2 to 8 letters or digits`() {
        val invalidFormat = listOf(ValidationError.InvalidFormat(Teacher.FIELD_SHORT_CODE))

        assertEquals(invalidFormat, errorsOf(Teacher.create(schoolId, "Anna", "S", 0, 10)))
        assertEquals(invalidFormat, errorsOf(Teacher.create(schoolId, "Anna", "SCHMIDTAN", 0, 10)))
        assertEquals(invalidFormat, errorsOf(Teacher.create(schoolId, "Anna", "SC-A", 0, 10)))
        assertEquals("SCH2", Teacher.create(schoolId, "Anna", "sch2", 0, 10).valid().shortCode)
    }

    @Test
    fun `weekly load must be between 0 and the teaching slots of the school`() {
        val outOfRange = listOf(ValidationError.OutOfRange(Teacher.FIELD_WEEKLY_LOAD, 0, 38))

        assertEquals(outOfRange, errorsOf(Teacher.create(schoolId, "Anna", "SCHA", weeklyLoad = 40, teachingSlotsPerWeek = 38)))
        assertEquals(outOfRange, errorsOf(Teacher.create(schoolId, "Anna", "SCHA", weeklyLoad = -1, teachingSlotsPerWeek = 38)))
        assertEquals(38, Teacher.create(schoolId, "Anna", "SCHA", weeklyLoad = 38, teachingSlotsPerWeek = 38).valid().weeklyLoad)
        assertEquals(0, Teacher.create(schoolId, "Anna", "SCHA", weeklyLoad = 0, teachingSlotsPerWeek = 38).valid().weeklyLoad)
    }

    @Test
    fun `general data update keeps qualifications and unavailability`() {
        val prepared =
            anna
                .withQualifications(setOf(mathe), setOf(mathe))
                .valid()
                .withUnavailability(setOf(Unavailability(FRIDAY, sixthPeriod.id)), grid)
                .valid()

        val updated = prepared.withGeneralData("Anna Schmidt-Berg", "SCHB", 3, teachingSlotsPerWeek = 4).valid()

        assertEquals("SCHB", updated.shortCode)
        assertEquals(prepared.qualifiedSubjectIds, updated.qualifiedSubjectIds)
        assertEquals(prepared.unavailability, updated.unavailability)
    }

    @Test
    fun `qualifications must be subjects of the same school`() {
        assertEquals(setOf(mathe, chemie), anna.withQualifications(setOf(mathe, chemie), setOf(mathe, chemie)).valid().qualifiedSubjectIds)
        assertEquals(
            listOf(ValidationError.InvalidReference(Teacher.FIELD_SUBJECTS)),
            errorsOf(anna.withQualifications(setOf(SubjectId.new()), setOf(mathe))),
        )
    }

    @Test
    fun `unavailability must be a teaching slot that exists on that day`() {
        val friday6 = Unavailability(FRIDAY, sixthPeriod.id)
        val excludedGrid = grid.withExclusions(sixthPeriod.id, setOf(FRIDAY)).valid()
        val invalid = listOf(ValidationError.InvalidReference(Teacher.FIELD_SLOT))

        assertEquals(setOf(friday6), anna.withUnavailability(setOf(friday6), grid).valid().unavailability)
        assertEquals(invalid, errorsOf(anna.withUnavailability(setOf(friday6), excludedGrid)))
        assertEquals(invalid, errorsOf(anna.withUnavailability(setOf(Unavailability(SATURDAY, firstPeriod.id)), grid)))
        assertEquals(invalid, errorsOf(anna.withUnavailability(setOf(Unavailability(MONDAY, breakSlot.id)), grid)))
    }

    @Test
    fun `entries for days that are no longer teaching days are kept when the grid is saved again`() {
        val withFriday = anna.withUnavailability(setOf(Unavailability(FRIDAY, sixthPeriod.id)), grid).valid()
        val mondayOnlyGrid = grid.withTeachingDays(setOf(MONDAY))

        val updated = withFriday.withUnavailability(setOf(Unavailability(MONDAY, firstPeriod.id)), mondayOnlyGrid).valid()

        assertEquals(setOf(Unavailability(MONDAY, firstPeriod.id), Unavailability(FRIDAY, sixthPeriod.id)), updated.unavailability)
    }

    @Test
    fun `load above the grid is detected for the readiness check`() {
        assertEquals(0, anna.loadExceedingGrid(teachingSlotsPerWeek = 4))
        assertEquals(1, anna.loadExceedingGrid(teachingSlotsPerWeek = 1))
    }
}
