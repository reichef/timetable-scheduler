package timetablescheduler.adapters.web.timegrid

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import timetablescheduler.adapters.web.TestSystem
import timetablescheduler.adapters.web.login
import timetablescheduler.adapters.web.startWith
import timetablescheduler.adapters.web.submitChecked
import timetablescheduler.domain.school.SlotKind
import java.time.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimeGridRoutesTest {
    private fun TestSystem.version() = world.schools.findById(firstSchoolId())!!.version

    @Test
    fun `defines teaching days and slots and shows them chronologically with breaks marked`() =
        testApplication {
            val system = TestSystem().installed()
            val gridPath = "/schools/${system.firstSchoolId().value}/time-grid"
            val client = startWith(system)
            client.login()

            client.submitChecked("$gridPath/days", "dayOfWeek" to "MONDAY", "dayOfWeek" to "FRIDAY", "version" to "${system.version()}")
            client.submitChecked(
                "$gridPath/slots",
                "label" to "2. Stunde",
                "startTime" to "08:50",
                "endTime" to "09:35",
                "kind" to "TEACHING",
                "version" to "${system.version()}",
            )
            client.submitChecked(
                "$gridPath/slots",
                "label" to "1. Stunde",
                "startTime" to "08:00",
                "endTime" to "08:45",
                "kind" to "TEACHING",
                "version" to "${system.version()}",
            )
            client.submitChecked(
                "$gridPath/slots",
                "label" to "Große Pause",
                "startTime" to "09:35",
                "endTime" to "09:55",
                "kind" to "BREAK",
                "version" to "${system.version()}",
            )
            val page = client.get(gridPath).bodyAsText()

            assertTrue(page.indexOf("1. Stunde") < page.indexOf("2. Stunde"))
            assertTrue(page.indexOf("2. Stunde") < page.indexOf("Große Pause"))
            assertTrue("class=\"break\"" in page)
            assertEquals(
                4,
                system.world.schools
                    .findTimeGrid(system.firstSchoolId())
                    .teachingSlotsPerWeek(),
            )
        }

    @Test
    fun `overlapping slot is rejected with a business message`() =
        testApplication {
            val system = TestSystem().installed()
            system.world.slot(system.firstSchoolId(), "1. Stunde", "08:00", "08:45")
            val client = startWith(system)
            client.login()

            val response =
                client.submitChecked(
                    "/schools/${system.firstSchoolId().value}/time-grid/slots",
                    "label" to "Extra",
                    "startTime" to "08:30",
                    "endTime" to "09:15",
                    "kind" to "TEACHING",
                    "version" to "${system.version()}",
                )

            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
            assertTrue("überschneidet sich" in response.bodyAsText())
        }

    @Test
    fun `delete confirmation shows the number of exclusions before deleting`() =
        testApplication {
            val system = TestSystem().installed()
            val schoolId = system.firstSchoolId()
            system.world.teachingDays(schoolId, DayOfWeek.MONDAY, DayOfWeek.FRIDAY)
            val slot = system.world.slot(schoolId, "8. Stunde", "14:00", "14:45", SlotKind.TEACHING)
            val slotPath = "/schools/${schoolId.value}/time-grid/slots/${slot.id.value}"

            val client = startWith(system)
            client.login()

            client.submitChecked("$slotPath/exclusions", "dayOfWeek" to "FRIDAY", "version" to "${system.version()}")

            val confirmation = client.get("$slotPath/delete").bodyAsText()
            val delete = client.submitChecked("$slotPath/delete", "version" to "${system.version()}")

            assertTrue("1 Tagesausnahme" in confirmation)
            assertTrue("0 Sperrzeiten" in confirmation)
            assertEquals(HttpStatusCode.SeeOther, delete.status)
            assertTrue(
                system.world.schools
                    .findTimeGrid(schoolId)
                    .slots
                    .isEmpty(),
            )
        }

    @Test
    fun `invalid time input is reported`() =
        testApplication {
            val system = TestSystem().installed()
            val client = startWith(system)
            client.login()

            val response =
                client.submitChecked(
                    "/schools/${system.firstSchoolId().value}/time-grid/slots",
                    "label" to "Extra",
                    "startTime" to "acht",
                    "endTime" to "09:15",
                    "kind" to "TEACHING",
                    "version" to "${system.version()}",
                )

            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
            assertTrue("Beginn hat ein ungültiges Format." in response.bodyAsText())
        }

    @Test
    fun `a time slot is changed, a time that is no time shows the editor again`() =
        testApplication {
            val system = TestSystem().installed()
            val schoolId = system.firstSchoolId()
            val slot = system.world.slot(schoolId, "1. Stunde", "08:00", "08:45")
            val slotPath = "/schools/${schoolId.value}/time-grid/slots/${slot.id.value}"
            val version = "${system.world.schools.findById(schoolId)!!.version}"

            val client = startWith(system)
            client.login()

            val noTime =
                client.submitChecked(
                    slotPath,
                    "label" to "1. Stunde",
                    "startTime" to "acht",
                    "endTime" to "08:45",
                    "kind" to "TEACHING",
                    "version" to version,
                )
            val changed =
                client.submitChecked(
                    slotPath,
                    "label" to "Erste Stunde",
                    "startTime" to "08:05",
                    "endTime" to "08:50",
                    "kind" to "TEACHING",
                    "version" to version,
                )

            assertEquals(HttpStatusCode.UnprocessableEntity, noTime.status)
            assertEquals(HttpStatusCode.SeeOther, changed.status)
            assertEquals(
                "Erste Stunde",
                system.world.schools
                    .findTimeGrid(schoolId)
                    .slotById(slot.id)
                    ?.label,
            )
        }
}
