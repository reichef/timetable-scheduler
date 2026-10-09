package timetablescheduler.application.scheduling

import timetablescheduler.application.error.UseCaseResult
import timetablescheduler.domain.scheduling.generation.Generation
import timetablescheduler.domain.scheduling.generation.GenerationStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RecoverInterruptedGenerationsTest {

    private val test = SchedulingTestSchool()
    private val world = test.world

    @Test
    fun `generations still running at start-up failed with the restart and keep their alternatives`() {
        world.executor.deferred = true
        world.solver.candidates = { listOf(test.complete()) }
        val generation = assertIs<UseCaseResult.Success<Generation>>(
            test.scheduling.start.execute(test.admin.id, test.school.id, StartRequest(timeLimitMinutes = 1, warningsConfirmed = true)),
        ).value
        val alternative = world.storeAlternative(generation, test.complete())

        val recovered = test.scheduling.recoverInterrupted.execute()

        assertEquals(1, recovered)
        assertEquals(GenerationStatus.FAILED, world.generations.findById(generation.id)!!.status)
        assertEquals(listOf(alternative), world.generations.findAlternatives(generation.id))
    }

    @Test
    fun `nothing to recover when no generation runs`() {
        assertEquals(0, test.scheduling.recoverInterrupted.execute())
    }
}
