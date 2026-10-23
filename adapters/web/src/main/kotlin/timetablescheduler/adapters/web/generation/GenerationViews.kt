package timetablescheduler.adapters.web.generation

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.fieldSet
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.legend
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.tr
import kotlinx.html.ul
import timetablescheduler.adapters.web.constraint.constraintList
import timetablescheduler.adapters.web.constraint.describe
import timetablescheduler.adapters.web.constraint.describeRule
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
import timetablescheduler.application.scheduling.TimetableNames
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.scheduling.generation.Alternative
import timetablescheduler.domain.scheduling.generation.Generation
import timetablescheduler.domain.scheduling.generation.GenerationStatus
import timetablescheduler.domain.validation.ValidationError
import java.time.Duration
import java.time.Instant

fun generationsPath(schoolId: SchoolId) = "/schools/${schoolId.value}/generations"

fun generationPath(generation: Generation) = "${generationsPath(generation.schoolId)}/${generation.id.value}"

fun alternativePath(
    generation: Generation,
    alternative: Alternative,
) = "${generationPath(generation)}/alternatives/${alternative.id.value}"

fun comparePath(generation: Generation) = "${generationPath(generation)}/compare"

fun diffPath(
    generation: Generation,
    first: Alternative,
    second: Alternative,
) = "${alternativePath(generation, first)}/diff/${second.id.value}"

/** Seconds between automatic reloads of a running generation's page (G6). */
private const val REFRESH_SECONDS = 3

/**
 * One objective of a generation's result table; keys as in the routes contract (classFill, separate1, …). Values are
 * shown as percentages; [display] can add more, e.g. the changed lessons next to stability.
 */
class ObjectiveColumn(
    val key: String,
    val title: String,
    private val texts: UiTexts,
    private val details: ((Alternative) -> String?)? = null,
    val valueOf: (Alternative) -> Double,
) {
    fun display(alternative: Alternative): String {
        val extra = details?.invoke(alternative)
        val percent = texts.generations.percent(valueOf(alternative))
        return if (extra == null) percent else "$percent ($extra)"
    }
}

/**
 * The objectives of a generation (FR-006): class fill, teacher load usage, each soft constraint compared separately
 * (named from the generation's snapshot, FR-006a, FR-022), the other soft constraints and — when the generation
 * started from the chosen timetable — stability with the number of changed lessons (FR-006b).
 */
fun objectiveColumns(
    generation: Generation,
    names: TimetableNames?,
    texts: UiTexts,
    alternatives: List<Alternative>,
): List<ObjectiveColumn> {
    val separate =
        generation.separateObjectiveIds.mapIndexed { index, constraintId ->
            val constraint = generation.constraintSnapshot.firstOrNull { it.id == constraintId }
            val title = if (constraint != null && names != null) describeRule(constraint, names, texts) else texts.timetables.deletedEntry
            ObjectiveColumn("separate${index + 1}", title, texts) { it.objectiveValues.separate.getOrNull(index) ?: 1.0 }
        }
    val withStability = generation.referenceAlternativeId != null || alternatives.any { it.objectiveValues.stability != null }
    val stability =
        ObjectiveColumn(
            "stability",
            texts.generations.stability,
            texts,
            details = { alternative -> alternative.changedLessons?.let(texts.generations::changedLessons) },
        ) { it.objectiveValues.stability ?: 1.0 }
    return listOf(
        ObjectiveColumn("classFill", texts.generations.classFill, texts) { it.objectiveValues.classFill },
        ObjectiveColumn("teacherLoad", texts.generations.teacherLoad, texts) { it.objectiveValues.teacherLoadUsage },
    ) + separate + ObjectiveColumn("otherSoft", texts.generations.otherSoft, texts) { it.objectiveValues.otherSoft } +
        listOfNotNull(stability.takeIf { withStability })
}

/** Start form (FR-009, FR-010) and the list of generations of the school (US4 scenario 1). */
suspend fun ApplicationCall.respondGenerationList(
    overview: GenerationOverview,
    errors: List<ValidationError> = emptyList(),
    enteredTimeLimit: String? = null,
    status: HttpStatusCode = HttpStatusCode.OK,
    startFromChosen: Boolean = false,
    enteredShare: String? = null,
) {
    val texts = texts
    respondPage(texts.generations.title, status) { session ->
        h2 { +texts.generations.startHeading }
        validationErrors(errors, texts)
        form(action = generationsPath(overview.schoolId), method = FormMethod.post) {
            csrfField(session)
            textField(
                "timeLimitMinutes",
                texts.generations.timeLimitLabel,
                enteredTimeLimit ?: "${Generation.DEFAULT_TIME_LIMIT_MINUTES}",
                InputType.number,
            )
            textField(
                StartRequest.FIELD_MINIMUM_PLACED_SHARE,
                texts.generations.minimumShareLabel,
                enteredShare ?: "${Generation.DEFAULT_MINIMUM_PLACED_SHARE}",
                InputType.number,
            )

            if (overview.gate.warnings.isNotEmpty()) {
                p { +texts.generations.warningsIntro }
                ul { overview.gate.warnings.forEach { issue -> li { issueEntry(overview.readiness, issue, texts) } } }
                label {
                    input(type = InputType.checkBox, name = StartRequest.FIELD_WARNINGS_CONFIRMED) { value = "true" }
                    +texts.generations.warningsConfirm
                }
            }

            val names = overview.names
            if (overview.softConstraints.isNotEmpty() && names != null) {
                fieldSet {
                    legend { +texts.constraints.separateObjectivesLegend }
                    overview.softConstraints.forEach { constraint ->
                        label {
                            input(type = InputType.checkBox, name = StartRequest.FIELD_SEPARATE_OBJECTIVES) {
                                value = "${constraint.id.value}"
                            }
                            +describe(constraint, names, texts)
                        }
                    }
                }
            }

            if (overview.hasChosenTimetable) {
                label {
                    input(type = InputType.checkBox, name = StartRequest.FIELD_START_FROM_CHOSEN) {
                        value = "true"
                        checked = startFromChosen
                    }
                    +texts.generations.startFromChosen
                }
            }

            button { +texts.generations.startButton }
        }
        generationTable(overview, texts)
        p { a(href = "/schools/${overview.schoolId.value}") { +texts.common.toSchool } }
    }
}

private fun FlowContent.generationTable(
    overview: GenerationOverview,
    texts: UiTexts,
) {
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
                td {
                    +texts.generations.statusName(generation.status)
                    if (summary.outdated) {
                        +" (${texts.generations.outdatedMark})"
                    }
                }
                td { +"${generation.alternativesFound}" }
            }
        }
    }
}

/**
 * A generation: progress with stop button and automatic reload while it runs (FR-011), afterwards the alternatives
 * with their objective values, sortable by every objective (FR-018, FR-020).
 */
suspend fun ApplicationCall.respondGenerationDetails(
    details: GenerationDetails,
    sortKey: String?,
    descending: Boolean,
    now: Instant,
) {
    val texts = texts
    val generation = details.generation
    val refresh = if (generation.isRunning) REFRESH_SECONDS else null
    respondPage(texts.generations.generationTitle(texts.generations.dateTime(generation.startedAt)), refreshSeconds = refresh) { session ->
        p { +"${texts.generations.statusColumn}: ${texts.generations.statusName(generation.status)}" }
        p { +texts.generations.minimumShare(generation.minimumPlacedShare) }
        if (details.summary.outdated) {
            p(classes = "warning") { +texts.generations.outdatedHint }
        }

        if (generation.isRunning) {
            val elapsed = texts.generations.duration(Duration.between(generation.startedAt, now).seconds.coerceAtLeast(0))
            p { +texts.generations.progress(elapsed, generation.alternativesFound) }
            form(action = "${generationPath(generation)}/stop", method = FormMethod.post) {
                csrfField(session)
                button(classes = "danger") { +texts.generations.stopButton }
            }
        }
        if (generation.status == GenerationStatus.FAILED_INFEASIBLE) {
            p(classes = "errors") { +texts.generations.infeasibleHint }
            details.names?.let { names ->
                constraintList(generation.constraintSnapshot.filter { it.id in generation.infeasibleConstraintIds }, names, texts)
            }
        }

        val columns = objectiveColumns(generation, details.names, texts, details.alternatives)
        if (details.alternatives.size > 1) {
            p { a(href = comparePath(generation)) { +texts.generations.compareLink } }
        }
        alternativeTable(details, columns, columns.firstOrNull { it.key == sortKey }, descending, texts) { key, dir ->
            "${generationPath(generation)}?sort=$key&dir=$dir"
        }
        constraintsInEffect(details, texts)

        p {
            a(href = generationsPath(generation.schoolId)) { +texts.generations.toGenerations }
            if (!generation.isRunning) {
                +" · "
                a(href = "${generationPath(generation)}/delete") { +texts.generations.deleteLink }
            }
        }
    }
}

/** US4: deleting a generation needs a second step, so it cannot happen by a single click. */
suspend fun ApplicationCall.respondDeleteConfirmation(details: GenerationDetails) {
    val texts = texts
    val generation = details.generation
    respondPage(texts.generations.deleteTitle) { session ->
        p { +"${texts.generations.generationTitle(texts.generations.dateTime(generation.startedAt))}: ${texts.generations.deleteIntro}" }
        form(action = "${generationPath(generation)}/delete", method = FormMethod.post, classes = "inline") {
            csrfField(session)
            button(classes = "danger") { +texts.generations.deleteButton }
        }
        +" "
        a(href = generationPath(generation)) { +texts.generations.cancel }
    }
}

/** The constraints and fixations of the generation's snapshot, named with today's names (FR-022). */
private fun FlowContent.constraintsInEffect(
    details: GenerationDetails,
    texts: UiTexts,
) {
    val names = details.names ?: return
    val generation = details.generation
    if (generation.constraintSnapshot.isEmpty() && generation.fixationSnapshot.isEmpty()) {
        return
    }

    h2 { +texts.constraints.inEffectHeading }
    ul {
        generation.constraintSnapshot.forEach { li { +describe(it, names, texts) } }
        generation.fixationSnapshot.forEach { li { +describe(it, names, texts) } }
    }
}

/** The alternatives with their objective values, sortable by every objective via [sortLink] (FR-018, FR-020). */
fun FlowContent.alternativeTable(
    details: GenerationDetails,
    columns: List<ObjectiveColumn>,
    sortColumn: ObjectiveColumn?,
    descending: Boolean,
    texts: UiTexts,
    sortLink: (key: String, direction: String) -> String,
) {
    h2 { +texts.generations.alternativesHeading }
    if (details.alternatives.isEmpty()) {
        p { +texts.generations.noAlternatives }
        return
    }

    val numbered = details.alternatives.mapIndexed { index, alternative -> index + 1 to alternative }
    val sorted =
        if (sortColumn == null) {
            numbered
        } else {
            val ascending = numbered.sortedBy { (_, alternative) -> sortColumn.valueOf(alternative) }
            if (descending) {
                ascending.reversed()
            } else {
                ascending
            }
        }

    val generation = details.generation
    table {
        tr {
            th { +"#" }
            columns.forEach { column ->
                th {
                    val nextDescending = !(column.key == sortColumn?.key && descending)
                    a(href = sortLink(column.key, if (nextDescending) "desc" else "asc")) { +column.title }
                }
            }
            th { +texts.generations.unplacedColumn }
            th { +texts.generations.softViolationsColumn }
            th { }
        }
        sorted.forEach { (number, alternative) ->
            tr {
                td { +"$number" }
                columns.forEach { column -> td { +column.display(alternative) } }
                td { +"${alternative.unplacedPeriods()}" }
                td { +"${alternative.softViolations.values.sum()}" }
                td { a(href = alternativePath(generation, alternative)) { +texts.generations.alternativeTitle(number) } }
            }
        }
    }
}
