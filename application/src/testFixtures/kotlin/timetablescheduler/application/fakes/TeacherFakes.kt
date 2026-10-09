package timetablescheduler.application.fakes

import timetablescheduler.application.port.TeacherRepository
import timetablescheduler.application.port.TimeSlotUsage
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.common.SubjectId
import timetablescheduler.domain.common.TeacherId
import timetablescheduler.domain.common.TimeSlotId
import timetablescheduler.domain.teacher.Teacher

class InMemoryTeacherRepository : VersionedInMemoryStore<TeacherId, Teacher>(
    keyOf = { it.id },
    versionOf = { it.version },
    withVersion = { teacher, version -> teacher.copy(version = version) },
), TeacherRepository {
    override fun insert(teacher: Teacher) = super.insert(teacher)
    override fun update(teacher: Teacher): Boolean = super.update(teacher)

    override fun delete(teacherId: TeacherId) {
        entries.remove(teacherId)
    }

    override fun findById(teacherId: TeacherId): Teacher? = entries[teacherId]
    override fun findBySchool(schoolId: SchoolId): List<Teacher> = entries.values.filter { it.schoolId == schoolId }
    override fun findByShortCode(shortCode: String): Teacher? =
        entries.values.firstOrNull { it.shortCode.equals(shortCode.trim(), ignoreCase = true) }
    override fun findQualifiedFor(subjectId: SubjectId): List<Teacher> = entries.values.filter { subjectId in it.qualifiedSubjectIds }

    /** Like the database: removing a slot's unavailabilities does not change the teachers' versions. */
    fun removeUnavailabilitiesOf(slotId: TimeSlotId) {
        entries.replaceAll { _, teacher -> teacher.copy(unavailability = teacher.unavailability.filterNot { it.slotId == slotId }.toSet()) }
    }
}

/** Counts and removes the unavailabilities stored in the fake teacher repository. */
class InMemoryTimeSlotUsage(private val teachers: InMemoryTeacherRepository) : TimeSlotUsage {
    override fun countUnavailabilities(slotId: TimeSlotId): Int = teachers.all().sumOf { teacher -> teacher.unavailability.count { it.slotId == slotId } }

    override fun deleteUnavailabilities(slotId: TimeSlotId) = teachers.removeUnavailabilitiesOf(slotId)
}
