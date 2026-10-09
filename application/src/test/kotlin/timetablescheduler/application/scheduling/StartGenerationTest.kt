package timetablescheduler.application.scheduling

import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.domain.readiness.ReadinessIssue
import timetablescheduler.domain.scheduling.generation.Generation
import timetablescheduler.domain.scheduling.generation.GenerationStatus
import timetablescheduler.domain.scheduling.problem.SchedulingInputFingerprint
import timetablescheduler.domain.validation.ValidationError
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StartGenerationTest {

    private val test = SchedulingTestSchool()
    private val world = test.world

    private fun <T> UseCaseResult<T>.value(): T = assertIs<UseCaseResult.Success<T>>(this).value

    private fun UseCaseResult<*>.error(): ApplicationError = assertIs<UseCaseResult.Failure>(this).error

    private fun start(timeLimitMinutes: Int = 10, warningsConfirmed: Boolean = false, actor: timetablescheduler.domain.common.AccountId = test.admin.id) =
        test.scheduling.start.execute(actor, test.school.id, StartRequest(timeLimitMinutes, warningsConfirmed))

    @Test
    fun `a planner starts a generation for the current data`() {
        world.executor.deferred = true

        val generation = start().value()

        val stored = world.generations.findById(generation.id)!!
        assertEquals(GenerationStatus.RUNNING, stored.status)
        assertEquals(test.admin.id, stored.startedBy)
        assertEquals(SchedulingInputFingerprint.of(world.problemOf(test.school.id)), stored.inputFingerprint)
    }

    @Test
    fun `the generation is handed to the background executor with its time limit`() {
        start(timeLimitMinutes = 3).value()

        assertEquals(Duration.ofMinutes(3), world.solver.lastTimeLimit)
    }

    @Test
    fun `teachers may not start generations (FR-025)`() {
        assertEquals(ApplicationError.AccessDenied, start(actor = test.teacherAccount.id).error())
        assertTrue(world.generations.all().isEmpty())
    }

    @Test
    fun `a time limit outside 1 to 60 minutes is rejected (FR-009)`() {
        assertEquals(
            ApplicationError.Invalid(listOf(ValidationError.OutOfRange(Generation.FIELD_TIME_LIMIT, 1, 60))),
            start(timeLimitMinutes = 61).error(),
        )
    }

    @Test
    fun `blocking readiness issues refuse the start and are named (FR-010)`() {
        world.teachers.update(world.teachers.findById(test.schmidt.id)!!.copy(weeklyLoad = 9))

        val error = assertIs<ApplicationError.GenerationBlocked>(start().error())

        assertEquals(listOf(ReadinessIssue.TeacherLoadExceedsGrid(test.schmidt.id, 5)), error.blockingIssues)
        assertTrue(world.generations.all().isEmpty())
    }

    @Test
    fun `warnings must be confirmed before the start`() {
        world.teacher(test.school.id, "Ohne Fach", "OHNE", weeklyLoad = 2)

        assertEquals(
            ApplicationError.Invalid(listOf(ValidationError.ConfirmationMissing(StartRequest.FIELD_WARNINGS_CONFIRMED))),
            start(warningsConfirmed = false).error(),
        )
        assertIs<Generation>(start(warningsConfirmed = true).value())
    }

    @Test
    fun `only one generation may run per school (FR-011)`() {
        world.executor.deferred = true
        val running = start().value()

        assertEquals(ApplicationError.GenerationRunning(running.id), start().error())
    }

    @Test
    fun `a finished generation does not block a new start`() {
        start().value()

        assertIs<Generation>(start().value())
        assertNull(world.generations.findRunning(test.school.id))
    }
}
