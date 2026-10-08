package timetablescheduler.adapters.persistence.teacher

import timetablescheduler.adapters.persistence.PostgresTestBase
import timetablescheduler.adapters.persistence.curriculum.ExposedSubjectRepository
import timetablescheduler.adapters.persistence.school.ExposedSchoolRepository
import timetablescheduler.domain.curriculum.Subject
import timetablescheduler.domain.school.School
import timetablescheduler.domain.school.SlotKind
import timetablescheduler.domain.school.TimeGrid
import timetablescheduler.domain.teacher.Teacher
import timetablescheduler.domain.teacher.Unavailability
import timetablescheduler.domain.validation.ValidationResult
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TeacherRepositoryIT : PostgresTestBase() {

    private val schools = ExposedSchoolRepository()
    private val subjects = ExposedSubjectRepository()
    private val teachers = ExposedTeacherRepository()
    private val slotUsage = ExposedTimeSlotUsage()

    private fun <T> ValidationResult<T>.valid(): T = (this as ValidationResult.Valid).value

    private val school = School.create("Gymnasium Nord").valid()
    private val grid = TimeGrid.empty().withTeachingDays(setOf(MONDAY, FRIDAY))
        .withSlotAdded("1. Stunde", LocalTime.of(8, 0), LocalTime.of(8, 45), SlotKind.TEACHING).valid()
        .withSlotAdded("2. Stunde", LocalTime.of(8, 50), LocalTime.of(9, 35), SlotKind.TEACHING).valid()
    private val first = grid.slots[0]
    private val second = grid.slots[1]
    private val mathe = Subject.create(school.id, "Mathematik", emptySet(), emptySet()).valid()
    private val chemie = Subject.create(school.id, "Chemie", emptySet(), emptySet()).valid()

    private fun arrangeSchool() = inTransaction {
        schools.insert(school)
        schools.saveTimeGrid(school.id, grid)
        subjects.insert(mathe)
        subjects.insert(chemie)
    }

    private fun teacher(name: String, shortCode: String): Teacher =
        Teacher.create(school.id, name, shortCode, weeklyLoad = 2, teachingSlotsPerWeek = 4).valid()
            .withQualifications(setOf(mathe.id), setOf(mathe.id, chemie.id)).valid()
            .withUnavailability(setOf(Unavailability(FRIDAY, second.id), Unavailability(MONDAY, first.id)), grid).valid()

    @Test
    fun `round trip with qualifications and unavailability`() {
        arrangeSchool()
        val anna = teacher("Anna Schmidt", "SCHA")
        inTransaction { teachers.insert(anna) }

        assertEquals(anna, inTransaction { teachers.findById(anna.id) })
        assertEquals(listOf(anna), inTransaction { teachers.findBySchool(school.id) })
        assertEquals(anna, inTransaction { teachers.findByShortCode("scha") })
        assertEquals(listOf(anna), inTransaction { teachers.findQualifiedFor(mathe.id) })
        assertEquals(emptyList(), inTransaction { teachers.findQualifiedFor(chemie.id) })
    }

    @Test
    fun `update replaces qualifications and unavailability with optimistic locking`() {
        arrangeSchool()
        val anna = teacher("Anna Schmidt", "SCHA")
        inTransaction { teachers.insert(anna) }
        val changed = anna.withQualifications(setOf(chemie.id), setOf(mathe.id, chemie.id)).valid()
            .withUnavailability(emptySet(), grid).valid()

        assertTrue(inTransaction { teachers.update(changed) })
        assertFalse(inTransaction { teachers.update(changed) })

        val reloaded = inTransaction { teachers.findById(anna.id) }!!
        assertEquals(setOf(chemie.id), reloaded.qualifiedSubjectIds)
        assertEquals(emptySet(), reloaded.unavailability)
        assertEquals(1, reloaded.version)
    }

    @Test
    fun `short code is unique in the database`() {
        arrangeSchool()
        inTransaction { teachers.insert(teacher("Anna Schmidt", "SCHA")) }

        assertTrue(runCatching { inTransaction { teachers.insert(teacher("Anna Schmidt", "SCHA")) } }.isFailure)
    }

    @Test
    fun `slot usage counts and removes the unavailabilities of a slot`() {
        arrangeSchool()
        val anna = teacher("Anna Schmidt", "SCHA")
        val max = teacher("Max Meyer", "MEY")
        inTransaction {
            teachers.insert(anna)
            teachers.insert(max)
        }

        assertEquals(2, inTransaction { slotUsage.countUnavailabilities(second.id) })
        inTransaction { slotUsage.deleteUnavailabilities(second.id) }

        assertEquals(0, inTransaction { slotUsage.countUnavailabilities(second.id) })
        assertEquals(setOf(Unavailability(MONDAY, first.id)), inTransaction { teachers.findById(anna.id) }!!.unavailability)
    }

    @Test
    fun `removing a slot from the grid removes its unavailabilities`() {
        arrangeSchool()
        val anna = teacher("Anna Schmidt", "SCHA")
        inTransaction { teachers.insert(anna) }

        inTransaction { schools.saveTimeGrid(school.id, grid.withSlotRemoved(second.id)) }

        assertEquals(setOf(Unavailability(MONDAY, first.id)), inTransaction { teachers.findById(anna.id) }!!.unavailability)
    }

    @Test
    fun `deleting a teacher removes qualifications and unavailability`() {
        arrangeSchool()
        val anna = teacher("Anna Schmidt", "SCHA")
        inTransaction { teachers.insert(anna) }

        inTransaction { teachers.delete(anna.id) }

        assertNull(inTransaction { teachers.findById(anna.id) })
        assertEquals(0, inTransaction { slotUsage.countUnavailabilities(second.id) })
        assertEquals(emptyList(), inTransaction { teachers.findQualifiedFor(mathe.id) })
    }
}
