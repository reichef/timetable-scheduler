package timetablescheduler.domain.curriculum

import timetablescheduler.domain.common.ClassLevelId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.common.SubjectId
import timetablescheduler.domain.validation.ValidationCollector
import timetablescheduler.domain.validation.ValidationError
import timetablescheduler.domain.validation.ValidationResult
import timetablescheduler.domain.validation.intInRange
import timetablescheduler.domain.validation.requiredText

/** One subject of a level's curriculum with its weekly periods (FR-011). */
data class CurriculumEntry(val subjectId: SubjectId, val periodsPerWeek: Int) {
    companion object {
        const val FIELD_PERIODS = "periodsPerWeek"
        const val MIN_PERIODS = 1
    }
}

/**
 * A grade such as "6" with the curriculum every class of the level receives (FR-010, FR-011, FR-014).
 * Identifier uniqueness is checked by the use case.
 */
data class ClassLevel(
    val id: ClassLevelId,
    val schoolId: SchoolId,
    val identifier: String,
    val curriculum: List<CurriculumEntry>,
    val version: Int,
) {
    fun weeklyPeriods(): Int = curriculum.sumOf { it.periodsPerWeek }

    /** [schoolSubjects] are the subjects of this level's school; others are rejected. */
    fun update(newIdentifier: String, newCurriculum: List<CurriculumEntry>, schoolSubjects: Set<SubjectId>): ValidationResult<ClassLevel> {
        val validation = ValidationCollector()
        val validIdentifier = validation.requiredText(FIELD_IDENTIFIER, newIdentifier, IDENTIFIER_MAX_LENGTH)
        validateCurriculum(validation, newCurriculum, schoolSubjects)
        return validation.result { copy(identifier = validIdentifier, curriculum = newCurriculum) }
    }

    /** Each entry needs ≥ 1 period, each subject may appear once and must belong to the school; each rule is reported once. */
    private fun validateCurriculum(validation: ValidationCollector, entries: List<CurriculumEntry>, schoolSubjects: Set<SubjectId>) {
        entries.firstOrNull { it.periodsPerWeek < CurriculumEntry.MIN_PERIODS }
            ?.let { validation.intInRange(CurriculumEntry.FIELD_PERIODS, it.periodsPerWeek, CurriculumEntry.MIN_PERIODS, null) }
        if (entries.map { it.subjectId }.toSet().size != entries.size) validation.add(ValidationError.DuplicateValue(FIELD_SUBJECTS))
        if (entries.any { it.subjectId !in schoolSubjects }) validation.add(ValidationError.InvalidReference(FIELD_SUBJECTS))
    }

    companion object {
        const val FIELD_IDENTIFIER = "identifier"
        const val FIELD_SUBJECTS = "subjects"
        const val IDENTIFIER_MAX_LENGTH = 10

        fun create(schoolId: SchoolId, identifier: String): ValidationResult<ClassLevel> =
            ClassLevel(ClassLevelId.new(), schoolId, identifier = "", curriculum = emptyList(), version = 0)
                .update(identifier, emptyList(), emptySet())
    }
}
