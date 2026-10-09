package timetablescheduler.application.curriculum

import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.error.Dependant
import timetablescheduler.application.error.DependantType
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.fakes.MasterDataWorld
import timetablescheduler.domain.account.Role
import timetablescheduler.domain.common.ClassLevelId
import timetablescheduler.domain.curriculum.ClassLevel
import timetablescheduler.domain.curriculum.CurriculumEntry
import timetablescheduler.domain.curriculum.SchoolClass
import timetablescheduler.domain.curriculum.Subject
import timetablescheduler.domain.readiness.ReadinessIssue
import timetablescheduler.domain.validation.ValidationError
import java.time.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class CurriculumUseCasesTest {

    private val world = MasterDataWorld()
    private val nord = world.school("Gymnasium Nord")
    private val admin = world.planner("admin", nord.id)
    private val curriculum get() = world.useCases.curriculum

    private fun <T> UseCaseResult<T>.value(): T = assertIs<UseCaseResult.Success<T>>(this).value

    private fun UseCaseResult<*>.error(): ApplicationError = assertIs<UseCaseResult.Failure>(this).error

    private fun duplicate(field: String) = ApplicationError.Invalid(listOf(ValidationError.DuplicateValue(field)))

    private fun blockedBy(vararg dependants: Dependant) = ApplicationError.DeletionBlocked(dependants.toList(), hiddenDependantCount = 0)

    @Test
    fun `subject with required feature is listed with it`() {
        val lab = world.feature(nord.id, "Chemieraum")

        val chemie = curriculum.createSubject.execute(admin.id, nord.id, "Chemie", setOf(lab.id)).value()

        val overview = curriculum.listSubjects.execute(admin.id, nord.id).value()
        assertEquals(listOf(chemie), overview.subjects)
        assertEquals(setOf(lab.id), overview.subjects.single().requiredFeatureIds)
    }

    @Test
    fun `subject name is unique per school case-insensitively`() {
        world.subject(nord.id, "Mathematik")

        assertEquals(duplicate(Subject.FIELD_NAME), curriculum.createSubject.execute(admin.id, nord.id, "MATHEMATIK", emptySet()).error())
    }

    @Test
    fun `updating a subject checks the version`() {
        val mathe = world.subject(nord.id, "Mathematik")

        curriculum.updateSubject.execute(admin.id, nord.id, mathe.id, "Mathe", emptySet(), mathe.version).value()

        assertEquals(ApplicationError.VersionConflict, curriculum.updateSubject.execute(admin.id, nord.id, mathe.id, "M", emptySet(), mathe.version).error())
    }

    @Test
    fun `class level shows the weekly total of its curriculum`() {
        val mathe = world.subject(nord.id, "Mathematik")
        val deutsch = world.subject(nord.id, "Deutsch")
        val level = curriculum.createClassLevel.execute(admin.id, nord.id, "6").value()

        val saved = curriculum.updateClassLevel.execute(
            admin.id, nord.id, level.id, "6", listOf(CurriculumEntry(mathe.id, 4), CurriculumEntry(deutsch.id, 4)), level.version,
        ).value()

        assertEquals(8, saved.level.weeklyPeriods())
    }

    @Test
    fun `level identifier is unique per school`() {
        world.classLevel(nord.id, "6")

        assertEquals(duplicate(ClassLevel.FIELD_IDENTIFIER), curriculum.createClassLevel.execute(admin.id, nord.id, " 6 ").error())
    }

    @Test
    fun `over-full level is saved with a grid warning`() {
        world.teachingDays(nord.id, DayOfWeek.MONDAY)
        world.slot(nord.id, "1. Stunde", "08:00", "08:45")
        val mathe = world.subject(nord.id, "Mathematik")
        val level = world.classLevel(nord.id, "6")

        val saved = curriculum.updateClassLevel.execute(admin.id, nord.id, level.id, "6", listOf(CurriculumEntry(mathe.id, 3)), level.version).value()

        assertEquals(ReadinessIssue.LevelExceedsGrid(level.id, excessPeriods = 2), saved.warning)
        assertEquals(3, world.classLevels.findById(level.id)!!.weeklyPeriods())
    }

    @Test
    fun `level fitting into the grid has no warning`() {
        world.teachingDays(nord.id, DayOfWeek.MONDAY)
        world.slot(nord.id, "1. Stunde", "08:00", "08:45")
        val mathe = world.subject(nord.id, "Mathematik")
        val level = world.classLevel(nord.id, "6")

        assertNull(curriculum.updateClassLevel.execute(admin.id, nord.id, level.id, "6", listOf(CurriculumEntry(mathe.id, 1)), level.version).value().warning)
    }

    @Test
    fun `next suffix is proposed and the class is named by level and suffix`() {
        val level = world.classLevel(nord.id, "6")
        world.schoolClass(level, "a")
        world.schoolClass(level, "b")

        val proposal = curriculum.proposeClassSuffix.execute(admin.id, nord.id, level.id).value()
        val created = curriculum.createSchoolClass.execute(admin.id, nord.id, level.id, proposal.suffix!!, SchoolClass.DEFAULT_PRIORITY_WEIGHT).value()

        assertEquals("c", proposal.suffix)
        assertEquals("6c", created.displayName(level))
    }

    @Test
    fun `duplicate class is rejected`() {
        val level = world.classLevel(nord.id, "6")
        world.schoolClass(level, "a")

        assertEquals(duplicate(SchoolClass.FIELD_SUFFIX), curriculum.createSchoolClass.execute(admin.id, nord.id, level.id, "A", 10).error())
    }

    @Test
    fun `class shows the curriculum of its level including later changes`() {
        val mathe = world.subject(nord.id, "Mathematik")
        val level = world.classLevel(nord.id, "6", mathe to 4)
        val class6a = world.schoolClass(level, "a")

        curriculum.updateClassLevel.execute(admin.id, nord.id, level.id, "6", listOf(CurriculumEntry(mathe.id, 5)), level.version).value()

        val details = curriculum.getSchoolClass.execute(admin.id, nord.id, class6a.id).value()
        assertEquals(listOf(CurriculumEntry(mathe.id, 5)), details.curriculum)
        assertEquals("6a", details.displayName)
    }

    @Test
    fun `deleting a subject used in a curriculum is blocked naming the level`() {
        val mathe = world.subject(nord.id, "Mathematik")
        world.classLevel(nord.id, "6", mathe to 4)

        assertEquals(
            blockedBy(Dependant(DependantType.CLASS_LEVEL, "6")),
            curriculum.deleteSubject.execute(admin.id, nord.id, mathe.id, mathe.version).error(),
        )
    }

    @Test
    fun `deleting a level with classes is blocked naming the classes`() {
        val level = world.classLevel(nord.id, "6")
        world.schoolClass(level, "a")
        world.schoolClass(level, "b")

        assertEquals(
            blockedBy(Dependant(DependantType.SCHOOL_CLASS, "6a"), Dependant(DependantType.SCHOOL_CLASS, "6b")),
            curriculum.deleteClassLevel.execute(admin.id, nord.id, level.id, level.version).error(),
        )
    }

    @Test
    fun `deleting a room feature required by a subject is blocked naming the subject`() {
        val lab = world.feature(nord.id, "Chemieraum")
        world.subject(nord.id, "Chemie", setOf(lab.id))

        assertEquals(
            blockedBy(Dependant(DependantType.SUBJECT, "Chemie")),
            world.roomUseCases.deleteFeature.execute(admin.id, nord.id, lab.id).error(),
        )
    }

    @Test
    fun `unused entries can be deleted`() {
        val mathe = world.subject(nord.id, "Mathematik")
        val level = world.classLevel(nord.id, "6")
        val class6a = world.schoolClass(level, "a")

        curriculum.deleteSchoolClass.execute(admin.id, nord.id, class6a.id, class6a.version).value()
        curriculum.deleteClassLevel.execute(admin.id, nord.id, level.id, level.version).value()
        curriculum.deleteSubject.execute(admin.id, nord.id, mathe.id, mathe.version).value()

        assertEquals(emptyList(), curriculum.listSubjects.execute(admin.id, nord.id).value().subjects)
        assertEquals(emptyList(), curriculum.listClassLevels.execute(admin.id, nord.id).value().levels)
    }

    @Test
    fun `entries of an unknown level or foreign school are not found`() {
        val foreignSchool = world.school("Andere Schule")
        val foreignLevel = world.classLevel(foreignSchool.id, "6")

        assertEquals(ApplicationError.NotFound, curriculum.getClassLevel.execute(admin.id, nord.id, foreignLevel.id).error())
        assertEquals(ApplicationError.NotFound, curriculum.getClassLevel.execute(admin.id, nord.id, ClassLevelId.new()).error())
        assertEquals(ApplicationError.NotFound, curriculum.listSubjects.execute(admin.id, foreignSchool.id).error())
    }

    @Test
    fun `teacher account has no access`() {
        val teacher = world.account("lehrer1", Role.TEACHER, nord.id)

        assertEquals(ApplicationError.AccessDenied, curriculum.listSubjects.execute(teacher.id, nord.id).error())
        assertEquals(ApplicationError.AccessDenied, curriculum.createClassLevel.execute(teacher.id, nord.id, "7").error())
    }
}
