package timetablescheduler.application.scheduling

import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.domain.scheduling.constraint.ClassScope
import timetablescheduler.domain.scheduling.constraint.ConstraintKind
import timetablescheduler.domain.scheduling.constraint.ConstraintRule
import timetablescheduler.domain.scheduling.constraint.ConstraintValidation
import timetablescheduler.domain.scheduling.constraint.SchedulingConstraint
import timetablescheduler.domain.scheduling.constraint.SoftWeight
import timetablescheduler.domain.validation.ValidationError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SchedulingConstraintUseCasesTest {

    private val test = SchedulingTestSchool()
    private val world = test.world
    private val constraints get() = world.useCases.constraints

    private fun <T> UseCaseResult<T>.value(): T = assertIs<UseCaseResult.Success<T>>(this).value

    private fun UseCaseResult<*>.error(): ApplicationError = assertIs<UseCaseResult.Failure>(this).error

    private val atMostTwo = ConstraintRule.ClassMaxPeriodsPerDay(ClassScope.SpecificClass(test.class5a.id), 2)

    private fun create(rule: ConstraintRule = atMostTwo, kind: ConstraintKind = ConstraintKind.HARD, weight: SoftWeight? = null) =
        constraints.create.execute(test.admin.id, test.school.id, ConstraintInput(kind, weight, rule))

    @Test
    fun `a planner creates, lists and changes a constraint (FR-012)`() {
        val created = create().value()

        assertEquals(listOf(created), constraints.list.execute(test.admin.id, test.school.id).value().constraints.filter { it.id == created.id })
        val changed = constraints.update.execute(
            test.admin.id, test.school.id, created.id, ConstraintInput(ConstraintKind.SOFT, SoftWeight.HIGH, atMostTwo.copy(limit = 1)), created.version,
        ).value()

        assertEquals(ConstraintKind.SOFT, changed.kind)
        assertEquals(SoftWeight.HIGH, changed.weight)
        assertEquals(1, (changed.rule as ConstraintRule.ClassMaxPeriodsPerDay).limit)
    }

    @Test
    fun `a soft constraint without weight gets medium (FR-014)`() {
        assertEquals(SoftWeight.MEDIUM, create(kind = ConstraintKind.SOFT).value().weight)
    }

    @Test
    fun `invalid parameters are rejected naming the problem (FR-015)`() {
        assertEquals(
            ApplicationError.Invalid(listOf(ValidationError.OutOfRange(ConstraintValidation.FIELD_LIMIT, 1, 2))),
            create(atMostTwo.copy(limit = 0)).error(),
        )
    }

    @Test
    fun `concurrent changes are detected (FR-028)`() {
        val created = create().value()
        constraints.update.execute(test.admin.id, test.school.id, created.id, ConstraintInput(ConstraintKind.HARD, null, atMostTwo), created.version).value()

        assertEquals(
            ApplicationError.VersionConflict,
            constraints.update.execute(test.admin.id, test.school.id, created.id, ConstraintInput(ConstraintKind.HARD, null, atMostTwo), created.version).error(),
        )
    }

    @Test
    fun `a constraint is switched off and on and deleted`() {
        val created = create().value()

        val off = constraints.setActive.execute(test.admin.id, test.school.id, created.id, false, created.version).value()
        assertFalse(off.active)
        assertTrue(constraints.setActive.execute(test.admin.id, test.school.id, created.id, true, off.version).value().active)
        constraints.delete.execute(test.admin.id, test.school.id, created.id, off.version + 1).value()
        assertNull(world.constraints.findById(created.id))
    }

    @Test
    fun `teachers may not manage constraints (FR-025)`() {
        assertEquals(ApplicationError.AccessDenied, constraints.list.execute(test.teacherAccount.id, test.school.id).error())
    }

    @Test
    fun `deleting a class, teacher or subject used by a constraint is blocked and lists it (FR-016)`() {
        create().value()
        constraints.create.execute(test.admin.id, test.school.id, ConstraintInput(ConstraintKind.SOFT, null, ConstraintRule.TeacherMaxPeriodsPerDay(test.schmidt.id, 2))).value()

        val classBlocked = assertIs<ApplicationError.DeletionBlocked>(
            world.useCases.curriculum.deleteSchoolClass.execute(test.admin.id, test.school.id, test.class5a.id, test.class5a.version).error(),
        )
        val teacherBlocked = assertIs<ApplicationError.DeletionBlocked>(
            world.useCases.teachers.delete.execute(test.admin.id, test.school.id, test.schmidt.id, test.schmidt.version).error(),
        )

        assertEquals(listOf("5a"), classBlocked.visibleDependants.map { it.displayName })
        assertEquals(listOf("Anna Schmidt (SCHA)"), teacherBlocked.visibleDependants.map { it.displayName })
    }

    @Test
    fun `new schools start with the two default constraints (FR-013c)`() {
        val installed = timetablescheduler.application.fakes.MasterDataWorld()
        installed.installation().execute(timetablescheduler.application.account.InitialSetup("Gymnasium Süd", "chef", "sehr-geheim-1")).value()
        val schoolId = installed.schools.findByNameIgnoreCase("Gymnasium Süd")!!.id

        val defaults: List<SchedulingConstraint> = installed.constraints.findBySchool(schoolId)

        assertEquals(2, defaults.size)
        assertTrue(defaults.all { it.kind == ConstraintKind.SOFT && it.weight == SoftWeight.MEDIUM })
    }
}
