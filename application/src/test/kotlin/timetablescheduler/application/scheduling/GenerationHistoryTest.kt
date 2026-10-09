package timetablescheduler.application.scheduling

import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.error.Dependant
import timetablescheduler.application.error.DependantType
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.domain.scheduling.evaluation.HardRule
import timetablescheduler.domain.scheduling.generation.Generation
import timetablescheduler.domain.scheduling.solution.TimetableCandidate
import timetablescheduler.domain.teacher.Unavailability
import java.time.DayOfWeek.MONDAY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Outdated marks, conflicts of outdated alternatives and deleting generations (002 US4, FR-023, FR-024, G8). */
class GenerationHistoryTest {

    private val test = SchedulingTestSchool()
    private val world = test.world
    private val scheduling get() = test.scheduling

    private fun <T> UseCaseResult<T>.value(): T = assertIs<UseCaseResult.Success<T>>(this).value

    private fun UseCaseResult<*>.error(): ApplicationError = assertIs<UseCaseResult.Failure>(this).error

    private fun generationWith(vararg candidates: TimetableCandidate): Generation {
        world.solver.candidates = { candidates.toList() }
        return scheduling.start.execute(test.admin.id, test.school.id, StartRequest(timeLimitMinutes = 1)).value()
    }

    private fun listed(generation: Generation) =
        scheduling.list.execute(test.admin.id, test.school.id).value().generations.single { it.generation.id == generation.id }

    @Test
    fun `a generation is outdated once master data or constraints change (G8)`() {
        val generation = generationWith(test.complete())
        assertFalse(listed(generation).outdated)
        assertFalse(scheduling.get.execute(test.admin.id, test.school.id, generation.id).value().summary.outdated)

        world.markUnavailable(world.teachers.findById(test.schmidt.id)!!, Unavailability(MONDAY, test.first.id))

        assertTrue(listed(generation).outdated)
        assertTrue(scheduling.get.execute(test.admin.id, test.school.id, generation.id).value().summary.outdated)
    }

    @Test
    fun `an outdated alternative names the lessons that now violate a hard constraint (FR-023)`() {
        val generation = generationWith(test.complete())
        val alternative = world.generations.findAlternatives(generation.id).single()
        assertEquals(emptyList(), scheduling.getAlternative.execute(test.admin.id, test.school.id, generation.id, alternative.id).value().conflicts)

        world.markUnavailable(world.teachers.findById(test.schmidt.id)!!, Unavailability(MONDAY, test.first.id))
        val details = scheduling.getAlternative.execute(test.admin.id, test.school.id, generation.id, alternative.id).value()

        assertEquals(listOf(HardRule.TEACHER_UNAVAILABLE), details.conflicts.map { it.rule })
        assertEquals(listOf(test.mathLesson(test.schmidt, MONDAY)), details.conflicts.single().lessons)
    }

    @Test
    fun `deleting a generation removes its alternatives`() {
        val generation = generationWith(test.complete())

        scheduling.delete.execute(test.admin.id, test.school.id, generation.id).value()

        assertNull(world.generations.findById(generation.id))
        assertEquals(emptyList(), world.generations.findAlternatives(generation.id))
    }

    @Test
    fun `the generation of the chosen timetable and a running generation cannot be deleted (FR-024)`() {
        val generation = generationWith(test.complete())
        val alternative = world.generations.findAlternatives(generation.id).single()
        scheduling.choose.execute(test.admin.id, test.school.id, generation.id, alternative.id, null, false).value()

        assertEquals(
            ApplicationError.DeletionBlocked(listOf(Dependant(DependantType.CHOSEN_TIMETABLE, "")), 0),
            scheduling.delete.execute(test.admin.id, test.school.id, generation.id).error(),
        )

        world.executor.deferred = true
        val running = generationWith(test.complete())
        assertIs<ApplicationError.GenerationRunning>(scheduling.delete.execute(test.admin.id, test.school.id, running.id).error())
        assertTrue(world.generations.findById(running.id) != null)
    }

    @Test
    fun `a teacher account cannot delete generations`() {
        val generation = generationWith(test.complete())

        assertEquals(ApplicationError.AccessDenied, scheduling.delete.execute(test.teacherAccount.id, test.school.id, generation.id).error())
        assertTrue(world.generations.findById(generation.id) != null)
    }
}
