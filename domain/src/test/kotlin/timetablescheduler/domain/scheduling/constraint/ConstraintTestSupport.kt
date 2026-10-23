package timetablescheduler.domain.scheduling.constraint

import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.scheduling.TestSchool
import timetablescheduler.domain.scheduling.problem.SchedulingProblem
import timetablescheduler.domain.scheduling.solution.LessonAssignment

/** A constraint as the planner would save it. */
fun hard(
    rule: ConstraintRule,
    active: Boolean = true,
) = SchedulingConstraint(SchedulingConstraintId.new(), SchoolId.new(), ConstraintKind.HARD, null, active, rule, 0)

fun soft(
    rule: ConstraintRule,
    weight: SoftWeight = SoftWeight.MEDIUM,
    active: Boolean = true,
) = SchedulingConstraint(SchedulingConstraintId.new(), SchoolId.new(), ConstraintKind.SOFT, weight, active, rule, 0)

/** Violations of [constraint] in [lessons], counted by the domain rule over all classes it applies to. */
fun TestSchool.violationsOf(
    constraint: SchedulingConstraint,
    lessons: List<LessonAssignment>,
    problem: SchedulingProblem = problem(),
): Int {
    val withConstraint = problem.copy(constraints = listOf(constraint))
    return EffectiveConstraints.resolve(withConstraint).sumOf { RuleViolations.of(it, withConstraint, lessons).count }
}
