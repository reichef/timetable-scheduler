package timetablescheduler.application.fakes

import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.port.PasswordCredentialRepository
import timetablescheduler.application.port.PasswordHasher
import timetablescheduler.application.port.SchoolRepository
import timetablescheduler.application.port.TransactionRunner
import timetablescheduler.application.port.UserAccountRepository
import timetablescheduler.domain.account.InterfaceLanguage
import timetablescheduler.domain.account.UserAccount
import timetablescheduler.domain.common.AccountId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.school.School
import timetablescheduler.domain.school.TimeGrid

/** Runs the block directly; in-memory fakes need no rollback because use cases validate before writing. */
class InMemoryTransactionRunner : TransactionRunner {
    override fun <T> inTransaction(block: () -> UseCaseResult<T>): UseCaseResult<T> = block()
}

/** Shared optimistic-locking behaviour of the in-memory repositories. */
abstract class VersionedInMemoryStore<K, V : Any>(
    private val keyOf: (V) -> K,
    private val versionOf: (V) -> Int,
    private val withVersion: (V, Int) -> V,
) {
    protected val entries = linkedMapOf<K, V>()

    open fun insert(entry: V) {
        entries[keyOf(entry)] = entry
    }

    open fun update(entry: V): Boolean {
        val stored = entries[keyOf(entry)] ?: return false
        if (versionOf(stored) != versionOf(entry)) {
            return false
        }
        entries[keyOf(entry)] = withVersion(entry, versionOf(entry) + 1)
        return true
    }

    fun count(): Int = entries.size

    fun all(): List<V> = entries.values.toList()
}

class InMemorySchoolRepository :
    VersionedInMemoryStore<SchoolId, School>(
        keyOf = { it.id },
        versionOf = { it.version },
        withVersion = { school, version -> school.copy(version = version) },
    ),
    SchoolRepository {
    override fun insert(school: School) = super.insert(school)

    override fun update(school: School): Boolean = super.update(school)

    override fun findById(schoolId: SchoolId): School? = entries[schoolId]

    override fun findByIds(schoolIds: Set<SchoolId>): List<School> = schoolIds.mapNotNull { entries[it] }

    override fun findByNameIgnoreCase(name: String): School? = entries.values.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }

    private val timeGrids = mutableMapOf<SchoolId, TimeGrid>()

    override fun findAllIds(): List<SchoolId> = entries.keys.toList()

    override fun findTimeGrid(schoolId: SchoolId): TimeGrid = timeGrids[schoolId] ?: TimeGrid.empty()

    override fun saveTimeGrid(
        schoolId: SchoolId,
        timeGrid: TimeGrid,
    ) {
        timeGrids[schoolId] = timeGrid
    }
}

class InMemoryUserAccountRepository :
    VersionedInMemoryStore<AccountId, UserAccount>(
        keyOf = { it.id },
        versionOf = { it.version },
        withVersion = { account, version -> account.copy(version = version) },
    ),
    UserAccountRepository {
    override fun insert(account: UserAccount) = super.insert(account)

    /** Like the database: `update` keeps the stored language, `updatePreferredLanguage` keeps the version. */
    override fun update(account: UserAccount): Boolean =
        super.update(account.copy(preferredLanguage = entries[account.id]?.preferredLanguage))

    override fun updatePreferredLanguage(
        accountId: AccountId,
        language: InterfaceLanguage,
    ) {
        entries[accountId]?.let { entries[accountId] = it.withPreferredLanguage(language) }
    }

    override fun findById(accountId: AccountId): UserAccount? = entries[accountId]

    override fun findByUsernameIgnoreCase(username: String): UserAccount? =
        entries.values.firstOrNull { it.username.equals(username.trim(), ignoreCase = true) }

    override fun existsAny(): Boolean = entries.isNotEmpty()

    override fun findActivePlannerIdsOfSchool(schoolId: SchoolId): Set<AccountId> =
        entries.values
            .filter { it.isActivePlannerOf(schoolId) }
            .map { it.id }
            .toSet()

    override fun findBySchool(schoolId: SchoolId): List<UserAccount> = entries.values.filter { schoolId in it.schoolIds }
}

class InMemoryPasswordCredentialRepository : PasswordCredentialRepository {
    private val passwordHashes = mutableMapOf<AccountId, String>()

    override fun save(
        accountId: AccountId,
        passwordHash: String,
    ) {
        passwordHashes[accountId] = passwordHash
    }

    override fun findPasswordHash(accountId: AccountId): String? = passwordHashes[accountId]
}

/** Not secure — for tests only, keeps tests fast and deterministic. */
class PlainTextTestPasswordHasher : PasswordHasher {
    override fun hash(plainPassword: String): String = "test-hash:$plainPassword"

    override fun matches(
        plainPassword: String,
        passwordHash: String,
    ): Boolean = passwordHash == hash(plainPassword)
}
