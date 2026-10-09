package timetablescheduler.adapters.web.teacher

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import timetablescheduler.adapters.web.TestSystem
import timetablescheduler.adapters.web.login
import timetablescheduler.adapters.web.startWith
import timetablescheduler.adapters.web.submitChecked
import timetablescheduler.domain.teacher.Unavailability
import java.time.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TeacherRoutesTest {

    private fun TestSystem.teachersPath() = "/schools/${firstSchoolId().value}/teachers"

    private fun TestSystem.withGrid() = apply {
        world.teachingDays(firstSchoolId(), DayOfWeek.MONDAY, DayOfWeek.FRIDAY)
        world.slot(firstSchoolId(), "1. Stunde", "08:00", "08:45")
        world.slot(firstSchoolId(), "6. Stunde", "12:30", "13:15")
    }

    @Test
    fun `creating a teacher redirects to the teacher page and lists the teacher`() = testApplication {
        val system = TestSystem().installed().withGrid()
        val client = startWith(system)
        client.login()

        val response = client.submitChecked(system.teachersPath(), "name" to "Anna Schmidt", "shortCode" to "scha", "weeklyLoad" to "2")

        val anna = assertNotNull(system.world.teachers.findByShortCode("SCHA"))
        assertEquals(HttpStatusCode.SeeOther, response.status)
        assertEquals("${system.teachersPath()}/${anna.id.value}", response.headers[HttpHeaders.Location])
        val list = client.get(system.teachersPath()).bodyAsText()
        assertTrue("Anna Schmidt" in list && "SCHA" in list)
    }

    @Test
    fun `duplicate short code answers 422 with the entered values`() = testApplication {
        val system = TestSystem().installed().withGrid()
        system.world.teacher(system.firstSchoolId(), "Anna Schmidt", "SCHA")
        val client = startWith(system)
        client.login()

        val response = client.submitChecked(system.teachersPath(), "name" to "Anna Schmidt", "shortCode" to "SCHA", "weeklyLoad" to "2")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertTrue("Kürzel ist bereits vergeben." in body)
        assertTrue("value=\"Anna Schmidt\"" in body)
    }

    @Test
    fun `load above the grid answers 422`() = testApplication {
        val system = TestSystem().installed().withGrid()
        val client = startWith(system)
        client.login()

        val response = client.submitChecked(system.teachersPath(), "name" to "Anna Schmidt", "shortCode" to "SCHA", "weeklyLoad" to "5")

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertTrue("Deputat muss zwischen 0 und 4 liegen." in response.bodyAsText())
    }

    @Test
    fun `qualifications and unavailability grid are saved`() = testApplication {
        val system = TestSystem().installed().withGrid()
        val schoolId = system.firstSchoolId()
        val mathe = system.world.subject(schoolId, "Mathematik")
        val anna = system.world.teacher(schoolId, "Anna Schmidt", "SCHA")
        val sixth = system.world.schools.findTimeGrid(schoolId).slots.last()
        val path = "${system.teachersPath()}/${anna.id.value}"
        val client = startWith(system)
        client.login()

        val qualified = client.submitChecked("$path/qualifications", "subjectId" to "${mathe.id.value}", "version" to "0")
        val blocked = client.submitChecked("$path/unavailability", "slot" to "FRIDAY:${sixth.id.value}", "version" to "1")
        val page = client.get(path).bodyAsText()

        assertEquals(HttpStatusCode.SeeOther, qualified.status)
        assertEquals(HttpStatusCode.SeeOther, blocked.status)
        val stored = system.world.teachers.findById(anna.id)!!
        assertEquals(setOf(mathe.id), stored.qualifiedSubjectIds)
        assertEquals(setOf(Unavailability(DayOfWeek.FRIDAY, sixth.id)), stored.unavailability)
        assertTrue("value=\"FRIDAY:${sixth.id.value}\" checked" in page)
    }

    @Test
    fun `deleting a teacher redirects to the list`() = testApplication {
        val system = TestSystem().installed().withGrid()
        val anna = system.world.teacher(system.firstSchoolId(), "Anna Schmidt", "SCHA")
        val client = startWith(system)
        client.login()

        val response = client.submitChecked("${system.teachersPath()}/${anna.id.value}/delete", "version" to "0")

        assertEquals(HttpStatusCode.SeeOther, response.status)
        assertEquals(system.teachersPath(), response.headers[HttpHeaders.Location])
        assertNull(system.world.teachers.findById(anna.id))
    }

    @Test
    fun `slot deletion confirmation names the unavailabilities that will be removed`() = testApplication {
        val system = TestSystem().installed().withGrid()
        val schoolId = system.firstSchoolId()
        val sixth = system.world.schools.findTimeGrid(schoolId).slots.last()
        val anna = system.world.teacher(schoolId, "Anna Schmidt", "SCHA")
        system.world.markUnavailable(anna, Unavailability(DayOfWeek.FRIDAY, sixth.id))
        val client = startWith(system)
        client.login()

        val page = client.get("/schools/${schoolId.value}/time-grid/slots/${sixth.id.value}/delete").bodyAsText()

        assertTrue("1 Sperrzeiten von Lehrkräften" in page)
    }

    @Test
    fun `dashboard links to the teachers`() = testApplication {
        val system = TestSystem().installed()
        val client = startWith(system)
        client.login()

        assertTrue(system.teachersPath() in client.get("/schools/${system.firstSchoolId().value}").bodyAsText())
    }
}
