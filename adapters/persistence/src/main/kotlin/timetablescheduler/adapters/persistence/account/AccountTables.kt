package timetablescheduler.adapters.persistence.account

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import timetablescheduler.adapters.persistence.school.SchoolTable
import timetablescheduler.domain.account.UserAccount

object UserAccountTable : Table("user_account") {
    val id = javaUUID("id")
    val username = varchar("username", UserAccount.USERNAME_MAX_LENGTH)
    val role = varchar("role", 16)
    val active = bool("active")
    val version = integer("version")
    val preferredLanguage = varchar("preferred_language", 2).nullable()
    override val primaryKey = PrimaryKey(id)
}

object AccountSchoolTable : Table("account_school") {
    val accountId = javaUUID("account_id").references(UserAccountTable.id)
    val schoolId = javaUUID("school_id").references(SchoolTable.id)
    override val primaryKey = PrimaryKey(accountId, schoolId)
}

object PasswordCredentialTable : Table("password_credential") {
    val accountId = javaUUID("account_id").references(UserAccountTable.id)
    val passwordHash = varchar("password_hash", 100)
    override val primaryKey = PrimaryKey(accountId)
}
