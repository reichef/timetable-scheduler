package timetablescheduler.adapters.web.layout

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import kotlinx.html.FlowContent
import kotlinx.html.FORM
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.ul
import timetablescheduler.application.error.ApplicationError
import timetablescheduler.domain.validation.ValidationError

/** Lists business errors above a form (SC-003: every violated rule is named). */
fun FlowContent.validationErrors(errors: List<ValidationError>) {
    if (errors.isEmpty()) return
    div(classes = "errors") {
        p { +Messages.INVALID_INPUT }
        ul { errors.forEach { li { +Messages.describe(it) } } }
    }
}

fun FORM.textField(name: String, labelText: String, currentValue: String?, inputType: InputType = InputType.text) {
    label {
        +labelText
        input(type = inputType, name = name) {
            if (currentValue != null && inputType != InputType.password) value = currentValue
        }
    }
}

/**
 * Answers a business failure that has no form to re-render (constitution III: business messages may name
 * entries the planner may see; they are not logged with that content).
 */
suspend fun ApplicationCall.respondBusinessFailure(error: ApplicationError, backLink: String? = null) {
    when (error) {
        ApplicationError.NotFound -> respondMessage(HttpStatusCode.NotFound, Messages.NOT_FOUND)
        ApplicationError.AccessDenied -> respondMessage(HttpStatusCode.Forbidden, Messages.ACCESS_DENIED)
        ApplicationError.AuthenticationFailed -> respondMessage(HttpStatusCode.Unauthorized, Messages.LOGIN_FAILED)
        ApplicationError.VersionConflict -> respondPage("Konflikt", HttpStatusCode.Conflict) {
            p { +Messages.VERSION_CONFLICT }
            if (backLink != null) a(href = backLink) { +Messages.RELOAD }
        }
        is ApplicationError.DeletionBlocked -> respondPage("Löschen nicht möglich", HttpStatusCode.Conflict) {
            p { +Messages.DELETION_BLOCKED }
            ul {
                error.visibleDependants.forEach { li { +"${Messages.describe(it.type)}: ${it.displayName}" } }
                if (error.hiddenDependantCount > 0) li { +Messages.hiddenDependants(error.hiddenDependantCount) }
            }
            if (backLink != null) a(href = backLink) { +"Zurück" }
        }
        is ApplicationError.Invalid -> respondPage("Ungültige Eingabe", HttpStatusCode.UnprocessableEntity) {
            validationErrors(error.errors)
            if (backLink != null) a(href = backLink) { +"Zurück" }
        }
    }
}

suspend fun ApplicationCall.respondMessage(status: HttpStatusCode, message: String) {
    respondPage(if (status.value >= 500) "Fehler" else "Hinweis", status) { p { +message } }
}
