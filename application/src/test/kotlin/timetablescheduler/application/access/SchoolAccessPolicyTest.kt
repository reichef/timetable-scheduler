package timetablescheduler.application.access

import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.fakes.InMemoryUserAccountRepository
import timetablescheduler.domain.account.Role
import timetablescheduler.domain.account.UserAccount
import timetablescheduler.domain.common.AccountId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.validation.ValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SchoolAccessPolicyTest {

    private val gymnasiumNord = SchoolId.new()
    private val realschuleSued = SchoolId.new()
    private val accounts = InMemoryUserAccountRepository()
    private val policy = SchoolAccessPolicy(accounts)

    private fun storedAccount(role: Role, schools: Set<SchoolId>, active: Boolean = true): UserAccount {
        val account = (UserAccount.create("user${accounts.count()}", role, schools) as ValidationResult.Valid).value
            .let { if (active) it else it.deactivated() }
        accounts.insert(account)
        return account
    }

    @Test
    fun `active planner gets access to assigned school`() {
        val planner = storedAccount(Role.PLANNER, setOf(gymnasiumNord))

        val result = policy.requireSchoolAccess(planner.id, gymnasiumNord)

        assertEquals(planner.id, assertIs<UseCaseResult.Success<UserAccount>>(result).value.id)
    }

    @Test
    fun `unassigned school is reported as not found so its existence is not revealed`() {
        val planner = storedAccount(Role.PLANNER, setOf(gymnasiumNord))

        val result = policy.requireSchoolAccess(planner.id, realschuleSued)

        assertEquals(ApplicationError.NotFound, assertIs<UseCaseResult.Failure>(result).error)
    }

    @Test
    fun `teacher account is denied master data access`() {
        val teacher = storedAccount(Role.TEACHER, setOf(gymnasiumNord))

        assertEquals(ApplicationError.AccessDenied, (policy.requirePlanner(teacher.id) as UseCaseResult.Failure).error)
        assertEquals(
            ApplicationError.AccessDenied,
            (policy.requireSchoolAccess(teacher.id, gymnasiumNord) as UseCaseResult.Failure).error,
        )
    }

    @Test
    fun `deactivated planner is denied`() {
        val planner = storedAccount(Role.PLANNER, setOf(gymnasiumNord), active = false)

        assertEquals(ApplicationError.AccessDenied, (policy.requirePlanner(planner.id) as UseCaseResult.Failure).error)
    }

    @Test
    fun `unknown account is denied`() {
        assertEquals(ApplicationError.AccessDenied, (policy.requirePlanner(AccountId.new()) as UseCaseResult.Failure).error)
    }

    @Test
    fun `changes to the account take effect immediately`() {
        val planner = storedAccount(Role.PLANNER, setOf(gymnasiumNord))
        accounts.update(planner.deactivated())

        assertIs<UseCaseResult.Failure>(policy.requireSchoolAccess(planner.id, gymnasiumNord))
    }
}
