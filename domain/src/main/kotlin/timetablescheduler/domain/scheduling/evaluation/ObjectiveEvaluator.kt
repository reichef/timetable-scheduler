package timetablescheduler.domain.scheduling.evaluation

import timetablescheduler.domain.scheduling.constraint.EffectiveConstraints
import timetablescheduler.domain.scheduling.constraint.RuleViolations
import timetablescheduler.domain.scheduling.constraint.SchedulingConstraintId
import timetablescheduler.domain.scheduling.problem.SchedulingProblem
import timetablescheduler.domain.scheduling.solution.LessonAssignment
import timetablescheduler.domain.scheduling.solution.ObjectiveValues
import timetablescheduler.domain.scheduling.solution.TimetableCandidate

/** The objective values of an alternative and how often each soft constraint is violated (FR-018). */
data class Evaluation(val values: ObjectiveValues, val softViolations: Map<SchedulingConstraintId, Int>)

/** Computes the objectives of an alternative (FR-006, G3); all values are normalized to 0…1, higher is better. */
object ObjectiveEvaluator {

    fun evaluate(problem: SchedulingProblem, candidate: TimetableCandidate): Evaluation {
        val soft = softTotals(problem, candidate.lessons)
        val separateIds = problem.separateObjectiveIds.toSet()
        return Evaluation(
            values = ObjectiveValues.rounded(
                classFill = classFill(problem, candidate.lessons),
                teacherLoadUsage = teacherLoadUsage(problem, candidate.lessons),
                separate = problem.separateObjectiveIds.map { id -> soft[id]?.let { 1.0 - it.violations.toDouble() / it.opportunities } ?: 1.0 },
                otherSoft = combinedSatisfaction(soft.filterKeys { it !in separateIds }.values),
            ),
            softViolations = soft.mapValues { it.value.violations },
        )
    }

    /** Violations and opportunities of each active soft constraint, summed over all classes it applies to. */
    private fun softTotals(problem: SchedulingProblem, lessons: List<LessonAssignment>): Map<SchedulingConstraintId, SoftTotal> {
        val totals = linkedMapOf<SchedulingConstraintId, SoftTotal>()
        EffectiveConstraints.resolve(problem).filter { !it.constraint.isHard }.forEach { applied ->
            val violation = RuleViolations.of(applied, problem, lessons)
            val previous = totals[applied.constraint.id] ?: SoftTotal(applied.constraint.weight?.factor ?: 1, 0, 0)
            totals[applied.constraint.id] = previous.copy(
                violations = previous.violations + violation.count,
                opportunities = previous.opportunities + violation.opportunities,
            )
        }
        return totals
    }

    /** 1 − Σ weight · violations / Σ weight · opportunities (G3); 1 if there is no such soft constraint. */
    private fun combinedSatisfaction(totals: Collection<SoftTotal>): Double {
        val possible = totals.sumOf { it.weightFactor * it.opportunities }
        if (possible == 0) return 1.0
        return 1.0 - totals.sumOf { it.weightFactor * it.violations }.toDouble() / possible
    }

    private data class SoftTotal(val weightFactor: Int, val violations: Int, val opportunities: Int)

    /**
     * Σ weight · placed periods / Σ weight · required periods over all courses, with placed periods capped at the
     * required ones, so a class with priority 20 counts twice as much as one with 10 (001 FR-015). 1 if nothing is required.
     */
    fun classFill(problem: SchedulingProblem, lessons: List<LessonAssignment>): Double {
        val placedByCourse = lessons.groupingBy { it.course }.eachCount()
        var weightedRequired = 0.0
        var weightedPlaced = 0.0
        problem.courses().forEach { (course, required) ->
            val weight = problem.schoolClass(course.classId)?.priorityWeight ?: 0
            weightedRequired += weight * required
            weightedPlaced += weight * minOf(required, placedByCourse[course] ?: 0)
        }
        return if (weightedRequired == 0.0) 1.0 else weightedPlaced / weightedRequired
    }

    /** Σ min(assigned, load) / Σ load over teachers with a load (001 FR-018); 1 if no teacher has a load. */
    fun teacherLoadUsage(problem: SchedulingProblem, lessons: List<LessonAssignment>): Double {
        val assignedByTeacher = lessons.groupingBy { it.teacherId }.eachCount()
        val teachersWithLoad = problem.teachers.filter { it.weeklyLoad > 0 }
        val totalLoad = teachersWithLoad.sumOf { it.weeklyLoad }
        if (totalLoad == 0) return 1.0
        return teachersWithLoad.sumOf { minOf(it.weeklyLoad, assignedByTeacher[it.id] ?: 0) }.toDouble() / totalLoad
    }
}
