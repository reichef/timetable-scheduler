package timetablescheduler.domain.curriculum

import timetablescheduler.domain.common.RoomFeatureId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.validation.ValidationError
import timetablescheduler.domain.validation.ValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SubjectTest {
    private val schoolId = SchoolId.new()
    private val chemieraum = RoomFeatureId.new()
    private val schoolFeatures = setOf(chemieraum)

    @Test
    fun `subject keeps trimmed name and required features`() {
        val subject =
            assertIs<ValidationResult.Valid<Subject>>(
                Subject.create(schoolId, " Chemie ", setOf(chemieraum), schoolFeatures),
            ).value

        assertEquals("Chemie", subject.name)
        assertEquals(setOf(chemieraum), subject.requiredFeatureIds)
        assertEquals(0, subject.version)
    }

    @Test
    fun `name must have 1 to 80 characters`() {
        assertEquals(
            ValidationResult.Invalid(listOf(ValidationError.BlankValue(Subject.FIELD_NAME))),
            Subject.create(schoolId, " ", emptySet(), schoolFeatures),
        )
        assertEquals(
            ValidationResult.Invalid(listOf(ValidationError.TooLong(Subject.FIELD_NAME, 80))),
            Subject.create(schoolId, "x".repeat(81), emptySet(), schoolFeatures),
        )
    }

    @Test
    fun `required features must belong to the same school`() {
        val foreignFeature = RoomFeatureId.new()

        assertEquals(
            ValidationResult.Invalid(listOf(ValidationError.InvalidReference(Subject.FIELD_FEATURES))),
            Subject.create(schoolId, "Chemie", setOf(foreignFeature), schoolFeatures),
        )
    }

    @Test
    fun `update keeps identity and version`() {
        val subject = (Subject.create(schoolId, "Chemie", emptySet(), schoolFeatures) as ValidationResult.Valid).value

        val updated = assertIs<ValidationResult.Valid<Subject>>(subject.update("Chemie LK", setOf(chemieraum), schoolFeatures)).value

        assertEquals(subject.id, updated.id)
        assertEquals("Chemie LK", updated.name)
        assertEquals(setOf(chemieraum), updated.requiredFeatureIds)
    }
}
