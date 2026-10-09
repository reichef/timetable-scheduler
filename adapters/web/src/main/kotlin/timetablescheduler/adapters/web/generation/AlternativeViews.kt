package timetablescheduler.adapters.web.generation

import io.ktor.server.application.ApplicationCall
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.input
import kotlinx.html.li
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.ul
import timetablescheduler.adapters.web.i18n.UiTexts
import timetablescheduler.adapters.web.i18n.texts
import timetablescheduler.adapters.web.layout.respondPage
import timetablescheduler.adapters.web.timetable.GridCell
import timetablescheduler.adapters.web.timetable.TimetableView
import timetablescheduler.adapters.web.timetable.entityName
import timetablescheduler.adapters.web.timetable.lessonLines
import timetablescheduler.adapters.web.timetable.lessonsOf
import timetablescheduler.adapters.web.timetable.weeklyGrid
import timetablescheduler.application.scheduling.AlternativeDetails
import timetablescheduler.application.scheduling.TimetableNames

/** The entities a view can show, sorted by name: classes, teachers or rooms. */
private fun selectableEntities(view: TimetableView, names: TimetableNames): List<Pair<String, String>> = when (view) {
    TimetableView.CLASS -> names.classNames.map { (id, name) -> "${id.value}" to name }
    TimetableView.TEACHER -> names.teacherNames.map { (id, name) -> "${id.value}" to name }
    TimetableView.ROOM -> names.roomNames.map { (id, name) -> "${id.value}" to name }
}.sortedBy { it.second.lowercase() }

/** The class, teacher or room to show: the requested one, else the first of the view. */
fun selectedEntity(view: TimetableView, requestedId: String?, names: TimetableNames): String? =
    requestedId?.takeIf { it.isNotBlank() } ?: selectableEntities(view, names).firstOrNull()?.first

/** One alternative per class, teacher or room (FR-019) with its objective values and unplaced lessons (FR-018). */
suspend fun ApplicationCall.respondAlternative(details: AlternativeDetails, view: TimetableView, entityId: String?) {
    val texts = texts
    val generation = details.generation
    val alternative = details.alternative
    respondPage(texts.generations.alternativeTitle(details.number)) {
        objectiveSummary(details, texts)
        viewSelection(details, view, entityId, texts)
        if (entityId != null) {
            h2 { +entityName(view, entityId, details.names, texts) }
            val lessons = lessonsOf(alternative.lessons, view, entityId)
            weeklyGrid(details.names, texts) { daySlot ->
                lessons.firstOrNull { it.daySlot == daySlot }?.let { GridCell(lessonLines(it, view, details.names, texts)) }
            }
        }
        unplacedLessons(details, texts)
        p { a(href = generationPath(generation)) { +texts.generations.toGenerations } }
    }
}

private fun FlowContent.objectiveSummary(details: AlternativeDetails, texts: UiTexts) {
    h2 { +texts.timetables.objectivesHeading }
    ul {
        objectiveColumns(details.generation, details.names, texts).forEach { column ->
            li { +"${column.title}: ${texts.generations.percent(column.valueOf(details.alternative))}" }
        }
    }
}

/** A plain GET form to switch between class, teacher and room views; works without any script. */
private fun FlowContent.viewSelection(details: AlternativeDetails, view: TimetableView, entityId: String?, texts: UiTexts) {
    val path = alternativePath(details.generation, details.alternative)
    TimetableView.entries.forEach { candidateView ->
        form(action = path, method = FormMethod.get, classes = "inline") {
            input(type = InputType.hidden, name = "view") { value = candidateView.parameter }
            select {
                name = "id"
                selectableEntities(candidateView, details.names).forEach { (id, name) ->
                    option {
                        value = id
                        selected = candidateView == view && id == entityId
                        +name
                    }
                }
            }
            button { +"${texts.timetables.show}: ${viewName(candidateView, texts)}" }
        }
    }
}

private fun viewName(view: TimetableView, texts: UiTexts) = when (view) {
    TimetableView.CLASS -> texts.timetables.viewClass
    TimetableView.TEACHER -> texts.timetables.viewTeacher
    TimetableView.ROOM -> texts.timetables.viewRoom
}

private fun FlowContent.unplacedLessons(details: AlternativeDetails, texts: UiTexts) {
    val unplaced = details.alternative.unplaced.filterValues { it > 0 }
    if (unplaced.isEmpty()) return
    val names = details.names
    h2 { +texts.timetables.unplacedHeading }
    ul {
        unplaced.entries
            .map { (course, periods) ->
                texts.timetables.unplacedEntry(
                    names.classNames[course.classId] ?: texts.timetables.deletedEntry,
                    names.subjectNames[course.subjectId] ?: texts.timetables.deletedEntry,
                    periods,
                )
            }
            .sorted()
            .forEach { li { +it } }
    }
}
