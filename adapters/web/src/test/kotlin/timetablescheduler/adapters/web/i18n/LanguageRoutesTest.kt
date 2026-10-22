package timetablescheduler.adapters.web.i18n

import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parametersOf
import io.ktor.server.testing.testApplication
import timetablescheduler.adapters.web.TestSystem
import timetablescheduler.adapters.web.login
import timetablescheduler.adapters.web.newClient
import timetablescheduler.adapters.web.startWith
import timetablescheduler.domain.account.InterfaceLanguage
import timetablescheduler.domain.account.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LanguageRoutesTest {
    private fun TestSystem.language(username: String) = world.accounts.findByUsernameIgnoreCase(username)!!.preferredLanguage

    private fun TestSystem.withTeacher() = apply { addAccount("lehrer1", Role.TEACHER, setOf(firstSchoolId())) }

    @Test
    fun `logged-in switch saves the account language, sets the cookie and shows the page again`() =
        testApplication {
            val system = TestSystem().installed()
            val roomsPath = "/schools/${system.firstSchoolId().value}/rooms"
            val client = startWith(system)
            client.login()

            val response = client.switchLanguage("en", returnTo = roomsPath)

            assertEquals(HttpStatusCode.SeeOther, response.status)
            assertEquals(roomsPath, response.headers[HttpHeaders.Location])
            assertTrue(
                response.headers
                    .getAll(HttpHeaders.SetCookie)
                    .orEmpty()
                    .any { it.startsWith("TTS_LANG=en") },
            )
            assertEquals(InterfaceLanguage.ENGLISH, system.language("admin"))
            assertEquals("en", client.get(roomsPath).bodyAsText().htmlLanguage())
        }

    @Test
    fun `logged-in switch without CSRF token is rejected`() =
        testApplication {
            val system = TestSystem().installed()
            val client = startWith(system)
            client.login()

            val response = client.submitForm("/language", parametersOf("language" to listOf("en"), "returnTo" to listOf("/home")))

            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertNull(system.language("admin"))
        }

    @Test
    fun `external return address and unknown language are handled safely`() =
        testApplication {
            val system = TestSystem().installed()
            val client = startWith(system)
            client.login()

            val external = client.switchLanguage("en", returnTo = "https://example.org")
            val unknown = client.switchLanguage("fr", returnTo = "/home")

            assertEquals("/", external.headers[HttpHeaders.Location])
            assertEquals("/home", unknown.headers[HttpHeaders.Location])
            assertEquals(InterfaceLanguage.ENGLISH, system.language("admin"))
        }

    @Test
    fun `switch on the login page only sets the cookie and offers the choice to the login form`() =
        testApplication {
            val system = TestSystem().installed()

            val client = startWith(system)

            val response = client.switchLanguageLoggedOut("en")
            val loginWithChoice = client.get(response.headers[HttpHeaders.Location]!!).bodyAsText()
            val laterVisit = client.get("/login").bodyAsText()

            assertEquals("/login?chosen=1", response.headers[HttpHeaders.Location])
            assertEquals("en", loginWithChoice.htmlLanguage())
            assertTrue("""name="chosenLanguage" value="en"""" in loginWithChoice)

            assertEquals("en", laterVisit.htmlLanguage())
            assertFalse("chosenLanguage" in laterVisit)
            assertNull(system.language("admin"))
        }

    @Test
    fun `login page follows the browser language, falls back to German and the cookie wins`() =
        testApplication {
            val system = TestSystem().installed()
            val client = startWith(system)

            val english = client.get("/login") { header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.9") }.bodyAsText()
            val french = client.get("/login") { header(HttpHeaders.AcceptLanguage, "fr") }.bodyAsText()
            client.switchLanguageLoggedOut("de")
            val remembered = client.get("/login") { header(HttpHeaders.AcceptLanguage, "en") }.bodyAsText()

            assertEquals("en", english.htmlLanguage())
            assertEquals("de", french.htmlLanguage())
            assertEquals("de", remembered.htmlLanguage())
        }

    @Test
    fun `an active login page choice is saved only for an account without language`() =
        testApplication {
            val system = TestSystem().installed().withTeacher()
            system.world.accounts.updatePreferredLanguage(system.admin().id, InterfaceLanguage.GERMAN)
            val client = startWith(system)

            client.loginWithChosenLanguage("lehrer1", TestSystem.PASSWORD, "en")
            val adminClient = newClient()
            adminClient.loginWithChosenLanguage(TestSystem.ADMIN, TestSystem.PASSWORD, "en")

            assertEquals(InterfaceLanguage.ENGLISH, system.language("lehrer1"))
            assertEquals("en", client.get("/home").bodyAsText().htmlLanguage())
            assertEquals(InterfaceLanguage.GERMAN, system.language("admin"))
            assertEquals("de", adminClient.get("/home").bodyAsText().htmlLanguage())
        }

    @Test
    fun `language left in the browser by another user is never saved and not used after login`() =
        testApplication {
            val system = TestSystem().installed().withTeacher()
            val sharedBrowser = startWith(system)
            sharedBrowser.switchLanguageLoggedOut("en")

            sharedBrowser.login("lehrer1")
            val home = sharedBrowser.get("/home") { header(HttpHeaders.AcceptLanguage, "de") }.bodyAsText()

            assertNull(system.language("lehrer1"))
            assertEquals("de", home.htmlLanguage())
        }

    @Test
    fun `without any choice the browser language applies after login and nothing is saved`() =
        testApplication {
            val system = TestSystem().installed()
            val client = startWith(system)
            client.login()

            val page = client.get("/home") { header(HttpHeaders.AcceptLanguage, "en") }.bodyAsText()

            assertEquals("en", page.htmlLanguage())
            assertNull(system.language("admin"))
        }

    @Test
    fun `every page states its language and marks the current one in the switch`() =
        testApplication {
            val system = TestSystem().installed()
            val client = startWith(system)

            val login = client.get("/login").bodyAsText()
            client.login()
            val dashboard = client.get("/schools/${system.firstSchoolId().value}").bodyAsText()

            listOf(login, dashboard).forEach { page ->
                assertEquals("de", page.htmlLanguage())
                assertTrue("""class="language-switch"""" in page)
                assertTrue(Regex("""value="de"[^>]*aria-current="true"""").containsMatchIn(page), "German marked as current")
            }
        }

    @Test
    fun `switching changes no master data and not the account version`() =
        testApplication {
            val system = TestSystem().installed()
            val schoolId = system.firstSchoolId()

            val room = system.world.room(schoolId, "B-104")
            val subject = system.world.subject(schoolId, "Mathematik")
            val teacher = system.world.teacher(schoolId, "Anna Schmidt", "SCHA")
            val versionBefore = system.admin().version

            val client = startWith(system)
            client.login()

            client.switchLanguage("en")

            assertEquals(room, system.world.rooms.findById(room.id))
            assertEquals(subject, system.world.subjects.findById(subject.id))
            assertEquals(teacher, system.world.teachers.findById(teacher.id))
            assertEquals(versionBefore, system.admin().version)
        }
}
