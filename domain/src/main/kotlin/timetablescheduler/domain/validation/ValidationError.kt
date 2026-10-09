package timetablescheduler.domain.validation

import timetablescheduler.domain.common.SchoolId

/**
 * A violated business rule. Errors are typed values (not exception messages) so the web layer can render
 * them as business messages and logs never need to contain the offending input (constitution III).
 */
sealed interface ValidationError {
    data class BlankValue(val field: String) : ValidationError
    data class TooLong(val field: String, val maxLength: Int) : ValidationError
    data class OutOfRange(val field: String, val min: Int, val max: Int?) : ValidationError
    data class InvalidFormat(val field: String) : ValidationError
    data class DuplicateValue(val field: String) : ValidationError
    data class InvalidReference(val field: String) : ValidationError
    data object Overlap : ValidationError
    data object EndNotAfterStart : ValidationError
    data object LastSchoolRemoved : ValidationError
    data class NoActivePlannerLeft(val schoolId: SchoolId) : ValidationError
    data object IncorrectCurrentPassword : ValidationError

    /** The room is already the home room of the class [otherClassName] (002 FR-002a); the planner may see that class. */
    data class HomeRoomTaken(val otherClassName: String) : ValidationError

    /** A required confirmation (e.g. of readiness warnings before a generation) was not given. */
    data class ConfirmationMissing(val field: String) : ValidationError
}
