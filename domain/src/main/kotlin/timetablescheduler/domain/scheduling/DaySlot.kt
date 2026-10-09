package timetablescheduler.domain.scheduling

import timetablescheduler.domain.common.TimeSlotId
import java.time.DayOfWeek

/** One time slot on one weekday — the unit in which lessons are placed. */
data class DaySlot(val day: DayOfWeek, val slotId: TimeSlotId)
