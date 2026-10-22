package timetablescheduler.domain.scheduling.generation

import timetablescheduler.domain.common.AccountId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.scheduling.solution.CourseKey
import timetablescheduler.domain.scheduling.solution.LessonAssignment
import java.time.Instant

/**
 * The timetable the planner chose for the school (FR-021): a copy of the alternative's lessons and of the periods it
 * could not place, so it stays unchanged when new generations run or old ones are deleted. [version] detects
 * concurrent choices (FR-028).
 */
data class ChosenTimetable(
    val schoolId: SchoolId,
    val alternativeId: AlternativeId,
    val generationId: GenerationId,
    val chosenBy: AccountId,
    val chosenAt: Instant,
    val lessons: List<LessonAssignment>,
    val unplaced: Map<CourseKey, Int>,
    val version: Int,
)
