package timetablescheduler.domain.teacher

import timetablescheduler.domain.common.TimeSlotId
import java.time.DayOfWeek

/** A time slot on one weekday in which the teacher cannot teach — a hard constraint (FR-021, FR-022). */
data class Unavailability(val dayOfWeek: DayOfWeek, val slotId: TimeSlotId)
