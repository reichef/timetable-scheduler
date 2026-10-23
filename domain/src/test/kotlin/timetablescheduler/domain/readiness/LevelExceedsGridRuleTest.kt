package timetablescheduler.domain.readiness

import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.common.SubjectId
import timetablescheduler.domain.curriculum.ClassLevel
import timetablescheduler.domain.curriculum.CurriculumEntry
import timetablescheduler.domain.validation.ValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LevelExceedsGridRuleTest {
    private val subjects = List(3) { SubjectId.new() }

    private fun levelWith(vararg periods: Int): ClassLevel =
        (ClassLevel.create(SchoolId.new(), "6") as ValidationResult.Valid)
            .value
            .update("6", periods.mapIndexed { index, count -> CurriculumEntry(subjects[index], count) }, subjects.toSet())
            .let { (it as ValidationResult.Valid).value }

    @Test
    fun `level needing more periods than the grid offers is reported with the excess`() {
        val level = levelWith(10, 10, 10)

        assertEquals(
            ReadinessIssue.LevelExceedsGrid(level.id, excessPeriods = 2),
            LevelExceedsGridRule.check(level, teachingSlotsPerWeek = 28),
        )
    }

    @Test
    fun `level exactly filling the grid is fine`() {
        assertNull(LevelExceedsGridRule.check(levelWith(10, 10, 8), teachingSlotsPerWeek = 28))
    }

    @Test
    fun `empty grid reports any curriculum`() {
        val level = levelWith(2)

        assertEquals(
            ReadinessIssue.LevelExceedsGrid(level.id, excessPeriods = 2),
            LevelExceedsGridRule.check(level, teachingSlotsPerWeek = 0),
        )
    }
}
