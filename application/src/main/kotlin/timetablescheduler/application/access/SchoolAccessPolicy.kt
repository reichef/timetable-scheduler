package timetablescheduler.application.access

import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.port.UserAccountRepository
import timetablescheduler.domain.account.UserAccount
import timetablescheduler.domain.common.AccountId
import timetablescheduler.domain.common.SchoolId

/**
 * Decides whether an account may work with master data (FR-028, FR-028a). The account is re-read on every
 * call so deactivation and re-assignment take effect immediately (R6).
 */
class SchoolAccessPolicy(private val accounts: UserAccountRepository) {

    fun requirePlanner(accountId: AccountId): UseCaseResult<UserAccount> {
        val account = accounts.findById(accountId)
        return if (account != null && account.isActivePlanner()) {
            UseCaseResult.Success(account)
        } else {
            UseCaseResult.Failure(ApplicationError.AccessDenied)
        }
    }

    /** Unassigned schools answer [ApplicationError.NotFound] so their existence is not revealed. */
    fun requireSchoolAccess(accountId: AccountId, schoolId: SchoolId): UseCaseResult<UserAccount> =
        when (val plannerResult = requirePlanner(accountId)) {
            is UseCaseResult.Failure -> plannerResult
            is UseCaseResult.Success ->
                if (schoolId in plannerResult.value.schoolIds) plannerResult
                else UseCaseResult.Failure(ApplicationError.NotFound)
        }
}
