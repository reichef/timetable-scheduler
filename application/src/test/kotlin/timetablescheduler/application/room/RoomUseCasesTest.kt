package timetablescheduler.application.room

import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.error.Dependant
import timetablescheduler.application.error.DependantType
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.fakes.MasterDataWorld
import timetablescheduler.domain.room.Room
import timetablescheduler.domain.room.RoomFeature
import timetablescheduler.domain.validation.ValidationError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class RoomUseCasesTest {

    private val world = MasterDataWorld()
    private val nord = world.school("Gymnasium Nord")
    private val admin = world.planner("admin", nord.id)
    private val rooms = world.roomUseCases

    @Test
    fun `creates room with features of the school`() {
        val lab = world.feature(nord.id, "Chemieraum")

        val room = assertIs<UseCaseResult.Success<Room>>(rooms.createRoom.execute(admin.id, nord.id, "B-104", setOf(lab.id))).value

        assertEquals(setOf(lab.id), room.featureIds)
        assertEquals(listOf(room), assertIs<UseCaseResult.Success<RoomOverview>>(rooms.listRooms.execute(admin.id, nord.id)).value.rooms)
    }

    @Test
    fun `room identifier is unique per school case-insensitively`() {
        world.room(nord.id, "B-104")

        val result = rooms.createRoom.execute(admin.id, nord.id, "b-104", emptySet())

        val error = assertIs<ApplicationError.Invalid>(assertIs<UseCaseResult.Failure>(result).error)
        assertEquals(listOf(ValidationError.DuplicateValue(Room.FIELD_IDENTIFIER)), error.errors)
    }

    @Test
    fun `same room identifier is allowed in another school`() {
        val sued = world.school("Realschule Süd")
        world.assign(admin, sued.id)
        world.room(nord.id, "B-104")

        assertIs<UseCaseResult.Success<Room>>(rooms.createRoom.execute(admin.id, sued.id, "B-104", emptySet()))
    }

    @Test
    fun `room features of another school are rejected`() {
        val sued = world.school("Realschule Süd")
        val foreignLab = world.feature(sued.id, "Chemieraum")

        val result = rooms.createRoom.execute(admin.id, nord.id, "B-104", setOf(foreignLab.id))

        assertIs<ApplicationError.Invalid>(assertIs<UseCaseResult.Failure>(result).error)
    }

    @Test
    fun `room update checks version and uniqueness`() {
        val room = world.room(nord.id, "B-104")
        world.room(nord.id, "A-101")

        val duplicate = rooms.updateRoom.execute(admin.id, nord.id, room.id, "A-101", emptySet(), room.version)
        val updated = rooms.updateRoom.execute(admin.id, nord.id, room.id, "B-105", emptySet(), room.version)
        val stale = rooms.updateRoom.execute(admin.id, nord.id, room.id, "B-106", emptySet(), room.version)

        assertIs<ApplicationError.Invalid>(assertIs<UseCaseResult.Failure>(duplicate).error)
        assertEquals("B-105", assertIs<UseCaseResult.Success<Room>>(updated).value.identifier)
        assertEquals(UseCaseResult.Failure(ApplicationError.VersionConflict), stale)
    }

    @Test
    fun `room of another school is not found`() {
        val sued = world.school("Realschule Süd")
        val foreignRoom = world.room(sued.id, "X-1")

        assertEquals(UseCaseResult.Failure(ApplicationError.NotFound), rooms.deleteRoom.execute(admin.id, nord.id, foreignRoom.id, 0))
        assertEquals(UseCaseResult.Failure(ApplicationError.NotFound), rooms.deleteRoom.execute(admin.id, sued.id, foreignRoom.id, 0))
    }

    @Test
    fun `room can be deleted`() {
        val room = world.room(nord.id, "B-104")

        assertIs<UseCaseResult.Success<Unit>>(rooms.deleteRoom.execute(admin.id, nord.id, room.id, room.version))
        assertNull(world.rooms.findById(room.id))
    }

    @Test
    fun `feature name is unique per school`() {
        world.feature(nord.id, "Chemieraum")

        val result = rooms.createFeature.execute(admin.id, nord.id, "CHEMIERAUM")

        val error = assertIs<ApplicationError.Invalid>(assertIs<UseCaseResult.Failure>(result).error)
        assertEquals(listOf(ValidationError.DuplicateValue(RoomFeature.FIELD_NAME)), error.errors)
    }

    @Test
    fun `deleting a feature used by a room is blocked and names the room`() {
        val lab = world.feature(nord.id, "Chemieraum")
        world.room(nord.id, "B-104", lab.id)

        val result = rooms.deleteFeature.execute(admin.id, nord.id, lab.id)

        val blocked = assertIs<ApplicationError.DeletionBlocked>(assertIs<UseCaseResult.Failure>(result).error)
        assertEquals(listOf(Dependant(DependantType.ROOM, "B-104")), blocked.visibleDependants)
    }

    @Test
    fun `unused feature can be deleted`() {
        val lab = world.feature(nord.id, "Chemieraum")

        assertIs<UseCaseResult.Success<Unit>>(rooms.deleteFeature.execute(admin.id, nord.id, lab.id))
        assertNull(world.roomFeatures.findById(lab.id))
    }
}
