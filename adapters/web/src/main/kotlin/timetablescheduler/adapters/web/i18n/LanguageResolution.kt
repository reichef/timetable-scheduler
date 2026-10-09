package timetablescheduler.adapters.web.i18n

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import timetablescheduler.adapters.web.auth.UserSession
import timetablescheduler.domain.account.InterfaceLanguage
import java.net.URI

/** Remembers the last language chosen in this browser; only decides how the login page is shown (FR-040). */
const val LANGUAGE_COOKIE = "TTS_LANG"

/**
 * R19 / FR-039 – FR-042: logged in → the session's language (the account's, or the one chosen at login), else the
 * browser preference — the remembered cookie never applies after login. Not logged in → cookie, else browser
 * preference. German is the fallback.
 */
fun resolveInterfaceLanguage(
    loggedIn: Boolean,
    sessionLanguage: InterfaceLanguage?,
    cookieCode: String?,
    acceptLanguage: String?,
): InterfaceLanguage {
    val chosen = if (loggedIn) sessionLanguage else InterfaceLanguage.fromCode(cookieCode)
    return chosen ?: preferredBrowserLanguage(acceptLanguage) ?: InterfaceLanguage.GERMAN
}

/**
 * Picks German or English from an `Accept-Language` header by quality value ("fr, en;q=0.8" → English); regional
 * variants count for their language ("de-AT" → German). Ties keep the header order; null if neither is listed.
 */
fun preferredBrowserLanguage(acceptLanguage: String?): InterfaceLanguage? =
    acceptLanguage.orEmpty().split(',')
        .mapNotNull { entry ->
            val parts = entry.split(';').map { it.trim() }
            val language = InterfaceLanguage.fromCode(parts.first().substringBefore('-')) ?: return@mapNotNull null
            val quality = parts.drop(1).firstOrNull { it.startsWith("q=") }?.removePrefix("q=")?.toDoubleOrNull() ?: 1.0
            language to quality
        }
        .filter { (_, quality) -> quality > 0.0 }
        .maxByOrNull { (_, quality) -> quality }
        ?.first

/**
 * Only local paths are accepted as redirect target of the language switch, so it cannot send users to another
 * site (open redirect): must start with a single "/" and contain no backslash or control character.
 */
fun safeReturnTo(raw: String?): String {
    val candidate = raw.orEmpty()
    val isLocalPath = candidate.startsWith("/") && !candidate.startsWith("//") &&
        candidate.none { it == '\\' || it.isISOControl() }
    return if (isLocalPath) candidate else "/"
}

fun ApplicationCall.interfaceLanguage(): InterfaceLanguage {
    val session = sessions.get<UserSession>()
    return resolveInterfaceLanguage(
        loggedIn = session != null,
        sessionLanguage = session?.language,
        cookieCode = request.cookies[LANGUAGE_COOKIE],
        acceptLanguage = request.headers[HttpHeaders.AcceptLanguage],
    )
}

/** The texts of the language this request is shown in. */
val ApplicationCall.texts: UiTexts get() = textsFor(interfaceLanguage())

/**
 * Where the language switch returns to: the current page for GET requests; for a page shown after a form post
 * (e.g. a 422 with errors) the page the form was sent from, taken from the `Referer` path.
 */
fun ApplicationCall.currentPageForLanguageSwitch(): String {
    if (request.httpMethod == HttpMethod.Get) return request.uri
    val referer = request.headers[HttpHeaders.Referrer] ?: return "/"
    val uri = runCatching { URI(referer) }.getOrNull() ?: return "/"
    val path = uri.rawPath ?: return "/"
    return safeReturnTo(if (uri.rawQuery == null) path else "$path?${uri.rawQuery}")
}
