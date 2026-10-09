package timetablescheduler.application.scheduling

import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.domain.readiness.ReadinessIssue
import timetablescheduler.domain.scheduling.constraint.ClassScope
import timetablescheduler.domain.scheduling.constraint.ConstraintKind
import timetablescheduler.domain.scheduling.constraint.ConstraintRule
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

    private fun soft(rule: ConstraintRule) =
        world.useCases.constraints.create.execute(test.admin.id, test.school.id, ConstraintInput(ConstraintKind.SOFT, null, rule)).value()

    private fun hard(rule: ConstraintRule) =
        world.useCases.constraints.create.execute(test.admin.id, test.school.id, ConstraintInput(ConstraintKind.HARD, null, rule)).value()

    @Test
    fun `up to three active soft constraints are compared as separate objectives (FR-006a)`() {
        val noGaps = soft(ConstraintRule.ClassNoGaps(ClassScope.SpecificClass(test.class5a.id)))

        val generation = test.scheduling.start.execute(test.admin.id, test.school.id, StartRequest(separateObjectiveIds = listOf(noGaps.id))).value()

        assertEquals(listOf(noGaps.id), generation.separateObjectiveIds)
        assertEquals(listOf(noGaps.id), world.solver.lastProblem!!.separateObjectiveIds)
    }

    @Test
    fun `more than three, hard or switched-off constraints cannot be separate objectives`() {
        val four = (1..2).map { soft(ConstraintRule.ClassMaxPeriodsPerDay(ClassScope.SpecificClass(test.class5a.id), it)) } +
            soft(ConstraintRule.ClassNoGaps(ClassScope.SpecificClass(test.class5a.id))) + soft(ConstraintRule.TeacherMaxPeriodsPerDay(test.schmidt.id, 2))
        val hardOne = hard(ConstraintRule.TeacherMaxPeriodsPerDay(test.mueller.id, 2))
        val tooMany = ApplicationError.Invalid(listOf(ValidationError.OutOfRange(StartRequest.FIELD_SEPARATE_OBJECTIVES, 0, 3)))
        val notSoft = ApplicationError.Invalid(listOf(ValidationError.InvalidReference(StartRequest.FIELD_SEPARATE_OBJECTIVES)))

        assertEquals(tooMany, test.scheduling.start.execute(test.admin.id, test.school.id, StartRequest(separateObjectiveIds = four.map { it.id })).error())
        assertEquals(notSoft, test.scheduling.start.execute(test.admin.id, test.school.id, StartRequest(separateObjectiveIds = listOf(hardOne.id))).error())
    }

    @Test
    fun `contradicting hard constraints refuse the start and are named (FR-017)`() {
        val impossible = hard(ConstraintRule.ClassMinPeriodsPerDay(ClassScope.SpecificClass(test.class5a.id), 2))

        val error = assertIs<ApplicationError.ContradictingConstraints>(test.scheduling.start.execute(test.admin.id, test.school.id, StartRequest()).error())

        assertEquals(listOf(impossible.id), error.constraints.map { it.id })
    }

    @Test
    fun `a started generation keeps a snapshot of the active constraints and fixations (FR-022)`() {
        val active = soft(ConstraintRule.ClassNoGaps(ClassScope.SpecificClass(test.class5a.id)))
        val off = soft(ConstraintRule.TeacherMaxPeriodsPerDay(test.schmidt.id, 1))
        world.useCases.constraints.setActive.execute(test.admin.id, test.school.id, off.id, false, off.version).value()
        val fixation = world.useCases.fixations.create.execute(test.admin.id, test.school.id, test.class5a.id, test.math.id, test.schmidt.id).value()

        val generation = test.scheduling.start.execute(test.admin.id, test.school.id, StartRequest()).value()

        assertEquals(listOf(active), generation.constraintSnapshot)
        assertEquals(listOf(fixation), generation.fixationSnapshot)
    }
}
