package timetablescheduler.adapters.web.auth

import io.ktor.server.sessions.SessionSerializer
import timetablescheduler.domain.account.InterfaceLanguage
import timetablescheduler.domain.common.AccountId
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

/**
 * Server-side session: only a signed session id travels in the cookie. [language] is the account's interface
 * language (or the one chosen at login); null means the browser's preferred language applies (R19).
 */
data class UserSession(val accountId: AccountId, val csrfToken: String, val language: InterfaceLanguage? = null) {
    companion object {
        private val random = SecureRandom()

        fun startFor(accountId: AccountId, language: InterfaceLanguage? = null): UserSession {
            val tokenBytes = ByteArray(32).also(random::nextBytes)
            return UserSession(accountId, Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes), language)
        }
    }
}

/** Plain text format `accountId|csrfToken|language`; avoids a serialization plugin for three fields. */
object UserSessionSerializer : SessionSerializer<UserSession> {
    override fun serialize(session: UserSession): String =
        "${session.accountId.value}|${session.csrfToken}|${session.language?.code.orEmpty()}"

    override fun deserialize(text: String): UserSession {
        val parts = text.split('|', limit = 3)
        return UserSession(AccountId(UUID.fromString(parts[0])), parts[1], InterfaceLanguage.fromCode(parts.getOrNull(2)))
    }
}
