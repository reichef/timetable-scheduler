package timetablescheduler.adapters.web.auth

import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parametersOf
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import timetablescheduler.adapters.web.TestSystem
import timetablescheduler.adapters.web.login
import timetablescheduler.adapters.web.startWith
import timetablescheduler.adapters.web.submitChecked
import timetablescheduler.domain.account.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthRoutesTest {

    @Test
    fun `unauthenticated access redirects to login`() = testApplication {
        val client = startWith(TestSystem().installed())

        val response = client.get("/schools")

        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/login", response.headers[HttpHeaders.Location])
    }

    @Test
    fun `login page is reachable without session`() = testApplication {
        val client = startWith(TestSystem().installed())

        val response = client.get("/login")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue("Anmelden" in response.bodyAsText())
    }

    @Test
    fun `successful planner login redirects to the school`() = testApplication {
        val client = startWith(TestSystem().installed())

        val response = client.login()

        assertEquals(HttpStatusCode.SeeOther, response.status)
        assertEquals("/schools", response.headers[HttpHeaders.Location])
    }

    @Test
    fun `successful teacher login redirects to teacher start page without master data`() = testApplication {
        val system = TestSystem().installed()
        system.addAccount("lehrer1", Role.TEACHER, setOf(system.firstSchoolId()))
        val client = startWith(system)

        val response = client.login("lehrer1")
        val home = client.get("/home")

        assertEquals("/home", response.headers[HttpHeaders.Location])
        assertEquals(HttpStatusCode.OK, home.status)
        assertTrue("Für Lehrkräfte ist noch keine Ansicht verfügbar" in home.bodyAsText())
        assertFalse("Gymnasium Nord" in home.bodyAsText())
    }

    @Test
    fun `wrong password and unknown user get the same generic message`() = testApplication {
        val client = startWith(TestSystem().installed())

        val wrongPassword = client.login(password = "falsches-passwort")
        val unknownUser = client.login(username = "niemand")

        assertEquals(HttpStatusCode.Unauthorized, wrongPassword.status)
        assertEquals(HttpStatusCode.Unauthorized, unknownUser.status)
        val message = "Benutzername oder Passwort ist falsch."
        assertTrue(message in wrongPassword.bodyAsText())
        assertTrue(message in unknownUser.bodyAsText())
    }

    @Test
    fun `logout ends the session`() = testApplication {
        val client = startWith(TestSystem().installed())
        client.login()

        val logout = client.submitChecked("/logout")
        val afterLogout = client.get("/home")

        assertEquals(HttpStatusCode.SeeOther, logout.status)
        assertEquals(HttpStatusCode.Found, afterLogout.status)
        assertEquals("/login", afterLogout.headers[HttpHeaders.Location])
    }

    @Test
    fun `form post without valid CSRF token is rejected`() = testApplication {
        val client = startWith(TestSystem().installed())
        client.login()

        val response = client.submitForm("/logout", parametersOf("_csrf" to listOf("gefaelscht")))

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `unexpected error shows only a generic page with correlation id`() = testApplication {
        val system = TestSystem().installed()
        application {
            routing { get("/explode") { error("Anna Schmidt secret detail") } }
        }
        val client = startWith(system)

        val response = client.get("/explode")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertTrue("Ein unerwarteter Fehler ist aufgetreten" in body)
        assertTrue("Fehlerkennung" in body)
        assertFalse("Anna Schmidt" in body)
    }
}
