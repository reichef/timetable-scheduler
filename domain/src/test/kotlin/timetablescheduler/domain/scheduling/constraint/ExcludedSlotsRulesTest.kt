package timetablescheduler.domain.scheduling.constraint

import timetablescheduler.domain.scheduling.DaySlot
import timetablescheduler.domain.scheduling.TestSchool
import timetablescheduler.domain.validation.ValidationError
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.TUESDAY
import kotlin.test.Test
import kotlin.test.assertEquals

class ExcludedSlotsRulesTest {
    private val school = TestSchool(teachingSlotsPerDay = 4, breakAfterSlot = 2)
    private val room = school.room()
    private val math = school.subject()
    private val art = school.subject()
    private val schmidt = school.teacher(20, setOf(math, art))
    private val mueller = school.teacher(20, setOf(math, art))
    private val class5a = school.schoolClass(math to 4, art to 2)
    private val class5b = school.schoolClass(math to 4)
    private val mondayFirst = school.slot(MONDAY, 0)

    @Test
    fun `a class lesson in an excluded slot counts once`() {
        val excluded = soft(ConstraintRule.ClassExcludedSlots(class5a, setOf(mondayFirst)))

        assertEquals(
            1,
            school.violationsOf(
                excluded,
                listOf(school.lesson(class5a, math, schmidt, room, MONDAY, 0), school.lesson(class5b, math, mueller, room, MONDAY, 0)),
            ),
        )
    }

    @Test
    fun `a teacher lesson in an excluded slot counts once (preferred free slots when soft)`() {
        val freeMonday = soft(ConstraintRule.TeacherExcludedSlots(schmidt, setOf(mondayFirst, school.slot(MONDAY, 1))))

        assertEquals(
            2,
            school.violationsOf(
                freeMonday,
                listOf(
                    school.lesson(class5a, math, schmidt, room, MONDAY, 0),
                    school.lesson(class5b, math, schmidt, room, MONDAY, 1),
                    school.lesson(class5a, art, mueller, room, MONDAY, 1),
                ),
            ),
        )
    }

    @Test
    fun `a subject exclusion applies to every class or only the given one`() {
        val lessons = listOf(school.lesson(class5a, math, schmidt, room, MONDAY, 0), school.lesson(class5b, math, mueller, room, MONDAY, 0))

        assertEquals(2, school.violationsOf(soft(ConstraintRule.SubjectExcludedSlots(math, null, setOf(mondayFirst))), lessons))
        assertEquals(1, school.violationsOf(soft(ConstraintRule.SubjectExcludedSlots(math, class5b, setOf(mondayFirst))), lessons))
    }

    @Test
    fun `excluded slots must be teaching slots on teaching days and at least one is needed (FR-015)`() {
        val problem = school.problem()

        assertEquals(
            listOf(ValidationError.BlankValue(ConstraintValidation.FIELD_SLOT)),
            ConstraintValidation.validate(ConstraintRule.ClassExcludedSlots(class5a, emptySet()), ConstraintKind.HARD, problem),
        )
        assertEquals(
            listOf(ValidationError.InvalidReference(ConstraintValidation.FIELD_SLOT)),
            ConstraintValidation.validate(
                ConstraintRule.ClassExcludedSlots(class5a, setOf(DaySlot(TUESDAY, school.breakSlot!!))),
                ConstraintKind.HARD,
                problem,
            ),
        )
        assertEquals(
            listOf(ValidationError.InvalidReference(ConstraintValidation.FIELD_SLOT)),
            ConstraintValidation.validate(
                ConstraintRule.TeacherExcludedSlots(schmidt, setOf(school.slot(SATURDAY, 0))),
                ConstraintKind.HARD,
                problem,
            ),
        )
        assertEquals(
            listOf(ValidationError.InvalidReference(ConstraintValidation.FIELD_SUBJECT)),
            ConstraintValidation.validate(
                ConstraintRule.SubjectExcludedSlots(art, class5b, setOf(mondayFirst)),
                ConstraintKind.HARD,
                problem,
            ),
        )
    }
}
