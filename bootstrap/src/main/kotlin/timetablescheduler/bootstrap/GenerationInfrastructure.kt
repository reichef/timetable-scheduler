package timetablescheduler.bootstrap

import org.slf4j.LoggerFactory
import timetablescheduler.application.port.GenerationEventLog
import timetablescheduler.application.port.GenerationExecutor
import timetablescheduler.domain.scheduling.generation.GenerationId
import timetablescheduler.domain.scheduling.generation.GenerationStatus
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Runs generations one after another on one background thread (G6). A failure that escapes a generation is logged
 * by exception class only and does not stop later generations.
 */
class SingleThreadGenerationExecutor : GenerationExecutor {
    private val threadNumber = AtomicInteger()
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "generation-${threadNumber.incrementAndGet()}")
    }

    override fun submit(task: () -> Unit) {
        executor.execute {
            try {
                task()
            } catch (failure: Exception) {
                log.error("Generation task failed: {}", failure.javaClass.name)
            }
        }
    }

    fun shutdown() {
        executor.shutdownNow()
    }

    private companion object {
        val log = LoggerFactory.getLogger(SingleThreadGenerationExecutor::class.java)
    }
}

/** Writes generation events with identifiers, statuses, counts and durations only (G13, FR-026). */
class Slf4jGenerationEventLog : GenerationEventLog {
    private val log = LoggerFactory.getLogger(LOGGER_NAME)

    override fun started(generationId: GenerationId) {
        log.info("Generation started id={}", generationId.value)
    }

    override fun discarded(generationId: GenerationId, candidates: Int) {
        log.warn("Generation discarded candidates violating hard constraints id={} count={}", generationId.value, candidates)
    }

    override fun finished(generationId: GenerationId, status: GenerationStatus, alternatives: Int, duration: Duration) {
        log.info("Generation ended id={} status={} alternatives={} durationSeconds={}", generationId.value, status, alternatives, duration.seconds)
    }

    override fun failed(generationId: GenerationId, exceptionClass: String) {
        log.error("Generation failed id={} exception={}", generationId.value, exceptionClass)
    }

    companion object {
        const val LOGGER_NAME = "timetablescheduler.generation"
    }
}
