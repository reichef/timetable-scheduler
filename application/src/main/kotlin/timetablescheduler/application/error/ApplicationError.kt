package timetablescheduler.application.error

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
}

enum class DependantType { ROOM, ROOM_FEATURE, SUBJECT, CLASS_LEVEL, SCHOOL_CLASS, TIME_SLOT, TEACHER, USER_ACCOUNT }

/** An entry that prevents a deletion (FR-023). */
data class Dependant(val type: DependantType, val displayName: String)
