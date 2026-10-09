package timetablescheduler.application.scheduling

import timetablescheduler.application.fakes.MasterDataWorld
import timetablescheduler.domain.account.Role
import timetablescheduler.domain.scheduling.DaySlot
import timetablescheduler.domain.scheduling.solution.CourseKey
import timetablescheduler.domain.scheduling.solution.LessonAssignment
import timetablescheduler.domain.scheduling.solution.TimetableCandidate
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.TUESDAY

/**
 * A small ready school for scheduling use case tests: two days with two teaching slots, one class 5a with
 * Mathematics 2 periods, teachers SCHA (load 4) and MUEL (load 4), rooms A-101 and B-102.
 */
class SchedulingTestSchool(val world: MasterDataWorld = MasterDataWorld()) {
    val school = world.school("Gymnasium Nord")
    val admin = world.planner("admin", school.id)
    val teacherAccount = world.account("lehrer1", Role.TEACHER, school.id)
    val first: timetablescheduler.domain.school.TimeSlot
    val second: timetablescheduler.domain.school.TimeSlot

    init {
        world.teachingDays(school.id, MONDAY, TUESDAY)
        first = world.slot(school.id, "1. Stunde", "08:00", "08:45")
        second = world.slot(school.id, "2. Stunde", "08:50", "09:35")
    }

    val roomA = world.room(school.id, "A-101")
    val roomB = world.room(school.id, "B-102")
    val math = world.subject(school.id, "Mathematik")
    val level5 = world.classLevel(school.id, "5", math to 2)
    val class5a = world.schoolClass(level5, "a")
    val schmidt = world.teacher(school.id, "Anna Schmidt", "SCHA", weeklyLoad = 4, qualifiedSubjects = setOf(math.id))
    val mueller = world.teacher(school.id, "Peter Müller", "MUEL", weeklyLoad = 4, qualifiedSubjects = setOf(math.id))

    val scheduling get() = world.useCases.scheduling

    fun mathLesson(teacher: timetablescheduler.domain.teacher.Teacher = schmidt, day: java.time.DayOfWeek = MONDAY, early: Boolean = true) =
        LessonAssignment(class5a.id, math.id, teacher.id, roomA.id, DaySlot(day, if (early) first.id else second.id))

    /** Both periods placed, taught by [teacher]. */
    fun complete(teacher: timetablescheduler.domain.teacher.Teacher = schmidt) =
        TimetableCandidate(listOf(mathLesson(teacher, MONDAY), mathLesson(teacher, TUESDAY)), emptyMap())

    /** Only one period placed. */
    fun halfFilled(teacher: timetablescheduler.domain.teacher.Teacher = schmidt) =
        TimetableCandidate(listOf(mathLesson(teacher, MONDAY)), mapOf(CourseKey(class5a.id, math.id) to 1))

    /** Violates a hard constraint: both lessons in the same slot (class double-booked). */
    fun invalid() = TimetableCandidate(listOf(mathLesson(schmidt, MONDAY), mathLesson(schmidt, MONDAY).copy(roomId = roomB.id)), emptyMap())
}
