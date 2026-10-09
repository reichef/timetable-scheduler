package timetablescheduler.adapters.persistence

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.port.TransactionRunner

/** One database transaction per use case; a business failure rolls back everything the use case wrote. */
class ExposedTransactionRunner(private val database: Database) : TransactionRunner {
    override fun <T> inTransaction(block: () -> UseCaseResult<T>): UseCaseResult<T> =
        transaction(database) {
            val result = block()
            if (result is UseCaseResult.Failure) rollback()
            result
        }
}
