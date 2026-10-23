package timetablescheduler.domain.curriculum

import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.common.SubjectId
import timetablescheduler.domain.validation.ValidationError
import timetablescheduler.domain.validation.ValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ClassLevelTest {
    private val schoolId = SchoolId.new()
    private val mathe = SubjectId.new()
    private val deutsch = SubjectId.new()
    private val schoolSubjects = setOf(mathe, deutsch)
    private val level6 = (ClassLevel.create(schoolId, " 6 ") as ValidationResult.Valid).value

    @Test
    fun `new level has a trimmed identifier and an empty curriculum`() {
        assertEquals("6", level6.identifier)
        assertEquals(emptyList(), level6.curriculum)
        assertEquals(0, level6.weeklyPeriods())
    }

    @Test
    fun `identifier must have 1 to 10 characters`() {
        assertEquals(
            ValidationResult.Invalid(listOf(ValidationError.BlankValue(ClassLevel.FIELD_IDENTIFIER))),
            ClassLevel.create(schoolId, ""),
        )
        assertEquals(
            ValidationResult.Invalid(listOf(ValidationError.TooLong(ClassLevel.FIELD_IDENTIFIER, 10))),
            ClassLevel.create(schoolId, "Oberstufe-1"),
        )
    }

    @Test
    fun `weekly periods are the sum of the curriculum`() {
        val curriculum = listOf(CurriculumEntry(mathe, 4), CurriculumEntry(deutsch, 4))

        val updated = assertIs<ValidationResult.Valid<ClassLevel>>(level6.update("6", curriculum, schoolSubjects)).value

        assertEquals(8, updated.weeklyPeriods())
        assertEquals(curriculum, updated.curriculum)
    }

    @Test
    fun `periods per week must be at least 1`() {
        assertEquals(
            ValidationResult.Invalid(listOf(ValidationError.OutOfRange(CurriculumEntry.FIELD_PERIODS, 1, null))),
            level6.update("6", listOf(CurriculumEntry(mathe, 0)), schoolSubjects),
        )
    }

    @Test
    fun `a subject appears at most once`() {
        assertEquals(
            ValidationResult.Invalid(listOf(ValidationError.DuplicateValue(ClassLevel.FIELD_SUBJECTS))),
            level6.update("6", listOf(CurriculumEntry(mathe, 4), CurriculumEntry(mathe, 2)), schoolSubjects),
        )
    }

    @Test
    fun `curriculum subjects must belong to the same school`() {
        assertEquals(
            ValidationResult.Invalid(listOf(ValidationError.InvalidReference(ClassLevel.FIELD_SUBJECTS))),
            level6.update("6", listOf(CurriculumEntry(SubjectId.new(), 2)), schoolSubjects),
        )
    }

    @Test
    fun `all violations are reported together`() {
        val result = level6.update(" ", listOf(CurriculumEntry(mathe, 0), CurriculumEntry(mathe, 1)), schoolSubjects)

        assertEquals(3, assertIs<ValidationResult.Invalid>(result).errors.size)
    }
}
