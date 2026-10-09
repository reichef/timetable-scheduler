package timetablescheduler.application.fakes

import timetablescheduler.application.port.RoomFeatureRepository
import timetablescheduler.application.port.RoomRepository
import timetablescheduler.domain.common.RoomFeatureId
import timetablescheduler.domain.common.RoomId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.room.Room
import timetablescheduler.domain.room.RoomFeature

class InMemoryRoomFeatureRepository : RoomFeatureRepository {
    private val features = linkedMapOf<RoomFeatureId, RoomFeature>()

    override fun insert(feature: RoomFeature) {
        features[feature.id] = feature
    }

    override fun delete(featureId: RoomFeatureId) {
        features.remove(featureId)
    }

    override fun findById(featureId: RoomFeatureId): RoomFeature? = features[featureId]
    override fun findBySchool(schoolId: SchoolId): List<RoomFeature> = features.values.filter { it.schoolId == schoolId }
    override fun findByNameIgnoreCase(schoolId: SchoolId, name: String): RoomFeature? =
        findBySchool(schoolId).firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
}

class InMemoryRoomRepository : VersionedInMemoryStore<RoomId, Room>(
    keyOf = { it.id },
    versionOf = { it.version },
    withVersion = { room, version -> room.copy(version = version) },
), RoomRepository {
    override fun insert(room: Room) = super.insert(room)
    override fun update(room: Room): Boolean = super.update(room)

    override fun delete(roomId: RoomId) {
        entries.remove(roomId)
    }

    override fun findById(roomId: RoomId): Room? = entries[roomId]
    override fun findBySchool(schoolId: SchoolId): List<Room> = entries.values.filter { it.schoolId == schoolId }
    override fun findByIdentifierIgnoreCase(schoolId: SchoolId, identifier: String): Room? =
        findBySchool(schoolId).firstOrNull { it.identifier.equals(identifier.trim(), ignoreCase = true) }
    override fun findUsingFeature(featureId: RoomFeatureId): List<Room> = entries.values.filter { featureId in it.featureIds }
}
