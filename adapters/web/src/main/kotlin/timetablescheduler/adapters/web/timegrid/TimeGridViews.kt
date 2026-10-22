package timetablescheduler.adapters.web.timegrid

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import kotlinx.html.FORM
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
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.tr
import timetablescheduler.adapters.web.auth.UserSession
import timetablescheduler.adapters.web.i18n.UiTexts
import timetablescheduler.adapters.web.i18n.texts
import timetablescheduler.adapters.web.layout.csrfField
import timetablescheduler.adapters.web.layout.hiddenField
import timetablescheduler.adapters.web.layout.respondPage
import timetablescheduler.adapters.web.layout.textField
import timetablescheduler.adapters.web.layout.validationErrors
import timetablescheduler.application.school.SlotDeletionPreview
import timetablescheduler.application.school.TimeGridView
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.common.TimeSlotId
import timetablescheduler.domain.school.SlotKind
import timetablescheduler.domain.school.TimeGrid
import timetablescheduler.domain.school.TimeSlot
import timetablescheduler.domain.validation.ValidationError
import java.time.DayOfWeek

fun timeGridPath(schoolId: SchoolId) = "/schools/${schoolId.value}/time-grid"

fun slotPath(
    schoolId: SchoolId,
    slotId: TimeSlotId,
) = "${timeGridPath(schoolId)}/slots/${slotId.value}"

/** Entered values of a slot form, kept when the form is shown again after an error. */
data class SlotFormInput(
    val label: String,
    val startTime: String,
    val endTime: String,
    val kind: String,
)

private fun TimeSlot.toFormInput() = SlotFormInput(label, "$startTime", "$endTime", kind.name)

suspend fun ApplicationCall.respondTimeGrid(
    view: TimeGridView,
    errors: List<ValidationError> = emptyList(),
    enteredSlot: SlotFormInput? = null,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    val texts = texts
    val schoolId = view.school.id
    val version = view.school.version
    respondPage(texts.timeGrid.title(view.school.name), status) { session ->
        validationErrors(errors, texts)
        teachingDaysForm(session, schoolId, view.timeGrid.teachingDays, version, texts)
        weeklyGridTable(schoolId, view.timeGrid, texts)
        p { +texts.timeGrid.teachingSlotsPerWeek(view.timeGrid.teachingSlotsPerWeek()) }
        h2 { +texts.timeGrid.addSlot }
        form(action = "${timeGridPath(schoolId)}/slots", method = FormMethod.post) {
            csrfField(session)
            hiddenField("version", "$version")
            slotFields(enteredSlot, texts)
            button { +texts.common.add }
        }
        p { a(href = "/schools/${schoolId.value}") { +texts.common.toSchool } }
    }
}

private fun FlowContent.teachingDaysForm(
    session: UserSession?,
    schoolId: SchoolId,
    teachingDays: Set<DayOfWeek>,
    version: Int,
    texts: UiTexts,
) {
    form(action = "${timeGridPath(schoolId)}/days", method = FormMethod.post) {
        csrfField(session)
        hiddenField("version", "$version")
        dayCheckboxes(texts.timeGrid.teachingDays, DayOfWeek.entries, teachingDays, texts)
        button { +texts.timeGrid.saveTeachingDays }
    }
}

/** Days × slots overview; breaks are marked so planners can tell them apart at a glance (acceptance 1.4). */
private fun FlowContent.weeklyGridTable(
    schoolId: SchoolId,
    timeGrid: TimeGrid,
    texts: UiTexts,
) {
    val days = timeGrid.teachingDays.sorted()
    table {
        tr {
            th { +texts.timeGrid.slotColumn }
            th { +texts.timeGrid.timeColumn }
            th { +texts.timeGrid.kindColumn }
            days.forEach { th { +texts.common.dayName(it) } }
            th { +texts.timeGrid.actionsColumn }
        }
        timeGrid.slots.forEach { slot ->
            tr(classes = if (slot.kind == SlotKind.BREAK) "break" else null) {
                td { +slot.label }
                td { +"${slot.startTime}–${slot.endTime}" }
                td { +texts.timeGrid.slotKindName(slot.kind) }
                days.forEach { day -> td { +(if (slot in timeGrid.slotsOn(day)) "✓" else "–") } }
                td {
                    a(href = slotPath(schoolId, slot.id)) { +texts.common.edit }
                    +" "
                    a(href = "${slotPath(schoolId, slot.id)}/delete") { +texts.common.delete }
                }
            }
        }
    }
}

private fun FORM.slotFields(
    entered: SlotFormInput?,
    texts: UiTexts,
) {
    textField("label", texts.timeGrid.label, entered?.label)
    label {
        +texts.timeGrid.start
        input(type = InputType.time, name = "startTime") { value = entered?.startTime.orEmpty() }
    }
    label {
        +texts.timeGrid.end
        input(type = InputType.time, name = "endTime") { value = entered?.endTime.orEmpty() }
    }
    label {
        +texts.timeGrid.kind
        select {
            name = "kind"
            SlotKind.entries.forEach { kind ->
                option {
                    value = kind.name
                    selected = entered?.kind == kind.name
                    +texts.timeGrid.slotKindName(kind)
                }
            }
        }
    }
}

private fun FORM.dayCheckboxes(
    title: String,
    days: List<DayOfWeek>,
    checkedDays: Set<DayOfWeek>,
    texts: UiTexts,
) {
    fieldSet {
        legend { +title }
        days.forEach { day ->
            label {
                input(type = InputType.checkBox, name = "dayOfWeek") {
                    value = day.name
                    checked = day in checkedDays
                }
                +texts.common.dayName(day)
            }
        }
    }
}

suspend fun ApplicationCall.respondSlotEditor(
    view: TimeGridView,
    slot: TimeSlot,
    errors: List<ValidationError> = emptyList(),
    entered: SlotFormInput? = null,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    val texts = texts
    val schoolId = view.school.id
    val version = view.school.version
    val excludedDays =
        view.timeGrid.exclusions
            .filter { it.slotId == slot.id }
            .map { it.dayOfWeek }
            .toSet()
    respondPage(texts.timeGrid.slotTitle(slot.label), status) { session ->
        validationErrors(errors, texts)
        form(action = slotPath(schoolId, slot.id), method = FormMethod.post) {
            csrfField(session)
            hiddenField("version", "$version")
            slotFields(entered ?: slot.toFormInput(), texts)
            button { +texts.common.save }
        }
        h2 { +texts.timeGrid.daysWithoutSlot }
        form(action = "${slotPath(schoolId, slot.id)}/exclusions", method = FormMethod.post) {
            csrfField(session)
            hiddenField("version", "$version")
            dayCheckboxes(texts.timeGrid.notHeldOn, view.timeGrid.teachingDays.sorted(), excludedDays, texts)
            button { +texts.common.save }
        }
        p { a(href = "${slotPath(schoolId, slot.id)}/delete") { +texts.timeGrid.deleteSlot } }
        p { a(href = timeGridPath(schoolId)) { +texts.timeGrid.toTimeGrid } }
    }
}

suspend fun ApplicationCall.respondSlotDeletionConfirmation(
    schoolId: SchoolId,
    preview: SlotDeletionPreview,
) {
    val texts = texts
    val slot = preview.slot
    respondPage(texts.timeGrid.deleteSlotTitle) { session ->
        p { +texts.timeGrid.deleteQuestion(slot.label, slot.startTime, slot.endTime) }
        p { +texts.timeGrid.deleteRemovals(preview.exclusionCount, preview.unavailabilityCount) }
        form(action = "${slotPath(schoolId, slot.id)}/delete", method = FormMethod.post) {
            csrfField(session)
            hiddenField("version", "${preview.schoolVersion}")
            button(classes = "danger") { +texts.timeGrid.deletePermanently }
        }
        p { a(href = timeGridPath(schoolId)) { +texts.common.cancel } }
    }
}
