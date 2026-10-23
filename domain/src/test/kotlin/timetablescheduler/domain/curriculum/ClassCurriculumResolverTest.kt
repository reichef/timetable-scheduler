package timetablescheduler.domain.curriculum

import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.common.SubjectId
import timetablescheduler.domain.validation.ValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ClassCurriculumResolverTest {
    private val mathe = SubjectId.new()
    private val deutsch = SubjectId.new()
    private val schoolSubjects = setOf(mathe, deutsch)
    private val level6 =
        (ClassLevel.create(SchoolId.new(), "6") as ValidationResult.Valid)
            .value
            .update("6", listOf(CurriculumEntry(mathe, 4), CurriculumEntry(deutsch, 4)), schoolSubjects)
            .let { (it as ValidationResult.Valid).value }
    private val class6a = (SchoolClass.create(level6.id, "a") as ValidationResult.Valid).value

    @Test
    fun `class receives the curriculum of its level`() {
        assertEquals(level6.curriculum, ClassCurriculumResolver.curriculumOf(class6a, level6))
    }

    @Test
    fun `later changes of the level are reflected`() {
        val changedLevel = (level6.update("6", listOf(CurriculumEntry(mathe, 5)), schoolSubjects) as ValidationResult.Valid).value

        assertEquals(listOf(CurriculumEntry(mathe, 5)), ClassCurriculumResolver.curriculumOf(class6a, changedLevel))
    }

    @Test
    fun `level of another class is rejected`() {
        val otherLevel = (ClassLevel.create(SchoolId.new(), "7") as ValidationResult.Valid).value

        assertFailsWith<IllegalArgumentException> { ClassCurriculumResolver.curriculumOf(class6a, otherLevel) }
    }
}
