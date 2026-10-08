package timetablescheduler.application.school

import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.fakes.MasterDataWorld
import timetablescheduler.domain.account.Role
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.school.School
import timetablescheduler.domain.school.SlotKind
import timetablescheduler.domain.validation.ValidationError
import java.time.DayOfWeek
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SchoolUseCasesTest {

    private val world = MasterDataWorld()
    private val nord = world.school("Gymnasium Nord")
    private val admin = world.planner("admin", nord.id)

    @Test
    fun `every active planner sees exactly the installation's school`() {
        val mueller = world.planner("mueller", nord.id)

        listOf(admin, mueller).forEach { planner ->
            val schools = assertIs<UseCaseResult.Success<List<School>>>(world.schoolUseCases.list.execute(planner.id)).value
            assertEquals(listOf(nord.id), schools.map { it.id })
        }
    }

    @Test
    fun `teacher account cannot list the school`() {
        val teacher = world.account("lehrer1", Role.TEACHER, nord.id)

        assertEquals(UseCaseResult.Failure(ApplicationError.AccessDenied), world.schoolUseCases.list.execute(teacher.id))
    }

    @Test
    fun `renaming checks the version`() {
        val renamed = world.schoolUseCases.rename.execute(admin.id, nord.id, " Gymnasium am Nordpark ", nord.version)
        val stale = world.schoolUseCases.rename.execute(admin.id, nord.id, "Anders", nord.version)

        assertEquals("Gymnasium am Nordpark", assertIs<UseCaseResult.Success<School>>(renamed).value.name)
        assertEquals("Gymnasium am Nordpark", world.schools.findById(nord.id)!!.name)
        assertEquals(UseCaseResult.Failure(ApplicationError.VersionConflict), stale)
    }

    @Test
    fun `renaming validates the name length`() {
        val blank = world.schoolUseCases.rename.execute(admin.id, nord.id, "  ", nord.version)
        val tooLong = world.schoolUseCases.rename.execute(admin.id, nord.id, "x".repeat(School.NAME_MAX_LENGTH + 1), nord.version)

        assertEquals(
            listOf(ValidationError.BlankValue(School.FIELD_NAME)),
            assertIs<ApplicationError.Invalid>(assertIs<UseCaseResult.Failure>(blank).error).errors,
        )
        assertEquals(
            listOf(ValidationError.TooLong(School.FIELD_NAME, School.NAME_MAX_LENGTH)),
            assertIs<ApplicationError.Invalid>(assertIs<UseCaseResult.Failure>(tooLong).error).errors,
        )
    }

    @Test
    fun `unknown school id is not found`() {
        val result = world.schoolUseCases.dashboard.execute(admin.id, SchoolId.new())

        assertEquals(UseCaseResult.Failure(ApplicationError.NotFound), result)
    }

    @Test
    fun `dashboard shows counts of the school`() {
        world.room(nord.id, "A-101")
        world.slot(nord.id, "1. Stunde", "08:00", "08:45")
        world.teachingDays(nord.id, DayOfWeek.MONDAY, DayOfWeek.TUESDAY)

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
