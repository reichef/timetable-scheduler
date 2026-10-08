package timetablescheduler.application.account

import timetablescheduler.application.access.SchoolAccessPolicy
import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.error.OutcomeScope
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.error.outcome
import timetablescheduler.application.port.PasswordCredentialRepository
import timetablescheduler.application.port.PasswordHasher
import timetablescheduler.application.port.TransactionRunner
import timetablescheduler.application.port.UserAccountRepository
import timetablescheduler.domain.account.PasswordPolicy
import timetablescheduler.domain.account.PlannerCoverage
import timetablescheduler.domain.account.Role
import timetablescheduler.domain.account.UserAccount
import timetablescheduler.domain.common.AccountId
import timetablescheduler.domain.validation.ValidationError
import timetablescheduler.domain.validation.ValidationResult

/** Loads the account for a change and checks optimistic locking against [expectedVersion] (FR-027). */
private fun OutcomeScope.loadAccountForChange(accounts: UserAccountRepository, accountId: AccountId, expectedVersion: Int): UserAccount {
    val account = accounts.findById(accountId).orNotFound()
    if (account.version != expectedVersion) fail(ApplicationError.VersionConflict)
    return account
}

/**
 * FR-033: rejects changing [before] into [after] when a school would be left without an active planner.
 * With one school this means the installation keeps at least one active planner.
 */
private fun OutcomeScope.requireActivePlannerLeft(accounts: UserAccountRepository, before: UserAccount, after: UserAccount) {
    val uncoveredSchools = PlannerCoverage.schoolsLeftWithoutActivePlanner(before, after, accounts::findActivePlannerIdsOfSchool)
    if (uncoveredSchools.isNotEmpty()) {
        fail(ApplicationError.Invalid(uncoveredSchools.map { ValidationError.NoActivePlannerLeft(it) }))
    }
}

/** Saves an account change after the FR-033 check and returns the stored state. */
private fun OutcomeScope.saveAccountChange(accounts: UserAccountRepository, before: UserAccount, after: UserAccount): UserAccount {
    requireActivePlannerLeft(accounts, before, after)
    requireUpdated(accounts.update(after))
    return after.copy(version = after.version + 1)
}

private fun OutcomeScope.requireValidPassword(plainPassword: String) {
    PasswordPolicy.validate(plainPassword)?.let { fail(ApplicationError.Invalid(listOf(it))) }
}

class ListAccounts(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val accounts: UserAccountRepository,
) {
    /** All accounts of the planner's schools — with one school all accounts (FR-032). */
    fun execute(actor: AccountId): UseCaseResult<List<UserAccount>> =
        transactions.inTransaction {
            outcome {
                val planner = access.requirePlanner(actor).bind()
                planner.schoolIds.flatMap(accounts::findBySchool).distinctBy { it.id }.sortedBy { it.username.lowercase() }
            }
        }
}

class GetAccount(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val accounts: UserAccountRepository,
) {
    fun execute(actor: AccountId, accountId: AccountId): UseCaseResult<UserAccount> =
        transactions.inTransaction {
            outcome {
                access.requirePlanner(actor).bind()
                accounts.findById(accountId).orNotFound()
            }
        }
}

/** A planner creates an account; it is assigned to the planner's school automatically (FR-032, FR-036). */
class CreateAccount(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val accounts: UserAccountRepository,
    private val credentials: PasswordCredentialRepository,
    private val hasher: PasswordHasher,
) {
    fun execute(actor: AccountId, username: String, role: Role, initialPassword: String): UseCaseResult<UserAccount> =
        transactions.inTransaction {
            outcome {
                val planner = access.requirePlanner(actor).bind()
                val created = UserAccount.create(username, role, planner.schoolIds)
                val errors = validationErrorsOf(created) + duplicateUsernameErrors(username) +
                    listOfNotNull(PasswordPolicy.validate(initialPassword))
                if (errors.isNotEmpty()) fail(ApplicationError.Invalid(errors))
                val account = created.bind()
                accounts.insert(account)
                credentials.save(account.id, hasher.hash(initialPassword))
                account
            }
        }

    private fun validationErrorsOf(created: ValidationResult<UserAccount>): List<ValidationError> =
        (created as? ValidationResult.Invalid)?.errors.orEmpty()

    /** FR-032: usernames are unique in the installation, case-insensitive. */
    private fun duplicateUsernameErrors(username: String): List<ValidationError> =
        if (accounts.findByUsernameIgnoreCase(username) != null) listOf(ValidationError.DuplicateValue(UserAccount.FIELD_USERNAME))
        else emptyList()
}

class UpdateAccountRole(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val accounts: UserAccountRepository,
) {
    fun execute(actor: AccountId, accountId: AccountId, newRole: Role, expectedVersion: Int): UseCaseResult<UserAccount> =
        transactions.inTransaction {
            outcome {
                access.requirePlanner(actor).bind()
                val account = loadAccountForChange(accounts, accountId, expectedVersion)
                val changed = account.withRoleAndSchools(newRole, account.schoolIds).bind()
                saveAccountChange(accounts, account, changed)
            }
        }
}

class DeactivateAccount(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val accounts: UserAccountRepository,
) {
    fun execute(actor: AccountId, accountId: AccountId, expectedVersion: Int): UseCaseResult<UserAccount> =
        transactions.inTransaction {
            outcome {
                access.requirePlanner(actor).bind()
                val account = loadAccountForChange(accounts, accountId, expectedVersion)
                saveAccountChange(accounts, account, account.deactivated())
            }
        }
}

class ActivateAccount(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val accounts: UserAccountRepository,
) {
    fun execute(actor: AccountId, accountId: AccountId, expectedVersion: Int): UseCaseResult<UserAccount> =
        transactions.inTransaction {
            outcome {
                access.requirePlanner(actor).bind()
                val account = loadAccountForChange(accounts, accountId, expectedVersion)
                saveAccountChange(accounts, account, account.activated())
            }
        }
}

/** A planner sets a new password for another account (FR-032, FR-034). */
class ResetPassword(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val accounts: UserAccountRepository,
    private val credentials: PasswordCredentialRepository,
    private val hasher: PasswordHasher,
) {
    fun execute(actor: AccountId, accountId: AccountId, newPassword: String): UseCaseResult<Unit> =
        transactions.inTransaction {
            outcome {
                access.requirePlanner(actor).bind()
                val account = accounts.findById(accountId).orNotFound()
                requireValidPassword(newPassword)
                credentials.save(account.id, hasher.hash(newPassword))
            }
        }
}

/** Every active user changes their own password after proving the current one (FR-032). */
class ChangeOwnPassword(
    private val transactions: TransactionRunner,
    private val accounts: UserAccountRepository,
    private val credentials: PasswordCredentialRepository,
    private val hasher: PasswordHasher,
) {
    fun execute(actor: AccountId, currentPassword: String, newPassword: String): UseCaseResult<Unit> =
        transactions.inTransaction {
            outcome {
                val account = accounts.findById(actor)
                if (account == null || !account.active) fail(ApplicationError.AccessDenied)
                val storedHash = credentials.findPasswordHash(account.id)
                if (storedHash == null || !hasher.matches(currentPassword, storedHash)) {
                    fail(ApplicationError.Invalid(listOf(ValidationError.IncorrectCurrentPassword)))
                }
                requireValidPassword(newPassword)
                credentials.save(account.id, hasher.hash(newPassword))
            }
        }
}

class AccountUseCases(
    val list: ListAccounts,
    val get: GetAccount,
    val create: CreateAccount,
    val updateRole: UpdateAccountRole,
    val deactivate: DeactivateAccount,
    val activate: ActivateAccount,
    val resetPassword: ResetPassword,
    val changeOwnPassword: ChangeOwnPassword,
)
