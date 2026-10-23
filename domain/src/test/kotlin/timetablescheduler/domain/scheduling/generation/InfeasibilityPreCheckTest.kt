package timetablescheduler.domain.scheduling.generation

import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.scheduling.TestSchool
import timetablescheduler.domain.scheduling.constraint.ClassScope
import timetablescheduler.domain.scheduling.constraint.ConstraintRule
import timetablescheduler.domain.scheduling.constraint.SchedulingConstraint
import timetablescheduler.domain.scheduling.constraint.TeacherFixation
import timetablescheduler.domain.scheduling.constraint.TeacherFixationId
import timetablescheduler.domain.scheduling.constraint.hard
import timetablescheduler.domain.scheduling.constraint.soft
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.TUESDAY
import kotlin.test.Test
import kotlin.test.assertEquals

class InfeasibilityPreCheckTest {
    /** Two days with 4 teaching slots each: 8 slots per week. */
    private val school = TestSchool(teachingSlotsPerDay = 4)
    private val math = school.subject()
    private val teacher = school.teacher(8, setOf(math))
    private val class5a = school.schoolClass(math to 6)

    private fun contradictionsOf(vararg constraints: SchedulingConstraint) =
        InfeasibilityPreCheck.contradictionsOf(school.problem().copy(constraints = constraints.toList()))

    @Test
    fun `consistent hard constraints and soft constraints are no contradiction`() {
        assertEquals(
            emptyList(),
            contradictionsOf(
                hard(ConstraintRule.ClassMinPeriodsPerDay(ClassScope.SpecificClass(class5a), 3)),
                soft(ConstraintRule.ClassMinPeriodsPerDay(ClassScope.AllClasses, 4)),
            ),
        )
    }

    @Test
    fun `a hard minimum above the teaching slots of a day is named (FR-017)`() {
        val tooHigh = hard(ConstraintRule.ClassMinPeriodsPerDay(ClassScope.SpecificClass(class5a), 5))

        assertEquals(listOf(tooHigh.id), contradictionsOf(tooHigh))
    }

    @Test
    fun `a hard minimum over all days above the class's periods is named`() {
        // 4 per day × 2 days = 8 > 6 periods
        val tooMany = hard(ConstraintRule.ClassMinPeriodsPerDay(ClassScope.AllClasses, 4))

        assertEquals(listOf(tooMany.id), contradictionsOf(tooMany))
    }

    @Test
    fun `hard exclusions leaving fewer slots than a class needs are named`() {
        val excluded =
            hard(ConstraintRule.ClassExcludedSlots(class5a, setOf(school.slot(MONDAY, 0), school.slot(MONDAY, 1), school.slot(TUESDAY, 0))))

        // 8 slots − 3 excluded = 5 < 6 periods
        assertEquals(listOf(excluded.id), contradictionsOf(excluded))
    }

    @Test
    fun `hard teacher exclusions leaving fewer slots than the teacher's fixed periods are named`() {
        val problem = school.problem()
        val fixation = TeacherFixation(TeacherFixationId.new(), SchoolId.new(), class5a, math, teacher, 0)
        val excluded =
            hard(
                ConstraintRule.TeacherExcludedSlots(teacher, setOf(school.slot(MONDAY, 0), school.slot(MONDAY, 1), school.slot(MONDAY, 2))),
            )

        assertEquals(
            listOf(excluded.id),
            InfeasibilityPreCheck.contradictionsOf(problem.copy(constraints = listOf(excluded), fixations = listOf(fixation))),
        )
    }

    @Test
    fun `hard subject exclusions leaving fewer slots than a course needs are named, for the class they name only`() {
        val threeSlots = setOf(school.slot(MONDAY, 0), school.slot(MONDAY, 1), school.slot(TUESDAY, 0))
        val class5b = school.schoolClass(math to 4)
        val forEveryClass = hard(ConstraintRule.SubjectExcludedSlots(math, null, threeSlots))
        val forClass5b = hard(ConstraintRule.SubjectExcludedSlots(math, class5b, threeSlots))

        // 8 slots − 3 excluded = 5: too few for the 6 math periods of 5a, enough for the 4 of 5b
        assertEquals(listOf(forEveryClass.id), contradictionsOf(forEveryClass, forClass5b))
    }
}
