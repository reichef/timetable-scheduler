package timetablescheduler.domain.scheduling.generation

import timetablescheduler.domain.common.AccountId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.validation.ValidationError
import timetablescheduler.domain.validation.ValidationResult
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class GenerationTest {

    private val startedAt = Instant.parse("2026-10-09T08:00:00Z")

    private fun start(
        timeLimitMinutes: Int = Generation.DEFAULT_TIME_LIMIT_MINUTES,
        minimumPlacedShare: Int = Generation.DEFAULT_MINIMUM_PLACED_SHARE,
    ) = Generation.start(SchoolId.new(), AccountId.new(), startedAt, timeLimitMinutes, inputFingerprint = "f".repeat(64), minimumPlacedShare)

    private fun started() = assertIs<ValidationResult.Valid<Generation>>(start()).value

    @Test
    fun `a new generation runs with the default limit of 10 minutes and at most 10 alternatives`() {
        val generation = started()

        assertEquals(GenerationStatus.RUNNING, generation.status)
        assertEquals(10, generation.timeLimitMinutes)
        assertEquals(10, generation.maxAlternatives)
        assertEquals(0, generation.alternativesFound)
        assertNull(generation.finishedAt)
    }

    @Test
    fun `the time limit must be between 1 and 60 minutes (FR-009)`() {
        val outOfRange = ValidationResult.Invalid(listOf(ValidationError.OutOfRange(Generation.FIELD_TIME_LIMIT, 1, 60)))

        assertEquals(outOfRange, start(0))
        assertEquals(outOfRange, start(61))
        assertIs<ValidationResult.Valid<Generation>>(start(60))
        assertIs<ValidationResult.Valid<Generation>>(start(1))
    }

    @Test
    fun `a running generation ends in one of the final states`() {
        val finishedAt = startedAt.plusSeconds(120)

        GenerationStatus.entries.filter { it != GenerationStatus.RUNNING }.forEach { status ->
            val ended = started().endAs(status, finishedAt)
            assertEquals(status, ended.status)
            assertEquals(finishedAt, ended.finishedAt)
        }
    }

    @Test
    fun `an ended generation cannot end again or go back to running`() {
        val ended = started().endAs(GenerationStatus.FINISHED, startedAt)

        assertFailsWith<IllegalStateException> { ended.endAs(GenerationStatus.STOPPED, startedAt) }
        assertFailsWith<IllegalArgumentException> { started().endAs(GenerationStatus.RUNNING, startedAt) }
    }

    @Test
    fun `the minimum share of placed lessons defaults to 90 and must be between 50 and 100 percent (FR-007a)`() {
        val outOfRange = ValidationResult.Invalid(listOf(ValidationError.OutOfRange(Generation.FIELD_MINIMUM_PLACED_SHARE, 50, 100)))

        assertEquals(90, started().minimumPlacedShare)
        assertEquals(outOfRange, start(minimumPlacedShare = 49))
        assertEquals(outOfRange, start(minimumPlacedShare = 101))
        assertEquals(50, assertIs<ValidationResult.Valid<Generation>>(start(minimumPlacedShare = 50)).value.minimumPlacedShare)
        assertEquals(100, assertIs<ValidationResult.Valid<Generation>>(start(minimumPlacedShare = 100)).value.minimumPlacedShare)
    }
}
