package timetablescheduler.adapters.web.school

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import timetablescheduler.adapters.web.TestSystem
import timetablescheduler.adapters.web.login
import timetablescheduler.adapters.web.startWith
import timetablescheduler.adapters.web.submitChecked
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SchoolRoutesTest {

    @Test
    fun `initial planner sees exactly the first school`() = testApplication {
        val client = startWith(TestSystem().installed())
        client.login()

        val page = client.get("/schools").bodyAsText()

        assertTrue("Gymnasium Nord" in page)
    }

    @Test
    fun `creating a school redirects to its dashboard and lists it`() = testApplication {
        val system = TestSystem().installed()
        val client = startWith(system)
        client.login()

        val response = client.submitChecked("/schools", "name" to "Realschule Süd")

        val created = assertNotNull(system.world.schools.findByNameIgnoreCase("Realschule Süd"))
        assertEquals(HttpStatusCode.SeeOther, response.status)
        assertEquals("/schools/${created.id.value}", response.headers[HttpHeaders.Location])
        assertTrue("Realschule Süd" in client.get("/schools").bodyAsText())
    }

    @Test
    fun `duplicate school name re-renders the form with the entered value`() = testApplication {
        val client = startWith(TestSystem().installed())
        client.login()

        val response = client.submitChecked("/schools", "name" to "gymnasium nord")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertTrue("Name ist bereits vergeben." in body)
        assertTrue("value=\"gymnasium nord\"" in body)
    }

    @Test
    fun `school of another planner answers not found`() = testApplication {
        val system = TestSystem().installed()
        val foreign = system.world.school("Fremde Schule")
        val client = startWith(system)
        client.login()

        val response = client.get("/schools/${foreign.id.value}")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertFalse("Fremde Schule" in response.bodyAsText())
    }

    @Test
    fun `invalid school id answers not found`() = testApplication {
        val client = startWith(TestSystem().installed())
        client.login()

        assertEquals(HttpStatusCode.NotFound, client.get("/schools/kein-uuid").status)
    }

    @Test
    fun `renaming with a stale version answers conflict`() = testApplication {
        val system = TestSystem().installed()
        val school = system.firstSchool()
        val client = startWith(system)
        client.login()

        val first = client.submitChecked("/schools/${school.id.value}", "name" to "Neu", "version" to "${school.version}")
        val second = client.submitChecked("/schools/${school.id.value}", "name" to "Neuer", "version" to "${school.version}")

        assertEquals(HttpStatusCode.SeeOther, first.status)
        assertEquals(HttpStatusCode.Conflict, second.status)
        assertTrue("inzwischen von jemand anderem geändert" in second.bodyAsText())
    }

    @Test
    fun `deleting a school in use lists the blocking entries`() = testApplication {
        val system = TestSystem().installed()
        val school = system.firstSchool()
        system.world.room(school.id, "A-101")
        val client = startWith(system)
        client.login()

        val response = client.submitChecked("/schools/${school.id.value}/delete", "version" to "${school.version}")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertTrue("Raum: A-101" in body)
        assertTrue("Benutzerkonto: admin" in body)
    }
}
