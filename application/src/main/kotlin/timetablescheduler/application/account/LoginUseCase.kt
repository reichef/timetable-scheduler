package timetablescheduler.application.account

import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.error.outcome
import timetablescheduler.application.port.CredentialAuthenticator
import timetablescheduler.application.port.TransactionRunner
import timetablescheduler.application.port.UserAccountRepository
import timetablescheduler.domain.account.InterfaceLanguage
import timetablescheduler.domain.account.Role
import timetablescheduler.domain.common.AccountId

/** [language]: the account's saved language, else the language chosen on the login page; null = use the browser's. */
data class LoggedInUser(val accountId: AccountId, val role: Role, val language: InterfaceLanguage?)

/**
 * FR-030/FR-032: every failure — unknown user, wrong password, deactivated account — yields the same
 * [ApplicationError.AuthenticationFailed] so the response does not reveal which part was wrong.
 */
class LoginUseCase(
    private val transactions: TransactionRunner,
    private val authenticator: CredentialAuthenticator,
    private val accounts: UserAccountRepository,
) {
    /**
     * [chosenLanguage] is a language the user actively chose on the login page before logging in. FR-042: it is saved
     * only if the account has no language yet; a saved language always wins.
     */
    fun execute(username: String, plainPassword: String, chosenLanguage: InterfaceLanguage? = null): UseCaseResult<LoggedInUser> =
        transactions.inTransaction {
            outcome {
                val accountId = authenticator.authenticate(username, plainPassword)
                    ?: fail(ApplicationError.AuthenticationFailed)
                val account = accounts.findById(accountId)
                if (account == null || !account.active) fail(ApplicationError.AuthenticationFailed)
                if (account.preferredLanguage == null && chosenLanguage != null) {
                    accounts.updatePreferredLanguage(account.id, chosenLanguage)
                }
                LoggedInUser(account.id, account.role, account.preferredLanguage ?: chosenLanguage)
            }
        }
}
