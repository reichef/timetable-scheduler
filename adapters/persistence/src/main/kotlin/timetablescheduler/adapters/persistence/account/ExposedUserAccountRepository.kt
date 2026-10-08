package timetablescheduler.adapters.persistence.account

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import timetablescheduler.application.port.UserAccountRepository
import timetablescheduler.domain.account.Role
import timetablescheduler.domain.account.UserAccount
import timetablescheduler.domain.common.AccountId
import timetablescheduler.domain.common.SchoolId
import java.util.UUID

class ExposedUserAccountRepository : UserAccountRepository {

    override fun insert(account: UserAccount) {
        UserAccountTable.insert {
            it[id] = account.id.value
            it[username] = account.username
            it[role] = account.role.name
            it[active] = account.active
            it[version] = account.version
        }
        insertSchoolAssignments(account)
    }

    override fun update(account: UserAccount): Boolean {
        val updatedRows = UserAccountTable.update({
            (UserAccountTable.id eq account.id.value) and (UserAccountTable.version eq account.version)
        }) {
            it[role] = account.role.name
            it[active] = account.active
            it[version] = account.version + 1
        }
        if (updatedRows != 1) return false
        AccountSchoolTable.deleteWhere { AccountSchoolTable.accountId eq account.id.value }
        insertSchoolAssignments(account)
        return true
    }

    override fun findById(accountId: AccountId): UserAccount? =
        UserAccountTable.selectAll().where { UserAccountTable.id eq accountId.value }
            .singleOrNull()?.toAccount()

    override fun findByUsernameIgnoreCase(username: String): UserAccount? =
        UserAccountTable.selectAll().where { UserAccountTable.username.lowerCase() eq username.trim().lowercase() }
            .singleOrNull()?.toAccount()

    override fun existsAny(): Boolean = !UserAccountTable.selectAll().limit(1).empty()

    override fun findActivePlannerIdsOfSchool(schoolId: SchoolId): Set<AccountId> =
        (UserAccountTable innerJoin AccountSchoolTable)
            .select(UserAccountTable.id)
            .where {
                (AccountSchoolTable.schoolId eq schoolId.value) and
                    (UserAccountTable.active eq true) and
                    (UserAccountTable.role eq Role.PLANNER.name)
            }
            .map { AccountId(it[UserAccountTable.id]) }
            .toSet()

    override fun findAccountsWithOnlySchool(schoolId: SchoolId): List<UserAccount> {
        val candidateIds = AccountSchoolTable.select(AccountSchoolTable.accountId)
            .where { AccountSchoolTable.schoolId eq schoolId.value }
            .map { it[AccountSchoolTable.accountId] }
        if (candidateIds.isEmpty()) return emptyList()
        return UserAccountTable.selectAll().where { UserAccountTable.id inList candidateIds }
            .map { it.toAccount() }
            .filter { it.schoolIds == setOf(schoolId) }
    }

    override fun removeSchoolAssignments(schoolId: SchoolId) {
        AccountSchoolTable.deleteWhere { AccountSchoolTable.schoolId eq schoolId.value }
    }

    private fun insertSchoolAssignments(account: UserAccount) {
        AccountSchoolTable.batchInsert(account.schoolIds) { schoolId ->
            this[AccountSchoolTable.accountId] = account.id.value
            this[AccountSchoolTable.schoolId] = schoolId.value
        }
    }

    private fun schoolIdsOf(accountIds: Collection<UUID>): Map<UUID, Set<SchoolId>> =
        AccountSchoolTable.selectAll().where { AccountSchoolTable.accountId inList accountIds }
            .groupBy({ it[AccountSchoolTable.accountId] }, { SchoolId(it[AccountSchoolTable.schoolId]) })
            .mapValues { it.value.toSet() }

    private fun ResultRow.toAccount(): UserAccount {
        val accountId = this[UserAccountTable.id]
        return UserAccount(
            id = AccountId(accountId),
            username = this[UserAccountTable.username],
            role = Role.valueOf(this[UserAccountTable.role]),
            active = this[UserAccountTable.active],
            schoolIds = schoolIdsOf(listOf(accountId))[accountId].orEmpty(),
            version = this[UserAccountTable.version],
        )
    }
}
