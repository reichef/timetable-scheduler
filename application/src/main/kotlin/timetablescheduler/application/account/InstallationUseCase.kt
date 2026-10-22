package timetablescheduler.application.account

import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.error.outcome
import timetablescheduler.application.port.PasswordCredentialRepository
import timetablescheduler.application.port.PasswordHasher
import timetablescheduler.application.port.SchedulingConstraintRepository
import timetablescheduler.application.port.SchoolRepository
import timetablescheduler.application.port.TransactionRunner
import timetablescheduler.application.port.UserAccountRepository
import timetablescheduler.domain.account.PasswordPolicy
import timetablescheduler.domain.account.Role
import timetablescheduler.domain.account.UserAccount
import timetablescheduler.domain.scheduling.constraint.DefaultConstraints
import timetablescheduler.domain.school.School

data class InitialSetup(val schoolName: String, val plannerUsername: String, val plannerPassword: String)

enum class InstallationOutcome { INSTALLED, ALREADY_INSTALLED }

/**
 * FR-031: an empty installation gets its first school and an initial planner assigned to it, atomically, and the
 * school starts with the default scheduling constraints (002 FR-013c).
 */
class InstallationUseCase(
    private val transactions: TransactionRunner,
    private val schools: SchoolRepository,
    private val accounts: UserAccountRepository,
    private val credentials: PasswordCredentialRepository,
    private val hasher: PasswordHasher,
    private val constraints: SchedulingConstraintRepository,
) {
    fun isInstalled(): Boolean =
        (transactions.inTransaction { UseCaseResult.Success(accounts.existsAny()) } as UseCaseResult.Success).value

    fun execute(setup: InitialSetup): UseCaseResult<InstallationOutcome> =
        transactions.inTransaction {
            outcome {
                if (accounts.existsAny()) return@outcome InstallationOutcome.ALREADY_INSTALLED
                PasswordPolicy.validate(setup.plannerPassword)?.let { fail(ApplicationError.Invalid(listOf(it))) }
                val firstSchool = School.create(setup.schoolName).bind()
                val initialPlanner = UserAccount.create(setup.plannerUsername, Role.PLANNER, setOf(firstSchool.id)).bind()
                schools.insert(firstSchool)
                accounts.insert(initialPlanner)
                credentials.save(initialPlanner.id, hasher.hash(setup.plannerPassword))
                DefaultConstraints.forSchool(firstSchool.id).forEach(constraints::insert)
                InstallationOutcome.INSTALLED
            }
        }
}
