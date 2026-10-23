package timetablescheduler.adapters.web.timetable

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import timetablescheduler.adapters.web.TestSystem
import timetablescheduler.adapters.web.generation.FIELD_CHOSEN_VERSION
import timetablescheduler.adapters.web.generation.FIELD_UNPLACED_CONFIRMED
import timetablescheduler.adapters.web.generation.GenerationTestData
import timetablescheduler.adapters.web.login
import timetablescheduler.adapters.web.newClient
import timetablescheduler.adapters.web.startWith
import timetablescheduler.adapters.web.submitChecked
import timetablescheduler.domain.account.Role
import timetablescheduler.domain.curriculum.CurriculumEntry
import timetablescheduler.domain.teacher.Unavailability
import timetablescheduler.domain.validation.ValidationResult
import java.time.DayOfWeek.TUESDAY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The school's chosen timetable (002 US3, FR-021, FR-023). */
class ChosenTimetableRoutesTest {
    private val GenerationTestData.timetablePath get() = "/schools/${schoolId.value}/timetable"

    @Test
    fun `without a choice the page says so`() =
        testApplication {
            val system = TestSystem().installed()
            val data = GenerationTestData(system)
            val client = startWith(system)
            client.login()

            val page = client.get(data.timetablePath).bodyAsText()

            assertTrue("Es wurde noch kein Stundenplan gewählt." in page)
            assertTrue("Gewählter Stundenplan" in client.get("/schools/${data.schoolId.value}").bodyAsText())
        }

    @Test
    fun `the chosen timetable shows its grid, who chose it and its unplaced lessons`() =
        testApplication {
            val system = TestSystem().installed()
            val data = GenerationTestData(system)
            val (_, paths) = data.generationWith(data.withoutGerman())

            val client = startWith(system)
            client.login()

            client.submitChecked("${paths[1]}/choose", FIELD_CHOSEN_VERSION to "", FIELD_UNPLACED_CONFIRMED to "true")

            val page = client.get(data.timetablePath).bodyAsText()

            assertTrue("Gewählt am" in page && "von admin" in page)
            assertTrue("Mathematik" in page && "SCHA" in page && "A-101" in page)
            assertTrue("Nicht verplant" in page && "5a: Deutsch – 1 Stunde" in page)
            assertTrue("Zum Lauf, aus dem er stammt" in page)
        }

    @Test
    fun `conflicts, dropped lessons and new classes are shown with a hint to start from the chosen timetable (FR-023)`() =
        testApplication {
            val system = TestSystem().installed()
            val data = GenerationTestData(system)
            val (_, paths) = data.generationWith()

            val client = startWith(system)
            client.login()

            client.submitChecked("${paths[0]}/choose", FIELD_CHOSEN_VERSION to "")
            val world = system.world

            assertTrue("passt nicht mehr" !in client.get(data.timetablePath).bodyAsText())

            world.markUnavailable(world.teachers.findById(data.schmidt.id)!!, Unavailability(TUESDAY, data.first.id))

            val level = world.classLevels.findById(data.level5.id)!!
            val fewerMath =
                level.update(
                    "5",
                    listOf(CurriculumEntry(data.math.id, 1), CurriculumEntry(data.german.id, 1)),
                    setOf(data.math.id, data.german.id),
                )
            world.classLevels.update((fewerMath as ValidationResult.Valid).value)
            world.schoolClass(world.classLevels.findById(data.level5.id)!!, "b")
            val page = client.get(data.timetablePath).bodyAsText()

            assertTrue("passt nicht mehr zu den aktuellen Daten" in page)
            assertTrue("""href="/schools/${data.schoolId.value}/generations?startFromChosen=true"""" in page)
            assertTrue("""class="highlight"""" in page)

            assertTrue("Lehrkraft nicht verfügbar" in page && "5a, Mathematik – Dienstag, 1. Stunde" in page)
            assertTrue("entfällt" in page)
            assertTrue("5b: Mathematik – 1 Stunde" in page && "5b: Deutsch – 1 Stunde" in page)

            assertEquals(
                3,
                world.chosenTimetables
                    .find(data.schoolId)!!
                    .lessons.size,
            )

            val startForm = client.get("/schools/${data.schoolId.value}/generations?startFromChosen=true").bodyAsText()

            assertTrue(Regex("""name="startFromChosen"[^>]*checked""").containsMatchIn(startForm))
        }

    @Test
    fun `a teacher used in the chosen timetable cannot be deleted`() =
        testApplication {
            val system = TestSystem().installed()
            val data = GenerationTestData(system)
            val (_, paths) = data.generationWith()

            val client = startWith(system)
            client.login()

            client.submitChecked("${paths[0]}/choose", FIELD_CHOSEN_VERSION to "")

            val response =
                client.submitChecked(
                    "/schools/${data.schoolId.value}/teachers/${data.schmidt.id.value}/delete",
                    "version" to "${system.world.teachers.findById(data.schmidt.id)!!.version}",
                )

            assertEquals(HttpStatusCode.Conflict, response.status)
            val body = response.bodyAsText()

            assertTrue("Gewählter Stundenplan" in body)
        }

    @Test
    fun `a teacher account gets 403 without any timetable data (SC-008)`() =
        testApplication {
            val system = TestSystem().installed()
            val data = GenerationTestData(system)
            val (_, paths) = data.generationWith()
            system.addAccount("lehrer1", Role.TEACHER, setOf(data.schoolId))

            val planner = startWith(system)
            planner.login()
            planner.submitChecked("${paths[0]}/choose", FIELD_CHOSEN_VERSION to "")

            val teacher = newClient()
            teacher.login("lehrer1")

            val response = teacher.get(data.timetablePath)

            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertTrue("Mathematik" !in response.bodyAsText())
        }
}
