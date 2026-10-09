package timetablescheduler.adapters.solver

import timetablescheduler.adapters.solver.model.ClassFact
import timetablescheduler.adapters.solver.model.CourseAssignment
import timetablescheduler.adapters.solver.model.LessonPlacement
import timetablescheduler.adapters.solver.model.PassWeights
import timetablescheduler.adapters.solver.model.RoomFact
import timetablescheduler.adapters.solver.model.TeacherFact
import timetablescheduler.adapters.solver.model.TimeslotFact
import timetablescheduler.adapters.solver.model.TimetablePlan
import timetablescheduler.domain.scheduling.DaySlot
import timetablescheduler.domain.scheduling.problem.ProblemClass
import timetablescheduler.domain.scheduling.problem.SchedulingProblem
import timetablescheduler.domain.scheduling.solution.CourseKey
import timetablescheduler.domain.scheduling.solution.LessonAssignment
import timetablescheduler.domain.scheduling.solution.TimetableCandidate
import timetablescheduler.domain.school.SlotKind

/** Translates between the domain's [SchedulingProblem] / [TimetableCandidate] and the Timefold model (G5). */
object ProblemMapping {

    fun toPlan(problem: SchedulingProblem): TimetablePlan {
        val timeslots = timeslotsOf(problem)
        val weekIndexByDaySlot = timeslots.associate { it.daySlot to it.weekIndex }
        val rooms = problem.rooms.map { RoomFact(it.id, it.featureIds) }
        val teachers = problem.teachers.map { teacher ->
            TeacherFact(teacher.id, teacher.weeklyLoad, teacher.qualifiedSubjectIds, teacher.unavailable.mapNotNull { weekIndexByDaySlot[it] }.toSet())
        }
        val classes = problem.classes.map { ClassFact(it.id, it.priorityWeight) }
        val courses = problem.classes.flatMap { schoolClass -> coursesOf(schoolClass, classes, teachers) }
        var nextLessonId = 0
        val lessons = courses.flatMap { course ->
            val suitable = suitableRooms(problem, course, rooms)
            if (suitable.isEmpty()) emptyList() else List(course.requiredPeriods) { LessonPlacement(nextLessonId++, course, suitable) }
        }
        return TimetablePlan().apply {
            this.timeslots = timeslots
            this.rooms = rooms
            this.teachers = teachers
            this.classes = classes
            this.courses = courses
            this.lessons = lessons
            this.passWeights = PassWeights(
                classFill = 0.5,
                teacherLoad = 0.5,
                totalWeightedDemand = totalWeightedDemand(problem),
                totalTeacherLoad = teachers.sumOf { it.weeklyLoad }.coerceAtLeast(1),
            )
        }
    }

    /** Placed lessons with their teacher; the remaining periods of each course are reported as unplaced (FR-005). */
    fun toCandidate(plan: TimetablePlan): TimetableCandidate {
        val placed = plan.lessons.filter { it.isPlaced && it.course.teacher != null }
        val lessons = placed.map { lesson ->
            LessonAssignment(lesson.course.classFact.id, lesson.course.subjectId, lesson.course.teacher!!.id, lesson.room!!.id, lesson.timeslot!!.daySlot)
        }
        val placedByCourse = lessons.groupingBy { it.course }.eachCount()
        val unplaced = plan.courses
            .associate { CourseKey(it.classFact.id, it.subjectId) to it.requiredPeriods - (placedByCourse[CourseKey(it.classFact.id, it.subjectId)] ?: 0) }
            .filterValues { it > 0 }
        return TimetableCandidate(lessons, unplaced)
    }

    private fun timeslotsOf(problem: SchedulingProblem): List<TimeslotFact> {
        var weekIndex = 0
        return problem.days.sortedBy { it.day }.flatMap { day ->
            day.slots.withIndex()
                .filter { (_, slot) -> slot.kind == SlotKind.TEACHING }
                .map { (position, slot) -> TimeslotFact(weekIndex++, DaySlot(day.day, slot.slotId), position) }
        }
    }

    private fun coursesOf(schoolClass: ProblemClass, classes: List<ClassFact>, teachers: List<TeacherFact>): List<CourseAssignment> {
        val classFact = classes.single { it.id == schoolClass.id }
        return schoolClass.curriculum.map { entry ->
            CourseAssignment(classFact, entry.subjectId, entry.periodsPerWeek, teachers.filter { entry.subjectId in it.qualifiedSubjectIds })
        }
    }

    /** Rooms offering all required features; for subjects without features only the home room, if the class has one. */
    private fun suitableRooms(problem: SchedulingProblem, course: CourseAssignment, rooms: List<RoomFact>): List<RoomFact> {
        val required = problem.subject(course.subjectId)?.requiredFeatureIds ?: return emptyList()
        val homeRoom = problem.schoolClass(course.classFact.id)?.homeRoomId
        if (required.isEmpty() && homeRoom != null) return rooms.filter { it.id == homeRoom }
        return rooms.filter { it.featureIds.containsAll(required) }
    }

    private fun totalWeightedDemand(problem: SchedulingProblem): Double =
        problem.classes.sumOf { schoolClass -> schoolClass.priorityWeight * schoolClass.curriculum.sumOf { it.periodsPerWeek } }
            .toDouble()
            .coerceAtLeast(1.0)
}
