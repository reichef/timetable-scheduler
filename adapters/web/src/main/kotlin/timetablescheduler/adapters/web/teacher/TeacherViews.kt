package timetablescheduler.adapters.web.teacher

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import kotlinx.html.FORM
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
import kotlinx.html.p
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.tr
import timetablescheduler.adapters.web.i18n.UiTexts
import timetablescheduler.adapters.web.i18n.texts
import timetablescheduler.adapters.web.layout.csrfField
import timetablescheduler.adapters.web.layout.hiddenField
import timetablescheduler.adapters.web.layout.respondPage
import timetablescheduler.adapters.web.layout.textField
import timetablescheduler.adapters.web.layout.validationErrors
import timetablescheduler.application.teacher.TeacherDetails
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.curriculum.Subject
import timetablescheduler.domain.school.TimeGrid
import timetablescheduler.domain.teacher.Teacher
import timetablescheduler.domain.teacher.Unavailability
import timetablescheduler.domain.validation.ValidationError

const val QUALIFICATION_FIELD = "subjectId"
const val SLOT_FIELD = "slot"

fun teachersPath(schoolId: SchoolId) = "/schools/${schoolId.value}/teachers"
fun teacherPath(teacher: Teacher) = "${teachersPath(teacher.schoolId)}/${teacher.id.value}"

/** Form value of one unavailability checkbox, `{dayOfWeek}:{slotId}` as in the route contract. */
fun Unavailability.formValue() = "${dayOfWeek.name}:${slotId.value}"

/** Entered general data, kept when the form is shown again after an error. */
data class TeacherFormInput(val name: String, val shortCode: String, val weeklyLoad: String)

private fun FORM.generalFields(entered: TeacherFormInput?, teachingSlotsPerWeek: Int, texts: UiTexts) {
    textField("name", texts.common.name, entered?.name)
    textField("shortCode", texts.teachers.shortCodeLabel, entered?.shortCode)
    textField("weeklyLoad", texts.teachers.loadLabel(teachingSlotsPerWeek), entered?.weeklyLoad, InputType.number)
}

suspend fun ApplicationCall.respondTeacherList(
    schoolId: SchoolId,
    teachers: List<Teacher>,
    teachingSlotsPerWeek: Int,
    errors: List<ValidationError> = emptyList(),
    entered: TeacherFormInput? = null,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    val texts = texts
    respondPage(texts.teachers.title, status) { session ->
        table {
            tr { th { +texts.common.name }; th { +texts.teachers.shortCodeColumn }; th { +texts.teachers.loadColumn } }
            teachers.forEach { teacher ->
                tr {
                    td { a(href = teacherPath(teacher)) { +teacher.name } }
                    td { +teacher.shortCode }
                    td { +"${teacher.weeklyLoad}" }
                }
            }
        }
        h2 { +texts.teachers.createTeacher }
        validationErrors(errors, texts)
        form(action = teachersPath(schoolId), method = FormMethod.post) {
            csrfField(session)
            generalFields(entered, teachingSlotsPerWeek, texts)
            button { +texts.common.create }
        }
        p { a(href = "/schools/${schoolId.value}") { +texts.common.toSchool } }
    }
}

private fun FORM.qualificationCheckboxes(subjects: List<Subject>, teacher: Teacher, texts: UiTexts) {
    fieldSet {
        legend { +texts.teachers.subjectsLegend }
        if (subjects.isEmpty()) p { +texts.teachers.noSubjects }
        subjects.forEach { subject ->
            label {
                input(type = InputType.checkBox, name = QUALIFICATION_FIELD) {
                    value = "${subject.id.value}"
                    checked = subject.id in teacher.qualifiedSubjectIds
                }
                +subject.name
            }
        }
    }
}

/** Days × teaching slots; a cell offers a checkbox only where the slot exists on that day (FR-021). */
private fun FORM.unavailabilityGrid(grid: TimeGrid, teacher: Teacher, texts: UiTexts) {
    val days = grid.teachingDays.sorted()
    val teachingSlots = grid.slots.filter { slot -> days.any { day -> grid.teachingSlotsOn(day).contains(slot) } }
    if (teachingSlots.isEmpty()) {
        p { +texts.teachers.noTeachingSlots }
        return
    }
    table {
        tr {
            th { +texts.teachers.slotColumn }
            days.forEach { th { +texts.common.dayName(it) } }
        }
        teachingSlots.forEach { slot ->
            tr {
                td { +"${slot.label} (${slot.startTime}–${slot.endTime})" }
                days.forEach { day ->
                    td {
                        if (grid.teachingSlotsOn(day).contains(slot)) {
                            val entry = Unavailability(day, slot.id)
                            input(type = InputType.checkBox, name = SLOT_FIELD) {
                                value = entry.formValue()
                                checked = entry in teacher.unavailability
                            }
                        } else {
                            +"–"
                        }
                    }
                }
            }
        }
    }
}

suspend fun ApplicationCall.respondTeacherDetails(
    details: TeacherDetails,
    errors: List<ValidationError> = emptyList(),
    entered: TeacherFormInput? = null,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    val texts = texts
    val teacher = details.teacher
    val path = teacherPath(teacher)
    respondPage(texts.teachers.teacherTitle(teacher.displayName), status) { session ->
        validationErrors(errors, texts)
        h2 { +texts.teachers.general }
        form(action = path, method = FormMethod.post) {
            csrfField(session)
            hiddenField("version", "${teacher.version}")
            val current = entered ?: TeacherFormInput(teacher.name, teacher.shortCode, "${teacher.weeklyLoad}")
            generalFields(current, details.timeGrid.teachingSlotsPerWeek(), texts)
            button { +texts.common.save }
        }
        h2 { +texts.teachers.qualifications }
        form(action = "$path/qualifications", method = FormMethod.post) {
            csrfField(session)
            hiddenField("version", "${teacher.version}")
            qualificationCheckboxes(details.subjects, teacher, texts)
            button { +texts.teachers.saveQualifications }
        }
        h2 { +texts.teachers.unavailabilityTitle }
        form(action = "$path/unavailability", method = FormMethod.post) {
            csrfField(session)
            hiddenField("version", "${teacher.version}")
            unavailabilityGrid(details.timeGrid, teacher, texts)
            button { +texts.teachers.saveUnavailability }
        }
        form(action = "$path/delete", method = FormMethod.post) {
            csrfField(session)
            hiddenField("version", "${teacher.version}")
            button(classes = "danger") { +texts.teachers.deleteTeacher }
        }
        p { a(href = teachersPath(teacher.schoolId)) { +texts.teachers.toTeachers } }
    }
}
