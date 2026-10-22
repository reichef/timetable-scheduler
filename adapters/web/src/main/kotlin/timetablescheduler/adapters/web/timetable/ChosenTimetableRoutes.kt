package timetablescheduler.adapters.web.timetable

import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import timetablescheduler.adapters.web.WebDependencies
import timetablescheduler.adapters.web.auth.SESSION_AUTH
import timetablescheduler.adapters.web.auth.actor
import timetablescheduler.adapters.web.layout.respondResult
import timetablescheduler.adapters.web.school.schoolIdParameter

/** The school's chosen timetable (002 US3, FR-021, FR-023). */
fun Route.chosenTimetableRoutes(dependencies: WebDependencies) {
    val scheduling = dependencies.useCases.scheduling

    authenticate(SESSION_AUTH) {
        get("/schools/{schoolId}/timetable") {
            val schoolId = call.schoolIdParameter() ?: return@get
            val view = TimetableView.fromParameter(call.parameters["view"])
            call.respondResult(scheduling.getChosen.execute(call.actor, schoolId)) { details ->
                call.respondChosenTimetable(details, view, selectedEntity(view, call.parameters["id"], details.names))
            }
        }
    }
}
