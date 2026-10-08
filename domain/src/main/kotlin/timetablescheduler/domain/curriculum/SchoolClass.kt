package timetablescheduler.domain.curriculum

import timetablescheduler.domain.common.ClassLevelId
import timetablescheduler.domain.common.SchoolClassId
import timetablescheduler.domain.validation.ValidationCollector
import timetablescheduler.domain.validation.ValidationResult
import timetablescheduler.domain.validation.intInRange
import timetablescheduler.domain.validation.requiredText

/**
 * A group of students of one class level, named level identifier + suffix, e.g. "6b" (FR-012). The priority
 * weight tells the scheduler how important filling this class's timetable is (FR-015). Its curriculum is not
 * stored but resolved from the level by [ClassCurriculumResolver]. Suffix uniqueness is checked by the use case.
 */
data class SchoolClass(
    val id: SchoolClassId,
    val classLevelId: ClassLevelId,
    val suffix: String,
    val priorityWeight: Int,
    val version: Int,
) {
    fun displayName(level: ClassLevel): String = level.identifier + suffix

    fun update(newSuffix: String, newPriorityWeight: Int): ValidationResult<SchoolClass> {
        val validation = ValidationCollector()
        val validSuffix = validation.requiredText(FIELD_SUFFIX, newSuffix, SUFFIX_MAX_LENGTH)
        validation.intInRange(FIELD_PRIORITY_WEIGHT, newPriorityWeight, MIN_PRIORITY_WEIGHT, MAX_PRIORITY_WEIGHT)
        return validation.result { copy(suffix = validSuffix, priorityWeight = newPriorityWeight) }
    }

    companion object {
        const val FIELD_SUFFIX = "suffix"
        const val FIELD_PRIORITY_WEIGHT = "priorityWeight"
        const val SUFFIX_MAX_LENGTH = 5
        const val MIN_PRIORITY_WEIGHT = 1
        const val MAX_PRIORITY_WEIGHT = 100

        /** Equal weight for all classes unless the planner changes it (FR-015). */
        const val DEFAULT_PRIORITY_WEIGHT = 10

        fun create(classLevelId: ClassLevelId, suffix: String, priorityWeight: Int = DEFAULT_PRIORITY_WEIGHT): ValidationResult<SchoolClass> =
            SchoolClass(SchoolClassId.new(), classLevelId, suffix = "", priorityWeight = DEFAULT_PRIORITY_WEIGHT, version = 0)
                .update(suffix, priorityWeight)
    }
}
