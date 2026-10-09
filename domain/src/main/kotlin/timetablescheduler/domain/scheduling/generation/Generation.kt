package timetablescheduler.domain.scheduling.generation

import timetablescheduler.domain.common.AccountId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.scheduling.constraint.SchedulingConstraint
import timetablescheduler.domain.scheduling.constraint.SchedulingConstraintId
import timetablescheduler.domain.scheduling.constraint.TeacherFixation
import timetablescheduler.domain.scheduling.pareto.ParetoArchive
import timetablescheduler.domain.scheduling.solution.CourseKey
import timetablescheduler.domain.scheduling.solution.LessonAssignment
import timetablescheduler.domain.scheduling.solution.ObjectiveValues
import timetablescheduler.domain.scheduling.solution.TimetableCandidate
import timetablescheduler.domain.validation.ValidationCollector
import timetablescheduler.domain.validation.ValidationResult
import timetablescheduler.domain.validation.intInRange
import java.time.Instant
import java.util.UUID

@JvmInline
value class GenerationId(val value: UUID) {
    companion object {
        fun new() = GenerationId(UUID.randomUUID())
    }
}

@JvmInline
value class AlternativeId(val value: UUID) {
    companion object {
        fun new() = AlternativeId(UUID.randomUUID())
    }
}

/**
 * `STOPPED` = stopped early by a planner; `FAILED` = technical error or server restart; `FAILED_INFEASIBLE` = ended
 * without a single valid alternative because the hard constraints could not be met (G4).
 */
enum class GenerationStatus { RUNNING, FINISHED, STOPPED, FAILED, FAILED_INFEASIBLE }

/**
 * One run of the scheduler (FR-022): who started it when, its time limit (FR-009), status and progress, and the
 * fingerprint of the scheduling input at its start, which tells whether it is outdated (G8). The snapshot of the
 * constraints and fixations in effect at the start stays unchanged when they are changed or deleted later; the soft
 * constraints compared separately (FR-006a) refer to it. [infeasibleConstraintIds] names the hard constraints that can
 * force violations when the generation ended as infeasible (FR-017).
 */
data class Generation(
    val id: GenerationId,
    val schoolId: SchoolId,
    val startedBy: AccountId,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val timeLimitMinutes: Int,
    val maxAlternatives: Int,
    val status: GenerationStatus,
    val inputFingerprint: String,
    val alternativesFound: Int,
    val separateObjectiveIds: List<SchedulingConstraintId> = emptyList(),
    val constraintSnapshot: List<SchedulingConstraint> = emptyList(),
    val fixationSnapshot: List<TeacherFixation> = emptyList(),
    val infeasibleConstraintIds: List<SchedulingConstraintId> = emptyList(),
    val referenceAlternativeId: AlternativeId? = null,
) {
    val isRunning: Boolean get() = status == GenerationStatus.RUNNING

    fun endAs(finalStatus: GenerationStatus, at: Instant): Generation {
        require(finalStatus != GenerationStatus.RUNNING) { "A generation cannot end as running" }
        check(isRunning) { "Generation has already ended" }
        return copy(status = finalStatus, finishedAt = at)
    }

    companion object {
        const val FIELD_TIME_LIMIT = "timeLimitMinutes"
        const val DEFAULT_TIME_LIMIT_MINUTES = 10
        const val MIN_TIME_LIMIT_MINUTES = 1
        const val MAX_TIME_LIMIT_MINUTES = 60

        fun start(schoolId: SchoolId, startedBy: AccountId, startedAt: Instant, timeLimitMinutes: Int, inputFingerprint: String): ValidationResult<Generation> {
            val validation = ValidationCollector()
            validation.intInRange(FIELD_TIME_LIMIT, timeLimitMinutes, MIN_TIME_LIMIT_MINUTES, MAX_TIME_LIMIT_MINUTES)
            return validation.result {
                Generation(
                    id = GenerationId.new(),
                    schoolId = schoolId,
                    startedBy = startedBy,
                    startedAt = startedAt,
                    finishedAt = null,
                    timeLimitMinutes = timeLimitMinutes,
                    maxAlternatives = ParetoArchive.DEFAULT_CAPACITY,
                    status = GenerationStatus.RUNNING,
                    inputFingerprint = inputFingerprint,
                    alternativesFound = 0,
                )
            }
        }
    }
}

/**
 * One equally optimal timetable of a generation (FR-018): its objective values, the lessons, the periods that could
 * not be placed per course and how often each soft constraint is violated.
 */
data class Alternative(
    val id: AlternativeId,
    val generationId: GenerationId,
    val objectiveValues: ObjectiveValues,
    val lessons: List<LessonAssignment>,
    val unplaced: Map<CourseKey, Int>,
    val softViolations: Map<SchedulingConstraintId, Int>,
    val changedLessons: Int? = null,
) {
    val candidate: TimetableCandidate get() = TimetableCandidate(lessons, unplaced)

    fun unplacedPeriods(): Int = unplaced.values.sum()
}
