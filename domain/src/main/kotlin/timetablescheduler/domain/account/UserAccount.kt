package timetablescheduler.domain.account

import timetablescheduler.domain.common.AccountId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.validation.ValidationCollector
import timetablescheduler.domain.validation.ValidationError
import timetablescheduler.domain.validation.ValidationResult

enum class Role { PLANNER, TEACHER }

/**
 * A person who may log in. Credentials are kept elsewhere so single sign-on can be added later (FR-035).
 * Every account always has at least one school (FR-031a).
 */
data class UserAccount(
    val id: AccountId,
    val username: String,
    val role: Role,
    val active: Boolean,
    val schoolIds: Set<SchoolId>,
    val version: Int,
    /** Chosen by the user; null until the first active choice (FR-039, FR-042). */
    val preferredLanguage: InterfaceLanguage? = null,
) {
    fun isActivePlanner(): Boolean = active && role == Role.PLANNER

    fun isActivePlannerOf(schoolId: SchoolId): Boolean = isActivePlanner() && schoolId in schoolIds

    fun withRoleAndSchools(newRole: Role, newSchoolIds: Set<SchoolId>): ValidationResult<UserAccount> {
        val validation = ValidationCollector()
        validation.add(lastSchoolError(newSchoolIds))
        return validation.result { copy(role = newRole, schoolIds = newSchoolIds) }
    }

    fun withPreferredLanguage(language: InterfaceLanguage): UserAccount = copy(preferredLanguage = language)

    fun deactivated(): UserAccount = copy(active = false)

    fun activated(): UserAccount = copy(active = true)

    companion object {
        const val FIELD_USERNAME = "username"
        const val USERNAME_MIN_LENGTH = 3
        const val USERNAME_MAX_LENGTH = 60

        fun create(username: String, role: Role, schoolIds: Set<SchoolId>): ValidationResult<UserAccount> {
            val validation = ValidationCollector()
            val trimmedUsername = username.trim()
            if (trimmedUsername.length !in USERNAME_MIN_LENGTH..USERNAME_MAX_LENGTH) {
                validation.add(ValidationError.OutOfRange(FIELD_USERNAME, USERNAME_MIN_LENGTH, USERNAME_MAX_LENGTH))
            }
            validation.add(lastSchoolError(schoolIds))
            return validation.result {
                UserAccount(AccountId.new(), trimmedUsername, role, active = true, schoolIds = schoolIds, version = 0)
            }
        }

        private fun lastSchoolError(schoolIds: Set<SchoolId>): ValidationError? =
            if (schoolIds.isEmpty()) ValidationError.LastSchoolRemoved else null
    }
}
