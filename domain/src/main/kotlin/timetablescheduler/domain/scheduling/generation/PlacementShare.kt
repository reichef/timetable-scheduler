package timetablescheduler.domain.scheduling.generation

/**
 * FR-007a: the minimum share of placed lessons. It remembers the most lessons any alternative of the generation has
 * placed so far ([bestPlaced]) and admits only alternatives that place at least [sharePercent] percent of that, so an
 * almost empty timetable is never offered just because it violates no soft constraint.
 */
class PlacementShare(private val sharePercent: Int) {

    var bestPlaced: Int = 0
        private set

    /** Records an alternative placing [placed] lessons; true when it is the new best one. */
    fun record(placed: Int): Boolean {
        if (placed <= bestPlaced) {
            return false
        }

        bestPlaced = placed
        return true
    }

    /**
     * Whether an alternative placing [placed] lessons may be kept. Compared in whole numbers
     * (placed · 100 ≥ share · best), so no rounding decides — 90 % of 569 lessons needs 513, not 512. An alternative
     * without any placed lesson is never kept, even when no better one exists yet.
     */
    fun admits(placed: Int): Boolean =
        placed > 0 && placed * PERCENT >= sharePercent * bestPlaced

    private companion object {
        const val PERCENT = 100
    }
}
