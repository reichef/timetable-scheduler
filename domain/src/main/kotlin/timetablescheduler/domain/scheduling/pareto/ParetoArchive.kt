package timetablescheduler.domain.scheduling.pareto

import timetablescheduler.domain.scheduling.solution.ObjectiveValues

object Dominance {
    /**
     * Pareto dominance for "higher is better" objectives: [first] dominates [second] when it is at least as good in
     * every objective and strictly better in at least one. Values are compared after rounding to 4 decimals (G3), so
     * floating-point noise never makes one alternative look better.
     */
    fun dominates(
        first: List<Double>,
        second: List<Double>,
    ): Boolean {
        require(first.size == second.size) { "Objective vectors differ in length" }

        val roundedFirst = first.map(ObjectiveValues::round4)
        val roundedSecond = second.map(ObjectiveValues::round4)
        val neverWorse = roundedFirst.indices.all { roundedFirst[it] >= roundedSecond[it] }
        val betterOnce = roundedFirst.indices.any { roundedFirst[it] > roundedSecond[it] }

        return neverWorse && betterOnce
    }
}

/** An archived alternative with its objective vector. */
data class ParetoEntry<T>(
    val item: T,
    val objectives: List<Double>,
)

/** What an offer changed: whether the offered item was kept and which items it pushed out. */
data class ArchiveChange<T>(
    val admitted: Boolean,
    val evicted: List<T>,
)

/**
 * Keeps the equally optimal alternatives found so far (FR-007, FR-008, G2): only items no other item dominates,
 * different timetables with identical values side by side, the same timetable once ([sameTimetable]), and at most
 * [capacity] items — beyond that the most crowded item is dropped so the trade-off range stays covered.
 */
class ParetoArchive<T>(
    val capacity: Int = DEFAULT_CAPACITY,
    private val sameTimetable: (T, T) -> Boolean,
) {
    private val kept = mutableListOf<ParetoEntry<T>>()

    val entries: List<ParetoEntry<T>> get() = kept.toList()

    fun offer(
        item: T,
        objectives: List<Double>,
    ): ArchiveChange<T> {
        val offered = ParetoEntry(item, objectives.map(ObjectiveValues::round4))
        val rejected = kept.any { Dominance.dominates(it.objectives, offered.objectives) || isDuplicate(it, offered) }
        if (rejected) {
            return ArchiveChange(admitted = false, evicted = emptyList())
        }

        val dominated = kept.filter { Dominance.dominates(offered.objectives, it.objectives) }
        kept.removeAll(dominated)
        kept += offered
        val pruned = if (kept.size > capacity) listOf(removeMostCrowded()) else emptyList()
        return ArchiveChange(admitted = offered in kept, evicted = (dominated + pruned).map { it.item }.filterNot { it === item })
    }

    /** Removes and returns the kept items matching [shouldRemove], e.g. those below the minimum share (FR-007a). */
    fun removeIf(shouldRemove: (T) -> Boolean): List<T> {
        val removed = kept.filter { shouldRemove(it.item) }

        kept.removeAll(removed)
        return removed.map { it.item }
    }

    private fun isDuplicate(
        existing: ParetoEntry<T>,
        offered: ParetoEntry<T>,
    ): Boolean = existing.objectives == offered.objectives && sameTimetable(existing.item, offered.item)

    /**
     * Crowding distance (NSGA-II): for every objective, sort the items and add the normalized gap between each item's
     * two neighbours; the best and worst item of every objective get an infinite distance and are therefore never
     * dropped. The item with the smallest distance lies in the densest region and is removed; on ties the most recently
     * added one goes, so earlier results stay stable.
     */
    private fun removeMostCrowded(): ParetoEntry<T> {
        val distances = DoubleArray(kept.size)
        val objectiveCount = kept.first().objectives.size
        for (objective in 0 until objectiveCount) {
            val order = kept.indices.sortedBy { kept[it].objectives[objective] }
            val range = kept[order.last()].objectives[objective] - kept[order.first()].objectives[objective]
            distances[order.first()] = Double.POSITIVE_INFINITY
            distances[order.last()] = Double.POSITIVE_INFINITY
            if (range == 0.0) {
                continue
            }
            for (position in 1 until order.size - 1) {
                val gap = kept[order[position + 1]].objectives[objective] - kept[order[position - 1]].objectives[objective]
                distances[order[position]] += gap / range
            }
        }
        val mostCrowded = kept.indices.reversed().minBy { distances[it] }
        return kept.removeAt(mostCrowded)
    }

    companion object {
        const val DEFAULT_CAPACITY = 10
    }
}
