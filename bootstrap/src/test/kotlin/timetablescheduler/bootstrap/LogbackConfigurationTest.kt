package timetablescheduler.bootstrap

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Constitution III: the production logging configuration never lets confidential exception messages through. */
class LogbackConfigurationTest {

    @Test
    fun `Ktor status pages logger stays at WARN even when the root logger is lowered for debugging`() {
        val root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
        val statusPages = LoggerFactory.getLogger("io.ktor.server.plugins.statuspages.StatusPages") as Logger
        val previousRootLevel = root.level
        try {
            root.level = Level.TRACE

            assertEquals(Level.WARN, statusPages.effectiveLevel)
            assertFalse(statusPages.isDebugEnabled)
        } finally {
            root.level = previousRootLevel
        }
    }
}
