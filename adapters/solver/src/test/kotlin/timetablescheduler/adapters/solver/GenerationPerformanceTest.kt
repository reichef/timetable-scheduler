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
 * and 20 constraints — gets its first valid timetable within 2 minutes and, within 10 minutes, at least 3 equally
 * optimal alternatives or one timetable that places every lesson without any violation.
 * Runs only with `./gradlew :adapters:solver:performanceTest`.
 */
@Tag("performance")
class GenerationPerformanceTest {

    @Test
    fun `a school of the target size gets a first timetable within 2 minutes and 3 alternatives or one perfect timetable within 10 minutes (SC-001)`() {
        val problem = targetSizeSchool()
        val listener = TimingListener(problem)

        TimefoldTimetableSolver().solve(problem, Duration.ofMinutes(10), StopSignal { false }, listener)

        val firstAfter = checkNotNull(listener.firstUsefulAfter) { "no valid timetable placing $USEFUL_CLASS_FILL of the lessons" }
        println("SC-001: first useful timetable after ${firstAfter.toSeconds()} s, ${listener.archive.entries.size} alternatives " +
            "${listener.archive.entries.map { it.objectives }}, ${listener.invalid} invalid candidates")
        assertEquals(0, listener.invalid)
        assertTrue(firstAfter < Duration.ofMinutes(2), "first timetable after $firstAfter")
        assertTrue(
            listener.archive.entries.size >= 3 || listener.perfectFound,
            "alternatives: ${listener.archive.entries.size}, none fills every class without any violation",
        )
    }

    /**
     * Records when the first useful timetable arrived — the solver's very first report is the still empty start
     * timetable — and keeps the equally optimal ones, like the generation runner.
     */
    private class TimingListener(private val problem: SchedulingProblem) : SolverListener {
        private val start = System.nanoTime()
        val archive = ParetoArchive<TimetableCandidate> { first, second -> first.sameTimetableAs(second) }
        var firstUsefulAfter: Duration? = null
        var invalid = 0

        /** SC-001: one timetable placing every lesson without any violation, hard or soft, is sufficient on its own. */
        var perfectFound = false

        override fun onCandidate(candidate: TimetableCandidate) {
            if (HardConstraintChecker.violationsOf(problem, candidate).isNotEmpty()) {
                invalid++
                return
            }
            val evaluation = ObjectiveEvaluator.evaluate(problem, candidate)
            val values = evaluation.values

            if (candidate.unplacedPeriods() == 0 && evaluation.softViolations.values.sum() == 0) {
                perfectFound = true
            }
            if (firstUsefulAfter == null && values.classFill >= USEFUL_CLASS_FILL) firstUsefulAfter = Duration.ofNanos(System.nanoTime() - start)
            archive.offer(candidate, values.asVector())
        }

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
        val subjects = List(12) { school.subject() } + school.subject(setOf(lab)) + school.subject(setOf(gym)) + school.subject(setOf(music))
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
        fun hard(rule: ConstraintRule) = SchedulingConstraint(SchedulingConstraintId.new(), schoolId, ConstraintKind.HARD, null, true, rule, 0)
        fun soft(rule: ConstraintRule) = SchedulingConstraint(SchedulingConstraintId.new(), schoolId, ConstraintKind.SOFT, SoftWeight.MEDIUM, true, rule, 0)
        val constraints = DefaultConstraints.forSchool(schoolId) +
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
    private fun addTeachers(school: SeededSchool, subjects: List<SubjectId>, demand: Map<SubjectId, Int>) {
        val total = demand.values.sum().toDouble()
        val primary = subjects.flatMap { subject -> List(maxOf(1, Math.round(TEACHERS * (demand[subject] ?: 0) / total).toInt())) { subject } }
        primary.take(TEACHERS).plus(List((TEACHERS - primary.size).coerceAtLeast(0)) { subjects[it % subjects.size] })
            .forEachIndexed { index, subject -> school.teacher(LOAD, setOf(subject, subjects[(subjects.indexOf(subject) + 1 + index % 3) % subjects.size])) }
    }

    private companion object {
        const val TEACHERS = 60
        const val LOAD = 15
        const val USEFUL_CLASS_FILL = 0.9
    }
}
