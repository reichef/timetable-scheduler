package timetablescheduler.adapters.web.curriculum

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import timetablescheduler.adapters.web.TestSystem
import timetablescheduler.adapters.web.login
import timetablescheduler.adapters.web.startWith
import timetablescheduler.adapters.web.submitChecked
import java.time.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CurriculumRoutesTest {

    private fun TestSystem.schoolPath() = "/schools/${firstSchoolId().value}"

    @Test
    fun `subject with required feature is created and listed`() = testApplication {
        val system = TestSystem().installed()
        val lab = system.world.feature(system.firstSchoolId(), "Chemieraum")
        val client = startWith(system)
        client.login()

        val response = client.submitChecked("${system.schoolPath()}/subjects", "name" to "Chemie", "requiredFeatureId" to "${lab.id.value}")
        val page = client.get("${system.schoolPath()}/subjects").bodyAsText()

        assertEquals(HttpStatusCode.SeeOther, response.status)
        assertTrue("Chemie" in page && "Chemieraum" in page)
    }

    @Test
    fun `duplicate subject re-renders the form with the entered name`() = testApplication {
        val system = TestSystem().installed()
        system.world.subject(system.firstSchoolId(), "Mathematik")
        val client = startWith(system)
        client.login()

        val response = client.submitChecked("${system.schoolPath()}/subjects", "name" to "mathematik")

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertTrue("value=\"mathematik\"" in response.bodyAsText())
    }

    @Test
    fun `curriculum editor saves periods and shows the weekly total`() = testApplication {
        val system = TestSystem().installed()
        val schoolId = system.firstSchoolId()
        val mathe = system.world.subject(schoolId, "Mathematik")
        val deutsch = system.world.subject(schoolId, "Deutsch")
        system.world.subject(schoolId, "Chemie")
        val client = startWith(system)
        client.login()

        val created = client.submitChecked("${system.schoolPath()}/class-levels", "identifier" to "6")
        val level = assertNotNull(system.world.classLevels.findByIdentifierIgnoreCase(schoolId, "6"))
        val levelPath = "${system.schoolPath()}/class-levels/${level.id.value}"
        val saved = client.submitChecked(
            levelPath,
            "identifier" to "6", "version" to "${level.version}",
            "subjectId" to "${mathe.id.value}", "periodsPerWeek" to "4",
            "subjectId" to "${deutsch.id.value}", "periodsPerWeek" to "4",
        )

        assertEquals(HttpStatusCode.SeeOther, created.status)
        assertEquals(levelPath, created.headers[HttpHeaders.Location])
        assertEquals(HttpStatusCode.SeeOther, saved.status)
        assertEquals(8, system.world.classLevels.findById(level.id)!!.weeklyPeriods())
        assertTrue("8 Wochenstunden" in client.get(levelPath).bodyAsText())
    }

    @Test
    fun `over-full level shows the grid warning`() = testApplication {
        val system = TestSystem().installed()
        val schoolId = system.firstSchoolId()
        system.world.teachingDays(schoolId, DayOfWeek.MONDAY)
        system.world.slot(schoolId, "1. Stunde", "08:00", "08:45")
        val mathe = system.world.subject(schoolId, "Mathematik")
        val level = system.world.classLevel(schoolId, "6", mathe to 3)
        val client = startWith(system)
        client.login()

        val page = client.get("${system.schoolPath()}/class-levels/${level.id.value}").bodyAsText()

        assertTrue("passt nicht in das Zeitraster" in page)
    }

    @Test
    fun `new class form proposes the next suffix and the class shows the resolved curriculum`() = testApplication {
        val system = TestSystem().installed()
        val schoolId = system.firstSchoolId()
        val mathe = system.world.subject(schoolId, "Mathematik")
        val level = system.world.classLevel(schoolId, "6", mathe to 4)
        system.world.schoolClass(level, "a")
        val levelPath = "${system.schoolPath()}/class-levels/${level.id.value}"
        val client = startWith(system)
        client.login()

        val form = client.get("$levelPath/classes/new").bodyAsText()
        val created = client.submitChecked("$levelPath/classes", "suffix" to "b", "priorityWeight" to "10")
        val classPage = client.get(created.headers[HttpHeaders.Location]!!).bodyAsText()

        assertTrue("name=\"suffix\" value=\"b\"" in form)
        assertEquals(HttpStatusCode.SeeOther, created.status)
        assertTrue("6b" in classPage)
        assertTrue("Mathematik" in classPage && "4" in classPage)
        assertFalse("name=\"periodsPerWeek\"" in classPage)
    }

    @Test
    fun `duplicate class answers 422`() = testApplication {
        val system = TestSystem().installed()
        val level = system.world.classLevel(system.firstSchoolId(), "6")
        system.world.schoolClass(level, "a")
        val client = startWith(system)
        client.login()

        val response = client.submitChecked("${system.schoolPath()}/class-levels/${level.id.value}/classes", "suffix" to "a", "priorityWeight" to "10")

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertTrue("Klassenzusatz ist bereits vergeben." in response.bodyAsText())
    }

    @Test
    fun `deleting a subject in use lists the level`() = testApplication {
        val system = TestSystem().installed()
        val schoolId = system.firstSchoolId()
        val mathe = system.world.subject(schoolId, "Mathematik")
        system.world.classLevel(schoolId, "6", mathe to 4)
        val client = startWith(system)
        client.login()

        val response = client.submitChecked("${system.schoolPath()}/subjects/${mathe.id.value}/delete", "version" to "${mathe.version}")

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertTrue("Klassenstufe: 6" in response.bodyAsText())
    }

    @Test
    fun `dashboard links to subjects and class levels`() = testApplication {
        val system = TestSystem().installed()
        val client = startWith(system)
        client.login()

        val dashboard = client.get(system.schoolPath()).bodyAsText()

        assertTrue("${system.schoolPath()}/subjects" in dashboard)
        assertTrue("${system.schoolPath()}/class-levels" in dashboard)
    }

    @Test
    fun `the class form saves a home room and rejects a room already used by another class (002 FR-002a)`() = testApplication {
        val system = TestSystem().installed()
        val schoolId = system.firstSchoolId()
        val room = system.world.room(schoolId, "A-101")
        val level = system.world.classLevel(schoolId, "5")
        val classesPath = "${system.schoolPath()}/class-levels/${level.id.value}/classes"
        val client = startWith(system)
        client.login()

        assertTrue("A-101" in client.get("$classesPath/new").bodyAsText())
        val created = client.submitChecked(classesPath, "suffix" to "a", "priorityWeight" to "10", "homeRoomId" to "${room.id.value}")
        val duplicate = client.submitChecked(classesPath, "suffix" to "b", "priorityWeight" to "10", "homeRoomId" to "${room.id.value}")

        assertEquals(HttpStatusCode.SeeOther, created.status)
        assertEquals(room.id, system.world.schoolClasses.findBySchool(schoolId).single().homeRoomId)
        assertEquals(HttpStatusCode.UnprocessableEntity, duplicate.status)
        assertTrue("Der Raum ist bereits Klassenraum von 5a." in duplicate.bodyAsText())
    }
}
