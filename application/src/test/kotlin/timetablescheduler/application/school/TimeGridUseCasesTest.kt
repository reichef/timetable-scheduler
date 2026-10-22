package timetablescheduler.application.school

import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.fakes.MasterDataWorld
import timetablescheduler.domain.school.SlotKind
import timetablescheduler.domain.school.TimeGrid
import timetablescheduler.domain.validation.ValidationError
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TimeGridUseCasesTest {
    private val world = MasterDataWorld()
    private val nord = world.school("Gymnasium Nord")
    private val admin = world.planner("admin", nord.id)
    private val grid = world.timeGridUseCases

    private fun currentVersion() = world.schools.findById(nord.id)!!.version

    @Test
    fun `adds slots and teaching days and bumps the school version`() {
        val added =
            grid.addSlot.execute(
                admin.id,
                nord.id,
                "1. Stunde",
                LocalTime.of(8, 0),
                LocalTime.of(8, 45),
                SlotKind.TEACHING,
                currentVersion(),
            )
        val days = grid.setTeachingDays.execute(admin.id, nord.id, setOf(MONDAY, FRIDAY), currentVersion())

        assertIs<UseCaseResult.Success<TimeGrid>>(added)
        assertEquals(2, assertIs<UseCaseResult.Success<TimeGrid>>(days).value.teachingSlotsPerWeek())
        assertEquals(nord.version + 2, currentVersion())
    }

    @Test
    fun `overlapping slot is rejected`() {
        world.slot(nord.id, "1. Stunde", "08:00", "08:45")

        val result =
            grid.addSlot.execute(
                admin.id,
                nord.id,
                "Extra",
                LocalTime.of(8, 30),
                LocalTime.of(9, 15),
                SlotKind.TEACHING,
                currentVersion(),
            )

        val error = assertIs<ApplicationError.Invalid>(assertIs<UseCaseResult.Failure>(result).error)
        assertTrue(ValidationError.Overlap in error.errors)
    }

    @Test
    fun `stale version is a conflict`() {
        val stale = currentVersion()
        world.slot(nord.id, "1. Stunde", "08:00", "08:45")
        world.bumpSchoolVersion(nord.id)

        val result =
            grid.addSlot.execute(
                admin.id,
                nord.id,
                "2. Stunde",
                LocalTime.of(8, 50),
                LocalTime.of(9, 35),
                SlotKind.TEACHING,
                stale,
            )

        assertEquals(UseCaseResult.Failure(ApplicationError.VersionConflict), result)
    }

    @Test
    fun `deletion preview counts exclusions and deleting is never blocked`() {
        world.teachingDays(nord.id, MONDAY, FRIDAY)
        val slot = world.slot(nord.id, "8. Stunde", "14:00", "14:45")
        grid.setExclusions.execute(admin.id, nord.id, slot.id, setOf(FRIDAY), currentVersion())

        val preview = assertIs<UseCaseResult.Success<SlotDeletionPreview>>(grid.previewDeletion.execute(admin.id, nord.id, slot.id)).value
        val deleted = grid.deleteSlot.execute(admin.id, nord.id, slot.id, currentVersion())

        assertEquals(1, preview.exclusionCount)
        assertIs<UseCaseResult.Success<SlotDeletionResult>>(deleted)
        assertTrue(
            world.schools
                .findTimeGrid(nord.id)
                .slots
                .isEmpty(),
        )
        assertTrue(
            world.schools
                .findTimeGrid(nord.id)
                .exclusions
                .isEmpty(),
        )
    }

    @Test
    fun `unknown slot is not found`() {
        val result =
            grid.previewDeletion.execute(
                admin.id,
                nord.id,
                timetablescheduler.domain.common.TimeSlotId
                    .new(),
            )

        assertEquals(UseCaseResult.Failure(ApplicationError.NotFound), result)
    }

    @Test
    fun `time grid of another school is not found`() {
        val sued = world.school("Realschule Süd")

        assertEquals(UseCaseResult.Failure(ApplicationError.NotFound), grid.getTimeGrid.execute(admin.id, sued.id))
    }
}
