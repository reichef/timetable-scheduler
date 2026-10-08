package timetablescheduler.domain.curriculum

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SuffixProposalTest {

    @Test
    fun `first class of a level gets a`() {
        assertEquals("a", SuffixProposal.next(emptyList()))
    }

    @Test
    fun `proposes the next letter after a and b`() {
        assertEquals("c", SuffixProposal.next(listOf("a", "b")))
    }

    @Test
    fun `fills the smallest gap and ignores case`() {
        assertEquals("b", SuffixProposal.next(listOf("A", "c")))
    }

    @Test
    fun `non-letter suffixes do not block letters`() {
        assertEquals("a", SuffixProposal.next(listOf("bili", "1")))
    }

    @Test
    fun `no proposal when all letters are used`() {
        assertNull(SuffixProposal.next(('a'..'z').map { "$it" }))
    }
}
