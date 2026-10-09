package timetablescheduler.adapters.web.i18n

import io.ktor.http.Cookie
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import timetablescheduler.adapters.web.WebDependencies
import timetablescheduler.adapters.web.WebSettings
import timetablescheduler.adapters.web.auth.UserSession
import timetablescheduler.adapters.web.layout.receiveCheckedForm
import timetablescheduler.adapters.web.layout.respondResult
import timetablescheduler.adapters.web.layout.seeOther
import timetablescheduler.domain.account.InterfaceLanguage

private const val ONE_YEAR_IN_SECONDS = 365 * 24 * 60 * 60

/** After a switch on the login page the login form offers the choice for saving (FR-042). */
const val LOGIN_AFTER_LANGUAGE_CHOICE = "/login?chosen=1"

private fun ApplicationCall.rememberInBrowser(language: InterfaceLanguage, settings: WebSettings) {
    response.cookies.append(
        Cookie(
            name = LANGUAGE_COOKIE,
            value = language.code,
            maxAge = ONE_YEAR_IN_SECONDS,
            path = "/",
            secure = !settings.devMode,
            httpOnly = true,
            extensions = mapOf("SameSite" to "Strict"),
        ),
    )
}

/**
 * The language switch (FR-038 – FR-040, R19). Logged in: CSRF-checked, saved with the account and in the session,
 * back to the current page. Not logged in: there is no session to bind a CSRF token to, so only the browser cookie
 * is set — at most the login page's display language changes — and the login form then offers the choice.
 */
fun Route.languageRoutes(dependencies: WebDependencies, settings: WebSettings) {
    val changeOwnLanguage = dependencies.useCases.accounts.changeOwnLanguage

    post("/language") {
        val session = call.sessions.get<UserSession>()
        val form = if (session != null) call.receiveCheckedForm() else call.receiveParameters()
        val returnTo = safeReturnTo(form["returnTo"])
        val language = InterfaceLanguage.fromCode(form["language"]) ?: return@post call.seeOther(returnTo)
        call.rememberInBrowser(language, settings)
        if (session == null) return@post call.seeOther(LOGIN_AFTER_LANGUAGE_CHOICE)
        call.respondResult(changeOwnLanguage.execute(session.accountId, language)) {
            call.sessions.set(session.copy(language = language))
            call.seeOther(returnTo)
        }
    }
}
