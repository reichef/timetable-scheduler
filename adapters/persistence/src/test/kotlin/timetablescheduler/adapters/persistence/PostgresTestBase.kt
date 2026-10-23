package timetablescheduler.adapters.persistence

import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.testcontainers.postgresql.PostgreSQLContainer
import timetablescheduler.application.error.UseCaseResult
import kotlin.test.BeforeTest

/** Starts one PostgreSQL container per test JVM, migrates it and empties all tables before each test. */
abstract class PostgresTestBase {
    protected val database: DatabaseFactory get() = sharedDatabase
    protected val transactions: ExposedTransactionRunner get() = sharedDatabase.transactionRunner

    @BeforeTest
    fun emptyAllTables() {
        transaction(sharedDatabase.database) {
            val tableNames =
                exec(
                    "SELECT string_agg(quote_ident(tablename), ', ') FROM pg_tables " +
                        "WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'",
                ) { result -> if (result.next()) result.getString(1) else null }
            if (!tableNames.isNullOrEmpty()) {
                exec("TRUNCATE $tableNames CASCADE")
            }
        }
    }

    /** Runs [block] in a committed transaction, as a use case would. */
    protected fun <T> inTransaction(block: () -> T): T =
        (transactions.inTransaction { UseCaseResult.Success(block()) } as UseCaseResult.Success).value

    companion object {
        private val container = PostgreSQLContainer("postgres:17").also { it.start() }

        private val sharedDatabase: DatabaseFactory by lazy {
            DatabaseFactory.connectAndMigrate(
                DatabaseSettings(jdbcUrl = container.jdbcUrl, username = container.username, password = container.password),
            )
        }
    }
}
