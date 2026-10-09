package timetablescheduler.adapters.web.generation

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import timetablescheduler.adapters.web.TestSystem
import timetablescheduler.adapters.web.login
import timetablescheduler.adapters.web.startWith
import timetablescheduler.application.scheduling.StartRequest
import timetablescheduler.domain.scheduling.generation.Generation
import timetablescheduler.domain.scheduling.solution.TimetableCandidate
import timetablescheduler.application.error.UseCaseResult
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AlternativeRoutesTest {

    /** Runs a generation that finds [candidate] and returns the path of its only alternative. */
    private fun TestSystem.alternativePathFor(data: GenerationTestData, candidate: TimetableCandidate): String {
        world.solver.candidates = { listOf(candidate) }
        val generation = assertIs<UseCaseResult.Success<Generation>>(
            world.useCases.scheduling.start.execute(admin().id, data.schoolId, StartRequest(timeLimitMinutes = 1)),
        ).value
        val alternative = world.generations.findAlternatives(generation.id).single()
        return "${data.generationsPath}/${generation.id.value}/alternatives/${alternative.id.value}"
    }

    @Test
    fun `the class view shows the weekly grid with subject, short code, room and the break (FR-019)`() = testApplication {
        val system = TestSystem().installed()
        val data = GenerationTestData(system)
        val path = system.alternativePathFor(data, data.complete())
        val client = startWith(system)
        client.login()

        val page = client.get(path).bodyAsText()

        assertTrue("Montag" in page && "Dienstag" in page)
        assertTrue("Mathematik" in page && "SCHA" in page && "A-101" in page)
        assertTrue("Große Pause" in page)
        assertTrue("Klassen gefüllt" in page && "100,0 %" in page)
    }

    @Test
    fun `the teacher and room views show the class`() = testApplication {
        val system = TestSystem().installed()
        val data = GenerationTestData(system)
        val path = system.alternativePathFor(data, data.complete())
        val client = startWith(system)
        client.login()

        val teacherView = client.get("$path?view=teacher&id=${data.schmidt.id.value}").bodyAsText()
        val roomView = client.get("$path?view=room&id=${data.roomA.id.value}").bodyAsText()

        assertTrue("5a" in teacherView && "Anna Schmidt (SCHA)" in teacherView)
        assertTrue("5a" in roomView && "A-101" in roomView)
    }

    @Test
    fun `unplaced lessons are listed per class (FR-005)`() = testApplication {
        val system = TestSystem().installed()
        val data = GenerationTestData(system)
        val path = system.alternativePathFor(data, data.withoutGerman())
        val client = startWith(system)
        client.login()

        val page = client.get(path).bodyAsText()

        assertTrue("Nicht verplante Stunden" in page)
        assertTrue("5a: Deutsch – 1 Stunde" in page)
    }

    @Test
    fun `an unknown alternative answers 404`() = testApplication {
        val system = TestSystem().installed()
        val data = GenerationTestData(system)
        val path = system.alternativePathFor(data, data.complete())
        val client = startWith(system)
        client.login()

        assertEquals(HttpStatusCode.NotFound, client.get(path.substringBeforeLast("/") + "/${UUID.randomUUID()}").status)
    }
}
