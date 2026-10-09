package timetablescheduler.bootstrap

import timetablescheduler.application.account.InitialSetup
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppConfigTest {

    private val required = mapOf(
        "TTS_DB_URL" to "jdbc:postgresql://localhost:5432/timetable",
        "TTS_DB_USER" to "app",
        "TTS_DB_PASSWORD" to "db-secret",
        "TTS_SESSION_SIGN_KEY" to "00ff10",
    )

    @Test
    fun `reads required settings and treats dev mode as off by default`() {
        val config = AppConfig.fromEnvironment(required)

        assertEquals("jdbc:postgresql://localhost:5432/timetable", config.database.jdbcUrl)
        assertEquals("app", config.database.username)
        assertContentEquals(byteArrayOf(0x00, 0xff.toByte(), 0x10), config.sessionSignKey)
        assertFalse(config.devMode)
    }

    @Test
    fun `dev mode is enabled only by true`() {
        assertTrue(AppConfig.fromEnvironment(required + ("TTS_DEV_MODE" to "true")).devMode)
        assertFalse(AppConfig.fromEnvironment(required + ("TTS_DEV_MODE" to "yes")).devMode)
    }

    @Test
    fun `missing required variable is named without revealing any value`() {
        val failure = assertFailsWith<MissingConfiguration> { AppConfig.fromEnvironment(required - "TTS_DB_PASSWORD") }

        assertEquals("Missing environment variable TTS_DB_PASSWORD", failure.message)
    }

    @Test
    fun `initial setup variables are optional`() {
        assertNull(AppConfig.fromEnvironment(required).initialSetup)
    }

    @Test
    fun `initial setup is read when all three variables are present`() {
        val config = AppConfig.fromEnvironment(
            required + mapOf(
                "TTS_INITIAL_SCHOOL_NAME" to "Gymnasium Nord",
                "TTS_INITIAL_PLANNER_USER" to "admin",
                "TTS_INITIAL_PLANNER_PASSWORD" to "sehr-geheim-1",
            ),
        )

        assertEquals(InitialSetup("Gymnasium Nord", "admin", "sehr-geheim-1"), config.initialSetup)
    }

    @Test
    fun `invalid session key is reported by name`() {
        val failure = assertFailsWith<MissingConfiguration> {
            AppConfig.fromEnvironment(required + ("TTS_SESSION_SIGN_KEY" to "xyz"))
        }
        assertEquals("Environment variable TTS_SESSION_SIGN_KEY must be hexadecimal", failure.message)
    }

    @Test
    fun `configuration string representation hides secrets`() {
        val text = AppConfig.fromEnvironment(required).toString()

        assertFalse("db-secret" in text)
        assertFalse("00ff10" in text)
    }
}
