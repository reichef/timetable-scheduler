package timetablescheduler.adapters.solver

import timetablescheduler.application.port.StopSignal
import timetablescheduler.domain.scheduling.evaluation.HardConstraintChecker
import timetablescheduler.domain.scheduling.evaluation.ObjectiveEvaluator
import timetablescheduler.domain.scheduling.pareto.Dominance
import timetablescheduler.domain.scheduling.pareto.ParetoArchive
import timetablescheduler.domain.scheduling.problem.SchedulingProblem
import timetablescheduler.domain.scheduling.solution.Stability
import timetablescheduler.domain.scheduling.solution.TimetableCandidate
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Solver adapter tests on seeded schools (SC-002 – SC-004); short time limits keep them fast. */
class TimefoldTimetableSolverTest {

    private val solver = TimefoldTimetableSolver(minimumFirstPass = Duration.ofSeconds(2))

    private fun solve(problem: SchedulingProblem, seconds: Long = 10, stop: StopSignal = neverStop): CollectingListener =
        CollectingListener().also { solver.solve(problem, Duration.ofSeconds(seconds), stop, it) }

    private fun SchedulingProblem.violations(candidate: TimetableCandidate) = HardConstraintChecker.violationsOf(this, candidate)

    @Test
    fun `every reported candidate respects all hard constraints (SC-002)`() {
        val problem = SeededSchools.competing().problem()

        val reported = solve(problem).candidates

        assertTrue(reported.isNotEmpty())
        reported.forEach { assertEquals(emptyList(), problem.violations(it)) }
    }

    @Test
    fun `a school with a known complete timetable gets one placing every lesson (SC-004)`() {
        val problem = SeededSchools.solvable().problem()

        val reported = solve(problem, seconds = 20).candidates

        val best = reported.maxBy { ObjectiveEvaluator.classFill(problem, it.lessons) }
        assertEquals(1.0, ObjectiveEvaluator.classFill(problem, best.lessons))
        assertEquals(0, best.unplacedPeriods())
        assertEquals(emptyList(), problem.violations(best))
    }

    @Test
    fun `lessons that cannot be placed stay unplaced instead of violating a constraint (FR-005)`() {
        val problem = SeededSchools.overConstrained().problem()

        val best = solve(problem, seconds = 5).candidates.last()

        assertEquals(emptyList(), problem.violations(best))
        assertEquals(4, best.lessons.size)
        assertEquals(4, best.unplacedPeriods())
    }

    @Test
    fun `lessons without required features take place in the class's home room (FR-002a)`() {
        val school = SeededSchools.solvable()
        val problem = school.problem()

        val best = solve(problem).candidates.last()

        val featureless = problem.subjects.filter { it.requiredFeatureIds.isEmpty() }.map { it.id }.toSet()
        best.lessons.filter { it.subjectId in featureless }.forEach { lesson ->
            assertEquals(problem.schoolClass(lesson.classId)!!.homeRoomId, lesson.roomId)
        }
    }

    @Test
    fun `a stop request ends solving within 2 seconds`() {
        val stop = AtomicBoolean(false)
        val listener = CollectingListener()
        val worker = Thread { solver.solve(SeededSchools.solvable().problem(), Duration.ofMinutes(5), { stop.get() }, listener) }
        worker.start()
        Thread.sleep(1_500)

        stop.set(true)
        worker.join(2_000)

        assertFalse(worker.isAlive)
    }

    @Test
    fun `all periods of a subject in a class have one teacher (FR-004)`() {
        val problem = SeededSchools.competing().problem()

        solve(problem).candidates.forEach { candidate ->
            candidate.lessons.groupBy { it.course }.values.forEach { lessons -> assertEquals(1, lessons.map { it.teacherId }.distinct().size) }
        }
    }

    @Test
    fun `the kept candidates of competing objectives are equally optimal (SC-003)`() {
        val problem = SeededSchools.competing().problem()
        val archive = ParetoArchive<TimetableCandidate> { first, second -> first.sameTimetableAs(second) }

        solve(problem, seconds = 15).candidates.forEach { archive.offer(it, ObjectiveEvaluator.evaluate(problem, it).values.asVector()) }

        val kept = archive.entries.map { it.objectives }
        assertTrue(kept.isNotEmpty())
        kept.forEach { first -> kept.forEach { second -> assertFalse(Dominance.dominates(first, second)) } }
    }

    @Test
    fun `starting from a reference keeps most of it when one lesson no longer fits (FR-006b)`() {
        val original = SeededSchools.solvable().problem()
        val reference = solve(original, seconds = 20).candidates
            .filter { it.unplacedPeriods() == 0 && original.violations(it).isEmpty() }
            .first()
        val moved = reference.lessons.first()
        val problem = original.copy(
            teachers = original.teachers.map { if (it.id == moved.teacherId) it.copy(unavailable = it.unavailable + moved.daySlot) else it },
            reference = reference.lessons,
        )

        val reported = solve(problem, seconds = 15).candidates

        reported.forEach { assertEquals(emptyList(), problem.violations(it)) }
        val leastChanged = reported.filter { it.unplacedPeriods() == 0 }.minOf { Stability.changedLessons(reference.lessons, it.lessons) }
        assertTrue(leastChanged in 1..1 + 3, "changed lessons: $leastChanged")
    }
}
