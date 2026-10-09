package timetablescheduler.domain.scheduling.constraint

import timetablescheduler.domain.common.SchoolClassId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.common.SubjectId
import timetablescheduler.domain.common.TeacherId
import timetablescheduler.domain.scheduling.solution.CourseKey
import java.util.UUID

@JvmInline
value class TeacherFixationId(val value: UUID) {
    companion object {
        fun new() = TeacherFixationId(UUID.randomUUID())
    }
}

/** The planner fixed who teaches a subject in a class — always a hard constraint (FR-013a). */
data class TeacherFixation(
    val id: TeacherFixationId,
    val schoolId: SchoolId,
    val classId: SchoolClassId,
    val subjectId: SubjectId,
    val teacherId: TeacherId,
    val version: Int,
) {
    val course: CourseKey get() = CourseKey(classId, subjectId)
}
