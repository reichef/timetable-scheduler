package timetablescheduler.adapters.web.auth

import io.ktor.server.sessions.SessionSerializer
import timetablescheduler.domain.common.AccountId
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

/** Server-side session: only a signed session id travels in the cookie. */
data class UserSession(val accountId: AccountId, val csrfToken: String) {
    companion object {
        private val random = SecureRandom()

        fun startFor(accountId: AccountId): UserSession {
            val tokenBytes = ByteArray(32).also(random::nextBytes)
            return UserSession(accountId, Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes))
        }
    }
}

/** Plain text format `accountId|csrfToken`; avoids a serialization plugin for two fields. */
object UserSessionSerializer : SessionSerializer<UserSession> {
    override fun serialize(session: UserSession): String = "${session.accountId.value}|${session.csrfToken}"

    override fun deserialize(text: String): UserSession {
        val (accountId, csrfToken) = text.split('|', limit = 2)
        return UserSession(AccountId(UUID.fromString(accountId)), csrfToken)
    }
}
