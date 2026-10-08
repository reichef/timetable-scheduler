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
import timetablescheduler.adapters.web.layout.csrfField
import timetablescheduler.adapters.web.layout.germanName
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
fun slotPath(schoolId: SchoolId, slotId: TimeSlotId) = "${timeGridPath(schoolId)}/slots/${slotId.value}"

/** Entered values of a slot form, kept when the form is shown again after an error. */
data class SlotFormInput(val label: String, val startTime: String, val endTime: String, val kind: String)

private fun TimeSlot.toFormInput() = SlotFormInput(label, "$startTime", "$endTime", kind.name)

private fun SlotKind.germanName() = if (this == SlotKind.TEACHING) "Unterricht" else "Pause"

suspend fun ApplicationCall.respondTimeGrid(
    view: TimeGridView,
    errors: List<ValidationError> = emptyList(),
    enteredSlot: SlotFormInput? = null,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    val schoolId = view.school.id
    val version = view.school.version
    respondPage("Zeitraster – ${view.school.name}", status) { session ->
        validationErrors(errors)
        teachingDaysForm(session, schoolId, view.timeGrid.teachingDays, version)
        weeklyGridTable(schoolId, view.timeGrid)
        p { +"Unterrichtsstunden pro Woche: ${view.timeGrid.teachingSlotsPerWeek()}" }
        h2 { +"Zeitfenster hinzufügen" }
        form(action = "${timeGridPath(schoolId)}/slots", method = FormMethod.post) {
            csrfField(session)
            hiddenField("version", "$version")
            slotFields(enteredSlot)
            button { +"Hinzufügen" }
        }
        p { a(href = "/schools/${schoolId.value}") { +"Zur Schule" } }
    }
}

private fun FlowContent.teachingDaysForm(session: UserSession?, schoolId: SchoolId, teachingDays: Set<DayOfWeek>, version: Int) {
    form(action = "${timeGridPath(schoolId)}/days", method = FormMethod.post) {
        csrfField(session)
        hiddenField("version", "$version")
        dayCheckboxes("Unterrichtstage", DayOfWeek.entries, teachingDays)
        button { +"Unterrichtstage speichern" }
    }
}

/** Days × slots overview; breaks are marked so planners can tell them apart at a glance (acceptance 1.4). */
private fun FlowContent.weeklyGridTable(schoolId: SchoolId, timeGrid: TimeGrid) {
    val days = timeGrid.teachingDays.sorted()
    table {
        tr {
            th { +"Zeitfenster" }
            th { +"Zeit" }
            th { +"Art" }
            days.forEach { th { +it.germanName() } }
            th { +"Aktionen" }
        }
        timeGrid.slots.forEach { slot ->
            tr(classes = if (slot.kind == SlotKind.BREAK) "break" else null) {
                td { +slot.label }
                td { +"${slot.startTime}–${slot.endTime}" }
                td { +slot.kind.germanName() }
                days.forEach { day -> td { +(if (slot in timeGrid.slotsOn(day)) "✓" else "–") } }
                td {
                    a(href = slotPath(schoolId, slot.id)) { +"Bearbeiten" }
                    +" "
                    a(href = "${slotPath(schoolId, slot.id)}/delete") { +"Löschen" }
                }
            }
        }
    }
}

private fun FORM.slotFields(entered: SlotFormInput?) {
    textField("label", "Bezeichnung", entered?.label)
    label {
        +"Beginn"
        input(type = InputType.time, name = "startTime") { value = entered?.startTime.orEmpty() }
    }
    label {
        +"Ende"
        input(type = InputType.time, name = "endTime") { value = entered?.endTime.orEmpty() }
    }
    label {
        +"Art"
        select {
            name = "kind"
            SlotKind.entries.forEach { kind ->
                option {
                    value = kind.name
                    selected = entered?.kind == kind.name
                    +kind.germanName()
                }
            }
        }
    }
}

private fun FORM.dayCheckboxes(title: String, days: List<DayOfWeek>, checkedDays: Set<DayOfWeek>) {
    fieldSet {
        legend { +title }
        days.forEach { day ->
            label {
                input(type = InputType.checkBox, name = "dayOfWeek") {
                    value = day.name
                    checked = day in checkedDays
                }
                +day.germanName()
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
    val schoolId = view.school.id
    val version = view.school.version
    val excludedDays = view.timeGrid.exclusions.filter { it.slotId == slot.id }.map { it.dayOfWeek }.toSet()
    respondPage("Zeitfenster ${slot.label}", status) { session ->
        validationErrors(errors)
        form(action = slotPath(schoolId, slot.id), method = FormMethod.post) {
            csrfField(session)
            hiddenField("version", "$version")
            slotFields(entered ?: slot.toFormInput())
            button { +"Speichern" }
        }
        h2 { +"Tage ohne dieses Zeitfenster" }
        form(action = "${slotPath(schoolId, slot.id)}/exclusions", method = FormMethod.post) {
            csrfField(session)
            hiddenField("version", "$version")
            dayCheckboxes("Entfällt am", view.timeGrid.teachingDays.sorted(), excludedDays)
            button { +"Speichern" }
        }
        p { a(href = "${slotPath(schoolId, slot.id)}/delete") { +"Zeitfenster löschen" } }
        p { a(href = timeGridPath(schoolId)) { +"Zum Zeitraster" } }
    }
}

suspend fun ApplicationCall.respondSlotDeletionConfirmation(schoolId: SchoolId, preview: SlotDeletionPreview) {
    val slot = preview.slot
    respondPage("Zeitfenster löschen") { session ->
        p { +"Zeitfenster „${slot.label}“ (${slot.startTime}–${slot.endTime}) wirklich löschen?" }
        val removals = "${preview.exclusionCount} Tagesausnahme(n) und ${preview.unavailabilityCount} Sperrzeiten von Lehrkräften"
        p { +"Dabei werden $removals entfernt." }
        form(action = "${slotPath(schoolId, slot.id)}/delete", method = FormMethod.post) {
            csrfField(session)
            hiddenField("version", "${preview.schoolVersion}")
            button(classes = "danger") { +"Endgültig löschen" }
        }
        p { a(href = timeGridPath(schoolId)) { +"Abbrechen" } }
    }
}
