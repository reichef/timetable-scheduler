package timetablescheduler.application.account

import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.fakes.InMemoryPasswordCredentialRepository
import timetablescheduler.application.fakes.InMemoryTransactionRunner
import timetablescheduler.application.fakes.InMemoryUserAccountRepository
import timetablescheduler.application.fakes.PlainTextTestPasswordHasher
import timetablescheduler.domain.account.InterfaceLanguage
import timetablescheduler.domain.account.Role
import timetablescheduler.domain.account.UserAccount
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.validation.ValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

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

    @Test
    fun `an active choice on the login page is saved for an account without language`() {
        val account = storedAccount("mueller", "geheim-genug-1")

        val loggedIn = assertIs<UseCaseResult.Success<LoggedInUser>>(login.execute("mueller", "geheim-genug-1", InterfaceLanguage.ENGLISH)).value

        assertEquals(InterfaceLanguage.ENGLISH, loggedIn.language)
        assertEquals(InterfaceLanguage.ENGLISH, accounts.findById(account.id)!!.preferredLanguage)
        assertEquals(account.version, accounts.findById(account.id)!!.version)
    }

    @Test
    fun `a saved account language wins over the login page choice`() {
        val account = storedAccount("mueller", "geheim-genug-1")
        accounts.updatePreferredLanguage(account.id, InterfaceLanguage.GERMAN)

        val loggedIn = assertIs<UseCaseResult.Success<LoggedInUser>>(login.execute("mueller", "geheim-genug-1", InterfaceLanguage.ENGLISH)).value

        assertEquals(InterfaceLanguage.GERMAN, loggedIn.language)
        assertEquals(InterfaceLanguage.GERMAN, accounts.findById(account.id)!!.preferredLanguage)
    }

    @Test
    fun `without an active choice nothing is saved`() {
        val account = storedAccount("mueller", "geheim-genug-1")

        val loggedIn = assertIs<UseCaseResult.Success<LoggedInUser>>(login.execute("mueller", "geheim-genug-1")).value

        assertNull(loggedIn.language)
        assertNull(accounts.findById(account.id)!!.preferredLanguage)
    }

    @Test
    fun `a failed login saves no language`() {
        val account = storedAccount("mueller", "geheim-genug-1")

        login.execute("mueller", "falsch-falsch", InterfaceLanguage.ENGLISH)

        assertNull(accounts.findById(account.id)!!.preferredLanguage)
    }
}
