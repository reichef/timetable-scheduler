package timetablescheduler.adapters.web.timegrid

import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import timetablescheduler.adapters.web.WebDependencies
import timetablescheduler.adapters.web.auth.actor
import timetablescheduler.adapters.web.auth.SESSION_AUTH
import timetablescheduler.adapters.web.layout.FormInput
import timetablescheduler.adapters.web.layout.daysOfWeek
import timetablescheduler.adapters.web.layout.idParameter
import timetablescheduler.adapters.web.layout.receiveCheckedForm
import timetablescheduler.adapters.web.layout.respondFormResult
import timetablescheduler.adapters.web.layout.respondResult
import timetablescheduler.adapters.web.layout.seeOther
import timetablescheduler.adapters.web.layout.text
import timetablescheduler.adapters.web.layout.version
import timetablescheduler.adapters.web.school.schoolIdParameter
import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.school.TimeGridUseCases
import timetablescheduler.domain.common.AccountId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.common.TimeSlotId
import timetablescheduler.domain.school.SlotKind
import timetablescheduler.domain.school.TimeGrid
import java.time.LocalTime

private suspend fun ApplicationCall.slotIdParameter(): TimeSlotId? = idParameter("slotId")?.let(::TimeSlotId)

private fun Parameters.slotInput() = SlotFormInput(text("label"), text("startTime"), text("endTime"), text("kind"))

/** Parses the slot form; format problems become [ApplicationError.Invalid] like business-rule violations. */
private fun Parameters.parseSlot(
    save: (label: String, startTime: LocalTime, endTime: LocalTime, kind: SlotKind) -> UseCaseResult<TimeGrid>,
): UseCaseResult<TimeGrid> {
    val input = FormInput(this)
    val startTime = input.time("startTime")
    val endTime = input.time("endTime")
    val kind = input.enum<SlotKind>("kind")
    if (startTime == null || endTime == null || kind == null) return UseCaseResult.Failure(ApplicationError.Invalid(input.errors))
    return save(text("label"), startTime, endTime, kind)
}

private suspend fun ApplicationCall.showGridAgain(
    timeGrid: TimeGridUseCases,
    actor: AccountId,
    schoolId: SchoolId,
    errors: List<timetablescheduler.domain.validation.ValidationError>,
    entered: SlotFormInput?,
) {
    respondResult(timeGrid.getTimeGrid.execute(actor, schoolId)) {
        respondTimeGrid(it, errors, entered, HttpStatusCode.UnprocessableEntity)
    }
}

fun Route.timeGridRoutes(dependencies: WebDependencies) {
    val timeGrid = dependencies.useCases.timeGrid

    authenticate(SESSION_AUTH) {
        get("/schools/{schoolId}/time-grid") {
            val schoolId = call.schoolIdParameter() ?: return@get
            call.respondResult(timeGrid.getTimeGrid.execute(call.actor, schoolId)) { call.respondTimeGrid(it) }
        }

        post("/schools/{schoolId}/time-grid/days") {
            val schoolId = call.schoolIdParameter() ?: return@post
            val form = call.receiveCheckedForm()
            call.respondFormResult(
                timeGrid.setTeachingDays.execute(call.actor, schoolId, form.daysOfWeek("dayOfWeek"), form.version()),
                backLink = timeGridPath(schoolId),
                onInvalid = { errors -> call.showGridAgain(timeGrid, call.actor, schoolId, errors, null) },
            ) { call.seeOther(timeGridPath(schoolId)) }
        }

        post("/schools/{schoolId}/time-grid/slots") {
            val schoolId = call.schoolIdParameter() ?: return@post
            val form = call.receiveCheckedForm()
            val result = form.parseSlot { label, start, end, kind ->
                timeGrid.addSlot.execute(call.actor, schoolId, label, start, end, kind, form.version())
            }
            call.respondFormResult(
                result,
                backLink = timeGridPath(schoolId),
                onInvalid = { errors -> call.showGridAgain(timeGrid, call.actor, schoolId, errors, form.slotInput()) },
            ) { call.seeOther(timeGridPath(schoolId)) }
        }

        get("/schools/{schoolId}/time-grid/slots/{slotId}") {
            val schoolId = call.schoolIdParameter() ?: return@get
            val slotId = call.slotIdParameter() ?: return@get
            call.respondResult(timeGrid.getTimeGrid.execute(call.actor, schoolId)) { view ->
                val slot = view.timeGrid.slotById(slotId)
                    ?: return@respondResult call.respondResult(UseCaseResult.Failure(ApplicationError.NotFound)) {}
                call.respondSlotEditor(view, slot)
            }
        }

        post("/schools/{schoolId}/time-grid/slots/{slotId}") {
            val schoolId = call.schoolIdParameter() ?: return@post
            val slotId = call.slotIdParameter() ?: return@post
            val form = call.receiveCheckedForm()
            val result = form.parseSlot { label, start, end, kind ->
                timeGrid.updateSlot.execute(call.actor, schoolId, slotId, label, start, end, kind, form.version())
            }
            call.respondFormResult(
                result,
                backLink = slotPath(schoolId, slotId),
                onInvalid = { errors ->
                    call.respondResult(timeGrid.getTimeGrid.execute(call.actor, schoolId)) { view ->
                        val slot = view.timeGrid.slotById(slotId)
                            ?: return@respondResult call.respondResult(UseCaseResult.Failure(ApplicationError.NotFound)) {}
                        call.respondSlotEditor(view, slot, errors, form.slotInput(), HttpStatusCode.UnprocessableEntity)
                    }
                },
            ) { call.seeOther(timeGridPath(schoolId)) }
        }

        post("/schools/{schoolId}/time-grid/slots/{slotId}/exclusions") {
            val schoolId = call.schoolIdParameter() ?: return@post
            val slotId = call.slotIdParameter() ?: return@post
            val form = call.receiveCheckedForm()
            call.respondFormResult(
                timeGrid.setExclusions.execute(call.actor, schoolId, slotId, form.daysOfWeek("dayOfWeek"), form.version()),
                backLink = slotPath(schoolId, slotId),
                onInvalid = { errors -> call.showGridAgain(timeGrid, call.actor, schoolId, errors, null) },
            ) { call.seeOther(timeGridPath(schoolId)) }
        }

        get("/schools/{schoolId}/time-grid/slots/{slotId}/delete") {
            val schoolId = call.schoolIdParameter() ?: return@get
            val slotId = call.slotIdParameter() ?: return@get
            call.respondResult(timeGrid.previewDeletion.execute(call.actor, schoolId, slotId)) {
                call.respondSlotDeletionConfirmation(schoolId, it)
            }
        }

        post("/schools/{schoolId}/time-grid/slots/{slotId}/delete") {
            val schoolId = call.schoolIdParameter() ?: return@post
            val slotId = call.slotIdParameter() ?: return@post
            val form = call.receiveCheckedForm()
            call.respondResult(timeGrid.deleteSlot.execute(call.actor, schoolId, slotId, form.version()), timeGridPath(schoolId)) {
                call.seeOther(timeGridPath(schoolId))
            }
        }
    }
}
