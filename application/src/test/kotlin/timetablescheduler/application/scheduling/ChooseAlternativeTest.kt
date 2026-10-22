package timetablescheduler.application.scheduling

import timetablescheduler.application.error.ApplicationError
import timetablescheduler.application.error.Dependant
import timetablescheduler.application.error.DependantType
import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.domain.scheduling.DaySlot
import timetablescheduler.domain.scheduling.evaluation.HardRule
import timetablescheduler.domain.scheduling.generation.Alternative
import timetablescheduler.domain.scheduling.generation.Generation
import timetablescheduler.domain.scheduling.solution.CourseKey
import timetablescheduler.domain.scheduling.solution.TimetableCandidate
import timetablescheduler.domain.teacher.Unavailability
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.TUESDAY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChooseAlternativeTest {
    private val test = SchedulingTestSchool()
    private val world = test.world
    private val choose get() = test.scheduling.choose
    private val chosen get() = test.scheduling.getChosen

    private fun <T> UseCaseResult<T>.value(): T = assertIs<UseCaseResult.Success<T>>(this).value

    private fun UseCaseResult<*>.error(): ApplicationError = assertIs<UseCaseResult.Failure>(this).error

    private fun generationWith(vararg candidates: TimetableCandidate): Pair<Generation, List<Alternative>> {
        world.solver.candidates = { candidates.toList() }
        val generation =
            test.scheduling.start
                .execute(test.admin.id, test.school.id, StartRequest(timeLimitMinutes = 1))
                .value()
        return generation to world.generations.findAlternatives(generation.id)
    }

    private fun chooseOf(
        generation: Generation,
        alternative: Alternative,
        chosenVersion: Int? = null,
        confirmed: Boolean = false,
    ) = choose.execute(test.admin.id, test.school.id, generation.id, alternative.id, chosenVersion, confirmed)

    @Test
    fun `a complete alternative becomes the chosen timetable (US3 scenario 3)`() {
        val (generation, alternatives) = generationWith(test.complete())

        val result = assertIs<ChooseOutcome.Chosen>(chooseOf(generation, alternatives.single()).value())

        assertEquals(alternatives.single().id, result.chosen.alternativeId)
        assertEquals(alternatives.single().id, world.chosenTimetables.find(test.school.id)!!.alternativeId)
    }

    @Test
    fun `choosing another alternative replaces the choice and detects concurrent choices (FR-028)`() {
        val (firstGeneration, firstAlternatives) = generationWith(test.complete(test.schmidt))
        chooseOf(firstGeneration, firstAlternatives.single()).value()
        val (secondGeneration, secondAlternatives) = generationWith(test.complete(test.mueller))

        assertEquals(
            ApplicationError.VersionConflict,
            chooseOf(secondGeneration, secondAlternatives.single(), chosenVersion = null).error(),
        )
        chooseOf(secondGeneration, secondAlternatives.single(), chosenVersion = 0).value()

        assertEquals(secondAlternatives.single().id, world.chosenTimetables.find(test.school.id)!!.alternativeId)
        assertEquals(ApplicationError.VersionConflict, chooseOf(firstGeneration, firstAlternatives.single(), chosenVersion = 0).error())
    }

    @Test
    fun `unplaced lessons must be confirmed and stay visible as not placed (FR-021)`() {
        val (generation, alternatives) = generationWith(test.halfFilled())

        val confirmation = assertIs<ChooseOutcome.ConfirmationRequired>(chooseOf(generation, alternatives.single()).value())
        assertEquals(mapOf(CourseKey(test.class5a.id, test.math.id) to 1), confirmation.unplaced)
        assertNull(world.chosenTimetables.find(test.school.id))

        assertIs<ChooseOutcome.Chosen>(chooseOf(generation, alternatives.single(), confirmed = true).value())
        assertEquals(
            mapOf(CourseKey(test.class5a.id, test.math.id) to 1),
            chosen
                .execute(test.admin.id, test.school.id)
                .value()
                .chosen!!
                .unplaced,
        )
    }

    @Test
    fun `an alternative violating a hard constraint under current data cannot be chosen (FR-021, US3 scenario 7)`() {
        val (generation, alternatives) = generationWith(test.complete())
        world.markUnavailable(world.teachers.findById(test.schmidt.id)!!, Unavailability(MONDAY, test.first.id))

        val conflicts = assertIs<ApplicationError.AlternativeConflicts>(chooseOf(generation, alternatives.single()).error())

        assertEquals(listOf(HardRule.TEACHER_UNAVAILABLE), conflicts.violations.map { it.rule })
        assertNull(world.chosenTimetables.find(test.school.id))
    }

    @Test
    fun `the chosen timetable shows conflicts and curriculum changes but stays unchanged (FR-023, US3 scenarios 10 and 11)`() {
        val (generation, alternatives) = generationWith(test.complete())
        chooseOf(generation, alternatives.single()).value()
        world.markUnavailable(world.teachers.findById(test.schmidt.id)!!, Unavailability(TUESDAY, test.first.id))
        val level6 = world.classLevel(test.school.id, "6", test.math to 1)
        val class6a = world.schoolClass(level6, "a")

        val details = chosen.execute(test.admin.id, test.school.id).value()

        assertEquals(
            listOf(DaySlot(TUESDAY, test.first.id)),
            details.conflicts.flatMap { violation -> violation.lessons.map { it.daySlot } },
        )
        assertEquals(mapOf(CourseKey(class6a.id, test.math.id) to 1), details.drift!!.notPlaced)
        assertTrue(!details.fitsCurrentData)
        assertEquals(alternatives.single().lessons.toSet(), details.chosen!!.lessons.toSet())
    }

    @Test
    fun `deleting a teacher used in the chosen timetable is blocked (FR-016)`() {
        val (generation, alternatives) = generationWith(test.complete())
        chooseOf(generation, alternatives.single()).value()

        val blocked =
            assertIs<ApplicationError.DeletionBlocked>(
                world.useCases.teachers.delete
                    .execute(test.admin.id, test.school.id, test.schmidt.id, test.schmidt.version)
                    .error(),
            )

        assertEquals(listOf(Dependant(DependantType.CHOSEN_TIMETABLE, "")), blocked.visibleDependants)
    }

    @Test
    fun `a generation can start from the chosen timetable, keeping its reference (FR-006b)`() {
        assertEquals(
            ApplicationError.Invalid(
                listOf(
                    timetablescheduler.domain.validation.ValidationError
                        .InvalidReference(StartRequest.FIELD_START_FROM_CHOSEN),
                ),
            ),
            test.scheduling.start
                .execute(test.admin.id, test.school.id, StartRequest(startFromChosen = true))
                .error(),
        )
        val (generation, alternatives) = generationWith(test.complete())
        chooseOf(generation, alternatives.single()).value()
        world.solver.candidates = { listOf(test.complete(test.mueller)) }

        val next =
            test.scheduling.start
                .execute(test.admin.id, test.school.id, StartRequest(startFromChosen = true))
                .value()

        assertEquals(alternatives.single().id, next.referenceAlternativeId)
        assertEquals(
            alternatives.single().lessons.toSet(),
            world.solver.lastProblem!!
                .reference!!
                .toSet(),
        )
        assertEquals(
            2,
            world.generations
                .findAlternatives(next.id)
                .single()
                .changedLessons,
        )
    }
}
