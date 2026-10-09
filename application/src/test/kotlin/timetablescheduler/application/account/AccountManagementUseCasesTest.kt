package timetablescheduler.application.account

import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.fakes.MasterDataWorld
import timetablescheduler.domain.account.PasswordPolicy
import timetablescheduler.domain.account.Role
import timetablescheduler.domain.account.UserAccount
import timetablescheduler.domain.common.AccountId
import timetablescheduler.domain.validation.ValidationError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AccountManagementUseCasesTest {

    private val world = MasterDataWorld()
    private val nord = world.school("Gymnasium Nord")
    private val admin = world.planner("admin", nord.id)
    private val accounts get() = world.useCases.accounts

    private fun <T> UseCaseResult<T>.value(): T = assertIs<UseCaseResult.Success<T>>(this).value

    private fun UseCaseResult<*>.validationErrors(): List<ValidationError> =
        assertIs<ApplicationError.Invalid>(assertIs<UseCaseResult.Failure>(this).error).errors

    private fun stored(account: UserAccount): UserAccount = world.accounts.findById(account.id)!!

    private fun canLogIn(username: String, password: String): Boolean =
        world.useCases.login.execute(username, password) is UseCaseResult.Success

    @Test
    fun `created account is assigned to the school and can log in`() {
        val created = accounts.create.execute(admin.id, " mueller ", Role.PLANNER, "anfangs-passwort").value()

        assertEquals("mueller", created.username)
        assertEquals(setOf(nord.id), created.schoolIds)
        assertTrue(created.active)
        assertTrue(canLogIn("mueller", "anfangs-passwort"))
    }

    @Test
    fun `created teacher account has no access to master data`() {
        val teacher = accounts.create.execute(admin.id, "lehrer1", Role.TEACHER, "anfangs-passwort").value()

        assertEquals(UseCaseResult.Failure(ApplicationError.AccessDenied), world.schoolUseCases.list.execute(teacher.id))
    }

    @Test
    fun `username length, uniqueness and password length are validated together`() {
        world.planner("mueller", nord.id)

        assertEquals(
            listOf(ValidationError.OutOfRange(UserAccount.FIELD_USERNAME, 3, 60), ValidationError.OutOfRange(PasswordPolicy.FIELD_PASSWORD, 10, null)),
            accounts.create.execute(admin.id, "ab", Role.TEACHER, "kurz").validationErrors(),
        )
        assertEquals(
            listOf(ValidationError.DuplicateValue(UserAccount.FIELD_USERNAME)),
            accounts.create.execute(admin.id, "MUELLER", Role.TEACHER, "anfangs-passwort").validationErrors(),
        )
    }

    @Test
    fun `list shows all accounts ordered by username`() {
        world.account("lehrer1", Role.TEACHER, nord.id)
        world.planner("Bauer", nord.id)

        assertEquals(listOf("admin", "Bauer", "lehrer1"), accounts.list.execute(admin.id).value().map { it.username })
    }

    @Test
    fun `role can be changed with version check`() {
        val mueller = world.planner("mueller", nord.id)

        val downgraded = accounts.updateRole.execute(admin.id, mueller.id, Role.TEACHER, mueller.version).value()
        val stale = accounts.updateRole.execute(admin.id, mueller.id, Role.PLANNER, mueller.version)

        assertEquals(Role.TEACHER, downgraded.role)
        assertEquals(Role.TEACHER, stored(mueller).role)
        assertEquals(UseCaseResult.Failure(ApplicationError.VersionConflict), stale)
    }

    @Test
    fun `deactivated account cannot log in and can be reactivated`() {
        val mueller = world.planner("mueller", nord.id)

        accounts.deactivate.execute(admin.id, mueller.id, mueller.version).value()
        assertFalse(canLogIn("mueller", MasterDataWorld.DEFAULT_PASSWORD))

        accounts.activate.execute(admin.id, mueller.id, stored(mueller).version).value()
        assertTrue(canLogIn("mueller", MasterDataWorld.DEFAULT_PASSWORD))
    }

    @Test
    fun `last active planner can neither be deactivated nor downgraded`() {
        val lastPlanner = listOf(ValidationError.NoActivePlannerLeft(nord.id))

        assertEquals(lastPlanner, accounts.deactivate.execute(admin.id, admin.id, admin.version).validationErrors())
        assertEquals(lastPlanner, accounts.updateRole.execute(admin.id, admin.id, Role.TEACHER, admin.version).validationErrors())
        assertTrue(stored(admin).isActivePlanner())
    }

    @Test
    fun `a planner may deactivate the last other planner but not then themselves`() {
        val mueller = world.planner("mueller", nord.id)

        accounts.deactivate.execute(mueller.id, admin.id, admin.version).value()

        assertEquals(
            listOf(ValidationError.NoActivePlannerLeft(nord.id)),
            accounts.deactivate.execute(mueller.id, mueller.id, mueller.version).validationErrors(),
        )
    }

    @Test
    fun `reset password replaces the old one`() {
        val lehrer = world.account("lehrer1", Role.TEACHER, nord.id)

        accounts.resetPassword.execute(admin.id, lehrer.id, "neues-passwort-1").value()

        assertTrue(canLogIn("lehrer1", "neues-passwort-1"))
        assertFalse(canLogIn("lehrer1", MasterDataWorld.DEFAULT_PASSWORD))
        assertEquals(
            listOf(ValidationError.OutOfRange(PasswordPolicy.FIELD_PASSWORD, 10, null)),
            accounts.resetPassword.execute(admin.id, lehrer.id, "kurz").validationErrors(),
        )
    }

    @Test
    fun `teacher account cannot manage accounts`() {
        val teacher = world.account("lehrer1", Role.TEACHER, nord.id)
        val denied = UseCaseResult.Failure(ApplicationError.AccessDenied)

        assertEquals(denied, accounts.list.execute(teacher.id))
        assertEquals(denied, accounts.get.execute(teacher.id, admin.id))
        assertEquals(denied, accounts.create.execute(teacher.id, "neu-konto", Role.PLANNER, "anfangs-passwort"))
        assertEquals(denied, accounts.updateRole.execute(teacher.id, teacher.id, Role.PLANNER, teacher.version))
        assertEquals(denied, accounts.deactivate.execute(teacher.id, admin.id, admin.version))
        assertEquals(denied, accounts.activate.execute(teacher.id, admin.id, admin.version))
        assertEquals(denied, accounts.resetPassword.execute(teacher.id, admin.id, "neues-passwort-1"))
    }

    @Test
    fun `unknown account is not found`() {
        assertEquals(UseCaseResult.Failure(ApplicationError.NotFound), accounts.get.execute(admin.id, AccountId.new()))
        assertEquals(UseCaseResult.Failure(ApplicationError.NotFound), accounts.resetPassword.execute(admin.id, AccountId.new(), "neues-passwort-1"))
    }

    @Test
    fun `own password change requires the current password and works for teachers`() {
        val teacher = world.account("lehrer1", Role.TEACHER, nord.id)

        assertEquals(
            listOf(ValidationError.IncorrectCurrentPassword),
            accounts.changeOwnPassword.execute(teacher.id, "falsches-passwort", "neues-passwort-1").validationErrors(),
        )
        assertEquals(
            listOf(ValidationError.OutOfRange(PasswordPolicy.FIELD_PASSWORD, 10, null)),
            accounts.changeOwnPassword.execute(teacher.id, MasterDataWorld.DEFAULT_PASSWORD, "kurz").validationErrors(),
        )

        accounts.changeOwnPassword.execute(teacher.id, MasterDataWorld.DEFAULT_PASSWORD, "neues-passwort-1").value()

        assertTrue(canLogIn("lehrer1", "neues-passwort-1"))
    }

    @Test
    fun `deactivated account cannot change its password`() {
        val mueller = world.planner("mueller", nord.id)
        accounts.deactivate.execute(admin.id, mueller.id, mueller.version).value()

        assertEquals(
            UseCaseResult.Failure(ApplicationError.AccessDenied),
            accounts.changeOwnPassword.execute(mueller.id, MasterDataWorld.DEFAULT_PASSWORD, "neues-passwort-1"),
        )
    }
}
