package timetablescheduler.domain.readiness

import timetablescheduler.domain.common.RoomFeatureId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.curriculum.ClassLevel
import timetablescheduler.domain.curriculum.CurriculumEntry
import timetablescheduler.domain.curriculum.SchoolClass
import timetablescheduler.domain.curriculum.Subject
import timetablescheduler.domain.room.Room
import timetablescheduler.domain.scheduling.constraint.FixationInvalidity
import timetablescheduler.domain.scheduling.constraint.TeacherFixation
import timetablescheduler.domain.scheduling.constraint.TeacherFixationId
import timetablescheduler.domain.teacher.Teacher
import timetablescheduler.domain.validation.ValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals

class ReadinessCheckTest {

    private val schoolId = SchoolId.new()
    private val chemieraum = RoomFeatureId.new()
    private val abzug = RoomFeatureId.new()

    private fun <T> ValidationResult<T>.valid(): T = (this as ValidationResult.Valid).value

    private fun subject(name: String, features: Set<RoomFeatureId> = emptySet()) = Subject.create(schoolId, name, features, features).valid()

    private fun room(identifier: String, features: Set<RoomFeatureId> = emptySet()) = Room.create(schoolId, identifier, features, features).valid()

    private fun level(identifier: String, vararg curriculum: Pair<Subject, Int>): ClassLevel {
        val entries = curriculum.map { (subject, periods) -> CurriculumEntry(subject.id, periods) }
        return ClassLevel.create(schoolId, identifier).valid().update(identifier, entries, entries.map { it.subjectId }.toSet()).valid()
    }

    private fun classes(level: ClassLevel, vararg suffixes: String) = suffixes.map { SchoolClass.create(level.id, it).valid() }

    private fun teacher(shortCode: String, load: Int, vararg qualifications: Subject) =
        Teacher.create(schoolId, "Lehrkraft $shortCode", shortCode, load, teachingSlotsPerWeek = 40).valid()
            .withQualifications(qualifications.map { it.id }.toSet(), qualifications.map { it.id }.toSet()).valid()

    private val mathe = subject("Mathematik")
    private val chemie = subject("Chemie", setOf(chemieraum))
    private val raumA = room("A-101")
    private val labor = room("B-104", setOf(chemieraum))

    private fun check(
        rooms: List<Room> = listOf(raumA, labor),
        subjects: List<Subject> = listOf(mathe, chemie),
        levels: List<ClassLevel> = emptyList(),
        classes: List<SchoolClass> = emptyList(),
        teachers: List<Teacher> = emptyList(),
        teachingSlotsPerWeek: Int = 38,
    ) = ReadinessCheck.issuesOf(ReadinessInput(teachingSlotsPerWeek, rooms, subjects, levels, classes, teachers))

    @Test
    fun `subject without a room offering all required features names the features no room has`() {
        val experimente = subject("Experimente", setOf(chemieraum, abzug))
        val level6 = level("6", experimente to 2)

        val issues = check(subjects = listOf(experimente), levels = listOf(level6), teachers = listOf(teacher("SCHA", 2, experimente)))

        assertEquals(listOf(ReadinessIssue.SubjectNoSuitableRoom(experimente.id, setOf(abzug))), issues)
    }

    @Test
    fun `subject whose features exist only in different rooms is reported without missing features`() {
        val experimente = subject("Experimente", setOf(chemieraum, abzug))
        val level6 = level("6", experimente to 2)

        val issues = check(
            rooms = listOf(labor, room("C-001", setOf(abzug))),
            subjects = listOf(experimente),
            levels = listOf(level6),
            teachers = listOf(teacher("SCHA", 2, experimente)),
        )

        assertEquals(listOf(ReadinessIssue.SubjectNoSuitableRoom(experimente.id, emptySet())), issues)
    }

    @Test
    fun `subjects not used in any curriculum are not checked`() {
        assertEquals(emptyList(), check(rooms = emptyList()))
    }

    @Test
    fun `subject in a curriculum without a qualified teacher`() {
        val level6 = level("6", mathe to 4)

        assertEquals(listOf(ReadinessIssue.SubjectNoQualifiedTeacher(mathe.id)), check(levels = listOf(level6), classes = classes(level6, "a")))
    }

    @Test
    fun `capacity shortfall of 10 when classes need 40 and qualified teachers offer 30 (acceptance 4_2)`() {
        val level5 = level("5", mathe to 4)
        val level6 = level("6", mathe to 4)
        val allClasses = classes(level5, "a", "b", "c", "d", "e") + classes(level6, "a", "b", "c", "d", "e")

        val issues = check(levels = listOf(level5, level6), classes = allClasses, teachers = listOf(teacher("SCHA", 20, mathe), teacher("MEY", 10, mathe)))

        assertEquals(listOf(ReadinessIssue.SubjectTeacherCapacityShortfall(mathe.id, shortfallPeriods = 10)), issues)
    }

    @Test
    fun `required periods are resolved per class, so a level without classes needs nothing`() {
        val level6 = level("6", mathe to 4)

        assertEquals(emptyList(), check(levels = listOf(level6), teachers = listOf(teacher("SCHA", 1, mathe))))
    }

    @Test
    fun `level exceeding the grid`() {
        val level6 = level("6", mathe to 40)

        val issues = check(levels = listOf(level6), teachers = listOf(teacher("SCHA", 38, mathe)))

        assertEquals(listOf(ReadinessIssue.LevelExceedsGrid(level6.id, excessPeriods = 2)), issues)
    }

    @Test
    fun `teacher without qualification and teacher with zero load`() {
        val unqualified = teacher("OHNE", 10)
        val onLeave = teacher("NULL", 0, mathe)

        assertEquals(
            listOf(ReadinessIssue.TeacherWithoutQualification(unqualified.id), ReadinessIssue.TeacherZeroLoad(onLeave.id)),
            check(teachers = listOf(unqualified, onLeave)),
        )
    }

    @Test
    fun `teacher load above a reduced grid`() {
        val anna = teacher("SCHA", 30, mathe)

        assertEquals(listOf(ReadinessIssue.TeacherLoadExceedsGrid(anna.id, excessPeriods = 2)), check(teachers = listOf(anna), teachingSlotsPerWeek = 28))
    }

    @Test
    fun `consistent data has no issues (acceptance 4_3)`() {
        val level6 = level("6", mathe to 4, chemie to 2)

        val issues = check(
            levels = listOf(level6),
            classes = classes(level6, "a", "b"),
            teachers = listOf(teacher("SCHA", 8, mathe), teacher("MEY", 4, chemie)),
        )

        assertEquals(emptyList(), issues)
    }

    @Test
    fun `a teacher fixation made invalid by later changes is reported with its reason (002 FR-013a)`() {
        val level6 = level("6", mathe to 4)
        val class6a = classes(level6, "a").single()
        val unqualified = teacher("OHNE", 10)
        val fixation = TeacherFixation(TeacherFixationId.new(), schoolId, class6a.id, mathe.id, unqualified.id, 0)

        val issues = ReadinessCheck.issuesOf(
            ReadinessInput(30, listOf(raumA), listOf(mathe), listOf(level6), listOf(class6a), listOf(unqualified, teacher("MATH", 10, mathe)), listOf(fixation)),
        )

        assertEquals(listOf(ReadinessIssue.InvalidTeacherFixation(fixation.id, FixationInvalidity.NOT_QUALIFIED)), issues.filterIsInstance<ReadinessIssue.InvalidTeacherFixation>())
    }
}
