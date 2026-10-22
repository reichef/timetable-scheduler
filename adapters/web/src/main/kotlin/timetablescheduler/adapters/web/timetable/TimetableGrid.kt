package timetablescheduler.adapters.web.timetable

import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.br
import kotlinx.html.button
import kotlinx.html.form
import kotlinx.html.input
import kotlinx.html.option
import kotlinx.html.select
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.tr
import timetablescheduler.adapters.web.i18n.UiTexts
import timetablescheduler.application.scheduling.TimetableNames
import timetablescheduler.domain.common.RoomId
import timetablescheduler.domain.common.SchoolClassId
import timetablescheduler.domain.common.TeacherId
import timetablescheduler.domain.scheduling.DaySlot
import timetablescheduler.domain.scheduling.evaluation.HardViolation
import timetablescheduler.domain.scheduling.solution.LessonAssignment
import timetablescheduler.domain.school.SlotKind
import java.time.format.DateTimeFormatter

/** Whose week a grid shows (002 FR-019). */
enum class TimetableView(
    val parameter: String,
) {
    CLASS("class"),
    TEACHER("teacher"),
    ROOM("room"),
    ;

    companion object {
        fun fromParameter(value: String?): TimetableView = entries.firstOrNull { it.parameter == value } ?: CLASS
    }
}

/** One cell of a weekly grid: the lines to show and whether it is highlighted (e.g. conflicts, differences). */
data class GridCell(
    val lines: List<String>,
    val highlighted: Boolean = false,
    val note: String? = null,
)

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

/** The lessons of [lessons] that belong to the selected class, teacher or room. */
fun lessonsOf(
    lessons: List<LessonAssignment>,
    view: TimetableView,
    entityId: String,
): List<LessonAssignment> =
    lessons.filter {
        when (view) {
            TimetableView.CLASS -> "${it.classId.value}" == entityId
            TimetableView.TEACHER -> "${it.teacherId.value}" == entityId
            TimetableView.ROOM -> "${it.roomId.value}" == entityId
        }
    }

/**
 * What a lesson cell says in each view: the class view needs subject, teacher and room; the teacher view class,
 * subject and room; the room view class, subject and teacher. Names are shown as entered; deleted entries as such.
 */
fun lessonLines(
    lesson: LessonAssignment,
    view: TimetableView,
    names: TimetableNames,
    texts: UiTexts,
): List<String> {
    val deleted = texts.timetables.deletedEntry
    val className = names.classNames[lesson.classId] ?: deleted
    val subject = names.subjectNames[lesson.subjectId] ?: deleted
    val teacher = names.teacherShortCodes[lesson.teacherId] ?: deleted
    val room = names.roomNames[lesson.roomId] ?: deleted
    return when (view) {
        TimetableView.CLASS -> listOf(subject, teacher, room)
        TimetableView.TEACHER -> listOf(className, subject, room)
        TimetableView.ROOM -> listOf(className, subject, teacher)
    }
}

/**
 * The cell of a lesson that may now violate hard constraints (FR-023): the rules it violates are added as lines and
 * as tooltip, and the cell is highlighted; [dropped] marks a lesson the current curricula no longer require.
 */
fun conflictCell(
    lesson: LessonAssignment,
    view: TimetableView,
    names: TimetableNames,
    conflicts: List<HardViolation>,
    texts: UiTexts,
    dropped: Boolean = false,
): GridCell {
    val rules = conflicts.filter { lesson in it.lessons }.map { texts.generations.hardRuleName(it.rule) }.distinct()
    val droppedLine = listOfNotNull(texts.timetables.noLongerRequired.takeIf { dropped })
    return GridCell(
        lines = lessonLines(lesson, view, names, texts) + droppedLine + rules,
        highlighted = dropped || rules.isNotEmpty(),
        note = rules.joinToString(", ").ifEmpty { null },
    )
}

/** One lesson in a sentence, e.g. in conflict lists: class, subject, day and slot. */
fun lessonDescription(
    lesson: LessonAssignment,
    names: TimetableNames,
    texts: UiTexts,
): String {
    val deleted = texts.timetables.deletedEntry
    return texts.timetables.lessonAt(
        names.classNames[lesson.classId] ?: deleted,
        names.subjectNames[lesson.subjectId] ?: deleted,
        texts.common.dayName(lesson.daySlot.day),
        names.timeGrid.slots
            .firstOrNull { it.id == lesson.daySlot.slotId }
            ?.label ?: deleted,
    )
}

/** The entities a view can show, sorted by name: classes, teachers or rooms. */
fun selectableEntities(
    view: TimetableView,
    names: TimetableNames,
): List<Pair<String, String>> =
    when (view) {
        TimetableView.CLASS -> names.classNames.map { (id, name) -> "${id.value}" to name }
        TimetableView.TEACHER -> names.teacherNames.map { (id, name) -> "${id.value}" to name }
        TimetableView.ROOM -> names.roomNames.map { (id, name) -> "${id.value}" to name }
    }.sortedBy { it.second.lowercase() }

/** The class, teacher or room to show: the requested one, else the first of the view. */
fun selectedEntity(
    view: TimetableView,
    requestedId: String?,
    names: TimetableNames,
): String? = requestedId?.takeIf { it.isNotBlank() } ?: selectableEntities(view, names).firstOrNull()?.first

/** Plain GET forms to switch between class, teacher and room views of the page at [path]; work without any script. */
fun FlowContent.viewSelection(
    path: String,
    names: TimetableNames,
    view: TimetableView,
    entityId: String?,
    texts: UiTexts,
) {
    TimetableView.entries.forEach { candidateView ->
        form(action = path, method = FormMethod.get, classes = "inline") {
            input(type = InputType.hidden, name = "view") { value = candidateView.parameter }
            select {
                name = "id"
                selectableEntities(candidateView, names).forEach { (id, name) ->
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

fun viewName(
    view: TimetableView,
    texts: UiTexts,
) = when (view) {
    TimetableView.CLASS -> texts.timetables.viewClass
    TimetableView.TEACHER -> texts.timetables.viewTeacher
    TimetableView.ROOM -> texts.timetables.viewRoom
}

/** The display name of a class, teacher or room id in [view]. */
fun entityName(
    view: TimetableView,
    entityId: String,
    names: TimetableNames,
    texts: UiTexts,
): String {
    val uuid = runCatching { java.util.UUID.fromString(entityId) }.getOrNull() ?: return texts.timetables.deletedEntry
    return when (view) {
        TimetableView.CLASS -> names.classNames[SchoolClassId(uuid)]
        TimetableView.TEACHER -> names.teacherNames[TeacherId(uuid)]
        TimetableView.ROOM -> names.roomNames[RoomId(uuid)]
    } ?: texts.timetables.deletedEntry
}

/**
 * A week as a table of teaching days × slots of the current time grid; breaks span the whole row, slots that do not
 * exist on a day stay empty. [cellOf] decides what a day-slot shows.
 */
fun FlowContent.weeklyGrid(
    names: TimetableNames,
    texts: UiTexts,
    cellOf: (DaySlot) -> GridCell?,
) {
    val grid = names.timeGrid
    val days = grid.teachingDays.sorted()
    table(classes = "timetable") {
        tr {
            th { +texts.timetables.slotColumn }
            days.forEach { th { +texts.common.dayName(it) } }
        }
        grid.slots.sortedBy { it.startTime }.forEach { slot ->
            tr(classes = if (slot.kind == SlotKind.BREAK) "break" else null) {
                th {
                    +slot.label
                    br()
                    +"${slot.startTime.format(timeFormat)}–${slot.endTime.format(timeFormat)}"
                }
                if (slot.kind == SlotKind.BREAK) {
                    td { colSpan = "${days.size}" }
                } else {
                    days.forEach { day ->
                        val exists = grid.slotsOn(day).any { it.id == slot.id }
                        val cell = if (exists) cellOf(DaySlot(day, slot.id)) else null
                        td(classes = if (cell?.highlighted == true) "highlight" else null) {
                            if (cell?.note != null) {
                                attributes["title"] = cell.note
                            }
                            cell?.lines?.forEachIndexed { index, line ->
                                if (index > 0) {
                                    br()
                                }
                                +line
                            }
                        }
                    }
                }
            }
        }
    }
}
