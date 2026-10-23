package timetablescheduler.adapters.solver.constraints

import ai.timefold.solver.core.api.score.HardSoftScore
import ai.timefold.solver.core.api.score.stream.Constraint
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors
import ai.timefold.solver.core.api.score.stream.ConstraintFactory
import ai.timefold.solver.core.api.score.stream.ConstraintProvider
import ai.timefold.solver.core.api.score.stream.Joiners
import timetablescheduler.adapters.solver.model.AppliedRuleFact
import timetablescheduler.adapters.solver.model.CourseAssignment
import timetablescheduler.adapters.solver.model.LessonPlacement
import timetablescheduler.adapters.solver.model.PassWeights
import timetablescheduler.domain.scheduling.solution.LessonAssignment
import kotlin.math.roundToLong

/**
 * Hard constraints of a timetable (FR-003) and the weighted objectives of the current pass (G2, G3). Qualification,
 * room features, home rooms and teaching slots hold by the value ranges of the model; the streams below cover what
 * depends on several lessons. Every rule reading a lesson's teacher joins the [CourseAssignment] explicitly, so the
 * incremental score calculation notices when the course's teacher changes.
 */
class TimetableConstraintProvider : ConstraintProvider {
    override fun defineConstraints(factory: ConstraintFactory): Array<Constraint> =
        arrayOf(
            classConflict(factory),
            roomConflict(factory),
            teacherConflict(factory),
            teacherUnavailable(factory),
            teacherLoadExceeded(factory),
            lessonWithoutTeacher(factory),
            classFill(factory),
            teacherLoadUsage(factory),
            stability(factory),
            classRulesHard(factory),
            classRulesSoft(factory),
            otherRulesHard(factory),
            otherRulesSoft(factory),
        )

    /**
     * Planner constraints about one class, joined by class: the placed lessons each rule covers are grouped per rule
     * and counted by the domain rule (G5), so solver and domain agree on every violation.
     */
    private fun classRuleGroups(factory: ConstraintFactory) =
        placedLessonsWithCourse(factory)
            .join(
                AppliedRuleFact::class.java,
                Joiners.equal({ _, course -> course.classFact.id }, { rule -> rule.classKey }),
                Joiners.filtering { _, course, rule -> rule.covers(course) },
            ).groupBy({ _, _, rule -> rule }, ConstraintCollectors.toList { lesson, course, _ -> assignmentOf(lesson, course) })

    /** Planner constraints about teachers or about a subject in every class, matched lesson by lesson. */
    private fun otherRuleGroups(factory: ConstraintFactory) =
        placedLessonsWithCourse(factory)
            .join(
                factory.forEach(AppliedRuleFact::class.java).filter { it.classKey == null },
                Joiners.filtering { _, course, rule -> rule.covers(course) },
            ).groupBy({ _, _, rule -> rule }, ConstraintCollectors.toList { lesson, course, _ -> assignmentOf(lesson, course) })

    private fun classRulesHard(factory: ConstraintFactory): Constraint =
        classRuleGroups(factory)
            .filter { rule, _ -> rule.isHard }
            .penalize(HardSoftScore.ONE_HARD) { rule, lessons -> rule.violations(lessons).toLong() }
            .asConstraint("Planner constraint for a class (hard)")

    private fun classRulesSoft(factory: ConstraintFactory): Constraint =
        classRuleGroups(factory)
            .filter { rule, _ -> !rule.isHard }
            .join(PassWeights::class.java)
            .penalize(HardSoftScore.ONE_SOFT) { rule, lessons, weights -> softPenalty(rule, rule.violations(lessons), weights) }
            .asConstraint("Planner constraint for a class (soft)")

    private fun otherRulesHard(factory: ConstraintFactory): Constraint =
        otherRuleGroups(factory)
            .filter { rule, _ -> rule.isHard }
            .penalize(HardSoftScore.ONE_HARD) { rule, lessons -> rule.violations(lessons).toLong() }
            .asConstraint("Planner constraint for teachers or subjects (hard)")

    private fun otherRulesSoft(factory: ConstraintFactory): Constraint =
        otherRuleGroups(factory)
            .filter { rule, _ -> !rule.isHard }
            .join(PassWeights::class.java)
            .penalize(HardSoftScore.ONE_SOFT) { rule, lessons, weights -> softPenalty(rule, rule.violations(lessons), weights) }
            .asConstraint("Planner constraint for teachers or subjects (soft)")

    /** G3: weight of the rule's objective in this pass times weight factor times violations / normalizer, in soft points. */
    private fun softPenalty(
        rule: AppliedRuleFact,
        violations: Int,
        weights: PassWeights,
    ): Long = scaled(weights.weightOf(rule.objectiveIndex) * rule.weightFactor * violations / rule.normalizer)

    private fun assignmentOf(
        lesson: LessonPlacement,
        course: CourseAssignment,
    ) = LessonAssignment(course.classFact.id, course.subjectId, course.teacher!!.id, lesson.room!!.id, lesson.timeslot!!.daySlot)

    private fun placedLessons(factory: ConstraintFactory) = factory.forEach(LessonPlacement::class.java).filter { it.isPlaced }

    /** A placed lesson together with its course, so teacher changes re-trigger the rule. */
    private fun placedLessonsWithCourse(factory: ConstraintFactory) =
        placedLessons(factory).join(CourseAssignment::class.java, Joiners.equal({ lesson -> lesson.course }, { course -> course }))

    private fun classConflict(factory: ConstraintFactory): Constraint =
        factory
            .forEachUniquePair(LessonPlacement::class.java, Joiners.equal { it.timeslot }, Joiners.equal { it.classId })
            .filter { first, second -> first.isPlaced && second.isPlaced }
            .penalize(HardSoftScore.ONE_HARD)
            .asConstraint("Class conflict")

    private fun roomConflict(factory: ConstraintFactory): Constraint =
        factory
            .forEachUniquePair(LessonPlacement::class.java, Joiners.equal { it.timeslot }, Joiners.equal { it.room })
            .filter { first, second -> first.isPlaced && second.isPlaced }
            .penalize(HardSoftScore.ONE_HARD)
            .asConstraint("Room conflict")

    /** Two placed lessons in one timeslot whose courses have the same teacher. */
    private fun teacherConflict(factory: ConstraintFactory): Constraint =
        factory
            .forEachUniquePair(LessonPlacement::class.java, Joiners.equal { it.timeslot })
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
            }.asConstraint("Class fill")

    /** Teacher load usage (G3): each lesson a teacher with load gives adds 1 / Σ load, weighted for the pass. */
    private fun teacherLoadUsage(factory: ConstraintFactory): Constraint =
        placedLessonsWithCourse(factory)
            .filter { _, course -> (course.teacher?.weeklyLoad ?: 0) > 0 }
            .join(PassWeights::class.java)
            .reward(HardSoftScore.ONE_SOFT) { _, _, weights -> scaled(weights.teacherLoad / weights.totalTeacherLoad) }
            .asConstraint("Teacher load usage")

    /** Stability (FR-006b): each reference lesson kept adds 1 / reference lessons, weighted for the pass. */
    private fun stability(factory: ConstraintFactory): Constraint =
        placedLessonsWithCourse(factory)
            .filter { lesson, course -> lesson.keepsReference(course.teacher) }
            .join(PassWeights::class.java)
            .filter { _, _, weights -> weights.stabilityIndex >= 0 }
            .reward(HardSoftScore.ONE_SOFT) { _, _, weights -> scaled(weights.stability / weights.totalReferenceLessons) }
            .asConstraint("Stability")

    private fun scaled(objectiveShare: Double): Long = (objectiveShare * PassWeights.SCALE).roundToLong()
}
