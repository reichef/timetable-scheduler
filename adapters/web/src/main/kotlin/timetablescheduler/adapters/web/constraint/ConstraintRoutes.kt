package timetablescheduler.adapters.web.constraint

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
import timetablescheduler.adapters.web.layout.idParameter
import timetablescheduler.adapters.web.layout.receiveCheckedForm
import timetablescheduler.adapters.web.layout.respondBusinessFailure
import timetablescheduler.adapters.web.layout.respondFormResult
import timetablescheduler.adapters.web.layout.respondResult
import timetablescheduler.adapters.web.layout.seeOther
import timetablescheduler.adapters.web.layout.version
import timetablescheduler.adapters.web.school.schoolIdParameter
import timetablescheduler.application.MasterDataUseCases
import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.domain.common.SchoolClassId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.common.SubjectId
import timetablescheduler.domain.common.TeacherId
import timetablescheduler.domain.scheduling.constraint.ConstraintType
import timetablescheduler.domain.scheduling.constraint.SchedulingConstraint
import timetablescheduler.domain.scheduling.constraint.SchedulingConstraintId
import timetablescheduler.domain.scheduling.constraint.TeacherFixationId
import timetablescheduler.domain.scheduling.constraint.type
import timetablescheduler.domain.validation.ValidationError
import java.util.UUID

private suspend fun ApplicationCall.constraintIdParameter(): SchedulingConstraintId? =
    idParameter("constraintId")?.let(::SchedulingConstraintId)

private suspend fun ApplicationCall.fixationIdParameter(): TeacherFixationId? = idParameter("fixationId")?.let(::TeacherFixationId)

private fun Parameters.uuid(name: String): UUID? = this[name]?.let { runCatching { UUID.fromString(it) }.getOrNull() }

/** Shows the form of [type] again with the entered values and messages (422). */
private suspend fun ApplicationCall.showConstraintFormAgain(
    useCases: MasterDataUseCases,
    schoolId: SchoolId,
    type: ConstraintType,
    existing: SchedulingConstraint?,
    form: Parameters,
    errors: List<ValidationError>,
) {
    respondResult(useCases.constraints.list.execute(actor, schoolId)) {
        respondConstraintForm(it, type, existing, form, errors, HttpStatusCode.UnprocessableEntity)
    }
}

/** Constraints and teacher fixations (002 US2, contracts/web-routes.md). */
fun Route.constraintRoutes(dependencies: WebDependencies) {
    val useCases = dependencies.useCases
    val constraints = useCases.constraints

    authenticate(SESSION_AUTH) {
        get("/schools/{schoolId}/constraints") {
            val schoolId = call.schoolIdParameter() ?: return@get
            call.respondResult(constraints.list.execute(call.actor, schoolId)) { call.respondConstraintList(it) }
        }

        get("/schools/{schoolId}/constraints/new") {
            val schoolId = call.schoolIdParameter() ?: return@get
            val type = ConstraintForm.typeOf(call.parameters["type"]) ?: return@get call.respondBusinessFailure(ApplicationError.NotFound)
            call.respondResult(constraints.list.execute(call.actor, schoolId)) { call.respondConstraintForm(it, type) }
        }

        post("/schools/{schoolId}/constraints") {
            val schoolId = call.schoolIdParameter() ?: return@post
            val form = call.receiveCheckedForm()
            val type = ConstraintForm.typeOf(form["type"]) ?: return@post call.respondBusinessFailure(ApplicationError.NotFound)
            val grid = useCases.timeGrid.getTimeGrid.execute(call.actor, schoolId)
            if (grid !is UseCaseResult.Success) {
                return@post call.respondResult(grid) {}
            }
            val result =
                when (val parsed = ConstraintForm.parse(form, type, grid.value.timeGrid)) {
                    is ParsedConstraint.Invalid -> UseCaseResult.Failure(ApplicationError.Invalid(parsed.errors))
                    is ParsedConstraint.Valid -> constraints.create.execute(call.actor, schoolId, parsed.input)
                }
            call.respondFormResult(
                result,
                constraintsPath(schoolId),
                onInvalid = { errors -> call.showConstraintFormAgain(useCases, schoolId, type, null, form, errors) },
            ) { call.seeOther(constraintsPath(schoolId)) }
        }

        get("/schools/{schoolId}/constraints/{constraintId}") {
            val schoolId = call.schoolIdParameter() ?: return@get
            val constraintId = call.constraintIdParameter() ?: return@get
            call.respondResult(constraints.get.execute(call.actor, schoolId, constraintId)) { constraint ->
                call.respondResult(
                    constraints.list.execute(call.actor, schoolId),
                ) { call.respondConstraintForm(it, constraint.rule.type, constraint) }
            }
        }

        post("/schools/{schoolId}/constraints/{constraintId}") {
            val schoolId = call.schoolIdParameter() ?: return@post
            val constraintId = call.constraintIdParameter() ?: return@post
            val form = call.receiveCheckedForm()
            val existing = constraints.get.execute(call.actor, schoolId, constraintId)
            if (existing !is UseCaseResult.Success) {
                return@post call.respondResult(existing) {}
            }
            val type = existing.value.rule.type
            val grid = useCases.timeGrid.getTimeGrid.execute(call.actor, schoolId)
            if (grid !is UseCaseResult.Success) {
                return@post call.respondResult(grid) {}
            }
            val result =
                when (val parsed = ConstraintForm.parse(form, type, grid.value.timeGrid)) {
                    is ParsedConstraint.Invalid -> {
                        UseCaseResult.Failure(ApplicationError.Invalid(parsed.errors))
                    }

                    is ParsedConstraint.Valid -> {
                        constraints.update.execute(
                            call.actor,
                            schoolId,
                            constraintId,
                            parsed.input,
                            form.version(),
                        )
                    }
                }
            call.respondFormResult(
                result,
                constraintPath(existing.value),
                onInvalid = { errors -> call.showConstraintFormAgain(useCases, schoolId, type, existing.value, form, errors) },
            ) { call.seeOther(constraintsPath(schoolId)) }
        }

        post("/schools/{schoolId}/constraints/{constraintId}/active") {
            val schoolId = call.schoolIdParameter() ?: return@post
            val constraintId = call.constraintIdParameter() ?: return@post
            val form = call.receiveCheckedForm()
            call.respondResult(
                constraints.setActive.execute(call.actor, schoolId, constraintId, form["active"] == "true", form.version()),
                constraintsPath(schoolId),
            ) {
                call.seeOther(constraintsPath(schoolId))
            }
        }

        post("/schools/{schoolId}/constraints/{constraintId}/delete") {
            val schoolId = call.schoolIdParameter() ?: return@post
            val constraintId = call.constraintIdParameter() ?: return@post
            val form = call.receiveCheckedForm()
            call.respondResult(constraints.delete.execute(call.actor, schoolId, constraintId, form.version()), constraintsPath(schoolId)) {
                call.seeOther(constraintsPath(schoolId))
            }
        }

        fixationRoutes(useCases)
    }
}

private fun Route.fixationRoutes(useCases: MasterDataUseCases) {
    val fixations = useCases.fixations

    get("/schools/{schoolId}/teacher-fixations/new") {
        val schoolId = call.schoolIdParameter() ?: return@get
        call.respondResult(useCases.constraints.list.execute(call.actor, schoolId)) { call.respondFixationForm(it, null) }
    }

    post("/schools/{schoolId}/teacher-fixations") {
        val schoolId = call.schoolIdParameter() ?: return@post
        val form = call.receiveCheckedForm()
        val classId = form.uuid("classId")
        val subjectId = form.uuid("subjectId")
        val teacherId = form.uuid("teacherId")
        val result =
            if (classId == null || subjectId == null || teacherId == null) {
                UseCaseResult.Failure(ApplicationError.Invalid(listOf(ValidationError.InvalidReference("course"))))
            } else {
                fixations.create.execute(call.actor, schoolId, SchoolClassId(classId), SubjectId(subjectId), TeacherId(teacherId))
            }
        call.respondFormResult(
            result,
            constraintsPath(schoolId),
            onInvalid = { errors ->
                call.respondResult(useCases.constraints.list.execute(call.actor, schoolId)) {
                    call.respondFixationForm(it, null, form, errors, HttpStatusCode.UnprocessableEntity)
                }
            },
        ) { call.seeOther(constraintsPath(schoolId)) }
    }

    get("/schools/{schoolId}/teacher-fixations/{fixationId}") {
        val schoolId = call.schoolIdParameter() ?: return@get
        val fixationId = call.fixationIdParameter() ?: return@get
        call.respondResult(useCases.constraints.list.execute(call.actor, schoolId)) { overview ->
            val fixation =
                overview.fixations.firstOrNull { it.id == fixationId }
                    ?: return@respondResult call.respondBusinessFailure(ApplicationError.NotFound)
            call.respondFixationForm(overview, fixation)
        }
    }

    post("/schools/{schoolId}/teacher-fixations/{fixationId}") {
        val schoolId = call.schoolIdParameter() ?: return@post
        val fixationId = call.fixationIdParameter() ?: return@post
        val form = call.receiveCheckedForm()
        val teacherId = form.uuid("teacherId")
        val result =
            if (teacherId == null) {
                UseCaseResult.Failure(ApplicationError.Invalid(listOf(ValidationError.InvalidReference("teacherId"))))
            } else {
                fixations.update.execute(call.actor, schoolId, fixationId, TeacherId(teacherId), form.version())
            }
        call.respondFormResult(
            result,
            constraintsPath(schoolId),
            onInvalid = { errors ->
                call.respondResult(useCases.constraints.list.execute(call.actor, schoolId)) { overview ->
                    call.respondFixationForm(
                        overview,
                        overview.fixations.firstOrNull { it.id == fixationId },
                        form,
                        errors,
                        HttpStatusCode.UnprocessableEntity,
                    )
                }
            },
        ) { call.seeOther(constraintsPath(schoolId)) }
    }

    post("/schools/{schoolId}/teacher-fixations/{fixationId}/delete") {
        val schoolId = call.schoolIdParameter() ?: return@post
        val fixationId = call.fixationIdParameter() ?: return@post
        val form = call.receiveCheckedForm()
        call.respondResult(fixations.delete.execute(call.actor, schoolId, fixationId, form.version()), constraintsPath(schoolId)) {
            call.seeOther(constraintsPath(schoolId))
        }
    }
}
