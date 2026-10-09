package timetablescheduler.domain.scheduling.solution

import timetablescheduler.domain.scheduling.DaySlot
import timetablescheduler.domain.scheduling.problem.SchedulingProblem

/** FR-020: where two timetables differ for one class, teacher or room. */
object TimetableDiff {

    /**
     * The day-slots in which the lessons selected by [belongsToView] differ between [first] and [second]: another
     * lesson (class, subject, teacher or room) or a lesson in only one of them.
     */
    fun differingSlots(first: List<LessonAssignment>, second: List<LessonAssignment>, belongsToView: (LessonAssignment) -> Boolean): Set<DaySlot> {
        val firstBySlot = first.filter(belongsToView).groupBy { it.daySlot }.mapValues { it.value.toSet() }
        val secondBySlot = second.filter(belongsToView).groupBy { it.daySlot }.mapValues { it.value.toSet() }
        return (firstBySlot.keys + secondBySlot.keys).filter { firstBySlot[it] != secondBySlot[it] }.toSet()
    }
}

/** FR-006b: how much of a reference timetable an alternative keeps. */
object Stability {

    /**
     * Reference lessons not kept with the same day-slot, teacher and room. Lessons are matched as a multiset per class
     * and subject, so moving one of two Mathematics lessons counts as one change, not two.
     */
    fun changedLessons(reference: List<LessonAssignment>, lessons: List<LessonAssignment>): Int {
        val remaining = lessons.groupingBy { it }.eachCount().toMutableMap()
        return reference.count { lesson ->
            val left = remaining[lesson] ?: 0
            if (left > 0) {
                remaining[lesson] = left - 1
                false
            } else {
                true
            }
        }
    }

    /** 1 − changed / reference lessons; 1 for an empty reference. */
    fun of(reference: List<LessonAssignment>, lessons: List<LessonAssignment>): Double =
        if (reference.isEmpty()) 1.0 else 1.0 - changedLessons(reference, lessons).toDouble() / reference.size
}

/** How the chosen timetable differs from today's curricula (FR-023). */
data class Drift(val notPlaced: Map<CourseKey, Int>, val noLongerRequired: List<LessonAssignment>) {
    val isEmpty: Boolean get() = notPlaced.isEmpty() && noLongerRequired.isEmpty()
}

object ChosenTimetableDrift {

    /**
     * Compares the chosen lessons per class and subject with the periods the current curricula require: missing
     * periods are "not placed", surplus lessons "no longer required" — the last ones of the subject in week order
     * (latest day, then latest slot), so the marking is reproducible.
     */
    fun of(chosen: List<LessonAssignment>, problem: SchedulingProblem): Drift {
        val required = problem.courses()
        val chosenByCourse = chosen.groupBy { it.course }
        val notPlaced = required.mapValues { (course, periods) -> periods - (chosenByCourse[course]?.size ?: 0) }.filterValues { it > 0 }
        val noLongerRequired = chosenByCourse.flatMap { (course, lessons) ->
            lessons.sortedWith(compareBy(problem.weekOrder) { it.daySlot }).drop(required[course] ?: 0)
        }
        return Drift(notPlaced, noLongerRequired)
    }
}
