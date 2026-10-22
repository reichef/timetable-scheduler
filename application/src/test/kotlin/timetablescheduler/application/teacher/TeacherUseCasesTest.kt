package timetablescheduler.application.teacher

import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.error.Dependant
import timetablescheduler.application.error.DependantType
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.fakes.MasterDataWorld
import timetablescheduler.domain.account.Role
import timetablescheduler.domain.common.TeacherId
import timetablescheduler.domain.teacher.Teacher
import timetablescheduler.domain.teacher.Unavailability
import timetablescheduler.domain.validation.ValidationError
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class TeacherUseCasesTest {
    private val world = MasterDataWorld()
    private val nord = world.school("Gymnasium Nord")
    private val admin = world.planner("admin", nord.id)
    private val teachers get() = world.useCases.teachers

    /** Monday–Friday, 8 teaching periods, 7th and 8th excluded on Friday: 38 teaching slots per week. */
    private val periods =
        run {
            world.teachingDays(nord.id, *DayOfWeek.entries.filter { it <= FRIDAY }.toTypedArray())
            val slots = (1..8).map { world.slot(nord.id, "$it. Stunde", "%02d:00".format(7 + it), "%02d:45".format(7 + it)) }
            world.excludeOn(nord.id, slots[6], FRIDAY)
            world.excludeOn(nord.id, slots[7], FRIDAY)
            slots
        }

    private fun <T> UseCaseResult<T>.value(): T = assertIs<UseCaseResult.Success<T>>(this).value

    private fun UseCaseResult<*>.error(): ApplicationError = assertIs<UseCaseResult.Failure>(this).error

    @Test
    fun `teacher is created with load and listed`() {
        val anna = teachers.create.execute(admin.id, nord.id, "Anna Schmidt", "SCHA", 24).value()

        assertEquals(listOf(anna), teachers.list.execute(admin.id, nord.id).value())
        assertEquals(nord.id, anna.schoolId)
    }

    @Test
    fun `short code is unique in the installation`() {
        world.teacher(nord.id, "Anna Schmidt", "SCHA")

        assertEquals(
            ApplicationError.Invalid(listOf(ValidationError.DuplicateValue(Teacher.FIELD_SHORT_CODE))),
            teachers.create.execute(admin.id, nord.id, "Anna Schmidt", "scha", 10).error(),
        )
        teachers.create.execute(admin.id, nord.id, "Anna Schmidt", "SCHM", 10).value()
    }

    @Test
    fun `load above the 38 teaching slots of the school is rejected`() {
        assertEquals(
            ApplicationError.Invalid(listOf(ValidationError.OutOfRange(Teacher.FIELD_WEEKLY_LOAD, 0, 38))),
            teachers.create.execute(admin.id, nord.id, "Anna Schmidt", "SCHA", 40).error(),
        )
        teachers.create.execute(admin.id, nord.id, "Anna Schmidt", "SCHA", 38).value()
    }

    @Test
    fun `qualifications and unavailability are saved with version check`() {
        val mathe = world.subject(nord.id, "Mathematik")
        val anna = world.teacher(nord.id, "Anna Schmidt", "SCHA")
        val friday6 = Unavailability(FRIDAY, periods[5].id)

        val qualified = teachers.updateQualifications.execute(admin.id, nord.id, anna.id, setOf(mathe.id), anna.version).value()
        val blocked = teachers.updateUnavailability.execute(admin.id, nord.id, anna.id, setOf(friday6), qualified.version).value()
        val stale = teachers.updateUnavailability.execute(admin.id, nord.id, anna.id, emptySet(), anna.version)

        assertEquals(setOf(mathe.id), blocked.qualifiedSubjectIds)
        assertEquals(setOf(friday6), world.teachers.findById(anna.id)!!.unavailability)
        assertEquals(ApplicationError.VersionConflict, stale.error())
    }

    @Test
    fun `general data update checks short code and load`() {
        val anna = world.teacher(nord.id, "Anna Schmidt", "SCHA")
        world.teacher(nord.id, "Max Meyer", "MEY")

        assertEquals(
            ApplicationError.Invalid(listOf(ValidationError.DuplicateValue(Teacher.FIELD_SHORT_CODE))),
            teachers.update.execute(admin.id, nord.id, anna.id, "Anna Schmidt", "MEY", 10, anna.version).error(),
        )
        val updated = teachers.update.execute(admin.id, nord.id, anna.id, "Anna Schmidt", "SCHA", 20, anna.version).value()
        assertEquals(20, updated.weeklyLoad)
    }

    @Test
    fun `teacher of a foreign school id is not found`() {
        val other = world.school("Andere Schule")
        val foreignTeacher = world.teacher(other.id, "Max Meyer", "MEY")

        assertEquals(ApplicationError.NotFound, teachers.get.execute(admin.id, nord.id, foreignTeacher.id).error())
        assertEquals(ApplicationError.NotFound, teachers.get.execute(admin.id, nord.id, TeacherId.new()).error())
    }

    @Test
    fun `deleting a subject a teacher is qualified for is blocked naming the teacher`() {
        val mathe = world.subject(nord.id, "Mathematik")
        world.teacher(nord.id, "Anna Schmidt", "SCHA", qualifiedSubjects = setOf(mathe.id))

        assertEquals(
            ApplicationError.DeletionBlocked(listOf(Dependant(DependantType.TEACHER, "Anna Schmidt (SCHA)")), hiddenDependantCount = 0),
            world.useCases.curriculum.deleteSubject
                .execute(admin.id, nord.id, mathe.id, mathe.version)
                .error(),
        )
    }

    @Test
    fun `deleting a time slot removes the unavailabilities of that slot and reports their count`() {
        val anna = world.teacher(nord.id, "Anna Schmidt", "SCHA")
        val max = world.teacher(nord.id, "Max Meyer", "MEY")
        val sixth = periods[5]
        world.markUnavailable(anna, Unavailability(FRIDAY, sixth.id), Unavailability(FRIDAY, periods[0].id))
        world.markUnavailable(max, Unavailability(DayOfWeek.MONDAY, sixth.id))
        val schoolVersion = world.schools.findById(nord.id)!!.version

        val preview =
            world.timeGridUseCases.previewDeletion
                .execute(admin.id, nord.id, sixth.id)
                .value()
        val result =
            world.timeGridUseCases.deleteSlot
                .execute(admin.id, nord.id, sixth.id, schoolVersion)
                .value()

        assertEquals(2, preview.unavailabilityCount)
        assertEquals(2, result.removedUnavailabilities)
        assertEquals(setOf(Unavailability(FRIDAY, periods[0].id)), world.teachers.findById(anna.id)!!.unavailability)
        assertEquals(emptySet(), world.teachers.findById(max.id)!!.unavailability)
    }

    @Test
    fun `deleting a teacher removes the teacher with qualifications and unavailability`() {
        val mathe = world.subject(nord.id, "Mathematik")
        val anna = world.teacher(nord.id, "Anna Schmidt", "SCHA", qualifiedSubjects = setOf(mathe.id))
        world.markUnavailable(anna, Unavailability(FRIDAY, periods[5].id))

        teachers.delete.execute(admin.id, nord.id, anna.id, world.teachers.findById(anna.id)!!.version).value()

        assertNull(world.teachers.findById(anna.id))
        assertEquals(0, world.timeSlotUsage.countUnavailabilities(periods[5].id))
        world.useCases.curriculum.deleteSubject
            .execute(admin.id, nord.id, mathe.id, mathe.version)
            .value()
    }

    @Test
    fun `teacher details offer subjects and the time grid`() {
        val mathe = world.subject(nord.id, "Mathematik")
        val anna = world.teacher(nord.id, "Anna Schmidt", "SCHA")

        val details = teachers.get.execute(admin.id, nord.id, anna.id).value()

        assertEquals(listOf(mathe), details.subjects)
        assertEquals(38, details.timeGrid.teachingSlotsPerWeek())
    }

    @Test
    fun `teacher account has no access`() {
        val teacherAccount = world.account("lehrer1", Role.TEACHER, nord.id)

        assertEquals(ApplicationError.AccessDenied, teachers.list.execute(teacherAccount.id, nord.id).error())
    }
}
