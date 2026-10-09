package timetablescheduler.adapters.web.generation

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import timetablescheduler.adapters.web.WebDependencies
import timetablescheduler.adapters.web.auth.SESSION_AUTH
import timetablescheduler.adapters.web.auth.actor
import timetablescheduler.adapters.web.layout.FormInput
import timetablescheduler.adapters.web.layout.idParameter
import timetablescheduler.adapters.web.layout.receiveCheckedForm
import timetablescheduler.adapters.web.layout.respondFormResult
import timetablescheduler.adapters.web.layout.respondResult
import timetablescheduler.adapters.web.layout.seeOther
import timetablescheduler.adapters.web.layout.text
import timetablescheduler.adapters.web.layout.uuids
import timetablescheduler.adapters.web.school.schoolIdParameter
import timetablescheduler.adapters.web.timetable.TimetableView
import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.scheduling.SchedulingUseCases
import timetablescheduler.application.scheduling.StartRequest
import timetablescheduler.domain.common.AccountId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.scheduling.constraint.SchedulingConstraintId
import timetablescheduler.domain.scheduling.generation.AlternativeId
import timetablescheduler.domain.scheduling.generation.GenerationId
import timetablescheduler.domain.validation.ValidationError
import java.time.Instant

private suspend fun ApplicationCall.generationIdParameter(): GenerationId? = idParameter("generationId")?.let(::GenerationId)

private suspend fun ApplicationCall.alternativeIdParameter(): AlternativeId? = idParameter("alternativeId")?.let(::AlternativeId)

private suspend fun ApplicationCall.showStartFormAgain(
    scheduling: SchedulingUseCases,
    actor: AccountId,
    schoolId: SchoolId,
    errors: List<ValidationError>,
    enteredTimeLimit: String,
) {
    respondResult(scheduling.list.execute(actor, schoolId)) {
        respondGenerationList(it, errors, enteredTimeLimit, HttpStatusCode.UnprocessableEntity)
    }
}

/** Generations: start, progress, stop, results and alternatives (002 US1, contracts/web-routes.md). */
fun Route.generationRoutes(dependencies: WebDependencies) {
    val scheduling = dependencies.useCases.scheduling

    authenticate(SESSION_AUTH) {
        get("/schools/{schoolId}/generations") {
            val schoolId = call.schoolIdParameter() ?: return@get
            call.respondResult(scheduling.list.execute(call.actor, schoolId)) { call.respondGenerationList(it) }
        }

        post("/schools/{schoolId}/generations") {
            val schoolId = call.schoolIdParameter() ?: return@post
            val form = call.receiveCheckedForm()
            val input = FormInput(form)
            val timeLimit = input.int("timeLimitMinutes")
            val result = if (timeLimit == null) UseCaseResult.Failure(ApplicationError.Invalid(input.errors))
            else scheduling.start.execute(
                call.actor,
                schoolId,
                StartRequest(
                    timeLimitMinutes = timeLimit,
                    warningsConfirmed = form[StartRequest.FIELD_WARNINGS_CONFIRMED] == "true",
                    separateObjectiveIds = form.uuids(StartRequest.FIELD_SEPARATE_OBJECTIVES).map(::SchedulingConstraintId),
                ),
            )
            call.respondFormResult(
                result,
                generationsPath(schoolId),
                onInvalid = { errors -> call.showStartFormAgain(scheduling, call.actor, schoolId, errors, form.text("timeLimitMinutes")) },
            ) { generation -> call.seeOther(generationPath(generation)) }
        }

        get("/schools/{schoolId}/generations/{generationId}") {
            val schoolId = call.schoolIdParameter() ?: return@get
            val generationId = call.generationIdParameter() ?: return@get
            val descending = call.parameters["dir"] != "asc"
            call.respondResult(scheduling.get.execute(call.actor, schoolId, generationId)) {
                call.respondGenerationDetails(it, call.parameters["sort"], descending, Instant.now())
            }
        }

        post("/schools/{schoolId}/generations/{generationId}/stop") {
            val schoolId = call.schoolIdParameter() ?: return@post
            val generationId = call.generationIdParameter() ?: return@post
            call.receiveCheckedForm()
            call.respondResult(scheduling.stop.execute(call.actor, schoolId, generationId)) {
                call.seeOther("${generationsPath(schoolId)}/${generationId.value}")
            }
        }

        get("/schools/{schoolId}/generations/{generationId}/alternatives/{alternativeId}") {
            val schoolId = call.schoolIdParameter() ?: return@get
            val generationId = call.generationIdParameter() ?: return@get
            val alternativeId = call.alternativeIdParameter() ?: return@get
            val view = TimetableView.fromParameter(call.parameters["view"])
            call.respondResult(scheduling.getAlternative.execute(call.actor, schoolId, generationId, alternativeId)) { details ->
                call.respondAlternative(details, view, selectedEntity(view, call.parameters["id"], details.names))
            }
        }
    }
}
