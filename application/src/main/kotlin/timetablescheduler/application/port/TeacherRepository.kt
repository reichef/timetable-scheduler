package timetablescheduler.application.port

import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.common.SubjectId
import timetablescheduler.domain.common.TeacherId
import timetablescheduler.domain.teacher.Teacher

interface TeacherRepository {
    fun insert(teacher: Teacher)

    /** Saves [teacher] with qualifications and unavailability if its stored version still equals `teacher.version`. */
    fun update(teacher: Teacher): Boolean

    /** Deletes the teacher together with qualifications and unavailability. */
    fun delete(teacherId: TeacherId)

    fun findById(teacherId: TeacherId): Teacher?

    fun findBySchool(schoolId: SchoolId): List<Teacher>

    /** Short codes are unique in the installation, compared case-insensitively (FR-016). */
    fun findByShortCode(shortCode: String): Teacher?

    fun findQualifiedFor(subjectId: SubjectId): List<Teacher>
}
