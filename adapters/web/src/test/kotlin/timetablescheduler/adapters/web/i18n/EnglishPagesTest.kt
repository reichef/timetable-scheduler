package timetablescheduler.adapters.web.i18n

import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import timetablescheduler.adapters.web.TestSystem
import timetablescheduler.adapters.web.login
import timetablescheduler.adapters.web.newClient
import timetablescheduler.adapters.web.startWith
import timetablescheduler.adapters.web.submitChecked
import timetablescheduler.domain.account.Role
import timetablescheduler.domain.teacher.Unavailability
import java.time.DayOfWeek
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** SC-009, FR-043 – FR-045: with English chosen, no page shows German interface text; user data stays unchanged. */
class EnglishPagesTest {

    private val germanInterfaceWords = listOf(
        "Speichern", "Löschen", "Anlegen", "Zurück", "Räume", "Fächer", "Lehrkräfte", "Klassenstufe", "Zeitraster",
        "Montag", "Planer", "Bezeichnung", "Wochenstunden", "Probleme", "Anmelden", "Abmelden", "Fehlerkennung",
    )

    private suspend fun HttpResponse.assertEnglish(page: String, expectedStatus: HttpStatusCode = HttpStatusCode.OK): String {
        val body = bodyAsText()
        assertEquals(expectedStatus, status, "status of $page")
        assertEquals("en", body.htmlLanguage(), "language of $page")
        val germanWords = germanInterfaceWords.filter { it in body }
        assertEquals(emptyList(), germanWords, "German interface text on $page")
        return body
    }

    @Test
    fun `all pages and messages are English when English is chosen`() = testApplication {
        val system = TestSystem().installed()
        application { routing { get("/explode") { error("Anna Schmidt") } } }
        val schoolId = system.firstSchoolId()
        val school = "/schools/${schoolId.value}"
        val world = system.world
        world.teachingDays(schoolId, DayOfWeek.MONDAY, DayOfWeek.FRIDAY)
        val slot = world.slot(schoolId, "1. Stunde", "08:00", "08:45")
        val lab = world.feature(schoolId, "Chemieraum")
        val room = world.room(schoolId, "B-104", lab.id)
        val mathe = world.subject(schoolId, "Mathematik")
        val level = world.classLevel(schoolId, "6", mathe to 4)
        val schoolClass = world.schoolClass(level, "a")
        val anna = world.teacher(schoolId, "Anna Schmidt", "SCHA", weeklyLoad = 1, qualifiedSubjects = setOf(mathe.id))
        world.markUnavailable(anna, Unavailability(DayOfWeek.FRIDAY, slot.id))
        val lehrer = system.addAccount("lehrer1", Role.TEACHER, setOf(schoolId))
        val client = startWith(system)
        client.login()
        client.switchLanguage("en")

        val pages = listOf(
            "/home", "/account/password", school, "$school/room-features", "$school/rooms", "$school/rooms/${room.id.value}",
            "$school/time-grid", "$school/time-grid/slots/${slot.id.value}", "$school/time-grid/slots/${slot.id.value}/delete",
            "$school/subjects", "$school/subjects/${mathe.id.value}", "$school/class-levels", "$school/class-levels/${level.id.value}",
            "$school/class-levels/${level.id.value}/classes/new", "$school/classes/${schoolClass.id.value}",
            "$school/teachers", "$school/teachers/${anna.id.value}", "/accounts", "/accounts/${lehrer.id.value}", "$school/readiness",
        )
        pages.forEach { page -> client.get(page).assertEnglish(page) }

        assertTrue("B-104" in client.get("$school/rooms").bodyAsText())
        assertTrue("Anna Schmidt" in client.get("$school/teachers").bodyAsText())
        client.get("/schools/${UUID.randomUUID()}").assertEnglish("404", HttpStatusCode.NotFound)
        client.submitChecked("$school/rooms", "identifier" to "b-104").assertEnglish("422", HttpStatusCode.UnprocessableEntity)
        client.submitChecked("$school/rooms/${room.id.value}", "identifier" to "B-105", "version" to "0")
        client.submitChecked("$school/rooms/${room.id.value}", "identifier" to "B-106", "version" to "0")
            .assertEnglish("409 conflict", HttpStatusCode.Conflict)
        val blocked = client.submitChecked("$school/subjects/${mathe.id.value}/delete", "version" to "0")
            .assertEnglish("409 deletion blocked", HttpStatusCode.Conflict)
        assertTrue("Anna Schmidt (SCHA)" in blocked)
        val technical = client.get("/explode").assertEnglish("500", HttpStatusCode.InternalServerError)
        assertTrue("Anna Schmidt" !in technical)

        val teacherClient = newClient()
        teacherClient.login("lehrer1")
        teacherClient.switchLanguage("en")
        teacherClient.get("/accounts").assertEnglish("403", HttpStatusCode.Forbidden)

        val visitor = newClient()
        val loginPage = visitor.switchLanguageLoggedOut("en").headers[HttpHeaders.Location]!!
        visitor.get(loginPage).assertEnglish("login")
    }
}
