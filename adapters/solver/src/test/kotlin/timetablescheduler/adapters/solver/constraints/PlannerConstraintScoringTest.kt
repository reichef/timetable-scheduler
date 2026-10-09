package timetablescheduler.adapters.solver.constraints

import ai.timefold.solver.core.api.score.HardSoftScore
import ai.timefold.solver.core.api.solver.SolutionManager
import ai.timefold.solver.core.api.solver.SolverFactory
import ai.timefold.solver.core.config.solver.SolverConfig
import timetablescheduler.adapters.solver.CollectingListener
import timetablescheduler.adapters.solver.ProblemMapping
import timetablescheduler.adapters.solver.SeededSchool
import timetablescheduler.adapters.solver.TimefoldTimetableSolver
import timetablescheduler.adapters.solver.model.CourseAssignment
import timetablescheduler.adapters.solver.model.LessonPlacement
import timetablescheduler.adapters.solver.model.TimetablePlan
import timetablescheduler.adapters.solver.neverStop
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.scheduling.constraint.ClassScope
import timetablescheduler.domain.scheduling.constraint.ConstraintKind
import timetablescheduler.domain.scheduling.constraint.ConstraintRule
import timetablescheduler.domain.scheduling.constraint.EffectiveConstraints
import timetablescheduler.domain.scheduling.constraint.RuleViolations
import timetablescheduler.domain.scheduling.constraint.SchedulingConstraint
import timetablescheduler.domain.scheduling.constraint.SchedulingConstraintId
import timetablescheduler.domain.scheduling.constraint.SoftWeight
import timetablescheduler.domain.scheduling.constraint.TeacherFixation
import timetablescheduler.domain.scheduling.constraint.TeacherFixationId
import timetablescheduler.domain.scheduling.evaluation.HardConstraintChecker
import timetablescheduler.domain.scheduling.problem.SchedulingProblem
import timetablescheduler.domain.scheduling.solution.LessonAssignment
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.TUESDAY
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The solver penalizes planner constraints exactly as the domain counts them (G5, constitution gate on constraints). */
class PlannerConstraintScoringTest {

    private val solutionManager: SolutionManager<TimetablePlan, HardSoftScore> = SolutionManager.create(
        SolverFactory.create<TimetablePlan>(
            SolverConfig()
                .withSolutionClass(TimetablePlan::class.java)
                .withEntityClasses(CourseAssignment::class.java, LessonPlacement::class.java)
                .withConstraintProviderClass(TimetableConstraintProvider::class.java),
        ),
    )

    private val school = SeededSchool(days = 2, teachingSlotsPerDay = 6, breakAfterSlot = 3)
    private val room = school.room()
    private val math = school.subject()
    private val art = school.subject()
    private val schmidt = school.teacher(20, setOf(math, art))
    private val mueller = school.teacher(20, setOf(math, art))
    private val class5a = school.schoolClass(listOf(math to 6, art to 2), homeRoom = room)

    private fun constraint(rule: ConstraintRule, kind: ConstraintKind = ConstraintKind.HARD) =
        SchedulingConstraint(SchedulingConstraintId.new(), SchoolId.new(), kind, if (kind == ConstraintKind.SOFT) SoftWeight.MEDIUM else null, true, rule, 0)

    private fun lesson(day: java.time.DayOfWeek, slot: Int, subject: timetablescheduler.domain.common.SubjectId = math) =
        LessonAssignment(class5a, subject, schmidt, room, school.slot(day, slot))

    /** The plan with exactly [lessons] placed, as the solver would represent them. */
    private fun scoreOf(problem: SchedulingProblem, lessons: List<LessonAssignment>): HardSoftScore {
        val plan = ProblemMapping.toPlan(problem)
        lessons.groupBy { it.course }.forEach { (course, courseLessons) ->
            val assignment = plan.courses.single { it.classFact.id == course.classId && it.subjectId == course.subjectId }
            assignment.teacher = plan.teachers.single { it.id == courseLessons.first().teacherId }
            val placements = plan.lessons.filter { it.course === assignment }
            courseLessons.zip(placements).forEach { (lesson, placement) ->
                placement.timeslot = plan.timeslots.single { it.daySlot == lesson.daySlot }
                placement.room = placement.suitableRooms.single { it.id == lesson.roomId }
            }
        }
        plan.lessons.filter { it.timeslot == null }.forEach { it.room = it.suitableRooms.first() }
        plan.courses.filter { it.teacher == null }.forEach { it.teacher = it.qualifiedTeachers.first() }
        return checkNotNull(solutionManager.update(plan))
    }

    private fun domainHardCount(problem: SchedulingProblem, lessons: List<LessonAssignment>) =
        EffectiveConstraints.resolve(problem).filter { it.constraint.isHard }.sumOf { RuleViolations.of(it, problem, lessons).count }

    private fun assertSameHardPenalty(rule: ConstraintRule, lessons: List<LessonAssignment>) {
        val problem = school.problem().copy(constraints = listOf(constraint(rule)))

        val expected = domainHardCount(problem, lessons)

        assertTrue(expected > 0, "test needs a violated rule")
        assertEquals(-expected.toLong(), scoreOf(problem, lessons).hardScore())
    }

    @Test
    fun `class maximum per day`() =
        assertSameHardPenalty(ConstraintRule.ClassMaxPeriodsPerDay(ClassScope.SpecificClass(class5a), 2), (0..3).map { lesson(MONDAY, it) })

    @Test
    fun `class without gaps for all classes`() =
        assertSameHardPenalty(ConstraintRule.ClassNoGaps(ClassScope.AllClasses), listOf(lesson(MONDAY, 0), lesson(MONDAY, 4)))

    @Test
    fun `teacher gaps above the limit`() =
        assertSameHardPenalty(ConstraintRule.TeacherMaxGapsPerDay(schmidt, 0), listOf(lesson(MONDAY, 0), lesson(MONDAY, 3)))

    @Test
    fun `subject daily limit for every subject`() =
        assertSameHardPenalty(ConstraintRule.SubjectMaxPerDayInClass(ClassScope.AllClasses, null, 1), listOf(lesson(MONDAY, 0), lesson(MONDAY, 1), lesson(TUESDAY, 0)))

    @Test
    fun `double periods`() =
        assertSameHardPenalty(ConstraintRule.SubjectDoublePeriodsInClass(class5a, math, acrossBreaks = false), listOf(lesson(MONDAY, 0), lesson(MONDAY, 2), lesson(TUESDAY, 2), lesson(TUESDAY, 3)))

    @Test
    fun `subject excluded in a slot for every class`() =
        assertSameHardPenalty(ConstraintRule.SubjectExcludedSlots(math, null, setOf(school.slot(MONDAY, 0))), listOf(lesson(MONDAY, 0), lesson(TUESDAY, 0)))

    @Test
    fun `teacher excluded slots`() =
        assertSameHardPenalty(ConstraintRule.TeacherExcludedSlots(schmidt, setOf(school.slot(TUESDAY, 1))), listOf(lesson(TUESDAY, 1)))

    @Test
    fun `a violated soft constraint lowers only the soft score`() {
        val lessons = (0..3).map { lesson(MONDAY, it) }
        val without = scoreOf(school.problem(), lessons)
        val withSoft = scoreOf(school.problem().copy(constraints = listOf(constraint(ConstraintRule.ClassMaxPeriodsPerDay(ClassScope.AllClasses, 2), ConstraintKind.SOFT))), lessons)

        assertEquals(without.hardScore(), withSoft.hardScore())
        assertTrue(withSoft.softScore() < without.softScore())
    }

    @Test
    fun `solving respects a hard daily maximum and a teacher fixation (US2 scenarios 3, 10)`() {
        val fixation = TeacherFixation(TeacherFixationId.new(), SchoolId.new(), class5a, math, mueller, 0)
        val problem = school.problem().copy(
            constraints = listOf(constraint(ConstraintRule.ClassMaxPeriodsPerDay(ClassScope.SpecificClass(class5a), 3))),
            fixations = listOf(fixation),
        )
        val listener = CollectingListener()

        TimefoldTimetableSolver(minimumFirstPass = Duration.ofSeconds(2)).solve(problem, Duration.ofSeconds(8), neverStop, listener)

        assertTrue(listener.candidates.isNotEmpty())
        listener.candidates.forEach { candidate ->
            assertEquals(emptyList(), HardConstraintChecker.violationsOf(problem, candidate))
            assertTrue(candidate.lessons.filter { it.subjectId == math }.all { it.teacherId == mueller })
        }
        // At most 3 lessons per day on 2 days.
        assertEquals(6, listener.candidates.last().lessons.size)
    }
}
