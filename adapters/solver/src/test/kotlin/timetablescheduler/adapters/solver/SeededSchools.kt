package timetablescheduler.adapters.solver

import timetablescheduler.application.port.SolverListener
import timetablescheduler.application.port.StopSignal
import timetablescheduler.domain.common.RoomFeatureId
import timetablescheduler.domain.common.RoomId
import timetablescheduler.domain.common.SchoolClassId
import timetablescheduler.domain.common.SubjectId
import timetablescheduler.domain.common.TeacherId
import timetablescheduler.domain.common.TimeSlotId
import timetablescheduler.domain.curriculum.CurriculumEntry
import timetablescheduler.domain.scheduling.DaySlot
import timetablescheduler.domain.scheduling.problem.DayStructure
import timetablescheduler.domain.scheduling.problem.ProblemClass
import timetablescheduler.domain.scheduling.problem.ProblemRoom
import timetablescheduler.domain.scheduling.problem.ProblemSubject
import timetablescheduler.domain.scheduling.problem.ProblemTeacher
import timetablescheduler.domain.scheduling.problem.SchedulingProblem
import timetablescheduler.domain.scheduling.problem.SlotRef
import timetablescheduler.domain.scheduling.solution.TimetableCandidate
import timetablescheduler.domain.school.SlotKind
import java.time.DayOfWeek

/** Builds scheduling problems for solver tests; mirrors the domain test fixture. */
class SeededSchool(days: Int = 5, teachingSlotsPerDay: Int = 6, breakAfterSlot: Int? = 3) {
    private val weekDays = DayOfWeek.entries.take(days)
    val teachingSlots: List<TimeSlotId> = List(teachingSlotsPerDay) { TimeSlotId.new() }
    private val breakSlot = breakAfterSlot?.let { TimeSlotId.new() }
    private val slotSequence = buildList {
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

    fun schoolClass(curriculum: List<Pair<SubjectId, Int>>, weight: Int = 10, homeRoom: RoomId? = null): SchoolClassId =
        SchoolClassId.new().also { id -> classes += ProblemClass(id, weight, homeRoom, curriculum.map { CurriculumEntry(it.first, it.second) }) }

    fun teacher(load: Int, qualified: Set<SubjectId>, unavailable: Set<DaySlot> = emptySet()): TeacherId =
        TeacherId.new().also { teachers += ProblemTeacher(it, load, qualified, unavailable) }

    fun slot(day: DayOfWeek, teachingIndex: Int) = DaySlot(day, teachingSlots[teachingIndex])

    fun problem() = SchedulingProblem(weekDays.map { DayStructure(it, slotSequence) }, rooms.toList(), subjects.toList(), classes.toList(), teachers.toList())
}

object SeededSchools {

    /**
     * Four classes, 5 days × 6 slots; each class needs 20 periods and has its own home room, and the teachers' loads
     * exactly suffice, so a timetable placing every lesson exists (SC-004).
     */
    fun solvable(): SeededSchool = SeededSchool().apply {
        val lab = RoomFeatureId.new()
        val chemistryLab = room(setOf(lab))
        val math = subject()
        val german = subject()
        val english = subject()
        val chemistry = subject(setOf(lab))
        repeat(4) {
            val homeRoom = room()
            schoolClass(listOf(math to 6, german to 6, english to 6, chemistry to 2), homeRoom = homeRoom)
            teacher(12, setOf(math, chemistry))
            teacher(12, setOf(german, english))
        }
        teacher(8, setOf(chemistry))
        check(chemistryLab in rooms.map { it.id })
    }

    /** More periods than teaching slots and teachers: some lessons must stay unplaced (FR-005). */
    fun overConstrained(): SeededSchool = SeededSchool(days = 2, teachingSlotsPerDay = 3, breakAfterSlot = null).apply {
        val math = subject()
        val room = room()
        schoolClass(listOf(math to 8), homeRoom = room)
        teacher(4, setOf(math))
    }

    /** Competing objectives: an important class and teachers whose loads cannot all be reached. */
    fun competing(): SeededSchool = SeededSchool(days = 2, teachingSlotsPerDay = 4, breakAfterSlot = 2).apply {
        val math = subject()
        val art = subject()
        schoolClass(listOf(math to 6, art to 2), weight = 30, homeRoom = room())
        schoolClass(listOf(math to 6, art to 2), weight = 10, homeRoom = room())
        teacher(6, setOf(math))
        teacher(4, setOf(math, art))
        teacher(4, setOf(art), unavailable = setOf(slot(DayOfWeek.MONDAY, 0), slot(DayOfWeek.TUESDAY, 0)))
    }
}

/** Collects what a solver run reports. */
class CollectingListener : SolverListener {
    val candidates = mutableListOf<TimetableCandidate>()
    var infeasiblePasses = 0

    override fun onCandidate(candidate: TimetableCandidate) {
        synchronized(candidates) { candidates += candidate }
    }

    override fun onInfeasiblePass() {
        infeasiblePasses++
    }
}

val neverStop = StopSignal { false }
