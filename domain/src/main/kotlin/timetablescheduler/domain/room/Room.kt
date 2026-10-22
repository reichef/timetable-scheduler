package timetablescheduler.domain.room

import timetablescheduler.domain.common.RoomFeatureId
import timetablescheduler.domain.common.RoomId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.validation.ValidationCollector
import timetablescheduler.domain.validation.ValidationError
import timetablescheduler.domain.validation.ValidationResult
import timetablescheduler.domain.validation.requiredText

/** A place within a school where lessons are held (FR-002); identifier uniqueness is checked by the use case. */
data class Room(
    val id: RoomId,
    val schoolId: SchoolId,
    val identifier: String,
    val featureIds: Set<RoomFeatureId>,
    val version: Int,
) {
    /** [schoolFeatures] are the features of this room's school; others are rejected (FR-003). */
    fun update(
        newIdentifier: String,
        newFeatureIds: Set<RoomFeatureId>,
        schoolFeatures: Set<RoomFeatureId>,
    ): ValidationResult<Room> {
        val validation = ValidationCollector()
        val validIdentifier = validation.requiredText(FIELD_IDENTIFIER, newIdentifier, IDENTIFIER_MAX_LENGTH)
        if (!schoolFeatures.containsAll(newFeatureIds)) {
            validation.add(ValidationError.InvalidReference(FIELD_FEATURES))
        }
        return validation.result { copy(identifier = validIdentifier, featureIds = newFeatureIds) }
    }

    fun offersAll(requiredFeatures: Set<RoomFeatureId>): Boolean = featureIds.containsAll(requiredFeatures)

    companion object {
        const val FIELD_IDENTIFIER = "identifier"
        const val FIELD_FEATURES = "features"
        const val IDENTIFIER_MAX_LENGTH = 40

        fun create(
            schoolId: SchoolId,
            identifier: String,
            featureIds: Set<RoomFeatureId>,
            schoolFeatures: Set<RoomFeatureId>,
        ): ValidationResult<Room> =
            Room(RoomId.new(), schoolId, identifier = "", featureIds = emptySet(), version = 0)
                .update(identifier, featureIds, schoolFeatures)
    }
}
