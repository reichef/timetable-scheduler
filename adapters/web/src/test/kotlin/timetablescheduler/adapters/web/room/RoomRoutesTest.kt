package timetablescheduler.adapters.web.room

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import timetablescheduler.adapters.web.TestSystem
import timetablescheduler.adapters.web.login
import timetablescheduler.adapters.web.startWith
import timetablescheduler.adapters.web.submitChecked
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoomRoutesTest {

    @Test
    fun `adds feature and room with feature and lists them`() = testApplication {
        val system = TestSystem().installed()
        val schoolId = system.firstSchoolId().value
        val client = startWith(system)
        client.login()

        val featureResponse = client.submitChecked("/schools/$schoolId/room-features", "name" to "Chemieraum")
        val lab = system.world.roomFeatures.findByNameIgnoreCase(system.firstSchoolId(), "Chemieraum")!!
        val roomResponse = client.submitChecked("/schools/$schoolId/rooms", "identifier" to "B-104", "featureId" to "${lab.id.value}")
        val page = client.get("/schools/$schoolId/rooms").bodyAsText()

        assertEquals(HttpStatusCode.SeeOther, featureResponse.status)
        assertEquals(HttpStatusCode.SeeOther, roomResponse.status)
        assertTrue("B-104" in page)
        assertTrue("Chemieraum" in page)
    }

    @Test
    fun `duplicate room identifier is rejected and the entered value kept`() = testApplication {
        val system = TestSystem().installed()
        val schoolId = system.firstSchoolId()
        system.world.room(schoolId, "B-104")
        val client = startWith(system)
        client.login()

        val response = client.submitChecked("/schools/${schoolId.value}/rooms", "identifier" to "b-104")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertTrue("Bezeichnung ist bereits vergeben." in body)
        assertTrue("value=\"b-104\"" in body)
    }

    @Test
    fun `room can be edited and deleted`() = testApplication {
        val system = TestSystem().installed()
        val schoolId = system.firstSchoolId()
        val room = system.world.room(schoolId, "B-104")
        val client = startWith(system)
        client.login()
        val roomPath = "/schools/${schoolId.value}/rooms/${room.id.value}"

        val edit = client.submitChecked(roomPath, "identifier" to "B-105", "version" to "0")
        val delete = client.submitChecked("$roomPath/delete", "version" to "1")

        assertEquals(HttpStatusCode.SeeOther, edit.status)
        assertEquals(HttpStatusCode.SeeOther, delete.status)
        assertEquals(null, system.world.rooms.findById(room.id))
    }

    @Test
    fun `deleting a feature used by a room names the room`() = testApplication {
        val system = TestSystem().installed()
        val schoolId = system.firstSchoolId()
        val lab = system.world.feature(schoolId, "Chemieraum")
        system.world.room(schoolId, "B-104", lab.id)
        val client = startWith(system)
        client.login()

        val response = client.submitChecked("/schools/${schoolId.value}/room-features/${lab.id.value}/delete")

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertTrue("Raum: B-104" in response.bodyAsText())
    }
}
