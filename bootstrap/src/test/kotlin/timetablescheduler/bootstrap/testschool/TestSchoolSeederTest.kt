package timetablescheduler.bootstrap.testschool

import timetablescheduler.application.account.InitialSetup
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.application.fakes.MasterDataWorld
import timetablescheduler.application.readiness.ReadinessReport
import timetablescheduler.domain.common.AccountId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.readiness.ReadinessIssue
import timetablescheduler.domain.scheduling.generation.GenerationGate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TestSchoolSeederTest {

    private val world = MasterDataWorld()

    private val actor: AccountId
    private val schoolId: SchoolId

    init {
        world.installation().execute(InitialSetup("Installationsschule", "testplaner", "testplaner-passwort-1"))

        actor = world.accounts.findByUsernameIgnoreCase("testplaner")!!.id
        schoolId = world.schools.findByNameIgnoreCase("Installationsschule")!!.id
    }

    private fun seed() = TestSchoolSeeder(world.useCases).seed(actor, schoolId)

    @Test
    fun `the whole test set is entered through the use cases and the school is ready for scheduling`() {
        val seeded = seed()

        assertEquals(BwGymnasiumTestSet.SCHOOL_NAME, world.schools.findById(schoolId)!!.name)
        assertEquals(31, world.rooms.findBySchool(schoolId).size)
        assertEquals(18, world.subjects.findBySchool(schoolId).size)
        assertEquals(18, seeded.classIds.size)
        assertEquals(32, world.teachers.findBySchool(schoolId).size)
        assertEquals(20, world.constraints.findBySchool(schoolId).size)
        assertEquals(3, world.fixations.findBySchool(schoolId).size)

        val report = assertIs<UseCaseResult.Success<ReadinessReport>>(world.useCases.readiness.execute(actor, schoolId)).value

        assertEquals(emptyList(), GenerationGate.classify(report.issues).blocking)
    }

    @Test
    fun `home rooms, qualifications and unavailability are stored as described`() {
        val seeded = seed()

        val class5a = world.schoolClasses.findById(seeded.classIds.getValue("5a"))!!
        val t05 = world.teachers.findById(seeded.teacherIds.getValue("T05"))!!
        val t10 = world.teachers.findById(seeded.teacherIds.getValue("T10"))!!

        assertEquals(seeded.roomIds.getValue("TEST-R101"), class5a.homeRoomId)
        assertEquals(10, t05.unavailability.size)
        assertEquals(3, t10.qualifiedSubjectIds.size)
    }

    @Test
    fun `a school that already has master data is never seeded, so test data cannot mix with real data`() {
        world.room(schoolId, "R-001")

        assertFailsWith<TestSchoolSeedingRefused> { seed() }

        assertEquals(1, world.rooms.findBySchool(schoolId).size)
        assertTrue(world.teachers.findBySchool(schoolId).isEmpty())
    }

    @Test
    fun `the teacher-shortage variant seeds and reports its capacity shortfalls as warnings only`() {
        val seeded = TestSchoolSeeder(world.useCases, UnderstaffedTestSet).seed(actor, schoolId)

        val report = assertIs<UseCaseResult.Success<ReadinessReport>>(world.useCases.readiness.execute(actor, schoolId)).value
        val gate = GenerationGate.classify(report.issues)
        val shortSubjects = gate.warnings.filterIsInstance<ReadinessIssue.SubjectTeacherCapacityShortfall>()
            .associate { it.subjectId to it.shortfallPeriods }

        assertEquals(UnderstaffedTestSet.schoolName, world.schools.findById(schoolId)!!.name)
        assertEquals(28, world.teachers.findBySchool(schoolId).size)
        assertEquals(emptyList(), gate.blocking)
        listOf(BwGymnasiumTestSet.MUSIC, BwGymnasiumTestSet.ART, BwGymnasiumTestSet.SPORT).forEach { subject ->
            assertTrue(seeded.subjectIds.getValue(subject) in shortSubjects, "no shortfall warning for $subject")
        }
    }
}
