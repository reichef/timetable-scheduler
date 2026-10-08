package timetablescheduler.adapters.persistence.account

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import timetablescheduler.application.port.PasswordCredentialRepository
import timetablescheduler.domain.common.AccountId

class ExposedPasswordCredentialRepository : PasswordCredentialRepository {

    override fun save(accountId: AccountId, passwordHash: String) {
        PasswordCredentialTable.upsert {
            it[PasswordCredentialTable.accountId] = accountId.value
            it[PasswordCredentialTable.passwordHash] = passwordHash
        }
    }

    override fun findPasswordHash(accountId: AccountId): String? =
        PasswordCredentialTable.selectAll().where { PasswordCredentialTable.accountId eq accountId.value }
            .singleOrNull()?.get(PasswordCredentialTable.passwordHash)
}
