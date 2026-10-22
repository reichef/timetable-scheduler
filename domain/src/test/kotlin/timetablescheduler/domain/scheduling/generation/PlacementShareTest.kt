package timetablescheduler.domain.scheduling.generation

import timetablescheduler.domain.scheduling.pareto.ParetoArchive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** FR-007a: only alternatives placing at least the minimum share of the best alternative's lessons are kept. */
class PlacementShareTest {

    @Test
    fun `an alternative is kept when it places at least the share of the best one's lessons, without rounding`() {
        val share = PlacementShare(90).apply { record(569) }

        assertTrue(share.admits(513), "90 % of 569 = 512.1, so 513 lessons are enough")
        assertFalse(share.admits(512))
        assertTrue(share.admits(569))
    }

    @Test
    fun `an alternative without any placed lesson is never kept, also when it is the best one`() {
        val share = PlacementShare(50)

        share.record(0)

        assertFalse(share.admits(0))
    }

    @Test
    fun `a share of 100 percent keeps only alternatives as complete as the best one`() {
        val share = PlacementShare(100).apply { record(40) }

        assertTrue(share.admits(40))
        assertFalse(share.admits(39))
    }

    @Test
    fun `when the best rises, kept alternatives below the new share are removed from the archive`() {
        val share = PlacementShare(90)
        val archive = ParetoArchive<Int> { first, second -> first == second }
        listOf(30, 28).forEach { placed ->
            share.record(placed)
            archive.offer(placed, listOf(placed.toDouble(), 1.0 / placed))
        }

        val rose = share.record(100)
        val removed = archive.removeIf { !share.admits(it) }

        assertTrue(rose)
        assertEquals(listOf(30, 28), removed)
        assertEquals(emptyList(), archive.entries)
    }

    @Test
    fun `recording fewer lessons than the best keeps the best`() {
        val share = PlacementShare(90).apply { record(100) }

        assertFalse(share.record(80))
        assertEquals(100, share.bestPlaced)
    }
}
