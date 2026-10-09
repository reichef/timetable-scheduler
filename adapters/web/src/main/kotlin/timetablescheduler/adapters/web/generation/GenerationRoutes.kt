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
import timetablescheduler.adapters.web.timetable.chosenTimetablePath
import timetablescheduler.adapters.web.timetable.selectedEntity
import timetablescheduler.application.scheduling.ChooseOutcome
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
    startFromChosen: Boolean,
) {
    respondResult(scheduling.list.execute(actor, schoolId)) {
        respondGenerationList(it, errors, enteredTimeLimit, HttpStatusCode.UnprocessableEntity, startFromChosen)
    }
}

/**
 * Generations: start, progress, stop, results, alternatives, their comparison and choosing one (002 US1, US3,
 * contracts/web-routes.md).
 */
fun Route.generationRoutes(dependencies: WebDependencies) {
    val scheduling = dependencies.useCases.scheduling

    authenticate(SESSION_AUTH) {
        get("/schools/{schoolId}/generations") {
            val schoolId = call.schoolIdParameter() ?: return@get
            val startFromChosen = call.parameters[StartRequest.FIELD_START_FROM_CHOSEN] == "true"
            call.respondResult(scheduling.list.execute(call.actor, schoolId)) { call.respondGenerationList(it, startFromChosen = startFromChosen) }
        }

        post("/schools/{schoolId}/generations") {
            val schoolId = call.schoolIdParameter() ?: return@post
            val form = call.receiveCheckedForm()
            val input = FormInput(form)
            val timeLimit = input.int("timeLimitMinutes")
            val startFromChosen = form[StartRequest.FIELD_START_FROM_CHOSEN] == "true"
            val result = if (timeLimit == null) UseCaseResult.Failure(ApplicationError.Invalid(input.errors))
            else scheduling.start.execute(
                call.actor,
                schoolId,
                StartRequest(
                    timeLimitMinutes = timeLimit,
                    warningsConfirmed = form[StartRequest.FIELD_WARNINGS_CONFIRMED] == "true",
                    separateObjectiveIds = form.uuids(StartRequest.FIELD_SEPARATE_OBJECTIVES).map(::SchedulingConstraintId),
                    startFromChosen = startFromChosen,
                ),
            )
            call.respondFormResult(
                result,
                generationsPath(schoolId),
                onInvalid = { errors -> call.showStartFormAgain(scheduling, call.actor, schoolId, errors, form.text("timeLimitMinutes"), startFromChosen) },
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

        get("/schools/{schoolId}/generations/{generationId}/compare") {
            val schoolId = call.schoolIdParameter() ?: return@get
            val generationId = call.generationIdParameter() ?: return@get
            val descending = call.parameters["dir"] != "asc"
            call.respondResult(scheduling.get.execute(call.actor, schoolId, generationId)) {
                call.respondComparison(it, call.parameters["x"], call.parameters["y"], call.parameters["sort"], descending)
            }
        }

        get("/schools/{schoolId}/generations/{generationId}/alternatives/{alternativeId}/diff/{otherId}") {
            val schoolId = call.schoolIdParameter() ?: return@get
            val generationId = call.generationIdParameter() ?: return@get
            val alternativeId = call.alternativeIdParameter() ?: return@get
            val otherId = call.idParameter("otherId")?.let(::AlternativeId) ?: return@get
            val view = TimetableView.fromParameter(call.parameters["view"])
            call.respondResult(scheduling.compare.execute(call.actor, schoolId, generationId, alternativeId, otherId)) { comparison ->
                call.respondDiff(comparison, view, selectedEntity(view, call.parameters["id"], comparison.names))
            }
        }

        post("/schools/{schoolId}/generations/{generationId}/alternatives/{alternativeId}/choose") {
            val schoolId = call.schoolIdParameter() ?: return@post
            val generationId = call.generationIdParameter() ?: return@post
            val alternativeId = call.alternativeIdParameter() ?: return@post
            val form = call.receiveCheckedForm()
            val enteredVersion = form.text(FIELD_CHOSEN_VERSION).trim()
            // An unreadable version can never match the current choice and is answered as a concurrent change.
            val expectedVersion = if (enteredVersion.isEmpty()) null else enteredVersion.toIntOrNull() ?: -1
            val result = scheduling.choose.execute(
                call.actor, schoolId, generationId, alternativeId, expectedVersion, unplacedConfirmed = form[FIELD_UNPLACED_CONFIRMED] == "true",
            )
            val backLink = "${generationsPath(schoolId)}/${generationId.value}/alternatives/${alternativeId.value}"
            call.respondResult(result, backLink) { outcome ->
                when (outcome) {
                    is ChooseOutcome.Chosen -> call.seeOther(chosenTimetablePath(schoolId))
                    is ChooseOutcome.ConfirmationRequired ->
                        call.respondChooseConfirmation(outcome.generation, outcome.alternative, outcome.unplaced, outcome.names, enteredVersion)
                }
            }
        }
    }
}
