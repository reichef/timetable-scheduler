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
import timetablescheduler.adapters.web.layout.respondFormResult
import timetablescheduler.adapters.web.layout.respondResult
import timetablescheduler.adapters.web.layout.seeOther
import timetablescheduler.adapters.web.layout.text
import timetablescheduler.adapters.web.layout.version
import timetablescheduler.domain.common.SchoolId

/** Reads `{schoolId}`; answers 404 itself and returns null when it is malformed. */
suspend fun ApplicationCall.schoolIdParameter(): SchoolId? = idParameter("schoolId")?.let(::SchoolId)

fun Route.schoolRoutes(dependencies: WebDependencies) {
    val schools = dependencies.useCases.schools

    authenticate(SESSION_AUTH) {
        get("/schools") {
            call.respondResult(schools.list.execute(call.actor)) { call.respondSchoolList(it) }
        }

        post("/schools") {
            val name = call.receiveCheckedForm().text("name")
            call.respondFormResult(
                schools.create.execute(call.actor, name),
                backLink = "/schools",
                onInvalid = { errors ->
                    call.respondResult(schools.list.execute(call.actor)) {
                        call.respondSchoolList(it, errors, name, HttpStatusCode.UnprocessableEntity)
                    }
                },
            ) { call.seeOther(schoolPath(it)) }
        }

        get("/schools/{schoolId}") {
            val schoolId = call.schoolIdParameter() ?: return@get
            call.respondResult(schools.dashboard.execute(call.actor, schoolId)) { call.respondSchoolDashboard(it) }
        }

        post("/schools/{schoolId}") {
            val schoolId = call.schoolIdParameter() ?: return@post
            val form = call.receiveCheckedForm()
            val name = form.text("name")
            call.respondFormResult(
                schools.rename.execute(call.actor, schoolId, name, form.version()),
                backLink = "/schools/${schoolId.value}",
                onInvalid = { errors ->
                    call.respondResult(schools.dashboard.execute(call.actor, schoolId)) {
                        call.respondSchoolDashboard(it, errors, name, HttpStatusCode.UnprocessableEntity)
                    }
                },
            ) { call.seeOther(schoolPath(it)) }
        }

        post("/schools/{schoolId}/delete") {
            val schoolId = call.schoolIdParameter() ?: return@post
            val form = call.receiveCheckedForm()
            call.respondResult(schools.delete.execute(call.actor, schoolId, form.version()), "/schools/${schoolId.value}") {
                call.seeOther("/schools")
            }
        }
    }
}
