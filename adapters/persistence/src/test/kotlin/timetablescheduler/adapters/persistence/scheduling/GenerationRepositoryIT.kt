package timetablescheduler.adapters.persistence.scheduling

import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import timetablescheduler.adapters.persistence.PostgresTestBase
import timetablescheduler.adapters.persistence.account.ExposedUserAccountRepository
import timetablescheduler.adapters.persistence.school.ExposedSchoolRepository
import timetablescheduler.domain.account.Role
import timetablescheduler.domain.account.UserAccount
import timetablescheduler.domain.common.RoomId
import timetablescheduler.domain.common.SchoolClassId
import timetablescheduler.domain.common.SubjectId
import timetablescheduler.domain.common.TeacherId
import timetablescheduler.domain.common.TimeSlotId
import timetablescheduler.domain.scheduling.DaySlot
import timetablescheduler.domain.scheduling.generation.Alternative
import timetablescheduler.domain.scheduling.generation.AlternativeId
import timetablescheduler.domain.scheduling.generation.Generation
import timetablescheduler.domain.scheduling.generation.GenerationStatus
import timetablescheduler.domain.scheduling.solution.CourseKey
import timetablescheduler.domain.scheduling.solution.LessonAssignment
import timetablescheduler.domain.scheduling.solution.ObjectiveValues
import timetablescheduler.domain.school.School
import timetablescheduler.domain.validation.ValidationResult
import java.time.DayOfWeek
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class GenerationRepositoryIT : PostgresTestBase() {

    private val schools = ExposedSchoolRepository()
    private val accounts = ExposedUserAccountRepository()
    private val generations = ExposedGenerationRepository()
    private val startedAt = Instant.parse("2026-10-09T08:00:00Z")

    private fun <T> ValidationResult<T>.valid(): T = (this as ValidationResult.Valid).value

    // Lazy: the tables are emptied before each test, after the test instance was created.
    private val school: School by lazy { School.create("Gymnasium Nord").valid().also { inTransaction { schools.insert(it) } } }
    private val planner: UserAccount by lazy {
        UserAccount.create("admin", Role.PLANNER, setOf(school.id)).valid().also { inTransaction { accounts.insert(it) } }
    }

    private fun storedGeneration(): Generation =
        Generation.start(school.id, planner.id, startedAt, 10, "a".repeat(64)).valid().also { inTransaction { generations.insert(it) } }

    private fun alternativeOf(generation: Generation, classFill: Double = 0.75): Alternative {
        val course = CourseKey(SchoolClassId.new(), SubjectId.new())
        val lesson = LessonAssignment(course.classId, course.subjectId, TeacherId.new(), RoomId.new(), DaySlot(DayOfWeek.MONDAY, TimeSlotId.new()))
        return Alternative(
            id = AlternativeId.new(),
            generationId = generation.id,
            objectiveValues = ObjectiveValues.rounded(classFill, 0.5, otherSoft = 1.0),
            lessons = listOf(lesson, lesson.copy(daySlot = DaySlot(DayOfWeek.TUESDAY, lesson.daySlot.slotId))),
            unplaced = mapOf(course to 2),
            softViolations = emptyMap(),
        )
    }

    @Test
    fun `generation round trip with progress and end`() {
        val generation = storedGeneration()

        assertEquals(generation, inTransaction { generations.findById(generation.id) })
        assertEquals(generation, inTransaction { generations.findRunning(school.id) })

        inTransaction { generations.updateProgress(generation.id, 3) }
        val ended = generation.copy(alternativesFound = 3).endAs(GenerationStatus.FINISHED, startedAt.plusSeconds(600))
        inTransaction { generations.saveEnd(ended) }

        assertEquals(ended, inTransaction { generations.findById(generation.id) })
        assertNull(inTransaction { generations.findRunning(school.id) })
        assertEquals(listOf(ended), inTransaction { generations.findBySchool(school.id) })
    }

    @Test
    fun `only one generation per school can run (FR-011)`() {
        storedGeneration()

        assertFailsWith<ExposedSQLException> { storedGeneration() }
    }

    @Test
    fun `alternatives round trip with lessons and unplaced periods`() {
        val generation = storedGeneration()
        val alternative = alternativeOf(generation)

        inTransaction { generations.addAlternative(alternative) }

        assertEquals(listOf(alternative), inTransaction { generations.findAlternatives(generation.id) })
        assertEquals(alternative.lessons.toSet(), inTransaction { generations.findAlternative(alternative.id) }!!.lessons.toSet())
    }

    @Test
    fun `removing an evicted alternative removes its lessons`() {
        val generation = storedGeneration()
        val kept = alternativeOf(generation, classFill = 1.0)
        val evicted = alternativeOf(generation, classFill = 0.5)
        inTransaction {
            generations.addAlternative(kept)
            generations.addAlternative(evicted)
        }

        inTransaction { generations.removeAlternative(evicted.id) }

        assertEquals(listOf(kept), inTransaction { generations.findAlternatives(generation.id) })
        assertNull(inTransaction { generations.findAlternative(evicted.id) })
    }

    @Test
    fun `running generations fail at start-up and keep their alternatives`() {
        val generation = storedGeneration()
        val alternative = alternativeOf(generation)
        inTransaction { generations.addAlternative(alternative) }

        assertEquals(1, inTransaction { generations.failRunning(startedAt.plusSeconds(30)) })

        assertEquals(GenerationStatus.FAILED, inTransaction { generations.findById(generation.id) }!!.status)
        assertEquals(listOf(alternative), inTransaction { generations.findAlternatives(generation.id) })
    }
}
