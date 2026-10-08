package timetablescheduler.adapters.web.account

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import timetablescheduler.adapters.web.TestSystem
import timetablescheduler.adapters.web.login
import timetablescheduler.adapters.web.newClient
import timetablescheduler.adapters.web.startWith
import timetablescheduler.adapters.web.submitChecked
import timetablescheduler.domain.account.Role
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AccountRoutesTest {

    private fun TestSystem.withTeacher() = apply { addAccount("lehrer1", Role.TEACHER, setOf(firstSchoolId())) }

    @Test
    fun `teacher gets 403 on account management without any account data`() = testApplication {
        val client = startWith(TestSystem().installed().withTeacher())
        client.login("lehrer1")

        val response = client.get("/accounts")

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertFalse("admin" in response.bodyAsText())
    }

    @Test
    fun `list shows username, role and active state`() = testApplication {
        val client = startWith(TestSystem().installed().withTeacher())
        client.login()

        val page = client.get("/accounts").bodyAsText()

        assertTrue("admin" in page && "lehrer1" in page)
        assertTrue("Planer" in page && "Lehrkraft" in page)
        assertTrue("aktiv" in page)
    }

    @Test
    fun `creating an account redirects and lists it`() = testApplication {
        val system = TestSystem().installed()
        val client = startWith(system)
        client.login()

        val response = client.submitChecked("/accounts", "username" to "mueller", "role" to "PLANNER", "initialPassword" to "anfangs-passwort")

        assertEquals(HttpStatusCode.SeeOther, response.status)
        assertEquals("/accounts", response.headers[HttpHeaders.Location])
        assertTrue("mueller" in client.get("/accounts").bodyAsText())
        val mueller = assertNotNull(system.world.accounts.findByUsernameIgnoreCase("mueller"))
        assertEquals(setOf(system.firstSchoolId()), mueller.schoolIds)
    }

    @Test
    fun `invalid account input re-renders the form with the entered username`() = testApplication {
        val client = startWith(TestSystem().installed())
        client.login()

        val response = client.submitChecked("/accounts", "username" to "ADMIN", "role" to "TEACHER", "initialPassword" to "kurz")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertTrue("Benutzername ist bereits vergeben." in body)
        assertTrue("Passwort muss mindestens 10 sein." in body)
        assertTrue("value=\"ADMIN\"" in body)
        assertFalse("kurz" in body)
    }

    @Test
    fun `deactivating the last active planner answers 422 with a business message`() = testApplication {
        val system = TestSystem().installed()
        val admin = system.admin()
        val client = startWith(system)
        client.login()

        val response = client.submitChecked("/accounts/${admin.id.value}/deactivate", "version" to "${admin.version}")

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertTrue("aktiver Planer" in response.bodyAsText())
    }

    @Test
    fun `role change, deactivation and password reset work on the edit page`() = testApplication {
        val system = TestSystem().installed().withTeacher()
        val lehrer = system.world.accounts.findByUsernameIgnoreCase("lehrer1")!!
        val path = "/accounts/${lehrer.id.value}"
        val client = startWith(system)
        client.login()

        assertTrue("lehrer1" in client.get(path).bodyAsText())
        assertEquals(HttpStatusCode.SeeOther, client.submitChecked(path, "role" to "PLANNER", "version" to "0").status)
        assertEquals(HttpStatusCode.SeeOther, client.submitChecked("$path/deactivate", "version" to "1").status)
        assertEquals(HttpStatusCode.SeeOther, client.submitChecked("$path/activate", "version" to "2").status)
        assertEquals(HttpStatusCode.SeeOther, client.submitChecked("$path/password", "newPassword" to "neues-passwort-1").status)

        val stored = system.world.accounts.findById(lehrer.id)!!
        assertEquals(Role.PLANNER, stored.role)
        assertTrue(stored.active)
        assertEquals(HttpStatusCode.SeeOther, newClient().login("lehrer1", "neues-passwort-1").status)
    }

    @Test
    fun `unknown account answers not found`() = testApplication {
        val client = startWith(TestSystem().installed())
        client.login()

        assertEquals(HttpStatusCode.NotFound, client.get("/accounts/${UUID.randomUUID()}").status)
    }

    @Test
    fun `teacher can change the own password`() = testApplication {
        val system = TestSystem().installed().withTeacher()
        val client = startWith(system)
        client.login("lehrer1")

        assertEquals(HttpStatusCode.OK, client.get("/account/password").status)
        val wrong = client.submitChecked("/account/password", "currentPassword" to "falsch-falsch", "newPassword" to "neues-passwort-1")
        val changed = client.submitChecked("/account/password", "currentPassword" to TestSystem.PASSWORD, "newPassword" to "neues-passwort-1")

        assertEquals(HttpStatusCode.UnprocessableEntity, wrong.status)
        assertTrue("aktuelle Passwort ist falsch" in wrong.bodyAsText())
        assertEquals(HttpStatusCode.SeeOther, changed.status)
        assertTrue("Passwort wurde geändert" in client.get(changed.headers[HttpHeaders.Location]!!).bodyAsText())
    }
}
