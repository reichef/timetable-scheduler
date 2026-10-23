package timetablescheduler.adapters.web.generation

import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
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
import timetablescheduler.adapters.web.timetable.chosenTimetablePath
import timetablescheduler.adapters.web.timetable.selectedEntity
import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.scheduling.ChooseOutcome
import timetablescheduler.application.scheduling.SchedulingUseCases
import timetablescheduler.application.scheduling.StartRequest
import timetablescheduler.domain.common.AccountId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.scheduling.constraint.SchedulingConstraintId
import timetablescheduler.domain.scheduling.generation.AlternativeId
import timetablescheduler.domain.scheduling.generation.Generation
import timetablescheduler.domain.scheduling.generation.GenerationId
import timetablescheduler.domain.validation.ValidationError
import java.time.Instant

private suspend fun ApplicationCall.generationIdParameter(): GenerationId? = idParameter("generationId")?.let(::GenerationId)

private suspend fun ApplicationCall.alternativeIdParameter(): AlternativeId? = idParameter("alternativeId")?.let(::AlternativeId)

private fun Parameters.startsFromChosen() = this[StartRequest.FIELD_START_FROM_CHOSEN] == "true"

/** Reads the start form; a time limit or share that is no whole number is reported like a business-rule violation. */
private fun Parameters.parseStart(start: (StartRequest) -> UseCaseResult<Generation>): UseCaseResult<Generation> {
    val input = FormInput(this)
    val timeLimit = input.int("timeLimitMinutes")
    // Older forms without the field start with the default share (FR-007a).
    val share =
        if (text(StartRequest.FIELD_MINIMUM_PLACED_SHARE).isBlank()) {
            Generation.DEFAULT_MINIMUM_PLACED_SHARE
        } else {
            input.int(StartRequest.FIELD_MINIMUM_PLACED_SHARE)
        }
    if (timeLimit == null || share == null) {
        return UseCaseResult.Failure(ApplicationError.Invalid(input.errors))
    }

    return start(
        StartRequest(
            timeLimitMinutes = timeLimit,
            warningsConfirmed = this[StartRequest.FIELD_WARNINGS_CONFIRMED] == "true",
            separateObjectiveIds = uuids(StartRequest.FIELD_SEPARATE_OBJECTIVES).map(::SchedulingConstraintId),
            startFromChosen = startsFromChosen(),
            minimumPlacedShare = share,
        ),
    )
}

private suspend fun ApplicationCall.showStartFormAgain(
    scheduling: SchedulingUseCases,
    schoolId: SchoolId,
    errors: List<ValidationError>,
    form: Parameters,
) {
    respondResult(scheduling.list.execute(actor, schoolId)) {
        respondGenerationList(
            it,
            errors,
            form.text("timeLimitMinutes"),
            HttpStatusCode.UnprocessableEntity,
            form.startsFromChosen(),
            form.text(StartRequest.FIELD_MINIMUM_PLACED_SHARE),
        )
    }
}

/**
 * Generations: start, progress, stop, results, alternatives, their comparison and choosing one (002 US1, US3,
 * contracts/web-routes.md).
 */
fun Route.generationRoutes(dependencies: WebDependencies) {
    val scheduling = dependencies.useCases.scheduling

    authenticate(SESSION_AUTH) {
        generationRunRoutes(scheduling)
        alternativeRoutes(scheduling)
    }
}

/** Starting a generation, following its progress, stopping and deleting it (002 US1). */
private fun Route.generationRunRoutes(scheduling: SchedulingUseCases) {
    get("/schools/{schoolId}/generations") {
        val schoolId = call.schoolIdParameter() ?: return@get

        call.respondResult(scheduling.list.execute(call.actor, schoolId)) {
            call.respondGenerationList(it, startFromChosen = call.parameters.startsFromChosen())
        }
    }

    post("/schools/{schoolId}/generations") {
        val schoolId = call.schoolIdParameter() ?: return@post
        val form = call.receiveCheckedForm()
        val result = form.parseStart { request -> scheduling.start.execute(call.actor, schoolId, request) }

        call.respondFormResult(
            result,
            generationsPath(schoolId),
            onInvalid = { errors -> call.showStartFormAgain(scheduling, schoolId, errors, form) },
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

    get("/schools/{schoolId}/generations/{generationId}/delete") {
        val schoolId = call.schoolIdParameter() ?: return@get
        val generationId = call.generationIdParameter() ?: return@get
        call.respondResult(scheduling.get.execute(call.actor, schoolId, generationId)) { call.respondDeleteConfirmation(it) }
    }

    post("/schools/{schoolId}/generations/{generationId}/delete") {
        val schoolId = call.schoolIdParameter() ?: return@post
        val generationId = call.generationIdParameter() ?: return@post
        call.receiveCheckedForm()
        call.respondResult(scheduling.delete.execute(call.actor, schoolId, generationId), generationsPath(schoolId)) {
            call.seeOther(generationsPath(schoolId))
        }
    }
}

/** The alternatives of a generation: one alternative, the comparison of all, the differences of two, choosing one (002 US3). */
private fun Route.alternativeRoutes(scheduling: SchedulingUseCases) {
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

        val result =
            scheduling.choose.execute(
                call.actor,
                schoolId,
                generationId,
                alternativeId,
                expectedVersion,
                unplacedConfirmed = form[FIELD_UNPLACED_CONFIRMED] == "true",
            )
        val backLink = "${generationsPath(schoolId)}/${generationId.value}/alternatives/${alternativeId.value}"

        call.respondResult(result, backLink) { outcome ->
            when (outcome) {
                is ChooseOutcome.Chosen -> {
                    call.seeOther(chosenTimetablePath(schoolId))
                }

                is ChooseOutcome.ConfirmationRequired -> {
                    call.respondChooseConfirmation(
                        outcome.generation,
                        outcome.alternative,
                        outcome.unplaced,
                        outcome.names,
                        enteredVersion,
                    )
                }
            }
        }
    }
}
