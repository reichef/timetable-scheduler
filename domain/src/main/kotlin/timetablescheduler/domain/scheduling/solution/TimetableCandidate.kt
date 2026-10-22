package timetablescheduler.domain.scheduling.solution

import timetablescheduler.domain.common.RoomId
import timetablescheduler.domain.common.SchoolClassId
import timetablescheduler.domain.common.SubjectId
import timetablescheduler.domain.common.TeacherId
import timetablescheduler.domain.scheduling.DaySlot

/** A subject taught to one class; all its periods share one teacher (FR-004). */
data class CourseKey(
    val classId: SchoolClassId,
    val subjectId: SubjectId,
)

/** One lesson of a timetable: which class learns which subject when, with whom and where. */
data class LessonAssignment(
    val classId: SchoolClassId,
    val subjectId: SubjectId,
    val teacherId: TeacherId,
    val roomId: RoomId,
    val daySlot: DaySlot,
) {
    val course: CourseKey get() = CourseKey(classId, subjectId)
}

/**
 * A complete timetable proposal for all classes: the placed lessons and, per course, the periods that could not be
 * placed without violating a hard constraint (FR-005).
 */
data class TimetableCandidate(
    val lessons: List<LessonAssignment>,
    val unplaced: Map<CourseKey, Int>,
) {
    /** Same timetable ⇔ same lessons (in any order) and the same unplaced periods. */
    fun sameTimetableAs(other: TimetableCandidate): Boolean =
        lessons.toSet() == other.lessons.toSet() && unplaced.filterValues { it > 0 } == other.unplaced.filterValues { it > 0 }

    fun unplacedPeriods(): Int = unplaced.values.sum()
}
