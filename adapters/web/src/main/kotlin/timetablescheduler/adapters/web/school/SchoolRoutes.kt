package timetablescheduler.adapters.web.school

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
import timetablescheduler.adapters.web.layout.respondBusinessFailure
import timetablescheduler.adapters.web.layout.respondFormResult
import timetablescheduler.adapters.web.layout.respondResult
import timetablescheduler.adapters.web.layout.seeOther
import timetablescheduler.adapters.web.layout.text
import timetablescheduler.adapters.web.layout.version
import timetablescheduler.application.error.ApplicationError
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.validation.ValidationError

/** Reads `{schoolId}`; answers 404 itself and returns null when it is malformed. */
suspend fun ApplicationCall.schoolIdParameter(): SchoolId? = idParameter("schoolId")?.let(::SchoolId)

/** The installation has exactly one school (FR-001): `/schools` leads to it, only renaming is offered. */
fun Route.schoolRoutes(dependencies: WebDependencies) {
    val schools = dependencies.useCases.schools
    val readiness = dependencies.useCases.readiness

    /** The dashboard shows the school's sections and how many readiness issues are open (US4). */
    suspend fun ApplicationCall.respondDashboard(
        schoolId: SchoolId,
        errors: List<ValidationError> = emptyList(),
        enteredName: String? = null,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) {
        respondResult(schools.dashboard.execute(actor, schoolId)) { dashboard ->
            respondResult(readiness.execute(actor, schoolId)) { report ->
                respondSchoolDashboard(dashboard, report.issues.size, errors, enteredName, status)
            }
        }
    }

    authenticate(SESSION_AUTH) {
        get("/schools") {
            call.respondResult(schools.list.execute(call.actor)) { accessible ->
                val school = accessible.firstOrNull()
                if (school == null) call.respondBusinessFailure(ApplicationError.NotFound) else call.seeOther(schoolPath(school))
            }
        }

        get("/schools/{schoolId}") {
            val schoolId = call.schoolIdParameter() ?: return@get
            call.respondDashboard(schoolId)
        }

        post("/schools/{schoolId}") {
            val schoolId = call.schoolIdParameter() ?: return@post
            val form = call.receiveCheckedForm()
            val name = form.text("name")
            call.respondFormResult(
                schools.rename.execute(call.actor, schoolId, name, form.version()),
                backLink = "/schools/${schoolId.value}",
                onInvalid = { errors -> call.respondDashboard(schoolId, errors, name, HttpStatusCode.UnprocessableEntity) },
            ) { call.seeOther(schoolPath(it)) }
        }
    }
}
