package timetablescheduler.adapters.web.i18n

import timetablescheduler.domain.account.InterfaceLanguage.ENGLISH
import timetablescheduler.domain.account.InterfaceLanguage.GERMAN
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LanguageResolutionTest {
    @Test
    fun `logged-in user sees the session language`() {
        assertEquals(
            ENGLISH,
            resolveInterfaceLanguage(loggedIn = true, sessionLanguage = ENGLISH, cookieCode = "de", acceptLanguage = "de"),
        )
    }

    @Test
    fun `logged-in user without own language sees the browser language, never the remembered cookie`() {
        assertEquals(GERMAN, resolveInterfaceLanguage(loggedIn = true, sessionLanguage = null, cookieCode = "en", acceptLanguage = "de-DE"))
        assertEquals(GERMAN, resolveInterfaceLanguage(loggedIn = true, sessionLanguage = null, cookieCode = "en", acceptLanguage = null))
    }

    @Test
    fun `before login the remembered cookie wins over the browser language`() {
        assertEquals(ENGLISH, resolveInterfaceLanguage(loggedIn = false, sessionLanguage = null, cookieCode = "en", acceptLanguage = "de"))
        assertEquals(GERMAN, resolveInterfaceLanguage(loggedIn = false, sessionLanguage = null, cookieCode = "xx", acceptLanguage = "fr"))
    }

    @Test
    fun `browser preference picks the best of German and English by quality`() {
        assertEquals(ENGLISH, preferredBrowserLanguage("fr, en;q=0.8, de;q=0.5"))
        assertEquals(GERMAN, preferredBrowserLanguage("de-AT, en;q=0.9"))
        assertEquals(ENGLISH, preferredBrowserLanguage("EN-gb"))
        assertNull(preferredBrowserLanguage("fr, it;q=0.5"))
        assertNull(preferredBrowserLanguage(""))
        assertNull(preferredBrowserLanguage(null))
    }

    @Test
    fun `return address must be a local path`() {
        assertEquals("/schools/x?y=1", safeReturnTo("/schools/x?y=1"))
        assertEquals("/", safeReturnTo("//evil.org/path"))
        assertEquals("/", safeReturnTo("https://example.org"))
        assertEquals("/", safeReturnTo("javascript:alert(1)"))
        assertEquals("/", safeReturnTo("/\\evil.org"))
        assertEquals("/", safeReturnTo(""))
        assertEquals("/", safeReturnTo(null))
    }
}
