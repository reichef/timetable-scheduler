package timetablescheduler.domain.account

import timetablescheduler.domain.common.AccountId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.validation.ValidationError
import timetablescheduler.domain.validation.ValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UserAccountTest {
    private val gymnasiumNord = SchoolId.new()
    private val realschuleSued = SchoolId.new()

    private fun planner(schools: Set<SchoolId>): UserAccount =
        (UserAccount.create("planer", Role.PLANNER, schools) as ValidationResult.Valid).value

    @Test
    fun `creates active account with trimmed username`() {
        val result = UserAccount.create("  mueller ", Role.PLANNER, setOf(gymnasiumNord))

        val account = assertIs<ValidationResult.Valid<UserAccount>>(result).value
        assertEquals("mueller", account.username)
        assertTrue(account.active)
        assertEquals(setOf(gymnasiumNord), account.schoolIds)
    }

    @Test
    fun `rejects username shorter than 3 characters`() {
        val errors = assertIs<ValidationResult.Invalid>(UserAccount.create("ab", Role.PLANNER, setOf(gymnasiumNord))).errors
        assertTrue(ValidationError.OutOfRange(UserAccount.FIELD_USERNAME, 3, 60) in errors)
    }

    @Test
    fun `rejects username longer than 60 characters`() {
        val errors =
            assertIs<ValidationResult.Invalid>(
                UserAccount.create("a".repeat(61), Role.PLANNER, setOf(gymnasiumNord)),
            ).errors
        assertTrue(ValidationError.OutOfRange(UserAccount.FIELD_USERNAME, 3, 60) in errors)
    }

    @Test
    fun `rejects account without any school for both roles`() {
        Role.entries.forEach { role ->
            val errors = assertIs<ValidationResult.Invalid>(UserAccount.create("lehrer1", role, emptySet())).errors
            assertTrue(ValidationError.LastSchoolRemoved in errors)
        }
    }

    @Test
    fun `rejects removing the last school of an account`() {
        val account = planner(setOf(gymnasiumNord))

        val errors = assertIs<ValidationResult.Invalid>(account.withRoleAndSchools(Role.PLANNER, emptySet())).errors
        assertTrue(ValidationError.LastSchoolRemoved in errors)
    }

    @Test
    fun `active planner of a school is recognised only for assigned schools`() {
        val account = planner(setOf(gymnasiumNord))

        assertTrue(account.isActivePlannerOf(gymnasiumNord))
        assertFalse(account.isActivePlannerOf(realschuleSued))
        assertFalse(account.deactivated().isActivePlannerOf(gymnasiumNord))
    }

    @Test
    fun `deactivating the only active planner of a school leaves it uncovered`() {
        val mueller = planner(setOf(realschuleSued))

        val uncovered =
            PlannerCoverage.schoolsLeftWithoutActivePlanner(
                before = mueller,
                after = mueller.deactivated(),
                activePlannersOfSchool = { setOf(mueller.id) },
            )

        assertEquals(setOf(realschuleSued), uncovered)
    }

    @Test
    fun `downgrading or unassigning the only active planner leaves the school uncovered`() {
        val mueller = planner(setOf(gymnasiumNord, realschuleSued))
        val downgraded = (mueller.withRoleAndSchools(Role.TEACHER, mueller.schoolIds) as ValidationResult.Valid).value
        val unassigned = (mueller.withRoleAndSchools(Role.PLANNER, setOf(gymnasiumNord)) as ValidationResult.Valid).value
        val onlyMueller: (SchoolId) -> Set<AccountId> = { setOf(mueller.id) }

        assertEquals(
            setOf(gymnasiumNord, realschuleSued),
            PlannerCoverage.schoolsLeftWithoutActivePlanner(mueller, downgraded, onlyMueller),
        )
        assertEquals(setOf(realschuleSued), PlannerCoverage.schoolsLeftWithoutActivePlanner(mueller, unassigned, onlyMueller))
    }

    @Test
    fun `another active planner keeps the school covered`() {
        val mueller = planner(setOf(gymnasiumNord))
        val weber = planner(setOf(gymnasiumNord))

        val uncovered =
            PlannerCoverage.schoolsLeftWithoutActivePlanner(
                before = mueller,
                after = mueller.deactivated(),
                activePlannersOfSchool = { setOf(mueller.id, weber.id) },
            )

        assertTrue(uncovered.isEmpty())
    }

    @Test
    fun `password policy requires at least 10 characters`() {
        assertEquals(ValidationError.OutOfRange(PasswordPolicy.FIELD_PASSWORD, 10, null), PasswordPolicy.validate("123456789"))
        assertEquals(null, PasswordPolicy.validate("1234567890"))
    }
}
