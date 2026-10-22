package timetablescheduler.adapters.web.generation

import io.ktor.server.application.ApplicationCall
import kotlinx.html.FormMethod
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.label
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import timetablescheduler.adapters.web.i18n.texts
import timetablescheduler.adapters.web.layout.respondPage
import timetablescheduler.adapters.web.timetable.GridCell
import timetablescheduler.adapters.web.timetable.TimetableView
import timetablescheduler.adapters.web.timetable.entityName
import timetablescheduler.adapters.web.timetable.lessonLines
import timetablescheduler.adapters.web.timetable.lessonsOf
import timetablescheduler.adapters.web.timetable.viewSelection
import timetablescheduler.adapters.web.timetable.weeklyGrid
import timetablescheduler.application.scheduling.AlternativeComparison
import timetablescheduler.application.scheduling.GenerationDetails
import timetablescheduler.domain.scheduling.solution.TimetableDiff

/**
 * FR-020: the alternatives of a generation in a table sortable by every objective and a chart of two selected
 * objectives ([xKey], [yKey], defaulting to the first two), each point linking to its alternative.
 */
suspend fun ApplicationCall.respondComparison(
    details: GenerationDetails,
    xKey: String?,
    yKey: String?,
    sortKey: String?,
    descending: Boolean,
) {
    val texts = texts
    val generation = details.generation
    val columns = objectiveColumns(generation, details.names, texts, details.alternatives)
    val x = columns.firstOrNull { it.key == xKey } ?: columns[0]
    val y = columns.firstOrNull { it.key == yKey } ?: columns[1]
    respondPage(texts.generations.compareTitle) {
        form(action = comparePath(generation), method = FormMethod.get) {
            listOf("x" to (texts.generations.xAxis to x), "y" to (texts.generations.yAxis to y)).forEach { (field, labelled) ->
                val (labelText, current) = labelled
                label {
                    +labelText
                    select {
                        name = field
                        columns.forEach { column ->
                            option {
                                value = column.key
                                selected = column.key == current.key
                                +column.title
                            }
                        }
                    }
                }
            }
            button { +texts.generations.showChart }
        }
        if (details.alternatives.isNotEmpty()) {
            val points =
                details.alternatives.mapIndexed { index, alternative ->
                    ChartPoint(index + 1, x.valueOf(alternative), y.valueOf(alternative), alternativePath(generation, alternative))
                }
            tradeOffChart(points, x.title, y.title, texts.generations.chartCaption(x.title, y.title))
        }
        alternativeTable(details, columns, columns.firstOrNull { it.key == sortKey }, descending, texts) { key, direction ->
            "${comparePath(generation)}?x=${x.key}&y=${y.key}&sort=$key&dir=$direction"
        }
        p { a(href = generationPath(generation)) { +texts.generations.toGenerations } }
    }
}

/** FR-020: two alternatives side by side for one class, teacher or room; cells that differ are highlighted. */
suspend fun ApplicationCall.respondDiff(
    comparison: AlternativeComparison,
    view: TimetableView,
    entityId: String?,
) {
    val texts = texts
    val generation = comparison.generation
    val names = comparison.names
    respondPage(texts.generations.diffTitle(comparison.firstNumber, comparison.secondNumber)) {
        viewSelection(diffPath(generation, comparison.first, comparison.second), names, view, entityId, texts)
        if (entityId != null) {
            h2 { +entityName(view, entityId, names, texts) }
            p { +texts.generations.diffHint }
            val first = lessonsOf(comparison.first.lessons, view, entityId)
            val second = lessonsOf(comparison.second.lessons, view, entityId)
            val differing = TimetableDiff.differingSlots(first, second) { true }
            div(classes = "side-by-side") {
                listOf(comparison.firstNumber to first, comparison.secondNumber to second).forEach { (number, lessons) ->
                    div {
                        h2 { +texts.generations.alternativeTitle(number) }
                        weeklyGrid(names, texts) { daySlot ->
                            val lesson = lessons.firstOrNull { it.daySlot == daySlot }
                            when {
                                lesson != null -> GridCell(lessonLines(lesson, view, names, texts), highlighted = daySlot in differing)
                                daySlot in differing -> GridCell(emptyList(), highlighted = true)
                                else -> null
                            }
                        }
                    }
                }
            }
        }
        p {
            a(href = alternativePath(generation, comparison.first)) { +texts.generations.alternativeTitle(comparison.firstNumber) }
            +" · "
            a(href = alternativePath(generation, comparison.second)) { +texts.generations.alternativeTitle(comparison.secondNumber) }
            +" · "
            a(href = generationPath(generation)) { +texts.generations.toGenerations }
        }
    }
}
