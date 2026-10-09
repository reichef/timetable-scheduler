package timetablescheduler.adapters.web

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.slf4j.LoggerFactory
import timetablescheduler.adapters.web.i18n.EnglishTexts
import timetablescheduler.adapters.web.i18n.GermanTexts
import timetablescheduler.adapters.web.i18n.UiTexts
import timetablescheduler.adapters.web.i18n.switchLanguage
import timetablescheduler.application.error.DependantType
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Constitution III, FR-029, R10: confidential data may appear in business messages shown to the planner,
 * but never in log output, and technical errors show only a generic text and a correlation id.
 */
class LogConfidentialityTest {

    private val rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
    private val capturedLog = ListAppender<ILoggingEvent>()
    private var previousLevel: Level? = null

    /** Ktor repeats exception messages at TRACE; bootstrap's logback.xml pins this logger to WARN (checked there). */
    private val statusPagesLogger = LoggerFactory.getLogger("io.ktor.server.plugins.statuspages") as Logger

    private val confidentialValues = listOf("Anna Schmidt", "SCHA", "Mathematik", TestSystem.PASSWORD, "geheimes-passwort")

    @BeforeTest
    fun captureLog() {
        previousLevel = rootLogger.level
        rootLogger.level = Level.ALL
        statusPagesLogger.level = Level.WARN
        capturedLog.start()
        rootLogger.addAppender(capturedLog)
    }

    @AfterTest
    fun releaseLog() {
        rootLogger.detachAppender(capturedLog)
        rootLogger.level = previousLevel
        statusPagesLogger.level = null
    }

    /** Everything a log line could reveal: the formatted message, its arguments and any attached exception. */
    private fun ILoggingEvent.fullText(): String =
        listOfNotNull(formattedMessage, argumentArray?.joinToString(), throwableProxy?.message, throwableProxy?.className).joinToString(" | ")

    @Test
    fun `business errors name the teacher on the page but logs never contain names, form values or passwords`() =
        businessAndTechnicalErrorsKeepLogsClean(GermanTexts)

    @Test
    fun `the same holds with the English interface`() = businessAndTechnicalErrorsKeepLogsClean(EnglishTexts)

    /** Runs the confidential steps in the language of [texts] (FR-048) and checks pages and log output. */
    private fun businessAndTechnicalErrorsKeepLogsClean(texts: UiTexts) = testApplication {
        val system = TestSystem().installed()
        val schoolId = system.firstSchoolId()
        val schoolPath = "/schools/${schoolId.value}"
        application {
            routing { get("/explode") { error("Anna Schmidt kann nicht") } }
        }
        val client = startWith(system)
        client.login()
        client.switchLanguage(texts.language.code)

        client.submitChecked("$schoolPath/subjects", "name" to "Mathematik")
        val mathe = system.world.subjects.findByNameIgnoreCase(schoolId, "Mathematik")!!
        client.submitChecked("$schoolPath/teachers", "name" to "Anna Schmidt", "shortCode" to "SCHA", "weeklyLoad" to "0")
        val anna = system.world.teachers.findByShortCode("SCHA")!!
        client.submitChecked("$schoolPath/teachers/${anna.id.value}/qualifications", "subjectId" to "${mathe.id.value}", "version" to "0")
        val invalid = client.submitChecked("$schoolPath/teachers", "name" to "Anna Schmidt", "shortCode" to "SCHA", "weeklyLoad" to "0")
        val blocked = client.submitChecked("$schoolPath/subjects/${mathe.id.value}/delete", "version" to "0")
        client.submitChecked("/account/password", "currentPassword" to TestSystem.PASSWORD, "newPassword" to "geheimes-passwort")
        val technical = client.get("/explode")
        val technicalPage = technical.bodyAsText()

        assertEquals(HttpStatusCode.UnprocessableEntity, invalid.status)
        assertEquals(HttpStatusCode.Conflict, blocked.status)
        val teacherType = texts.errors.describe(DependantType.TEACHER)
        assertTrue("$teacherType: Anna Schmidt (SCHA)" in blocked.bodyAsText(), "business message names the dependant for the planner")
        assertEquals(HttpStatusCode.InternalServerError, technical.status)
        assertFalse("Anna Schmidt" in technicalPage)
        assertTrue(texts.errors.unexpectedError in technicalPage)
        assertTrue(texts.errors.correlationId in technicalPage)

        val logLines = capturedLog.list.map { it.fullText() }
        assertTrue(logLines.any { "IllegalStateException" in it }, "technical error is logged by class")
        confidentialValues.forEach { value ->
            assertEquals(emptyList(), logLines.filter { value in it }, "log must not contain \"$value\"")
        }
    }
}
