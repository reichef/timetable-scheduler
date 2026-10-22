package timetablescheduler.domain.scheduling.constraint

import timetablescheduler.domain.scheduling.TestSchool
import timetablescheduler.domain.validation.ValidationError
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.TUESDAY
import kotlin.test.Test
import kotlin.test.assertEquals

class SubjectRulesTest {

    /** Six teaching slots with a break after the third: 1 2 3 | 4 5 6. */
    private val school = TestSchool(teachingSlotsPerDay = 6, breakAfterSlot = 3)
    private val room = school.room()
    private val math = school.subject()
    private val german = school.subject()
    private val art = school.subject()
    private val teacher = school.teacher(30, setOf(math, german, art))
    private val class5a = school.schoolClass(math to 5, german to 4, art to 1)

    private fun lesson(subject: timetablescheduler.domain.common.SubjectId, day: java.time.DayOfWeek, slot: Int) =
        school.lesson(class5a, subject, teacher, room, day, slot)

    @Test
    fun `a subject above its daily limit counts the excess periods`() {
        val mathAtMostTwo = soft(ConstraintRule.SubjectMaxPerDayInClass(ClassScope.SpecificClass(class5a), math, 2))

        val lessons = listOf(lesson(math, MONDAY, 0), lesson(math, MONDAY, 1), lesson(math, MONDAY, 2), lesson(german, MONDAY, 3), lesson(german, MONDAY, 4))
        assertEquals(1, school.violationsOf(mathAtMostTwo, lessons))
    }

    @Test
    fun `without a subject the limit applies to every subject of the class`() {
        val everySubjectAtMostTwo = soft(ConstraintRule.SubjectMaxPerDayInClass(ClassScope.SpecificClass(class5a), null, 2))

        val lessons = (0..2).map { lesson(math, MONDAY, it) } + (3..5).map { lesson(german, MONDAY, it) }
        assertEquals(2, school.violationsOf(everySubjectAtMostTwo, lessons))
    }

    @Test
    fun `double periods count lessons that are not part of a pair, one single allowed for an odd number`() {
        val mathDouble = soft(ConstraintRule.SubjectDoublePeriodsInClass(class5a, math, acrossBreaks = false))

        // 5 periods: pair Mon 1-2, pair Tue 4-5, single Tue 1 → fine
        assertEquals(0, school.violationsOf(mathDouble, listOf(lesson(math, MONDAY, 0), lesson(math, MONDAY, 1), lesson(math, TUESDAY, 3), lesson(math, TUESDAY, 4), lesson(math, TUESDAY, 0))))
        // pair only Mon 1-2; Mon 4, Tue 1, Tue 6 single → 3 singles, 1 allowed
        assertEquals(2, school.violationsOf(mathDouble, listOf(lesson(math, MONDAY, 0), lesson(math, MONDAY, 1), lesson(math, MONDAY, 3), lesson(math, TUESDAY, 0), lesson(math, TUESDAY, 5))))
    }

    @Test
    fun `a pair across the break counts only when allowed`() {
        val lessons = listOf(lesson(german, MONDAY, 2), lesson(german, MONDAY, 3), lesson(german, TUESDAY, 0), lesson(german, TUESDAY, 1))

        assertEquals(2, school.violationsOf(soft(ConstraintRule.SubjectDoublePeriodsInClass(class5a, german, acrossBreaks = false)), lessons))
        assertEquals(0, school.violationsOf(soft(ConstraintRule.SubjectDoublePeriodsInClass(class5a, german, acrossBreaks = true)), lessons))
    }

    @Test
    fun `subject rules need the subject in the class's curriculum and double periods at least 2 weekly periods`() {
        val other = school.subject()
        val problem = school.problem()

        assertEquals(
            listOf(ValidationError.InvalidReference(ConstraintValidation.FIELD_SUBJECT)),
            ConstraintValidation.validate(ConstraintRule.SubjectMaxPerDayInClass(ClassScope.SpecificClass(class5a), other, 1), ConstraintKind.SOFT, problem),
        )
        assertEquals(
            listOf(ValidationError.InvalidReference(ConstraintValidation.FIELD_SUBJECT)),
            ConstraintValidation.validate(ConstraintRule.SubjectDoublePeriodsInClass(class5a, art, acrossBreaks = false), ConstraintKind.SOFT, problem),
        )
        assertEquals(emptyList(), ConstraintValidation.validate(ConstraintRule.SubjectMaxPerDayInClass(ClassScope.AllClasses, null, 2), ConstraintKind.SOFT, problem))
    }
}
