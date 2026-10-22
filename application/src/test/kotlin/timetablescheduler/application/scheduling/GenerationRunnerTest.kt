package timetablescheduler.application.scheduling

import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.fakes.GenerationEvent
import timetablescheduler.domain.scheduling.DaySlot
import timetablescheduler.domain.scheduling.constraint.ClassScope
import timetablescheduler.domain.scheduling.constraint.ConstraintKind
import timetablescheduler.domain.scheduling.constraint.ConstraintRule
import timetablescheduler.domain.scheduling.constraint.SoftWeight
import timetablescheduler.domain.scheduling.generation.Generation
import timetablescheduler.domain.scheduling.generation.GenerationStatus
import timetablescheduler.domain.scheduling.pareto.Dominance
import timetablescheduler.domain.scheduling.solution.CourseKey
import timetablescheduler.domain.scheduling.solution.TimetableCandidate
import java.time.DayOfWeek.TUESDAY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GenerationRunnerTest {

    private val test = SchedulingTestSchool()
    private val world = test.world

    private fun <T> UseCaseResult<T>.value(): T = assertIs<UseCaseResult.Success<T>>(this).value

    private fun startedGeneration(): Generation =
        test.scheduling.start.execute(test.admin.id, test.school.id, StartRequest(timeLimitMinutes = 1, warningsConfirmed = true)).value()

    private fun alternativesOf(generation: Generation) = world.generations.findAlternatives(generation.id)

    @Test
    fun `admitted candidates are stored with objective values and unplaced lessons`() {
        world.solver.candidates = { listOf(test.halfFilled()) }

        val generation = startedGeneration()

        val alternative = alternativesOf(generation).single()
        assertEquals(0.5, alternative.objectiveValues.classFill)
        assertEquals(1, alternative.unplacedPeriods())
        assertEquals(GenerationStatus.FINISHED, world.generations.findById(generation.id)!!.status)
        assertEquals(1, world.generations.findById(generation.id)!!.alternativesFound)
    }

    @Test
    fun `candidates violating a hard constraint are discarded and only counted in the log (G4)`() {
        world.solver.candidates = { listOf(test.invalid(), test.complete()) }

        val generation = startedGeneration()

        assertEquals(listOf(test.complete().lessons.toSet()), alternativesOf(generation).map { it.lessons.toSet() })
        assertTrue(GenerationEvent.Discarded(generation.id, 1) in world.generationLog.events)
    }

    @Test
    fun `a dominated alternative is removed when a better one is found`() {
        world.solver.candidates = { listOf(test.halfFilled(), test.complete()) }

        val generation = startedGeneration()

        assertEquals(1.0, alternativesOf(generation).single().objectiveValues.classFill)
    }

    @Test
    fun `stored alternatives never dominate each other (SC-003)`() {
        world.solver.candidates = { listOf(test.halfFilled(), test.complete(test.mueller), test.complete(test.schmidt), test.halfFilled(test.mueller)) }

        val generation = startedGeneration()

        val vectors = alternativesOf(generation).map { it.objectiveValues.asVector() }
        assertEquals(2, vectors.size)
        vectors.forEach { first -> vectors.forEach { second -> assertFalse(Dominance.dominates(first, second)) } }
    }

    @Test
    fun `a stop request ends the generation as stopped and keeps the alternatives found so far`() {
        world.executor.deferred = true
        world.solver.candidates = { listOf(test.halfFilled(), test.complete()) }
        val generation = startedGeneration()

        test.scheduling.stop.execute(test.admin.id, test.school.id, generation.id).value()
        world.executor.runPending()

        assertEquals(GenerationStatus.STOPPED, world.generations.findById(generation.id)!!.status)
    }

    @Test
    fun `a solver failure ends the generation as failed and logs only class name and id`() {
        world.solver.candidates = { listOf(test.complete()) }
        world.solver.failure = IllegalStateException("Anna Schmidt could not be placed")

        val generation = startedGeneration()

        assertEquals(GenerationStatus.FAILED, world.generations.findById(generation.id)!!.status)
        assertEquals(1, alternativesOf(generation).size)
        assertTrue(GenerationEvent.Failed(generation.id, IllegalStateException::class.java.name) in world.generationLog.events)
        assertFalse(world.generationLog.events.toString().contains("Schmidt"))
    }

    @Test
    fun `without any valid alternative and with an infeasible pass the generation fails as infeasible (G4)`() {
        world.solver.candidates = { listOf(test.invalid()) }
        world.solver.infeasiblePasses = 1

        val generation = startedGeneration()

        assertEquals(GenerationStatus.FAILED_INFEASIBLE, world.generations.findById(generation.id)!!.status)
    }

    @Test
    fun `an infeasible generation names the active hard constraints that can force violations`() {
        val minimum = world.useCases.constraints.create.execute(
            test.admin.id, test.school.id,
            ConstraintInput(ConstraintKind.HARD, null, ConstraintRule.ClassMinPeriodsPerDay(ClassScope.SpecificClass(test.class5a.id), 1)),
        ).value()
        world.useCases.constraints.create.execute(
            test.admin.id, test.school.id, ConstraintInput(ConstraintKind.HARD, null, ConstraintRule.ClassNoGaps(ClassScope.AllClasses)),
        ).value()
        world.solver.infeasiblePasses = 1

        val generation = startedGeneration()

        val stored = world.generations.findById(generation.id)!!
        assertEquals(GenerationStatus.FAILED_INFEASIBLE, stored.status)
        assertEquals(listOf(minimum.id), stored.infeasibleConstraintIds)
    }

    @Test
    fun `an infeasible pass after a valid alternative still ends as finished (G4)`() {
        world.solver.candidates = { listOf(test.complete()) }
        world.solver.infeasiblePasses = 2

        val generation = startedGeneration()

        assertEquals(GenerationStatus.FINISHED, world.generations.findById(generation.id)!!.status)
        assertEquals(1, alternativesOf(generation).size)
    }

    @Test
    fun `start and end are logged with ids, status and counts only`() {
        world.solver.candidates = { listOf(test.complete()) }

        val generation = startedGeneration()

        assertEquals(GenerationEvent.Started(generation.id), world.generationLog.events.first())
        val finished = assertIs<GenerationEvent.Finished>(world.generationLog.events.last())
        assertEquals(GenerationStatus.FINISHED, finished.status)
        assertEquals(1, finished.alternatives)
    }

    @Test
    fun `stopping a generation of another school answers not found`() {
        val other = world.school("Gymnasium Süd")

        assertEquals(
            ApplicationError.NotFound,
            assertIs<UseCaseResult.Failure>(test.scheduling.stop.execute(test.admin.id, other.id, startedGeneration().id)).error,
        )
    }

    /**
     * A soft rule that the complete timetable violates (Mathematik on Tuesday) but the half-filled one does not, so the
     * two are equally optimal and only the minimum share of placed lessons decides (FR-007a).
     */
    private fun halfFilledIsNotDominated() {
        val tuesday = setOf(DaySlot(TUESDAY, test.first.id), DaySlot(TUESDAY, test.second.id))
        val rule = ConstraintRule.SubjectExcludedSlots(test.math.id, null, tuesday)

        world.useCases.constraints.create.execute(test.admin.id, test.school.id, ConstraintInput(ConstraintKind.SOFT, SoftWeight.MEDIUM, rule)).value()
    }

    private fun startedGeneration(minimumPlacedShare: Int): Generation =
        test.scheduling.start.execute(
            test.admin.id,
            test.school.id,
            StartRequest(timeLimitMinutes = 1, warningsConfirmed = true, minimumPlacedShare = minimumPlacedShare),
        ).value()

    @Test
    fun `an equally optimal alternative placing too few lessons is not kept (FR-007a)`() {
        halfFilledIsNotDominated()
        world.solver.candidates = { listOf(test.complete(), test.halfFilled()) }

        val withDefaultShare = startedGeneration()
        val withShareOf50 = startedGeneration(minimumPlacedShare = 50)

        assertEquals(listOf(2), alternativesOf(withDefaultShare).map { it.lessons.size })
        assertEquals(setOf(1, 2), alternativesOf(withShareOf50).map { it.lessons.size }.toSet())
    }

    @Test
    fun `a kept alternative is removed when a better one makes it fall below the share (FR-007a)`() {
        halfFilledIsNotDominated()
        world.solver.candidates = { listOf(test.halfFilled(), test.complete()) }

        val generation = startedGeneration()

        assertEquals(listOf(2), alternativesOf(generation).map { it.lessons.size })
        assertEquals(1, world.generations.findById(generation.id)!!.alternativesFound)
    }

    @Test
    fun `an alternative without any placed lesson is never kept, so such a run ends without alternatives (FR-007a)`() {
        val empty = TimetableCandidate(emptyList(), mapOf(CourseKey(test.class5a.id, test.math.id) to 2))
        world.solver.candidates = { listOf(empty) }

        val generation = startedGeneration()

        assertEquals(emptyList(), alternativesOf(generation))
        assertEquals(GenerationStatus.FINISHED, world.generations.findById(generation.id)!!.status)
    }
}
