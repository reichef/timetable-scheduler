package timetablescheduler.application.school

import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.error.Dependant
import timetablescheduler.application.error.DependantType
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.fakes.MasterDataWorld
import timetablescheduler.domain.account.Role
import timetablescheduler.domain.school.School
import timetablescheduler.domain.school.SlotKind
import timetablescheduler.domain.validation.ValidationError
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SchoolUseCasesTest {

    private val world = MasterDataWorld()
    private val nord = world.school("Gymnasium Nord")
    private val admin = world.planner("admin", nord.id)

    @Test
    fun `creating a school assigns it to the creator`() {
        val result = world.schoolUseCases.create.execute(admin.id, " Realschule Süd ")

        val school = assertIs<UseCaseResult.Success<School>>(result).value
        assertEquals("Realschule Süd", school.name)
        assertTrue(school.id in world.accounts.findById(admin.id)!!.schoolIds)
    }

    @Test
    fun `school name is unique case-insensitively`() {
        val result = world.schoolUseCases.create.execute(admin.id, "gymnasium nord")

        val error = assertIs<ApplicationError.Invalid>(assertIs<UseCaseResult.Failure>(result).error)
        assertEquals(listOf(ValidationError.DuplicateValue(School.FIELD_NAME)), error.errors)
    }

    @Test
    fun `teacher account cannot create schools`() {
        val teacher = world.account("lehrer1", Role.TEACHER, nord.id)

        assertEquals(UseCaseResult.Failure(ApplicationError.AccessDenied), world.schoolUseCases.create.execute(teacher.id, "Neu"))
    }

    @Test
    fun `renaming checks the version`() {
        val renamed = world.schoolUseCases.rename.execute(admin.id, nord.id, "Gymnasium am Nordpark", nord.version)
        val stale = world.schoolUseCases.rename.execute(admin.id, nord.id, "Anders", nord.version)

        assertEquals("Gymnasium am Nordpark", assertIs<UseCaseResult.Success<School>>(renamed).value.name)
        assertEquals(UseCaseResult.Failure(ApplicationError.VersionConflict), stale)
    }

    @Test
    fun `renaming to another school's name is rejected`() {
        val sued = world.school("Realschule Süd")
        world.assign(admin, sued.id)

        val result = world.schoolUseCases.rename.execute(admin.id, sued.id, "GYMNASIUM NORD", sued.version)

        assertIs<ApplicationError.Invalid>(assertIs<UseCaseResult.Failure>(result).error)
    }

    @Test
    fun `school of another planner is not found`() {
        val sued = world.school("Realschule Süd")

        val result = world.schoolUseCases.dashboard.execute(admin.id, sued.id)

        assertEquals(UseCaseResult.Failure(ApplicationError.NotFound), result)
    }

    @Test
    fun `deleting a school with rooms features and slots is blocked and lists them`() {
        val sued = world.school("Realschule Süd")
        world.assign(admin, sued.id)
        world.room(sued.id, "A-101")
        world.feature(sued.id, "Turnhalle")
        world.slot(sued.id, "1. Stunde", "08:00", "08:45")

        val result = world.schoolUseCases.delete.execute(admin.id, sued.id, sued.version)

        val blocked = assertIs<ApplicationError.DeletionBlocked>(assertIs<UseCaseResult.Failure>(result).error)
        assertEquals(
            setOf(
                Dependant(DependantType.ROOM, "A-101"),
                Dependant(DependantType.ROOM_FEATURE, "Turnhalle"),
                Dependant(DependantType.TIME_SLOT, "1. Stunde"),
            ),
            blocked.visibleDependants.toSet(),
        )
    }

    @Test
    fun `deleting the only school of an account is blocked and names the account`() {
        val sued = world.school("Realschule Süd")
        world.assign(admin, sued.id)
        world.planner("mueller", sued.id)

        val result = world.schoolUseCases.delete.execute(admin.id, sued.id, sued.version)

        val blocked = assertIs<ApplicationError.DeletionBlocked>(assertIs<UseCaseResult.Failure>(result).error)
        assertEquals(listOf(Dependant(DependantType.USER_ACCOUNT, "mueller")), blocked.visibleDependants)
    }

    @Test
    fun `deleting an unused school removes it from all accounts`() {
        val sued = world.school("Realschule Süd")
        world.assign(admin, sued.id)

        val result = world.schoolUseCases.delete.execute(admin.id, sued.id, sued.version)

        assertIs<UseCaseResult.Success<Unit>>(result)
        assertNull(world.schools.findById(sued.id))
        assertEquals(setOf(nord.id), world.accounts.findById(admin.id)!!.schoolIds)
    }

    @Test
    fun `dashboard shows counts of the school`() {
        world.room(nord.id, "A-101")
        world.slot(nord.id, "1. Stunde", "08:00", "08:45")
        world.teachingDays(nord.id, java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.TUESDAY)

        val dashboard = assertIs<UseCaseResult.Success<SchoolDashboard>>(world.schoolUseCases.dashboard.execute(admin.id, nord.id)).value

        assertEquals(1, dashboard.roomCount)
        assertEquals(2, dashboard.teachingSlotsPerWeek)
    }

    @Test
    fun `slot helper adds teaching slot`() {
        world.slot(nord.id, "Pause", "09:35", "09:55", SlotKind.BREAK)

        assertEquals(LocalTime.of(9, 35), world.schools.findTimeGrid(nord.id).slots.single().startTime)
    }
}
