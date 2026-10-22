package timetablescheduler.domain.scheduling.generation

import timetablescheduler.domain.scheduling.constraint.AppliedConstraint
import timetablescheduler.domain.scheduling.constraint.ConstraintRule
import timetablescheduler.domain.scheduling.constraint.EffectiveConstraints
import timetablescheduler.domain.scheduling.constraint.SchedulingConstraintId
import timetablescheduler.domain.scheduling.problem.SchedulingProblem

/**
 * FR-017, G4: finds active hard constraints that can never be met with the master data, before a generation starts.
 * Only minimum periods per day and exclusions can force violations — every other hard rule can be met by leaving lessons
 * unplaced. Contradictions found later by the solver are reported by the generation status instead.
 */
object InfeasibilityPreCheck {

    fun contradictionsOf(problem: SchedulingProblem): List<SchedulingConstraintId> =
        EffectiveConstraints.resolve(problem)
            .filter { it.constraint.isHard && isContradiction(it, problem) }
            .map { it.constraint.id }
            .distinct()

    private fun isContradiction(applied: AppliedConstraint, problem: SchedulingProblem): Boolean {
        val weekSlots = problem.teachingDaySlots()
        return when (val rule = applied.rule) {
            is ConstraintRule.ClassMinPeriodsPerDay -> {
                val classPeriods = problem.schoolClass(applied.classId!!)?.curriculum?.sumOf { it.periodsPerWeek } ?: 0
                // A day with fewer teaching slots than the minimum, or more periods demanded than the class has.
                problem.days.any { it.teachingSlots.size < rule.limit } || rule.limit * problem.days.size > classPeriods
            }
            is ConstraintRule.ClassExcludedSlots -> {
                val classPeriods = problem.schoolClass(rule.classId)?.curriculum?.sumOf { it.periodsPerWeek } ?: 0
                weekSlots.count { it !in rule.slots } < classPeriods
            }
            is ConstraintRule.TeacherExcludedSlots -> {
                val fixedPeriods = problem.fixations.filter { it.teacherId == rule.teacherId }.sumOf { problem.requiredPeriods(it.course) }
                weekSlots.count { it !in rule.slots } < fixedPeriods
            }
            is ConstraintRule.SubjectExcludedSlots -> {
                val available = weekSlots.count { it !in rule.slots }
                problem.courses().any { (course, periods) ->
                    course.subjectId == rule.subjectId && (rule.classId == null || course.classId == rule.classId) && periods > available
                }
            }
            else -> false
        }
    }
}
