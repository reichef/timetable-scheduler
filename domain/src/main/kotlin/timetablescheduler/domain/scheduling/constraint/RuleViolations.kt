package timetablescheduler.domain.scheduling.constraint

import timetablescheduler.domain.scheduling.DaySlot
import timetablescheduler.domain.scheduling.problem.SchedulingProblem
import timetablescheduler.domain.scheduling.solution.CourseKey
import timetablescheduler.domain.scheduling.solution.LessonAssignment
import timetablescheduler.domain.school.SlotKind
import java.time.DayOfWeek

/**
 * How often an applied constraint is violated ([count]), by which lessons, and how many violations were possible at
 * most ([opportunities], ≥ 1) — the denominator that normalizes soft constraints to 0…1 (G3).
 */
data class RuleViolation(val count: Int, val lessons: List<LessonAssignment>, val opportunities: Int)

/** Counts the violations of every catalogue rule (G9); the solver adapter uses the same functions (G5). */
object RuleViolations {

    fun of(applied: AppliedConstraint, problem: SchedulingProblem, lessons: List<LessonAssignment>): RuleViolation {
        val week = WeekPositions(problem)
        return when (val rule = applied.rule) {
            is ConstraintRule.ClassMaxPeriodsPerDay ->
                excessPerDay(lessons.ofClass(applied), rule.limit, week, classPeriods(problem, applied))
            is ConstraintRule.ClassMinPeriodsPerDay -> missingPerDay(lessons.ofClass(applied), rule.limit, week)
            is ConstraintRule.ClassNoGaps -> gapsPerDay(lessons.ofClass(applied), allowedGaps = 0, week)
            is ConstraintRule.TeacherMaxPeriodsPerDay ->
                excessPerDay(lessons.filter { it.teacherId == rule.teacherId }, rule.limit, week, problem.teacher(rule.teacherId)?.weeklyLoad ?: 0)
            is ConstraintRule.TeacherMaxGapsPerDay -> gapsPerDay(lessons.filter { it.teacherId == rule.teacherId }, rule.limit, week)
            is ConstraintRule.SubjectMaxPerDayInClass -> {
                val course = CourseKey(applied.classId!!, applied.subjectId!!)
                excessPerDay(lessons.filter { it.course == course }, rule.limit, week, problem.requiredPeriods(course))
            }
            is ConstraintRule.SubjectDoublePeriodsInClass -> unpairedLessons(lessons, CourseKey(rule.classId, rule.subjectId), rule.acrossBreaks, problem, week)
            is ConstraintRule.ClassExcludedSlots ->
                inExcludedSlots(lessons.filter { it.classId == rule.classId }, rule.slots, classPeriods(problem, rule.classId))
            is ConstraintRule.TeacherExcludedSlots ->
                inExcludedSlots(lessons.filter { it.teacherId == rule.teacherId }, rule.slots, problem.teacher(rule.teacherId)?.weeklyLoad ?: 0)
            is ConstraintRule.SubjectExcludedSlots -> {
                val inScope = lessons.filter { it.subjectId == rule.subjectId && (rule.classId == null || it.classId == rule.classId) }
                val periods = problem.courses().filterKeys { it.subjectId == rule.subjectId && (rule.classId == null || it.classId == rule.classId) }.values.sum()
                inExcludedSlots(inScope, rule.slots, periods)
            }
        }
    }

    private fun List<LessonAssignment>.ofClass(applied: AppliedConstraint) = filter { it.classId == applied.classId }

    private fun classPeriods(problem: SchedulingProblem, applied: AppliedConstraint) = applied.classId?.let { classPeriods(problem, it) } ?: 0

    private fun classPeriods(problem: SchedulingProblem, classId: timetablescheduler.domain.common.SchoolClassId) =
        problem.schoolClass(classId)?.curriculum?.sumOf { it.periodsPerWeek } ?: 0

    /** Periods above [limit] on each day; the latest lessons of a day are the excess ones. */
    private fun excessPerDay(lessons: List<LessonAssignment>, limit: Int, week: WeekPositions, opportunities: Int): RuleViolation {
        val excess = lessons.groupBy { it.daySlot.day }.values.flatMap { dayLessons -> dayLessons.sortedBy { week.teachingIndex(it.daySlot) }.drop(limit) }
        return RuleViolation(excess.size, excess, opportunities.coerceAtLeast(1))
    }

    /** Periods below [limit] on each teaching day, also on days without any lesson. */
    private fun missingPerDay(lessons: List<LessonAssignment>, limit: Int, week: WeekPositions): RuleViolation {
        val byDay = lessons.groupBy { it.daySlot.day }
        val shortDays = week.days.filter { (byDay[it]?.size ?: 0) < limit }
        val missing = shortDays.sumOf { limit - (byDay[it]?.size ?: 0) }
        return RuleViolation(missing, shortDays.flatMap { byDay[it].orEmpty() }, (limit * week.days.size).coerceAtLeast(1))
    }

    /**
     * Free teaching slots between the first and last lesson of a day, beyond [allowedGaps]: the span of teaching
     * indices minus the lessons in it. Breaks are no teaching slots and therefore never gaps.
     */
    private fun gapsPerDay(lessons: List<LessonAssignment>, allowedGaps: Int, week: WeekPositions): RuleViolation {
        var count = 0
        val involved = mutableListOf<LessonAssignment>()
        lessons.groupBy { it.daySlot.day }.values.forEach { dayLessons ->
            val indices = dayLessons.map { week.teachingIndex(it.daySlot) }.filter { it >= 0 }.distinct()
            if (indices.size < 2) return@forEach
            val gaps = indices.max() - indices.min() + 1 - indices.size
            if (gaps > allowedGaps) {
                count += gaps - allowedGaps
                involved += dayLessons
            }
        }
        val opportunities = week.days.sumOf { (week.teachingSlotsOn(it) - 2).coerceAtLeast(0) }
        return RuleViolation(count, involved, opportunities.coerceAtLeast(1))
    }

    /**
     * Double periods: on each day the course's lessons are paired greedily in time order whenever two follow each other
     * directly (and, unless [acrossBreaks], without a break between them). One single lesson is allowed for an odd
     * number of weekly periods; every further single lesson is a violation.
     */
    private fun unpairedLessons(
        lessons: List<LessonAssignment>,
        course: CourseKey,
        acrossBreaks: Boolean,
        problem: SchedulingProblem,
        week: WeekPositions,
    ): RuleViolation {
        val singles = mutableListOf<LessonAssignment>()
        lessons.filter { it.course == course }.groupBy { it.daySlot.day }.values.forEach { dayLessons ->
            val ordered = dayLessons.sortedBy { week.teachingIndex(it.daySlot) }
            var position = 0
            while (position < ordered.size) {
                val next = ordered.getOrNull(position + 1)
                if (next != null && week.followDirectly(ordered[position].daySlot, next.daySlot, acrossBreaks)) {
                    position += 2
                } else {
                    singles += ordered[position]
                    position += 1
                }
            }
        }
        val required = problem.requiredPeriods(course)
        val violations = (singles.size - required % 2).coerceAtLeast(0)
        return RuleViolation(violations, if (violations > 0) singles else emptyList(), required.coerceAtLeast(1))
    }

    private fun inExcludedSlots(lessons: List<LessonAssignment>, excluded: Set<DaySlot>, opportunities: Int): RuleViolation {
        val violating = lessons.filter { it.daySlot in excluded }
        return RuleViolation(violating.size, violating, opportunities.coerceAtLeast(1))
    }
}

/** Positions of slots within their day, needed for gaps and double periods. */
private class WeekPositions(problem: SchedulingProblem) {
    val days: List<DayOfWeek> = problem.days.map { it.day }.sorted()
    private val teachingIndex: Map<DaySlot, Int> = problem.days.flatMap { day ->
        day.teachingSlots.mapIndexed { index, slotId -> DaySlot(day.day, slotId) to index }
    }.toMap()
    private val fullIndex: Map<DaySlot, Int> = problem.days.flatMap { day ->
        day.slots.mapIndexed { index, slot -> DaySlot(day.day, slot.slotId) to index }
    }.toMap()
    private val breakFree: Map<DayOfWeek, List<SlotKind>> = problem.days.associate { day -> day.day to day.slots.map { it.kind } }
    private val teachingCounts: Map<DayOfWeek, Int> = problem.days.associate { it.day to it.teachingSlots.size }

    fun teachingIndex(daySlot: DaySlot): Int = teachingIndex[daySlot] ?: -1

    fun teachingSlotsOn(day: DayOfWeek): Int = teachingCounts[day] ?: 0

    /** [second] is the next teaching slot after [first] on the same day; a break in between is allowed only if [acrossBreaks]. */
    fun followDirectly(first: DaySlot, second: DaySlot, acrossBreaks: Boolean): Boolean {
        if (first.day != second.day || teachingIndex(second) != teachingIndex(first) + 1) return false
        val from = fullIndex[first] ?: return false
        val to = fullIndex[second] ?: return false
        val between = breakFree[first.day].orEmpty().subList(from + 1, to)
        return acrossBreaks || between.none { it == SlotKind.BREAK }
    }
}
