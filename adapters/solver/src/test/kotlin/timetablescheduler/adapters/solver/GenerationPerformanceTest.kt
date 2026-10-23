package timetablescheduler.adapters.solver

import org.junit.jupiter.api.Tag
import timetablescheduler.application.port.SolverListener
import timetablescheduler.application.port.StopSignal
import timetablescheduler.domain.common.RoomFeatureId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.common.SubjectId
import timetablescheduler.domain.scheduling.constraint.ClassScope
import timetablescheduler.domain.scheduling.constraint.ConstraintKind
import timetablescheduler.domain.scheduling.constraint.ConstraintRule
import timetablescheduler.domain.scheduling.constraint.DefaultConstraints
import timetablescheduler.domain.scheduling.constraint.SchedulingConstraint
import timetablescheduler.domain.scheduling.constraint.SchedulingConstraintId
import timetablescheduler.domain.scheduling.constraint.SoftWeight
import timetablescheduler.domain.scheduling.evaluation.HardConstraintChecker
import timetablescheduler.domain.scheduling.evaluation.ObjectiveEvaluator
import timetablescheduler.domain.scheduling.generation.PlacementShare
import timetablescheduler.domain.scheduling.pareto.ParetoArchive
import timetablescheduler.domain.scheduling.problem.SchedulingProblem
import timetablescheduler.domain.scheduling.solution.TimetableCandidate
import java.time.DayOfWeek
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * SC-001 (G12): a school of the target size — 40 rooms, 15 subjects, 6 levels, 25 classes, 60 teachers, a 5 × 10 grid
 * and 20 constraints — gets its first alternative meeting the minimum share of placed lessons (90 % of the best
 * alternative, FR-007a) within 2 minutes; the alternatives are kept like the generation runner keeps them. There is
 * no fixed number of alternatives; it is recorded. Runs only with `./gradlew :adapters:solver:performanceTest`.
 */
@Tag("performance")
class GenerationPerformanceTest {
    @Test
    fun `a school of the target size gets an alternative meeting the minimum share within 2 minutes (SC-001)`() {
        val problem = targetSizeSchool()
        val listener = TimingListener(problem)

        TimefoldTimetableSolver().solve(problem, Duration.ofMinutes(10), StopSignal { false }, listener)

        val firstAfter = checkNotNull(listener.firstMeetingShareAfter()) { "no valid timetable meeting the minimum share" }
        val alternatives = listener.archive.entries

        println(
            "SC-001: first alternative meeting the share after ${firstAfter.toSeconds()} s, ${alternatives.size} alternatives " +
                "${alternatives.map { it.objectives }}, best placed ${listener.share.bestPlaced}, ${listener.invalid} invalid candidates",
        )

        assertEquals(0, listener.invalid)
        assertTrue(firstAfter < Duration.ofMinutes(2), "first alternative meeting the share after $firstAfter")
        assertTrue(listener.archive.entries.isNotEmpty())
        listener.archive.entries.forEach { entry ->
            assertTrue(listener.share.admits(entry.item.lessons.size), "kept alternative below the minimum share (FR-007a)")
        }
    }

    /**
     * Keeps the alternatives like the generation runner (minimum share, then Pareto archive) and records when every
     * valid timetable arrived, so the first one meeting the share of the final best can be found afterwards.
     */
    private class TimingListener(
        private val problem: SchedulingProblem,
    ) : SolverListener {
        private val start = System.nanoTime()
        private val arrivals = mutableListOf<Pair<Duration, Int>>()
        val archive = ParetoArchive<TimetableCandidate> { first, second -> first.sameTimetableAs(second) }
        val share = PlacementShare(MINIMUM_PLACED_SHARE)
        var invalid = 0

        override fun onCandidate(candidate: TimetableCandidate) {
            if (HardConstraintChecker.violationsOf(problem, candidate).isNotEmpty()) {
                invalid++
                return
            }

            val placed = candidate.lessons.size
            arrivals += Duration.ofNanos(System.nanoTime() - start) to placed
            if (!share.admits(placed)) {
                return
            }

            val change = archive.offer(candidate, ObjectiveEvaluator.evaluate(problem, candidate).values.asVector())
            if (change.admitted && share.record(placed)) {
                archive.removeIf { !share.admits(it.lessons.size) }
            }
        }

        /** When the first timetable arrived that places at least the share of the final best one's lessons. */
        fun firstMeetingShareAfter(): Duration? = arrivals.firstOrNull { (_, placed) -> share.admits(placed) }?.first

        override fun onInfeasiblePass() = Unit
    }

    private fun targetSizeSchool(): SchedulingProblem {
        val school = SeededSchool(days = 5, teachingSlotsPerDay = 10, breakAfterSlot = 5)
        val lab = RoomFeatureId.new()
        val gym = RoomFeatureId.new()
        val music = RoomFeatureId.new()

        repeat(5) { school.room(setOf(lab)) }
        repeat(4) { school.room(setOf(gym)) }
        repeat(3) { school.room(setOf(music)) }
        repeat(3) { school.room() }

        val subjects =
            List(12) { school.subject() } + school.subject(setOf(lab)) + school.subject(setOf(gym)) + school.subject(setOf(music))

        // 13 of the 15 subjects per level, 30 periods per week; levels differ in which two subjects they leave out.
        val periods = listOf(4, 4, 4, 3, 3, 2, 2, 2, 2, 1, 1, 1, 1)
        val classesPerLevel = listOf(5, 4, 4, 4, 4, 4)
        val demand = mutableMapOf<SubjectId, Int>()
        classesPerLevel.forEachIndexed { level, classCount ->
            val levelSubjects = (subjects.drop(level * 2) + subjects.take(level * 2)).take(periods.size)
            val curriculum = levelSubjects.zip(periods)
            repeat(classCount) {
                school.schoolClass(curriculum, homeRoom = school.room())
                curriculum.forEach { (subject, count) -> demand.merge(subject, count, Int::plus) }
            }
        }

        addTeachers(school, subjects, demand)
        val teachers = school.teachers.map { it.id }
        val schoolId = SchoolId.new()

        fun hard(rule: ConstraintRule) =
            SchedulingConstraint(SchedulingConstraintId.new(), schoolId, ConstraintKind.HARD, null, true, rule, 0)

        fun soft(rule: ConstraintRule) =
            SchedulingConstraint(SchedulingConstraintId.new(), schoolId, ConstraintKind.SOFT, SoftWeight.MEDIUM, true, rule, 0)
        val constraints =
            DefaultConstraints.forSchool(schoolId) +
                school.classes.take(6).map { hard(ConstraintRule.ClassMaxPeriodsPerDay(ClassScope.SpecificClass(it.id), 8)) } +
                teachers.take(6).mapIndexed { index, teacher ->
                    hard(ConstraintRule.TeacherExcludedSlots(teacher, setOf(school.slot(DayOfWeek.entries[index % 5], 9))))
                } +
                teachers.drop(6).take(5).map { soft(ConstraintRule.TeacherMaxPeriodsPerDay(it, 4)) } +
                // 30 periods in 5 days need 6 per day: like in real schools, a full timetable competes with a short day.
                soft(ConstraintRule.ClassMaxPeriodsPerDay(ClassScope.AllClasses, 5))
        return school.problem().copy(constraints = constraints)
    }

    /**
     * 60 teachers with two subjects each: every subject gets teachers in proportion to its demand, the second subject
     * adds flexibility; loads of 15 leave about 20 % spare capacity.
     */
    private fun addTeachers(
        school: SeededSchool,
        subjects: List<SubjectId>,
        demand: Map<SubjectId, Int>,
    ) {
        val total = demand.values.sum().toDouble()
        val primary =
            subjects.flatMap { subject ->
                List(maxOf(1, Math.round(TEACHERS * (demand[subject] ?: 0) / total).toInt())) { subject }
            }
        primary
            .take(TEACHERS)
            .plus(List((TEACHERS - primary.size).coerceAtLeast(0)) { subjects[it % subjects.size] })
            .forEachIndexed { index, subject ->
                school.teacher(
                    LOAD,
                    setOf(
                        subject,
                        subjects[
                            (subjects.indexOf(subject) + 1 + index % 3) %
                                subjects.size,
                        ],
                    ),
                )
            }
    }

    private companion object {
        const val TEACHERS = 60
        const val LOAD = 15
        const val MINIMUM_PLACED_SHARE = 90
    }
}
