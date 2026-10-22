package timetablescheduler.domain.scheduling.pareto

/**
 * The weight vectors of the solver passes of one generation (G2): each pass optimizes the weighted sum of the
 * normalized objectives, so different vectors steer the solver to different trade-offs of the Pareto front.
 */
object WeightVectors {

    private const val EMPHASIS = 0.6
    private const val SPREAD_VECTORS = 3

    /** 1 equal vector, 1 emphasizing each objective in turn, then [SPREAD_VECTORS] mixed ones — 2 + n + 2 in total. */
    fun forObjectives(objectiveCount: Int): List<List<Double>> {
        require(objectiveCount >= 1) { "At least one objective is needed" }
        return listOf(equal(objectiveCount)) +
            (0 until objectiveCount).map { emphasizing(it, objectiveCount) } +
            (1..SPREAD_VECTORS).map { spread(it, objectiveCount) }
    }

    private fun equal(objectiveCount: Int) = List(objectiveCount) { 1.0 / objectiveCount }

    /** [EMPHASIS] on one objective, the rest shared equally by the others. */
    private fun emphasizing(objective: Int, objectiveCount: Int): List<Double> {
        if (objectiveCount == 1) return listOf(1.0)
        val rest = (1 - EMPHASIS) / (objectiveCount - 1)
        return List(objectiveCount) { if (it == objective) EMPHASIS else rest }
    }

    /**
     * Deterministic uneven mixes: objective i gets raw weight 1 + ((i + step) mod n) · step, normalized to sum 1.
     * Rotating by [step] and scaling by it yields vectors between the emphasized ones, e.g. (0.67, 0.33),
     * (0.25, 0.75) and (0.8, 0.2) for two objectives.
     */
    private fun spread(step: Int, objectiveCount: Int): List<Double> {
        val raw = List(objectiveCount) { 1.0 + ((it + step) % objectiveCount) * step }
        return raw.map { it / raw.sum() }
    }
}
