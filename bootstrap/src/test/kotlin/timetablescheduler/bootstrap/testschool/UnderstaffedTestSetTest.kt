package timetablescheduler.bootstrap.testschool

import timetablescheduler.bootstrap.testschool.BwGymnasiumTestSet.ART
import timetablescheduler.bootstrap.testschool.BwGymnasiumTestSet.MUSIC
import timetablescheduler.bootstrap.testschool.BwGymnasiumTestSet.SPORT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The teacher-shortage variant cannot be served completely, whatever the timetable (test-set-bw-gymnasium.md). */
class UnderstaffedTestSetTest {
    private val variant = UnderstaffedTestSet

    private fun demandOf(subject: String): Int = variant.classes.sumOf { variant.weeklyPeriods.getValue(it.grade)[subject] ?: 0 }

    private fun capacityFor(subject: String): Int = variant.teachers.filter { subject in it.subjects }.sumOf { it.weeklyLoad }

    @Test
    fun `28 teachers offer 574 periods for 588 lessons`() {
        assertEquals(28, variant.teachers.size)
        assertEquals(574, variant.teachers.sumOf { it.weeklyLoad })
        assertEquals(
            588,
            variant.classes.sumOf { schoolClass ->
                variant.weeklyPeriods
                    .getValue(schoolClass.grade)
                    .values
                    .sum()
            },
        )
    }

    @Test
    fun `Musik, Bildende Kunst and Sport are short by at least 9, 6 and 2 periods`() {
        assertEquals(9, demandOf(MUSIC) - capacityFor(MUSIC))
        assertEquals(6, demandOf(ART) - capacityFor(ART))
        assertEquals(2, demandOf(SPORT) - capacityFor(SPORT))
    }

    @Test
    fun `every subject keeps a qualified teacher and everything else equals the first test set`() {
        val requiredSubjects =
            variant.weeklyPeriods.values
                .flatMap { it.keys }
                .toSet()

        assertTrue(
            variant.teachers
                .flatMap { it.subjects }
                .toSet()
                .containsAll(requiredSubjects),
        )
        assertEquals(BwGymnasiumTestSet.classes, variant.classes)
        assertEquals(BwGymnasiumTestSet.weeklyPeriods, variant.weeklyPeriods)
        assertEquals(BwGymnasiumTestSet.fixations, variant.fixations)
    }

    @Test
    fun `constraints refer only to teachers of the variant`() {
        val codes = variant.teachers.map { it.code }.toSet()
        val referencedCodes =
            variant.constraints.mapNotNull { constraint ->
                when (val rule = constraint.rule) {
                    is TestRule.TeacherExcludedSlots -> rule.teacherCode
                    is TestRule.TeacherMaxPeriodsPerDay -> rule.teacherCode
                    is TestRule.TeacherMaxGapsPerDay -> rule.teacherCode
                    else -> null
                }
            }

        assertTrue(codes.containsAll(referencedCodes))
        assertEquals(BwGymnasiumTestSet.constraints.size - 1, variant.constraints.size, "only the rule of the removed T29 is dropped")
    }

    @Test
    fun `the variant is marked as test data and as shortage scenario`() {
        assertTrue(variant.schoolName.startsWith("TESTDATEN"))
        assertTrue("Lehrkräftemangel" in variant.schoolName)
        variant.teachers.forEach { assertTrue(it.name.startsWith("Testlehrkraft"), it.name) }
    }
}
