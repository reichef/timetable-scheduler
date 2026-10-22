package timetablescheduler.application.port

import timetablescheduler.domain.common.SchoolClassId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.common.SubjectId
import timetablescheduler.domain.common.TeacherId
import timetablescheduler.domain.common.TimeSlotId
import timetablescheduler.domain.scheduling.constraint.SchedulingConstraint
import timetablescheduler.domain.scheduling.constraint.SchedulingConstraintId
import timetablescheduler.domain.scheduling.constraint.TeacherFixation
import timetablescheduler.domain.scheduling.constraint.TeacherFixationId

/** Planner-defined scheduling constraints (002 FR-012). */
interface SchedulingConstraintRepository {
    fun insert(constraint: SchedulingConstraint)

    /** Saves [constraint] if its stored version still equals `constraint.version`; returns false otherwise. */
    fun update(constraint: SchedulingConstraint): Boolean

    fun delete(constraintId: SchedulingConstraintId)

    fun findById(constraintId: SchedulingConstraintId): SchedulingConstraint?

    fun findBySchool(schoolId: SchoolId): List<SchedulingConstraint>

    /** Constraints referring to the entry; they block its deletion (002 FR-016). */
    fun findReferencingClass(classId: SchoolClassId): List<SchedulingConstraint>

    fun findReferencingTeacher(teacherId: TeacherId): List<SchedulingConstraint>

    fun findReferencingSubject(subjectId: SubjectId): List<SchedulingConstraint>

    fun findReferencingSlot(slotId: TimeSlotId): List<SchedulingConstraint>
}

/** Teacher fixations (002 FR-013a); at most one per class and subject. */
interface TeacherFixationRepository {
    fun insert(fixation: TeacherFixation)

    /** Saves [fixation] if its stored version still equals `fixation.version`; returns false otherwise. */
    fun update(fixation: TeacherFixation): Boolean

    fun delete(fixationId: TeacherFixationId)

    fun findById(fixationId: TeacherFixationId): TeacherFixation?

    fun findBySchool(schoolId: SchoolId): List<TeacherFixation>

    fun findByCourse(
        classId: SchoolClassId,
        subjectId: SubjectId,
    ): TeacherFixation?

    fun findByClass(classId: SchoolClassId): List<TeacherFixation>

    fun findByTeacher(teacherId: TeacherId): List<TeacherFixation>

    fun findBySubject(subjectId: SubjectId): List<TeacherFixation>
}
