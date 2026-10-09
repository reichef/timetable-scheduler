package timetablescheduler.domain.scheduling.pareto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ParetoArchiveTest {

    /** Items are names; two items describe the same timetable when their names are equal. */
    private fun archive(capacity: Int = 10) = ParetoArchive<String>(capacity) { first, second -> first == second }

    private fun ParetoArchive<String>.names() = entries.map { it.item }.toSet()

    @Test
    fun `better or equal in every objective and better in one dominates`() {
        assertTrue(Dominance.dominates(listOf(0.9, 0.5), listOf(0.8, 0.5)))
        assertFalse(Dominance.dominates(listOf(0.9, 0.5), listOf(0.9, 0.5)))
        assertFalse(Dominance.dominates(listOf(0.9, 0.4), listOf(0.8, 0.5)))
    }

    @Test
    fun `values are compared after rounding to 4 decimals`() {
        assertFalse(Dominance.dominates(listOf(0.90001, 0.5), listOf(0.9, 0.5)))
    }

    @Test
    fun `dominated offer is rejected`() {
        val archive = archive()
        archive.offer("good", listOf(0.9, 0.9))

        val change = archive.offer("worse", listOf(0.8, 0.9))

        assertFalse(change.admitted)
        assertEquals(setOf("good"), archive.names())
    }

    @Test
    fun `dominating offer evicts every candidate it dominates`() {
        val archive = archive()
        archive.offer("a", listOf(0.5, 0.9))
        archive.offer("b", listOf(0.9, 0.5))
        archive.offer("c", listOf(0.6, 0.6))

        val change = archive.offer("best", listOf(0.9, 0.9))

        assertTrue(change.admitted)
        assertEquals(setOf("a", "b", "c"), change.evicted.toSet())
        assertEquals(setOf("best"), archive.names())
    }

    @Test
    fun `trade-off candidates are all kept`() {
        val archive = archive()
        archive.offer("fill", listOf(1.0, 0.6))
        archive.offer("load", listOf(0.8, 0.9))
        archive.offer("middle", listOf(0.9, 0.8))

        assertEquals(setOf("fill", "load", "middle"), archive.names())
    }

    @Test
    fun `equal objective values with a different timetable are both kept (FR-007)`() {
        val archive = archive()
        archive.offer("monday-math", listOf(0.9, 0.9))

        val change = archive.offer("tuesday-math", listOf(0.9, 0.9))

        assertTrue(change.admitted)
        assertEquals(setOf("monday-math", "tuesday-math"), archive.names())
    }

    @Test
    fun `the same timetable is kept only once`() {
        val archive = archive()
        archive.offer("same", listOf(0.9, 0.9))

        val change = archive.offer("same", listOf(0.9, 0.9))

        assertFalse(change.admitted)
        assertEquals(1, archive.entries.size)
    }

    @Test
    fun `beyond capacity the most crowded candidate is evicted and the extremes stay (FR-008)`() {
        val archive = archive(capacity = 3)
        archive.offer("extreme-fill", listOf(1.0, 0.0))
        archive.offer("extreme-load", listOf(0.0, 1.0))
        archive.offer("middle", listOf(0.5, 0.5))

        val change = archive.offer("near-middle", listOf(0.52, 0.48))

        assertEquals(3, archive.entries.size)
        assertTrue("extreme-fill" in archive.names())
        assertTrue("extreme-load" in archive.names())
        assertEquals(1, change.evicted.size + (if (change.admitted) 0 else 1))
    }

    @Test
    fun `default capacity is 10`() {
        assertEquals(10, ParetoArchive.DEFAULT_CAPACITY)
        val archive = archive(ParetoArchive.DEFAULT_CAPACITY)
        repeat(15) { index -> archive.offer("point-$index", listOf(index / 14.0, 1 - index / 14.0)) }

        assertEquals(10, archive.entries.size)
        assertTrue("point-0" in archive.names())
        assertTrue("point-14" in archive.names())
    }

    @Test
    fun `kept candidates never dominate each other`() {
        val archive = archive(capacity = 5)
        val offers = listOf(
            listOf(0.3, 0.9), listOf(0.6, 0.6), listOf(0.2, 0.2), listOf(0.9, 0.1), listOf(0.6, 0.7),
            listOf(0.95, 0.15), listOf(0.4, 0.85), listOf(0.7, 0.5),
        )
        offers.forEachIndexed { index, objectives -> archive.offer("c$index", objectives) }

        val kept = archive.entries.map { it.objectives }
        kept.forEach { first -> kept.forEach { second -> assertFalse(Dominance.dominates(first, second)) } }
    }
}
