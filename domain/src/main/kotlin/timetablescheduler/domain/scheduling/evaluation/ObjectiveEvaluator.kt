package timetablescheduler.domain.scheduling.evaluation

import timetablescheduler.domain.scheduling.constraint.SchedulingConstraintId
import timetablescheduler.domain.scheduling.problem.SchedulingProblem
import timetablescheduler.domain.scheduling.solution.LessonAssignment
import timetablescheduler.domain.scheduling.solution.ObjectiveValues
import timetablescheduler.domain.scheduling.solution.TimetableCandidate

/** The objective values of an alternative and how often each soft constraint is violated (FR-018). */
data class Evaluation(val values: ObjectiveValues, val softViolations: Map<SchedulingConstraintId, Int>)

/** Computes the objectives of an alternative (FR-006, G3); all values are normalized to 0…1, higher is better. */
object ObjectiveEvaluator {

    fun evaluate(problem: SchedulingProblem, candidate: TimetableCandidate): Evaluation =
        Evaluation(
            values = ObjectiveValues.rounded(
                classFill = classFill(problem, candidate.lessons),
                teacherLoadUsage = teacherLoadUsage(problem, candidate.lessons),
            ),
            softViolations = emptyMap(),
        )

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
