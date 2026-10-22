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

/** Comparing, diffing and choosing alternatives (002 US3, FR-020, FR-021, FR-028). */
class CompareRoutesTest {
    @Test
    fun `the comparison shows the table and a chart of the selected objectives with one linked point per alternative`() =
        testApplication {
            val system = TestSystem().installed()
            val data = GenerationTestData(system)
            val (generation, paths) = data.generationWith(data.withoutGerman())
            val client = startWith(system)
            client.login()

            val page = client.get("${data.generationsPath}/${generation.id.value}/compare?x=classFill&y=otherSoft").bodyAsText()

            assertTrue("<svg" in page)
            paths.forEach { path -> assertTrue("""<a href="$path">""" in page, "point linking to $path") }
            assertTrue("Klassen gefüllt (waagerecht) gegen Soll-Bedingungen erfüllt (senkrecht)" in page)
            assertTrue("""<option value="otherSoft" selected""" in page)
            assertTrue("sort=classFill" in page)
        }

    @Test
    fun `the diff highlights the cells in which two alternatives differ`() =
        testApplication {
            val system = TestSystem().installed()
            val data = GenerationTestData(system)
            val (_, paths) = data.generationWith(data.germanOnTuesday())
            val client = startWith(system)
            client.login()
            val otherId = paths[1].substringAfterLast("/")

            val page = client.get("${paths[0]}/diff/$otherId").bodyAsText()

            assertTrue("Alternative 1 und 2 im Vergleich" in page)
            // Monday and Tuesday 2nd slot differ in both grids.
            assertEquals(4, Regex("""class="highlight"""").findAll(page).count())
        }

    @Test
    fun `an alternative with unplaced lessons needs a confirmation, then it is chosen`() =
        testApplication {
            val system = TestSystem().installed()
            val data = GenerationTestData(system)
            val (_, paths) = data.generationWith(data.withoutGerman())
            val client = startWith(system)
            client.login()

            val confirmation = client.submitChecked("${paths[1]}/choose", FIELD_CHOSEN_VERSION to "")
            assertEquals(HttpStatusCode.OK, confirmation.status)
            val page = confirmation.bodyAsText()
            assertTrue("Nicht verplante Stunden bestätigen" in page && "5a: Deutsch – 1 Stunde" in page)
            assertTrue("""href="${paths[1]}">Abbrechen""" in page)
            assertEquals(null, system.world.chosenTimetables.find(data.schoolId))

            val chosen = client.submitChecked("${paths[1]}/choose", FIELD_CHOSEN_VERSION to "", FIELD_UNPLACED_CONFIRMED to "true")

            assertEquals(HttpStatusCode.SeeOther, chosen.status)
            assertEquals("/schools/${data.schoolId.value}/timetable", chosen.headers[HttpHeaders.Location])
            assertEquals(
                1,
                system.world.chosenTimetables
                    .find(data.schoolId)!!
                    .unplaced.values
                    .sum(),
            )
        }

    @Test
    fun `a concurrent choice or a conflicting alternative answers 409`() =
        testApplication {
            val system = TestSystem().installed()
            val data = GenerationTestData(system)
            val (_, paths) = data.generationWith(data.germanOnTuesday())
            val client = startWith(system)
            client.login()
            assertEquals(HttpStatusCode.SeeOther, client.submitChecked("${paths[0]}/choose", FIELD_CHOSEN_VERSION to "").status)

            val stale = client.submitChecked("${paths[1]}/choose", FIELD_CHOSEN_VERSION to "")
            assertEquals(HttpStatusCode.Conflict, stale.status)

            system.world.markUnavailable(system.world.teachers.findById(data.schmidt.id)!!, Unavailability(MONDAY, data.first.id))
            val conflicting = client.submitChecked("${paths[1]}/choose", FIELD_CHOSEN_VERSION to "0")
            assertEquals(HttpStatusCode.Conflict, conflicting.status)
            val page = conflicting.bodyAsText()
            assertTrue(
                "Alternative passt nicht mehr" in page && "Lehrkraft nicht verfügbar" in page &&
                    "5a, Mathematik – Montag, 1. Stunde" in page,
            )
            assertEquals(paths[0].substringAfterLast("/"), "${system.world.chosenTimetables.find(data.schoolId)!!.alternativeId.value}")
        }

    @Test
    fun `a teacher account cannot compare, diff or choose (SC-008)`() =
        testApplication {
            val system = TestSystem().installed()
            val data = GenerationTestData(system)
            val (generation, paths) = data.generationWith(data.germanOnTuesday())
            system.addAccount("lehrer1", Role.TEACHER, setOf(data.schoolId))
            startWith(system)
            val teacher = newClient()
            teacher.login("lehrer1")

            val compare = teacher.get("${data.generationsPath}/${generation.id.value}/compare")
            val diff = teacher.get("${paths[0]}/diff/${paths[1].substringAfterLast("/")}")
            val choose = teacher.submitChecked("${paths[0]}/choose", FIELD_CHOSEN_VERSION to "")

            listOf(compare, diff, choose).forEach { response ->
                assertEquals(HttpStatusCode.Forbidden, response.status)
                assertTrue("Mathematik" !in response.bodyAsText())
            }
            assertEquals(null, system.world.chosenTimetables.find(data.schoolId))
        }
}
