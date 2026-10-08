package timetablescheduler.application.error

import timetablescheduler.domain.validation.ValidationResult

sealed interface UseCaseResult<out T> {
    data class Success<T>(val value: T) : UseCaseResult<T>
    data class Failure(val error: ApplicationError) : UseCaseResult<Nothing>
}

/** Signals a business failure inside [outcome]; never escapes it. */
class BusinessFailure(val error: ApplicationError) : RuntimeException(null, null, false, false)

/**
 * Lets a use case be written as straight-line code: [OutcomeScope.bind] unwraps a result or stops the
 * block with its failure, which [outcome] turns back into a [UseCaseResult.Failure].
 */
inline fun <T> outcome(block: OutcomeScope.() -> T): UseCaseResult<T> =
    try {
        UseCaseResult.Success(OutcomeScope.block())
    } catch (failure: BusinessFailure) {
        UseCaseResult.Failure(failure.error)
    }

object OutcomeScope {
    fun fail(error: ApplicationError): Nothing = throw BusinessFailure(error)

    fun <T> UseCaseResult<T>.bind(): T = when (this) {
        is UseCaseResult.Success -> value
        is UseCaseResult.Failure -> fail(error)
    }

    fun <T> ValidationResult<T>.bind(): T = when (this) {
        is ValidationResult.Valid -> value
        is ValidationResult.Invalid -> fail(ApplicationError.Invalid(errors))
    }

    /** Fails with a version conflict if an optimistic-locking update touched no row (FR-027). */
    fun requireUpdated(updated: Boolean) {
        if (!updated) fail(ApplicationError.VersionConflict)
    }

    fun <T : Any> T?.orNotFound(): T = this ?: fail(ApplicationError.NotFound)
}
