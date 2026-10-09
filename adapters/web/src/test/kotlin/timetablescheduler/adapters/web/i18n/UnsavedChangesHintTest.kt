package timetablescheduler.adapters.web.i18n

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import timetablescheduler.adapters.web.TestSystem
import timetablescheduler.adapters.web.login
import timetablescheduler.adapters.web.startWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** FR-038: pages carry the localized warning for the language-switch script; the script stays tiny (constitution v1.2.0). */
class UnsavedChangesHintTest {

    @Test
    fun `form pages render the warning in the current language and load the script`() = testApplication {
        val system = TestSystem().installed()
        val roomsPath = "/schools/${system.firstSchoolId().value}/rooms"
        val client = startWith(system)
        client.login()

        val german = client.get(roomsPath).bodyAsText()
        client.switchLanguage("en", roomsPath)
        val english = client.get(roomsPath).bodyAsText()

        assertTrue("""data-unsaved-warning="${GermanTexts.languageSwitch.unsavedWarning}"""" in german)
        assertTrue("""data-unsaved-warning="${EnglishTexts.languageSwitch.unsavedWarning}"""" in english)
        listOf(german, english).forEach { assertTrue("""<script src="/static/language-switch.js"""" in it) }
    }

    @Test
    fun `the script is served by the application and only asks for confirmation`() = testApplication {
        val client = startWith(TestSystem().installed())

        val response = client.get("/static/language-switch.js")
        val script = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue("window.confirm(" in script)
        assertTrue(script.lines().size <= 30, "script has ${script.lines().size} lines")
    }
}
