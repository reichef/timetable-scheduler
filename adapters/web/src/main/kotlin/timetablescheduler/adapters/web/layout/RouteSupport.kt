package timetablescheduler.adapters.web.layout

import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import kotlinx.html.FORM
import kotlinx.html.InputType
import kotlinx.html.input
import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.domain.validation.ValidationError
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeParseException
import java.util.UUID

/** Reads an id from the URL; a malformed id is answered like an unknown one (404) and yields null. */
suspend fun ApplicationCall.idParameter(name: String): UUID? {
    val id = parameters[name]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    if (id == null) respondBusinessFailure(ApplicationError.NotFound)
    return id
}

/** Runs [onSuccess] for a successful result, otherwise answers the business failure. */
suspend fun <T> ApplicationCall.respondResult(
    result: UseCaseResult<T>,
    backLink: String? = null,
    onSuccess: suspend (T) -> Unit,
) {
    when (result) {
        is UseCaseResult.Success -> onSuccess(result.value)
        is UseCaseResult.Failure -> respondBusinessFailure(result.error, backLink)
    }
}

/**
 * For form posts: business-rule violations re-render the form via [onInvalid] (422 with the entered values),
 * other failures get their business page, success runs [onSuccess].
 */
suspend fun <T> ApplicationCall.respondFormResult(
    result: UseCaseResult<T>,
    backLink: String?,
    onInvalid: suspend (List<ValidationError>) -> Unit,
    onSuccess: suspend (T) -> Unit,
) {
    when (result) {
        is UseCaseResult.Success -> onSuccess(result.value)
        is UseCaseResult.Failure -> {
            val error = result.error
            if (error is ApplicationError.Invalid) onInvalid(error.errors) else respondBusinessFailure(error, backLink)
        }
    }
}

fun Parameters.text(name: String): String = this[name].orEmpty()

fun Parameters.version(): Int = this["version"]?.toIntOrNull() ?: -1

fun Parameters.uuids(name: String): Set<UUID> =
    getAll(name).orEmpty().mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }.toSet()

fun Parameters.daysOfWeek(name: String): Set<DayOfWeek> =
    getAll(name).orEmpty().mapNotNull { day -> DayOfWeek.entries.firstOrNull { it.name == day } }.toSet()

/** Collects form-format problems (not business rules) before a use case is called. */
class FormInput(val form: Parameters) {
    val errors = mutableListOf<ValidationError>()

    fun time(name: String): LocalTime? =
        try {
            LocalTime.parse(form.text(name))
        } catch (_: DateTimeParseException) {
            errors += ValidationError.InvalidFormat(name)
            null
        }

    fun int(name: String): Int? = form[name]?.trim()?.toIntOrNull() ?: run {
        errors += ValidationError.InvalidFormat(name)
        null
    }

    inline fun <reified E : Enum<E>> enum(name: String): E? =
        enumValues<E>().firstOrNull { it.name == form[name] } ?: run {
            errors += ValidationError.InvalidFormat(name)
            null
        }
}

fun FORM.hiddenField(name: String, fieldValue: String) {
    input(type = InputType.hidden, name = name) { value = fieldValue }
}

private val germanDayNames = mapOf(
    DayOfWeek.MONDAY to "Montag",
    DayOfWeek.TUESDAY to "Dienstag",
    DayOfWeek.WEDNESDAY to "Mittwoch",
    DayOfWeek.THURSDAY to "Donnerstag",
    DayOfWeek.FRIDAY to "Freitag",
    DayOfWeek.SATURDAY to "Samstag",
    DayOfWeek.SUNDAY to "Sonntag",
)

fun DayOfWeek.germanName(): String = germanDayNames.getValue(this)
