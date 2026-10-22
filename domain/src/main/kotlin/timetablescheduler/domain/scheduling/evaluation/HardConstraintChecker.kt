package timetablescheduler.domain.scheduling.evaluation

import timetablescheduler.domain.scheduling.constraint.EffectiveConstraints
import timetablescheduler.domain.scheduling.constraint.RuleViolations
import timetablescheduler.domain.scheduling.constraint.SchedulingConstraintId
import timetablescheduler.domain.scheduling.problem.SchedulingProblem
import timetablescheduler.domain.scheduling.solution.LessonAssignment
import timetablescheduler.domain.scheduling.solution.TimetableCandidate

/** The hard rules of a timetable (FR-003, FR-004, FR-002a, FR-013a). */
enum class HardRule {
    TEACHER_DOUBLE_BOOKED,
    CLASS_DOUBLE_BOOKED,
    ROOM_DOUBLE_BOOKED,
    TEACHER_NOT_QUALIFIED,
    ROOM_LACKS_FEATURE,
    OUTSIDE_HOME_ROOM,
    NOT_A_TEACHING_SLOT,
    TEACHER_LOAD_EXCEEDED,
    TEACHER_UNAVAILABLE,
    SEVERAL_TEACHERS_FOR_COURSE,
    LESSON_NOT_REQUIRED,
    TEACHER_FIXATION,
    PLANNER_CONSTRAINT,
}

/** A violated hard rule and the lessons that violate it; [constraintId] names the planner constraint if any. */
data class HardViolation(
    val rule: HardRule,
    val lessons: List<LessonAssignment>,
    val constraintId: SchedulingConstraintId? = null,
)

/**
 * The reference check of every hard constraint (G4): every alternative is checked before it is kept, independently
 * of the solver, and outdated alternatives are checked against current data to highlight conflicts (FR-023).
 */
object HardConstraintChecker {

    fun violationsOf(problem: SchedulingProblem, candidate: TimetableCandidate): List<HardViolation> {
        val lessons = candidate.lessons
        return doubleBookings(lessons) +
            lessons.violating(HardRule.TEACHER_NOT_QUALIFIED) { !isQualified(problem, it) } +
            lessons.violating(HardRule.ROOM_LACKS_FEATURE) { !roomOffersRequiredFeatures(problem, it) } +
            lessons.violating(HardRule.OUTSIDE_HOME_ROOM) { isOutsideHomeRoom(problem, it) } +
            lessons.violating(HardRule.NOT_A_TEACHING_SLOT) { !problem.isTeachingSlot(it.daySlot) } +
            loadExceeded(problem, lessons) +
            lessons.violating(HardRule.TEACHER_UNAVAILABLE) { problem.teacher(it.teacherId)?.unavailable?.contains(it.daySlot) == true } +
            severalTeachersPerCourse(lessons) +
            lessonsNotRequired(problem, lessons) +
            fixationViolations(problem, lessons) +
            plannerConstraintViolations(problem, lessons)
    }

    /** FR-013a: every lesson of a fixed course is taught by the fixed teacher. */
    private fun fixationViolations(problem: SchedulingProblem, lessons: List<LessonAssignment>): List<HardViolation> =
        problem.fixations.mapNotNull { fixation ->
            val others = lessons.filter { it.course == fixation.course && it.teacherId != fixation.teacherId }
            if (others.isEmpty()) null else HardViolation(HardRule.TEACHER_FIXATION, others)
        }

    /** Active hard planner constraints (FR-012), counted per class or target by the catalogue rules (G9). */
    private fun plannerConstraintViolations(problem: SchedulingProblem, lessons: List<LessonAssignment>): List<HardViolation> =
        EffectiveConstraints.resolve(problem)
            .filter { it.constraint.isHard }
            .mapNotNull { applied ->
                val violation = RuleViolations.of(applied, problem, lessons)
                if (violation.count == 0) null else HardViolation(HardRule.PLANNER_CONSTRAINT, violation.lessons, applied.constraint.id)
            }

    private fun List<LessonAssignment>.violating(rule: HardRule, isViolation: (LessonAssignment) -> Boolean): List<HardViolation> =
        filter(isViolation).map { HardViolation(rule, listOf(it)) }

    /** A teacher, class or room may hold only one lesson per day-slot; all lessons of a clash are reported together. */
    private fun doubleBookings(lessons: List<LessonAssignment>): List<HardViolation> =
        clashes(lessons, HardRule.TEACHER_DOUBLE_BOOKED) { it.teacherId to it.daySlot } +
            clashes(lessons, HardRule.CLASS_DOUBLE_BOOKED) { it.classId to it.daySlot } +
            clashes(lessons, HardRule.ROOM_DOUBLE_BOOKED) { it.roomId to it.daySlot }

    private fun clashes(lessons: List<LessonAssignment>, rule: HardRule, resourceSlot: (LessonAssignment) -> Any): List<HardViolation> =
        lessons.groupBy(resourceSlot).values.filter { it.size > 1 }.map { HardViolation(rule, it) }

    private fun isQualified(problem: SchedulingProblem, lesson: LessonAssignment): Boolean =
        problem.teacher(lesson.teacherId)?.qualifiedSubjectIds?.contains(lesson.subjectId) == true

    private fun roomOffersRequiredFeatures(problem: SchedulingProblem, lesson: LessonAssignment): Boolean {
        val room = problem.room(lesson.roomId) ?: return false
        val required = problem.subject(lesson.subjectId)?.requiredFeatureIds ?: return false
        return room.featureIds.containsAll(required)
    }

    /** FR-002a: lessons of subjects without required features belong into the class's home room, if it has one. */
    private fun isOutsideHomeRoom(problem: SchedulingProblem, lesson: LessonAssignment): Boolean {
        val homeRoom = problem.schoolClass(lesson.classId)?.homeRoomId ?: return false
        val needsNoFeatures = problem.subject(lesson.subjectId)?.requiredFeatureIds?.isEmpty() == true
        return needsNoFeatures && lesson.roomId != homeRoom
    }

    /** The weekly load is an upper limit (001 FR-018); the lessons beyond it — the latest of the week — are reported. */
    private fun loadExceeded(problem: SchedulingProblem, lessons: List<LessonAssignment>): List<HardViolation> =
        lessons.groupBy { it.teacherId }.mapNotNull { (teacherId, teacherLessons) ->
            val load = problem.teacher(teacherId)?.weeklyLoad ?: 0
            if (teacherLessons.size <= load) return@mapNotNull null
            HardViolation(HardRule.TEACHER_LOAD_EXCEEDED, teacherLessons.sortedWith(compareBy(problem.weekOrder) { it.daySlot }).drop(load))
        }

    private fun severalTeachersPerCourse(lessons: List<LessonAssignment>): List<HardViolation> =
        lessons.groupBy { it.course }.values
            .filter { courseLessons -> courseLessons.map { it.teacherId }.distinct().size > 1 }
            .map { HardViolation(HardRule.SEVERAL_TEACHERS_FOR_COURSE, it) }

    /** More lessons of a course than its weekly periods (or a course not in the curriculum): the surplus is reported. */
    private fun lessonsNotRequired(problem: SchedulingProblem, lessons: List<LessonAssignment>): List<HardViolation> =
        lessons.groupBy { it.course }.mapNotNull { (course, courseLessons) ->
            val required = problem.requiredPeriods(course)
            if (courseLessons.size <= required) return@mapNotNull null
            HardViolation(HardRule.LESSON_NOT_REQUIRED, courseLessons.sortedWith(compareBy(problem.weekOrder) { it.daySlot }).drop(required))
        }
}
