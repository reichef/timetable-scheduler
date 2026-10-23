package timetablescheduler.adapters.persistence.room

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import timetablescheduler.adapters.persistence.school.SchoolTable
import timetablescheduler.application.port.RoomFeatureRepository
import timetablescheduler.application.port.RoomRepository
import timetablescheduler.domain.common.RoomFeatureId
import timetablescheduler.domain.common.RoomId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.room.Room
import timetablescheduler.domain.room.RoomFeature
import java.util.UUID

object RoomFeatureTable : Table("room_feature") {
    val id = javaUUID("id")
    val schoolId = javaUUID("school_id").references(SchoolTable.id)
    val name = varchar("name", RoomFeature.NAME_MAX_LENGTH)
    override val primaryKey = PrimaryKey(id)
}

object RoomTable : Table("room") {
    val id = javaUUID("id")
    val schoolId = javaUUID("school_id").references(SchoolTable.id)
    val identifier = varchar("identifier", Room.IDENTIFIER_MAX_LENGTH)
    val version = integer("version")
    override val primaryKey = PrimaryKey(id)
}

object RoomHasFeatureTable : Table("room_has_feature") {
    val roomId = javaUUID("room_id").references(RoomTable.id)
    val featureId = javaUUID("feature_id").references(RoomFeatureTable.id)
    override val primaryKey = PrimaryKey(roomId, featureId)
}

class ExposedRoomFeatureRepository : RoomFeatureRepository {
    override fun insert(feature: RoomFeature) {
        RoomFeatureTable.insert {
            it[id] = feature.id.value
            it[schoolId] = feature.schoolId.value
            it[name] = feature.name
        }
    }

    override fun delete(featureId: RoomFeatureId) {
        RoomFeatureTable.deleteWhere { RoomFeatureTable.id eq featureId.value }
    }

    override fun findById(featureId: RoomFeatureId): RoomFeature? =
        RoomFeatureTable
            .selectAll()
            .where { RoomFeatureTable.id eq featureId.value }
            .singleOrNull()
            ?.toFeature()

    override fun findBySchool(schoolId: SchoolId): List<RoomFeature> =
        RoomFeatureTable.selectAll().where { RoomFeatureTable.schoolId eq schoolId.value }.map { it.toFeature() }

    override fun findByNameIgnoreCase(
        schoolId: SchoolId,
        name: String,
    ): RoomFeature? =
        RoomFeatureTable
            .selectAll()
            .where { (RoomFeatureTable.schoolId eq schoolId.value) and (RoomFeatureTable.name.lowerCase() eq name.trim().lowercase()) }
            .singleOrNull()
            ?.toFeature()

    private fun ResultRow.toFeature() =
        RoomFeature(RoomFeatureId(this[RoomFeatureTable.id]), SchoolId(this[RoomFeatureTable.schoolId]), this[RoomFeatureTable.name])
}

class ExposedRoomRepository : RoomRepository {
    override fun insert(room: Room) {
        RoomTable.insert {
            it[id] = room.id.value
            it[schoolId] = room.schoolId.value
            it[identifier] = room.identifier
            it[version] = room.version
        }
        insertFeatures(room)
    }

    override fun update(room: Room): Boolean {
        val updatedRows =
            RoomTable.update({ (RoomTable.id eq room.id.value) and (RoomTable.version eq room.version) }) {
                it[identifier] = room.identifier
                it[version] = room.version + 1
            }
        if (updatedRows != 1) {
            return false
        }

        RoomHasFeatureTable.deleteWhere { RoomHasFeatureTable.roomId eq room.id.value }
        insertFeatures(room)
        return true
    }

    override fun delete(roomId: RoomId) {
        RoomHasFeatureTable.deleteWhere { RoomHasFeatureTable.roomId eq roomId.value }
        RoomTable.deleteWhere { RoomTable.id eq roomId.value }
    }

    override fun findById(roomId: RoomId): Room? =
        loadRooms(
            RoomTable
                .selectAll()
                .where {
                    RoomTable.id eq roomId.value
                }.toList(),
        ).singleOrNull()

    override fun findBySchool(schoolId: SchoolId): List<Room> =
        loadRooms(RoomTable.selectAll().where { RoomTable.schoolId eq schoolId.value }.toList())

    override fun findByIdentifierIgnoreCase(
        schoolId: SchoolId,
        identifier: String,
    ): Room? =
        loadRooms(
            RoomTable
                .selectAll()
                .where { (RoomTable.schoolId eq schoolId.value) and (RoomTable.identifier.lowerCase() eq identifier.trim().lowercase()) }
                .toList(),
        ).singleOrNull()

    override fun findUsingFeature(featureId: RoomFeatureId): List<Room> {
        val roomIds =
            RoomHasFeatureTable
                .select(RoomHasFeatureTable.roomId)
                .where { RoomHasFeatureTable.featureId eq featureId.value }
                .map { it[RoomHasFeatureTable.roomId] }
        if (roomIds.isEmpty()) {
            return emptyList()
        }

        return loadRooms(RoomTable.selectAll().where { RoomTable.id inList roomIds }.toList())
    }

    private fun insertFeatures(room: Room) {
        RoomHasFeatureTable.batchInsert(room.featureIds) { featureId ->
            this[RoomHasFeatureTable.roomId] = room.id.value
            this[RoomHasFeatureTable.featureId] = featureId.value
        }
    }

    /** Loads the features of all [rows] with one query instead of one per room. */
    private fun loadRooms(rows: List<ResultRow>): List<Room> {
        if (rows.isEmpty()) {
            return emptyList()
        }

        val featuresByRoom: Map<UUID, Set<RoomFeatureId>> =
            RoomHasFeatureTable
                .selectAll()
                .where { RoomHasFeatureTable.roomId inList rows.map { it[RoomTable.id] } }
                .groupBy({ it[RoomHasFeatureTable.roomId] }, { RoomFeatureId(it[RoomHasFeatureTable.featureId]) })
                .mapValues { it.value.toSet() }
        return rows.map { row ->
            Room(
                id = RoomId(row[RoomTable.id]),
                schoolId = SchoolId(row[RoomTable.schoolId]),
                identifier = row[RoomTable.identifier],
                featureIds = featuresByRoom[row[RoomTable.id]].orEmpty(),
                version = row[RoomTable.version],
            )
        }
    }
}
