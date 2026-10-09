package timetablescheduler.application.fakes

import timetablescheduler.application.port.GenerationEventLog
import timetablescheduler.application.port.GenerationExecutor
import timetablescheduler.application.port.GenerationRepository
import timetablescheduler.application.port.SolverListener
import timetablescheduler.application.port.StopSignal
import timetablescheduler.application.port.TimetableSolver
import timetablescheduler.domain.common.SchoolId
import timetablescheduler.domain.scheduling.generation.Alternative
import timetablescheduler.domain.scheduling.generation.AlternativeId
import timetablescheduler.domain.scheduling.generation.Generation
import timetablescheduler.domain.scheduling.generation.GenerationId
import timetablescheduler.domain.scheduling.generation.GenerationStatus
import timetablescheduler.domain.scheduling.problem.SchedulingProblem
import timetablescheduler.domain.scheduling.solution.TimetableCandidate
import java.time.Duration
import java.time.Instant

/**
 * A deterministic solver for application and web tests: reports the scripted [candidates] (built from the problem it
 * receives) in order, then [infeasiblePasses] infeasibility hints, and optionally fails with [failure]. Before every
 * candidate it checks the stop signal, like the real solver does between steps.
 */
class FakeTimetableSolver(
    var candidates: (SchedulingProblem) -> List<TimetableCandidate> = { emptyList() },
    var infeasiblePasses: Int = 0,
    var failure: RuntimeException? = null,
) : TimetableSolver {
    var lastProblem: SchedulingProblem? = null
        private set
    var lastTimeLimit: Duration? = null
        private set

    override fun solve(problem: SchedulingProblem, timeLimit: Duration, stopSignal: StopSignal, listener: SolverListener) {
        lastProblem = problem
        lastTimeLimit = timeLimit
        for (candidate in candidates(problem)) {
            if (stopSignal.isStopRequested()) return
            listener.onCandidate(candidate)
        }
        repeat(infeasiblePasses) { listener.onInfeasiblePass() }
        failure?.let { throw it }
    }
}

/** Runs a submitted generation immediately, or keeps it until [runPending] when [deferred] (to observe "running"). */
class ImmediateGenerationExecutor(var deferred: Boolean = false) : GenerationExecutor {
    private val pending = mutableListOf<() -> Unit>()

    override fun submit(task: () -> Unit) {
        if (deferred) pending += task else task()
    }

    fun runPending() {
        val tasks = pending.toList()
        pending.clear()
        tasks.forEach { it() }
    }
}

class InMemoryGenerationRepository : GenerationRepository {
    private val generations = linkedMapOf<GenerationId, Generation>()
    private val alternatives = linkedMapOf<AlternativeId, Alternative>()

    fun all(): List<Generation> = generations.values.toList()

    override fun insert(generation: Generation) {
        generations[generation.id] = generation
    }

    override fun findById(generationId: GenerationId): Generation? = generations[generationId]
    override fun findBySchool(schoolId: SchoolId): List<Generation> = generations.values.filter { it.schoolId == schoolId }
    override fun findRunning(schoolId: SchoolId): Generation? = findBySchool(schoolId).firstOrNull { it.isRunning }

    override fun updateProgress(generationId: GenerationId, alternativesFound: Int) {
        generations.computeIfPresent(generationId) { _, generation -> generation.copy(alternativesFound = alternativesFound) }
    }

    override fun saveEnd(generation: Generation) {
        generations[generation.id] = generation
    }

    override fun failRunning(at: Instant): Int {
        val running = generations.values.filter { it.isRunning }
        running.forEach { generations[it.id] = it.endAs(GenerationStatus.FAILED, at) }
        return running.size
    }

    override fun addAlternative(alternative: Alternative) {
        alternatives[alternative.id] = alternative
    }

    override fun removeAlternative(alternativeId: AlternativeId) {
        alternatives.remove(alternativeId)
    }

    override fun findAlternatives(generationId: GenerationId): List<Alternative> = alternatives.values.filter { it.generationId == generationId }
    override fun findAlternative(alternativeId: AlternativeId): Alternative? = alternatives[alternativeId]
}

/** What the generation log received; only ids, statuses and counts can be recorded (G13). */
sealed interface GenerationEvent {
    data class Started(val generationId: GenerationId) : GenerationEvent
    data class Discarded(val generationId: GenerationId, val candidates: Int) : GenerationEvent
    data class Finished(val generationId: GenerationId, val status: GenerationStatus, val alternatives: Int) : GenerationEvent
    data class Failed(val generationId: GenerationId, val exceptionClass: String) : GenerationEvent
}

class RecordingGenerationEventLog : GenerationEventLog {
    val events = mutableListOf<GenerationEvent>()

    override fun started(generationId: GenerationId) {
        events += GenerationEvent.Started(generationId)
    }

    override fun discarded(generationId: GenerationId, candidates: Int) {
        events += GenerationEvent.Discarded(generationId, candidates)
    }

    override fun finished(generationId: GenerationId, status: GenerationStatus, alternatives: Int, duration: Duration) {
        events += GenerationEvent.Finished(generationId, status, alternatives)
    }

    override fun failed(generationId: GenerationId, exceptionClass: String) {
        events += GenerationEvent.Failed(generationId, exceptionClass)
    }
}
