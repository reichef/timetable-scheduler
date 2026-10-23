package timetablescheduler.adapters.persistence.school

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.common.TimeSlotId
import timetablescheduler.domain.school.SlotDayExclusion
import timetablescheduler.domain.school.SlotKind
import timetablescheduler.domain.school.TimeGrid
import timetablescheduler.domain.school.TimeSlot
import java.time.DayOfWeek
import java.util.UUID

/** Reads and writes the time grid of one school; slots are diffed so their ids (and references) survive. */
internal object TimeGridStore {
    fun load(schoolId: SchoolId): TimeGrid {
        val teachingDays =
            TeachingDayTable
                .selectAll()
                .where { TeachingDayTable.schoolId eq schoolId.value }
                .map { DayOfWeek.valueOf(it[TeachingDayTable.dayOfWeek]) }
                .toSet()
        val slots = loadSlots(schoolId)

        return TimeGrid(teachingDays, slots, loadExclusions(slots))
    }

    private fun loadSlots(schoolId: SchoolId): List<TimeSlot> =
        TimeSlotTable
            .selectAll()
            .where { TimeSlotTable.schoolId eq schoolId.value }
            .map {
                TimeSlot(
                    id = TimeSlotId(it[TimeSlotTable.id]),
                    label = it[TimeSlotTable.label],
                    startTime = it[TimeSlotTable.startTime],
                    endTime = it[TimeSlotTable.endTime],
                    kind = SlotKind.valueOf(it[TimeSlotTable.kind]),
                )
            }.sortedBy { it.startTime }

    private fun loadExclusions(slots: List<TimeSlot>): Set<SlotDayExclusion> {
        if (slots.isEmpty()) {
            return emptySet()
        }

        return SlotDayExclusionTable
            .selectAll()
            .where { SlotDayExclusionTable.slotId inList slots.map { it.id.value } }
            .map { SlotDayExclusion(TimeSlotId(it[SlotDayExclusionTable.slotId]), DayOfWeek.valueOf(it[SlotDayExclusionTable.dayOfWeek])) }
            .toSet()
    }

    fun save(
        schoolId: SchoolId,
        timeGrid: TimeGrid,
    ) {
        saveTeachingDays(schoolId, timeGrid.teachingDays)
        saveSlots(schoolId, timeGrid.slots)
        saveExclusions(timeGrid)
    }

    private fun saveTeachingDays(
        schoolId: SchoolId,
        teachingDays: Set<DayOfWeek>,
    ) {
        TeachingDayTable.deleteWhere { TeachingDayTable.schoolId eq schoolId.value }
        TeachingDayTable.batchInsert(teachingDays) { day ->
            this[TeachingDayTable.schoolId] = schoolId.value
            this[TeachingDayTable.dayOfWeek] = day.name
        }
    }

    /** Deletes removed slots (their exclusions cascade), updates kept ones and inserts new ones. */
    private fun saveSlots(
        schoolId: SchoolId,
        slots: List<TimeSlot>,
    ) {
        val existingIds = existingSlotIds(schoolId).toSet()
        val removedIds = existingIds - slots.map { it.id.value }.toSet()
        if (removedIds.isNotEmpty()) {
            TimeSlotTable.deleteWhere { TimeSlotTable.id inList removedIds }
        }
        slots.forEach { slot ->
            if (slot.id.value in existingIds) updateSlot(slot) else insertSlot(schoolId, slot)
        }
    }

    private fun saveExclusions(timeGrid: TimeGrid) {
        val slotIds = timeGrid.slots.map { it.id.value }
        if (slotIds.isEmpty()) {
            return
        }

        SlotDayExclusionTable.deleteWhere { SlotDayExclusionTable.slotId inList slotIds }
        SlotDayExclusionTable.batchInsert(timeGrid.exclusions) { exclusion ->
            this[SlotDayExclusionTable.slotId] = exclusion.slotId.value
            this[SlotDayExclusionTable.dayOfWeek] = exclusion.dayOfWeek.name
        }
    }

    private fun existingSlotIds(schoolId: SchoolId): List<UUID> =
        TimeSlotTable.select(TimeSlotTable.id).where { TimeSlotTable.schoolId eq schoolId.value }.map { it[TimeSlotTable.id] }

    private fun insertSlot(
        schoolId: SchoolId,
        slot: TimeSlot,
    ) {
        TimeSlotTable.insert {
            it[id] = slot.id.value
            it[TimeSlotTable.schoolId] = schoolId.value
            it[label] = slot.label
            it[startTime] = slot.startTime
            it[endTime] = slot.endTime
            it[kind] = slot.kind.name
        }
    }

    private fun updateSlot(slot: TimeSlot) {
        TimeSlotTable.update({ TimeSlotTable.id eq slot.id.value }) {
            it[label] = slot.label
            it[startTime] = slot.startTime
            it[endTime] = slot.endTime
            it[kind] = slot.kind.name
        }
    }
}
