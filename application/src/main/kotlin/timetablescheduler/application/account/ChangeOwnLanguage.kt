package timetablescheduler.application.account

import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.error.outcome
import timetablescheduler.application.port.TransactionRunner
import timetablescheduler.application.port.UserAccountRepository
import timetablescheduler.domain.account.InterfaceLanguage
import timetablescheduler.domain.common.AccountId

/** Every active user saves their own interface language (FR-039); planners cannot change it for others. */
class ChangeOwnLanguage(
    private val transactions: TransactionRunner,
    private val accounts: UserAccountRepository,
) {
    fun execute(
        actor: AccountId,
        language: InterfaceLanguage,
    ): UseCaseResult<Unit> =
        transactions.inTransaction {
            outcome {
                val account = accounts.findById(actor)
                if (account == null || !account.active) {
                    fail(ApplicationError.AccessDenied)
                }
                accounts.updatePreferredLanguage(account.id, language)
            }
        }
}
