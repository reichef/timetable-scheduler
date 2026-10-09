package timetablescheduler.application.error

import timetablescheduler.application.readiness.ReadinessReport
import timetablescheduler.application.scheduling.TimetableNames
import timetablescheduler.domain.readiness.ReadinessIssue
import timetablescheduler.domain.scheduling.constraint.ConstraintType
import timetablescheduler.domain.scheduling.constraint.SchedulingConstraint
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

    /** Active hard constraints that can never be met together with the master data (002 FR-017). */
    data class ContradictingConstraints(val constraints: List<SchedulingConstraint>, val names: TimetableNames) : ApplicationError

    /** Another generation of the school is still running (002 FR-011). */
    data class GenerationRunning(val generationId: GenerationId) : ApplicationError
}

enum class DependantType { ROOM, ROOM_FEATURE, SUBJECT, CLASS_LEVEL, SCHOOL_CLASS, TIME_SLOT, TEACHER, SCHEDULING_CONSTRAINT, TEACHER_FIXATION }

/**
 * An entry that prevents a deletion (FR-023). For constraints [constraintType] says which catalogue entry it is, so the
 * web layer can name it in the user's language; [displayName] then names the entries the constraint refers to.
 */
data class Dependant(val type: DependantType, val displayName: String, val constraintType: ConstraintType? = null)
