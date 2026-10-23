package timetablescheduler.domain.account

/** The languages the user interface is offered in (FR-037); German is the reference language. */
enum class InterfaceLanguage(
    val code: String,
) {
    GERMAN("de"),
    ENGLISH("en"),
    ;

    companion object {
        /** The language with the two-letter [code] (case-insensitive), or null for unknown or missing codes. */
        fun fromCode(code: String?): InterfaceLanguage? {
            val normalized = code?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.code == normalized }
        }
    }
}
