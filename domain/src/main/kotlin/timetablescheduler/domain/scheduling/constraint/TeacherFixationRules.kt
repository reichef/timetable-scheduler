package timetablescheduler.domain.scheduling.constraint

import timetablescheduler.domain.common.SubjectId
import timetablescheduler.domain.scheduling.problem.SchedulingProblem
import timetablescheduler.domain.validation.ValidationError

/** Why a stored teacher fixation no longer fits the master data (readiness issue `INVALID_TEACHER_FIXATION`). */
enum class FixationInvalidity { NOT_IN_CURRICULUM, NOT_QUALIFIED, EXCEEDS_LOAD }

/**
 * FR-013a: a teacher can be fixed for a subject of a class if the subject is in the class's curriculum, the teacher is
 * qualified for it, and all periods fixed for the teacher together stay within the teacher's weekly load.
 */
object TeacherFixationRules {
    /** Checks a fixation before it is saved; other fixations of the same course are replaced and do not count. */
    fun validate(
        fixation: TeacherFixation,
        problem: SchedulingProblem,
    ): List<ValidationError> {
        val periods = problem.requiredPeriods(fixation.course).takeIf { it > 0 }
        val teacher = problem.teacher(fixation.teacherId)
        val otherFixed =
            problem.fixations
                .filter { it.teacherId == fixation.teacherId && it.id != fixation.id && it.course != fixation.course }
                .sumOf { problem.requiredPeriods(it.course) }
        return buildList {
            if (periods == null) {
                add(ValidationError.SubjectNotInCurriculum)
            }
            if (teacher == null || fixation.subjectId !in teacher.qualifiedSubjectIds) {
                add(ValidationError.TeacherNotQualified)
            }
            val total = otherFixed + (periods ?: 0)
            if (teacher != null && total > teacher.weeklyLoad) {
                add(ValidationError.FixedPeriodsExceedLoad(teacher.weeklyLoad, total))
            }
        }
    }

    /** Re-checks a stored fixation against the current data of [problem], which contains all stored fixations. */
    fun invalidityOf(
        fixation: TeacherFixation,
        problem: SchedulingProblem,
    ): FixationInvalidity? {
        val teacher = problem.teacher(fixation.teacherId)
        val fixedPeriods = problem.fixations.filter { it.teacherId == fixation.teacherId }.sumOf { problem.requiredPeriods(it.course) }
        return invalidityOf(
            periodsInCurriculum = problem.requiredPeriods(fixation.course).takeIf { it > 0 },
            subjectId = fixation.subjectId,
            qualifiedSubjects = teacher?.qualifiedSubjectIds.orEmpty(),
            weeklyLoad = teacher?.weeklyLoad ?: 0,
            fixedPeriodsOfTeacher = fixedPeriods,
        )
    }

    /** The rule itself on plain values, so the readiness check can apply it to master data directly. */
    fun invalidityOf(
        periodsInCurriculum: Int?,
        subjectId: SubjectId,
        qualifiedSubjects: Set<SubjectId>,
        weeklyLoad: Int,
        fixedPeriodsOfTeacher: Int,
    ): FixationInvalidity? =
        when {
            periodsInCurriculum == null -> FixationInvalidity.NOT_IN_CURRICULUM
            subjectId !in qualifiedSubjects -> FixationInvalidity.NOT_QUALIFIED
            fixedPeriodsOfTeacher > weeklyLoad -> FixationInvalidity.EXCEEDS_LOAD
            else -> null
        }
}
