/*
 * TEST DATA — NOT A REAL SCHOOL.
 * A fictitious G9 Gymnasium in the style of Baden-Württemberg, grades 5–10, three classes per grade. Every name is
 * invented and carries a test marker; the weekly periods are an assumption, not the official subject table.
 * Description and accepted simplifications: specs/002-timetable-generation/test-set-bw-gymnasium.md
 */
package timetablescheduler.bootstrap.testschool

import timetablescheduler.domain.scheduling.constraint.ConstraintKind
import timetablescheduler.domain.scheduling.constraint.SoftWeight
import timetablescheduler.domain.school.SlotKind
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalTime

data class TestSlot(val label: String, val start: LocalTime, val end: LocalTime, val kind: SlotKind = SlotKind.TEACHING) {
    val isTeaching: Boolean get() = kind == SlotKind.TEACHING
}

/** A slot on a day, named by its label, because ids exist only after seeding. */
data class TestDaySlot(val day: DayOfWeek, val slotLabel: String)

data class TestRoom(val identifier: String, val feature: String? = null)

data class TestSubject(val name: String, val requiredFeature: String? = null)

data class TestClass(val grade: Int, val suffix: String, val homeRoom: String) {
    val name: String get() = "$grade$suffix"
}

data class TestTeacher(val code: String, val subjects: Set<String>, val weeklyLoad: Int, val unavailable: Set<TestDaySlot> = emptySet()) {
    val name: String get() = "Testlehrkraft ${code.removePrefix("T")}"
}

/** A planner constraint by names; the seeder turns it into a domain rule with the seeded ids. */
data class TestConstraint(val kind: ConstraintKind, val weight: SoftWeight?, val rule: TestRule)

sealed interface TestRule {
    data class AllClassesMaxPeriodsPerDay(val limit: Int) : TestRule
    data class ClassExcludedSlots(val className: String, val slots: Set<TestDaySlot>) : TestRule
    data class DoublePeriods(val className: String, val subject: String) : TestRule
    data class TeacherExcludedSlots(val teacherCode: String, val slots: Set<TestDaySlot>) : TestRule
    data class TeacherMaxPeriodsPerDay(val teacherCode: String, val limit: Int) : TestRule
    data class TeacherMaxGapsPerDay(val teacherCode: String, val limit: Int) : TestRule
    data class SubjectExcludedSlots(val subject: String, val slots: Set<TestDaySlot>) : TestRule
}

data class TestFixation(val className: String, val subject: String, val teacherCode: String)

object BwGymnasiumTestSet {
    const val SCHOOL_NAME = "TESTDATEN – Testgymnasium Musterstadt"

    const val PRIORITY_WEIGHT = 10

    const val NATURAL_SCIENCE = "Naturwissenschaftsraum"
    const val SPORTS_HALL = "Sporthalle"
    const val MUSIC_ROOM = "Musikraum"
    const val ART_ROOM = "Kunstraum"
    const val COMPUTER_ROOM = "Computerraum"

    const val GERMAN = "Deutsch"
    const val MATHS = "Mathematik"
    const val ENGLISH = "Englisch"
    const val SECOND_LANGUAGE = "2. Fremdsprache"
    const val PROFILE = "Profilfach"
    const val BNT = "BNT"
    const val BIOLOGY = "Biologie"
    const val PHYSICS = "Physik"
    const val CHEMISTRY = "Chemie"
    const val HISTORY = "Geschichte"
    const val GEOGRAPHY = "Geographie"
    const val CIVICS = "Gemeinschaftskunde"
    const val RELIGION = "Religion/Ethik"
    const val SPORT = "Sport"
    const val MUSIC = "Musik"
    const val ART = "Bildende Kunst"
    const val MEDIA = "Medienbildung"
    const val COMPUTER_SCIENCE = "Informatik"

    val grades = 5..10

    val classSuffixes = listOf("a", "b", "c")

    val teachingDays: Set<DayOfWeek> = DayOfWeek.entries.take(5).toSet()

    val slots: List<TestSlot> = listOf(
        TestSlot("1. Stunde", time("07:45"), time("08:30")),
        TestSlot("2. Stunde", time("08:35"), time("09:20")),
        TestSlot("Große Pause", time("09:20"), time("09:40"), SlotKind.BREAK),
        TestSlot("3. Stunde", time("09:40"), time("10:25")),
        TestSlot("4. Stunde", time("10:30"), time("11:15")),
        TestSlot("Pause", time("11:15"), time("11:30"), SlotKind.BREAK),
        TestSlot("5. Stunde", time("11:30"), time("12:15")),
        TestSlot("6. Stunde", time("12:20"), time("13:05")),
        TestSlot("Mittagspause", time("13:05"), time("13:50"), SlotKind.BREAK),
        TestSlot("7. Stunde", time("13:50"), time("14:35")),
        TestSlot("8. Stunde", time("14:40"), time("15:25")),
        TestSlot("9. Stunde", time("15:30"), time("16:15")),
        TestSlot("10. Stunde", time("16:20"), time("17:05")),
    )

    val features = listOf(NATURAL_SCIENCE, SPORTS_HALL, MUSIC_ROOM, ART_ROOM, COMPUTER_ROOM)

    val classes: List<TestClass> = grades.flatMap { grade ->
        classSuffixes.map { suffix ->
            val position = (grade - grades.first) * classSuffixes.size + classSuffixes.indexOf(suffix)

            TestClass(grade, suffix, homeRoom = "TEST-R${101 + position}")
        }
    }

    val rooms: List<TestRoom> =
        (1..4).map { TestRoom("TEST-NW$it", NATURAL_SCIENCE) } +
            (1..2).map { TestRoom("TEST-SH$it", SPORTS_HALL) } +
            (1..2).map { TestRoom("TEST-MU$it", MUSIC_ROOM) } +
            (1..2).map { TestRoom("TEST-KU$it", ART_ROOM) } +
            TestRoom("TEST-PC1", COMPUTER_ROOM) +
            (101..120).map { TestRoom("TEST-R$it") }

    val subjects: List<TestSubject> = listOf(
        TestSubject(GERMAN),
        TestSubject(MATHS),
        TestSubject(ENGLISH),
        TestSubject(SECOND_LANGUAGE),
        TestSubject(HISTORY),
        TestSubject(GEOGRAPHY),
        TestSubject(CIVICS),
        TestSubject(RELIGION),
        TestSubject(BNT, NATURAL_SCIENCE),
        TestSubject(BIOLOGY, NATURAL_SCIENCE),
        TestSubject(PHYSICS, NATURAL_SCIENCE),
        TestSubject(CHEMISTRY, NATURAL_SCIENCE),
        TestSubject(PROFILE, NATURAL_SCIENCE),
        TestSubject(SPORT, SPORTS_HALL),
        TestSubject(MUSIC, MUSIC_ROOM),
        TestSubject(ART, ART_ROOM),
        TestSubject(MEDIA, COMPUTER_ROOM),
        TestSubject(COMPUTER_SCIENCE, COMPUTER_ROOM),
    )

    /** Weekly periods per grade (an assumption in the style of a G9 subject table). */
    val weeklyPeriods: Map<Int, Map<String, Int>> = mapOf(
        5 to mapOf(
            GERMAN to 5, MATHS to 5, ENGLISH to 5, BNT to 4, GEOGRAPHY to 2, RELIGION to 2, SPORT to 3, MUSIC to 2, ART to 2,
            MEDIA to 1,
        ),
        6 to mapOf(
            GERMAN to 4, MATHS to 4, ENGLISH to 4, SECOND_LANGUAGE to 4, BNT to 3, HISTORY to 2, GEOGRAPHY to 2, RELIGION to 2,
            SPORT to 3, MUSIC to 2, ART to 2,
        ),
        7 to mapOf(
            GERMAN to 4, MATHS to 4, ENGLISH to 4, SECOND_LANGUAGE to 4, BIOLOGY to 2, PHYSICS to 2, HISTORY to 2, GEOGRAPHY to 1,
            RELIGION to 2, SPORT to 2, MUSIC to 2, ART to 2, COMPUTER_SCIENCE to 1,
        ),
        8 to mapOf(
            GERMAN to 4, MATHS to 4, ENGLISH to 3, SECOND_LANGUAGE to 3, PROFILE to 4, BIOLOGY to 2, PHYSICS to 2, CHEMISTRY to 2,
            HISTORY to 2, GEOGRAPHY to 1, RELIGION to 2, SPORT to 2, MUSIC to 1, ART to 1,
        ),
        9 to mapOf(
            GERMAN to 4, MATHS to 4, ENGLISH to 3, SECOND_LANGUAGE to 3, PROFILE to 4, BIOLOGY to 2, PHYSICS to 2, CHEMISTRY to 2,
            HISTORY to 2, CIVICS to 1, GEOGRAPHY to 1, RELIGION to 2, SPORT to 2, MUSIC to 1, ART to 1,
        ),
        10 to mapOf(
            GERMAN to 4, MATHS to 4, ENGLISH to 3, SECOND_LANGUAGE to 3, PROFILE to 4, BIOLOGY to 2, PHYSICS to 2, CHEMISTRY to 2,
            HISTORY to 2, CIVICS to 2, GEOGRAPHY to 1, RELIGION to 2, SPORT to 2, MUSIC to 1,
        ),
    )

    val teachers: List<TestTeacher> = listOf(
        TestTeacher("T01", setOf(GERMAN, HISTORY), 25),
        TestTeacher("T02", setOf(GERMAN, HISTORY), 25),
        TestTeacher("T03", setOf(GERMAN, GEOGRAPHY), 25),
        TestTeacher("T04", setOf(GERMAN, ENGLISH), 25),
        TestTeacher("T05", setOf(GERMAN, RELIGION), 18, unavailable = wholeDay(MONDAY)),
        TestTeacher("T06", setOf(GERMAN, CIVICS), 15),
        TestTeacher("T07", setOf(MATHS, PHYSICS), 25),
        TestTeacher("T08", setOf(MATHS, PHYSICS), 25),
        TestTeacher("T09", setOf(MATHS, CHEMISTRY), 25),
        TestTeacher("T10", setOf(MATHS, COMPUTER_SCIENCE, MEDIA), 25),
        TestTeacher("T11", setOf(MATHS, BIOLOGY), 18),
        TestTeacher("T12", setOf(MATHS, GEOGRAPHY), 15),
        TestTeacher("T13", setOf(ENGLISH, SECOND_LANGUAGE), 25),
        TestTeacher("T14", setOf(ENGLISH, SECOND_LANGUAGE), 25),
        TestTeacher("T15", setOf(ENGLISH, HISTORY), 25),
        TestTeacher("T16", setOf(ENGLISH, SPORT), 18),
        TestTeacher("T17", setOf(SECOND_LANGUAGE, CIVICS), 25),
        TestTeacher("T18", setOf(SECOND_LANGUAGE, GERMAN), 15, unavailable = daySlots(TUESDAY, 1..4)),
        TestTeacher("T19", setOf(BIOLOGY, CHEMISTRY, BNT), 25),
        TestTeacher("T20", setOf(BIOLOGY, BNT, PROFILE), 25),
        TestTeacher("T21", setOf(PHYSICS, PROFILE), 25),
        TestTeacher("T22", setOf(CHEMISTRY, PROFILE), 25),
        TestTeacher("T23", setOf(BNT, GEOGRAPHY), 18),
        TestTeacher("T24", setOf(RELIGION, HISTORY), 25),
        TestTeacher("T25", setOf(RELIGION, ENGLISH), 15, unavailable = wholeDay(FRIDAY)),
        TestTeacher("T26", setOf(SPORT, BIOLOGY), 25),
        TestTeacher("T27", setOf(SPORT, GEOGRAPHY), 25),
        TestTeacher("T28", setOf(MUSIC, GERMAN), 25),
        TestTeacher("T29", setOf(MUSIC, MATHS), 15),
        TestTeacher("T30", setOf(ART, ENGLISH), 25),
        TestTeacher("T31", setOf(ART, HISTORY), 18),
        TestTeacher("T32", setOf(SPORT, RELIGION), 15),
    )

    /** The 18 constraints besides the two school-wide defaults, which the installation creates. */
    val constraints: List<TestConstraint> =
        listOf(hard(TestRule.AllClassesMaxPeriodsPerDay(8))) +
            listOf("5a", "5b", "5c", "6a", "6b", "6c").map { hard(TestRule.ClassExcludedSlots(it, everyDay(9..10))) } +
            listOf("5a", "5b", "5c").map { soft(SoftWeight.MEDIUM, TestRule.DoublePeriods(it, BNT)) } +
            listOf(
                hard(TestRule.TeacherExcludedSlots("T06", daySlots(WEDNESDAY, 7..10))),
                hard(TestRule.TeacherExcludedSlots("T12", everyDay(1..1))),
                hard(TestRule.TeacherExcludedSlots("T29", wholeDay(FRIDAY))),
                soft(SoftWeight.LOW, TestRule.TeacherMaxPeriodsPerDay("T07", 6)),
                soft(SoftWeight.LOW, TestRule.TeacherMaxPeriodsPerDay("T13", 6)),
                soft(SoftWeight.MEDIUM, TestRule.TeacherMaxGapsPerDay("T01", 2)),
                soft(SoftWeight.MEDIUM, TestRule.TeacherMaxGapsPerDay("T20", 2)),
                soft(SoftWeight.LOW, TestRule.SubjectExcludedSlots(SPORT, everyDay(1..1))),
            )

    /** Class teachers who keep their subject (002 FR-013a). */
    val fixations: List<TestFixation> = listOf(
        TestFixation("5a", GERMAN, "T01"),
        TestFixation("5b", MATHS, "T07"),
        TestFixation("5c", ENGLISH, "T13"),
    )

    fun teacher(code: String): TestTeacher = teachers.single { it.code == code }

    private fun time(text: String): LocalTime = LocalTime.parse(text)

    private fun hard(rule: TestRule) = TestConstraint(ConstraintKind.HARD, null, rule)

    private fun soft(weight: SoftWeight, rule: TestRule) = TestConstraint(ConstraintKind.SOFT, weight, rule)

    /** Teaching periods are numbered 1–10 as on the timetable ("1. Stunde" … "10. Stunde"). */
    private fun daySlots(day: DayOfWeek, periods: IntRange): Set<TestDaySlot> =
        periods.map { TestDaySlot(day, "$it. Stunde") }.toSet()

    private fun wholeDay(day: DayOfWeek): Set<TestDaySlot> = daySlots(day, 1..10)

    private fun everyDay(periods: IntRange): Set<TestDaySlot> = teachingDays.flatMap { daySlots(it, periods) }.toSet()
}
