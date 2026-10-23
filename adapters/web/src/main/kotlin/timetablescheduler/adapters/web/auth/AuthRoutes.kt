package timetablescheduler.adapters.web.auth

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receiveParameters
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.input
import kotlinx.html.p
import timetablescheduler.adapters.web.WebDependencies
import timetablescheduler.adapters.web.i18n.interfaceLanguage
import timetablescheduler.adapters.web.i18n.texts
import timetablescheduler.adapters.web.layout.receiveCheckedForm
import timetablescheduler.adapters.web.layout.respondPage
import timetablescheduler.adapters.web.layout.seeOther
import timetablescheduler.adapters.web.layout.textField
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.domain.account.InterfaceLanguage
import timetablescheduler.domain.account.Role

const val SESSION_AUTH = "session"

/** Hidden login-form field; only present after an active language choice on the login page (FR-042). */
private const val CHOSEN_LANGUAGE_FIELD = "chosenLanguage"

fun Route.authRoutes(dependencies: WebDependencies) {
    get("/") { call.seeOther("/login") }

    get("/login") {
        val chosenLanguage = if (call.request.queryParameters["chosen"] != null) call.interfaceLanguage() else null
        call.respondLoginPage(HttpStatusCode.OK, loginFailed = false, username = null, chosenLanguage = chosenLanguage)
    }

    // The login form itself carries no CSRF token: there is no session yet to bind it to.
    post("/login") {
        val form = call.receiveParameters()
        val username = form["username"].orEmpty()
        val chosenLanguage = InterfaceLanguage.fromCode(form[CHOSEN_LANGUAGE_FIELD])
        when (val result = dependencies.useCases.login.execute(username, form["password"].orEmpty(), chosenLanguage)) {
            is UseCaseResult.Success -> {
                call.sessions.set(UserSession.startFor(result.value.accountId, result.value.language))
                call.seeOther(if (result.value.role == Role.PLANNER) "/schools" else "/home")
            }

            is UseCaseResult.Failure -> {
                call.respondLoginPage(HttpStatusCode.Unauthorized, loginFailed = true, username = username, chosenLanguage = chosenLanguage)
            }
        }
    }

    authenticate(SESSION_AUTH) {
        get("/home") {
            val texts = call.texts
            call.respondPage(texts.common.start) {
                p { +texts.auth.teacherHome }
                p { a(href = "/schools") { +texts.auth.plannerLinkToSchool } }
            }
        }

        post("/logout") {
            call.receiveCheckedForm()
            call.sessions.clear<UserSession>()
            call.seeOther("/login")
        }
    }
}

private suspend fun ApplicationCall.respondLoginPage(
    status: HttpStatusCode,
    loginFailed: Boolean,
    username: String?,
    chosenLanguage: InterfaceLanguage?,
) {
    val texts = texts
    respondPage(texts.auth.loginTitle, status) {
        if (loginFailed) {
            div(classes = "errors") { p { +texts.auth.loginFailed } }
        }
        form(action = "/login", method = FormMethod.post) {
            if (chosenLanguage != null) {
                input(type = InputType.hidden, name = CHOSEN_LANGUAGE_FIELD) { value = chosenLanguage.code }
            }
            textField("username", texts.auth.username, username)
            textField("password", texts.auth.password, null, InputType.password)
            button { +texts.auth.loginTitle }
        }
    }
}
