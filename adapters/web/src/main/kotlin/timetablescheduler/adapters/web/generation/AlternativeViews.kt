package timetablescheduler.adapters.web.generation

import io.ktor.server.application.ApplicationCall
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.ul
import timetablescheduler.adapters.web.auth.UserSession
import timetablescheduler.adapters.web.i18n.UiTexts
import timetablescheduler.adapters.web.i18n.texts
import timetablescheduler.adapters.web.layout.csrfField
import timetablescheduler.adapters.web.layout.hiddenField
import timetablescheduler.adapters.web.layout.respondPage
import timetablescheduler.adapters.web.timetable.TimetableView
import timetablescheduler.adapters.web.timetable.conflictCell
import timetablescheduler.adapters.web.timetable.conflictList
import timetablescheduler.adapters.web.timetable.entityName
import timetablescheduler.adapters.web.timetable.lessonsOf
import timetablescheduler.adapters.web.timetable.viewSelection
import timetablescheduler.adapters.web.timetable.weeklyGrid
import timetablescheduler.application.scheduling.AlternativeDetails
import timetablescheduler.application.scheduling.TimetableNames
import timetablescheduler.domain.scheduling.generation.Alternative
import timetablescheduler.domain.scheduling.generation.ChosenTimetable
import timetablescheduler.domain.scheduling.generation.Generation
import timetablescheduler.domain.scheduling.solution.CourseKey

/** Form field of the choose form: the version of the current choice the planner saw, empty if none (FR-028). */
const val FIELD_CHOSEN_VERSION = "chosenVersion"

/** Form field confirming that the chosen alternative leaves lessons unplaced (FR-021). */
const val FIELD_UNPLACED_CONFIRMED = "unplacedConfirmed"

fun choosePath(
    generation: Generation,
    alternative: Alternative,
) = "${alternativePath(generation, alternative)}/choose"

/**
 * One alternative per class, teacher or room (FR-019) with its objective values and unplaced lessons (FR-018), links
 * to compare it with the other alternatives (FR-020) and the button to choose it (FR-021).
 */
suspend fun ApplicationCall.respondAlternative(
    details: AlternativeDetails,
    view: TimetableView,
    entityId: String?,
) {
    val texts = texts
    val generation = details.generation
    val alternative = details.alternative
    respondPage(texts.generations.alternativeTitle(details.number)) { session ->
        if (details.conflicts.isNotEmpty()) {
            p(classes = "warning") { +texts.generations.outdatedHint }
        }
        objectiveSummary(details, texts)
        if (details.isChosen) {
            p { +texts.generations.chosenMarker }
        } else {
            chooseForm(session, generation, alternative, details.chosen, texts)
        }

        if (details.others.isNotEmpty()) {
            p {
                +"${texts.generations.compareWith}: "
                details.others.forEachIndexed { index, (number, other) ->
                    if (index > 0) {
                        +", "
                    }
                    a(href = "${diffPath(generation, alternative, other)}?view=${view.parameter}&id=${entityId.orEmpty()}") {
                        +texts.generations.alternativeTitle(number)
                    }
                }
            }
        }

        viewSelection(alternativePath(generation, alternative), details.names, view, entityId, texts)
        if (entityId != null) {
            h2 { +entityName(view, entityId, details.names, texts) }
            val lessons = lessonsOf(alternative.lessons, view, entityId)
            weeklyGrid(details.names, texts) { daySlot ->
                lessons.firstOrNull { it.daySlot == daySlot }?.let { conflictCell(it, view, details.names, details.conflicts, texts) }
            }
        }

        conflictList(details.conflicts, details.constraints, details.names, texts, texts.timetables.conflictsHeading)
        unplacedLessons(alternative.unplaced, details.names, texts)

        p { a(href = generationPath(generation)) { +texts.generations.toGenerations } }
    }
}

/** Choosing posts the version of the current choice, so a concurrent choice is detected (FR-028). */
fun FlowContent.chooseForm(
    session: UserSession?,
    generation: Generation,
    alternative: Alternative,
    chosen: ChosenTimetable?,
    texts: UiTexts,
    unplacedConfirmed: Boolean = false,
    buttonText: String = texts.generations.chooseButton,
) {
    form(action = choosePath(generation, alternative), method = FormMethod.post, classes = "inline") {
        csrfField(session)
        hiddenField(FIELD_CHOSEN_VERSION, chosen?.version?.toString().orEmpty())
        if (unplacedConfirmed) {
            hiddenField(FIELD_UNPLACED_CONFIRMED, "true")
        }
        button { +buttonText }
    }
}

/** FR-021: the alternative leaves lessons unplaced; they are listed and must be confirmed before it is chosen. */
suspend fun ApplicationCall.respondChooseConfirmation(
    generation: Generation,
    alternative: Alternative,
    unplaced: Map<CourseKey, Int>,
    names: TimetableNames,
    chosenVersion: String,
) {
    val texts = texts
    respondPage(texts.generations.confirmChooseTitle) { session ->
        p { +texts.generations.confirmChooseIntro }
        unplacedList(unplaced, names, texts)
        form(action = choosePath(generation, alternative), method = FormMethod.post, classes = "inline") {
            csrfField(session)
            hiddenField(FIELD_CHOSEN_VERSION, chosenVersion)
            hiddenField(FIELD_UNPLACED_CONFIRMED, "true")
            button { +texts.generations.confirmChooseButton }
        }
        +" "
        a(href = alternativePath(generation, alternative)) { +texts.generations.cancel }
    }
}

private fun FlowContent.objectiveSummary(
    details: AlternativeDetails,
    texts: UiTexts,
) {
    h2 { +texts.timetables.objectivesHeading }
    ul {
        objectiveColumns(details.generation, details.names, texts, listOf(details.alternative)).forEach { column ->
            li { +"${column.title}: ${column.display(details.alternative)}" }
        }
    }
}

fun FlowContent.unplacedLessons(
    unplaced: Map<CourseKey, Int>,
    names: TimetableNames,
    texts: UiTexts,
    heading: String = texts.timetables.unplacedHeading,
) {
    if (unplaced.none { it.value > 0 }) {
        return
    }
    h2 { +heading }
    unplacedList(unplaced, names, texts)
}

private fun FlowContent.unplacedList(
    unplaced: Map<CourseKey, Int>,
    names: TimetableNames,
    texts: UiTexts,
) {
    ul {
        unplaced
            .filterValues { it > 0 }
            .entries
            .map { (course, periods) ->
                texts.timetables.unplacedEntry(
                    names.classNames[course.classId] ?: texts.timetables.deletedEntry,
                    names.subjectNames[course.subjectId] ?: texts.timetables.deletedEntry,
                    periods,
                )
            }.sorted()
            .forEach { li { +it } }
    }
}
