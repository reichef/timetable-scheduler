package timetablescheduler.adapters.web.generation

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.tr
import kotlinx.html.ul
import timetablescheduler.adapters.web.i18n.UiTexts
import timetablescheduler.adapters.web.i18n.texts
import timetablescheduler.adapters.web.layout.csrfField
import timetablescheduler.adapters.web.layout.respondPage
import timetablescheduler.adapters.web.layout.textField
import timetablescheduler.adapters.web.layout.validationErrors
import timetablescheduler.adapters.web.readiness.issueEntry
import timetablescheduler.application.scheduling.GenerationDetails
import timetablescheduler.application.scheduling.GenerationOverview
import timetablescheduler.application.scheduling.StartRequest
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.scheduling.generation.Alternative
import timetablescheduler.domain.scheduling.generation.Generation
import timetablescheduler.domain.scheduling.generation.GenerationStatus
import timetablescheduler.domain.validation.ValidationError
import java.time.Duration
import java.time.Instant

fun generationsPath(schoolId: SchoolId) = "/schools/${schoolId.value}/generations"

fun generationPath(generation: Generation) = "${generationsPath(generation.schoolId)}/${generation.id.value}"

fun alternativePath(generation: Generation, alternative: Alternative) = "${generationPath(generation)}/alternatives/${alternative.id.value}"

/** Seconds between automatic reloads of a running generation's page (G6). */
private const val REFRESH_SECONDS = 3

/** The objectives a result table can be sorted by; keys as in the routes contract. */
enum class ObjectiveColumn(val key: String, val valueOf: (Alternative) -> Double) {
    CLASS_FILL("classFill", { it.objectiveValues.classFill }),
    TEACHER_LOAD("teacherLoad", { it.objectiveValues.teacherLoadUsage }),
    OTHER_SOFT("otherSoft", { it.objectiveValues.otherSoft });

    companion object {
        fun fromKey(key: String?): ObjectiveColumn? = entries.firstOrNull { it.key == key }
    }
}

fun UiTexts.objectiveName(column: ObjectiveColumn): String = when (column) {
    ObjectiveColumn.CLASS_FILL -> generations.classFill
    ObjectiveColumn.TEACHER_LOAD -> generations.teacherLoad
    ObjectiveColumn.OTHER_SOFT -> generations.otherSoft
}

/** Start form (FR-009, FR-010) and the list of generations of the school (US4 scenario 1). */
suspend fun ApplicationCall.respondGenerationList(
    overview: GenerationOverview,
    errors: List<ValidationError> = emptyList(),
    enteredTimeLimit: String? = null,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    val texts = texts
    respondPage(texts.generations.title, status) { session ->
        h2 { +texts.generations.startHeading }
        validationErrors(errors, texts)
        form(action = generationsPath(overview.schoolId), method = FormMethod.post) {
            csrfField(session)
            textField("timeLimitMinutes", texts.generations.timeLimitLabel, enteredTimeLimit ?: "${Generation.DEFAULT_TIME_LIMIT_MINUTES}", InputType.number)
            if (overview.gate.warnings.isNotEmpty()) {
                p { +texts.generations.warningsIntro }
                ul { overview.gate.warnings.forEach { issue -> li { issueEntry(overview.readiness, issue, texts) } } }
                label {
                    input(type = InputType.checkBox, name = StartRequest.FIELD_WARNINGS_CONFIRMED) { value = "true" }
                    +texts.generations.warningsConfirm
                }
            }
            button { +texts.generations.startButton }
        }
        generationTable(overview, texts)
        p { a(href = "/schools/${overview.schoolId.value}") { +texts.common.toSchool } }
    }
}

private fun FlowContent.generationTable(overview: GenerationOverview, texts: UiTexts) {
    h2 { +texts.generations.title }
    if (overview.generations.isEmpty()) {
        p { +texts.generations.noGenerations }
        return
    }
    table {
        tr {
            th { +texts.generations.startedColumn }
            th { +texts.generations.startedByColumn }
            th { +texts.generations.statusColumn }
            th { +texts.generations.alternativesColumn }
        }
        overview.generations.forEach { summary ->
            val generation = summary.generation
            tr {
                td { a(href = generationPath(generation)) { +texts.generations.dateTime(generation.startedAt) } }
                td { +summary.startedByUsername }
                td { +texts.generations.statusName(generation.status) }
                td { +"${generation.alternativesFound}" }
            }
        }
    }
}

/**
 * A generation: progress with stop button and automatic reload while it runs (FR-011), afterwards the alternatives
 * with their objective values, sortable by every objective (FR-018, FR-020).
 */
suspend fun ApplicationCall.respondGenerationDetails(details: GenerationDetails, sortColumn: ObjectiveColumn?, descending: Boolean, now: Instant) {
    val texts = texts
    val generation = details.generation
    val refresh = if (generation.isRunning) REFRESH_SECONDS else null
    respondPage(texts.generations.generationTitle(texts.generations.dateTime(generation.startedAt)), refreshSeconds = refresh) { session ->
        p { +"${texts.generations.statusColumn}: ${texts.generations.statusName(generation.status)}" }
        if (generation.isRunning) {
            val elapsed = texts.generations.duration(Duration.between(generation.startedAt, now).seconds.coerceAtLeast(0))
            p { +texts.generations.progress(elapsed, generation.alternativesFound) }
            form(action = "${generationPath(generation)}/stop", method = FormMethod.post) {
                csrfField(session)
                button(classes = "danger") { +texts.generations.stopButton }
            }
        }
        if (generation.status == GenerationStatus.FAILED_INFEASIBLE) p(classes = "errors") { +texts.generations.infeasibleHint }
        alternativeTable(details, sortColumn, descending, texts)
        p { a(href = generationsPath(generation.schoolId)) { +texts.generations.toGenerations } }
    }
}

private fun FlowContent.alternativeTable(details: GenerationDetails, sortColumn: ObjectiveColumn?, descending: Boolean, texts: UiTexts) {
    h2 { +texts.generations.alternativesHeading }
    if (details.alternatives.isEmpty()) {
        p { +texts.generations.noAlternatives }
        return
    }
    val numbered = details.alternatives.mapIndexed { index, alternative -> index + 1 to alternative }
    val sorted = if (sortColumn == null) numbered else {
        val ascending = numbered.sortedBy { (_, alternative) -> sortColumn.valueOf(alternative) }
        if (descending) ascending.reversed() else ascending
    }
    val generation = details.generation
    table {
        tr {
            th { +"#" }
            ObjectiveColumn.entries.forEach { column ->
                th {
                    val nextDescending = !(column == sortColumn && descending)
                    a(href = "${generationPath(generation)}?sort=${column.key}&dir=${if (nextDescending) "desc" else "asc"}") { +texts.objectiveName(column) }
                }
            }
            th { +texts.generations.unplacedColumn }
            th { +texts.generations.softViolationsColumn }
            th { }
        }
        sorted.forEach { (number, alternative) ->
            tr {
                td { +"$number" }
                ObjectiveColumn.entries.forEach { column -> td { +texts.generations.percent(column.valueOf(alternative)) } }
                td { +"${alternative.unplacedPeriods()}" }
                td { +"${alternative.softViolations.values.sum()}" }
                td { a(href = alternativePath(generation, alternative)) { +texts.generations.alternativeTitle(number) } }
            }
        }
    }
}
