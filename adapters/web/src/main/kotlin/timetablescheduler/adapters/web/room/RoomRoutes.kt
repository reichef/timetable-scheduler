package timetablescheduler.adapters.web.room

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import timetablescheduler.adapters.web.WebDependencies
import timetablescheduler.adapters.web.auth.SESSION_AUTH
import timetablescheduler.adapters.web.auth.actor
import timetablescheduler.adapters.web.layout.idParameter
import timetablescheduler.adapters.web.layout.receiveCheckedForm
import timetablescheduler.adapters.web.layout.respondFormResult
import timetablescheduler.adapters.web.layout.respondResult
import timetablescheduler.adapters.web.layout.seeOther
import timetablescheduler.adapters.web.layout.text
import timetablescheduler.adapters.web.layout.uuids
import timetablescheduler.adapters.web.layout.version
import timetablescheduler.adapters.web.school.schoolIdParameter
import timetablescheduler.domain.common.RoomFeatureId
import timetablescheduler.domain.common.RoomId

private suspend fun ApplicationCall.roomIdParameter(): RoomId? = idParameter("roomId")?.let(::RoomId)

fun Route.roomRoutes(dependencies: WebDependencies) {
    val rooms = dependencies.useCases.rooms

    authenticate(SESSION_AUTH) {
        get("/schools/{schoolId}/room-features") {
            val schoolId = call.schoolIdParameter() ?: return@get
            call.respondResult(rooms.listRooms.execute(call.actor, schoolId)) { call.respondFeatureList(schoolId, it.features) }
        }

        post("/schools/{schoolId}/room-features") {
            val schoolId = call.schoolIdParameter() ?: return@post
            val name = call.receiveCheckedForm().text("name")
            call.respondFormResult(
                rooms.createFeature.execute(call.actor, schoolId, name),
                backLink = featuresPath(schoolId),
                onInvalid = { errors ->
                    call.respondResult(rooms.listRooms.execute(call.actor, schoolId)) {
                        call.respondFeatureList(schoolId, it.features, errors, name, HttpStatusCode.UnprocessableEntity)
                    }
                },
            ) { call.seeOther(featuresPath(schoolId)) }
        }

        post("/schools/{schoolId}/room-features/{featureId}/delete") {
            val schoolId = call.schoolIdParameter() ?: return@post
            val featureId = call.idParameter("featureId")?.let(::RoomFeatureId) ?: return@post
            call.receiveCheckedForm()
            call.respondResult(rooms.deleteFeature.execute(call.actor, schoolId, featureId), featuresPath(schoolId)) {
                call.seeOther(featuresPath(schoolId))
            }
        }

        get("/schools/{schoolId}/rooms") {
            val schoolId = call.schoolIdParameter() ?: return@get
            call.respondResult(rooms.listRooms.execute(call.actor, schoolId)) { call.respondRoomList(schoolId, it) }
        }

        post("/schools/{schoolId}/rooms") {
            val schoolId = call.schoolIdParameter() ?: return@post
            val form = call.receiveCheckedForm()
            val entered = RoomFormInput(form.text("identifier"), form.uuids("featureId").map(::RoomFeatureId).toSet())
            call.respondFormResult(
                rooms.createRoom.execute(call.actor, schoolId, entered.identifier, entered.featureIds),
                backLink = roomsPath(schoolId),
                onInvalid = { errors ->
                    call.respondResult(rooms.listRooms.execute(call.actor, schoolId)) {
                        call.respondRoomList(schoolId, it, errors, entered, HttpStatusCode.UnprocessableEntity)
                    }
                },
            ) { call.seeOther(roomsPath(schoolId)) }
        }

        get("/schools/{schoolId}/rooms/{roomId}") {
            val schoolId = call.schoolIdParameter() ?: return@get
            val roomId = call.roomIdParameter() ?: return@get
            call.respondResult(rooms.getRoom.execute(call.actor, schoolId, roomId)) { call.respondRoomDetails(schoolId, it) }
        }

        post("/schools/{schoolId}/rooms/{roomId}") {
            val schoolId = call.schoolIdParameter() ?: return@post
            val roomId = call.roomIdParameter() ?: return@post
            val form = call.receiveCheckedForm()
            val entered = RoomFormInput(form.text("identifier"), form.uuids("featureId").map(::RoomFeatureId).toSet())
            val roomPath = "${roomsPath(schoolId)}/${roomId.value}"

            call.respondFormResult(
                rooms.updateRoom.execute(call.actor, schoolId, roomId, entered.identifier, entered.featureIds, form.version()),
                backLink = roomPath,
                onInvalid = { errors ->
                    call.respondResult(rooms.getRoom.execute(call.actor, schoolId, roomId)) {
                        call.respondRoomDetails(schoolId, it, errors, entered, HttpStatusCode.UnprocessableEntity)
                    }
                },
            ) { call.seeOther(roomsPath(schoolId)) }
        }

        post("/schools/{schoolId}/rooms/{roomId}/delete") {
            val schoolId = call.schoolIdParameter() ?: return@post
            val roomId = call.roomIdParameter() ?: return@post
            val form = call.receiveCheckedForm()
            call.respondResult(rooms.deleteRoom.execute(call.actor, schoolId, roomId, form.version()), roomsPath(schoolId)) {
                call.seeOther(roomsPath(schoolId))
            }
        }
    }
}
