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
import timetablescheduler.domain.scheduling.generation.PlacementShare
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
 * application — once for the test school and once for its teacher-shortage variant. Asserts the expected results of
 * specs/002-timetable-generation/test-set-bw-gymnasium.md and prints the recorded metrics. Runs about 10 minutes per
 * scenario: `./gradlew :bootstrap:testSchoolTest`.
 */
@Tag("testschool")
class TestSchoolScenarioTest {
    private val setup = InitialSetup("Installationsschule", "testplaner", "testplaner-passwort-1")

    @Test
    fun `the test school gets valid, equally optimal timetables with the default time limit`() {
        val result = Scenario(BwGymnasiumTestSet).run()

        assertTrue(result.alternatives.isNotEmpty())
    }

    /** Variant "teacher shortage": at least 17 lessons can never be placed (Musik 9, Bildende Kunst 6, Sport 2). */
    @Test
    fun `the teacher-shortage variant leaves lessons unplaced but keeps every rule`() {
        val result = Scenario(UnderstaffedTestSet).run()

        result.alternatives.forEach { alternative ->
            assertTrue(alternative.unplacedPeriods() >= MINIMUM_UNPLACED_IN_SHORTAGE, "unplaced: ${alternative.unplacedPeriods()}")
            assertTrue(alternative.unplaced.isNotEmpty(), "unplaced lessons are listed per class")
            assertNoTeacherAboveLoad(alternative.lessons, result.seeded)
        }
    }

    /** Independent check of the weekly loads (001 FR-018): no teacher teaches more lessons than the load allows. */
    private fun assertNoTeacherAboveLoad(
        lessons: List<LessonAssignment>,
        seeded: SeededTestSchool,
    ) {
        val loadOf = UnderstaffedTestSet.teachers.associate { seeded.teacherIds.getValue(it.code) to it.weeklyLoad }

        lessons.groupingBy { it.teacherId }.eachCount().forEach { (teacherId, lessonCount) ->
            assertTrue(lessonCount <= loadOf.getValue(teacherId), "a teacher exceeds the weekly load")
        }
    }

    /**
     * A finished generation with the time its first alternative was stored and, per poll, the elapsed time and the most
     * lessons any stored alternative placed — to find when the first alternative meeting the final share appeared.
     */
    private class GenerationRun(
        val generation: Generation,
        val firstAlternativeAfter: Duration?,
        val bestPlacedOverTime: List<Pair<Duration, Int>>,
    ) {
        /** SC-001: when the first alternative appeared that meets the minimum share of the final best one (FR-007a). */
        fun firstMeetingShareAfter(share: PlacementShare): Duration? =
            bestPlacedOverTime.firstOrNull { (_, placed) -> share.admits(placed) }?.first
    }

    /** The seeded test school and the alternatives of its finished generation. */
    private class ScenarioResult(
        val seeded: SeededTestSchool,
        val alternatives: List<Alternative>,
    )

    /** One scenario on [testSet]: seed it into a fresh database, run one generation and check the common rules. */
    private inner class Scenario(
        private val testSet: TestSchoolData,
    ) {
        fun run(): ScenarioResult =
            PostgreSQLContainer("postgres:17").use { container ->
                container.start()

                val database =
                    DatabaseFactory.connectAndMigrate(
                        DatabaseSettings(container.jdbcUrl, container.username, container.password),
                    )

                try {
                    runOn(database)
                } finally {
                    // Closed before the container stops, so the pool does not keep reconnecting to it.
                    database.close()
                }
            }

        private fun runOn(database: DatabaseFactory): ScenarioResult {
            val wiring = ApplicationWiring(database)
            wiring.installation.execute(setup).required("install")

            val useCases = wiring.useCases
            val (actor, schoolId) = plannerAndSchool(useCases, setup)
            val seeded = TestSchoolSeeder(useCases, testSet).seed(actor, schoolId)

            assertReadyForScheduling(useCases, actor, schoolId)

            val run = runGeneration(useCases, actor, schoolId)
            val alternatives =
                useCases.scheduling.get
                    .execute(actor, schoolId, run.generation.id)
                    .required("load result")
                    .alternatives

            printMetrics(run, alternatives, seeded)
            assertExpectedResults(useCases, actor, run, alternatives, seeded)

            return ScenarioResult(seeded, alternatives)
        }

        private fun assertReadyForScheduling(
            useCases: MasterDataUseCases,
            actor: AccountId,
            schoolId: SchoolId,
        ) {
            val report = useCases.readiness.execute(actor, schoolId).required("readiness")

            assertEquals(emptyList(), GenerationGate.classify(report.issues).blocking, "blocking readiness issues")
        }

        /** Starts a generation with the default time limit and polls it until it ends, like the progress page does. */
        private fun runGeneration(
            useCases: MasterDataUseCases,
            actor: AccountId,
            schoolId: SchoolId,
        ): GenerationRun {
            val started = Instant.now()
            val generation =
                useCases.scheduling.start
                    .execute(
                        actor,
                        schoolId,
                        StartRequest(warningsConfirmed = true),
                    ).required("start generation")
            val deadline = started.plus(Duration.ofMinutes(Generation.DEFAULT_TIME_LIMIT_MINUTES + 3L))
            var firstAlternativeAfter: Duration? = null
            val bestPlacedOverTime = mutableListOf<Pair<Duration, Int>>()

            while (true) {
                val details =
                    useCases.scheduling.get
                        .execute(actor, schoolId, generation.id)
                        .required("poll generation")
                val elapsed = Duration.between(started, Instant.now())

                if (firstAlternativeAfter == null && details.alternatives.isNotEmpty()) {
                    firstAlternativeAfter = elapsed
                }
                details.alternatives.maxOfOrNull { it.lessons.size }?.let { bestPlacedOverTime += elapsed to it }
                if (!details.generation.isRunning) {
                    return GenerationRun(details.generation, firstAlternativeAfter, bestPlacedOverTime)
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
            val share = finalShare(run, alternatives)
            val firstMeetingShare = run.firstMeetingShareAfter(share)

            assertEquals(GenerationStatus.FINISHED, run.generation.status)
            assertTrue(
                firstMeetingShare != null && firstMeetingShare < Duration.ofMinutes(2),
                "first alternative meeting the share after $firstMeetingShare (SC-001)",
            )
            alternatives.forEach { alternative ->
                assertTrue(share.admits(alternative.lessons.size), "alternative below the minimum share or without lessons (FR-007a)")
            }

            alternatives.forEach { alternative ->
                val details =
                    useCases.scheduling.getAlternative
                        .execute(
                            actor,
                            seeded.schoolId,
                            run.generation.id,
                            alternative.id,
                        ).required("load alternative")

                assertEquals(emptyList(), details.conflicts, "hard constraint violations re-checked against the stored data")
                assertLowerGradesEndBeforeNinthPeriod(alternative.lessons, seeded)
                assertRoomRules(alternative.lessons, seeded)
                assertFixationsAndUnavailability(alternative.lessons, seeded)
            }
            assertNoAlternativeDominated(alternatives)
        }

        /** The minimum share of the generation, measured against its best alternative. */
        private fun finalShare(
            run: GenerationRun,
            alternatives: List<Alternative>,
        ): PlacementShare =
            PlacementShare(run.generation.minimumPlacedShare).apply { record(alternatives.maxOfOrNull { it.lessons.size } ?: 0) }

        /** Hard constraints 4–9: grades 5 and 6 have no lessons in the 9. and 10. Stunde. */
        private fun assertLowerGradesEndBeforeNinthPeriod(
            lessons: List<LessonAssignment>,
            seeded: SeededTestSchool,
        ) {
            val lowerGrades =
                testSet.classes
                    .filter { it.grade <= 6 }
                    .map { seeded.classIds.getValue(it.name) }
                    .toSet()
            val lateSlots = setOf(seeded.slotIds.getValue("9. Stunde"), seeded.slotIds.getValue("10. Stunde"))

            assertTrue(lessons.none { it.classId in lowerGrades && it.daySlot.slotId in lateSlots }, "grades 5/6 in the 9./10. Stunde")
        }

        /** Subjects with a required feature take place in rooms with it, all others in the class's home room (FR-002a). */
        private fun assertRoomRules(
            lessons: List<LessonAssignment>,
            seeded: SeededTestSchool,
        ) {
            val homeRoomOf = testSet.classes.associate { seeded.classIds.getValue(it.name) to seeded.roomIds.getValue(it.homeRoom) }
            val roomsWithFeature =
                testSet.rooms
                    .filter { it.feature != null }
                    .groupBy({ it.feature!! }, { seeded.roomIds.getValue(it.identifier) })
            val allowedRooms =
                testSet.subjects.associate { subject ->
                    val rooms: Set<RoomId>? = subject.requiredFeature?.let { roomsWithFeature.getValue(it).toSet() }

                    seeded.subjectIds.getValue(subject.name) to rooms
                }

            lessons.forEach { lesson ->
                val expected = allowedRooms.getValue(lesson.subjectId) ?: setOf(homeRoomOf.getValue(lesson.classId))

                assertTrue(lesson.roomId in expected, "lesson in a room without the required feature or outside the home room")
            }
        }

        private fun assertFixationsAndUnavailability(
            lessons: List<LessonAssignment>,
            seeded: SeededTestSchool,
        ) {
            testSet.fixations.forEach { fixation ->
                val classId = seeded.classIds.getValue(fixation.className)
                val subjectId = seeded.subjectIds.getValue(fixation.subject)
                val teacherId = seeded.teacherIds.getValue(fixation.teacherCode)

                assertTrue(
                    lessons.filter { it.classId == classId && it.subjectId == subjectId }.all { it.teacherId == teacherId },
                    "fixation not kept",
                )
            }

            testSet.teachers.filter { it.unavailable.isNotEmpty() }.forEach { teacher ->
                val teacherId = seeded.teacherIds.getValue(teacher.code)
                val unavailable = teacher.unavailable.map(seeded::daySlot).toSet()

                assertTrue(
                    lessons.none { it.teacherId == teacherId && it.daySlot in unavailable },
                    "lesson while the teacher is unavailable",
                )
            }
        }

        private fun assertNoAlternativeDominated(alternatives: List<Alternative>) {
            val vectors = alternatives.map { it.objectiveValues.asVector() }

            vectors.forEach { first ->
                vectors.forEach { second -> assertTrue(!Dominance.dominates(first, second), "a dominated alternative was kept (SC-003)") }
            }
        }

        /** The recorded metrics of the test set; they decide whether the specification needs refining (T115). */
        private fun printMetrics(
            run: GenerationRun,
            alternatives: List<Alternative>,
            seeded: SeededTestSchool,
        ) {
            val classNames = seeded.classIds.entries.associate { (name, id) -> id to name }
            val best = alternatives.maxByOrNull { it.objectiveValues.classFill }
            val share = finalShare(run, alternatives)

            println(
                "TESTSCHOOL [${testSet.schoolName}] status=${run.generation.status} alternatives=${alternatives.size} minimumShare=${run.generation.minimumPlacedShare}% bestPlaced=${share.bestPlaced}",
            )
            println(
                "TESTSCHOOL [${testSet.schoolName}] firstAlternativeAfter=${run.firstAlternativeAfter?.toSeconds()}s firstMeetingShareAfter=${run.firstMeetingShareAfter(
                    share,
                )?.toSeconds()}s",
            )
            alternatives.forEachIndexed { index, alternative ->
                println(
                    "TESTSCHOOL [${testSet.schoolName}] alternative ${index + 1}: objectives=${alternative.objectiveValues.asVector()} unplaced=${alternative.unplacedPeriods()} softViolations=${alternative.softViolations.values.sum()}",
                )
            }
            if (best != null) {
                val unplacedPerClass =
                    best.unplaced.entries
                        .groupBy(
                            { classNames[it.key.classId] },
                            { it.value },
                        ).mapValues { it.value.sum() }

                val subjectNames = seeded.subjectIds.entries.associate { (name, id) -> id to name }
                val unplacedPerSubject =
                    best.unplaced.entries
                        .groupBy(
                            { subjectNames[it.key.subjectId] },
                            { it.value },
                        ).mapValues { it.value.sum() }

                println(
                    "TESTSCHOOL [${testSet.schoolName}] best class fill=${best.objectiveValues.classFill} unplaced per class=$unplacedPerClass",
                )
                println("TESTSCHOOL [${testSet.schoolName}] best unplaced per subject=$unplacedPerSubject")
            }
        }
    }

    private companion object {
        const val MINIMUM_UNPLACED_IN_SHORTAGE = 17
        const val POLL_MILLIS = 2_000L
    }
}
