package timetablescheduler.adapters.web.layout

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import kotlinx.html.FlowContent
import kotlinx.html.FORM
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.fieldSet
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.legend
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.ul
import timetablescheduler.adapters.web.i18n.UiTexts
import timetablescheduler.adapters.web.constraint.constraintList
import timetablescheduler.adapters.web.i18n.texts
import timetablescheduler.adapters.web.readiness.issueEntry
import timetablescheduler.adapters.web.readiness.readinessPath
import timetablescheduler.application.error.ApplicationError
import timetablescheduler.domain.common.RoomFeatureId
import timetablescheduler.domain.room.RoomFeature
import timetablescheduler.domain.validation.ValidationError

/** Lists business errors above a form (SC-003: every violated rule is named). */
fun FlowContent.validationErrors(errors: List<ValidationError>, texts: UiTexts) {
    if (errors.isEmpty()) return
    div(classes = "errors") {
        p { +texts.errors.invalidInput }
        ul { errors.forEach { li { +texts.errors.describe(it) } } }
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

/** Room features as checkboxes named [fieldName]; nothing is shown while the school has no features. */
fun FORM.featureCheckboxes(fieldName: String, features: List<RoomFeature>, checkedIds: Set<RoomFeatureId>, texts: UiTexts) {
    if (features.isEmpty()) return
    fieldSet {
        legend { +texts.common.roomFeatures }
        features.forEach { feature ->
            label {
                input(type = InputType.checkBox, name = fieldName) {
                    value = "${feature.id.value}"
                    checked = feature.id in checkedIds
                }
                +feature.name
            }
        }
    }
}

/**
 * Answers a business failure that has no form to re-render (constitution III: business messages may name
 * entries the planner may see; they are not logged with that content).
 */
suspend fun ApplicationCall.respondBusinessFailure(error: ApplicationError, backLink: String? = null) {
    val texts = texts
    when (error) {
        ApplicationError.NotFound -> respondMessage(HttpStatusCode.NotFound, texts.errors.notFound)
        ApplicationError.AccessDenied -> respondMessage(HttpStatusCode.Forbidden, texts.errors.accessDenied)
        ApplicationError.AuthenticationFailed -> respondMessage(HttpStatusCode.Unauthorized, texts.auth.loginFailed)
        ApplicationError.VersionConflict -> respondPage(texts.errors.conflictTitle, HttpStatusCode.Conflict) {
            p { +texts.errors.versionConflict }
            if (backLink != null) a(href = backLink) { +texts.errors.reload }
        }
        is ApplicationError.DeletionBlocked -> respondPage(texts.errors.deletionBlockedTitle, HttpStatusCode.Conflict) {
            p { +texts.errors.deletionBlocked }
            ul {
                error.visibleDependants.forEach { dependant ->
                    val kind = dependant.constraintType?.let { type -> "${texts.errors.describe(dependant.type)} „${texts.constraints.typeName(type)}“" }
                    li { +"${kind ?: texts.errors.describe(dependant.type)}: ${dependant.displayName}" }
                }
                if (error.hiddenDependantCount > 0) li { +texts.errors.hiddenDependants(error.hiddenDependantCount) }
            }
            if (backLink != null) a(href = backLink) { +texts.common.back }
        }
        is ApplicationError.GenerationBlocked -> respondPage(texts.generations.blockedTitle, HttpStatusCode.Conflict) {
            p { +texts.generations.blockedIntro }
            ul { error.blockingIssues.forEach { issue -> li { issueEntry(error.names, issue, texts) } } }
            a(href = readinessPath(error.names.schoolId)) { +texts.generations.toReadiness }
        }
        is ApplicationError.ContradictingConstraints -> respondPage(texts.constraints.contradictionTitle, HttpStatusCode.Conflict) {
            p { +texts.constraints.contradictionIntro }
            constraintList(error.constraints, error.names, texts)
            if (backLink != null) a(href = backLink) { +texts.common.back }
        }
        is ApplicationError.GenerationRunning -> respondPage(texts.generations.runningTitle, HttpStatusCode.Conflict) {
            p { +texts.generations.runningExists }
            if (backLink != null) a(href = "$backLink/${error.generationId.value}") { +texts.generations.toRunning }
        }
        is ApplicationError.Invalid -> respondPage(texts.errors.invalidInputTitle, HttpStatusCode.UnprocessableEntity) {
            validationErrors(error.errors, texts)
            if (backLink != null) a(href = backLink) { +texts.common.back }
        }
    }
}

suspend fun ApplicationCall.respondMessage(status: HttpStatusCode, message: String) {
    val texts = texts
    respondPage(if (status.value >= 500) texts.common.errorTitle else texts.common.noticeTitle, status) { p { +message } }
}
