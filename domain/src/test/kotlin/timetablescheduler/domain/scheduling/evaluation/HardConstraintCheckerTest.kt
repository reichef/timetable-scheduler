package timetablescheduler.domain.scheduling.evaluation

import timetablescheduler.domain.common.RoomFeatureId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.scheduling.constraint.ClassScope
import timetablescheduler.domain.scheduling.constraint.ConstraintRule
import timetablescheduler.domain.scheduling.constraint.TeacherFixation
import timetablescheduler.domain.scheduling.constraint.TeacherFixationId
import timetablescheduler.domain.scheduling.constraint.hard
import timetablescheduler.domain.scheduling.constraint.soft
import timetablescheduler.domain.scheduling.DaySlot
import timetablescheduler.domain.scheduling.TestSchool
import timetablescheduler.domain.scheduling.solution.LessonAssignment
import timetablescheduler.domain.scheduling.solution.TimetableCandidate
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.TUESDAY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HardConstraintCheckerTest {

    private val school = TestSchool(breakAfterSlot = 2)
    private val lab = RoomFeatureId.new()
    private val roomA = school.room()
    private val roomB = school.room()
    private val chemistryLab = school.room(setOf(lab))
    private val math = school.subject()
    private val german = school.subject()
    private val chemistry = school.subject(setOf(lab))
    private val schmidt = school.teacher(10, setOf(math, chemistry))
    private val mueller = school.teacher(10, setOf(math, german))
    private val class5a = school.schoolClass(math to 2, german to 1, chemistry to 1)
    private val class5b = school.schoolClass(math to 2)

    private fun violations(vararg lessons: LessonAssignment) =
        HardConstraintChecker.violationsOf(school.problem(), TimetableCandidate(lessons.toList(), emptyMap()))

    private fun rulesOf(vararg lessons: LessonAssignment) = violations(*lessons).map { it.rule }.toSet()

    @Test
    fun `a valid timetable has no violations`() {
        assertEquals(
            emptyList(),
            violations(
                school.lesson(class5a, math, schmidt, roomA, MONDAY, 0),
                school.lesson(class5a, math, schmidt, roomA, TUESDAY, 0),
                school.lesson(class5a, german, mueller, roomA, MONDAY, 1),
                school.lesson(class5a, chemistry, schmidt, chemistryLab, MONDAY, 2),
                school.lesson(class5b, math, mueller, roomB, MONDAY, 0),
            ),
        )
    }

    @Test
    fun `a teacher booked twice in one slot is reported with both lessons`() {
        val first = school.lesson(class5a, math, schmidt, roomA, MONDAY, 0)
        val second = school.lesson(class5b, math, schmidt, roomB, MONDAY, 0)

        val violation = violations(first, second).single()

        assertEquals(HardRule.TEACHER_DOUBLE_BOOKED, violation.rule)
        assertEquals(setOf(first, second), violation.lessons.toSet())
    }

    @Test
    fun `a class booked twice in one slot is reported`() {
        assertEquals(
            setOf(HardRule.CLASS_DOUBLE_BOOKED),
            rulesOf(school.lesson(class5a, math, schmidt, roomA, MONDAY, 0), school.lesson(class5a, german, mueller, roomB, MONDAY, 0)),
        )
    }

    @Test
    fun `a room booked twice in one slot is reported`() {
        assertEquals(
            setOf(HardRule.ROOM_DOUBLE_BOOKED),
            rulesOf(school.lesson(class5a, math, schmidt, roomA, MONDAY, 0), school.lesson(class5b, math, mueller, roomA, MONDAY, 0)),
        )
    }

    @Test
    fun `a teacher not qualified for the subject is reported`() {
        assertEquals(setOf(HardRule.TEACHER_NOT_QUALIFIED), rulesOf(school.lesson(class5a, german, schmidt, roomA, MONDAY, 0)))
    }

    @Test
    fun `a room lacking a required feature is reported`() {
        assertEquals(setOf(HardRule.ROOM_LACKS_FEATURE), rulesOf(school.lesson(class5a, chemistry, schmidt, roomA, MONDAY, 0)))
    }

    @Test
    fun `a lesson without required features outside the home room is reported (FR-002a)`() {
        val class6a = school.schoolClass(math to 1, chemistry to 1, homeRoom = roomA)

        assertEquals(setOf(HardRule.OUTSIDE_HOME_ROOM), rulesOf(school.lesson(class6a, math, schmidt, roomB, MONDAY, 0)))
        assertEquals(emptySet(), rulesOf(school.lesson(class6a, math, schmidt, roomA, MONDAY, 0)))
        assertEquals(emptySet(), rulesOf(school.lesson(class6a, chemistry, schmidt, chemistryLab, MONDAY, 0)))
    }

    @Test
    fun `lessons in a break or on a day without teaching are reported`() {
        val inBreak = LessonAssignment(class5a, math, schmidt, roomA, DaySlot(MONDAY, school.breakSlot!!))
        val onSaturday = school.lesson(class5a, math, schmidt, roomA, SATURDAY, 0)

        assertEquals(setOf(HardRule.NOT_A_TEACHING_SLOT), rulesOf(inBreak))
        assertEquals(setOf(HardRule.NOT_A_TEACHING_SLOT), rulesOf(onSaturday))
    }

    @Test
    fun `lessons above the weekly load are reported, the latest ones of the week`() {
        val lowLoad = school.teacher(1, setOf(math))
        val monday = school.lesson(class5a, math, lowLoad, roomA, MONDAY, 0)
        val tuesday = school.lesson(class5a, math, lowLoad, roomA, TUESDAY, 0)

        val violation = violations(tuesday, monday).single()

        assertEquals(HardRule.TEACHER_LOAD_EXCEEDED, violation.rule)
        assertEquals(listOf(tuesday), violation.lessons)
    }

    @Test
    fun `a lesson in a slot in which the teacher is unavailable is reported`() {
        val absent = school.teacher(10, setOf(math), unavailable = setOf(school.slot(MONDAY, 0)))

        assertEquals(setOf(HardRule.TEACHER_UNAVAILABLE), rulesOf(school.lesson(class5a, math, absent, roomA, MONDAY, 0)))
        assertEquals(emptySet(), rulesOf(school.lesson(class5a, math, absent, roomA, MONDAY, 1)))
    }

    @Test
    fun `two teachers for the periods of one subject in one class are reported (FR-004)`() {
        assertEquals(
            setOf(HardRule.SEVERAL_TEACHERS_FOR_COURSE),
            rulesOf(school.lesson(class5a, math, schmidt, roomA, MONDAY, 0), school.lesson(class5a, math, mueller, roomA, TUESDAY, 0)),
        )
    }

    @Test
    fun `lessons beyond the curriculum are reported`() {
        val lessons = (0..2).map { school.lesson(class5b, math, mueller, roomB, MONDAY, it) }

        val violation = violations(*lessons.toTypedArray()).single()

        assertEquals(HardRule.LESSON_NOT_REQUIRED, violation.rule)
        assertEquals(listOf(lessons[2]), violation.lessons)
    }

    @Test
    fun `every violation names its lessons`() {
        val result = violations(
            school.lesson(class5a, german, schmidt, roomA, MONDAY, 0),
            school.lesson(class5a, chemistry, schmidt, roomA, MONDAY, 0),
        )

        assertTrue(result.isNotEmpty())
        assertTrue(result.all { it.lessons.isNotEmpty() })
    }

    @Test
    fun `an active hard planner constraint is checked and named, a soft or switched-off one is not`() {
        val atMostOne = hard(ConstraintRule.ClassMaxPeriodsPerDay(ClassScope.SpecificClass(class5a), 1))
        val lessons = listOf(school.lesson(class5a, math, schmidt, roomA, MONDAY, 0), school.lesson(class5a, german, mueller, roomA, MONDAY, 1))

        val violation = HardConstraintChecker.violationsOf(school.problem().copy(constraints = listOf(atMostOne)), TimetableCandidate(lessons, emptyMap())).single()

        assertEquals(HardRule.PLANNER_CONSTRAINT, violation.rule)
        assertEquals(atMostOne.id, violation.constraintId)
        assertEquals(listOf(lessons[1]), violation.lessons)
        assertEquals(emptyList(), HardConstraintChecker.violationsOf(school.problem().copy(constraints = listOf(soft(atMostOne.rule))), TimetableCandidate(lessons, emptyMap())))
        assertEquals(emptyList(), HardConstraintChecker.violationsOf(school.problem().copy(constraints = listOf(hard(atMostOne.rule, active = false))), TimetableCandidate(lessons, emptyMap())))
    }

    @Test
    fun `lessons of a fixed course taught by another teacher are reported (FR-013a)`() {
        val fixation = TeacherFixation(TeacherFixationId.new(), SchoolId.new(), class5a, math, schmidt, 0)
        val byMueller = school.lesson(class5a, math, mueller, roomA, MONDAY, 0)

        val violation = HardConstraintChecker.violationsOf(school.problem().copy(fixations = listOf(fixation)), TimetableCandidate(listOf(byMueller), emptyMap())).single()

        assertEquals(HardRule.TEACHER_FIXATION, violation.rule)
        assertEquals(listOf(byMueller), violation.lessons)
    }
}
