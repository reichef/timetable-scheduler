package timetablescheduler.domain.school

import timetablescheduler.domain.common.TimeSlotId
import java.time.DayOfWeek
import java.time.LocalTime

enum class SlotKind { TEACHING, BREAK }

/** One entry of the weekly time grid, e.g. "3. Stunde, 09:55–10:40" (FR-005). */
data class TimeSlot(
    val id: TimeSlotId,
    val label: String,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val kind: SlotKind,
) {
    /** Half-open intervals: slots that only touch (end == start) do not overlap. */
    fun overlaps(other: TimeSlot): Boolean = startTime < other.endTime && other.startTime < endTime

    companion object {
        const val FIELD_LABEL = "label"
        const val LABEL_MAX_LENGTH = 40
    }
}

/** The slot does not exist on that teaching day, e.g. a shorter Friday (FR-007). */
data class SlotDayExclusion(val slotId: TimeSlotId, val dayOfWeek: DayOfWeek)
