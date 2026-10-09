package timetablescheduler.adapters.web.i18n

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.HttpResponse
import io.ktor.http.parametersOf
import timetablescheduler.adapters.web.submitChecked

/** Presses a language button while logged in (with CSRF token, like the browser). */
suspend fun HttpClient.switchLanguage(code: String, returnTo: String = "/home"): HttpResponse =
    submitChecked("/language", "language" to code, "returnTo" to returnTo)

/** Presses a language button on the login page (no session, no CSRF token). */
suspend fun HttpClient.switchLanguageLoggedOut(code: String, returnTo: String = "/login"): HttpResponse =
    submitForm("/language", parametersOf("language" to listOf(code), "returnTo" to listOf(returnTo)))

/** Logs in with the hidden field the login page renders after an active language choice. */
suspend fun HttpClient.loginWithChosenLanguage(username: String, password: String, chosenLanguage: String): HttpResponse =
    submitForm(
        "/login",
        parametersOf("username" to listOf(username), "password" to listOf(password), "chosenLanguage" to listOf(chosenLanguage)),
    )

fun String.htmlLanguage(): String? = Regex("""<html[^>]*\blang="([a-z]{2})"""").find(this)?.groupValues?.get(1)
