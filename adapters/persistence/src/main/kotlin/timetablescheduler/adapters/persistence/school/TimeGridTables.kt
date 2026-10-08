package timetablescheduler.adapters.persistence.school

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.time
import timetablescheduler.domain.school.TimeSlot

object TeachingDayTable : Table("teaching_day") {
    val schoolId = javaUUID("school_id").references(SchoolTable.id)
    val dayOfWeek = varchar("day_of_week", 9)
    override val primaryKey = PrimaryKey(schoolId, dayOfWeek)
}

object TimeSlotTable : Table("time_slot") {
    val id = javaUUID("id")
    val schoolId = javaUUID("school_id").references(SchoolTable.id)
    val label = varchar("label", TimeSlot.LABEL_MAX_LENGTH)
    val startTime = time("start_time")
    val endTime = time("end_time")
    val kind = varchar("kind", 8)
    override val primaryKey = PrimaryKey(id)
}

object SlotDayExclusionTable : Table("slot_day_exclusion") {
    val slotId = javaUUID("slot_id").references(TimeSlotTable.id)
    val dayOfWeek = varchar("day_of_week", 9)
    override val primaryKey = PrimaryKey(slotId, dayOfWeek)
}
