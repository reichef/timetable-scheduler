package timetablescheduler.application.scheduling

import timetablescheduler.application.port.ClassLevelRepository
import timetablescheduler.application.port.RoomRepository
import timetablescheduler.application.port.SchoolClassRepository
import timetablescheduler.application.port.SchoolRepository
import timetablescheduler.application.port.SubjectRepository
import timetablescheduler.application.port.TeacherRepository
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.curriculum.ClassCurriculumResolver
import timetablescheduler.domain.scheduling.DaySlot
import timetablescheduler.domain.scheduling.problem.DayStructure
import timetablescheduler.domain.scheduling.problem.ProblemClass
import timetablescheduler.domain.scheduling.problem.ProblemRoom
import timetablescheduler.domain.scheduling.problem.ProblemSubject
import timetablescheduler.domain.scheduling.problem.ProblemTeacher
import timetablescheduler.domain.scheduling.problem.SchedulingProblem
import timetablescheduler.domain.scheduling.problem.SlotRef
import timetablescheduler.domain.school.TimeGrid
import timetablescheduler.domain.teacher.Teacher

/**
 * Turns the school's current master data into the plain [SchedulingProblem] a generation works on. Curricula are
 * resolved per class through [ClassCurriculumResolver] (001 R9); unavailabilities on days or slots that no longer
 * exist in the grid are ignored (001 clarification on grid reductions).
 */
class SchedulingProblemBuilder(
    private val schools: SchoolRepository,
    private val rooms: RoomRepository,
    private val subjects: SubjectRepository,
    private val levels: ClassLevelRepository,
    private val classes: SchoolClassRepository,
    private val teachers: TeacherRepository,
) {
    fun build(schoolId: SchoolId): SchedulingProblem {
        val grid = schools.findTimeGrid(schoolId)
        val days = dayStructures(grid)
        val teachingDaySlots = days.flatMap { day -> day.teachingSlots.map { DaySlot(day.day, it) } }.toSet()
        return SchedulingProblem(
            days = days,
            rooms = rooms.findBySchool(schoolId).map { ProblemRoom(it.id, it.featureIds) },
            subjects = subjects.findBySchool(schoolId).map { ProblemSubject(it.id, it.requiredFeatureIds) },
            classes = problemClasses(schoolId),
            teachers = teachers.findBySchool(schoolId).map { problemTeacher(it, teachingDaySlots) },
        )
    }

    private fun dayStructures(grid: TimeGrid): List<DayStructure> =
        grid.teachingDays.sorted().map { day -> DayStructure(day, grid.slotsOn(day).sortedBy { it.startTime }.map { SlotRef(it.id, it.kind) }) }

    private fun problemClasses(schoolId: SchoolId): List<ProblemClass> {
        val levelsById = levels.findBySchool(schoolId).associateBy { it.id }
        return classes.findBySchool(schoolId).mapNotNull { schoolClass ->
            val level = levelsById[schoolClass.classLevelId] ?: return@mapNotNull null
            ProblemClass(schoolClass.id, schoolClass.priorityWeight, schoolClass.homeRoomId, ClassCurriculumResolver.curriculumOf(schoolClass, level))
        }
    }

    private fun problemTeacher(teacher: Teacher, teachingDaySlots: Set<DaySlot>) = ProblemTeacher(
        id = teacher.id,
        weeklyLoad = teacher.weeklyLoad,
        qualifiedSubjectIds = teacher.qualifiedSubjectIds,
        unavailable = teacher.unavailability.map { DaySlot(it.dayOfWeek, it.slotId) }.filter { it in teachingDaySlots }.toSet(),
    )
}
