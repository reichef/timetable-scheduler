package timetablescheduler.domain.scheduling.constraint

import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.scheduling.TestSchool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EffectiveConstraintsTest {

    private val school = TestSchool()
    private val math = school.subject()
    private val german = school.subject()
    private val class5a = school.schoolClass(math to 4, german to 4)
    private val class6b = school.schoolClass(math to 4, german to 4)

    private fun applied(vararg constraints: SchedulingConstraint) = EffectiveConstraints.resolve(school.problem().copy(constraints = constraints.toList()))

    @Test
    fun `a constraint for all classes applies to every class, including classes created later (FR-013b)`() {
        val noGaps = soft(ConstraintRule.ClassNoGaps(ClassScope.AllClasses))
        val before = applied(noGaps)
        val class7c = school.schoolClass(math to 2)

        val after = applied(noGaps)

        assertEquals(setOf(class5a, class6b), before.map { it.classId }.toSet())
        assertEquals(setOf(class5a, class6b, class7c), after.map { it.classId }.toSet())
    }

    @Test
    fun `a constraint for a specific class replaces the school-wide one of the same type for that class`() {
        val schoolWide = soft(ConstraintRule.ClassMaxPeriodsPerDay(ClassScope.AllClasses, 6))
        val for6b = hard(ConstraintRule.ClassMaxPeriodsPerDay(ClassScope.SpecificClass(class6b), 5))

        val result = applied(schoolWide, for6b)

        assertEquals(setOf(schoolWide.id to class5a, for6b.id to class6b), result.map { it.constraint.id to it.classId }.toSet())
    }

    @Test
    fun `a subject-specific daily limit of a class replaces the school-wide limit only for that subject (US2 scenario 9)`() {
        val schoolWide = soft(ConstraintRule.SubjectMaxPerDayInClass(ClassScope.AllClasses, null, 2))
        val mathIn6b = soft(ConstraintRule.SubjectMaxPerDayInClass(ClassScope.SpecificClass(class6b), math, 1))

        val result = applied(schoolWide, mathIn6b).map { Triple(it.constraint.id, it.classId, it.subjectId) }.toSet()

        assertEquals(
            setOf(
                Triple(schoolWide.id, class5a, math), Triple(schoolWide.id, class5a, german),
                Triple(mathIn6b.id, class6b, math), Triple(schoolWide.id, class6b, german),
            ),
            result,
        )
    }

    @Test
    fun `switched-off constraints are ignored`() {
        assertTrue(applied(soft(ConstraintRule.ClassNoGaps(ClassScope.AllClasses), active = false)).isEmpty())
    }

    @Test
    fun `every school starts with two soft school-wide defaults of weight medium (FR-013c)`() {
        val schoolId = SchoolId.new()

        val defaults = DefaultConstraints.forSchool(schoolId)

        assertEquals(
            listOf(ConstraintRule.SubjectMaxPerDayInClass(ClassScope.AllClasses, null, 2), ConstraintRule.ClassNoGaps(ClassScope.AllClasses)),
            defaults.map { it.rule },
        )
        assertTrue(defaults.all { it.kind == ConstraintKind.SOFT && it.weight == SoftWeight.MEDIUM && it.active && it.schoolId == schoolId })
    }
}
