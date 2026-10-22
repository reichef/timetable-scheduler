package timetablescheduler.domain.teacher

import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.common.SubjectId
import timetablescheduler.domain.common.TeacherId
import timetablescheduler.domain.school.TimeGrid
import timetablescheduler.domain.validation.ValidationCollector
import timetablescheduler.domain.validation.ValidationError
import timetablescheduler.domain.validation.ValidationResult
import timetablescheduler.domain.validation.intInRange
import timetablescheduler.domain.validation.requiredText

/**
 * A person who teaches at the school (FR-016). Qualifications, the weekly load as upper limit and the
 * unavailability are hard constraints for scheduling (FR-022). Short-code uniqueness is checked by the use case.
 */
data class Teacher(
    val id: TeacherId,
    val schoolId: SchoolId,
    val name: String,
    val shortCode: String,
    val weeklyLoad: Int,
    val qualifiedSubjectIds: Set<SubjectId>,
    val unavailability: Set<Unavailability>,
    val version: Int,
) {
    val displayName: String get() = "$name ($shortCode)"

    /**
     * FR-018: the load may not exceed the school's [teachingSlotsPerWeek] at the time it is saved; a later
     * grid reduction is reported by the readiness check instead (see [loadExceedingGrid]).
     */
    fun withGeneralData(
        newName: String,
        newShortCode: String,
        newWeeklyLoad: Int,
        teachingSlotsPerWeek: Int,
    ): ValidationResult<Teacher> {
        val validation = ValidationCollector()
        val validName = validation.requiredText(FIELD_NAME, newName, NAME_MAX_LENGTH)
        val normalizedShortCode = newShortCode.trim().uppercase()
        if (!SHORT_CODE_PATTERN.matches(normalizedShortCode)) {
            validation.add(ValidationError.InvalidFormat(FIELD_SHORT_CODE))
        }
        validation.intInRange(FIELD_WEEKLY_LOAD, newWeeklyLoad, 0, teachingSlotsPerWeek)
        return validation.result { copy(name = validName, shortCode = normalizedShortCode, weeklyLoad = newWeeklyLoad) }
    }

    /** FR-017: only subjects of the teacher's school ([schoolSubjects]) are accepted. */
    fun withQualifications(
        subjectIds: Set<SubjectId>,
        schoolSubjects: Set<SubjectId>,
    ): ValidationResult<Teacher> {
        val validation = ValidationCollector()
        if (!schoolSubjects.containsAll(subjectIds)) {
            validation.add(ValidationError.InvalidReference(FIELD_SUBJECTS))
        }
        return validation.result { copy(qualifiedSubjectIds = subjectIds) }
    }

    /**
     * FR-021: every [entered] entry must be a teaching slot that exists on that weekday in [grid]. Entries for
     * days that are no longer teaching days cannot be shown or entered any more; they are kept unchanged, as the
     * spec requires for grid reductions.
     */
    fun withUnavailability(
        entered: Set<Unavailability>,
        grid: TimeGrid,
    ): ValidationResult<Teacher> {
        val validation = ValidationCollector()
        val allExist = entered.all { entry -> grid.teachingSlotsOn(entry.dayOfWeek).any { it.id == entry.slotId } }
        if (!allExist) {
            validation.add(ValidationError.InvalidReference(FIELD_SLOT))
        }
        val keptForFormerDays = unavailability.filter { it.dayOfWeek !in grid.teachingDays }
        return validation.result { copy(unavailability = entered + keptForFormerDays) }
    }

    /** Periods by which the load exceeds the current grid, 0 if it fits (readiness `TEACHER_LOAD_EXCEEDS_GRID`). */
    fun loadExceedingGrid(teachingSlotsPerWeek: Int): Int = maxOf(0, weeklyLoad - teachingSlotsPerWeek)

    companion object {
        const val FIELD_NAME = "name"
        const val FIELD_SHORT_CODE = "shortCode"
        const val FIELD_WEEKLY_LOAD = "weeklyLoad"
        const val FIELD_SUBJECTS = "subjects"
        const val FIELD_SLOT = "slot"
        const val NAME_MAX_LENGTH = 120
        const val SHORT_CODE_MAX_LENGTH = 8

        /** 2–8 upper case letters or digits, e.g. "SCHA". */
        private val SHORT_CODE_PATTERN = Regex("[A-Z0-9]{2,$SHORT_CODE_MAX_LENGTH}")

        fun create(
            schoolId: SchoolId,
            name: String,
            shortCode: String,
            weeklyLoad: Int,
            teachingSlotsPerWeek: Int,
        ): ValidationResult<Teacher> =
            Teacher(TeacherId.new(), schoolId, name = "", shortCode = "", weeklyLoad = 0, emptySet(), emptySet(), version = 0)
                .withGeneralData(name, shortCode, weeklyLoad, teachingSlotsPerWeek)
    }
}
