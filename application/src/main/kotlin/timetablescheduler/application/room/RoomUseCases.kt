package timetablescheduler.application.room

import timetablescheduler.application.access.DependantSource
import timetablescheduler.application.access.DependantVisibilityFilter
import timetablescheduler.application.access.SchoolAccessPolicy
import timetablescheduler.application.access.dependantsOf
import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.error.OutcomeScope
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.error.outcome
import timetablescheduler.application.port.RoomFeatureRepository
import timetablescheduler.application.port.RoomRepository
import timetablescheduler.application.port.TransactionRunner
import timetablescheduler.domain.common.AccountId
import timetablescheduler.domain.common.RoomFeatureId
import timetablescheduler.domain.common.RoomId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.room.Room
import timetablescheduler.domain.room.RoomFeature
import timetablescheduler.domain.validation.ValidationError

data class RoomOverview(
    val rooms: List<Room>,
    val features: List<RoomFeature>,
)

data class RoomDetails(
    val room: Room,
    val features: List<RoomFeature>,
)

private fun duplicate(field: String) = ApplicationError.Invalid(listOf(ValidationError.DuplicateValue(field)))

/** Rejects an identifier another room of the same school already uses (FR-002, case-insensitive). */
private fun OutcomeScope.requireUniqueIdentifier(
    rooms: RoomRepository,
    room: Room,
) {
    val existing = rooms.findByIdentifierIgnoreCase(room.schoolId, room.identifier)
    if (existing != null && existing.id != room.id) {
        fail(duplicate(Room.FIELD_IDENTIFIER))
    }
}

/** Rooms are only reachable through their own school; others are "not found". */
private fun OutcomeScope.loadRoomOfSchool(
    rooms: RoomRepository,
    schoolId: SchoolId,
    roomId: RoomId,
): Room {
    val room = rooms.findById(roomId)
    if (room == null || room.schoolId != schoolId) {
        fail(ApplicationError.NotFound)
    }
    return room
}

/** The ids of all room features of [schoolId]; entities may only reference these (FR-003, FR-009). */
internal fun RoomFeatureRepository.featureIdsOf(schoolId: SchoolId): Set<RoomFeatureId> = findBySchool(schoolId).map { it.id }.toSet()

class CreateRoomFeature(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val roomFeatures: RoomFeatureRepository,
) {
    fun execute(
        actor: AccountId,
        schoolId: SchoolId,
        name: String,
    ): UseCaseResult<RoomFeature> =
        transactions.inTransaction {
            outcome {
                access.requireSchoolAccess(actor, schoolId).bind()
                val feature = RoomFeature.create(schoolId, name).bind()
                if (roomFeatures.findByNameIgnoreCase(schoolId, feature.name) != null) {
                    fail(duplicate(RoomFeature.FIELD_NAME))
                }
                roomFeatures.insert(feature)
                feature
            }
        }
}

/** Blocked while rooms offer the feature or subjects require it (FR-023). */
class DeleteRoomFeature(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val roomFeatures: RoomFeatureRepository,
    private val dependantSources: List<DependantSource<RoomFeatureId>>,
) {
    fun execute(
        actor: AccountId,
        schoolId: SchoolId,
        featureId: RoomFeatureId,
    ): UseCaseResult<Unit> =
        transactions.inTransaction {
            outcome {
                val planner = access.requireSchoolAccess(actor, schoolId).bind()
                val feature = roomFeatures.findById(featureId)
                if (feature == null || feature.schoolId != schoolId) {
                    fail(ApplicationError.NotFound)
                }
                val dependants = dependantSources.dependantsOf(featureId)
                if (dependants.isNotEmpty()) {
                    fail(DependantVisibilityFilter.deletionBlocked(dependants, planner))
                }
                roomFeatures.delete(featureId)
            }
        }
}

class CreateRoom(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val rooms: RoomRepository,
    private val roomFeatures: RoomFeatureRepository,
) {
    fun execute(
        actor: AccountId,
        schoolId: SchoolId,
        identifier: String,
        featureIds: Set<RoomFeatureId>,
    ): UseCaseResult<Room> =
        transactions.inTransaction {
            outcome {
                access.requireSchoolAccess(actor, schoolId).bind()
                val room = Room.create(schoolId, identifier, featureIds, roomFeatures.featureIdsOf(schoolId)).bind()
                requireUniqueIdentifier(rooms, room)
                rooms.insert(room)
                room
            }
        }
}

class UpdateRoom(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val rooms: RoomRepository,
    private val roomFeatures: RoomFeatureRepository,
) {
    fun execute(
        actor: AccountId,
        schoolId: SchoolId,
        roomId: RoomId,
        identifier: String,
        featureIds: Set<RoomFeatureId>,
        expectedVersion: Int,
    ): UseCaseResult<Room> =
        transactions.inTransaction {
            outcome {
                access.requireSchoolAccess(actor, schoolId).bind()
                val room = loadRoomOfSchool(rooms, schoolId, roomId).copy(version = expectedVersion)
                val updated = room.update(identifier, featureIds, roomFeatures.featureIdsOf(schoolId)).bind()
                requireUniqueIdentifier(rooms, updated)
                requireUpdated(rooms.update(updated))
                updated.copy(version = expectedVersion + 1)
            }
        }
}

class DeleteRoom(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val rooms: RoomRepository,
    private val dependantSources: List<DependantSource<RoomId>>,
) {
    fun execute(
        actor: AccountId,
        schoolId: SchoolId,
        roomId: RoomId,
        expectedVersion: Int,
    ): UseCaseResult<Unit> =
        transactions.inTransaction {
            outcome {
                val planner = access.requireSchoolAccess(actor, schoolId).bind()
                val room = loadRoomOfSchool(rooms, schoolId, roomId)
                if (room.version != expectedVersion) {
                    fail(ApplicationError.VersionConflict)
                }
                val dependants = dependantSources.dependantsOf(roomId)
                if (dependants.isNotEmpty()) {
                    fail(DependantVisibilityFilter.deletionBlocked(dependants, planner))
                }
                rooms.delete(roomId)
            }
        }
}

class ListRooms(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val rooms: RoomRepository,
    private val roomFeatures: RoomFeatureRepository,
) {
    fun execute(
        actor: AccountId,
        schoolId: SchoolId,
    ): UseCaseResult<RoomOverview> =
        transactions.inTransaction {
            outcome {
                access.requireSchoolAccess(actor, schoolId).bind()
                RoomOverview(
                    rooms = rooms.findBySchool(schoolId).sortedBy { it.identifier.lowercase() },
                    features = roomFeatures.findBySchool(schoolId).sortedBy { it.name.lowercase() },
                )
            }
        }
}

class GetRoom(
    private val transactions: TransactionRunner,
    private val access: SchoolAccessPolicy,
    private val rooms: RoomRepository,
    private val roomFeatures: RoomFeatureRepository,
) {
    fun execute(
        actor: AccountId,
        schoolId: SchoolId,
        roomId: RoomId,
    ): UseCaseResult<RoomDetails> =
        transactions.inTransaction {
            outcome {
                access.requireSchoolAccess(actor, schoolId).bind()
                RoomDetails(loadRoomOfSchool(rooms, schoolId, roomId), roomFeatures.findBySchool(schoolId).sortedBy { it.name.lowercase() })
            }
        }
}

class RoomUseCases(
    val createFeature: CreateRoomFeature,
    val deleteFeature: DeleteRoomFeature,
    val createRoom: CreateRoom,
    val updateRoom: UpdateRoom,
    val deleteRoom: DeleteRoom,
    val listRooms: ListRooms,
    val getRoom: GetRoom,
)
