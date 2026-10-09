package timetablescheduler.adapters.web.generation

import timetablescheduler.adapters.web.TestSystem
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.scheduling.DaySlot
import timetablescheduler.domain.scheduling.solution.CourseKey
import timetablescheduler.domain.scheduling.solution.LessonAssignment
import timetablescheduler.domain.scheduling.solution.TimetableCandidate
import timetablescheduler.domain.school.SlotKind
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.TUESDAY

/** A ready school for generation pages: 2 days, slots 1 / break / 2, class 5a with Mathematik 2 and Deutsch 1. */
class GenerationTestData(system: TestSystem) {
    val world = system.world
    val schoolId: SchoolId = system.firstSchoolId()

    init {
        world.teachingDays(schoolId, MONDAY, TUESDAY)
    }

    val first = world.slot(schoolId, "1. Stunde", "08:00", "08:45")
    val pause = world.slot(schoolId, "Große Pause", "08:45", "09:05", SlotKind.BREAK)
    val second = world.slot(schoolId, "2. Stunde", "09:05", "09:50")
    val roomA = world.room(schoolId, "A-101")
    val math = world.subject(schoolId, "Mathematik")
    val german = world.subject(schoolId, "Deutsch")
    val level5 = world.classLevel(schoolId, "5", math to 2, german to 1)
    val class5a = world.schoolClass(level5, "a")
    val schmidt = world.teacher(schoolId, "Anna Schmidt", "SCHA", weeklyLoad = 4, qualifiedSubjects = setOf(math.id, german.id))

    val generationsPath = "/schools/${schoolId.value}/generations"

    private fun lesson(subject: timetablescheduler.domain.curriculum.Subject, day: java.time.DayOfWeek, early: Boolean) =
        LessonAssignment(class5a.id, subject.id, schmidt.id, roomA.id, DaySlot(day, if (early) first.id else second.id))

    /** All three lessons placed. */
    fun complete() = TimetableCandidate(listOf(lesson(math, MONDAY, true), lesson(math, TUESDAY, true), lesson(german, MONDAY, false)), emptyMap())

    /** German is missing. */
    fun withoutGerman() = TimetableCandidate(listOf(lesson(math, MONDAY, true), lesson(math, TUESDAY, false)), mapOf(CourseKey(class5a.id, german.id) to 1))
}
