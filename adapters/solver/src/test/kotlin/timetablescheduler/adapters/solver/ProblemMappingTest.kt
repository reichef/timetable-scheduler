package timetablescheduler.adapters.solver

import timetablescheduler.adapters.solver.model.ObjectiveLayout
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.scheduling.constraint.ClassScope
import timetablescheduler.domain.scheduling.constraint.ConstraintKind
import timetablescheduler.domain.scheduling.constraint.ConstraintRule
import timetablescheduler.domain.scheduling.constraint.SchedulingConstraint
import timetablescheduler.domain.scheduling.constraint.SchedulingConstraintId
import timetablescheduler.domain.scheduling.constraint.SoftWeight
import kotlin.test.Test
import kotlin.test.assertEquals

/** The rule facts carry the objective layout of the domain into the solver (G3, FR-006a). */
class ProblemMappingTest {
    private val school = SeededSchool(days = 2, teachingSlotsPerDay = 6, breakAfterSlot = 3)
    private val math = school.subject()
    private val art = school.subject()
    private val class5a = school.schoolClass(listOf(math to 6, art to 2), homeRoom = school.room())

    init {
        school.teacher(20, setOf(math, art))
    }

    private fun soft(
        rule: ConstraintRule,
        weight: SoftWeight,
    ) = SchedulingConstraint(SchedulingConstraintId.new(), SchoolId.new(), ConstraintKind.SOFT, weight, true, rule, 0)

    @Test
    fun `a separate objective gets its own index and normalizer, the other soft constraints share the next one`() {
        val atMostThree = soft(ConstraintRule.ClassMaxPeriodsPerDay(ClassScope.SpecificClass(class5a), 3), SoftWeight.MEDIUM)
        val noGaps = soft(ConstraintRule.ClassNoGaps(ClassScope.AllClasses), SoftWeight.HIGH)
        val problem = school.problem().copy(constraints = listOf(atMostThree, noGaps), separateObjectiveIds = listOf(atMostThree.id))

        val facts = ProblemMapping.toPlan(problem).rules.associateBy { it.applied.constraint.id }

        // the daily maximum can be exceeded once per period of the class: 8; a gap fits 6 − 2 slots on each of 2 days: 8
        val separate = facts.getValue(atMostThree.id)
        val combined = facts.getValue(noGaps.id)
        assertEquals(
            Triple(ObjectiveLayout.FIRST_SEPARATE, 1, 8.0),
            Triple(separate.objectiveIndex, separate.weightFactor, separate.normalizer),
        )
        assertEquals(
            Triple(ObjectiveLayout.FIRST_SEPARATE + 1, SoftWeight.HIGH.factor, SoftWeight.HIGH.factor * 8.0),
            Triple(combined.objectiveIndex, combined.weightFactor, combined.normalizer),
        )
    }
}
