package timetablescheduler.domain.room

import timetablescheduler.domain.common.RoomFeatureId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.validation.ValidationCollector
import timetablescheduler.domain.validation.ValidationResult
import timetablescheduler.domain.validation.requiredText

/** A property or equipment a room may offer, e.g. "Chemieraum" (FR-003). */
data class RoomFeature(
    val id: RoomFeatureId,
    val schoolId: SchoolId,
    val name: String,
) {
    companion object {
        const val FIELD_NAME = "name"
        const val NAME_MAX_LENGTH = 80

        fun create(
            schoolId: SchoolId,
            name: String,
        ): ValidationResult<RoomFeature> {
            val validation = ValidationCollector()
            val validName = validation.requiredText(FIELD_NAME, name, NAME_MAX_LENGTH)
            return validation.result { RoomFeature(RoomFeatureId.new(), schoolId, validName) }
        }
    }
}
