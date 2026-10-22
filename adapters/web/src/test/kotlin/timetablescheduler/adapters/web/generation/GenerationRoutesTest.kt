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
import timetablescheduler.domain.scheduling.generation.Alternative
import timetablescheduler.domain.scheduling.generation.AlternativeId
import timetablescheduler.domain.scheduling.generation.Generation
import timetablescheduler.domain.scheduling.generation.GenerationStatus
import timetablescheduler.domain.scheduling.solution.ObjectiveValues
import timetablescheduler.domain.validation.ValidationResult
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GenerationRoutesTest {
    private fun TestSystem.latestGeneration(): Generation = world.generations.all().maxBy { it.startedAt }

    @Test
    fun `the list shows the start form with the default time limit`() =
        testApplication {
            val system = TestSystem().installed()
            val data = GenerationTestData(system)
            val client = startWith(system)
            client.login()

            val page = client.get(data.generationsPath).bodyAsText()

            assertTrue("Stundenplanläufe" in page)
            assertTrue("""name="timeLimitMinutes"""" in page && """value="10"""" in page)
            assertTrue("Noch keine Läufe." in page)
        }

    @Test
    fun `starting leads to the generation, whose result lists the alternatives`() =
        testApplication {
            val system = TestSystem().installed()
            val data = GenerationTestData(system)
            system.world.solver.candidates = { listOf(data.withoutGerman()) }
            val client = startWith(system)
            client.login()

            val started = client.submitChecked(data.generationsPath, "timeLimitMinutes" to "2")

            val generation = system.latestGeneration()
            assertEquals(HttpStatusCode.SeeOther, started.status)
            assertEquals("${data.generationsPath}/${generation.id.value}", started.headers[HttpHeaders.Location])
            val page = client.get("${data.generationsPath}/${generation.id.value}").bodyAsText()
            assertTrue("abgeschlossen" in page)
            assertTrue("Klassen gefüllt" in page && "66,7 %" in page)
            assertTrue("Alternative 1" in page)
            assertFalse("http-equiv=\"refresh\"" in page)
        }

    @Test
    fun `a time limit outside 1 to 60 minutes re-renders the form with 422`() =
        testApplication {
            val system = TestSystem().installed()
            val data = GenerationTestData(system)
            val client = startWith(system)
            client.login()

            val response = client.submitChecked(data.generationsPath, "timeLimitMinutes" to "90")

            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
            assertTrue("Zeitlimit muss zwischen 1 und 60 liegen." in response.bodyAsText())
        }

    @Test
    fun `blocking readiness issues refuse the start and name the entry (FR-010)`() =
        testApplication {
            val system = TestSystem().installed()
            val data = GenerationTestData(system)
            system.world.teachers.update(
                system.world.teachers
                    .findById(data.schmidt.id)!!
                    .copy(weeklyLoad = 9),
            )
            val client = startWith(system)
            client.login()

            val response = client.submitChecked(data.generationsPath, "timeLimitMinutes" to "10")

            assertEquals(HttpStatusCode.Conflict, response.status)
            val page = response.bodyAsText()
            assertTrue("Start nicht möglich" in page && "Anna Schmidt (SCHA)" in page)
            assertTrue("/schools/${data.schoolId.value}/readiness" in page)
        }

    @Test
    fun `warnings are listed on the start form and must be confirmed`() =
        testApplication {
            val system = TestSystem().installed()
            val data = GenerationTestData(system)
            system.world.teacher(data.schoolId, "Ohne Fach", "OHNE", weeklyLoad = 2)
            val client = startWith(system)
            client.login()

            assertTrue("Ohne Fach (OHNE)" in client.get(data.generationsPath).bodyAsText())
            val unconfirmed = client.submitChecked(data.generationsPath, "timeLimitMinutes" to "10")
            assertEquals(HttpStatusCode.UnprocessableEntity, unconfirmed.status)
            assertTrue("Bitte bestätigen Sie die Hinweise." in unconfirmed.bodyAsText())
            val confirmed = client.submitChecked(data.generationsPath, "timeLimitMinutes" to "10", "warningsConfirmed" to "true")
            assertEquals(HttpStatusCode.SeeOther, confirmed.status)
        }

    @Test
    fun `a running generation refreshes itself, can be stopped and blocks a second start (FR-011)`() =
        testApplication {
            val system = TestSystem().installed()
            val data = GenerationTestData(system)
            system.world.executor.deferred = true
            system.world.solver.candidates = { listOf(data.complete()) }
            val client = startWith(system)
            client.login()
            client.submitChecked(data.generationsPath, "timeLimitMinutes" to "10")
            val generationPath = "${data.generationsPath}/${system.latestGeneration().id.value}"

            val running = client.get(generationPath).bodyAsText()
            assertTrue("""<meta http-equiv="refresh" content="3">""" in running)
            assertTrue("läuft" in running && "$generationPath/stop" in running)

            val second = client.submitChecked(data.generationsPath, "timeLimitMinutes" to "10")
            assertEquals(HttpStatusCode.Conflict, second.status)
            assertTrue(generationPath in second.bodyAsText())

            val stop = client.submitChecked("$generationPath/stop")
            assertEquals(HttpStatusCode.SeeOther, stop.status)
            system.world.executor.runPending()
            assertTrue("vorzeitig gestoppt" in client.get(generationPath).bodyAsText())
        }

    @Test
    fun `the result table can be sorted by every objective`() =
        testApplication {
            val system = TestSystem().installed()
            val data = GenerationTestData(system)
            val generation =
                (
                    Generation.start(
                        data.schoolId,
                        system.admin().id,
                        Instant.parse("2026-10-09T08:00:00Z"),
                        10,
                        "f".repeat(64),
                    ) as ValidationResult.Valid
                ).value
                    .endAs(GenerationStatus.FINISHED, Instant.parse("2026-10-09T08:10:00Z"))
            system.world.generations.insert(generation)
            listOf(0.9 to 0.4, 0.6 to 0.8).forEach { (fill, load) ->
                system.world.generations.addAlternative(
                    Alternative(
                        AlternativeId.new(),
                        generation.id,
                        ObjectiveValues.rounded(fill, load),
                        data.complete().lessons,
                        emptyMap(),
                        emptyMap(),
                    ),
                )
            }
            val client = startWith(system)
            client.login()
            val path = "${data.generationsPath}/${generation.id.value}"

            val byLoad = client.get("$path?sort=teacherLoad&dir=desc").bodyAsText()
            val byFill = client.get("$path?sort=classFill&dir=desc").bodyAsText()

            assertTrue(byLoad.indexOf("80,0 %") < byLoad.indexOf("40,0 %"))
            assertTrue(byFill.indexOf("90,0 %") < byFill.indexOf("60,0 %"))
        }

    @Test
    fun `teachers get 403 and unknown schools or generations 404 (FR-025)`() =
        testApplication {
            val system = TestSystem().installed()
            val data = GenerationTestData(system)
            system.addAccount("lehrer1", Role.TEACHER, setOf(data.schoolId))
            val planner = startWith(system)
            planner.login()
            val teacher = newClient()
            teacher.login("lehrer1")

            assertEquals(HttpStatusCode.Forbidden, teacher.get(data.generationsPath).status)
            assertEquals(HttpStatusCode.NotFound, planner.get("/schools/${UUID.randomUUID()}/generations").status)
            assertEquals(HttpStatusCode.NotFound, planner.get("${data.generationsPath}/${UUID.randomUUID()}").status)
        }

    @Test
    fun `the start form offers the minimum share of placed lessons, which the result shows (FR-007a)`() =
        testApplication {
            val system = TestSystem().installed()
            val data = GenerationTestData(system)
            system.world.solver.candidates = { listOf(data.complete()) }
            val client = startWith(system)
            client.login()

            val form = client.get(data.generationsPath).bodyAsText()
            client.submitChecked(data.generationsPath, "timeLimitMinutes" to "2", "minimumPlacedShare" to "95")
            val result = client.get("${data.generationsPath}/${system.latestGeneration().id.value}").bodyAsText()

            assertTrue("Mindestanteil verplanter Stunden (%)" in form)
            assertTrue(Regex("""name="minimumPlacedShare"[^>]*value="90"""").containsMatchIn(form))
            assertEquals(95, system.latestGeneration().minimumPlacedShare)
            assertTrue("Mindestanteil verplanter Stunden: 95 %" in result)
        }

    @Test
    fun `a minimum share outside 50 to 100 percent re-renders the form with 422`() =
        testApplication {
            val system = TestSystem().installed()
            val data = GenerationTestData(system)
            val client = startWith(system)
            client.login()

            val response = client.submitChecked(data.generationsPath, "timeLimitMinutes" to "2", "minimumPlacedShare" to "49")

            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
            assertTrue("Mindestanteil verplanter Stunden muss zwischen 50 und 100 liegen." in response.bodyAsText())
        }

    @Test
    fun `a generation that ended without alternatives says that none was found yet (FR-009)`() =
        testApplication {
            val system = TestSystem().installed()
            val data = GenerationTestData(system)
            system.world.solver.candidates = { emptyList() }
            val client = startWith(system)
            client.login()

            client.submitChecked(data.generationsPath, "timeLimitMinutes" to "2")
            val result = client.get("${data.generationsPath}/${system.latestGeneration().id.value}").bodyAsText()

            assertTrue("Noch keine Alternative gefunden." in result)
        }
}
