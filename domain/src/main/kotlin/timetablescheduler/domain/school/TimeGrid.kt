package timetablescheduler.domain.school

import timetablescheduler.domain.common.TimeSlotId
import timetablescheduler.domain.validation.ValidationCollector
import timetablescheduler.domain.validation.ValidationError
import timetablescheduler.domain.validation.ValidationResult
import timetablescheduler.domain.validation.requiredText
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * The weekly structure of one school (FR-004 – FR-007): teaching days and one slot list that applies to every
 * teaching day unless a slot is excluded for a day. Exclusions for days that are no longer teaching days are
 * kept but ignored, so re-adding the day restores them.
 */
data class TimeGrid(
    val teachingDays: Set<DayOfWeek>,
    val slots: List<TimeSlot>,
    val exclusions: Set<SlotDayExclusion>,
) {
    fun withTeachingDays(days: Set<DayOfWeek>): TimeGrid = copy(teachingDays = days)

    fun withSlotAdded(label: String, startTime: LocalTime, endTime: LocalTime, kind: SlotKind): ValidationResult<TimeGrid> =
        validatedSlot(TimeSlotId.new(), label, startTime, endTime, kind, otherSlots = slots)

    fun withSlotUpdated(
        slotId: TimeSlotId,
        label: String,
        startTime: LocalTime,
        endTime: LocalTime,
        kind: SlotKind,
    ): ValidationResult<TimeGrid> =
        validatedSlot(slotId, label, startTime, endTime, kind, otherSlots = slots.filterNot { it.id == slotId })

    fun withSlotRemoved(slotId: TimeSlotId): TimeGrid =
        copy(slots = slots.filterNot { it.id == slotId }, exclusions = exclusions.filterNot { it.slotId == slotId }.toSet())

    /** Replaces the days on which [slotId] does not exist; only teaching days may be excluded. */
    fun withExclusions(slotId: TimeSlotId, excludedDays: Set<DayOfWeek>): ValidationResult<TimeGrid> {
        val validation = ValidationCollector()
        if (!excludedDays.all { it in teachingDays }) validation.add(ValidationError.InvalidReference(FIELD_DAY_OF_WEEK))
        return validation.result {
            val otherExclusions = exclusions.filterNot { it.slotId == slotId }
            copy(exclusions = (otherExclusions + excludedDays.map { SlotDayExclusion(slotId, it) }).toSet())
        }
    }

    fun slotById(slotId: TimeSlotId): TimeSlot? = slots.firstOrNull { it.id == slotId }

    fun slotsOn(day: DayOfWeek): List<TimeSlot> =
        if (day !in teachingDays) emptyList()
        else slots.filterNot { SlotDayExclusion(it.id, day) in exclusions }

    fun teachingSlotsOn(day: DayOfWeek): List<TimeSlot> = slotsOn(day).filter { it.kind == SlotKind.TEACHING }

    /** Σ over teaching days of the teaching slots not excluded that day — the weekly capacity of a class. */
    fun teachingSlotsPerWeek(): Int = teachingDays.sumOf { teachingSlotsOn(it).size }

    fun exclusionCountOf(slotId: TimeSlotId): Int = exclusions.count { it.slotId == slotId }

    private fun validatedSlot(
        slotId: TimeSlotId,
        label: String,
        startTime: LocalTime,
        endTime: LocalTime,
        kind: SlotKind,
        otherSlots: List<TimeSlot>,
    ): ValidationResult<TimeGrid> {
        val validation = ValidationCollector()
        val validLabel = validation.requiredText(TimeSlot.FIELD_LABEL, label, TimeSlot.LABEL_MAX_LENGTH)
        val slot = TimeSlot(slotId, validLabel, startTime, endTime, kind)
        if (endTime <= startTime) validation.add(ValidationError.EndNotAfterStart)
        else if (otherSlots.any { it.overlaps(slot) }) validation.add(ValidationError.Overlap)
        return validation.result { copy(slots = (otherSlots + slot).sortedBy { it.startTime }) }
    }

    companion object {
        const val FIELD_DAY_OF_WEEK = "dayOfWeek"

        fun empty() = TimeGrid(teachingDays = emptySet(), slots = emptyList(), exclusions = emptySet())
    }
}
