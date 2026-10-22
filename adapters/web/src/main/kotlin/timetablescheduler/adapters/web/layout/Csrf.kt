package timetablescheduler.adapters.web.layout

import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import kotlinx.html.FORM
import kotlinx.html.InputType
import kotlinx.html.input
import timetablescheduler.adapters.web.auth.UserSession
import java.security.MessageDigest

const val CSRF_FIELD = "_csrf"

/** Thrown when a form post lacks the session's CSRF token; answered with 403. */
class CsrfViolation : RuntimeException("CSRF token missing or invalid", null, false, false)

/** Reads a posted form and verifies its CSRF token against the session (constant-time comparison). */
suspend fun ApplicationCall.receiveCheckedForm(): Parameters {
    val form = receiveParameters()
    val expectedToken = sessions.get<UserSession>()?.csrfToken ?: throw CsrfViolation()
    val sentToken = form[CSRF_FIELD] ?: throw CsrfViolation()
    if (!MessageDigest.isEqual(expectedToken.toByteArray(), sentToken.toByteArray())) {
        throw CsrfViolation()
    }
    return form
}

fun FORM.csrfField(session: UserSession?) {
    if (session != null) {
        input(type = InputType.hidden, name = CSRF_FIELD) { value = session.csrfToken }
    }
}
