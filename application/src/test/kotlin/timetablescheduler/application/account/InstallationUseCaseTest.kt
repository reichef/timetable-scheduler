package timetablescheduler.application.account

import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.fakes.InMemoryPasswordCredentialRepository
import timetablescheduler.application.fakes.InMemorySchoolRepository
import timetablescheduler.application.fakes.InMemoryTransactionRunner
import timetablescheduler.application.fakes.InMemoryUserAccountRepository
import timetablescheduler.application.fakes.PlainTextTestPasswordHasher
import timetablescheduler.domain.account.PasswordPolicy
import timetablescheduler.domain.account.Role
import timetablescheduler.domain.validation.ValidationError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InstallationUseCaseTest {

    private val schools = InMemorySchoolRepository()
    private val accounts = InMemoryUserAccountRepository()
    private val credentials = InMemoryPasswordCredentialRepository()
    private val hasher = PlainTextTestPasswordHasher()
    private val installation = InstallationUseCase(InMemoryTransactionRunner(), schools, accounts, credentials, hasher)

    private val initialSetup = InitialSetup(
        schoolName = "Gymnasium Nord",
        plannerUsername = "admin",
        plannerPassword = "sehr-geheim-1",
    )

    @Test
    fun `empty installation creates first school and initial planner assigned to it`() {
        val result = installation.execute(initialSetup)

        assertEquals(InstallationOutcome.INSTALLED, assertIs<UseCaseResult.Success<InstallationOutcome>>(result).value)
        val school = assertNotNull(schools.findByNameIgnoreCase("Gymnasium Nord"))
        val planner = assertNotNull(accounts.findByUsernameIgnoreCase("admin"))
        assertEquals(Role.PLANNER, planner.role)
        assertTrue(planner.active)
        assertEquals(setOf(school.id), planner.schoolIds)
        assertTrue(hasher.matches("sehr-geheim-1", assertNotNull(credentials.findPasswordHash(planner.id))))
    }

    @Test
    fun `existing installation is left untouched`() {
        installation.execute(initialSetup)

        val result = installation.execute(initialSetup.copy(schoolName = "Andere Schule", plannerUsername = "zweiter"))

        assertEquals(InstallationOutcome.ALREADY_INSTALLED, assertIs<UseCaseResult.Success<InstallationOutcome>>(result).value)
        assertEquals(null, schools.findByNameIgnoreCase("Andere Schule"))
        assertEquals(null, accounts.findByUsernameIgnoreCase("zweiter"))
    }

    @Test
    fun `initial password must have at least 10 characters`() {
        val result = installation.execute(initialSetup.copy(plannerPassword = "kurz"))

        val error = assertIs<ApplicationError.Invalid>(assertIs<UseCaseResult.Failure>(result).error)
        assertTrue(ValidationError.OutOfRange(PasswordPolicy.FIELD_PASSWORD, 10, null) in error.errors)
        assertEquals(false, accounts.existsAny())
    }
}
