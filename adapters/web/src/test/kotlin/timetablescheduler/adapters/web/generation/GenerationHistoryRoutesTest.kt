package timetablescheduler.adapters.web.generation

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
import timetablescheduler.domain.teacher.Unavailability
import java.time.DayOfWeek.MONDAY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Outdated generations and deleting them (002 US4, FR-023, FR-024, G8). */
class GenerationHistoryRoutesTest {

    @Test
    fun `the list marks outdated generations and the outdated alternative highlights the conflicting lesson`() = testApplication {
        val system = TestSystem().installed()
        val data = GenerationTestData(system)
        val (generation, paths) = data.generationWith()
        val client = startWith(system)
        client.login()
        assertTrue("veraltet" !in client.get(data.generationsPath).bodyAsText())

        system.world.markUnavailable(system.world.teachers.findById(data.schmidt.id)!!, Unavailability(MONDAY, data.first.id))

        val list = client.get(data.generationsPath).bodyAsText()
        assertTrue("veraltet" in list && "admin" in list)
        assertTrue("seit dem Start geändert" in client.get("${data.generationsPath}/${generation.id.value}").bodyAsText())
        val alternative = client.get(paths[0]).bodyAsText()
        assertTrue("seit dem Start geändert" in alternative)
        assertEquals(1, Regex("""class="highlight"""").findAll(alternative).count())
        assertTrue("Lehrkraft nicht verfügbar: 5a, Mathematik – Montag, 1. Stunde" in alternative)
    }

    @Test
    fun `deleting asks for confirmation, then removes the generation`() = testApplication {
        val system = TestSystem().installed()
        val data = GenerationTestData(system)
        val (generation, _) = data.generationWith()
        val client = startWith(system)
        client.login()
        val path = "${data.generationsPath}/${generation.id.value}"
        assertTrue("""href="$path/delete"""" in client.get(path).bodyAsText())

        val confirmation = client.get("$path/delete").bodyAsText()
        assertTrue("Lauf löschen?" in confirmation && """action="$path/delete"""" in confirmation)

        val response = client.submitChecked("$path/delete")
        assertEquals(HttpStatusCode.SeeOther, response.status)
        assertEquals(data.generationsPath, response.headers[HttpHeaders.Location])
        assertEquals(null, system.world.generations.findById(generation.id))
    }

    @Test
    fun `the generation of the chosen timetable cannot be deleted (FR-024)`() = testApplication {
        val system = TestSystem().installed()
        val data = GenerationTestData(system)
        val (generation, paths) = data.generationWith()
        val client = startWith(system)
        client.login()
        client.submitChecked("${paths[0]}/choose", FIELD_CHOSEN_VERSION to "")

        val response = client.submitChecked("${data.generationsPath}/${generation.id.value}/delete")

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertTrue("Gewählter Stundenplan" in response.bodyAsText())
    }

    @Test
    fun `a teacher account cannot delete generations`() = testApplication {
        val system = TestSystem().installed()
        val data = GenerationTestData(system)
        val (generation, _) = data.generationWith()
        system.addAccount("lehrer1", Role.TEACHER, setOf(data.schoolId))
        startWith(system)
        val teacher = newClient()
        teacher.login("lehrer1")

        assertEquals(HttpStatusCode.Forbidden, teacher.get("${data.generationsPath}/${generation.id.value}/delete").status)
        assertEquals(HttpStatusCode.Forbidden, teacher.submitChecked("${data.generationsPath}/${generation.id.value}/delete").status)
        assertTrue(system.world.generations.findById(generation.id) != null)
    }
}
