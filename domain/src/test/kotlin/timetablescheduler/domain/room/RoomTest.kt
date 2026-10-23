package timetablescheduler.domain.room

import timetablescheduler.domain.common.RoomFeatureId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.validation.ValidationError
import timetablescheduler.domain.validation.ValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RoomTest {
    private val school = SchoolId.new()
    private val chemistryLab = (RoomFeature.create(school, "Chemieraum") as ValidationResult.Valid).value

    @Test
    fun `creates room with trimmed identifier and features of the same school`() {
        val result = Room.create(school, " B-104 ", setOf(chemistryLab.id), schoolFeatures = setOf(chemistryLab.id))

        val room = assertIs<ValidationResult.Valid<Room>>(result).value
        assertEquals("B-104", room.identifier)
        assertEquals(setOf(chemistryLab.id), room.featureIds)
    }

    @Test
    fun `room identifier must not be blank or longer than 40 characters`() {
        val blank = Room.create(school, "  ", emptySet(), emptySet())
        val tooLong = Room.create(school, "x".repeat(41), emptySet(), emptySet())

        assertTrue(ValidationError.BlankValue(Room.FIELD_IDENTIFIER) in assertIs<ValidationResult.Invalid>(blank).errors)
        assertTrue(ValidationError.TooLong(Room.FIELD_IDENTIFIER, 40) in assertIs<ValidationResult.Invalid>(tooLong).errors)
    }

    @Test
    fun `features of another school are rejected`() {
        val foreignFeature = RoomFeatureId.new()

        val result = Room.create(school, "B-104", setOf(foreignFeature), schoolFeatures = setOf(chemistryLab.id))

        assertTrue(ValidationError.InvalidReference(Room.FIELD_FEATURES) in assertIs<ValidationResult.Invalid>(result).errors)
    }

    @Test
    fun `update keeps identity and version`() {
        val room = (Room.create(school, "B-104", emptySet(), emptySet()) as ValidationResult.Valid).value

        val updated = assertIs<ValidationResult.Valid<Room>>(room.update("B-105", setOf(chemistryLab.id), setOf(chemistryLab.id))).value

        assertEquals(room.id, updated.id)
        assertEquals(room.version, updated.version)
        assertEquals("B-105", updated.identifier)
    }

    @Test
    fun `room feature name must not be blank or longer than 80 characters`() {
        val blank = RoomFeature.create(school, " ")
        val tooLong = RoomFeature.create(school, "x".repeat(81))

        assertTrue(ValidationError.BlankValue(RoomFeature.FIELD_NAME) in assertIs<ValidationResult.Invalid>(blank).errors)
        assertTrue(ValidationError.TooLong(RoomFeature.FIELD_NAME, 80) in assertIs<ValidationResult.Invalid>(tooLong).errors)
    }
}
