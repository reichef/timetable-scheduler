package timetablescheduler.domain.scheduling.solution

import timetablescheduler.domain.scheduling.TestSchool
import timetablescheduler.domain.scheduling.evaluation.ObjectiveEvaluator
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.TUESDAY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TimetableComparisonTest {

    private val school = TestSchool(teachingSlotsPerDay = 3)
    private val roomA = school.room()
    private val roomB = school.room()
    private val math = school.subject()
    private val german = school.subject()
    private val schmidt = school.teacher(10, setOf(math, german))
    private val mueller = school.teacher(10, setOf(math, german))
    private val class5a = school.schoolClass(math to 3, german to 2)
    private val class5b = school.schoolClass(math to 2)

    private fun lesson(subject: timetablescheduler.domain.common.SubjectId, day: java.time.DayOfWeek, slot: Int, teacher: timetablescheduler.domain.common.TeacherId = schmidt, room: timetablescheduler.domain.common.RoomId = roomA, schoolClass: timetablescheduler.domain.common.SchoolClassId = class5a) =
        school.lesson(schoolClass, subject, teacher, room, day, slot)

    private val reference = listOf(lesson(math, MONDAY, 0), lesson(math, MONDAY, 1), lesson(math, TUESDAY, 0), lesson(german, TUESDAY, 1), lesson(german, TUESDAY, 2))

    @Test
    fun `the diff lists the day-slots whose lesson differs for the selected class (FR-020)`() {
        val other = listOf(lesson(math, MONDAY, 0), lesson(german, MONDAY, 1), lesson(math, TUESDAY, 0, room = roomB), lesson(german, TUESDAY, 2))

        val differing = TimetableDiff.differingSlots(reference, other) { it.classId == class5a }

        assertEquals(setOf(school.slot(MONDAY, 1), school.slot(TUESDAY, 0), school.slot(TUESDAY, 1)), differing)
    }

    @Test
    fun `the diff can be taken for a teacher`() {
        val other = reference.map { if (it.subjectId == german) it.copy(teacherId = mueller) else it }

        assertEquals(setOf(school.slot(TUESDAY, 1), school.slot(TUESDAY, 2)), TimetableDiff.differingSlots(reference, other) { it.teacherId == schmidt })
    }

    @Test
    fun `changed lessons are those of the reference not kept with the same slot, teacher and room (FR-006b)`() {
        val candidate = listOf(lesson(math, MONDAY, 0), lesson(math, MONDAY, 1), lesson(math, TUESDAY, 1), lesson(german, TUESDAY, 2), lesson(german, MONDAY, 2, teacher = mueller))

        assertEquals(2, Stability.changedLessons(reference, candidate))
    }

    @Test
    fun `stability is the share of the reference kept and part of the objectives only with a reference`() {
        val problem = school.problem().copy(reference = reference)
        val keepsFour = reference.dropLast(1)

        assertEquals(0.8, ObjectiveEvaluator.evaluate(problem, TimetableCandidate(keepsFour, emptyMap())).values.stability)
        assertEquals(1, ObjectiveEvaluator.evaluate(problem, TimetableCandidate(keepsFour, emptyMap())).changedLessons)
        assertNull(ObjectiveEvaluator.evaluate(school.problem(), TimetableCandidate(keepsFour, emptyMap())).values.stability)
    }

    @Test
    fun `against the current curricula, missing lessons are not placed and surplus ones no longer required, the latest of the week (FR-023)`() {
        val chosen = reference + lesson(math, MONDAY, 2, schoolClass = class5b, teacher = mueller)
        val problem = school.problem().copy(
            classes = school.problem().classes.map { schoolClass ->
                when (schoolClass.id) {
                    class5a -> schoolClass.copy(curriculum = schoolClass.curriculum.map { if (it.subjectId == math) it.copy(periodsPerWeek = 2) else it })
                    else -> schoolClass
                }
            },
        )

        val drift = ChosenTimetableDrift.of(chosen, problem)

        assertEquals(listOf(lesson(math, TUESDAY, 0)), drift.noLongerRequired)
        assertEquals(mapOf(CourseKey(class5b, math) to 1), drift.notPlaced)
    }

    @Test
    fun `a removed subject makes all its lessons no longer required and a new class is not placed entirely`() {
        val class6a = school.schoolClass(math to 2)
        val problem = school.problem().copy(classes = school.problem().classes.map { if (it.id == class5a) it.copy(curriculum = it.curriculum.filter { e -> e.subjectId == math }) else it })

        val drift = ChosenTimetableDrift.of(reference, problem)

        assertEquals(reference.filter { it.subjectId == german }.toSet(), drift.noLongerRequired.toSet())
        assertEquals(mapOf(CourseKey(class5b, math) to 2, CourseKey(class6a, math) to 2), drift.notPlaced)
    }
}
