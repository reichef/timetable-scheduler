package timetablescheduler.adapters.web.room

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
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.tr
import kotlinx.html.ul
import timetablescheduler.adapters.web.layout.csrfField
import timetablescheduler.adapters.web.layout.hiddenField
import timetablescheduler.adapters.web.layout.respondPage
import timetablescheduler.adapters.web.layout.textField
import timetablescheduler.adapters.web.layout.validationErrors
import timetablescheduler.application.room.RoomDetails
import timetablescheduler.application.room.RoomOverview
import timetablescheduler.domain.common.RoomFeatureId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.room.RoomFeature
import timetablescheduler.domain.validation.ValidationError

fun roomsPath(schoolId: SchoolId) = "/schools/${schoolId.value}/rooms"
fun featuresPath(schoolId: SchoolId) = "/schools/${schoolId.value}/room-features"

/** Entered values of the room form, kept when the form is shown again after an error. */
data class RoomFormInput(val identifier: String, val featureIds: Set<RoomFeatureId>)

private fun FORM.featureCheckboxes(features: List<RoomFeature>, checkedIds: Set<RoomFeatureId>) {
    if (features.isEmpty()) return
    fieldSet {
        legend { +"Ausstattung" }
        features.forEach { feature ->
            label {
                input(type = InputType.checkBox, name = "featureId") {
                    value = "${feature.id.value}"
                    checked = feature.id in checkedIds
                }
                +feature.name
            }
        }
    }
}

suspend fun ApplicationCall.respondRoomList(
    schoolId: SchoolId,
    overview: RoomOverview,
    errors: List<ValidationError> = emptyList(),
    entered: RoomFormInput? = null,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    val featureNames = overview.features.associate { it.id to it.name }
    respondPage("Räume", status) { session ->
        table {
            tr { th { +"Raum" }; th { +"Ausstattung" } }
            overview.rooms.forEach { room ->
                tr {
                    td { a(href = "${roomsPath(schoolId)}/${room.id.value}") { +room.identifier } }
                    td { +room.featureIds.mapNotNull { featureNames[it] }.sorted().joinToString(", ") }
                }
            }
        }
        h2 { +"Raum anlegen" }
        validationErrors(errors)
        form(action = roomsPath(schoolId), method = FormMethod.post) {
            csrfField(session)
            textField("identifier", "Bezeichnung", entered?.identifier)
            featureCheckboxes(overview.features, entered?.featureIds.orEmpty())
            button { +"Anlegen" }
        }
        p { a(href = featuresPath(schoolId)) { +"Ausstattung verwalten" } }
        p { a(href = "/schools/${schoolId.value}") { +"Zur Schule" } }
    }
}

suspend fun ApplicationCall.respondRoomDetails(
    schoolId: SchoolId,
    details: RoomDetails,
    errors: List<ValidationError> = emptyList(),
    entered: RoomFormInput? = null,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    val room = details.room
    val roomPath = "${roomsPath(schoolId)}/${room.id.value}"
    respondPage("Raum ${room.identifier}", status) { session ->
        validationErrors(errors)
        form(action = roomPath, method = FormMethod.post) {
            csrfField(session)
            hiddenField("version", "${room.version}")
            textField("identifier", "Bezeichnung", entered?.identifier ?: room.identifier)
            featureCheckboxes(details.features, entered?.featureIds ?: room.featureIds)
            button { +"Speichern" }
        }
        form(action = "$roomPath/delete", method = FormMethod.post) {
            csrfField(session)
            hiddenField("version", "${room.version}")
            button(classes = "danger") { +"Raum löschen" }
        }
        p { a(href = roomsPath(schoolId)) { +"Zur Raumliste" } }
    }
}

suspend fun ApplicationCall.respondFeatureList(
    schoolId: SchoolId,
    features: List<RoomFeature>,
    errors: List<ValidationError> = emptyList(),
    enteredName: String? = null,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    respondPage("Ausstattung", status) { session ->
        ul {
            features.forEach { feature ->
                li {
                    +feature.name
                    +" "
                    form(action = "${featuresPath(schoolId)}/${feature.id.value}/delete", method = FormMethod.post, classes = "inline") {
                        csrfField(session)
                        button(classes = "link") { +"löschen" }
                    }
                }
            }
        }
        h2 { +"Ausstattung anlegen" }
        validationErrors(errors)
        form(action = featuresPath(schoolId), method = FormMethod.post) {
            csrfField(session)
            textField("name", "Name", enteredName)
            button { +"Anlegen" }
        }
        p { a(href = roomsPath(schoolId)) { +"Zu den Räumen" } }
    }
}
