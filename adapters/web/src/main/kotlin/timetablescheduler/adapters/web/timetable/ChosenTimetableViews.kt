package timetablescheduler.adapters.web.timetable

import io.ktor.server.application.ApplicationCall
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.h2
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.ul
import timetablescheduler.adapters.web.constraint.describe
import timetablescheduler.adapters.web.generation.generationPath
import timetablescheduler.adapters.web.generation.generationsPath
import timetablescheduler.adapters.web.generation.unplacedLessons
import timetablescheduler.adapters.web.i18n.UiTexts
import timetablescheduler.adapters.web.i18n.texts
import timetablescheduler.adapters.web.layout.respondPage
import timetablescheduler.application.scheduling.ChosenTimetableDetails
import timetablescheduler.application.scheduling.StartRequest
import timetablescheduler.application.scheduling.TimetableNames
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.scheduling.constraint.SchedulingConstraint
import timetablescheduler.domain.scheduling.evaluation.HardViolation

fun chosenTimetablePath(schoolId: SchoolId) = "/schools/${schoolId.value}/timetable"

/**
 * The school's chosen timetable (FR-021, FR-023): its grid per class, teacher or room, the lessons that now violate a
 * hard constraint (highlighted, with the rule) and those the current curricula no longer require ("entfällt"), the
 * lessons not placed under today's curricula, and — if any of this applies — a hint with a link to start a new
 * generation from it.
 */
suspend fun ApplicationCall.respondChosenTimetable(details: ChosenTimetableDetails, view: TimetableView, entityId: String?) {
    val texts = texts
    val chosen = details.chosen
    respondPage(texts.timetables.chosenTitle) {
        if (chosen == null) {
            p { +texts.timetables.noChosen }
            p { a(href = generationsPath(details.schoolId)) { +texts.generations.toGenerations } }
            return@respondPage
        }
        p { +texts.timetables.chosenInfo(texts.generations.dateTime(chosen.chosenAt), details.chosenByUsername) }
        details.sourceGeneration?.let { generation -> p { a(href = generationPath(generation)) { +texts.timetables.toSourceGeneration } } }
        if (!details.fitsCurrentData) {
            p(classes = "warning") {
                +"${texts.timetables.noLongerFits} "
                a(href = "${generationsPath(details.schoolId)}?${StartRequest.FIELD_START_FROM_CHOSEN}=true") { +texts.timetables.startFromChosenLink }
            }
        }
        viewSelection(chosenTimetablePath(details.schoolId), details.names, view, entityId, texts)
        if (entityId != null) {
            h2 { +entityName(view, entityId, details.names, texts) }
            val lessons = lessonsOf(chosen.lessons, view, entityId)
            val noLongerRequired = details.drift?.noLongerRequired.orEmpty()
            weeklyGrid(details.names, texts) { daySlot ->
                val lesson = lessons.firstOrNull { it.daySlot == daySlot } ?: return@weeklyGrid null
                val rules = details.conflicts.filter { lesson in it.lessons }.map { texts.generations.hardRuleName(it.rule) }.distinct()
                val dropped = lesson in noLongerRequired
                GridCell(
                    lines = lessonLines(lesson, view, details.names, texts) + listOfNotNull(texts.timetables.noLongerRequired.takeIf { dropped }) + rules,
                    highlighted = dropped || rules.isNotEmpty(),
                    note = rules.joinToString(", ").ifEmpty { null },
                )
            }
        }
        conflictList(details.conflicts, details.constraints, details.names, texts, texts.timetables.conflictsHeading)
        val noLongerRequired = details.drift?.noLongerRequired.orEmpty()
        if (noLongerRequired.isNotEmpty()) {
            h2 { +texts.timetables.noLongerRequiredHeading }
            ul { noLongerRequired.map { lessonDescription(it, details.names, texts) }.sorted().forEach { li { +it } } }
        }
        unplacedLessons(details.drift?.notPlaced.orEmpty(), details.names, texts, texts.timetables.notPlacedHeading)
    }
}

/** Hard-constraint violations with their rule (and the planner constraint, if one) and the lessons concerned. */
fun FlowContent.conflictList(
    violations: List<HardViolation>,
    constraints: List<SchedulingConstraint>,
    names: TimetableNames,
    texts: UiTexts,
    heading: String? = null,
) {
    if (violations.isEmpty()) return
    if (heading != null) h2 { +heading }
    ul {
        violations.forEach { violation ->
            val constraint = violation.constraintId?.let { id -> constraints.firstOrNull { it.id == id } }
            val rule = constraint?.let { "${texts.generations.hardRuleName(violation.rule)} („${describe(it, names, texts)}“)" }
                ?: texts.generations.hardRuleName(violation.rule)
            li { +"$rule: ${violation.lessons.joinToString("; ") { lessonDescription(it, names, texts) }}" }
        }
    }
}
