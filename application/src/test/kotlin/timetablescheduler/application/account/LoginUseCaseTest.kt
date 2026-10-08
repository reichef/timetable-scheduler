package timetablescheduler.application.account

import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.fakes.InMemoryPasswordCredentialRepository
import timetablescheduler.application.fakes.InMemoryTransactionRunner
import timetablescheduler.application.fakes.InMemoryUserAccountRepository
import timetablescheduler.application.fakes.PlainTextTestPasswordHasher
import timetablescheduler.domain.account.Role
import timetablescheduler.domain.account.UserAccount
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.validation.ValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LoginUseCaseTest {

    private val accounts = InMemoryUserAccountRepository()
    private val credentials = InMemoryPasswordCredentialRepository()
    private val hasher = PlainTextTestPasswordHasher()
    private val login = LoginUseCase(
        InMemoryTransactionRunner(),
        PasswordCredentialAuthenticator(accounts, credentials, hasher),
        accounts,
    )

    private fun storedAccount(username: String, password: String, role: Role = Role.PLANNER): UserAccount {
        val account = (UserAccount.create(username, role, setOf(SchoolId.new())) as ValidationResult.Valid).value
        accounts.insert(account)
        credentials.save(account.id, hasher.hash(password))
        return account
    }

    @Test
    fun `correct credentials log the user in with their role`() {
        val account = storedAccount("mueller", "geheim-genug-1")

        val result = login.execute("mueller", "geheim-genug-1")

        val loggedIn = assertIs<UseCaseResult.Success<LoggedInUser>>(result).value
        assertEquals(account.id, loggedIn.accountId)
        assertEquals(Role.PLANNER, loggedIn.role)
    }

    @Test
    fun `username is matched case-insensitively`() {
        storedAccount("mueller", "geheim-genug-1")

        assertIs<UseCaseResult.Success<LoggedInUser>>(login.execute("Mueller", "geheim-genug-1"))
    }

    @Test
    fun `wrong password and unknown username fail identically`() {
        storedAccount("mueller", "geheim-genug-1")

        val wrongPassword = login.execute("mueller", "falsches-passwort")
        val unknownUser = login.execute("niemand", "geheim-genug-1")

        assertEquals(UseCaseResult.Failure(ApplicationError.AuthenticationFailed), wrongPassword)
        assertEquals(wrongPassword, unknownUser)
    }

    @Test
    fun `deactivated account cannot log in`() {
        val account = storedAccount("mueller", "geheim-genug-1")
        accounts.update(account.deactivated())

        assertEquals(UseCaseResult.Failure(ApplicationError.AuthenticationFailed), login.execute("mueller", "geheim-genug-1"))
    }

    @Test
    fun `teacher account can log in`() {
        storedAccount("lehrer1", "geheim-genug-1", Role.TEACHER)

        val loggedIn = assertIs<UseCaseResult.Success<LoggedInUser>>(login.execute("lehrer1", "geheim-genug-1")).value
        assertEquals(Role.TEACHER, loggedIn.role)
    }
}
