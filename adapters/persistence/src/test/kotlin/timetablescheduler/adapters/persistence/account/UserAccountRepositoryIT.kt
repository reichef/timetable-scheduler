package timetablescheduler.adapters.persistence.account

import timetablescheduler.adapters.persistence.PostgresTestBase
import timetablescheduler.adapters.persistence.school.ExposedSchoolRepository
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import timetablescheduler.domain.account.InterfaceLanguage
import timetablescheduler.domain.account.Role
import timetablescheduler.domain.account.UserAccount
import timetablescheduler.domain.school.School
import timetablescheduler.domain.validation.ValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserAccountRepositoryIT : PostgresTestBase() {

    private val schools = ExposedSchoolRepository()
    private val accounts = ExposedUserAccountRepository()
    private val credentials = ExposedPasswordCredentialRepository()
    private val hasher = BcryptPasswordHasher()

    private fun newSchool(name: String): School = (School.create(name) as ValidationResult.Valid).value

    private fun newPlanner(username: String, school: School): UserAccount =
        (UserAccount.create(username, Role.PLANNER, setOf(school.id)) as ValidationResult.Valid).value

    @Test
    fun `stores and loads an account with its schools`() {
        val nord = newSchool("Gymnasium Nord")
        val sued = newSchool("Realschule Süd")
        val account = (UserAccount.create("mueller", Role.PLANNER, setOf(nord.id, sued.id)) as ValidationResult.Valid).value
        inTransaction {
            schools.insert(nord)
            schools.insert(sued)
            accounts.insert(account)
        }

        val loaded = inTransaction { accounts.findById(account.id) }

        assertEquals(account, loaded)
        assertTrue(inTransaction { accounts.existsAny() })
    }

    @Test
    fun `finds account by username case-insensitively`() {
        val nord = newSchool("Gymnasium Nord")
        inTransaction {
            schools.insert(nord)
            accounts.insert(newPlanner("Mueller", nord))
        }

        assertNotNull(inTransaction { accounts.findByUsernameIgnoreCase("mUELLER") })
        assertNull(inTransaction { accounts.findByUsernameIgnoreCase("weber") })
    }

    @Test
    fun `finds all accounts of a school`() {
        val nord = newSchool("Gymnasium Nord")
        val teacher = (UserAccount.create("lehrer1", Role.TEACHER, setOf(nord.id)) as ValidationResult.Valid).value
        val planner = newPlanner("mueller", nord)
        inTransaction {
            schools.insert(nord)
            accounts.insert(planner)
            accounts.insert(teacher)
        }

        assertEquals(setOf(planner, teacher), inTransaction { accounts.findBySchool(nord.id) }.toSet())
    }

    @Test
    fun `username is unique regardless of case`() {
        val nord = newSchool("Gymnasium Nord")
        inTransaction {
            schools.insert(nord)
            accounts.insert(newPlanner("mueller", nord))
        }

        assertFailsWith<Exception> { inTransaction { accounts.insert(newPlanner("MUELLER", nord)) } }
    }

    @Test
    fun `update with current version succeeds and increments version`() {
        val nord = newSchool("Gymnasium Nord")
        val account = newPlanner("mueller", nord)
        inTransaction {
            schools.insert(nord)
            accounts.insert(account)
        }

        assertTrue(inTransaction { accounts.update(account.deactivated()) })

        val loaded = assertNotNull(inTransaction { accounts.findById(account.id) })
        assertFalse(loaded.active)
        assertEquals(account.version + 1, loaded.version)
    }

    @Test
    fun `update with stale version is rejected`() {
        val nord = newSchool("Gymnasium Nord")
        val account = newPlanner("mueller", nord)
        inTransaction {
            schools.insert(nord)
            accounts.insert(account)
            accounts.update(account.deactivated())
        }

        assertFalse(inTransaction { accounts.update(account.activated()) })
    }

    @Test
    fun `active planners of a school exclude deactivated and teacher accounts`() {
        val nord = newSchool("Gymnasium Nord")
        val mueller = newPlanner("mueller", nord)
        val weber = newPlanner("weber", nord).deactivated()
        val lehrer = (UserAccount.create("lehrer1", Role.TEACHER, setOf(nord.id)) as ValidationResult.Valid).value
        inTransaction {
            schools.insert(nord)
            listOf(mueller, weber, lehrer).forEach(accounts::insert)
        }

        assertEquals(setOf(mueller.id), inTransaction { accounts.findActivePlannerIdsOfSchool(nord.id) })
    }

    @Test
    fun `password is stored only as bcrypt hash`() {
        val nord = newSchool("Gymnasium Nord")
        val account = newPlanner("mueller", nord)
        inTransaction {
            schools.insert(nord)
            accounts.insert(account)
            credentials.save(account.id, hasher.hash("sehr-geheim-1"))
        }

        val storedHash = assertNotNull(inTransaction { credentials.findPasswordHash(account.id) })
        assertNotEquals("sehr-geheim-1", storedHash)
        assertTrue(storedHash.startsWith("\$2"))
        assertTrue(hasher.matches("sehr-geheim-1", storedHash))
        assertFalse(hasher.matches("falsch-falsch", storedHash))
    }

    @Test
    fun `saving a credential again replaces the old one`() {
        val nord = newSchool("Gymnasium Nord")
        val account = newPlanner("mueller", nord)
        inTransaction {
            schools.insert(nord)
            accounts.insert(account)
            credentials.save(account.id, hasher.hash("erstes-passwort"))
            credentials.save(account.id, hasher.hash("zweites-passwort"))
        }

        val storedHash = assertNotNull(inTransaction { credentials.findPasswordHash(account.id) })
        assertTrue(hasher.matches("zweites-passwort", storedHash))
    }

    @Test
    fun `preferred language is stored without touching version, role or active flag`() {
        val nord = newSchool("Gymnasium Nord")
        val planner = newPlanner("mueller", nord)
        inTransaction {
            schools.insert(nord)
            accounts.insert(planner)
        }
        assertNull(inTransaction { accounts.findById(planner.id) }!!.preferredLanguage)

        inTransaction { accounts.updatePreferredLanguage(planner.id, InterfaceLanguage.ENGLISH) }

        assertEquals(planner.copy(preferredLanguage = InterfaceLanguage.ENGLISH), inTransaction { accounts.findById(planner.id) })
    }

    @Test
    fun `versioned update keeps the saved language`() {
        val nord = newSchool("Gymnasium Nord")
        val planner = newPlanner("mueller", nord)
        inTransaction {
            schools.insert(nord)
            accounts.insert(planner)
            accounts.updatePreferredLanguage(planner.id, InterfaceLanguage.ENGLISH)
        }

        assertTrue(inTransaction { accounts.update(planner.deactivated()) })

        assertEquals(InterfaceLanguage.ENGLISH, inTransaction { accounts.findById(planner.id) }!!.preferredLanguage)
    }

    @Test
    fun `database accepts only de and en as language`() {
        val nord = newSchool("Gymnasium Nord")
        inTransaction {
            schools.insert(nord)
            accounts.insert(newPlanner("mueller", nord))
        }

        val rejected = runCatching {
            inTransaction { TransactionManager.current().exec("UPDATE user_account SET preferred_language = 'fr'") }
        }

        assertTrue(rejected.isFailure)
    }
}
