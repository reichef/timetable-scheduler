package timetablescheduler.adapters.web.readiness

import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import timetablescheduler.adapters.web.WebDependencies
import timetablescheduler.adapters.web.auth.SESSION_AUTH
import timetablescheduler.adapters.web.auth.actor
import timetablescheduler.adapters.web.layout.respondResult
import timetablescheduler.adapters.web.school.schoolIdParameter

/** Readiness overview of the school's data before scheduling (FR-024). */
fun Route.readinessRoutes(dependencies: WebDependencies) {
    val readiness = dependencies.useCases.readiness

    authenticate(SESSION_AUTH) {
        get("/schools/{schoolId}/readiness") {
            val schoolId = call.schoolIdParameter() ?: return@get
            call.respondResult(readiness.execute(call.actor, schoolId)) { call.respondReadiness(it) }
        }
    }
}
