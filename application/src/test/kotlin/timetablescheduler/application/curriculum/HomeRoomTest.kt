package timetablescheduler.application.curriculum

import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.error.Dependant
import timetablescheduler.application.error.DependantType
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.fakes.MasterDataWorld
import timetablescheduler.domain.common.RoomId
import timetablescheduler.domain.curriculum.SchoolClass
import timetablescheduler.domain.validation.ValidationError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** 002 FR-002a: optional home room per class, at most one class per room, rooms in use cannot be deleted. */
class HomeRoomTest {

    private val world = MasterDataWorld()
    private val nord = world.school("Gymnasium Nord")
    private val admin = world.planner("admin", nord.id)
    private val curriculum get() = world.useCases.curriculum
    private val level5 = world.classLevel(nord.id, "5")
    private val roomA = world.room(nord.id, "A-101")

    private fun <T> UseCaseResult<T>.value(): T = assertIs<UseCaseResult.Success<T>>(this).value

    private fun UseCaseResult<*>.error(): ApplicationError = assertIs<UseCaseResult.Failure>(this).error

    private fun update(schoolClass: SchoolClass, homeRoom: RoomId?) =
        curriculum.updateSchoolClass.execute(admin.id, nord.id, schoolClass.id, schoolClass.suffix, schoolClass.priorityWeight, schoolClass.version, homeRoom)

    @Test
    fun `a class is created with a home room`() {
        val created = curriculum.createSchoolClass.execute(admin.id, nord.id, level5.id, "a", 10, roomA.id).value()

        assertEquals(roomA.id, world.schoolClasses.findById(created.id)!!.homeRoomId)
    }

    @Test
    fun `the home room can be changed and cleared`() {
        val class5a = world.schoolClass(level5, "a")
        val roomB = world.room(nord.id, "B-102")

        val withRoom = update(class5a, roomA.id).value()
        assertEquals(roomA.id, withRoom.homeRoomId)
        val changed = update(withRoom, roomB.id).value()
        assertEquals(roomB.id, changed.homeRoomId)
        assertNull(update(changed, null).value().homeRoomId)
    }

    @Test
    fun `a room that is already another class's home room is rejected naming that class`() {
        update(world.schoolClass(level5, "a"), roomA.id).value()
        val class5b = world.schoolClass(level5, "b")

        assertEquals(ApplicationError.Invalid(listOf(ValidationError.HomeRoomTaken("5a"))), update(class5b, roomA.id).error())
    }

    @Test
    fun `a room of another school cannot be a home room`() {
        val other = world.school("Gymnasium Süd")
        val foreignRoom = world.room(other.id, "S-1")

        assertEquals(
            ApplicationError.Invalid(listOf(ValidationError.InvalidReference(SchoolClass.FIELD_HOME_ROOM))),
            update(world.schoolClass(level5, "a"), foreignRoom.id).error(),
        )
    }

    @Test
    fun `a room used as home room cannot be deleted and the class is listed`() {
        update(world.schoolClass(level5, "a"), roomA.id).value()

        assertEquals(
            ApplicationError.DeletionBlocked(listOf(Dependant(DependantType.SCHOOL_CLASS, "5a")), hiddenDependantCount = 0),
            world.useCases.rooms.deleteRoom.execute(admin.id, nord.id, roomA.id, roomA.version).error(),
        )
    }
}
