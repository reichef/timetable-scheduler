package timetablescheduler.application.school

import timetablescheduler.application.access.DependantSource
import timetablescheduler.application.access.DependantVisibilityFilter
import timetablescheduler.application.access.SchoolAccessPolicy
import timetablescheduler.application.access.dependantsOf
import timetablescheduler.application.error.OutcomeScope
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.error.outcome
import timetablescheduler.application.port.SchoolRepository
import timetablescheduler.application.port.TimeSlotUsage
import timetablescheduler.application.port.TransactionRunner
import timetablescheduler.domain.common.AccountId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.common.TimeSlotId
import timetablescheduler.domain.school.School
import timetablescheduler.domain.school.SlotKind
import timetablescheduler.domain.school.TimeGrid
import timetablescheduler.domain.school.TimeSlot
import java.time.DayOfWeek
import java.time.LocalTime

data class TimeGridView(
    val school: School,
    val timeGrid: TimeGrid,
)

data class SlotDeletionPreview(
    val slot: TimeSlot,
    val exclusionCount: Int,
    val unavailabilityCount: Int,
    val schoolVersion: Int,
)

data class SlotDeletionResult(
    val removedUnavailabilities: Int,
)

/**
 * The time grid belongs to the school aggregate, so every change is checked against and bumps the school's
 * version (optimistic locking, FR-027). Reducing the grid is never blocked (spec clarification).
 */
private class TimeGridChange(
    private val access: SchoolAccessPolicy,
    private val schools: SchoolRepository,
) {
    fun OutcomeScope.apply(
        actor: AccountId,
        schoolId: SchoolId,
        expectedVersion: Int,
        change: OutcomeScope.(TimeGrid) -> TimeGrid,
    ): TimeGrid {
        access.requireSchoolAccess(actor, schoolId).bind()

        val school = loadSchoolForChange(schools, schoolId, expectedVersion)
        val changedGrid = change(schools.findTimeGrid(schoolId))

        requireUpdated(schools.update(school))
        schools.saveTimeGrid(schoolId, changedGrid)

        return changedGrid
    }
}

private fun OutcomeScope.requireSlot(
    timeGrid: TimeGrid,
    slotId: TimeSlotId,
): TimeSlot = timeGrid.slotById(slotId).orNotFound()

class GetTimeGrid(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val schools: SchoolRepository,
) {
    fun execute(
        actor: AccountId,
        schoolId: SchoolId,
    ): UseCaseResult<TimeGridView> =
        transactions.inTransaction {
            outcome {
                access.requireSchoolAccess(actor, schoolId).bind()
                TimeGridView(schools.findById(schoolId).orNotFound(), schools.findTimeGrid(schoolId))
            }
        }
}

class SetTeachingDays(
    private val transactions: TransactionRunner,
    access: SchoolAccessPolicy,
    schools: SchoolRepository,
) {
    private val change = TimeGridChange(access, schools)

    fun execute(
        actor: AccountId,
        schoolId: SchoolId,
        days: Set<DayOfWeek>,
        expectedVersion: Int,
    ): UseCaseResult<TimeGrid> =
        transactions.inTransaction {
            outcome { with(change) { apply(actor, schoolId, expectedVersion) { it.withTeachingDays(days) } } }
        }
}

class AddTimeSlot(
    private val transactions: TransactionRunner,
    access: SchoolAccessPolicy,
    schools: SchoolRepository,
) {
    private val change = TimeGridChange(access, schools)

    fun execute(
        actor: AccountId,
        schoolId: SchoolId,
        label: String,
        startTime: LocalTime,
        endTime: LocalTime,
        kind: SlotKind,
        expectedVersion: Int,
    ): UseCaseResult<TimeGrid> =
        transactions.inTransaction {
            outcome {
                with(change) { apply(actor, schoolId, expectedVersion) { it.withSlotAdded(label, startTime, endTime, kind).bind() } }
            }
        }
}

class UpdateTimeSlot(
    private val transactions: TransactionRunner,
    access: SchoolAccessPolicy,
    schools: SchoolRepository,
) {
    private val change = TimeGridChange(access, schools)

    fun execute(
        actor: AccountId,
        schoolId: SchoolId,
        slotId: TimeSlotId,
        label: String,
        startTime: LocalTime,
        endTime: LocalTime,
        kind: SlotKind,
        expectedVersion: Int,
    ): UseCaseResult<TimeGrid> =
        transactions.inTransaction {
            outcome {
                with(change) {
                    apply(actor, schoolId, expectedVersion) { grid ->
                        requireSlot(grid, slotId)
                        grid.withSlotUpdated(slotId, label, startTime, endTime, kind).bind()
                    }
                }
            }
        }
}

class SetSlotExclusions(
    private val transactions: TransactionRunner,
    access: SchoolAccessPolicy,
    schools: SchoolRepository,
) {
    private val change = TimeGridChange(access, schools)

    fun execute(
        actor: AccountId,
        schoolId: SchoolId,
        slotId: TimeSlotId,
        excludedDays: Set<DayOfWeek>,
        expectedVersion: Int,
    ): UseCaseResult<TimeGrid> =
        transactions.inTransaction {
            outcome {
                with(change) {
                    apply(actor, schoolId, expectedVersion) { grid ->
                        requireSlot(grid, slotId)
                        grid.withExclusions(slotId, excludedDays).bind()
                    }
                }
            }
        }
}

/** Shows the planner what deleting a slot removes before they confirm (spec clarification). */
class PreviewTimeSlotDeletion(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val schools: SchoolRepository,
    private val slotUsage: TimeSlotUsage,
) {
    fun execute(
        actor: AccountId,
        schoolId: SchoolId,
        slotId: TimeSlotId,
    ): UseCaseResult<SlotDeletionPreview> =
        transactions.inTransaction {
            outcome {
                access.requireSchoolAccess(actor, schoolId).bind()
                val school = schools.findById(schoolId).orNotFound()
                val grid = schools.findTimeGrid(schoolId)
                SlotDeletionPreview(
                    slot = requireSlot(grid, slotId),
                    exclusionCount = grid.exclusionCountOf(slotId),
                    unavailabilityCount = slotUsage.countUnavailabilities(slotId),
                    schoolVersion = school.version,
                )
            }
        }
}

/** Never blocked: removes the slot, its day exclusions and the teacher unavailabilities entered for it. */
class DeleteTimeSlot(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    schools: SchoolRepository,
    private val slotUsage: TimeSlotUsage,
    private val dependantSources: List<DependantSource<TimeSlotId>>,
) {
    private val change = TimeGridChange(access, schools)

    fun execute(
        actor: AccountId,
        schoolId: SchoolId,
        slotId: TimeSlotId,
        expectedVersion: Int,
    ): UseCaseResult<SlotDeletionResult> =
        transactions.inTransaction {
            outcome {
                val planner = access.requireSchoolAccess(actor, schoolId).bind()

                val dependants = dependantSources.dependantsOf(slotId)
                if (dependants.isNotEmpty()) {
                    fail(DependantVisibilityFilter.deletionBlocked(dependants, planner))
                }
                var removedUnavailabilities = 0
                with(change) {
                    apply(actor, schoolId, expectedVersion) { grid ->
                        requireSlot(grid, slotId)
                        removedUnavailabilities = slotUsage.countUnavailabilities(slotId)
                        slotUsage.deleteUnavailabilities(slotId)
                        grid.withSlotRemoved(slotId)
                    }
                }

                SlotDeletionResult(removedUnavailabilities)
            }
        }
}

class TimeGridUseCases(
    val getTimeGrid: GetTimeGrid,
    val setTeachingDays: SetTeachingDays,
    val addSlot: AddTimeSlot,
    val updateSlot: UpdateTimeSlot,
    val setExclusions: SetSlotExclusions,
    val previewDeletion: PreviewTimeSlotDeletion,
    val deleteSlot: DeleteTimeSlot,
)
