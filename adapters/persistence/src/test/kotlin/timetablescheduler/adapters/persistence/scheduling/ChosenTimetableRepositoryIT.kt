package timetablescheduler.adapters.persistence.scheduling

import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import timetablescheduler.adapters.persistence.PostgresTestBase
import timetablescheduler.adapters.persistence.account.ExposedUserAccountRepository
import timetablescheduler.adapters.persistence.curriculum.ExposedClassLevelRepository
import timetablescheduler.adapters.persistence.curriculum.ExposedSchoolClassRepository
import timetablescheduler.adapters.persistence.curriculum.ExposedSubjectRepository
import timetablescheduler.adapters.persistence.room.ExposedRoomRepository
import timetablescheduler.adapters.persistence.school.ExposedSchoolRepository
import timetablescheduler.adapters.persistence.teacher.ExposedTeacherRepository
import timetablescheduler.domain.account.Role
import timetablescheduler.domain.account.UserAccount
import timetablescheduler.domain.curriculum.ClassLevel
import timetablescheduler.domain.curriculum.CurriculumEntry
import timetablescheduler.domain.curriculum.SchoolClass
import timetablescheduler.domain.curriculum.Subject
import timetablescheduler.domain.room.Room
import timetablescheduler.domain.scheduling.DaySlot
import timetablescheduler.domain.scheduling.generation.Alternative
import timetablescheduler.domain.scheduling.generation.AlternativeId
import timetablescheduler.domain.scheduling.generation.ChosenTimetable
import timetablescheduler.domain.scheduling.generation.Generation
import timetablescheduler.domain.scheduling.generation.GenerationStatus
import timetablescheduler.domain.scheduling.solution.CourseKey
import timetablescheduler.domain.scheduling.solution.LessonAssignment
import timetablescheduler.domain.scheduling.solution.ObjectiveValues
import timetablescheduler.domain.school.School
import timetablescheduler.domain.school.SlotKind
import timetablescheduler.domain.school.TimeGrid
import timetablescheduler.domain.teacher.Teacher
import timetablescheduler.domain.validation.ValidationResult
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ChosenTimetableRepositoryIT : PostgresTestBase() {

    private val schools = ExposedSchoolRepository()
    private val accounts = ExposedUserAccountRepository()
    private val rooms = ExposedRoomRepository()
    private val subjects = ExposedSubjectRepository()
    private val levels = ExposedClassLevelRepository()
    private val classes = ExposedSchoolClassRepository()
    private val teachers = ExposedTeacherRepository()
    private val generations = ExposedGenerationRepository()
    private val chosenTimetables = ExposedChosenTimetableRepository()
    private val at = Instant.parse("2026-10-09T08:00:00Z")

    private fun <T> ValidationResult<T>.valid(): T = (this as ValidationResult.Valid).value

    /** A school with one stored generation whose only alternative has one lesson and one unplaced period. */
    private inner class Data {
        val school = School.create("Gymnasium Nord").valid().also { inTransaction { schools.insert(it) } }
        val planner = UserAccount.create("admin", Role.PLANNER, setOf(school.id)).valid().also { inTransaction { accounts.insert(it) } }
        val grid = TimeGrid.empty().withTeachingDays(setOf(DayOfWeek.MONDAY))
            .withSlotAdded("1. Stunde", LocalTime.of(8, 0), LocalTime.of(8, 45), SlotKind.TEACHING).valid()
            .also { inTransaction { schools.saveTimeGrid(school.id, it) } }
        val slot = DaySlot(DayOfWeek.MONDAY, inTransaction { schools.findTimeGrid(school.id) }.slots.single().id)
        val room = Room.create(school.id, "A-101", emptySet(), emptySet()).valid().also { inTransaction { rooms.insert(it) } }
        val math = Subject.create(school.id, "Mathematik", emptySet(), emptySet()).valid().also { inTransaction { subjects.insert(it) } }
        val level = ClassLevel.create(school.id, "5").valid().update("5", listOf(CurriculumEntry(math.id, 2)), setOf(math.id)).valid()
            .also { inTransaction { levels.insert(it) } }
        val class5a = SchoolClass.create(level.id, "a").valid().also { inTransaction { classes.insert(it) } }
        val teacher = Teacher.create(school.id, "Anna Schmidt", "SCHA", 10, 40).valid().also { inTransaction { teachers.insert(it) } }
        val lesson = LessonAssignment(class5a.id, math.id, teacher.id, room.id, slot)
        val generation = Generation.start(school.id, planner.id, at, 1, "a".repeat(64)).valid().endAs(GenerationStatus.FINISHED, at)
            .also { inTransaction { generations.insert(it) } }
        val alternative = Alternative(AlternativeId.new(), generation.id, ObjectiveValues.rounded(0.5, 0.1), listOf(lesson), mapOf(CourseKey(class5a.id, math.id) to 1), emptyMap())
            .also { inTransaction { generations.addAlternative(it) } }

        fun chosen(version: Int) =
            ChosenTimetable(school.id, alternative.id, generation.id, planner.id, at, alternative.lessons, alternative.unplaced, version)
    }

    @Test
    fun `the chosen timetable round-trips with lessons and unplaced periods`() {
        val data = Data()
        assertNull(inTransaction { chosenTimetables.find(data.school.id) })

        assertEquals(true, inTransaction { chosenTimetables.save(data.chosen(0), expectedVersion = null) })

        assertEquals(data.chosen(0), inTransaction { chosenTimetables.find(data.school.id) })
    }

    @Test
    fun `a concurrent choice is detected by the version (FR-028)`() {
        val data = Data()
        inTransaction { chosenTimetables.save(data.chosen(0), expectedVersion = null) }

        assertEquals(false, inTransaction { chosenTimetables.save(data.chosen(0), expectedVersion = null) })
        assertEquals(true, inTransaction { chosenTimetables.save(data.chosen(1), expectedVersion = 0) })
        assertEquals(false, inTransaction { chosenTimetables.save(data.chosen(1), expectedVersion = 0) })
        assertEquals(1, inTransaction { chosenTimetables.find(data.school.id) }!!.version)
    }

    @Test
    fun `master data used by the chosen timetable and its generation cannot be deleted (FR-016, FR-024)`() {
        val data = Data()
        inTransaction { chosenTimetables.save(data.chosen(0), expectedVersion = null) }

        assertFailsWith<ExposedSQLException> { inTransaction { teachers.delete(data.teacher.id) } }
        assertFailsWith<ExposedSQLException> { inTransaction { rooms.delete(data.room.id) } }
        assertFailsWith<ExposedSQLException> { inTransaction { generations.removeAlternative(data.alternative.id) } }
    }

    @Test
    fun `stability, changed lessons and the reference of a generation round-trip (FR-006b)`() {
        val data = Data()
        val next = Generation.start(data.school.id, data.planner.id, at.plusSeconds(60), 1, "b".repeat(64)).valid().copy(referenceAlternativeId = data.alternative.id)
        val alternative = Alternative(
            AlternativeId.new(), next.id, ObjectiveValues.rounded(1.0, 0.2, stability = 0.5), listOf(data.lesson), emptyMap(), emptyMap(), changedLessons = 1,
        )

        inTransaction {
            generations.insert(next)
            generations.addAlternative(alternative)
        }

        assertEquals(data.alternative.id, inTransaction { generations.findById(next.id) }!!.referenceAlternativeId)
        assertEquals(alternative, inTransaction { generations.findAlternative(alternative.id) })
    }
}
