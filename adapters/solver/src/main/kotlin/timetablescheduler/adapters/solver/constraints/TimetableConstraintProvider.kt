package timetablescheduler.adapters.solver.constraints

import ai.timefold.solver.core.api.score.HardSoftScore
import ai.timefold.solver.core.api.score.stream.Constraint
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors
import ai.timefold.solver.core.api.score.stream.ConstraintFactory
import ai.timefold.solver.core.api.score.stream.ConstraintProvider
import ai.timefold.solver.core.api.score.stream.Joiners
import timetablescheduler.adapters.solver.model.CourseAssignment
import timetablescheduler.adapters.solver.model.LessonPlacement
import timetablescheduler.adapters.solver.model.PassWeights
import kotlin.math.roundToLong

/**
 * Hard constraints of a timetable (FR-003) and the weighted objectives of the current pass (G2, G3). Qualification,
 * room features, home rooms and teaching slots hold by the value ranges of the model; the streams below cover what
 * depends on several lessons. Every rule reading a lesson's teacher joins the [CourseAssignment] explicitly, so the
 * incremental score calculation notices when the course's teacher changes.
 */
class TimetableConstraintProvider : ConstraintProvider {

    override fun defineConstraints(factory: ConstraintFactory): Array<Constraint> = arrayOf(
        classConflict(factory),
        roomConflict(factory),
        teacherConflict(factory),
        teacherUnavailable(factory),
        teacherLoadExceeded(factory),
        lessonWithoutTeacher(factory),
        classFill(factory),
        teacherLoadUsage(factory),
    )

    private fun placedLessons(factory: ConstraintFactory) = factory.forEach(LessonPlacement::class.java).filter { it.isPlaced }

    /** A placed lesson together with its course, so teacher changes re-trigger the rule. */
    private fun placedLessonsWithCourse(factory: ConstraintFactory) =
        placedLessons(factory).join(CourseAssignment::class.java, Joiners.equal({ lesson -> lesson.course }, { course -> course }))

    private fun classConflict(factory: ConstraintFactory): Constraint =
        factory.forEachUniquePair(LessonPlacement::class.java, Joiners.equal { it.timeslot }, Joiners.equal { it.classId })
            .filter { first, second -> first.isPlaced && second.isPlaced }
            .penalize(HardSoftScore.ONE_HARD)
            .asConstraint("Class conflict")

    private fun roomConflict(factory: ConstraintFactory): Constraint =
        factory.forEachUniquePair(LessonPlacement::class.java, Joiners.equal { it.timeslot }, Joiners.equal { it.room })
            .filter { first, second -> first.isPlaced && second.isPlaced }
            .penalize(HardSoftScore.ONE_HARD)
            .asConstraint("Room conflict")

    /** Two placed lessons in one timeslot whose courses have the same teacher. */
    private fun teacherConflict(factory: ConstraintFactory): Constraint =
        factory.forEachUniquePair(LessonPlacement::class.java, Joiners.equal { it.timeslot })
            .filter { first, second -> first.isPlaced && second.isPlaced }
            .join(CourseAssignment::class.java, Joiners.equal({ first, _ -> first.course }, { course -> course }))
            .join(CourseAssignment::class.java, Joiners.equal({ _, second, _ -> second.course }, { course -> course }))
            .filter { _, _, firstCourse, secondCourse -> firstCourse.teacher != null && firstCourse.teacher == secondCourse.teacher }
            .penalize(HardSoftScore.ONE_HARD)
            .asConstraint("Teacher conflict")

    private fun teacherUnavailable(factory: ConstraintFactory): Constraint =
        placedLessonsWithCourse(factory)
            .filter { lesson, course -> course.teacher?.unavailableWeekIndices?.contains(lesson.timeslot!!.weekIndex) == true }
            .penalize(HardSoftScore.ONE_HARD)
            .asConstraint("Teacher unavailable")

    /** The weekly load is an upper limit (001 FR-018): every placed lesson beyond it costs one hard point. */
    private fun teacherLoadExceeded(factory: ConstraintFactory): Constraint =
        placedLessonsWithCourse(factory)
            .filter { _, course -> course.teacher != null }
            .groupBy({ _, course -> course.teacher!! }, ConstraintCollectors.countBi())
            .filter { teacher, lessons -> lessons > teacher.weeklyLoad }
            .penalize(HardSoftScore.ONE_HARD) { teacher, lessons -> (lessons - teacher.weeklyLoad).toLong() }
            .asConstraint("Teacher load exceeded")

    /** A placed lesson needs a teacher; courses without any qualified teacher therefore stay unplaced. */
    private fun lessonWithoutTeacher(factory: ConstraintFactory): Constraint =
        placedLessonsWithCourse(factory)
            .filter { _, course -> course.teacher == null }
            .penalize(HardSoftScore.ONE_HARD)
            .asConstraint("Lesson without teacher")

    /**
     * Class fill (G3): each placed lesson adds priority / Σ(priority · required periods) to the objective, weighted with
     * the pass's class-fill weight and scaled to whole soft points.
     */
    private fun classFill(factory: ConstraintFactory): Constraint =
        placedLessonsWithCourse(factory)
            .join(PassWeights::class.java)
            .reward(HardSoftScore.ONE_SOFT) { _, course, weights ->
                scaled(weights.classFill * course.classFact.priorityWeight / weights.totalWeightedDemand)
            }
            .asConstraint("Class fill")

    /** Teacher load usage (G3): each lesson a teacher with load gives adds 1 / Σ load, weighted for the pass. */
    private fun teacherLoadUsage(factory: ConstraintFactory): Constraint =
        placedLessonsWithCourse(factory)
            .filter { _, course -> (course.teacher?.weeklyLoad ?: 0) > 0 }
            .join(PassWeights::class.java)
            .reward(HardSoftScore.ONE_SOFT) { _, _, weights -> scaled(weights.teacherLoad / weights.totalTeacherLoad) }
            .asConstraint("Teacher load usage")

    private fun scaled(objectiveShare: Double): Long = (objectiveShare * PassWeights.SCALE).roundToLong()
}
