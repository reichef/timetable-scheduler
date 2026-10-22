package timetablescheduler.application.port

import timetablescheduler.domain.common.RoomFeatureId
import timetablescheduler.domain.common.RoomId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.common.TimeSlotId
import timetablescheduler.domain.room.Room
import timetablescheduler.domain.room.RoomFeature

interface RoomFeatureRepository {
    fun insert(feature: RoomFeature)

    fun delete(featureId: RoomFeatureId)

    fun findById(featureId: RoomFeatureId): RoomFeature?

    fun findBySchool(schoolId: SchoolId): List<RoomFeature>

    fun findByNameIgnoreCase(
        schoolId: SchoolId,
        name: String,
    ): RoomFeature?
}

interface RoomRepository {
    fun insert(room: Room)

    /** Saves [room] if its stored version still equals `room.version`; returns false otherwise. */
    fun update(room: Room): Boolean

    fun delete(roomId: RoomId)

    fun findById(roomId: RoomId): Room?

    fun findBySchool(schoolId: SchoolId): List<Room>

    fun findByIdentifierIgnoreCase(
        schoolId: SchoolId,
        identifier: String,
    ): Room?

    fun findUsingFeature(featureId: RoomFeatureId): List<Room>
}

/** Teacher unavailabilities entered for a time slot; they are removed together with the slot. */
interface TimeSlotUsage {
    fun countUnavailabilities(slotId: TimeSlotId): Int

    fun deleteUnavailabilities(slotId: TimeSlotId)
}
