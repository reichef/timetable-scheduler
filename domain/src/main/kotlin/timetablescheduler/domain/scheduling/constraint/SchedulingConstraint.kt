package timetablescheduler.domain.scheduling.constraint

import timetablescheduler.domain.common.SchoolId
import java.util.UUID

@JvmInline
value class SchedulingConstraintId(val value: UUID) {
    companion object {
        fun new() = SchedulingConstraintId(UUID.randomUUID())
    }
}

/** Must never be violated (hard) or should be respected as far as possible (soft, with a weight) — FR-012. */
enum class ConstraintKind { HARD, SOFT }

/** Importance of a soft constraint (FR-014); violations count [factor] times (G3: 1 / 3 / 9). */
enum class SoftWeight(val factor: Int) {
    LOW(1),
    MEDIUM(3),
    HIGH(9),
}

/**
 * A planner-defined scheduling constraint (FR-012). [weight] is set exactly for soft constraints; switched-off
 * constraints ([active] = false) are kept but ignored by generations.
 */
data class SchedulingConstraint(
    val id: SchedulingConstraintId,
    val schoolId: SchoolId,
    val kind: ConstraintKind,
    val weight: SoftWeight?,
    val active: Boolean,
    val rule: ConstraintRule,
    val version: Int,
) {
    init {
        require((kind == ConstraintKind.SOFT) == (weight != null)) { "Exactly soft constraints carry a weight" }
    }

    val isHard: Boolean get() = kind == ConstraintKind.HARD
}
