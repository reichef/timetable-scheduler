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
import kotlinx.html.p
import timetablescheduler.adapters.web.WebDependencies
import timetablescheduler.adapters.web.layout.Messages
import timetablescheduler.adapters.web.layout.receiveCheckedForm
import timetablescheduler.adapters.web.layout.respondPage
import timetablescheduler.adapters.web.layout.seeOther
import timetablescheduler.adapters.web.layout.textField
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.domain.account.Role

const val SESSION_AUTH = "session"

fun Route.authRoutes(dependencies: WebDependencies) {
    get("/") { call.seeOther("/login") }

    get("/login") { call.respondLoginPage(HttpStatusCode.OK, loginFailed = false, username = null) }

    // The login form itself carries no CSRF token: there is no session yet to bind it to.
    post("/login") {
        val form = call.receiveParameters()
        val username = form["username"].orEmpty()
        when (val result = dependencies.useCases.login.execute(username, form["password"].orEmpty())) {
            is UseCaseResult.Success -> {
                call.sessions.set(UserSession.startFor(result.value.accountId))
                call.seeOther(if (result.value.role == Role.PLANNER) "/schools" else "/home")
            }
            is UseCaseResult.Failure -> call.respondLoginPage(HttpStatusCode.Unauthorized, loginFailed = true, username = username)
        }
    }

    authenticate(SESSION_AUTH) {
        get("/home") {
            call.respondPage("Start") {
                p { +Messages.TEACHER_HOME }
                p { a(href = "/schools") { +"Planer: zur Schule" } }
            }
        }

        post("/logout") {
            call.receiveCheckedForm()
            call.sessions.clear<UserSession>()
            call.seeOther("/login")
        }
    }
}

private suspend fun ApplicationCall.respondLoginPage(status: HttpStatusCode, loginFailed: Boolean, username: String?) {
    respondPage(Messages.LOGIN_TITLE, status) {
        if (loginFailed) div(classes = "errors") { p { +Messages.LOGIN_FAILED } }
        form(action = "/login", method = FormMethod.post) {
            textField("username", "Benutzername", username)
            textField("password", "Passwort", null, InputType.password)
            button { +Messages.LOGIN_TITLE }
        }
    }
}
