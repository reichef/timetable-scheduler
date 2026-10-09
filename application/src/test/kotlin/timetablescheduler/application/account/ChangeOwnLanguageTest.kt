package timetablescheduler.application.account

import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.fakes.MasterDataWorld
import timetablescheduler.domain.account.InterfaceLanguage
import timetablescheduler.domain.account.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ChangeOwnLanguageTest {

    private val world = MasterDataWorld()
    private val nord = world.school("Gymnasium Nord")
    private val admin = world.planner("admin", nord.id)
    private val accounts get() = world.useCases.accounts

    private fun stored(username: String) = world.accounts.findByUsernameIgnoreCase(username)!!

    @Test
    fun `planner and teacher save their own language`() {
        val teacher = world.account("lehrer1", Role.TEACHER, nord.id)

        assertIs<UseCaseResult.Success<Unit>>(accounts.changeOwnLanguage.execute(admin.id, InterfaceLanguage.ENGLISH))
        assertIs<UseCaseResult.Success<Unit>>(accounts.changeOwnLanguage.execute(teacher.id, InterfaceLanguage.GERMAN))

        assertEquals(InterfaceLanguage.ENGLISH, stored("admin").preferredLanguage)
        assertEquals(InterfaceLanguage.GERMAN, stored("lehrer1").preferredLanguage)
    }

    @Test
    fun `saving the language does not change the account version`() {
        accounts.changeOwnLanguage.execute(admin.id, InterfaceLanguage.ENGLISH)

        assertEquals(admin.version, stored("admin").version)
        assertIs<UseCaseResult.Success<*>>(accounts.updateRole.execute(admin.id, admin.id, Role.PLANNER, admin.version))
    }

    @Test
    fun `deactivated account cannot change its language`() {
        val mueller = world.planner("mueller", nord.id)
        accounts.deactivate.execute(admin.id, mueller.id, mueller.version)

        assertEquals(UseCaseResult.Failure(ApplicationError.AccessDenied), accounts.changeOwnLanguage.execute(mueller.id, InterfaceLanguage.ENGLISH))
        assertNull(stored("mueller").preferredLanguage)
    }

    @Test
    fun `account changes by a planner keep the saved language`() {
        val lehrer = world.account("lehrer1", Role.TEACHER, nord.id)
        accounts.changeOwnLanguage.execute(lehrer.id, InterfaceLanguage.ENGLISH)

        accounts.updateRole.execute(admin.id, lehrer.id, Role.PLANNER, stored("lehrer1").version)
        accounts.deactivate.execute(admin.id, lehrer.id, stored("lehrer1").version)
        accounts.activate.execute(admin.id, lehrer.id, stored("lehrer1").version)
        accounts.resetPassword.execute(admin.id, lehrer.id, "neues-passwort-1")

        assertEquals(InterfaceLanguage.ENGLISH, stored("lehrer1").preferredLanguage)
    }
}
