package timetablescheduler.domain.curriculum

import timetablescheduler.domain.common.RoomId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.validation.ValidationError
import timetablescheduler.domain.validation.ValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class SchoolClassTest {

    private val level6 = (ClassLevel.create(SchoolId.new(), "6") as ValidationResult.Valid).value

    @Test
    fun `class is named by level identifier and suffix`() {
        val schoolClass = assertIs<ValidationResult.Valid<SchoolClass>>(SchoolClass.create(level6.id, " b ")).value

        assertEquals("b", schoolClass.suffix)
        assertEquals("6b", schoolClass.displayName(level6))
    }

    @Test
    fun `priority weight defaults to 10`() {
        val schoolClass = (SchoolClass.create(level6.id, "a") as ValidationResult.Valid).value

        assertEquals(10, schoolClass.priorityWeight)
        assertEquals(SchoolClass.DEFAULT_PRIORITY_WEIGHT, schoolClass.priorityWeight)
    }

    @Test
    fun `suffix must have 1 to 5 characters`() {
        assertEquals(ValidationResult.Invalid(listOf(ValidationError.BlankValue(SchoolClass.FIELD_SUFFIX))), SchoolClass.create(level6.id, ""))
        assertEquals(
            ValidationResult.Invalid(listOf(ValidationError.TooLong(SchoolClass.FIELD_SUFFIX, 5))),
            SchoolClass.create(level6.id, "abcdef"),
        )
    }

    @Test
    fun `priority weight must be between 1 and 100`() {
        val outOfRange = ValidationResult.Invalid(listOf(ValidationError.OutOfRange(SchoolClass.FIELD_PRIORITY_WEIGHT, 1, 100)))

        assertEquals(outOfRange, SchoolClass.create(level6.id, "a", priorityWeight = 0))
        assertEquals(outOfRange, SchoolClass.create(level6.id, "a", priorityWeight = 101))
        assertIs<ValidationResult.Valid<SchoolClass>>(SchoolClass.create(level6.id, "a", priorityWeight = 100))
    }

    @Test
    fun `update changes suffix and weight but keeps the level`() {
        val schoolClass = (SchoolClass.create(level6.id, "a") as ValidationResult.Valid).value

        val updated = assertIs<ValidationResult.Valid<SchoolClass>>(schoolClass.update("c", 25)).value

        assertEquals(level6.id, updated.classLevelId)
        assertEquals("c", updated.suffix)
        assertEquals(25, updated.priorityWeight)
    }

    @Test
    fun `a class has no home room until one is set (FR-002a)`() {
        val schoolClass = (SchoolClass.create(level6.id, "a") as ValidationResult.Valid).value

        assertNull(schoolClass.homeRoomId)
    }

    @Test
    fun `setting and clearing the home room keeps all other fields`() {
        val schoolClass = (SchoolClass.create(level6.id, "a", priorityWeight = 20) as ValidationResult.Valid).value
        val room = RoomId.new()

        val withRoom = schoolClass.withHomeRoom(room)

        assertEquals(schoolClass.copy(homeRoomId = room), withRoom)
        assertEquals(schoolClass, withRoom.withHomeRoom(null))
    }
}
