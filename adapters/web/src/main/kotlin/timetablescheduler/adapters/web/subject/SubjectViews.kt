package timetablescheduler.adapters.web.subject

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import kotlinx.html.FormMethod
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.p
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.tr
import timetablescheduler.adapters.web.layout.csrfField
import timetablescheduler.adapters.web.layout.featureCheckboxes
import timetablescheduler.adapters.web.layout.hiddenField
import timetablescheduler.adapters.web.layout.respondPage
import timetablescheduler.adapters.web.layout.textField
import timetablescheduler.adapters.web.layout.validationErrors
import timetablescheduler.application.curriculum.SubjectDetails
import timetablescheduler.application.curriculum.SubjectOverview
import timetablescheduler.domain.common.RoomFeatureId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.curriculum.Subject
import timetablescheduler.domain.validation.ValidationError

const val REQUIRED_FEATURE_FIELD = "requiredFeatureId"

fun subjectsPath(schoolId: SchoolId) = "/schools/${schoolId.value}/subjects"
fun subjectPath(subject: Subject) = "${subjectsPath(subject.schoolId)}/${subject.id.value}"

/** Entered values of the subject form, kept when the form is shown again after an error. */
data class SubjectFormInput(val name: String, val requiredFeatureIds: Set<RoomFeatureId>)

suspend fun ApplicationCall.respondSubjectList(
    schoolId: SchoolId,
    overview: SubjectOverview,
    errors: List<ValidationError> = emptyList(),
    entered: SubjectFormInput? = null,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    val featureNames = overview.features.associate { it.id to it.name }
    respondPage("Fächer", status) { session ->
        table {
            tr { th { +"Fach" }; th { +"Benötigte Ausstattung" } }
            overview.subjects.forEach { subject ->
                tr {
                    td { a(href = subjectPath(subject)) { +subject.name } }
                    td { +subject.requiredFeatureIds.mapNotNull { featureNames[it] }.sorted().joinToString(", ") }
                }
            }
        }
        h2 { +"Fach anlegen" }
        validationErrors(errors)
        form(action = subjectsPath(schoolId), method = FormMethod.post) {
            csrfField(session)
            textField("name", "Name", entered?.name)
            featureCheckboxes(REQUIRED_FEATURE_FIELD, overview.features, entered?.requiredFeatureIds.orEmpty())
            button { +"Anlegen" }
        }
        p { a(href = "/schools/${schoolId.value}") { +"Zur Schule" } }
    }
}

suspend fun ApplicationCall.respondSubjectDetails(
    details: SubjectDetails,
    errors: List<ValidationError> = emptyList(),
    entered: SubjectFormInput? = null,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    val subject = details.subject
    respondPage("Fach ${subject.name}", status) { session ->
        validationErrors(errors)
        form(action = subjectPath(subject), method = FormMethod.post) {
            csrfField(session)
            hiddenField("version", "${subject.version}")
            textField("name", "Name", entered?.name ?: subject.name)
            featureCheckboxes(REQUIRED_FEATURE_FIELD, details.features, entered?.requiredFeatureIds ?: subject.requiredFeatureIds)
            button { +"Speichern" }
        }
        form(action = "${subjectPath(subject)}/delete", method = FormMethod.post) {
            csrfField(session)
            hiddenField("version", "${subject.version}")
            button(classes = "danger") { +"Fach löschen" }
        }
        p { a(href = subjectsPath(subject.schoolId)) { +"Zur Fächerliste" } }
    }
}
