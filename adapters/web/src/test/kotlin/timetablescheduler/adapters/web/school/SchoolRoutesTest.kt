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
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SchoolRoutesTest {

    private val notRouted = setOf(HttpStatusCode.NotFound, HttpStatusCode.MethodNotAllowed)

    @Test
    fun `school overview redirects to the dashboard of the installation's school`() = testApplication {
        val system = TestSystem().installed()
        val client = startWith(system)
        client.login()

        val response = client.get("/schools")

        assertEquals(HttpStatusCode.SeeOther, response.status)
        assertEquals("/schools/${system.firstSchoolId().value}", response.headers[HttpHeaders.Location])
    }

    @Test
    fun `dashboard shows the school without create or delete forms`() = testApplication {
        val system = TestSystem().installed()
        val client = startWith(system)
        client.login()

        val page = client.get("/schools/${system.firstSchoolId().value}").bodyAsText()

        assertTrue("Gymnasium Nord" in page)
        assertFalse("/delete\"" in page)
        assertFalse("action=\"/schools\"" in page)
    }

    @Test
    fun `creating and deleting schools is not routed`() = testApplication {
        val system = TestSystem().installed()
        val client = startWith(system)
        client.login()

        val create = client.submitChecked("/schools", "name" to "Realschule Süd")
        val delete = client.submitChecked("/schools/${system.firstSchoolId().value}/delete", "version" to "0")

        assertTrue(create.status in notRouted, "create answered ${create.status}")
        assertTrue(delete.status in notRouted, "delete answered ${delete.status}")
        assertEquals(1, system.world.schools.count())
    }

    @Test
    fun `renaming shows the new name`() = testApplication {
        val system = TestSystem().installed()
        val school = system.firstSchool()
        val client = startWith(system)
        client.login()

        val response = client.submitChecked("/schools/${school.id.value}", "name" to "Gymnasium am Nordpark", "version" to "${school.version}")

        assertEquals(HttpStatusCode.SeeOther, response.status)
        assertTrue("Gymnasium am Nordpark" in client.get("/schools/${school.id.value}").bodyAsText())
    }

    @Test
    fun `unknown school id answers not found`() = testApplication {
        val client = startWith(TestSystem().installed())
        client.login()

        assertEquals(HttpStatusCode.NotFound, client.get("/schools/${UUID.randomUUID()}").status)
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
}
