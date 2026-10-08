package timetablescheduler.application.account

import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.error.outcome
import timetablescheduler.application.port.CredentialAuthenticator
import timetablescheduler.application.port.TransactionRunner
import timetablescheduler.application.port.UserAccountRepository
import timetablescheduler.domain.account.Role
import timetablescheduler.domain.common.AccountId

data class LoggedInUser(val accountId: AccountId, val role: Role)

/**
 * FR-030/FR-032: every failure — unknown user, wrong password, deactivated account — yields the same
 * [ApplicationError.AuthenticationFailed] so the response does not reveal which part was wrong.
 */
class LoginUseCase(
    private val transactions: TransactionRunner,
    private val authenticator: CredentialAuthenticator,
    private val accounts: UserAccountRepository,
) {
    fun execute(username: String, plainPassword: String): UseCaseResult<LoggedInUser> =
        transactions.inTransaction {
            outcome {
                val accountId = authenticator.authenticate(username, plainPassword)
                    ?: fail(ApplicationError.AuthenticationFailed)
                val account = accounts.findById(accountId)
                if (account == null || !account.active) fail(ApplicationError.AuthenticationFailed)
                LoggedInUser(account.id, account.role)
            }
        }
}
