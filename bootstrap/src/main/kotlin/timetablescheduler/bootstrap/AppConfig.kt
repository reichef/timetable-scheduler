package timetablescheduler.bootstrap

import timetablescheduler.adapters.persistence.DatabaseSettings
import timetablescheduler.application.account.InitialSetup

/** Raised for missing or malformed configuration; the message names the variable, never its value. */
class MissingConfiguration(
    message: String,
) : RuntimeException(message)

/**
 * All configuration comes from `TTS_*` environment variables (constitution: secrets outside source control).
 * The initial setup variables are only needed for the very first start with an empty database.
 */
class AppConfig(
    val database: DatabaseSettings,
    val sessionSignKey: ByteArray,
    val devMode: Boolean,
    val initialSetup: InitialSetup?,
) {
    override fun toString(): String = "AppConfig(devMode=$devMode, initialSetupPresent=${initialSetup != null})"

    companion object {
        fun fromEnvironment(environment: Map<String, String>): AppConfig {
            fun required(name: String): String =
                environment[name]?.takeIf { it.isNotBlank() } ?: throw MissingConfiguration("Missing environment variable $name")

            return AppConfig(
                database =
                    DatabaseSettings(
                        jdbcUrl = required("TTS_DB_URL"),
                        username = required("TTS_DB_USER"),
                        password = required("TTS_DB_PASSWORD"),
                    ),
                sessionSignKey = parseHexKey(required("TTS_SESSION_SIGN_KEY")),
                devMode = environment["TTS_DEV_MODE"] == "true",
                initialSetup = readInitialSetup(environment),
            )
        }

        private fun readInitialSetup(environment: Map<String, String>): InitialSetup? {
            val schoolName = environment["TTS_INITIAL_SCHOOL_NAME"]?.takeIf { it.isNotBlank() }
            val username = environment["TTS_INITIAL_PLANNER_USER"]?.takeIf { it.isNotBlank() }
            val password = environment["TTS_INITIAL_PLANNER_PASSWORD"]?.takeIf { it.isNotBlank() }
            return if (schoolName != null && username != null && password != null) {
                InitialSetup(schoolName, username, password)
            } else {
                null
            }
        }

        private fun parseHexKey(hexKey: String): ByteArray {
            val isHex = hexKey.length % 2 == 0 && hexKey.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
            if (!isHex) {
                throw MissingConfiguration("Environment variable TTS_SESSION_SIGN_KEY must be hexadecimal")
            }
            return hexKey.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }
    }
}
