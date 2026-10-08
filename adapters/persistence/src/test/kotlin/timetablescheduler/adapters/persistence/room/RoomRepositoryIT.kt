package timetablescheduler.adapters.persistence.room

import timetablescheduler.adapters.persistence.PostgresTestBase
import timetablescheduler.adapters.persistence.school.ExposedSchoolRepository
import timetablescheduler.domain.room.Room
import timetablescheduler.domain.room.RoomFeature
import timetablescheduler.domain.school.School
import timetablescheduler.domain.validation.ValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomRepositoryIT : PostgresTestBase() {

    private val schools = ExposedSchoolRepository()
    private val rooms = ExposedRoomRepository()
    private val features = ExposedRoomFeatureRepository()

    private fun <T> ValidationResult<T>.valid(): T = (this as ValidationResult.Valid).value

    private val school = School.create("Gymnasium Nord").valid()

    private fun arrange(): Pair<RoomFeature, Room> {
        val lab = RoomFeature.create(school.id, "Chemieraum").valid()
        val room = Room.create(school.id, "B-104", setOf(lab.id), setOf(lab.id)).valid()
        inTransaction {
            schools.insert(school)
            features.insert(lab)
            rooms.insert(room)
        }
        return lab to room
    }

    @Test
    fun `stores and reloads rooms with features`() {
        val (lab, room) = arrange()

        assertEquals(room, inTransaction { rooms.findById(room.id) })
        assertEquals(listOf(room), inTransaction { rooms.findBySchool(school.id) })
        assertEquals(listOf(room), inTransaction { rooms.findUsingFeature(lab.id) })
        assertEquals(listOf(lab), inTransaction { features.findBySchool(school.id) })
    }

    @Test
    fun `finds room and feature by name case-insensitively`() {
        arrange()

        assertEquals("B-104", inTransaction { rooms.findByIdentifierIgnoreCase(school.id, "b-104") }?.identifier)
        assertEquals("Chemieraum", inTransaction { features.findByNameIgnoreCase(school.id, "CHEMIERAUM") }?.name)
    }

    @Test
    fun `room update uses optimistic locking and replaces features`() {
        val (_, room) = arrange()

        assertTrue(inTransaction { rooms.update(room.copy(identifier = "B-105", featureIds = emptySet())) })
        assertFalse(inTransaction { rooms.update(room.copy(identifier = "B-106")) })

        val reloaded = inTransaction { rooms.findById(room.id) }!!
        assertEquals("B-105", reloaded.identifier)
        assertTrue(reloaded.featureIds.isEmpty())
        assertEquals(1, reloaded.version)
    }

    @Test
    fun `deletes room and unused feature`() {
        val (lab, room) = arrange()

        inTransaction {
            rooms.delete(room.id)
            features.delete(lab.id)
        }

        assertNull(inTransaction { rooms.findById(room.id) })
        assertNull(inTransaction { features.findById(lab.id) })
    }
}
