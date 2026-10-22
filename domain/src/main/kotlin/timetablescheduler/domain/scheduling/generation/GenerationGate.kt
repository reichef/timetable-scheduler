package timetablescheduler.domain.scheduling.generation

import timetablescheduler.domain.readiness.ReadinessIssue

/** Readiness issues split into those that prevent a start and those the planner confirms (FR-010). */
data class GateResult(
    val blocking: List<ReadinessIssue>,
    val warnings: List<ReadinessIssue>,
)

/**
 * G11: issues that make the scheduling problem ill-defined block the start — a teacher load or a level above the time
 * grid, and teacher fixations made invalid by later master-data changes. All others only limit how completely the
 * classes can be filled and are shown as warnings.
 */
object GenerationGate {
    fun classify(issues: List<ReadinessIssue>): GateResult {
        val (blocking, warnings) = issues.partition(::isBlocking)
        return GateResult(blocking, warnings)
    }

    private fun isBlocking(issue: ReadinessIssue): Boolean =
        when (issue) {
            is ReadinessIssue.TeacherLoadExceedsGrid, is ReadinessIssue.LevelExceedsGrid, is ReadinessIssue.InvalidTeacherFixation -> true
            else -> false
        }
}
