package timetablescheduler.adapters.web.layout

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.html.respondHtml
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.button
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.header
import kotlinx.html.lang
import kotlinx.html.link
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.nav
import kotlinx.html.title
import timetablescheduler.adapters.web.auth.UserSession

/** Renders a full page with navigation (when logged in) around [content]. */
suspend fun ApplicationCall.respondPage(
    pageTitle: String,
    status: HttpStatusCode = HttpStatusCode.OK,
    content: FlowContent.(UserSession?) -> Unit,
) {
    val session = sessions.get<UserSession>()
    respondHtml(status) {
        lang = "de"
        head {
            meta(charset = "utf-8")
            meta(name = "viewport", content = "width=device-width, initial-scale=1")
            title("$pageTitle – ${Messages.APP_TITLE}")
            link(rel = "stylesheet", href = "/static/app.css")
        }
        body {
            header {
                a(href = "/home", classes = "brand") { +Messages.APP_TITLE }
                if (session != null) navigation(session)
            }
            main {
                h1 { +pageTitle }
                content(session)
            }
        }
    }
}

private fun FlowContent.navigation(session: UserSession) {
    nav {
        a(href = "/schools") { +"Schule" }
        a(href = "/accounts") { +"Konten" }
        a(href = "/account/password") { +"Passwort ändern" }
        form(action = "/logout", method = FormMethod.post, classes = "inline") {
            csrfField(session)
            button(classes = "link") { +"Abmelden" }
        }
    }
}
