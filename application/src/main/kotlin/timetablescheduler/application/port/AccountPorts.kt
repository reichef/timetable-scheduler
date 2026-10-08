package timetablescheduler.application.port

import timetablescheduler.domain.account.UserAccount
import timetablescheduler.domain.common.AccountId
import timetablescheduler.domain.common.SchoolId

interface UserAccountRepository {
    fun insert(account: UserAccount)

    /** Saves [account] if its stored version still equals `account.version`; returns false otherwise. */
    fun update(account: UserAccount): Boolean

    fun findById(accountId: AccountId): UserAccount?

    fun findByUsernameIgnoreCase(username: String): UserAccount?

    fun existsAny(): Boolean

    fun findActivePlannerIdsOfSchool(schoolId: SchoolId): Set<AccountId>

    /** Accounts for which [schoolId] is the only assigned school; they block deleting it (FR-031a). */
    fun findAccountsWithOnlySchool(schoolId: SchoolId): List<UserAccount>

    fun removeSchoolAssignments(schoolId: SchoolId)
}

/** Password credentials, kept apart from accounts so single sign-on can be added beside them (FR-035). */
interface PasswordCredentialRepository {
    fun save(accountId: AccountId, passwordHash: String)

    fun findPasswordHash(accountId: AccountId): String?
}

interface PasswordHasher {
    fun hash(plainPassword: String): String

    fun matches(plainPassword: String, passwordHash: String): Boolean
}

/** Proves a user's identity; returns the account or null. A future SSO adapter implements it as well. */
interface CredentialAuthenticator {
    fun authenticate(username: String, plainPassword: String): AccountId?
}
