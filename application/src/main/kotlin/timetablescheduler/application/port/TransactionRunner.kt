package timetablescheduler.application.port

import timetablescheduler.application.error.UseCaseResult

/** Runs one use case atomically; a [UseCaseResult.Failure] rolls back all writes of the block. */
interface TransactionRunner {
    fun <T> inTransaction(block: () -> UseCaseResult<T>): UseCaseResult<T>
}
