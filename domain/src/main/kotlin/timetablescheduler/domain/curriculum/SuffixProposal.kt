package timetablescheduler.domain.curriculum

/** FR-013: proposes the next class suffix of a level. */
object SuffixProposal {
    /** The smallest letter `a..z` not used by [usedSuffixes] (case-insensitive), or null if all are taken. */
    fun next(usedSuffixes: Collection<String>): String? {
        val used = usedSuffixes.map { it.lowercase() }.toSet()
        return ('a'..'z').map { "$it" }.firstOrNull { it !in used }
    }
}
