package timetablescheduler.adapters.web.room

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import kotlinx.html.FormMethod
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.tr
import kotlinx.html.ul
import timetablescheduler.adapters.web.i18n.texts
import timetablescheduler.adapters.web.layout.csrfField
import timetablescheduler.adapters.web.layout.featureCheckboxes
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
data class RoomFormInput(
    val identifier: String,
    val featureIds: Set<RoomFeatureId>,
)

suspend fun ApplicationCall.respondRoomList(
    schoolId: SchoolId,
    overview: RoomOverview,
    errors: List<ValidationError> = emptyList(),
    entered: RoomFormInput? = null,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    val texts = texts
    val featureNames = overview.features.associate { it.id to it.name }
    respondPage(texts.rooms.roomsTitle, status) { session ->
        table {
            tr {
                th { +texts.rooms.roomColumn }
                th { +texts.common.roomFeatures }
            }
            overview.rooms.forEach { room ->
                tr {
                    td { a(href = "${roomsPath(schoolId)}/${room.id.value}") { +room.identifier } }
                    td {
                        +room.featureIds
                            .mapNotNull { featureNames[it] }
                            .sorted()
                            .joinToString(", ")
                    }
                }
            }
        }
        h2 { +texts.rooms.createRoom }
        validationErrors(errors, texts)
        form(action = roomsPath(schoolId), method = FormMethod.post) {
            csrfField(session)
            textField("identifier", texts.common.identifier, entered?.identifier)
            featureCheckboxes("featureId", overview.features, entered?.featureIds.orEmpty(), texts)
            button { +texts.common.create }
        }
        p { a(href = featuresPath(schoolId)) { +texts.rooms.manageFeatures } }

        p { a(href = "/schools/${schoolId.value}") { +texts.common.toSchool } }
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
    val texts = texts
    respondPage(texts.rooms.roomTitle(room.identifier), status) { session ->
        validationErrors(errors, texts)
        form(action = roomPath, method = FormMethod.post) {
            csrfField(session)
            hiddenField("version", "${room.version}")
            textField("identifier", texts.common.identifier, entered?.identifier ?: room.identifier)
            featureCheckboxes("featureId", details.features, entered?.featureIds ?: room.featureIds, texts)
            button { +texts.common.save }
        }
        form(action = "$roomPath/delete", method = FormMethod.post) {
            csrfField(session)
            hiddenField("version", "${room.version}")
            button(classes = "danger") { +texts.rooms.deleteRoom }
        }
        p { a(href = roomsPath(schoolId)) { +texts.rooms.toRoomList } }
    }
}

suspend fun ApplicationCall.respondFeatureList(
    schoolId: SchoolId,
    features: List<RoomFeature>,
    errors: List<ValidationError> = emptyList(),
    enteredName: String? = null,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    val texts = texts
    respondPage(texts.common.roomFeatures, status) { session ->
        ul {
            features.forEach { feature ->
                li {
                    +feature.name
                    +" "
                    form(action = "${featuresPath(schoolId)}/${feature.id.value}/delete", method = FormMethod.post, classes = "inline") {
                        csrfField(session)
                        button(classes = "link") { +texts.common.delete }
                    }
                }
            }
        }
        h2 { +texts.rooms.createFeature }
        validationErrors(errors, texts)
        form(action = featuresPath(schoolId), method = FormMethod.post) {
            csrfField(session)
            textField("name", texts.common.name, enteredName)
            button { +texts.common.create }
        }
        p { a(href = roomsPath(schoolId)) { +texts.rooms.toRooms } }
    }
}
