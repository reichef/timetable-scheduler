package timetablescheduler.bootstrap.testschool

import org.junit.jupiter.api.Tag
import org.testcontainers.postgresql.PostgreSQLContainer
import timetablescheduler.adapters.persistence.DatabaseFactory
import timetablescheduler.adapters.persistence.DatabaseSettings
import timetablescheduler.application.MasterDataUseCases
import timetablescheduler.application.account.InitialSetup
import timetablescheduler.application.scheduling.StartRequest
import timetablescheduler.bootstrap.ApplicationWiring
import timetablescheduler.domain.common.AccountId
import timetablescheduler.domain.common.RoomId
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.scheduling.generation.Alternative
import timetablescheduler.domain.scheduling.generation.Generation
import timetablescheduler.domain.scheduling.generation.GenerationGate
import timetablescheduler.domain.scheduling.generation.GenerationStatus
import timetablescheduler.domain.scheduling.pareto.Dominance
import timetablescheduler.domain.scheduling.solution.LessonAssignment
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Extended validation of feature 002 (T114): the fictitious test school of realistic size is seeded into a fresh
 * PostgreSQL database and one generation runs with the default time limit through the real wiring of the
 * application. Asserts the expected results of specs/002-timetable-generation/test-set-bw-gymnasium.md and prints the
 * recorded metrics. Runs about 10 minutes: `./gradlew :bootstrap:testSchoolTest`.
 */
@Tag("testschool")
class TestSchoolScenarioTest {

    private val testSet = BwGymnasiumTestSet
    private val setup = InitialSetup("Installationsschule", "testplaner", "testplaner-passwort-1")

    @Test
    fun `the test school gets valid, equally optimal timetables with the default time limit`() {
        PostgreSQLContainer("postgres:17").use { container ->
            container.start()

            val database = DatabaseFactory.connectAndMigrate(DatabaseSettings(container.jdbcUrl, container.username, container.password))
            val wiring = ApplicationWiring(database)
            wiring.installation.execute(setup).required("install")

            val useCases = wiring.useCases
            val (actor, schoolId) = plannerAndSchool(useCases, setup)
            val seeded = TestSchoolSeeder(useCases).seed(actor, schoolId)

            assertReadyForScheduling(useCases, actor, schoolId)

            val run = runGeneration(useCases, actor, schoolId)
            val alternatives = useCases.scheduling.get.execute(actor, schoolId, run.generation.id).required("load result").alternatives

            printMetrics(run, alternatives, seeded)
            assertExpectedResults(useCases, actor, run, alternatives, seeded)
        }
    }

    private fun assertReadyForScheduling(useCases: MasterDataUseCases, actor: AccountId, schoolId: SchoolId) {
        val report = useCases.readiness.execute(actor, schoolId).required("readiness")

        assertEquals(emptyList(), GenerationGate.classify(report.issues).blocking, "blocking readiness issues")
    }

    /** A finished generation with the times at which its first and its first useful alternative were stored. */
    private class GenerationRun(val generation: Generation, val firstAlternativeAfter: Duration?, val firstUsefulAfter: Duration?)

    /** Starts a generation with the default time limit and polls it until it ends, like the progress page does. */
    private fun runGeneration(useCases: MasterDataUseCases, actor: AccountId, schoolId: SchoolId): GenerationRun {
        val started = Instant.now()
        val generation = useCases.scheduling.start.execute(actor, schoolId, StartRequest(warningsConfirmed = true)).required("start generation")
        val deadline = started.plus(Duration.ofMinutes(Generation.DEFAULT_TIME_LIMIT_MINUTES + 3L))
        var firstAlternativeAfter: Duration? = null
        var firstUsefulAfter: Duration? = null

        while (true) {
            val details = useCases.scheduling.get.execute(actor, schoolId, generation.id).required("poll generation")
            val elapsed = Duration.between(started, Instant.now())

            if (firstAlternativeAfter == null && details.alternatives.isNotEmpty()) {
                firstAlternativeAfter = elapsed
            }
            if (firstUsefulAfter == null && details.alternatives.any { it.objectiveValues.classFill >= USEFUL_CLASS_FILL }) {
                firstUsefulAfter = elapsed
            }
            if (!details.generation.isRunning) {
                return GenerationRun(details.generation, firstAlternativeAfter, firstUsefulAfter)
            }

            check(Instant.now().isBefore(deadline)) { "generation did not end in time" }
            Thread.sleep(POLL_MILLIS)
        }
    }

    private fun assertExpectedResults(
        useCases: MasterDataUseCases,
        actor: AccountId,
        run: GenerationRun,
        alternatives: List<Alternative>,
        seeded: SeededTestSchool,
    ) {
        assertEquals(GenerationStatus.FINISHED, run.generation.status)
        assertTrue(run.firstAlternativeAfter != null && run.firstAlternativeAfter < Duration.ofMinutes(2), "first alternative after ${run.firstAlternativeAfter}")
        assertTrue(
            alternatives.size >= 3 || alternatives.any(::isPerfect),
            "alternatives: ${alternatives.size}, none fills every class without any violation (SC-001)",
        )

        alternatives.forEach { alternative ->
            val details = useCases.scheduling.getAlternative.execute(actor, seeded.schoolId, run.generation.id, alternative.id).required("load alternative")

            assertEquals(emptyList(), details.conflicts, "hard constraint violations re-checked against the stored data")
            assertLowerGradesEndBeforeNinthPeriod(alternative.lessons, seeded)
            assertRoomRules(alternative.lessons, seeded)
            assertFixationsAndUnavailability(alternative.lessons, seeded)
        }
        assertNoAlternativeDominated(alternatives)
    }

    /** Hard constraints 4–9: grades 5 and 6 have no lessons in the 9. and 10. Stunde. */
    private fun assertLowerGradesEndBeforeNinthPeriod(lessons: List<LessonAssignment>, seeded: SeededTestSchool) {
        val lowerGrades = testSet.classes.filter { it.grade <= 6 }.map { seeded.classIds.getValue(it.name) }.toSet()
        val lateSlots = setOf(seeded.slotIds.getValue("9. Stunde"), seeded.slotIds.getValue("10. Stunde"))

        assertTrue(lessons.none { it.classId in lowerGrades && it.daySlot.slotId in lateSlots }, "grades 5/6 in the 9./10. Stunde")
    }

    /** Subjects with a required feature take place in rooms with it, all others in the class's home room (FR-002a). */
    private fun assertRoomRules(lessons: List<LessonAssignment>, seeded: SeededTestSchool) {
        val homeRoomOf = testSet.classes.associate { seeded.classIds.getValue(it.name) to seeded.roomIds.getValue(it.homeRoom) }
        val roomsWithFeature = testSet.rooms.filter { it.feature != null }
            .groupBy({ it.feature!! }, { seeded.roomIds.getValue(it.identifier) })
        val allowedRooms = testSet.subjects.associate { subject ->
            val rooms: Set<RoomId>? = subject.requiredFeature?.let { roomsWithFeature.getValue(it).toSet() }

            seeded.subjectIds.getValue(subject.name) to rooms
        }

        lessons.forEach { lesson ->
            val expected = allowedRooms.getValue(lesson.subjectId) ?: setOf(homeRoomOf.getValue(lesson.classId))

            assertTrue(lesson.roomId in expected, "lesson in a room without the required feature or outside the home room")
        }
    }

    private fun assertFixationsAndUnavailability(lessons: List<LessonAssignment>, seeded: SeededTestSchool) {
        testSet.fixations.forEach { fixation ->
            val classId = seeded.classIds.getValue(fixation.className)
            val subjectId = seeded.subjectIds.getValue(fixation.subject)
            val teacherId = seeded.teacherIds.getValue(fixation.teacherCode)

            assertTrue(lessons.filter { it.classId == classId && it.subjectId == subjectId }.all { it.teacherId == teacherId }, "fixation not kept")
        }

        testSet.teachers.filter { it.unavailable.isNotEmpty() }.forEach { teacher ->
            val teacherId = seeded.teacherIds.getValue(teacher.code)
            val unavailable = teacher.unavailable.map(seeded::daySlot).toSet()

            assertTrue(lessons.none { it.teacherId == teacherId && it.daySlot in unavailable }, "lesson while the teacher is unavailable")
        }
    }

    /** SC-001: a single alternative suffices when it places every lesson and violates no constraint, hard or soft. */
    private fun isPerfect(alternative: Alternative): Boolean =
        alternative.unplacedPeriods() == 0 && alternative.softViolations.values.sum() == 0

    private fun assertNoAlternativeDominated(alternatives: List<Alternative>) {
        val vectors = alternatives.map { it.objectiveValues.asVector() }

        vectors.forEach { first ->
            vectors.forEach { second -> assertTrue(!Dominance.dominates(first, second), "a dominated alternative was kept (SC-003)") }
        }
    }

    /** The recorded metrics of the test set; they decide whether the specification needs refining (T115). */
    private fun printMetrics(run: GenerationRun, alternatives: List<Alternative>, seeded: SeededTestSchool) {
        val classNames = seeded.classIds.entries.associate { (name, id) -> id to name }
        val best = alternatives.maxByOrNull { it.objectiveValues.classFill }
        val useful = alternatives.count { it.objectiveValues.classFill >= USEFUL_CLASS_FILL }

        println("TESTSCHOOL status=${run.generation.status} alternatives=${alternatives.size} useful(>=${USEFUL_CLASS_FILL})=$useful")
        println("TESTSCHOOL firstAlternativeAfter=${run.firstAlternativeAfter?.toSeconds()}s firstUsefulAfter=${run.firstUsefulAfter?.toSeconds()}s")
        alternatives.forEachIndexed { index, alternative ->
            println("TESTSCHOOL alternative ${index + 1}: objectives=${alternative.objectiveValues.asVector()} unplaced=${alternative.unplacedPeriods()} softViolations=${alternative.softViolations.values.sum()}")
        }
        if (best != null) {
            val unplacedPerClass = best.unplaced.entries.groupBy({ classNames[it.key.classId] }, { it.value }).mapValues { it.value.sum() }

            println("TESTSCHOOL best class fill=${best.objectiveValues.classFill} unplaced per class=$unplacedPerClass")
        }
    }

    private companion object {
        const val USEFUL_CLASS_FILL = 0.9
        const val POLL_MILLIS = 2_000L
    }
}
