package timetablescheduler.domain.scheduling

import timetablescheduler.domain.common.RoomFeatureId
import timetablescheduler.domain.common.RoomId
import timetablescheduler.domain.common.SchoolClassId
import timetablescheduler.domain.common.SubjectId
import timetablescheduler.domain.common.TeacherId
import timetablescheduler.domain.common.TimeSlotId
import timetablescheduler.domain.curriculum.CurriculumEntry
import timetablescheduler.domain.scheduling.problem.DayStructure
import timetablescheduler.domain.scheduling.problem.ProblemClass
import timetablescheduler.domain.scheduling.problem.ProblemRoom
import timetablescheduler.domain.scheduling.problem.ProblemSubject
import timetablescheduler.domain.scheduling.problem.ProblemTeacher
import timetablescheduler.domain.scheduling.problem.SchedulingProblem
import timetablescheduler.domain.scheduling.problem.SlotRef
import timetablescheduler.domain.scheduling.solution.LessonAssignment
import timetablescheduler.domain.school.SlotKind
import java.time.DayOfWeek

/**
 * Builds small scheduling problems for domain tests: [days] teaching days with [teachingSlotsPerDay] slots each and,
 * if [breakAfterSlot] is given, a break after that many teaching slots.
 */
class TestSchool(
    val days: List<DayOfWeek> = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
    teachingSlotsPerDay: Int = 4,
    breakAfterSlot: Int? = null,
) {
    /** Teaching slots in order, shared by all days. */
    val teachingSlots: List<TimeSlotId> = List(teachingSlotsPerDay) { TimeSlotId.new() }
    val breakSlot: TimeSlotId? = breakAfterSlot?.let { TimeSlotId.new() }
    private val slotSequence: List<SlotRef> = buildList {
        teachingSlots.forEachIndexed { index, slotId ->
            add(SlotRef(slotId, SlotKind.TEACHING))
            if (breakSlot != null && index + 1 == breakAfterSlot) add(SlotRef(breakSlot, SlotKind.BREAK))
        }
    }

    val rooms = mutableListOf<ProblemRoom>()
    val subjects = mutableListOf<ProblemSubject>()
    val classes = mutableListOf<ProblemClass>()
    val teachers = mutableListOf<ProblemTeacher>()

    fun room(features: Set<RoomFeatureId> = emptySet()): RoomId = RoomId.new().also { rooms += ProblemRoom(it, features) }

    fun subject(requiredFeatures: Set<RoomFeatureId> = emptySet()): SubjectId = SubjectId.new().also { subjects += ProblemSubject(it, requiredFeatures) }

    fun schoolClass(vararg curriculum: Pair<SubjectId, Int>, weight: Int = 10, homeRoom: RoomId? = null): SchoolClassId =
        SchoolClassId.new().also { id ->
            classes += ProblemClass(id, weight, homeRoom, curriculum.map { (subject, periods) -> CurriculumEntry(subject, periods) })
        }

    fun teacher(load: Int, qualified: Set<SubjectId>, unavailable: Set<DaySlot> = emptySet()): TeacherId =
        TeacherId.new().also { teachers += ProblemTeacher(it, load, qualified, unavailable) }

    fun slot(day: DayOfWeek, teachingIndex: Int): DaySlot = DaySlot(day, teachingSlots[teachingIndex])

    fun lesson(schoolClass: SchoolClassId, subject: SubjectId, teacher: TeacherId, room: RoomId, day: DayOfWeek, teachingIndex: Int) =
        LessonAssignment(schoolClass, subject, teacher, room, slot(day, teachingIndex))

    fun problem(): SchedulingProblem = SchedulingProblem(
        days = days.map { DayStructure(it, slotSequence) },
        rooms = rooms.toList(),
        subjects = subjects.toList(),
        classes = classes.toList(),
        teachers = teachers.toList(),
    )
}
