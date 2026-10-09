package timetablescheduler.domain.readiness

import timetablescheduler.domain.common.ClassLevelId
import timetablescheduler.domain.common.RoomFeatureId
import timetablescheduler.domain.common.SubjectId
import timetablescheduler.domain.common.TeacherId
import timetablescheduler.domain.scheduling.constraint.FixationInvalidity
import timetablescheduler.domain.scheduling.constraint.TeacherFixationId

/**
 * A gap or inconsistency that would prevent classes from being fully scheduled (FR-024). Issues are warnings:
 * they never block saving (FR-025) and are computed on demand, never persisted.
 */
sealed interface ReadinessIssue {
    /** `SUBJECT_NO_SUITABLE_ROOM`: no room offers all required features; [missingFeatureIds] are offered by no room at all. */
    data class SubjectNoSuitableRoom(val subjectId: SubjectId, val missingFeatureIds: Set<RoomFeatureId>) : ReadinessIssue

    /** `SUBJECT_NO_QUALIFIED_TEACHER` */
    data class SubjectNoQualifiedTeacher(val subjectId: SubjectId) : ReadinessIssue

    /** `SUBJECT_TEACHER_CAPACITY_SHORTFALL` */
    data class SubjectTeacherCapacityShortfall(val subjectId: SubjectId, val shortfallPeriods: Int) : ReadinessIssue

    /** `LEVEL_EXCEEDS_GRID` */
    data class LevelExceedsGrid(val classLevelId: ClassLevelId, val excessPeriods: Int) : ReadinessIssue

    /** `TEACHER_WITHOUT_QUALIFICATION` */
    data class TeacherWithoutQualification(val teacherId: TeacherId) : ReadinessIssue

    /** `TEACHER_ZERO_LOAD` */
    data class TeacherZeroLoad(val teacherId: TeacherId) : ReadinessIssue

    /** `TEACHER_LOAD_EXCEEDS_GRID` */
    data class TeacherLoadExceedsGrid(val teacherId: TeacherId, val excessPeriods: Int) : ReadinessIssue

    /** `INVALID_TEACHER_FIXATION` (002 FR-013a): a fixation no longer fits the master data; blocks generations. */
    data class InvalidTeacherFixation(val fixationId: TeacherFixationId, val reason: FixationInvalidity) : ReadinessIssue
}
