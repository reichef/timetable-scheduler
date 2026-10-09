package timetablescheduler.domain.scheduling.solution

import kotlin.math.roundToLong

/**
 * How well an alternative achieves each objective (FR-006, G3). All values lie in 0…1 and higher is better, so
 * alternatives can be compared objective by objective. Values are kept rounded to 4 decimals (see [rounded]).
 *
 * - [classFill]: placed periods weighted by class priority
 * - [teacherLoadUsage]: how fully the teachers' weekly loads are used
 * - [separate]: one value per soft constraint compared as its own objective (FR-006a), in selection order
 * - [otherSoft]: all other soft constraints combined by their weights
 * - [stability]: only when starting from the chosen timetable — share of its lessons kept (FR-006b)
 */
data class ObjectiveValues(
    val classFill: Double,
    val teacherLoadUsage: Double,
    val separate: List<Double>,
    val otherSoft: Double,
    val stability: Double?,
) {
    /** The values in a fixed order, as compared by the Pareto dominance (FR-007). */
    fun asVector(): List<Double> = listOf(classFill, teacherLoadUsage) + separate + otherSoft + listOfNotNull(stability)

    companion object {
        fun rounded(
            classFill: Double,
            teacherLoadUsage: Double,
            separate: List<Double> = emptyList(),
            otherSoft: Double = 1.0,
            stability: Double? = null,
        ) = ObjectiveValues(round4(classFill), round4(teacherLoadUsage), separate.map(::round4), round4(otherSoft), stability?.let(::round4))

        /** Rounds to 4 decimals so tiny floating-point differences never decide dominance (G3). */
        fun round4(value: Double): Double = (value * 10_000).roundToLong() / 10_000.0
    }
}
