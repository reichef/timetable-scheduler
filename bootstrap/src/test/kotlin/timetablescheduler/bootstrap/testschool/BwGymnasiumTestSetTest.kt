package timetablescheduler.bootstrap.testschool

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The test set matches specs/002-timetable-generation/test-set-bw-gymnasium.md and is recognizable as test data. */
class BwGymnasiumTestSetTest {
    private val testSet = BwGymnasiumTestSet

    @Test
    fun `the test set has the size described in the specification`() {
        assertEquals(31, testSet.rooms.size)
        assertEquals(18, testSet.subjects.size)
        assertEquals(18, testSet.classes.size)
        assertEquals(32, testSet.teachers.size)
        assertEquals(10, testSet.slots.count { it.isTeaching })
        assertEquals(18, testSet.constraints.size, "20 constraints minus the 2 defaults the installation creates")
        assertEquals(3, testSet.fixations.size)
    }

    @Test
    fun `the weekly periods add up to 588 lessons and the teachers offer 705 periods`() {
        val totalsPerGrade = testSet.weeklyPeriods.mapValues { (_, periods) -> periods.values.sum() }

        assertEquals(mapOf(5 to 31, 6 to 32, 7 to 32, 8 to 33, 9 to 34, 10 to 34), totalsPerGrade)
        assertEquals(588, testSet.classes.sumOf { totalsPerGrade.getValue(it.grade) })
        assertEquals(705, testSet.teachers.sumOf { it.weeklyLoad })
    }

    @Test
    fun `every subject of every curriculum has a qualified teacher and refers to known names`() {
        val subjectNames = testSet.subjects.map { it.name }.toSet()
        val taughtSubjects = testSet.teachers.flatMap { it.subjects }.toSet()
        val requiredSubjects =
            testSet.weeklyPeriods.values
                .flatMap { it.keys }
                .toSet()

        assertTrue(subjectNames.containsAll(requiredSubjects), "curricula use unknown subjects")
        assertTrue(subjectNames.containsAll(taughtSubjects), "teachers use unknown subjects")
        assertEquals(requiredSubjects, requiredSubjects.intersect(taughtSubjects), "subjects without a qualified teacher")
    }

    @Test
    fun `every class has its own home room and every feature has rooms`() {
        val homeRooms = testSet.classes.map { it.homeRoom }
        val roomIdentifiers = testSet.rooms.map { it.identifier }.toSet()
        val requiredFeatures = testSet.subjects.mapNotNull { it.requiredFeature }.toSet()

        assertEquals(homeRooms.size, homeRooms.toSet().size)
        assertTrue(roomIdentifiers.containsAll(homeRooms))
        assertTrue(
            testSet.rooms
                .mapNotNull { it.feature }
                .toSet()
                .containsAll(requiredFeatures),
        )
    }

    @Test
    fun `every name carries the test marker, so the data is never mistaken for a real school`() {
        assertTrue(testSet.SCHOOL_NAME.startsWith("TESTDATEN"))
        testSet.teachers.forEach { assertTrue(it.name.startsWith("Testlehrkraft"), it.name) }
        testSet.rooms.forEach { assertTrue(it.identifier.startsWith("TEST-"), it.identifier) }
    }
}
