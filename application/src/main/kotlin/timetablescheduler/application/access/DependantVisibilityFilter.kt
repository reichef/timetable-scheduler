package timetablescheduler.application.access

import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.error.Dependant
import timetablescheduler.application.error.DependantType
import timetablescheduler.domain.account.UserAccount
import timetablescheduler.domain.common.SchoolId

/** An entry blocking a deletion, together with the schools it belongs to (needed for the visibility rule). */
data class DependantRef(val type: DependantType, val displayName: String, val schoolIds: Set<SchoolId>)

/** Finds the entries that would block deleting the entity identified by [K]. */
fun interface DependantSource<K> {
    fun dependantsOf(key: K): List<DependantRef>
}

/**
 * Constitution III / R10: a deletion-blocked business message may name only entries the planner may see,
 * i.e. entries sharing at least one school with the planner; all others are only counted.
 */
object DependantVisibilityFilter {

    fun deletionBlocked(dependants: List<DependantRef>, actor: UserAccount): ApplicationError.DeletionBlocked {
        val (visible, hidden) = dependants.partition { dependant -> dependant.schoolIds.any { it in actor.schoolIds } }
        return ApplicationError.DeletionBlocked(
            visibleDependants = visible.map { Dependant(it.type, it.displayName) }.sortedWith(compareBy({ it.type }, { it.displayName })),
            hiddenDependantCount = hidden.size,
        )
    }
}

/** Collects the dependants of [key] from all [sources]. */
fun <K> List<DependantSource<K>>.dependantsOf(key: K): List<DependantRef> = flatMap { it.dependantsOf(key) }
