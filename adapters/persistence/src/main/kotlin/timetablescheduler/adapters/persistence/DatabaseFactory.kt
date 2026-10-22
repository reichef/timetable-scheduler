package timetablescheduler.adapters.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database

data class DatabaseSettings(
    val jdbcUrl: String,
    val username: String,
    val password: String,
) {
    override fun toString(): String = "DatabaseSettings(jdbcUrl=<hidden>, username=<hidden>, password=<hidden>)"
}

/** Owns the connection pool and the Exposed database; schema changes are applied by Flyway on start (R5). */
class DatabaseFactory private constructor(
    private val dataSource: HikariDataSource,
) {
    val database: Database = Database.connect(dataSource)
    val transactionRunner = ExposedTransactionRunner(database)

    fun close() = dataSource.close()

    companion object {
        fun connectAndMigrate(settings: DatabaseSettings): DatabaseFactory {
            val dataSource =
                HikariDataSource(
                    HikariConfig().apply {
                        jdbcUrl = settings.jdbcUrl
                        username = settings.username
                        password = settings.password
                        maximumPoolSize = 10
                        isAutoCommit = false
                    },
                )
            Flyway
                .configure()
                .dataSource(dataSource)
                .load()
                .migrate()
            return DatabaseFactory(dataSource)
        }
    }
}
