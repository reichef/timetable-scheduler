package timetablescheduler.application.readiness

import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.fakes.MasterDataWorld
import timetablescheduler.domain.account.Role
import timetablescheduler.domain.readiness.ReadinessIssue
import java.time.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GetSchoolReadinessTest {

    private val world = MasterDataWorld()
    private val nord = world.school("Gymnasium Nord")
    private val admin = world.planner("admin", nord.id)
    private val readiness get() = world.useCases.readiness

    @Test
    fun `report contains the issues of the school's data and the entries to name them`() {
        world.teachingDays(nord.id, DayOfWeek.MONDAY)
        world.slot(nord.id, "1. Stunde", "08:00", "08:45")
        val lab = world.feature(nord.id, "Chemieraum")
        val chemie = world.subject(nord.id, "Chemie", setOf(lab.id))
        val level = world.classLevel(nord.id, "6", chemie to 2)
        world.schoolClass(level, "a")
        val anna = world.teacher(nord.id, "Anna Schmidt", "SCHA", weeklyLoad = 2, qualifiedSubjects = setOf(chemie.id))

        val report = assertIs<UseCaseResult.Success<ReadinessReport>>(readiness.execute(admin.id, nord.id)).value

        assertEquals(
            listOf(
                ReadinessIssue.SubjectNoSuitableRoom(chemie.id, setOf(lab.id)),
                ReadinessIssue.LevelExceedsGrid(level.id, 1),
                ReadinessIssue.TeacherLoadExceedsGrid(anna.id, 1),
            ),
            report.issues,
        )
        assertEquals("Chemie", report.subjectName(chemie.id))
        assertEquals("Chemieraum", report.featureName(lab.id))
    }

    @Test
    fun `teacher account has no access`() {
        val teacher = world.account("lehrer1", Role.TEACHER, nord.id)

        assertEquals(UseCaseResult.Failure(ApplicationError.AccessDenied), readiness.execute(teacher.id, nord.id))
    }
}
