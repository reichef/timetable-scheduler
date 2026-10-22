package timetablescheduler.adapters.web

import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import timetablescheduler.adapters.web.generation.FIELD_CHOSEN_VERSION
import timetablescheduler.adapters.web.generation.GenerationTestData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** FR-027, quickstart 15: the later of two saves based on the same version must not overwrite the earlier one. */
class ConcurrentEditTest {
    @Test
    fun `second save of the same room version gets the conflict page with a reload link`() =
        testApplication {
            val system = TestSystem().installed()
            val room = system.world.room(system.firstSchoolId(), "A-101")
            val roomPath = "/schools/${system.firstSchoolId().value}/rooms/${room.id.value}"

            val firstTab = startWith(system)
            val secondTab = newClient()
            firstTab.login()
            secondTab.login()

            val first = firstTab.submitChecked(roomPath, "identifier" to "A-102", "version" to "${room.version}")
            val second = secondTab.submitChecked(roomPath, "identifier" to "A-103", "version" to "${room.version}")
            val conflictPage = second.bodyAsText()

            assertEquals(HttpStatusCode.SeeOther, first.status)
            assertEquals(HttpStatusCode.Conflict, second.status)
            assertTrue("inzwischen von jemand anderem geändert" in conflictPage)
            assertTrue("href=\"$roomPath\"" in conflictPage && "Neu laden" in conflictPage)
            assertEquals(
                "A-102",
                system.world.rooms
                    .findById(room.id)!!
                    .identifier,
            )
        }

    @Test
    fun `the later of two edits of the same constraint version gets the conflict page (002 FR-028)`() =
        testApplication {
            val system = TestSystem().installed()
            val school = "/schools/${system.firstSchoolId().value}"
            val constraint =
                system.world.constraints
                    .findBySchool(system.firstSchoolId())
                    .first()
            val path = "$school/constraints/${constraint.id.value}"

            val firstTab = startWith(system)
            val secondTab = newClient()
            firstTab.login()
            secondTab.login()

            val first = firstTab.submitChecked("$path/active", "active" to "false", "version" to "${constraint.version}")
            val second = secondTab.submitChecked("$path/active", "active" to "true", "version" to "${constraint.version}")

            assertEquals(HttpStatusCode.SeeOther, first.status)
            assertEquals(HttpStatusCode.Conflict, second.status)
            assertEquals(
                false,
                system.world.constraints
                    .findById(constraint.id)!!
                    .active,
            )
        }

    @Test
    fun `the later of two choices based on the same chosen timetable gets the conflict page (002 FR-028)`() =
        testApplication {
            val system = TestSystem().installed()
            val data = GenerationTestData(system)
            val (_, paths) = data.generationWith(data.germanOnTuesday())

            val firstTab = startWith(system)
            val secondTab = newClient()
            firstTab.login()
            secondTab.login()

            val first = firstTab.submitChecked("${paths[0]}/choose", FIELD_CHOSEN_VERSION to "")
            val second = secondTab.submitChecked("${paths[1]}/choose", FIELD_CHOSEN_VERSION to "")

            assertEquals(HttpStatusCode.SeeOther, first.status)
            assertEquals(HttpStatusCode.Conflict, second.status)
            assertTrue("inzwischen von jemand anderem geändert" in second.bodyAsText())
            assertEquals(paths[0].substringAfterLast("/"), "${system.world.chosenTimetables.find(data.schoolId)!!.alternativeId.value}")
        }
}
