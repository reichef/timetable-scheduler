package timetablescheduler.domain.school

import timetablescheduler.domain.validation.ValidationError
import timetablescheduler.domain.validation.ValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SchoolTest {
    @Test
    fun `creates school with trimmed name`() {
        val result = School.create("  Gymnasium Nord  ")

        val school = assertIs<ValidationResult.Valid<School>>(result).value
        assertEquals("Gymnasium Nord", school.name)
        assertEquals(0, school.version)
    }

    @Test
    fun `rejects blank school name`() {
        val result = School.create("   ")

        val errors = assertIs<ValidationResult.Invalid>(result).errors
        assertTrue(ValidationError.BlankValue(School.FIELD_NAME) in errors)
    }

    @Test
    fun `rejects school name longer than 120 characters`() {
        val result = School.create("x".repeat(121))

        val errors = assertIs<ValidationResult.Invalid>(result).errors
        assertTrue(ValidationError.TooLong(School.FIELD_NAME, 120) in errors)
    }

    @Test
    fun `accepts school name of exactly 120 characters`() {
        assertIs<ValidationResult.Valid<School>>(School.create("x".repeat(120)))
    }

    @Test
    fun `rename keeps identity and version`() {
        val school = (School.create("Alt") as ValidationResult.Valid).value

        val renamed = assertIs<ValidationResult.Valid<School>>(school.rename(" Neu ")).value

        assertEquals(school.id, renamed.id)
        assertEquals("Neu", renamed.name)
        assertEquals(school.version, renamed.version)
    }
}
