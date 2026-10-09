package timetablescheduler.bootstrap

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory
import timetablescheduler.domain.scheduling.generation.GenerationId
import timetablescheduler.domain.scheduling.generation.GenerationStatus
import java.time.Duration
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GenerationInfrastructureTest {

    @Test
    fun `generations run one after another on a single background thread (G6)`() {
        val executor = SingleThreadGenerationExecutor()
        val threads = Collections.synchronizedList(mutableListOf<String>())
        val order = Collections.synchronizedList(mutableListOf<Int>())
        val done = CountDownLatch(3)

        (1..3).forEach { number ->
            executor.submit {
                threads += Thread.currentThread().name
                order += number
                done.countDown()
            }
        }

        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals(listOf(1, 2, 3), order)
        assertEquals(1, threads.toSet().size)
        assertTrue(threads.first().startsWith("generation-"))
        executor.shutdown()
    }

    @Test
    fun `a failing generation does not stop the executor`() {
        val executor = SingleThreadGenerationExecutor()
        val done = CountDownLatch(1)

        executor.submit { error("Anna Schmidt") }
        executor.submit { done.countDown() }

        assertTrue(done.await(5, TimeUnit.SECONDS))
        executor.shutdown()
    }

    @Test
    fun `the generation log contains only ids, statuses, counts and durations (G13)`() {
        val logger = LoggerFactory.getLogger(Slf4jGenerationEventLog.LOGGER_NAME) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        val generationId = GenerationId.new()
        try {
            Slf4jGenerationEventLog().apply {
                started(generationId)
                discarded(generationId, 2)
                failed(generationId, IllegalStateException::class.java.name)
                finished(generationId, GenerationStatus.FINISHED, 3, Duration.ofSeconds(125))
            }
        } finally {
            logger.detachAppender(appender)
        }

        val lines = appender.list.map { it.formattedMessage }
        assertEquals(4, lines.size)
        assertTrue(lines.all { "${generationId.value}" in it })
        assertTrue("status=FINISHED" in lines.last() && "alternatives=3" in lines.last() && "durationSeconds=125" in lines.last())
    }
}
