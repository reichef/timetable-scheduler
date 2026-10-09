package timetablescheduler.application.fakes

import timetablescheduler.application.port.GenerationExecutor
import timetablescheduler.application.port.SolverListener
import timetablescheduler.application.port.StopSignal
import timetablescheduler.application.port.TimetableSolver
import timetablescheduler.domain.scheduling.problem.SchedulingProblem
import timetablescheduler.domain.scheduling.solution.TimetableCandidate
import java.time.Duration

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
