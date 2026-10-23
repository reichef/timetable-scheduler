package timetablescheduler.domain.readiness

import timetablescheduler.domain.curriculum.ClassLevel

/** `LEVEL_EXCEEDS_GRID` (acceptance 2.5): a class of the level cannot receive all periods in the weekly grid. */
object LevelExceedsGridRule {
    /** Reports the periods by which the level's weekly total exceeds [teachingSlotsPerWeek], or null if it fits. */
    fun check(
        level: ClassLevel,
        teachingSlotsPerWeek: Int,
    ): ReadinessIssue.LevelExceedsGrid? {
        val excessPeriods = level.weeklyPeriods() - teachingSlotsPerWeek
        return if (excessPeriods > 0) ReadinessIssue.LevelExceedsGrid(level.id, excessPeriods) else null
    }
}
