package timetablescheduler.application.port

import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.school.School
import timetablescheduler.domain.school.TimeGrid

interface SchoolRepository {
    fun insert(school: School)

    /** Saves [school] if its stored version still equals `school.version`; returns false otherwise. */
    fun update(school: School): Boolean

    fun findById(schoolId: SchoolId): School?

    fun findByIds(schoolIds: Set<SchoolId>): List<School>

    fun findByNameIgnoreCase(name: String): School?

    fun findAllIds(): List<SchoolId>

    fun findTimeGrid(schoolId: SchoolId): TimeGrid

    /** Stores teaching days, slots and exclusions; existing slots keep their identity. */
    fun saveTimeGrid(schoolId: SchoolId, timeGrid: TimeGrid)
}
