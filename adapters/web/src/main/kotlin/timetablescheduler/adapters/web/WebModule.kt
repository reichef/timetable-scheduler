package timetablescheduler.adapters.web

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.session
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.routing
import io.ktor.server.sessions.SessionStorageMemory
import io.ktor.server.sessions.SessionTransportTransformerMessageAuthentication
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import kotlinx.html.p
import org.slf4j.LoggerFactory
import timetablescheduler.adapters.web.auth.SESSION_AUTH
import timetablescheduler.adapters.web.auth.UserSession
import timetablescheduler.adapters.web.auth.UserSessionSerializer
import timetablescheduler.adapters.web.auth.authRoutes
import timetablescheduler.adapters.web.layout.CsrfViolation
import timetablescheduler.adapters.web.layout.Messages
import timetablescheduler.adapters.web.layout.respondMessage
import timetablescheduler.adapters.web.layout.respondPage
import timetablescheduler.adapters.web.room.roomRoutes
import timetablescheduler.adapters.web.school.schoolRoutes
import timetablescheduler.adapters.web.timegrid.timeGridRoutes
import java.util.UUID

private val technicalErrorLog = LoggerFactory.getLogger("timetablescheduler.web.TechnicalError")

fun Application.webModule(dependencies: WebDependencies, settings: WebSettings) {
    install(CallId) {
        generate { UUID.randomUUID().toString() }
    }
    installSessions(settings)
    install(Authentication) {
        session<UserSession>(SESSION_AUTH) {
            validate { session -> session }
            challenge { call.respondRedirect("/login") }
        }
    }
    installErrorPages()

    routing {
        staticResources("/static", "static")
        authRoutes(dependencies)
        schoolRoutes(dependencies)
        roomRoutes(dependencies)
        timeGridRoutes(dependencies)
    }
}

private fun Application.installSessions(settings: WebSettings) {
    install(Sessions) {
        cookie<UserSession>("TTS_SESSION", SessionStorageMemory()) {
            serializer = UserSessionSerializer
            cookie.path = "/"
            cookie.httpOnly = true
            cookie.secure = !settings.devMode
            cookie.extensions["SameSite"] = "Strict"
            transform(SessionTransportTransformerMessageAuthentication(settings.sessionSignKey))
        }
    }
}

/**
 * Technical and unexpected errors (constitution III, R10): the user sees a generic text plus correlation id,
 * the log gets only the exception class and the correlation id — never the message, which may contain data.
 */
private fun Application.installErrorPages() {
    install(StatusPages) {
        exception<CsrfViolation> { call, _ ->
            call.respondMessage(HttpStatusCode.Forbidden, Messages.ACCESS_DENIED)
        }
        exception<Throwable> { call, cause ->
            val correlationId = call.callId ?: "unbekannt"
            technicalErrorLog.error("Unexpected error {} (correlationId={})", cause.javaClass.name, correlationId)
            call.respondPage("Fehler", HttpStatusCode.InternalServerError) {
                p { +Messages.UNEXPECTED_ERROR }
                p { +"${Messages.CORRELATION_ID}: $correlationId" }
            }
        }
    }
}
