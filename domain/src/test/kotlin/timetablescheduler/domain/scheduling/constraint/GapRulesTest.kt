package timetablescheduler.domain.scheduling.constraint

import timetablescheduler.domain.scheduling.TestSchool
import timetablescheduler.domain.validation.ValidationError
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.TUESDAY
import kotlin.test.Test
import kotlin.test.assertEquals

class GapRulesTest {

    /** Six teaching slots with a break after the third: 1 2 3 | 4 5 6. */
    private val school = TestSchool(teachingSlotsPerDay = 6, breakAfterSlot = 3)
    private val room = school.room()
    private val math = school.subject()
    private val teacher = school.teacher(20, setOf(math))
    private val class5a = school.schoolClass(math to 10)

    private fun lessonsAt(day: java.time.DayOfWeek, vararg slots: Int) = slots.map { school.lesson(class5a, math, teacher, room, day, it) }

    @Test
    fun `free teaching slots between the first and last lesson of a day are gaps`() {
        val noGaps = soft(ConstraintRule.ClassNoGaps(ClassScope.SpecificClass(class5a)))

        assertEquals(0, school.violationsOf(noGaps, lessonsAt(MONDAY, 0, 1, 2, 3)))
        assertEquals(2, school.violationsOf(noGaps, lessonsAt(MONDAY, 0, 3, 4) + lessonsAt(TUESDAY, 1, 2)))
    }

    @Test
    fun `the break is never counted as a gap`() {
        val noGaps = soft(ConstraintRule.ClassNoGaps(ClassScope.SpecificClass(class5a)))

        // 3rd teaching slot, break, 4th teaching slot: no gap
        assertEquals(0, school.violationsOf(noGaps, lessonsAt(MONDAY, 2, 3)))
    }

    @Test
    fun `teacher gaps above the limit count per day`() {
        val atMostOneGap = soft(ConstraintRule.TeacherMaxGapsPerDay(teacher, 1))

        // Monday: lessons in 1 and 5 → gaps 2, 3, 4 = 3 gaps, 2 above the limit
        assertEquals(2, school.violationsOf(atMostOneGap, lessonsAt(MONDAY, 0, 4)))
        assertEquals(0, school.violationsOf(atMostOneGap, lessonsAt(MONDAY, 0, 2)))
    }

    @Test
    fun `the teacher gap limit lies between 0 and the teaching slots per day minus 2`() {
        val problem = school.problem()

        assertEquals(emptyList(), ConstraintValidation.validate(ConstraintRule.TeacherMaxGapsPerDay(teacher, 0), ConstraintKind.HARD, problem))
        assertEquals(
            listOf(ValidationError.OutOfRange(ConstraintValidation.FIELD_LIMIT, 0, 4)),
            ConstraintValidation.validate(ConstraintRule.TeacherMaxGapsPerDay(teacher, 5), ConstraintKind.HARD, problem),
        )
    }
}
