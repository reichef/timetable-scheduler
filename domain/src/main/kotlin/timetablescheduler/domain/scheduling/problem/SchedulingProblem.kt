package timetablescheduler.domain.scheduling.problem

import timetablescheduler.domain.common.RoomFeatureId
import timetablescheduler.domain.common.RoomId
import timetablescheduler.domain.common.SchoolClassId
import timetablescheduler.domain.common.SubjectId
import timetablescheduler.domain.common.TeacherId
import timetablescheduler.domain.common.TimeSlotId
import timetablescheduler.domain.curriculum.CurriculumEntry
import timetablescheduler.domain.scheduling.DaySlot
import timetablescheduler.domain.scheduling.constraint.SchedulingConstraint
import timetablescheduler.domain.scheduling.constraint.SchedulingConstraintId
import timetablescheduler.domain.scheduling.constraint.TeacherFixation
import timetablescheduler.domain.scheduling.solution.CourseKey
import timetablescheduler.domain.scheduling.solution.LessonAssignment
import timetablescheduler.domain.school.SlotKind
import java.time.DayOfWeek

/** A slot of a day in time order, teaching slot or break. */
data class SlotRef(val slotId: TimeSlotId, val kind: SlotKind)

/** The slots of one teaching day in time order, breaks included (needed for gaps and double periods). */
data class DayStructure(val day: DayOfWeek, val slots: List<SlotRef>) {
    val teachingSlots: List<TimeSlotId> get() = slots.filter { it.kind == SlotKind.TEACHING }.map { it.slotId }
}

data class ProblemRoom(val id: RoomId, val featureIds: Set<RoomFeatureId>)

data class ProblemSubject(val id: SubjectId, val requiredFeatureIds: Set<RoomFeatureId>)

/** A class with its priority weight (FR-015), optional home room (FR-002a) and resolved curriculum. */
data class ProblemClass(
    val id: SchoolClassId,
    val priorityWeight: Int,
    val homeRoomId: RoomId?,
    val curriculum: List<CurriculumEntry>,
)

/** A teacher with qualifications, weekly load as upper limit and unavailable day-slots (001 FR-017, FR-018, FR-021). */
data class ProblemTeacher(
    val id: TeacherId,
    val weeklyLoad: Int,
    val qualifiedSubjectIds: Set<SubjectId>,
    val unavailable: Set<DaySlot>,
)

/**
 * Everything one generation schedules, as plain values built from the master data at the start: the week, rooms,
 * subjects, classes and teachers, plus the active planner constraints, teacher fixations, the soft constraints compared
 * as separate objectives (FR-006a) and the reference timetable when starting from the chosen one (FR-006b).
 */
data class SchedulingProblem(
    val days: List<DayStructure>,
    val rooms: List<ProblemRoom>,
    val subjects: List<ProblemSubject>,
    val classes: List<ProblemClass>,
    val teachers: List<ProblemTeacher>,
    val constraints: List<SchedulingConstraint> = emptyList(),
    val fixations: List<TeacherFixation> = emptyList(),
    val separateObjectiveIds: List<SchedulingConstraintId> = emptyList(),
    val reference: List<LessonAssignment>? = null,
) {
    private val roomsById = rooms.associateBy { it.id }
    private val subjectsById = subjects.associateBy { it.id }
    private val classesById = classes.associateBy { it.id }
    private val teachersById = teachers.associateBy { it.id }
    private val teachingDaySlots: List<DaySlot> = days.sortedBy { it.day }.flatMap { day -> day.teachingSlots.map { DaySlot(day.day, it) } }
    private val weekPositions: Map<DaySlot, Int> = days.sortedBy { it.day }
        .flatMap { day -> day.slots.map { DaySlot(day.day, it.slotId) } }
        .withIndex()
        .associate { (position, daySlot) -> daySlot to position }

    fun room(id: RoomId): ProblemRoom? = roomsById[id]
    fun subject(id: SubjectId): ProblemSubject? = subjectsById[id]
    fun schoolClass(id: SchoolClassId): ProblemClass? = classesById[id]
    fun teacher(id: TeacherId): ProblemTeacher? = teachersById[id]

    /** All teaching slots of the week in time order. */
    fun teachingDaySlots(): List<DaySlot> = teachingDaySlots

    fun isTeachingSlot(daySlot: DaySlot): Boolean = daySlot in teachingDaySlotSet

    private val teachingDaySlotSet = teachingDaySlots.toSet()

    /** Orders lessons by time of the week (day, then slot); unknown slots come last. */
    val weekOrder: Comparator<DaySlot> = compareBy { weekPositions[it] ?: Int.MAX_VALUE }

    /** Every course to be taught with its weekly periods. */
    fun courses(): Map<CourseKey, Int> =
        classes.flatMap { schoolClass -> schoolClass.curriculum.map { CourseKey(schoolClass.id, it.subjectId) to it.periodsPerWeek } }.toMap()

    fun requiredPeriods(course: CourseKey): Int =
        classesById[course.classId]?.curriculum?.firstOrNull { it.subjectId == course.subjectId }?.periodsPerWeek ?: 0

    fun dayStructure(day: DayOfWeek): DayStructure? = days.firstOrNull { it.day == day }
}
