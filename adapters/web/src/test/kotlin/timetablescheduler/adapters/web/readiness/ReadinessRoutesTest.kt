package timetablescheduler.adapters.web.readiness

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import timetablescheduler.adapters.web.TestSystem
import timetablescheduler.adapters.web.login
import timetablescheduler.adapters.web.startWith
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReadinessRoutesTest {
    private fun TestSystem.readinessPath() = "/schools/${firstSchoolId().value}/readiness"

    @Test
    fun `issues are grouped by kind and link to the affected entries`() =
        testApplication {
            val system = TestSystem().installed()
            val schoolId = system.firstSchoolId()

            val lab = system.world.feature(schoolId, "Chemieraum")
            val chemie = system.world.subject(schoolId, "Chemie", setOf(lab.id))
            val physik = system.world.subject(schoolId, "Physik")
            val level = system.world.classLevel(schoolId, "6", chemie to 2, physik to 2)
            system.world.schoolClass(level, "a")

            system.world.teacher(schoolId, "Anna Schmidt", "SCHA", weeklyLoad = 2, qualifiedSubjects = setOf(chemie.id))

            val client = startWith(system)
            client.login()

            val page = client.get(system.readinessPath()).bodyAsText()

            assertTrue("Fächer ohne passenden Raum" in page)
            assertTrue("Chemie" in page && "fehlende Ausstattung: Chemieraum" in page)
            assertTrue("Fächer ohne qualifizierte Lehrkraft" in page && "Physik" in page)
            assertTrue("/schools/${schoolId.value}/subjects/${physik.id.value}" in page)
            assertTrue("Klassenstufen über dem Zeitraster" in page)
        }

    @Test
    fun `consistent data shows that nothing blocks scheduling`() =
        testApplication {
            val system = TestSystem().installed()
            val client = startWith(system)
            client.login()

            val page = client.get(system.readinessPath()).bodyAsText()

            assertTrue("Keine blockierenden Probleme gefunden" in page)
        }

    @Test
    fun `dashboard summarises the readiness`() =
        testApplication {
            val system = TestSystem().installed()
            system.world.teacher(system.firstSchoolId(), "Anna Schmidt", "SCHA")
            val client = startWith(system)
            client.login()

            val dashboard = client.get("/schools/${system.firstSchoolId().value}").bodyAsText()

            assertTrue(system.readinessPath() in dashboard)
            assertTrue("2 Probleme" in dashboard)
        }

    @Test
    fun `unknown school answers not found`() =
        testApplication {
            val client = startWith(TestSystem().installed())
            client.login()

            assertEquals(HttpStatusCode.NotFound, client.get("/schools/${UUID.randomUUID()}/readiness").status)
        }
}
