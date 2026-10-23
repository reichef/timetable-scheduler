package timetablescheduler.application.scheduling

import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.domain.readiness.ReadinessIssue
import timetablescheduler.domain.scheduling.constraint.FixationInvalidity
import timetablescheduler.domain.validation.ValidationError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TeacherFixationUseCasesTest {
    private val test = SchedulingTestSchool()
    private val world = test.world
    private val fixations get() = world.useCases.fixations

    private fun <T> UseCaseResult<T>.value(): T = assertIs<UseCaseResult.Success<T>>(this).value

    private fun UseCaseResult<*>.error(): ApplicationError = assertIs<UseCaseResult.Failure>(this).error

    @Test
    fun `a planner fixes the teacher of a subject in a class (FR-013a)`() {
        val fixation = fixations.create.execute(test.admin.id, test.school.id, test.class5a.id, test.math.id, test.schmidt.id).value()

        assertEquals(listOf(fixation), world.fixations.findBySchool(test.school.id))
    }

    @Test
    fun `a second fixation of the same subject and class is rejected`() {
        fixations.create.execute(test.admin.id, test.school.id, test.class5a.id, test.math.id, test.schmidt.id).value()

        assertEquals(
            ApplicationError.Invalid(listOf(ValidationError.DuplicateValue(TeacherFixationUseCases.FIELD_COURSE))),
            fixations.create.execute(test.admin.id, test.school.id, test.class5a.id, test.math.id, test.mueller.id).error(),
        )
    }

    @Test
    fun `a teacher not qualified or without load left is rejected`() {
        val art = world.subject(test.school.id, "Kunst")
        world.classLevels.update(
            world.classLevels.findById(test.level5.id)!!.let {
                it.copy(
                    curriculum =
                        it.curriculum + timetablescheduler.domain.curriculum.CurriculumEntry(art.id, 2),
                )
            },
        )

        assertEquals(
            ApplicationError.Invalid(listOf(ValidationError.TeacherNotQualified)),
            fixations.create.execute(test.admin.id, test.school.id, test.class5a.id, art.id, test.schmidt.id).error(),
        )
    }

    @Test
    fun `the teacher of a fixation can be changed and the fixation deleted`() {
        val fixation = fixations.create.execute(test.admin.id, test.school.id, test.class5a.id, test.math.id, test.schmidt.id).value()

        val changed = fixations.update.execute(test.admin.id, test.school.id, fixation.id, test.mueller.id, fixation.version).value()
        fixations.delete.execute(test.admin.id, test.school.id, fixation.id, changed.version).value()

        assertEquals(test.mueller.id, changed.teacherId)
        assertEquals(emptyList(), world.fixations.findBySchool(test.school.id))
    }

    @Test
    fun `a fixation made invalid by a later change is a blocking readiness issue (G11)`() {
        val fixation = fixations.create.execute(test.admin.id, test.school.id, test.class5a.id, test.math.id, test.schmidt.id).value()
        world.teachers.update(world.teachers.findById(test.schmidt.id)!!.copy(qualifiedSubjectIds = emptySet()))

        val report =
            world.useCases.readiness
                .execute(test.admin.id, test.school.id)
                .value()

        assertEquals(
            listOf(ReadinessIssue.InvalidTeacherFixation(fixation.id, FixationInvalidity.NOT_QUALIFIED)),
            report.issues.filterIsInstance<ReadinessIssue.InvalidTeacherFixation>(),
        )
        assertIs<ApplicationError.GenerationBlocked>(
            test.scheduling.start.execute(test.admin.id, test.school.id, StartRequest(warningsConfirmed = true)).let {
                assertIs<UseCaseResult.Failure>(it).error
            },
        )
    }
}
