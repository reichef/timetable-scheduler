package timetablescheduler.adapters.web.layout

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.html.respondHtml
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import kotlinx.html.FlowContent
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.button
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.header
import kotlinx.html.input
import kotlinx.html.lang
import kotlinx.html.link
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.nav
import kotlinx.html.script
import kotlinx.html.title
import timetablescheduler.adapters.web.auth.UserSession
import timetablescheduler.adapters.web.i18n.UiTexts
import timetablescheduler.adapters.web.i18n.currentPageForLanguageSwitch
import timetablescheduler.adapters.web.i18n.texts
import timetablescheduler.domain.account.InterfaceLanguage

/** Language names are shown in their own language, so they read the same in every catalog. */
private val ownLanguageNames = mapOf(InterfaceLanguage.GERMAN to "Deutsch", InterfaceLanguage.ENGLISH to "English")

/** Renders a full page in the request's language with navigation (when logged in) and the language switch. */
suspend fun ApplicationCall.respondPage(
    pageTitle: String,
    status: HttpStatusCode = HttpStatusCode.OK,
    refreshSeconds: Int? = null,
    content: FlowContent.(UserSession?) -> Unit,
) {
    val session = sessions.get<UserSession>()
    val texts = texts
    val returnTo = currentPageForLanguageSwitch()
    respondHtml(status) {
        lang = texts.language.code
        head {
            meta(charset = "utf-8")
            meta(name = "viewport", content = "width=device-width, initial-scale=1")
            // Pages of running work reload themselves without any script (002 G6).
            if (refreshSeconds != null) meta { httpEquiv = "refresh"; this.content = "$refreshSeconds" }
            title("$pageTitle – ${texts.common.appTitle}")
            link(rel = "stylesheet", href = "/static/app.css")
            script(src = "/static/language-switch.js") { defer = true }
        }
        body {
            header {
                a(href = "/home", classes = "brand") { +texts.common.appTitle }
                if (session != null) navigation(session, texts)
                languageSwitch(session, texts, returnTo)
            }
            main {
                h1 { +pageTitle }
                content(session)
            }
        }
    }
}

private fun FlowContent.navigation(session: UserSession, texts: UiTexts) {
    nav {
        a(href = "/schools") { +texts.common.navSchool }
        a(href = "/accounts") { +texts.common.navAccounts }
        a(href = "/account/password") { +texts.common.navChangePassword }
        form(action = "/logout", method = FormMethod.post, classes = "inline") {
            csrfField(session)
            button(classes = "link") { +texts.common.logout }
        }
    }
}

/**
 * One button per language (FR-038); the current one is marked. `data-unsaved-warning` is the text the static
 * script shows before unsaved form input would be lost.
 */
private fun FlowContent.languageSwitch(session: UserSession?, texts: UiTexts, returnTo: String) {
    form(action = "/language", method = FormMethod.post, classes = "language-switch") {
        attributes["aria-label"] = texts.languageSwitch.label
        attributes["data-unsaved-warning"] = texts.languageSwitch.unsavedWarning
        csrfField(session)
        input(type = InputType.hidden, name = "returnTo") { value = returnTo }
        InterfaceLanguage.entries.forEach { language ->
            button(name = "language", classes = if (language == texts.language) "current" else null) {
                value = language.code
                if (language == texts.language) attributes["aria-current"] = "true"
                +ownLanguageNames.getValue(language)
            }
        }
    }
}
