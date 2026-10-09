package timetablescheduler.application.account

import timetablescheduler.application.port.CredentialAuthenticator
import timetablescheduler.application.port.PasswordCredentialRepository
import timetablescheduler.application.port.PasswordHasher
import timetablescheduler.application.port.UserAccountRepository
import timetablescheduler.domain.common.AccountId

/** Username/password authentication against the stored password credentials (FR-030). */
class PasswordCredentialAuthenticator(
    private val accounts: UserAccountRepository,
    private val credentials: PasswordCredentialRepository,
    private val hasher: PasswordHasher,
) : CredentialAuthenticator {

    private val hashForUnknownUsers: String by lazy { hasher.hash("unknown-user-placeholder") }

    /**
     * Unknown usernames are still checked against a placeholder hash so that the response time does not
     * reveal whether a username exists.
     */
    override fun authenticate(username: String, plainPassword: String): AccountId? {
        val account = accounts.findByUsernameIgnoreCase(username.trim())
        val storedHash = account?.let { credentials.findPasswordHash(it.id) }
        val passwordMatches = hasher.matches(plainPassword, storedHash ?: hashForUnknownUsers)
        return if (account != null && storedHash != null && passwordMatches) account.id else null
    }
}
