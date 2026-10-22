package timetablescheduler.adapters.solver.model

import ai.timefold.solver.core.api.domain.common.PlanningId
import ai.timefold.solver.core.api.domain.entity.PlanningEntity
import ai.timefold.solver.core.api.domain.entity.PlanningPin
import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty
import ai.timefold.solver.core.api.domain.solution.PlanningScore
import ai.timefold.solver.core.api.domain.solution.PlanningSolution
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty
import ai.timefold.solver.core.api.domain.solution.ProblemFactProperty
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider
import ai.timefold.solver.core.api.domain.variable.PlanningVariable
import ai.timefold.solver.core.api.score.HardSoftScore
import timetablescheduler.domain.common.RoomFeatureId
import timetablescheduler.domain.common.RoomId
import timetablescheduler.domain.common.SchoolClassId
import timetablescheduler.domain.common.SubjectId
import timetablescheduler.domain.common.TeacherId
import timetablescheduler.domain.scheduling.DaySlot
import timetablescheduler.domain.scheduling.constraint.AppliedConstraint
import timetablescheduler.domain.scheduling.constraint.ConstraintRule
import timetablescheduler.domain.scheduling.constraint.RuleViolations
import timetablescheduler.domain.scheduling.problem.SchedulingProblem
import timetablescheduler.domain.scheduling.solution.LessonAssignment
import java.time.DayOfWeek

// Timefold model (G5). Planning entities and the solution need mutable fields and a no-arg constructor for the
// solution cloner; problem facts are immutable. None of these types leaves the solver adapter.

/** A teaching slot of the week; [weekIndex] orders all teaching slots, [dayPosition] counts slots incl. breaks. */
data class TimeslotFact(
    val weekIndex: Int,
    val daySlot: DaySlot,
    val dayPosition: Int,
) {
    val day: DayOfWeek get() = daySlot.day
}

data class RoomFact(
    val id: RoomId,
    val featureIds: Set<RoomFeatureId>,
)

/** A teacher; [unavailableWeekIndices] are the timeslots in which the teacher cannot teach (001 FR-021). */
data class TeacherFact(
    val id: TeacherId,
    val weeklyLoad: Int,
    val qualifiedSubjectIds: Set<SubjectId>,
    val unavailableWeekIndices: Set<Int>,
)

/** The class of a course with its normalized fill weight: priority / Σ (priority · required periods) (G3). */
data class ClassFact(
    val id: SchoolClassId,
    val priorityWeight: Int,
)

/**
 * The weights of the current solver pass (G2) in objective order — class fill, teacher load usage, the soft
 * constraints compared separately, the other soft constraints and, when starting from the chosen timetable, stability
 * (see [ObjectiveLayout]). Every objective's normalized contribution is multiplied by its weight and scaled to whole
 * soft points by [SCALE]. [stabilityIndex] is -1 without a reference.
 */
data class PassWeights(
    val weights: List<Double>,
    val totalWeightedDemand: Double,
    val totalTeacherLoad: Int,
    val stabilityIndex: Int = -1,
    val totalReferenceLessons: Int = 1,
) {
    val classFill: Double get() = weights[ObjectiveLayout.CLASS_FILL]
    val teacherLoad: Double get() = weights[ObjectiveLayout.TEACHER_LOAD]
    val stability: Double get() = if (stabilityIndex < 0) 0.0 else weights[stabilityIndex]

    fun weightOf(objectiveIndex: Int): Double = weights[objectiveIndex]

    companion object {
        const val SCALE = 1_000_000.0
    }
}

/**
 * A planner constraint as it applies to one class or target (domain `AppliedConstraint`), with what the solver needs
 * to score it: whether it is hard, and for soft ones the objective it belongs to, its weight factor and the
 * normalizer that turns violations into a share of that objective (G3). Violations are counted by the domain rules,
 * so solver and domain always agree.
 */
class AppliedRuleFact(
    val id: Int,
    val applied: AppliedConstraint,
    private val problem: SchedulingProblem,
    val objectiveIndex: Int,
    val weightFactor: Int,
    val normalizer: Double,
) {
    val isHard: Boolean get() = applied.constraint.isHard

    /** Rules about one class are joined by class; all others are matched lesson by lesson. */
    val classKey: SchoolClassId? =
        when (val rule = applied.rule) {
            is ConstraintRule.TeacherMaxPeriodsPerDay,
            is ConstraintRule.TeacherMaxGapsPerDay,
            is ConstraintRule.TeacherExcludedSlots,
            -> null

            is ConstraintRule.SubjectExcludedSlots -> rule.classId

            is ConstraintRule.SubjectDoublePeriodsInClass -> rule.classId

            is ConstraintRule.ClassExcludedSlots -> rule.classId

            else -> applied.classId
        }

    /** Whether a placed lesson of [course] can count for this rule. */
    fun covers(course: CourseAssignment): Boolean =
        when (val rule = applied.rule) {
            is ConstraintRule.TeacherMaxPeriodsPerDay -> {
                course.teacher?.id == rule.teacherId
            }

            is ConstraintRule.TeacherMaxGapsPerDay -> {
                course.teacher?.id == rule.teacherId
            }

            is ConstraintRule.TeacherExcludedSlots -> {
                course.teacher?.id == rule.teacherId
            }

            is ConstraintRule.SubjectExcludedSlots -> {
                course.subjectId == rule.subjectId && (rule.classId == null || course.classFact.id == rule.classId)
            }

            is ConstraintRule.SubjectDoublePeriodsInClass -> {
                course.classFact.id == rule.classId && course.subjectId == rule.subjectId
            }

            is ConstraintRule.SubjectMaxPerDayInClass -> {
                course.classFact.id == applied.classId && course.subjectId == applied.subjectId
            }

            is ConstraintRule.ClassExcludedSlots -> {
                course.classFact.id == rule.classId
            }

            else -> {
                course.classFact.id == applied.classId
            }
        }

    fun violations(lessons: List<LessonAssignment>): Int = RuleViolations.of(applied, problem, lessons).count

    override fun toString(): String = "AppliedRuleFact($id)"
}

/** Positions of the objectives in weight vectors and objective vectors (matches `ObjectiveValues.asVector`). */
object ObjectiveLayout {
    const val CLASS_FILL = 0
    const val TEACHER_LOAD = 1
    const val FIRST_SEPARATE = 2
}

/**
 * Who teaches one subject in one class (FR-004): all lessons of the course share this teacher. The teacher comes only
 * from the qualified teachers (001 FR-017); a fixed teacher is pinned (FR-013a).
 */
@PlanningEntity
class CourseAssignment() {
    lateinit var classFact: ClassFact
    var subjectId: SubjectId = SubjectId(java.util.UUID(0, 0))
    var requiredPeriods: Int = 0
    var qualifiedTeachers: List<TeacherFact> = emptyList()

    @field:PlanningPin
    var pinned: Boolean = false

    @field:PlanningVariable(valueRangeProviderRefs = ["qualifiedTeachers"], allowsUnassigned = true)
    var teacher: TeacherFact? = null

    constructor(classFact: ClassFact, subjectId: SubjectId, requiredPeriods: Int, qualifiedTeachers: List<TeacherFact>) : this() {
        this.classFact = classFact
        this.subjectId = subjectId
        this.requiredPeriods = requiredPeriods
        this.qualifiedTeachers = qualifiedTeachers
    }

    @ValueRangeProvider(id = "qualifiedTeachers")
    fun qualifiedTeacherRange(): List<TeacherFact> = qualifiedTeachers

    override fun toString(): String = "CourseAssignment"
}

/**
 * One period of a course. Every lesson always has a room — only rooms offering the subject's required features, or only
 * the class's home room for subjects without required features (001 FR-009, 002 FR-002a), so these rules hold by
 * construction. A lesson counts as placed when it also has a timeslot; placing or removing a lesson is therefore a
 * single change of its timeslot, which keeps the solver's moves simple. Lessons without any suitable room are not
 * created and stay unplaced.
 */
@PlanningEntity
class LessonPlacement() {
    @field:PlanningId
    var id: Int = 0
    lateinit var course: CourseAssignment
    var suitableRooms: List<RoomFact> = emptyList()

    @field:PlanningVariable(valueRangeProviderRefs = ["timeslots"], allowsUnassigned = true)
    var timeslot: TimeslotFact? = null

    @field:PlanningVariable(valueRangeProviderRefs = ["suitableRooms"])
    var room: RoomFact? = null

    constructor(id: Int, course: CourseAssignment, suitableRooms: List<RoomFact>) : this() {
        this.id = id
        this.course = course
        this.suitableRooms = suitableRooms
    }

    /**
     * The reference lesson this lesson is matched with when starting from the chosen timetable (FR-006b); the lesson
     * counts as kept when it stays in the same timeslot and room and its course keeps the teacher. Lessons of a course
     * are interchangeable, so a fixed one-to-one matching rewards the same timetables as the domain's multiset matching.
     */
    var referenceTimeslot: TimeslotFact? = null
    var referenceRoom: RoomFact? = null
    var referenceTeacher: TeacherFact? = null

    @ValueRangeProvider(id = "suitableRooms")
    fun suitableRoomRange(): List<RoomFact> = suitableRooms

    val isPlaced: Boolean get() = timeslot != null && room != null

    fun keepsReference(teacher: TeacherFact?): Boolean =
        referenceTimeslot != null && timeslot == referenceTimeslot && room == referenceRoom && teacher == referenceTeacher

    val classId: SchoolClassId get() = course.classFact.id

    override fun toString(): String = "LessonPlacement($id)"
}

@PlanningSolution
class TimetablePlan {
    @field:ProblemFactCollectionProperty
    @field:ValueRangeProvider(id = "timeslots")
    var timeslots: List<TimeslotFact> = emptyList()

    @field:ProblemFactCollectionProperty
    var rooms: List<RoomFact> = emptyList()

    @field:ProblemFactCollectionProperty
    var teachers: List<TeacherFact> = emptyList()

    @field:ProblemFactCollectionProperty
    var classes: List<ClassFact> = emptyList()

    @field:ProblemFactCollectionProperty
    var rules: List<AppliedRuleFact> = emptyList()

    @field:ProblemFactProperty
    var passWeights: PassWeights = PassWeights(listOf(0.5, 0.5), 1.0, 1)

    @field:PlanningEntityCollectionProperty
    var courses: List<CourseAssignment> = emptyList()

    @field:PlanningEntityCollectionProperty
    var lessons: List<LessonPlacement> = emptyList()

    @field:PlanningScore
    var score: HardSoftScore? = null
}
