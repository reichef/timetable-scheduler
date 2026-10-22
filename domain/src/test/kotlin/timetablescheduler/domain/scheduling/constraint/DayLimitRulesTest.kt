package timetablescheduler.domain.scheduling.constraint

import timetablescheduler.domain.common.SchoolClassId
import timetablescheduler.domain.common.TeacherId
import timetablescheduler.domain.scheduling.TestSchool
import timetablescheduler.domain.validation.ValidationError
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.TUESDAY
import kotlin.test.Test
import kotlin.test.assertEquals

class DayLimitRulesTest {

    private val school = TestSchool(teachingSlotsPerDay = 6)
    private val room = school.room()
    private val math = school.subject()
    private val teacher = school.teacher(20, setOf(math))
    private val class5a = school.schoolClass(math to 8)

    private fun mondayLessons(count: Int) = (0 until count).map { school.lesson(class5a, math, teacher, room, MONDAY, it) }

    @Test
    fun `class maximum per day counts the excess periods per day`() {
        val atMostFour = soft(ConstraintRule.ClassMaxPeriodsPerDay(ClassScope.SpecificClass(class5a), 4))

        assertEquals(0, school.violationsOf(atMostFour, mondayLessons(4)))
        assertEquals(2, school.violationsOf(atMostFour, mondayLessons(6)))
    }

    @Test
    fun `class minimum per day counts the missing periods on every teaching day`() {
        val atLeastThree = soft(ConstraintRule.ClassMinPeriodsPerDay(ClassScope.SpecificClass(class5a), 3))

        // Monday 4 (fine), Tuesday 1 (2 missing)
        val lessons = mondayLessons(4) + school.lesson(class5a, math, teacher, room, TUESDAY, 0)
        assertEquals(2, school.violationsOf(atLeastThree, lessons))
    }

    @Test
    fun `teacher maximum per day counts the excess periods per day`() {
        val atMostTwo = soft(ConstraintRule.TeacherMaxPeriodsPerDay(teacher, 2))

        assertEquals(3, school.violationsOf(atMostTwo, mondayLessons(5)))
    }

    @Test
    fun `limits must lie within the teaching slots of the longest day (FR-015)`() {
        val problem = school.problem()

        assertEquals(
            listOf(ValidationError.OutOfRange(ConstraintValidation.FIELD_LIMIT, 1, 6)),
            ConstraintValidation.validate(ConstraintRule.ClassMaxPeriodsPerDay(ClassScope.SpecificClass(class5a), 0), ConstraintKind.HARD, problem),
        )
        assertEquals(
            listOf(ValidationError.OutOfRange(ConstraintValidation.FIELD_LIMIT, 1, 6)),
            ConstraintValidation.validate(ConstraintRule.TeacherMaxPeriodsPerDay(teacher, 7), ConstraintKind.SOFT, problem),
        )
        assertEquals(emptyList(), ConstraintValidation.validate(ConstraintRule.ClassMaxPeriodsPerDay(ClassScope.AllClasses, 6), ConstraintKind.SOFT, problem))
    }

    @Test
    fun `references to unknown classes or teachers are rejected`() {
        val problem = school.problem()

        assertEquals(
            listOf(ValidationError.InvalidReference(ConstraintValidation.FIELD_CLASS)),
            ConstraintValidation.validate(ConstraintRule.ClassMaxPeriodsPerDay(ClassScope.SpecificClass(SchoolClassId.new()), 4), ConstraintKind.HARD, problem),
        )
        assertEquals(
            listOf(ValidationError.InvalidReference(ConstraintValidation.FIELD_TEACHER)),
            ConstraintValidation.validate(ConstraintRule.TeacherMaxPeriodsPerDay(TeacherId.new(), 4), ConstraintKind.HARD, problem),
        )
    }

    @Test
    fun `a hard minimum above an active hard maximum of the same class is rejected`() {
        val maximum = hard(ConstraintRule.ClassMaxPeriodsPerDay(ClassScope.SpecificClass(class5a), 4))
        val problem = school.problem().copy(constraints = listOf(maximum))

        assertEquals(
            listOf(ValidationError.OutOfRange(ConstraintValidation.FIELD_LIMIT, 1, 4)),
            ConstraintValidation.validate(ConstraintRule.ClassMinPeriodsPerDay(ClassScope.SpecificClass(class5a), 5), ConstraintKind.HARD, problem),
        )
        assertEquals(emptyList(), ConstraintValidation.validate(ConstraintRule.ClassMinPeriodsPerDay(ClassScope.SpecificClass(class5a), 5), ConstraintKind.SOFT, problem))
    }
}
