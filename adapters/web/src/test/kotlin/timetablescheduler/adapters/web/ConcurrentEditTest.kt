package timetablescheduler.adapters.web

import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** FR-027, quickstart 15: the later of two saves based on the same version must not overwrite the earlier one. */
class ConcurrentEditTest {

    @Test
    fun `second save of the same room version gets the conflict page with a reload link`() = testApplication {
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
        assertEquals("A-102", system.world.rooms.findById(room.id)!!.identifier)
    }
}
