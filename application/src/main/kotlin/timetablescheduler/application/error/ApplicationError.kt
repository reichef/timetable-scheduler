package timetablescheduler.application.error

import timetablescheduler.application.readiness.ReadinessReport
import timetablescheduler.domain.readiness.ReadinessIssue
import timetablescheduler.domain.scheduling.generation.GenerationId
import timetablescheduler.domain.validation.ValidationError

/**
 * Business errors of a use case (constitution III, R10). They may carry names the requesting planner is
 * allowed to see and are rendered as business messages; they are never logged with that content.
 * Technical failures are exceptions and handled separately by the web layer.
 */
sealed interface ApplicationError {
    data object NotFound : ApplicationError
    data object AccessDenied : ApplicationError
    data object VersionConflict : ApplicationError
    data object AuthenticationFailed : ApplicationError
    data class Invalid(val errors: List<ValidationError>) : ApplicationError
    data class DeletionBlocked(val visibleDependants: List<Dependant>, val hiddenDependantCount: Int) : ApplicationError

    /** Readiness issues that must be fixed before scheduling (002 FR-010). */
    data class GenerationBlocked(val blockingIssues: List<ReadinessIssue>, val names: ReadinessReport) : ApplicationError

    /** Another generation of the school is still running (002 FR-011). */
    data class GenerationRunning(val generationId: GenerationId) : ApplicationError
}

enum class DependantType { ROOM, ROOM_FEATURE, SUBJECT, CLASS_LEVEL, SCHOOL_CLASS, TIME_SLOT, TEACHER }

/** An entry that prevents a deletion (FR-023). */
data class Dependant(val type: DependantType, val displayName: String)
