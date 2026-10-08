package timetablescheduler.domain.school

import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.validation.ValidationCollector
import timetablescheduler.domain.validation.ValidationResult
import timetablescheduler.domain.validation.requiredText

/** A location at which lessons take place (FR-001). Name uniqueness is checked by the use case. */
data class School(
    val id: SchoolId,
    val name: String,
    val version: Int,
) {
    fun rename(newName: String): ValidationResult<School> {
        val validation = ValidationCollector()
        val validName = validation.requiredText(FIELD_NAME, newName, NAME_MAX_LENGTH)
        return validation.result { copy(name = validName) }
    }

    companion object {
        const val FIELD_NAME = "name"
        const val NAME_MAX_LENGTH = 120

        fun create(name: String): ValidationResult<School> =
            School(SchoolId.new(), name = "", version = 0).rename(name)
    }
}
