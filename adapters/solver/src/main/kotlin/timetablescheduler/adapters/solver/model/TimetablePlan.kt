package timetablescheduler.adapters.solver.model

import ai.timefold.solver.core.api.domain.entity.PlanningEntity
import ai.timefold.solver.core.api.domain.entity.PlanningPin
import ai.timefold.solver.core.api.domain.common.PlanningId
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
import java.time.DayOfWeek

// Timefold model (G5). Planning entities and the solution need mutable fields and a no-arg constructor for the
// solution cloner; problem facts are immutable. None of these types leaves the solver adapter.

/** A teaching slot of the week; [weekIndex] orders all teaching slots, [dayPosition] counts slots incl. breaks. */
data class TimeslotFact(val weekIndex: Int, val daySlot: DaySlot, val dayPosition: Int) {
    val day: DayOfWeek get() = daySlot.day
}

data class RoomFact(val id: RoomId, val featureIds: Set<RoomFeatureId>)

/** A teacher; [unavailableWeekIndices] are the timeslots in which the teacher cannot teach (001 FR-021). */
data class TeacherFact(
    val id: TeacherId,
    val weeklyLoad: Int,
    val qualifiedSubjectIds: Set<SubjectId>,
    val unavailableWeekIndices: Set<Int>,
)

/** The class of a course with its normalized fill weight: priority / Σ (priority · required periods) (G3). */
data class ClassFact(val id: SchoolClassId, val priorityWeight: Int)

/**
 * The weights of the current solver pass (G2): every placed lesson is rewarded with the pass weights times its
 * normalized contribution to each objective, scaled to whole numbers by [SCALE].
 */
data class PassWeights(
    val classFill: Double,
    val teacherLoad: Double,
    val totalWeightedDemand: Double,
    val totalTeacherLoad: Int,
) {
    companion object {
        const val SCALE = 1_000_000.0
    }
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

    @ValueRangeProvider(id = "suitableRooms")
    fun suitableRoomRange(): List<RoomFact> = suitableRooms

    val isPlaced: Boolean get() = timeslot != null && room != null

    val classId: SchoolClassId get() = course.classFact.id

    override fun toString(): String = "LessonPlacement($id)"
}

@PlanningSolution
class TimetablePlan() {
    @field:ProblemFactCollectionProperty
    @field:ValueRangeProvider(id = "timeslots")
    var timeslots: List<TimeslotFact> = emptyList()

    @field:ProblemFactCollectionProperty
    var rooms: List<RoomFact> = emptyList()

    @field:ProblemFactCollectionProperty
    var teachers: List<TeacherFact> = emptyList()

    @field:ProblemFactCollectionProperty
    var classes: List<ClassFact> = emptyList()

    @field:ProblemFactProperty
    var passWeights: PassWeights = PassWeights(0.5, 0.5, 1.0, 1)

    @field:PlanningEntityCollectionProperty
    var courses: List<CourseAssignment> = emptyList()

    @field:PlanningEntityCollectionProperty
    var lessons: List<LessonPlacement> = emptyList()

    @field:PlanningScore
    var score: HardSoftScore? = null
}
