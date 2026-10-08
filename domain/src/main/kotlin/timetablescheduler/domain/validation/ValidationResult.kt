package timetablescheduler.domain.validation

sealed interface ValidationResult<out T> {
    data class Valid<T>(val value: T) : ValidationResult<T>

    data class Invalid(val errors: List<ValidationError>) : ValidationResult<Nothing> {
        init {
            require(errors.isNotEmpty()) { "Invalid result needs at least one error" }
        }
    }
}

/** Collects all rule violations of one input so the user sees every problem at once (SC-003). */
class ValidationCollector {
    private val errors = mutableListOf<ValidationError>()

    fun add(error: ValidationError?) {
        if (error != null) errors += error
    }

    fun <T> result(valueFactory: () -> T): ValidationResult<T> =
        if (errors.isEmpty()) ValidationResult.Valid(valueFactory()) else ValidationResult.Invalid(errors.toList())
}

/** Trims [rawText] and reports a blank or too long value for [field]. */
fun ValidationCollector.requiredText(field: String, rawText: String, maxLength: Int): String {
    val trimmedText = rawText.trim()
    when {
        trimmedText.isEmpty() -> add(ValidationError.BlankValue(field))
        trimmedText.length > maxLength -> add(ValidationError.TooLong(field, maxLength))
    }
    return trimmedText
}

/** Reports [number] outside `min..max` for [field]; `max == null` means no upper bound. */
fun ValidationCollector.intInRange(field: String, number: Int, min: Int, max: Int?) {
    if (number < min || (max != null && number > max)) add(ValidationError.OutOfRange(field, min, max))
}
