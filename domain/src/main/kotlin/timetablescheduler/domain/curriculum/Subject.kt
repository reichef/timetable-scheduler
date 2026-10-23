package timetablescheduler.domain.curriculum

import timetablescheduler.domain.common.RoomFeatureId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.common.SubjectId
import timetablescheduler.domain.validation.ValidationCollector
import timetablescheduler.domain.validation.ValidationError
import timetablescheduler.domain.validation.ValidationResult
import timetablescheduler.domain.validation.requiredText

/**
 * Something that is taught (FR-008). A lesson of the subject needs a room offering **all** required features —
 * a hard constraint for scheduling (FR-009, FR-022). Name uniqueness is checked by the use case.
 */
data class Subject(
    val id: SubjectId,
    val schoolId: SchoolId,
    val name: String,
    val requiredFeatureIds: Set<RoomFeatureId>,
    val version: Int,
) {
    /** [schoolFeatures] are the room features of this subject's school; others are rejected. */
    fun update(
        newName: String,
        newRequiredFeatureIds: Set<RoomFeatureId>,
        schoolFeatures: Set<RoomFeatureId>,
    ): ValidationResult<Subject> {
        val validation = ValidationCollector()
        val validName = validation.requiredText(FIELD_NAME, newName, NAME_MAX_LENGTH)
        if (!schoolFeatures.containsAll(newRequiredFeatureIds)) {
            validation.add(ValidationError.InvalidReference(FIELD_FEATURES))
        }
        return validation.result { copy(name = validName, requiredFeatureIds = newRequiredFeatureIds) }
    }

    companion object {
        const val FIELD_NAME = "name"
        const val FIELD_FEATURES = "features"
        const val NAME_MAX_LENGTH = 80

        fun create(
            schoolId: SchoolId,
            name: String,
            requiredFeatureIds: Set<RoomFeatureId>,
            schoolFeatures: Set<RoomFeatureId>,
        ): ValidationResult<Subject> =
            Subject(SubjectId.new(), schoolId, name = "", requiredFeatureIds = emptySet(), version = 0)
                .update(name, requiredFeatureIds, schoolFeatures)
    }
}
