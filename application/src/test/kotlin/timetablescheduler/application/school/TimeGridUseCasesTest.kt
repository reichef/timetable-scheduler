package timetablescheduler.application.school

import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.error.Dependant
import timetablescheduler.application.error.DependantType
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.fakes.MasterDataWorld
import timetablescheduler.application.scheduling.ConstraintInput
import timetablescheduler.domain.common.TimeSlotId
import timetablescheduler.domain.scheduling.DaySlot
import timetablescheduler.domain.scheduling.constraint.ConstraintKind
import timetablescheduler.domain.scheduling.constraint.ConstraintRule
import timetablescheduler.domain.scheduling.constraint.ConstraintType
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

        val gridAfter = world.schools.findTimeGrid(nord.id)

        assertEquals(1, preview.exclusionCount)
        assertIs<UseCaseResult.Success<SlotDeletionResult>>(deleted)
        assertTrue(gridAfter.slots.isEmpty())
        assertTrue(gridAfter.exclusions.isEmpty())
    }

    @Test
    fun `deleting a slot a constraint refers to is blocked and names the constraint by the slot label`() {
        world.teachingDays(nord.id, MONDAY)
        val slot = world.slot(nord.id, "8. Stunde", "14:00", "14:45")
        val sport = world.subject(nord.id, "Sport")
        val notLast = ConstraintRule.SubjectExcludedSlots(sport.id, null, setOf(DaySlot(MONDAY, slot.id)))
        val created =
            world.useCases.constraints.create
                .execute(admin.id, nord.id, ConstraintInput(ConstraintKind.HARD, null, notLast))

        val result = grid.deleteSlot.execute(admin.id, nord.id, slot.id, currentVersion())

        assertIs<UseCaseResult.Success<*>>(created)
        val blocked = assertIs<ApplicationError.DeletionBlocked>(assertIs<UseCaseResult.Failure>(result).error)
        assertEquals(
            listOf(Dependant(DependantType.SCHEDULING_CONSTRAINT, "8. Stunde", ConstraintType.SUBJECT_EXCLUDED_SLOTS)),
            blocked.visibleDependants,
        )
    }

    @Test
    fun `a changed time slot is saved and bumps the school version`() {
        val slot = world.slot(nord.id, "1. Stunde", "08:00", "08:45")
        val versionBefore = currentVersion()

        val result =
            grid.updateSlot.execute(
                admin.id,
                nord.id,
                slot.id,
                "Erste Stunde",
                LocalTime.of(8, 5),
                LocalTime.of(8, 50),
                SlotKind.TEACHING,
                versionBefore,
            )

        val changed = assertIs<UseCaseResult.Success<TimeGrid>>(result).value.slotById(slot.id)
        assertEquals("Erste Stunde", changed?.label)
        assertEquals(LocalTime.of(8, 5), changed?.startTime)
        assertEquals(versionBefore + 1, currentVersion())
    }

    @Test
    fun `unknown slot is not found`() {
        val result =
            grid.previewDeletion.execute(
                admin.id,
                nord.id,
                TimeSlotId.new(),
            )

        assertEquals(UseCaseResult.Failure(ApplicationError.NotFound), result)
    }

    @Test
    fun `time grid of another school is not found`() {
        val sued = world.school("Realschule Süd")

        assertEquals(UseCaseResult.Failure(ApplicationError.NotFound), grid.getTimeGrid.execute(admin.id, sued.id))
    }
}
